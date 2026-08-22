# Product Factory v2 — Besluitenregister

Status: eerste ontwerp van ondersteunende module en publieke interface.

Het Besluitenregister bewaart alleen grote, blijvende productbesluiten. Het is geen proceslogboek en
neemt zelf geen besluiten. Het heeft geen agents, scheduler of `runProcessSession()` en is een
ondersteunende Spring Modulith-module met eigen tabellen en publieke commands en queries.

## Wat een besluit is

> Een besluit is een expliciete, blijvende keuze die toekomstige processessies begrenst en die niet
> vanzelf uit het normale productproces volgt.

Een keuze hoort alleen in het register wanneer zij doorgaans:

- over meerdere epics, stories of processessies heen geldt;
- toekomstige mogelijkheden of uitvoering merkbaar begrenst;
- kostbaar, riskant of belangrijk is om terug te draaien;
- iets is waarvan de Stakeholder redelijkerwijs op de hoogte wil zijn en waarop die moet kunnen
  terugkomen.

Voorbeelden zijn de keuze voor SQL in plaats van MongoDB, een blijvende privacygrens of een
belangrijke doelgroepbeperking.

Het gewone proces volgen is geen besluit. Dit zijn daarom geen besluiten:

- een droombeeld maken of aanpassen;
- een epic maken, herzien, kiezen, activeren, verifiëren of afsluiten;
- een epic in stories verdelen;
- een bug of epic tijdelijk voorrang geven;
- stories ordenen of een backlogitem dispatchen;
- een verificatie-uitkomst, gebruikerssignaal of normale statusovergang verwerken;
- intern onderzoek, bewijs, hypotheses, agentredeneringen of leerresultaten bewaren.

Deze gegevens blijven bij hun inhoudelijke eigenaar. Zo blijft het Besluitenregister klein en bevat
het alleen keuzes die echt richtinggevend zijn.

## Wie een besluit neemt

Een besluit heeft precies één herkomst:

- **Stakeholder** — de Stakeholder neemt het besluit tijdens een overleg. De notulenagent herkent de
  expliciete besluiten in de afgeronde notulen en registreert of wijzigt ze via de publieke commands.
  De agent is de registrator en niet de beslisser; een nieuw besluit krijgt
  `origin = STAKEHOLDER`.
- **Factory** — een proces of agent neemt binnen de `ProductAssignment` en geldige besluiten
  zelfstandig een grote, blijvende beslissing. Het besluit wordt direct zichtbaar voor de
  Stakeholder. Die kan het later via een overleg herzien, intrekken of vervangen.

Past een mogelijke keuze niet binnen de bestaande richting of zou zij die richting wezenlijk
veranderen, dan registreert de Factory geen actief besluit. Zij maakt een overlegverzoek of voorstel
waarop de Stakeholder kan beslissen.

## Intern datamodel

Dit is het opslagmodel binnen de module. Andere modules krijgen deze objecten en hun repositories
niet rechtstreeks.

```java
class Decision {
    String id;                         // UUID.toString()
    String productId;
    DecisionOrigin origin;             // STAKEHOLDER of FACTORY
    List<DecisionDetails> history;
    DecisionState state;               // ACTIVE, WITHDRAWN of SUPERSEDED
    String supersededByDecisionId;      // alleen bij SUPERSEDED
    String withdrawalReason;            // optioneel, alleen bij WITHDRAWN
}

class DecisionDetails {
    String id;                         // UUID.toString(), ID van deze versie
    Timestamp validFrom;
    Timestamp validUntil;              // null zolang deze versie actueel is
    String decision;
}
```

`Decision` is de stabiele identiteit van één besluitonderwerp. `DecisionDetails` bevat de versies
van de besluittekst. De volgorde volgt uit `validFrom`; een apart versienummer is niet nodig.

Er staat bewust geen `contractVersion` in dit domeinmodel. Een eventuele versie van een REST- of
eventcontract hoort in de technische interface- of berichtlaag en zegt niets over het besluit zelf.

## Aanpassen, intrekken en vervangen

De drie bewerkingen betekenen iets anders.

### Hetzelfde besluit aanpassen

Een tekstuele verduidelijking of wijziging die nog over hetzelfde besluitonderwerp gaat, maakt een
nieuwe `DecisionDetails` binnen dezelfde `Decision`:

1. de huidige versie krijgt `validUntil` gelijk aan het wijzigingsmoment;
2. een nieuwe versie krijgt precies dat moment als `validFrom` en `validUntil = null`;
3. de `Decision` blijft `ACTIVE`;
4. de oude versie blijft volledig leesbaar.

### Intrekken zonder opvolger

Wanneer een besluit niet meer geldt en geen nieuw besluit het overneemt:

1. de actuele versie krijgt een `validUntil`;
2. de `Decision` krijgt state `WITHDRAWN`;
3. een eventuele `withdrawalReason` wordt opgeslagen;
4. alle versies blijven in de historie staan.

### Laten overnemen door een nieuw besluit

Wanneer een inhoudelijk ander besluit het oude vervangt, heet dat **superseded**: in de frontend
tonen we “Vervangen door”. Het nieuwe besluit krijgt een eigen `Decision`-ID. Het oude besluit:

1. krijgt state `SUPERSEDED`;
2. krijgt op zijn actuele versie `validUntil` gelijk aan `validFrom` van het nieuwe besluit;
3. krijgt `supersededByDecisionId` met het ID van het nieuwe besluit.

