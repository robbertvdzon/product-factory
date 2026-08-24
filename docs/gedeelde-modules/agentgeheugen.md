# Product Factory v2 — Agentgeheugen

Status: eerste ontwerp van de ondersteunende module en publieke interface.

Agentgeheugen geeft iedere AI-agentrol een eigen permanent, versieerbaar geheugen per product. Een
agentinstantie is tijdelijk, maar de rol onthoudt bruikbare lessen tussen processessies. De
Stakeholder kan dit geheugen via de gebruikersinterface bekijken, corrigeren, aanvullen en
intrekken.

Agentgeheugen is een ondersteunende capability met een klein publiek contract in
`product-factory-api` en een implementatiemodule.
Alleen de implementatie mag intern Spring Modulith gebruiken. De capability heeft geen agents,
scheduler of `runProcessSession()` en neemt geen productbesluiten. Zij bewaart alleen gevalideerde
geheugenwijzigingen en levert de actuele projectie voor de input van een agenttaak.

## Hoofdregel

> Een gewone procesagent leest en wijzigt uitsluitend het geheugen van zijn eigen stabiele
> agentrol binnen het product van de huidige processessie.

De aanvragende procesruntime bepaalt `productId` en `agentRole` uit de vertrouwde
`AgentExecutionContext`. Deze waarden
komen nooit uit agentoutput of vrije prompttekst. Een agent kan daardoor geen andere rol of ander
product opgeven om daar geheugen te lezen of te wijzigen.

Er zijn drie expliciete, vertrouwde uitzonderingen op de rolgrens:

- de globale Stakeholder mag via bevoegde UI-commands ieder rolgeheugen beheren;
- de Meeting Agent mag tijdens één overleg het actuele geheugen van alle actieve rollen van precies
  dat product lezen;
- de notulenagent mag bij afsluiting dezelfde productbrede geheugensnapshot lezen en
  controleerbare wijzigingen voor meerdere rollen als één idempotente batch laten toepassen.

Deze uitzonderingen gebruiken een door de product-/overlegruntime gemaakte
`MeetingExecutionContext`. Een vrij agentantwoord kan deze context, het product of de toegestane
rollen niet kiezen. Gewone procesagents krijgen deze API nooit aangeboden.

## Waar geheugen voor bedoeld is

Rolgeheugen bevat compacte, herbruikbare lessen die dezelfde rol bij een volgende sessie helpen,
bijvoorbeeld:

- een Productontwerper onthoudt welke soorten UX-aanname vaak verkeerd blijken;
- een Planner onthoudt welke storysplitsing voor dit product goed of juist slecht werkte;
- een Tester onthoudt risicovolle routes, lastige testdata of terugkerende regressiegebieden;
- een gespecialiseerde rol onthoudt alleen lessen die bij haar eigen taak horen.

Geheugen is geen vervanging voor publieke productwaarheid. De volgende informatie blijft bij haar
eigen domeineigenaar:

- productdoel en harde grenzen in `ProductAssignment`;
- grote blijvende keuzes in `Decision`;
- feedback en observaties in `UserSignal`;
- tijdelijke vragen en antwoorden in `StakeholderQuestion` en `Meeting`;
- afgesproken productwerk in `Epic` en `Story`;
- aangetoonde kwaliteit in `Bug`, `Verification` en `QualitySnapshot`.

Een geheugenitem mag naar deze objecten verwijzen, maar kopieert ze niet als alternatieve waarheid.

## Agentrol is de eigenaar van de inhoud

Het geheugen hoort bij een stabiele `AgentRoleKey`, niet bij:

- een specifieke agentinstantie;
- een model of modelversie;
- een promptversie;
- een processessie;
- de procesmodule als geheel.

Alle instanties die later dezelfde rol uitvoeren, krijgen hetzelfde actuele rolgeheugen. Voorbeelden
van stabiele sleutels zijn `PRODUCT_DESIGNER_MVP`, `PLANNER_MVP` en `TESTER_MVP`. Uitgebreide
implementaties registreren voor iedere gespecialiseerde rol een eigen sleutel.

