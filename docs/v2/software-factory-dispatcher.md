# Product Factory v2 — Software Factory-dispatcher

Status: eerste ontwerp van de technische adapter en zijn contract.

De Software Factory-dispatcher stuurt steeds de eerste uitvoerbare story naar Software Factory en
verwerkt externe opleverstatussen. Hij is een apart uitvoerend onderdeel met een eigen schedule en
dit eigen document, maar blijft technisch een eenvoudige adapter binnen de module Productplanning.
Hij is geen intelligente procesmodule, gebruikt geen AI-agents en bezit geen productlogica.
Daarom heeft hij geen `AgentRoleKey`, leest of schrijft hij geen Agentgeheugen en vraagt hij geen
`AiTask` aan.

## Verantwoordelijkheid

De dispatcher:

- synchroniseert de status van eerder verzonden stories met Software Factory;
- meldt verzending en oplevering via de publieke commands van Productplanning;
- verstuurt maximaal één nieuwe story wanneer Software Factory voor dat product geen open werk heeft;
- bouwt een volledig, onveranderlijk `StoryDeliveryPackage` uit de gekozen `StoryDetails`;
- bewaart iedere externe poging als `DeliveryAttempt`;
- handelt technische leveringsfouten zelf af;
- maakt alleen bij definitieve inhoudelijke afwijzing gericht `REPAIR_STORY`-planwerk.

De dispatcher kiest geen epic, maakt of herschrijft geen story, bepaalt geen prioriteit en start
Productontwerp, Productplanning of Kwaliteitsbewaking niet.

## Technische ingang

De scheduler start:

```java
void runDispatchSession();
```

Een sessie start nooit agents. Gelijktijdige schedulerpogingen mogen door atomische selectie en
idempotentie nooit twee nieuwe externe stories voor hetzelfde product maken.

## Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| backlogquery | Productplanning | alle `TODO`- en `IN_PROGRESS`-stories in `sequenceNumber`-volgorde |
| `StoryDetails` | Productplanning | complete storyinhoud, UX, assets, afhankelijkheden, status en externe referentie |
| `EpicDetails` | Productontwerp | controle dat de bijbehorende epic niet `CANCELLED` is |
| `SoftwareFactoryWork` | externe adapter | actuele externe status en oplevergegevens van eerder verzonden werk |

De dispatcher leest geen Git-repository, acceptatieomgeving of productieomgeving. Alles wat
Software Factory nodig heeft, moet al zelfstandig in de story staan.

## Output en effecten

| Contract of effect | Eigenaar | Betekenis |
|---|---|---|
| `StoryDeliveryPackage` | tijdelijk transportobject van de dispatcher | volledige, onveranderlijke story voor Software Factory |
| `DeliveryAttempt` | dispatcher binnen Productplanning | technische historie van request, response, fout, retry en idempotentiesleutel |
| `markStoryAsDispatched(...)` | Productplanning | leg extern ID vast en zet de story atomair op `IN_PROGRESS` |
| `markStoryAsDeveloped(...)` | Productplanning | leg oplevering vast en zet de story atomair op `DONE` |
| `REPAIR_STORY` | Productplanning | intern `PlanningWorkItem` na definitieve inhoudelijke pakketafwijzing |

De dispatcher schrijft niet rechtstreeks in `Story` of `PlanningWorkItem`. De adapterservice mag
binnen Productplanning een `DeliveryAttempt` opslaan, maar inhoudelijke veranderingen lopen via de
normale domeinservices en hun invarianten.

## StoryDeliveryPackage

Het pakket bevat minimaal:

- stabiel story-ID, storyversie, product-ID en type;
- zelfstandig gebruikersgedrag en gebruikerswaarde;
- alle acceptatiecriteria;
- het relevante volledige UX-ontwerp en alle schermtoestanden;
- tekstuele assets als UTF-8 en binaire assets met MIME-type, grootte, hash en transportencoding;
- afhankelijkheden en technische grenzen;
- exacte epic-, bug- en andere bronversies;
- inhoudshash en idempotentiesleutel;
- geen verwijzing die Software Factory verplicht terug te vragen bij Product Factory.

Tekst, Markdown, JSON en SVG blijven tekst. Alleen binaire inhoud gebruikt Base64 wanneer het
externe transport uitsluitend JSON ondersteunt.

## Verloop van één dispatchersessie