Bij een overstap van MongoDB naar SQL ontstaat dus een nieuw SQL-besluit. Ieder oud MongoDB-besluit
dat daardoor werkelijk ongeldig wordt, kan `SUPERSEDED` worden en naar hetzelfde SQL-besluit wijzen.
Een gewone versiecorrectie gebruikt deze relatie niet.

Geldigheidsperioden zijn halfopen: `validFrom` hoort erbij en `validUntil` niet. Daardoor kunnen een
oude en nieuwe versie of een oud en opvolgend besluit exact op hetzelfde tijdstip aansluiten zonder
overlap.

## Publieke commandinterface

De notulenagent en bevoegde Factory-modules gebruiken vier doelgerichte, idempotente commands:

```java
DecisionId createDecision(CreateDecisionCommand command);
void reviseDecision(ReviseDecisionCommand command);
void withdrawDecision(WithdrawDecisionCommand command);
DecisionId supersedeDecisions(SupersedeDecisionsCommand command);
```

`supersedeDecisions(...)` maakt het nieuwe besluit en sluit één of meer oude besluiten atomair af.
Ieder command controleert product, bevoegde aanroeper, verwachte state en idempotentiesleutel. Er is
geen algemene setter en een aanroeper schrijft nooit rechtstreeks in `Decision` of
`DecisionDetails`.

## Query 1 — geldige besluiten op een moment

De normale query levert een platte momentopname en nooit het interne aggregate:

```java
List<DecisionDto> getDecisions(ProductId productId, Timestamp validAt);
```

`validAt` is optioneel en staat standaard op het huidige tijdstip. Zonder datum krijgt de aanroeper
alle besluiten die nu geldig zijn. Met een datum in het verleden krijgt zij alle besluiten die op
dat moment geldig waren. Dit is vooral bedoeld om later te reconstrueren waarom het product een
bepaalde richting volgde.

De query:

- kiest per besluit alleen de `DecisionDetails` waarvoor
  `validFrom <= validAt < validUntil`, waarbij `null` geen einddatum betekent;
- retourneert geen volledige versiehistorie;
- retourneert geen besluit dat op het gekozen moment ingetrokken of vervangen was;
- retourneert een nu ingetrokken of vervangen besluit wél wanneer het op de gevraagde historische
  datum nog geldig was;
- filtert daarom nooit eerst uitsluitend op de huidige `Decision.state`.

```java
class DecisionDto {
    String id;
    String productId;
    DecisionOrigin origin;
    String decision;
    Timestamp validFrom;
    Timestamp validUntil;
}
```

Procesmodules gebruiken normaal alleen deze query met het huidige tijdstip. Zij gebruiken geldige
besluiten als begrenzende context en interpreteren vrije besluittekst niet als een ongetypeerd
command.

## Query 2 — volledig besluitenarchief

De frontend en auditfuncties gebruiken een aparte query:

```java
List<DecisionHistoryDto> getDecisionArchive(ProductId productId);
```

Deze query retourneert alle actieve, ingetrokken en vervangen besluiten met alle versies,
intrekkingsredenen en vervangingsrelaties. Het archief is read-only en wordt niet als operationele
input voor de processen gebruikt.

```java
class DecisionHistoryDto {
    String id;
    String productId;
    DecisionOrigin origin;
    DecisionState state;
    String supersededByDecisionId;
    String withdrawalReason;
    List<DecisionDetailsDto> history;
}

class DecisionDetailsDto {
    String id;
    Timestamp validFrom;
    Timestamp validUntil;
    String decision;
}
```

De normale frontendweergave toont de huidige uitkomst van `getDecisions(...)`. Onder **Historie**
kan de Stakeholder het volledige archief openen, eerdere versies vergelijken, ingetrokken besluiten
zien en van een vervangen besluit naar zijn opvolger navigeren.

## Overleg en Factory-besluiten

Na afsluiting van een overleg maakt de notulenagent eerst de notulen. Daarna verwerkt hij alleen de
expliciete grote besluiten uit die notulen:

- een nieuw besluit via `createDecision(...)`;
- een nieuwe versie van hetzelfde besluit via `reviseDecision(...)`;
- een intrekking met eventuele reden via `withdrawDecision(...)`;
- een inhoudelijk nieuw besluit dat oudere besluiten overneemt via `supersedeDecisions(...)`.

De notulen bewaren hun eigen besproken context en kunnen de betrokken besluit-ID's tonen. Die
vergaderinformatie hoeft daarom niet ook in het minimale `Decision`-aggregate te worden gekopieerd.

Een Factory-besluit volgt exact dezelfde opslag- en lifecycle-regels. Het verschil staat alleen in
`origin = FACTORY`. De Stakeholder ziet het via de normale frontendquery en kan via een later overleg
dezelfde revise-, withdraw- of supersedecommands laten uitvoeren.

## Technische regels

- Het Besluitenregister is de enige schrijver van `Decision` en `DecisionDetails`.
- Het register bewaakt dat per `Decision` maximaal één `DecisionDetails`-versie tegelijk geldig is.
- Een revise-, withdraw- of supersedecommand gebruikt de verwachte actuele versie om verloren
  wijzigingen te voorkomen.
- Het aanmaken van een opvolger en afsluiten van alle overgenomen besluiten gebeurt atomair.
- Historische besluiten en versies worden nooit fysiek verwijderd.
- UUID's worden extern als strings getransporteerd; de implementatie mag intern een UUID-type
  gebruiken.
- De DTO's bevatten geen prompts, chain-of-thought, tokens, secrets of interne agentinformatie.

## Gerelateerde documenten

- [Overzicht](overzicht.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
