# Product Factory v2 — Besluitenregister

Status: eerste ontwerp van ondersteunende module en publieke interface.

Het Besluitenregister bewaart alle betekenisvolle productbesluiten op één leesbare en herleidbare
plek. Het register neemt zelf geen besluiten. Productontwerp, Productplanning, de product-/overlegmodule
of een andere bevoegde eigenaar neemt het besluit en levert daarna een registratieverzoek aan.

Het Besluitenregister is geen vierde intelligent proces. Het heeft geen agents, geen scheduler en
geen `runProcessSession()`. Het is een ondersteunende Spring Modulith-module met gewone application
commands, eigen tabellen in dezelfde fysieke productdatabase en read-only queries voor de
frontend.

## Wat wel en niet een besluit is

Een besluit verandert richting, productinhoud, prioriteit, uitvoering of de afhandeling van een
onderwerp. Voorbeelden zijn:

- een droombeeld wezenlijk aanpassen;
- een epic maken, herzien, intrekken of bewust niet maken;
- een gebruikerssignaal zonder epic afsluiten als duplicaat of buiten scope;
- een exacte epicversie kiezen;
- een belangrijke prioriteitskeuze of prioriteitsregel vastleggen;
- een epic na verificatie afsluiten, openhouden of stoppen;
- een bindende richting, grens of correctie van de Stakeholder vastleggen.

Ruwe onderzoeksresultaten, hypotheses, observaties, agentredeneringen, testbewijs en iedere kleine
uitvoeringsstap zijn geen besluiten. Zij blijven bij hun eigenaar. Een besluit mag ernaar verwijzen
en bevat alleen de korte onderbouwing die nodig is om de keuze later te begrijpen.

Een bugclassificatie, storyverificatie of epicverificatie is eerst een inhoudelijk oordeel met bewijs
van Kwaliteitsbewaking en blijft in die eigen entiteit staan. De daaropvolgende keuze om bijvoorbeeld
een fix voorrang te geven, een epic open te houden of een uitvoering te stoppen is wél een besluit
voor het Besluitenregister. Zo kopieert het register geen testresultaten.

## Publieke interface

De schrijvende application interface bestaat uit twee idempotente commands:

```java
DecisionId recordDecision(RecordDecisionCommand command);
void withdrawDecision(WithdrawDecisionCommand command);
```

Procesmodules krijgen geen repository van het Besluitenregister. Zij leveren een command of
gepubliceerd application event met een idempotentiesleutel. Alleen het Besluitenregister vormt en
schrijft het duurzame besluitrecord.

De publieke leesinterface retourneert `DecisionDetails`. Dit DTO is geen tweede entiteit. Andere processen gebruiken dit generieke record
niet als vervanging van hun inhoudelijke contracten. Productplanning blijft bijvoorbeeld
`EpicDetails` lezen en niet een besluittekst interpreteren om een epic te bouwen. Het
besluitenregister is primair voor audit, uitleg, de frontend en de afhandeling van expliciet aan een
gebruikerssignaal gekoppelde ontwerpbesluiten.

## DecisionRecord

Een besluit bevat minimaal:

- stabiel besluit-ID, product-ID en contractversie;
- een stabiele `decisionKey` voor het onderwerp waarop geldigheid en vervanging worden bewaakt;
- besluitsoort en toepassingsgebied;
- de concrete beslissing in gewone producttaal;
- korte onderbouwing en relevante overwogen alternatieven;
- verwijzingen naar gebruikte bewijs-, signaal- en productentiteiten met exacte versies;
- de module en eventueel de bevoegde Stakeholder die het besluit namen;
- processessie-ID of overleg-ID;
- `validFrom` en een optionele `validUntil`;
- status **Gepland**, **Actief**, **Vervangen** of **Ingetrokken**;
- optioneel `supersedesDecisionId` en `replacedByDecisionId`;
- aanmaakmoment en, bij intrekking, intrekkingsreden.

De inhoud, onderbouwing, alternatieven en bronverwijzingen van een geregistreerd besluit zijn
onveranderlijk. Alleen het Besluitenregister mag de levenscyclusvelden invullen wanneer het besluit
eindigt. Daardoor blijft altijd zichtbaar wat destijds is besloten en op basis waarvan.

