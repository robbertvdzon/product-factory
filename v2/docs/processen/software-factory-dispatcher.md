# Product Factory v2 — Software Factory-dispatcher

Status: eerste ontwerp van de technische adapter en zijn contract.

De Software Factory-dispatcher stuurt steeds de eerste uitvoerbare story naar Software Factory en
verwerkt externe opleverstatussen. Hij is een apart uitvoerend onderdeel met een eigen schedule en
een eigen Maven-API/implementatiemodule. Zijn implementatie gebruikt uitsluitend
`product-planning-api` en andere benodigde API-modules; zij kent geen planningsimplementatie. Hij is
geen intelligente procesmodule, gebruikt geen AI-agents en bezit geen productlogica.
Daarom heeft hij geen `AgentRoleKey`, leest of schrijft hij geen Agentgeheugen en vraagt hij geen
`AiTask` aan.

## Verantwoordelijkheid

De dispatcher:

- synchroniseert de status van eerder verzonden stories met Software Factory;
- reserveert de volgende story atomair bij Productplanning voordat een externe call begint;
- meldt verzending en oplevering via de publieke commands van Productplanning;
- verwerkt alle geconfigureerde producten en verstuurt per product maximaal één nieuwe story wanneer
  Software Factory voor dat product geen open werk heeft;
- bouwt een volledig, onveranderlijk `StoryDeliveryPackage` uit de gekozen `StoryDetails`;
- bewaart iedere externe poging als `DeliveryAttempt`;
- handelt technische leveringsfouten zelf af.

De dispatcher kiest geen epic, maakt of herschrijft geen story, bepaalt geen prioriteit en start
Productontwerp, Productplanning of Kwaliteitsbewaking niet.

## Technische ingang

De scheduler, een bevoegde handmatige UI-actie of de bijbehorende REST-ingang start:

```java
void runDispatchSession();
```

De publieke read-only queries zijn:

```java
DispatcherProductStatusDetails getDispatchStatus(ProductId productId);
List<DeliveryAttemptDetails> findDeliveryAttempts(DeliveryAttemptFilter filter);
```

Een sessie start nooit agents. Er kan modulebreed maximaal één uitvoering tegelijk lopen. Een
botsende handmatige UI- of REST-aanroep krijgt `ProcessAlreadyRunning`, bij REST bijvoorbeeld HTTP
409. Een botsende schedulerrun wordt als overgeslagen geregistreerd. Atomische selectie en
idempotentie voorkomen altijd twee nieuwe externe stories voor hetzelfde product.

De twee queries zijn read-only en ondersteunen de gewone operationele en frontendweergave. De
productstatus toont onder meer open extern werk, eventuele technische blokkade en laatste poging.

## Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| geconfigureerde-productenquery | productmodule | alle producten waarvoor dispatching actief is |
| backlog- en statusqueries | Productplanning | open lokaal en extern gekoppeld werk in `sequenceNumber`-volgorde |
| `StoryDispatchReservationDetails` | Productplanning via `reserveNextStoryForDispatch(...)` | atomair gereserveerde complete storymomentopname die niet meer door annulering kan worden ingehaald |
| `SoftwareFactoryWork` | externe adapter | actuele externe status en oplevergegevens van eerder verzonden werk |

De dispatcher leest geen Git-repository, acceptatieomgeving of productieomgeving. Alles wat
Software Factory nodig heeft, moet al zelfstandig in de story staan.

## Output en effecten

| Contract of effect | Eigenaar | Betekenis |
|---|---|---|
| `StoryDeliveryPackage` | tijdelijk transportobject van de dispatcher | volledige, onveranderlijke story voor Software Factory |
| `DeliveryAttempt` | Software Factory-dispatcher | technische historie van request, response, fout, retry en idempotentiesleutel |
| `reserveNextStoryForDispatch(...)` | Productplanning | reserveer atomair hooguit één geldige volgende story en verkrijg de momentopname |
| `markStoryAsDispatched(...)` | Productplanning | leg extern ID vast en zet de story atomair op `IN_PROGRESS` |
| `markStoryAsDeveloped(...)` | Productplanning | leg oplevering vast en zet de story atomair op `DONE` |

De dispatcher schrijft niet rechtstreeks in `Story` of `PlanningWorkItem`. Zijn implementatiemodule
is de enige schrijver van `DeliveryAttempt`; inhoudelijke veranderingen lopen via de publieke
Productplanning-commands en hun invarianten.

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
              reserveNextStoryForDispatch(...)
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

Iedere sessie leest eerst de vaste set producten waarvoor dispatching actief is en voert daarna per
product dezelfde deterministische stappen uit. Iedere sessie:

1. vraagt de externe status op van stories met `IN_PROGRESS`;
2. bewaart iedere response of fout als `DeliveryAttempt`;
3. roept bij een oplevering idempotent `markStoryAsDeveloped(...)` aan;
4. verstuurt niets zolang Software Factory voor dat product nog open werk heeft;
5. vraagt anders `reserveNextStoryForDispatch(...)` aan; Productplanning kiest atomair de eerste
   afhankelijkheidsvrije `TODO`-story zonder annuleringsmarker en retourneert de onveranderlijke
   reservering;