Een hernoemde rol krijgt niet stilzwijgend een nieuwe sleutel. De rolcatalogus bewaart de stabiele
sleutel en een wijzigbare weergavenaam. Als een rol werkelijk wordt vervangen, gebeurt overdracht
via een expliciete, controleerbare geheugenmigratie; de nieuwe rol leest nooit automatisch het
geheugen van de oude rol.

## Rolcatalogus

Iedere gekozen procesimplementatie registreert bij het opbouwen van de applicatie haar actieve
agentrollen in één publieke rolcatalogus van Agentgeheugen. Een `AgentRoleDefinitionDetails` bevat
minimaal:

- stabiele `AgentRoleKey` en menselijke weergavenaam;
- eigenaar/capability en implementatievariant;
- doel en verantwoordelijkheden van de rol;
- expliciete grenzen: wat de rol niet beslist of wijzigt;
- of de rol voor het gekozen product actief is.

`getAgentRoleCatalog(productId)` geeft alleen rollen terug die voor dat product en de gekozen
implementaties actief zijn. De Meeting Agent gebruikt deze definities om een gerichte vraag
herkenbaar vanuit de juiste rol te beantwoorden. De catalogus is geen vrij wijzigbaar agentgeheugen:
de definities komen uit vertrouwde implementatieregistratie en agents kunnen hun eigen rol of
bevoegdheden niet herschrijven.

## Publieke module-interface

Andere modules gebruiken alleen deze publieke API:

```java
List<AgentMemoryItemDetails> getActiveMemory(
    AgentExecutionContext context
);

List<AgentMemoryItemDetails> getMemoryAt(
    ProductId productId,
    AgentRoleKey agentRole,
    Instant validAt
);

List<AgentMemoryVersionDetails> getMemoryHistory(
    ProductId productId,
    AgentRoleKey agentRole,
    MemoryItemId memoryItemId
);

List<AgentRoleDefinitionDetails> getAgentRoleCatalog(ProductId productId);
MeetingMemorySnapshot getMeetingMemorySnapshot(MeetingExecutionContext context);

MemoryItemId addAgentMemory(AddAgentMemoryCommand command);
MemoryVersionId replaceAgentMemory(ReplaceAgentMemoryCommand command);
void retractAgentMemory(RetractAgentMemoryCommand command);
MeetingMemoryChangeResult applyMeetingMemoryChanges(
    ApplyMeetingMemoryChangesCommand command
);
```

`getActiveMemory(...)` is de enige normale agentquery. Zij accepteert een vertrouwde
`AgentExecutionContext` en controleert dat product en rol overeenkomen met de actieve
procesuitvoering die de agenttaak samenstelt.

De historische queries zijn bestemd voor Stakeholder-UI, audit en beheer. Een gewone agenttaak krijgt
geen ingetrokken of vervangen versies en kan geen peildatum kiezen.

`getMeetingMemorySnapshot(...)` is uitsluitend beschikbaar aan de product-/overlegmodule met een
geldige open meeting en levert de rolcatalogus plus alle actuele geheugenitems van precies dat
product. De snapshot bevat de exacte versie-ID's en wordt op de overlegtaak vastgezet. Een Meeting
Agent kan geen ander product kiezen en krijgt geen historische of ingetrokken inhoud.

## Wie mag schrijven

Er zijn drie schrijfbronnen:

### De eigen agentrol

Een agent kan aan het einde van een succesvolle taak gestructureerde geheugenacties voorstellen:

- `ADD` — voeg een nieuwe herinneringslijn toe;
- `REPLACE` — vervang één actuele versie door een complete nieuwe versie;
- `RETRACT` — trek een actueel item in omdat het onjuist, achterhaald of niet meer nuttig is.

De procesruntime vult product, rol, actor, sessie-ID en idempotentiesleutel zelf in. De agent levert
alleen titel, inhoud, doelitem en reden. Een agentactie voor een andere rol of een ander product is
daardoor niet uitdrukbaar in het schema.