```text
synchroniseer eerder verzonden werk
                 │
                 ├── opgeleverd ──> markStoryAsDeveloped(...)
                 │
                 ▼
heeft Software Factory nog open werk voor dit product?
                 │
          ja ────┴──── nee
          │              │
        no-op            ▼
              eerste uitvoerbare TODO-story
                         │
                         ▼
              StoryDeliveryPackage vormen
                         │
                         ▼
              idempotent extern aanmaken
                         │
                         ▼
              markStoryAsDispatched(...)
```

Iedere sessie:

1. vraagt de externe status op van stories met `IN_PROGRESS`;
2. bewaart iedere response of fout als `DeliveryAttempt`;
3. roept bij een oplevering idempotent `markStoryAsDeveloped(...)` aan;
4. verstuurt niets zolang Software Factory voor dat product nog open werk heeft;
5. kiest anders atomair de afhankelijke-vrije `TODO`-story met het laagste `sequenceNumber` waarvan
   de epic niet `CANCELLED` is;
6. vormt zonder inhoudelijke beslissing het pakket;
7. maakt met een stabiele idempotentiesleutel precies één externe Software Factory-story;
8. roept `markStoryAsDispatched(...)` aan met extern ID en verwachte storyversie.

Als de backlog leeg is of geen story uitvoerbaar is, eindigt de sessie als normale no-op. Dat is
geen aanleiding om een intelligent proces te starten.

## Oplevering en verificatiewerk

`markStoryAsDeveloped(...)` handelt binnen Productplanning snel en deterministisch de storystatus
af. Die commandhandler vraagt vervolgens bij Kwaliteitsbewaking storyverificatie of een
bugfixhertest aan. Wanneer voor een niet-geannuleerde epic geen open stories meer bestaan, vraagt de
handler ook epicverificatie aan.

De dispatcher zelf maakt geen `QualityWorkItem` en beoordeelt niet of de oplevering goed is. Hij
constateert alleen wat Software Factory als opgeleverd meldt.

## Fouten en herstel

### Tijdelijke transportfout

Bij timeout, netwerkfout of tijdelijke externe storing:

1. bewaar een mislukte `DeliveryAttempt`;
2. zoek vóór iedere retry op dezelfde idempotentiesleutel of het externe werk toch bestaat;
3. herstel in dat geval de externe koppeling en markeer de story als verzonden;
4. probeer anders later met begrensde backoff opnieuw.

De dispatcher maakt nooit blind een duplicaat.

### Configuratie- of autorisatiefout

De dispatcher blokkeert de levering en maakt een operationele melding. De story blijft `TODO` als
geen extern werk bestaat. Er start geen planningsagent en er ontstaat geen inhoudelijk workitem.

### Definitieve inhoudelijke afwijzing

Alleen wanneer Software Factory het complete pakket definitief op inhoud of contract afwijst, maakt
Productplanning intern een idempotent `REPAIR_STORY`-workitem met de afwijzingsdetails. Een latere
`runProcessSession()` van Productplanning mag de nog niet verzonden story herstellen. De dispatcher
past de inhoud nooit zelf aan.

### Fout na externe aanmaak

Bestaat het externe werk al maar is de lokale statusupdate mislukt, dan vindt de volgende sessie het
via de idempotentiesleutel. De story wordt daarna gekoppeld en `IN_PROGRESS`; er wordt geen tweede
externe story aangemaakt.

## Invarianten

- De dispatcher gebruikt nooit agents.
- De dispatcher gebruikt geen Agentgeheugen.
- De dispatcher gebruikt AI-uitvoering niet.
- Voor een product staat normaal maximaal één Software Factory-story extern open.
- Alleen een afhankelijke-vrije `TODO`-story kan worden verstuurd.
- Het laagste geldige `sequenceNumber` bepaalt de keuze.
- Een story van een `CANCELLED` epic wordt niet verstuurd.
- Een storypakket is een onveranderlijke momentopname van één exacte storyversie.
- Iedere externe poging heeft een `DeliveryAttempt` en een stabiele idempotentiesleutel.
- De dispatcher wijzigt geen productinhoud en neemt geen besluit.

## Gerelateerde documenten

- [Productplanning-API](productplanning.md)
- [Productplanning — MVP](productplanning-mvp.md)
- [Productplanning — uitgebreide implementatie](productplanning-uitgebreid.md)
- [Kwaliteitsbewaking-API](kwaliteitsbewaking.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