6. vormt zonder inhoudelijke beslissing het pakket;
7. maakt met een stabiele idempotentiesleutel voor dit product maximaal één externe Software
   Factory-story;
8. roept `markStoryAsDispatched(...)` aan met reserverings-ID, extern ID en verwachte storyversie.

Bij een tijdelijke externe fout blijft dezelfde reservering bij dezelfde idempotentiesleutel horen.
Een volgende sessie herstelt of probeert precies die levering opnieuw en passeert haar niet met een
andere backlogstory. Als annulering de Productplanning-transactie vóór de reservering wint, ontstaat
geen reservering. Wint de reservering, dan geldt de story als reeds gestart en wordt die levering
normaal afgemaakt.

Als de backlog leeg is of geen story uitvoerbaar is, eindigt de sessie als normale no-op. Dat is
geen aanleiding om een intelligent proces te starten.

## Oplevering en verificatiewerk

`markStoryAsDeveloped(...)` handelt binnen Productplanning snel en deterministisch de storystatus
af. Die commandhandler vraagt vervolgens bij Kwaliteitsbewaking storyverificatie of een
bugfixhertest aan en laat de epic `ACTIVE`. Ook wanneer geen open stories meer bestaan, ontstaat nog
geen epicverificatie. Pas wanneer alle actuele controles binnen de epic geslaagd zijn, vraagt
Productplanning de epicverificatie aan.

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

De dispatcher blokkeert de levering en maakt een operationele melding. Als al een dispatchreservering
bestaat, blijft die met hetzelfde storypakket en dezelfde idempotentiesleutel bewaard totdat de
configuratie is hersteld. De story blijft `TODO` zolang geen extern werk bestaat. Er start geen
planningsagent en er ontstaat geen inhoudelijk workitem.

### Onverwachte contractbreuk door Software Factory

Software Factory moet ieder contractgeldig `StoryDeliveryPackage` accepteren. Een weigering is dus
geen inhoudelijke feedback en wordt nooit vertaald naar een aangepaste story of planningswerk. De
dispatcher bewaart de fout als `DeliveryAttempt`, blokkeert verdere levering voor dat product en
maakt een duidelijke operationele melding. Hervatten gebeurt pas nadat de integratie- of Software
Factory-fout is opgelost; het oorspronkelijke onveranderlijke storypakket blijft leidend.

### Fout na externe aanmaak

Bestaat het externe werk al maar is de lokale statusupdate mislukt, dan vindt de volgende sessie het
via de idempotentiesleutel. De story wordt daarna gekoppeld en `IN_PROGRESS`; er wordt geen tweede
externe story aangemaakt.

## Invarianten

- De dispatcher gebruikt nooit agents.
- De dispatcher gebruikt geen Agentgeheugen.
- De dispatcher gebruikt AI-uitvoering niet.
- De dispatcherimplementation gebruikt Productplanning uitsluitend via `product-planning-api`.
- Eén sessie verwerkt alle producten die bij de start voor dispatching geconfigureerd zijn.
- Voor een product staat normaal maximaal één Software Factory-story extern open.
- Eén sessie verstuurt per product maximaal één nieuwe story.
- Alleen een door Productplanning gereserveerde, afhankelijkheidsvrije `TODO`-story kan worden
  verstuurd.
- Het laagste geldige `sequenceNumber` bepaalt de keuze.
- Een story waarvoor annulering vóór de reservering is vastgelegd wordt niet verstuurd; een eerder
  gereserveerde story geldt als gestart.
- Een storypakket is een onveranderlijke momentopname van één exacte storyversie.
- Iedere externe poging heeft een `DeliveryAttempt` en een stabiele idempotentiesleutel.
- De dispatcher wijzigt geen productinhoud en neemt geen besluit.

## Integratie- en acceptatietesten

Integratietests en acceptatie gebruiken `MockSoftwareFactory` uit Product Factory Testbed. Deze
stateful simulator implementeert hetzelfde versioned externe contract als de echte Software
Factory. De dispatcher bevat geen testvertakking en gebruikt alleen een ander geconfigureerd
endpoint.

De simulator ondersteunt minimaal storyaanmaak, idempotente herhaling, open werk, statusverloop,
oplevering, uitvoeringsvragen, tijdelijke transportfouten, een verloren response na geslaagde
aanmaak en een technisch ongeldig of contractbrekend antwoord. Alleen de echte dispatcher vertaalt
deze antwoorden naar `DeliveryAttempt`s, blokkade en storystatussen; de simulator schrijft nooit
direct in Productplanning. De contractbreuk is uitsluitend een foutscenario voor de integratie en
maakt nooit product- of planningswerk.

Op acceptatie kan een tester via het aparte acceptatiescherm een scenario kiezen en bijvoorbeeld
een externe story afronden of de volgende call laten mislukken. De daaropvolgende
`runDispatchSession()` wordt bewust via de gewone UI gestart. Zie
[Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md).

## Gerelateerde documenten

- [Productplanning-API](productplanning/api.md)
- [Productplanning — MVP](productplanning/mvp.md)
- [Productplanning — uitgebreide implementatie](productplanning/uitgebreid.md)
- [Kwaliteitsbewaking-API](kwaliteitsbewaking/api.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