Geheugenacties worden pas toegepast nadat de agenttaak en haar publieke procesoutput geldig zijn.
Een mislukte of afgekeurde agenttaak leert niets permanent. De procesruntime biedt de acties idempotent
aan; een technische retry maakt geen dubbele versie.

### De Stakeholder via de UI

De globale Stakeholder kan voor iedere zichtbare agentrol binnen ieder product:

- een item toevoegen;
- titel en inhoud vervangen door een nieuwe versie;
- een item intrekken;
- een ingetrokken gedachte later als een nieuwe lijn opnieuw toevoegen;
- een korte verplichte wijzigingsreden opgeven.

De UI roept dezelfde commands aan, maar met `actorType = STAKEHOLDER`. Een overlegagent mag deze
commands niet gebruiken om een gewone agentwijziging na te bootsen.

### De notulenagent na een overleg

De notulenagent kan uit een afgerond gesprek compacte, blijvende lessen voor meerdere rollen
afleiden. De product-/overlegruntime zet die voorstellen om in één
`ApplyMeetingMemoryChangesCommand` met:

- product-ID, meeting-ID en notulentaak-ID uit vertrouwde context;
- per wijziging de doelrol, actie `ADD`, `REPLACE` of `RETRACT`, inhoud, reden en bij vervangen of
  intrekken de verwachte actuele versie;
- één idempotentiesleutel voor de volledige batch.

Agentgeheugen controleert dat de meeting gesloten is, iedere doelrol in de actieve rolcatalogus
staat en iedere verwachte versie nog actueel is. De batch wordt atomair toegepast of volledig als
conflict afgewezen. Iedere versie krijgt `actorType = MEETING_MINUTES_AGENT` en het meeting-ID als
bron. Een menselijke goedkeuringsstap is niet vereist; de Stakeholder ziet de veranderingen bij de
notulen en kan ze via de gewone versieerbare UI corrigeren.

Losse antwoorden, tijdelijke open vragen en normale productstatus horen niet in deze batch. Zij
blijven respectievelijk bij `StakeholderQuestion` of hun eigen domeineigenaar.

## Datamodel

Het opslagmodel volgt het bewezen append-only patroon uit v1.

### AgentMemoryItem

`AgentMemoryItem` is de stabiele identiteit van één herinneringslijn:

```java
class AgentMemoryItem {
    String id;              // UUID.toString()
    String productId;
    String agentRole;       // stabiele AgentRoleKey
}
```

### AgentMemoryVersion

Iedere toevoeging of vervanging maakt een onveranderlijke versie:

```java
class AgentMemoryVersion {
    String id;              // UUID.toString()
    String memoryItemId;
    String supersedesId;    // null bij versie 1
    String title;
    String content;
    Instant createdAt;
    ActorRef createdBy;
    String changeReason;
}
```

### AgentMemoryRetraction

Intrekken overschrijft geen versie, maar maakt een tombstone:

```java
class AgentMemoryRetraction {
    String id;              // UUID.toString()
    String memoryItemId;
    Instant createdAt;
    ActorRef createdBy;
    String reason;
}
```

De database garandeert maximaal één directe opvolger per versie en maximaal één intrekking per
geheugenlijn. `validUntil`, versienummer en status worden uit opvolgers en tombstones afgeleid en
niet teruggeschreven in oude versies.

## Read-only contracten

### AgentMemoryItemDetails

De actuele agentcontext bevat per actief item minimaal:

- geheugenitem-ID en exacte versie-ID;
- product-ID en agentrol;
- titel en inhoud;
- `validFrom`;
- actor en wijzigingsreden;
- eventuele bron-ID's die als tekst in de inhoud zijn opgenomen.

### AgentMemoryVersionDetails

De historiequery voegt toe:

- root/geheugenitem-ID;
- afgeleid versienummer;
- status `ACTIVE`, `SUPERSEDED` of `RETRACTED`;
- `validFrom` en afgeleide `validUntil`;
- voorganger en opvolger;
- reden en actor van vervanging of intrekking.

De normale agentcontext bevat nooit de historie-DTO.