## Geldigheid, intrekken en vervangen

Een besluit zonder `validUntil` geldt vanaf `validFrom` totdat het wordt ingetrokken of vervangen.
De geldigheidsperiode is halfopen: `validFrom` hoort erbij en `validUntil` niet. Daardoor kan een
nieuw besluit exact beginnen op het moment waarop het oude eindigt zonder overlap.

Bij intrekking zonder vervangend besluit:

1. het Besluitenregister zet `validUntil` op het effectieve intrekkingsmoment;
2. de status wordt **Ingetrokken**;
3. de reden en bron van de intrekking worden vastgelegd;
4. de oude inhoud blijft leesbaar.

Bij vervanging door een nieuw besluit gebeurt in één transactie:

1. het nieuwe besluit wordt aangemaakt met `supersedesDecisionId`;
2. het oude besluit krijgt `validUntil` gelijk aan `validFrom` van het nieuwe besluit;
3. het oude besluit krijgt status **Vervangen** en `replacedByDecisionId`;
4. het nieuwe besluit wordt **Actief**, of **Gepland** wanneer de ingangsdatum in de toekomst ligt.

Voor dezelfde `decisionKey` en hetzelfde toepassingsgebied mogen geldigheidsperioden niet ongemerkt
overlappen. Een correctie met terugwerkende kracht is een afzonderlijk, zichtbaar administratief
besluit en herschrijft de historische registratie niet stilletjes.

## Relatie met interne leerresultaten

`LearningResult` blijft een interne entiteit van Productontwerp. Het kan onderzoek, hypotheses,
tegenspraak en uitgebreide conclusies bevatten. Alleen wanneer daar een concrete keuze uit volgt,
registreert Productontwerp een `DecisionRecord` met een korte onderbouwing en verwijzingen naar
de interne bronregistratie of publieke bewijsentiteiten.

Als Productontwerp een gebruikerssignaal beoordeelt maar geen epic maakt, registreert het een besluit
met soort **Signaalbeoordeling** en het signaal-ID. Productontwerp roept daarna een command op de
productmodule aan om de status en besluitkoppeling op `UserSignal` te actualiseren. Het volledige
interne leerresultaat wordt niet publiek.

## Eigenaarschap en lezen

| Onderdeel | Verantwoordelijkheid |
|---|---|
| Bronmodule of bevoegde Stakeholder via productbediening | neemt het besluit en levert inhoud, onderbouwing, bronnen en geldigheid aan |
| Besluitenregister | maakt het besluitrecord, bewaakt idempotentie, geldigheidsperioden, intrekking en vervanging |
| Frontend | leest actieve en historische `DecisionDetails`-objecten en toont waarom en wanneer een besluit gold |
| Procesmodules | blijven hun specifieke procescontracten lezen; zij mogen relevante besluiten tonen of als gecontroleerde context gebruiken, maar niet als ongetypeerde opdracht uitvoeren |

De frontend biedt per product een chronologische besluitenlijst en toont bij ieder betrokken object
het actieve besluit, eerdere versies, ingangs- en einddatum, vervangingsrelaties, onderbouwing en
bronnen.

## Technische regels

- Het Besluitenregister beheert zijn eigen tabellen, repository en transacties.
- Het register valideert dat de bronmodule of Stakeholder volgens het mandaat bevoegd is voor de
  besluitsoort en het toepassingsgebied; alleen die eigenaar of een bevoegde opvolger kan het besluit
  intrekken of vervangen.
- Registratie is idempotent op bronmodule, bronobject, bronversie en besluitsoort.
- Het sluiten van het oude besluit en activeren van het vervangende besluit is atomair.
- **Gepland** en **Actief** worden uit het huidige tijdstip en de geldigheidsperiode afgeleid; daar is
  geen geplande processessie voor nodig.
- Historische besluiten worden nooit fysiek verwijderd om een actuele projectie eenvoudiger te
  maken.
- `DecisionDetails` bevat geen geheime prompts, verborgen chain-of-thought, tokens of secrets.
- Inhoudelijke productentiteiten blijven de bron voor uitvoering; het besluit verwijst ernaar en
  kopieert ze niet volledig.