## Actuele projectie en peildatum

De module bewaart geen mutable dagsnapshot. Zij reconstrueert de actieve projectie uit de
append-only versies en intrekkingen:

- zonder `validAt`: de nieuwste niet-ingetrokken versie van iedere lijn;
- met `validAt`: de versie die op dat exacte tijdstip actief was;
- een lokale datum uit de UI: het einde van die productdag in de producttijdzone.

Een vervanging of intrekking werkt pas vanaf haar eigen `createdAt`. Daardoor kan de UI betrouwbaar
tonen wat een rol op een willekeurige dag als actueel geheugen had.

Historische inhoud is auditinformatie en nooit bindende instructie voor een huidige agenttaak.

## Geheugen bij het starten van een agent

Voor iedere agenttaak doet de procesruntime vóór promptopbouw:

1. bepaal vertrouwd `productId`, `AgentRoleKey`, processessie-ID en agenttaak-ID;
2. vraag `getActiveMemory(context)` op;
3. leg de exacte gelezen geheugenversie-ID's vast bij de `ProcessSession` en in de opaque input van
   de `AiTask`;
4. voeg alleen deze actuele eigen-rolitems als onvertrouwde contextdata aan de prompt toe;
5. start daarna pas de agent.

Hierdoor is later precies te reconstrueren met welk geheugen een antwoord tot stand kwam. Een
geheugenwijziging tijdens de taak geldt pas voor een volgende agenttaak, niet met terugwerkende kracht
voor de lopende inputmomentopname.

De overlegafhandeling volgt een afzonderlijke, beperkte route: zij maakt een vertrouwde
`MeetingExecutionContext`, vraagt één productbreed snapshot op en legt alle gebruikte rol- en
geheugenversies op de meeting en opaque `AiTask` vast. Alleen `MEETING.CONVERSE` en
`MEETING.SUMMARIZE` gebruiken dit snapshot. AI-uitvoering ziet nog steeds slechts complete opaque
data en kent de rollen of de uitzonderingsbevoegdheid niet.

Parallelle procesagents hebben ieder hun eigen rolgeheugen. Zij delen tijdelijke resultaten uitsluitend
via de expliciete handoffs van de processessie en lezen nooit elkaars permanente geheugen.

De aanvragende procesruntime neemt de geselecteerde eigen geheugenversies op in de complete opaque
taak voor [AI-uitvoering](ai-uitvoering.md). AI-uitvoering krijgt geen afzonderlijke rolparameter,
vraagt zelf geen geheugen op en begrijpt de inhoud niet. Daarmee blijft de rolgrens bij
Agentgeheugen en blijft de technische AI-queue volledig generiek.

## Contextlimiet

De module voorkomt stille truncatie. Per rol geldt een zichtbaar configureerbaar maximum voor het
aantal actieve items, de grootte per item en de totale actieve inhoud. Wanneer een toevoeging het
budget overschrijdt:

- wordt de wijziging afgewezen met een duidelijke fout;
- toont de UI welke rol haar budget heeft bereikt;
- moet de agent of Stakeholder bestaande items vervangen, samenvoegen of intrekken.

Zo weet iedere agent zeker dat alle actieve items van zijn rol daadwerkelijk in de context passen.
De service kiest nooit onzichtbaar alleen de nieuwste of semantisch meest waarschijnlijke helft.

## Betrouwbaarheid en instructievolgorde

Geheugen wordt in prompts altijd gemarkeerd als onvertrouwde contextdata. De inhoud kan geen
systeemregels, bevoegdheden of modulecontracten wijzigen. De volgorde is:

1. systeem- en veiligheidsregels;
2. publieke modulecontracten en harde procesinvarianten;
3. `ProductAssignment` en geldige `Decision`s;
4. actuele publieke productentiteiten en exacte bronversies;
5. het eigen actuele rolgeheugen;
6. tijdelijke agenthandoffs en ruwe bronnen.

Bij tegenspraak verliest geheugen altijd van de bronnen erboven. De agent hoort een vermoedelijk
onjuist geheugenitem te negeren en een `REPLACE`- of `RETRACT`-actie met reden voor te stellen.

Geheugen bevat nooit secrets, toegangstokens, persoonsgegevens die niet noodzakelijk zijn,
chain-of-thought of vrije instructies uit externe bronnen.

## Frontend

Het productscherm krijgt een onderdeel **Agentgeheugen**. De Stakeholder ziet eerst een lijst van
processen en agentrollen. Per rol toont de UI:

- weergavenaam en stabiele rolesleutel;
- actuele geheugenitems;
- gebruikt en beschikbaar contextbudget;
- toevoegen, vervangen en intrekken met verplichte reden;
- een peildatumkiezer;
- de volledige versiegeschiedenis per geheugenlijn;
- actor, wijzigingsreden en geldigheidsperiode;
- processessies en overleggen die een exacte geheugenversie hebben gelezen.

De frontend schrijft nooit rechtstreeks in de geheugentabellen. Zij gebruikt publieke queries en
commands en toont een conflict wanneer een item intussen door een agent of de Stakeholder is
vervangen. Replace- en retractcommands bevatten daarom altijd de verwachte actuele versie-ID.

Bij een gesloten overleg toont de frontend daarnaast per rol welke geheugenregels door de
notulenagent zijn toegevoegd, vervangen of ingetrokken, met meetinglink en reden.

## Overstappen van MVP naar uitgebreide implementatie

MVP-rollen en gespecialiseerde rollen hebben verschillende `AgentRoleKey`s en lezen dus niet
automatisch elkaars geheugen. Bij een overstap:

1. toont de UI het geheugen van de oude en nieuwe rollen;
2. kiest de Stakeholder of een expliciete migratietaak welke lessen naar welke nieuwe rol horen;
3. maakt de migratie nieuwe versie-1-items met verwijzing naar de oude bron-ID in de reden;
4. blijven oude rollengeheugens historisch zichtbaar maar worden niet aan nieuwe rollen gegeven.

Er bestaat geen impliciet overervingsmechanisme. Dat beschermt de strikte rolgrens en voorkomt dat
een gespecialiseerde agent irrelevante of te machtige instructies erft.

## Verschil met v1

V1 levert het bewezen append-only patroon:

- vervangen via een nieuwe versie met voorganger;
- intrekken via een tombstone;
- actuele projectie zonder oude inhoud;
- reconstructie op peildatum;
- volledige auditlijn met status, actor en reden.

V2 behoudt dat patroon, maar verandert de scope van **productbreed geheugen** naar
**product + agentrol**. Daarnaast wordt geheugen automatisch voor iedere agentrol beschikbaar,
krijgt de Stakeholder directe UI-commands en wordt de exacte gebruikte geheugenversie bij iedere
agenttaak vastgelegd.

## Invarianten

- Iedere geregistreerde agentrol heeft per product een eigen, permanent geheugen.
- Een gewone procesagent leest en wijzigt uitsluitend haar eigen rolgeheugen.
- Alleen de product-/overlegmodule met geldige `MeetingExecutionContext` kan een productbreed
  meetingsnapshot lezen of een productbrede notulenbatch schrijven.
- De procesruntime bepaalt product en rol; agentoutput kan die niet kiezen.
- Een agent ziet normaal alleen actuele versies.
- De Stakeholder kan via de UI iedere rol corrigeren.
- Vervangen en intrekken zijn append-only; oude versies blijven auditbaar.
- Een peildatum reconstrueert de toen actieve projectie.
- Iedere processessie en ieder overleg legt per `AiTask` de exact gelezen geheugenversie-ID's vast.
- Een mislukte agenttaak schrijft geen geheugen.
- Geheugen overschrijft nooit publieke productwaarheid of harde regels.
- Geen wijziging wordt stil afgekapt of zonder actor en reden opgeslagen.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Frontend](../stakeholder/frontend.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Overleggen met de Stakeholder](../stakeholder/overleggen.md)
- [AI-uitvoering](ai-uitvoering.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
