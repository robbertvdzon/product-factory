# Product Factory v2 — Kwaliteitsbewaking-API

Status: eerste ontwerp van het publieke modulecontract.

Dit document beschrijft uitsluitend de buitenkant van Kwaliteitsbewaking. Andere modules mogen niet
afhankelijk zijn van agents, testorganisatie, interne observaties of de volgorde van teststappen. De
volgende implementaties gebruiken hetzelfde contract:

- [Kwaliteitsbewaking — MVP](mvp.md): één Tester-agent voert de volledige
  kwaliteitssessie uit;
- [Kwaliteitsbewaking — uitgebreide implementatie](uitgebreid.md): vier
  gespecialiseerde rollen, parallel testen, testrotatie en leren per agentrol.

Beide zijn afzonderlijke Maven-implementatiemodules van dezelfde `quality-api`. De main-module
neemt bij build-time exact één implementatie op.

## Verantwoordelijkheid

Kwaliteitsbewaking test de werkende applicatie, verifieert losse opleveringen en controleert na de
laatste story of de volledige bevroren epic de bedoelde gebruikersverbetering heeft bereikt. Zij
publiceert reproduceerbare bugs en duurzame verificaties met bewijs. Ontbrekende epicdekking staat
in een verificatie en wordt via een command als nieuw planwerk aangevraagd.

De module is eigenaar en enige schrijver van `QualityWorkItem`, `Bug`, `Verification`,
`QualitySnapshot` en haar eigen `ProcessSession`. Hoe de module intern tot testbewijs en conclusies
komt, verschilt per implementatie.

Kwaliteitsbewaking maakt geen stories, wijzigt geen epicinhoud en bepaalt geen backlogvolgorde. Zij
kan alleen via publieke commands een bugfix, aanvullend planwerk, epicuitkomst of signaaluitkomst
doorgeven aan de module die de betreffende entiteit bezit.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

De scheduler of een handmatige UI-/REST-actie kan deze functie starten. Er kan modulebreed maximaal
één uitvoering tegelijk lopen. Een tweede handmatige aanroep krijgt een
`ProcessAlreadyRunning`-fout; een botsende geplande aanroep wordt als overgeslagen geregistreerd.
Alleen deze functie mag voor Kwaliteitsbewaking nieuwe taken bij
[AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md) aanvragen. Welke en hoeveel taken een sessie gebruikt, is een
implementatiedetail.

Een run claimt atomair een vaste momentopname van de `PENDING` `QualityWorkItem`s die bij de start
klaarstaan. Nieuwe verzoeken wachten op de volgende run. Naast deze gerichte queueopdrachten mag de
run zelf periodiek testwerk kiezen. Zonder queuewerk of periodiek werk eindigt zij als succesvolle
no-op.

De deterministische publieke functies zijn beperkt tot read-only queries en lifecycle-commands voor
eigen bugs:

```java
BugDetails getBug(BugId bugId);
List<VerificationDetails> findVerifications(VerificationTarget target);
QualitySnapshotDetails getCurrentQuality(ProductId productId);
List<QualitySnapshotDetails> getQualityHistory(ProductId productId, TimeRange range);
List<QualityWorkItemDetails> findQualityWorkItems(ProductId productId, WorkItemStatus status);
QualityWorkItemId requestStoryVerification(RequestStoryVerificationCommand command);
QualityWorkItemId requestEpicVerification(RequestEpicVerificationCommand command);
QualityWorkItemId requestBugfixRetest(RequestBugfixRetestCommand command);
QualityWorkItemId requestSignalInvestigation(RequestSignalInvestigationCommand command);
void linkBugfixStory(LinkBugfixStoryCommand command);
```

De vier `request...`-commands starten geen test en geen agent. Zij valideren de bron en voegen alleen
een idempotent `PENDING`-werkitem toe. `linkBugfixStory(...)` laat Productplanning alleen een story-ID
aan een bestaande uitvoerbare bug koppelen. Geen aanroeper krijgt toegang tot de kwaliteitsrepository.

## QualityWorkItem: de queuegrens

Een `QualityWorkItem` bevat work-item-ID, product-ID, type, bron-ID en -versie, doelomgeving,
prioriteit, idempotentiesleutel, status, claim en eventuele foutinformatie. De typen zijn:

| Type | Normale aanvrager | Betekenis |
|---|---|---|
| `VERIFY_STORY` | Productplanning na een relevante storyoplevering | toets storycriteria en regressierisico |
| `VERIFY_EPIC` | Productplanning nadat alle stories en bugfixes van die epic `DONE` én actueel geslaagd geverifieerd zijn | toets de volledige bevroren epic |
| `RETEST_BUGFIX` | Productplanning na oplevering van een bugfixstory | herhaal reproductie en aangrenzende controles |
| `INVESTIGATE_USER_SIGNAL` | product-/overlegmodule | onderzoek een gemeld kwaliteitsprobleem |

De statussen zijn `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED` en `FAILED`. De commandhandler mag een
prioriteit overnemen, maar de latere kwaliteitsrun bepaalt zelf de testaanpak. De queue hoort bij
Kwaliteitsbewaking; Productontwerp bevat geen testqueue.

## Interface met andere modules en services

Kwaliteitsbewaking gebruikt publieke Maven-API-modules en hun read-only DTO's. DTO's zijn geen
database-entiteiten. Browser-, log- en testclients zijn interne adapters. Spring Modulith
structureert alleen de binnenkant van de gekozen Kwaliteitsbewaking-implementatie.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `ProductAssignmentDetails` | productmodule | productgrenzen en publieke Git-URL van het product |
| `TestableProductDetails` | productmodule | omgevingen, routes, toegestane accounts, databereik en testgrenzen |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote blijvende privacy-, veiligheids- of productgrenzen die het testen beïnvloeden |
| `EpicDetails` | Productontwerp | bevroren scope, UX, succescriteria en status van de geclaimde versie |
| `StoryDetails` | Productplanning | type, storyversie, status, oplevergegevens, acceptatiecriteria en zelfstandige UX |
| `UserSignalDetails` | productmodule | oorspronkelijke melding plus actuele status en resultaatkoppelingen; categorie `QUALITY_CONCERN` vraagt extra onderzoek |
| `QualityWorkItem` | Kwaliteitsbewaking | duurzame gerichte testopdracht die de run claimt |
| `AgentMemoryItemDetails` | Agentgeheugen | alleen de actuele geheugenitems van de agentrol die op dat moment wordt uitgevoerd |
| `AiJobConfigurationDetails` | AI-uitvoering (`settings`) | actuele provider en model voor het soort kwaliteitsjob; bevroren op iedere nieuwe taak |
| `AiTaskResultDetails` | AI-uitvoering | opaque resultaat van een eerder door deze processessie aangevraagde taak |

De module leest daarnaast eigen bugs en testhistorie. Iedere sessie legt de gebruikte
contractversies, exact gelezen geheugenversies en exacte geteste omgeving vast. Permanent leren
loopt uitsluitend via de publieke API van [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md); een agent leest en
wijzigt alleen geheugen van haar eigen vertrouwd geconfigureerde rol.

Een processessie bewaart haar AI-taak-ID's en keert met `WAITING_FOR_AI` terug zonder thread of lock
vast te houden. Een volgende run hervat dezelfde sessie. Zolang resultaten ontbreken, worden geen
nieuwe duplicerende taken aangemaakt.

Kwaliteitsbewaking mag de publieke Git-URL uit de productopdracht uitchecken en code, tests en
documentatie read-only gebruiken voor testselectie, regressierisico en uitleg. Zij commit en pusht
nooit. Code is context en geen bewijs dat gedrag werkt; de gedeployde applicatie en het verzamelde
testbewijs blijven leidend. Waar bekend legt
de verificatie vast welke commit is bekeken en welke productversie werkelijk is getest.

### Eigen output en downstream effect

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `BugDetails` | read-only weergave van een aantoonbare afwijking | werkelijk en verwacht gedrag, reproduceerstappen, omgeving, bewijs, impact, ernst, status en bron-signaal-ID's |
| `VerificationDetails` | read-only weergave van een story-, epic- of signaalcontrole | doeltype en -versie, uitkomst, omgeving, controles, bewijs, blokkade, ontbrekende dekking en vervolgkoppelingen |
| `QualitySnapshotDetails` | read-only kwaliteitsbeeld en historie | tijdstip, omgeving, productversie, onderzochte gebieden, dekking, open bugs per ernst, verificatie-uitkomsten, risico's en bron-ID's |
| `QualityWorkItemDetails` | read-only inzicht in de kwaliteitsqueue | type, doelversie, status, claim, resultaat en fout; geen wijzigbaar requestobject |
| `ProcessSession` | operationele historie van de sessie | sessie-ID, product-ID, implementatie-ID en -versie, inputversies, AI-taak-ID's, publicatie-ID's en wacht- of eindstatus |
| `PlanningWorkItem` bij Productplanning | downstream effect van een gericht herstelcommand; Productplanning maakt en bezit dit object | type bugfix of epicgat, exact bron-ID en -versie, bewijsreferentie en idempotentiesleutel |

Kwaliteitsbewaking schrijft `ProcessSession` uitsluitend voor zijn eigen sessies. De
scheduler roept alleen de procesfunctie aan; scheduler en frontend wijzigen het sessieresultaat niet.

Alleen Kwaliteitsbewaking schrijft `Bug` en `Verification`. Zij meldt een gerichte storyuitkomst via
`recordStoryVerification(...)` en vraagt Productplanning via `requestBugfix(...)` of
`requestEpicGapPlanning(...)` om vervolgwerk. Zij vraagt Productontwerp via
`recordEpicVerification(...)` om een epicuitkomst vast te leggen en de productmodule via
`recordSignalInvestigation(...)` om een gebruikerssignaal bij te werken. Geen ontvangende module
kan de onderliggende verificatie of het bewijs veranderen.

## Kwaliteitsbeeld en historie

Een `QualitySnapshot` is een onveranderlijke momentopname van wat op dat moment aantoonbaar bekend
is over de productkwaliteit. Kwaliteitsbewaking maakt na iedere afgeronde processessie waarin
daadwerkelijk is getest precies één nieuwe snapshot. Een no-op-sessie maakt geen duplicaat en een
oude snapshot wordt nooit bijgewerkt.

Een snapshot bevat geen verborgen totaalscore. Hij toont afzonderlijk:

- geteste omgeving en productversie;
- belangrijke routes en gebieden die recent zijn onderzocht;
- gebieden waarvan de controle verouderd is of ontbreekt;
- open bugs per ernst en relevante veranderingen sinds de vorige snapshot;
- aantallen en uitkomsten van story-, epic- en signaalverificaties;
- actuele risico's, blokkades en de bron-ID's waarop het beeld is gebaseerd.

`getCurrentQuality(...)` retourneert de nieuwste snapshot. `getQualityHistory(...)` retourneert de
snapshots in de gevraagde periode, zodat de frontend en Productontwerp per kwaliteitsdimensie kunnen
zien wat verbetert of verslechtert. Bugs en onveranderlijke verificaties blijven de inhoudelijke
bron; de snapshot is het controleerbare historische beeld daarvan op één moment.

## Een kwaliteitszorg uit een overleg

Wanneer de Stakeholder aangeeft dat een onderdeel mogelijk niet goed werkt of extra aandacht nodig
heeft, registreert de productmodule dit als `UserSignal`. De optionele categorie
`QUALITY_CONCERN` helpt Kwaliteitsbewaking bij de testagenda, maar maakt van de melding geen opdracht
met een vooraf bepaald resultaat.

Na registratie roept de product-/overlegmodule bij zo'n signaal idempotent
`requestSignalInvestigation(...)` aan. Dat zet alleen een `INVESTIGATE_USER_SIGNAL`-workitem in de
kwaliteitsqueue. De melding bepaalt dus wel wat onderzocht moet worden, maar niet wat de uitkomst is.

De Stakeholder schrijft dit databaseobject niet rechtstreeks. De frontend of overlegmodule voert een
command uit op de productmodule; die bewaart de oorspronkelijke melding daarna onveranderlijk.
Tijdens een latere run leest Kwaliteitsbewaking `UserSignalDetails`, bewaart het onderzoek als `Verification` en roept
`recordSignalInvestigation(...)` op de productmodule aan. Alleen de productmodule wijzigt status en
resultaatkoppelingen op `UserSignal`.

Een signaalonderzoeksresultaat bevat minimaal:

- stabiel resultaat-ID, product-ID, signaal-ID en exact signaalversienummer;
- resultaat **Bevestigde bug**, **Geen probleem gevonden**, **Meer bewijs nodig**, **Duplicaat**,
  **Buiten testscope** of **Kwaliteitspatroon gevonden**;
- uitleg, uitgevoerde controles, geteste omgeving en bewijs;
- eventuele koppeling naar `Bug`, een nieuw `UserSignal` met categorie `QUALITY_PATTERN` of het duplicaatsignaal;
- processessie-ID en onderzoekstijdstip.

Het command zet de actuele signaalstatus en koppelt het verificatie-ID. Daardoor kan de frontend op
één `UserSignalDetails` tonen wat is onderzocht en wat daaruit kwam, terwijl bronmelding en
testbewijs ieder hun eigen eigenaar houden.

## Storyverificatie

Een story- of bugfixoplevering krijgt:

- **Geslaagd** — afgesproken gedrag werkt in de bedoelde omgeving;
- **Afgekeurd** — gedrag wijkt aantoonbaar af;
- **Geblokkeerd** — controle is door omgeving, toegang of ontbrekende informatie niet mogelijk.

Bij **Afgekeurd** publiceert Kwaliteitsbewaking zo nodig een bug. Het herschrijft de story niet.

## Epicverificatie

Wanneer een storyverificatie of bugfixhertest is gepubliceerd, meldt Kwaliteitsbewaking de exacte
uitkomst via `recordStoryVerification(...)` aan Productplanning. Dat command start geen agent.
Productplanning vraagt pas epicverificatie aan wanneer alle niet-geannuleerde stories en bugfixes
van de epic `DONE` zijn, iedere actuele controle is geslaagd en geen herstelwerk of open bug resteert.
Een `CANCELLED` epic krijgt geen nieuwe epicverificatie. De controle gebruikt exact de door
Productontwerp bevroren `EpicDetails` en beoordeelt:

- de volledige gebruikersroute, niet alleen losse schermen;
- alle relevante UX-toestanden en overgangen;
- de expliciete scope in en uit;
- toegankelijkheid, privacy en andere kwaliteitsgrenzen;
- samenhang tussen de geleverde stories;
- de succescriteria en de merkbare verbetering voor de gebruiker.

De uitkomst is:

- **Geslaagd** — de gebruikersverbetering is aantoonbaar bereikt;
- **Onvolledig** — gedrag binnen scope of UX ontbreekt;
- **Niet aantoonbaar** — de uitvoering lijkt compleet, maar bewijs of meting ontbreekt;
- **Geblokkeerd** — de controle kan niet verantwoord worden afgerond;
- **Niet geslaagd** — alles werkt zoals ontworpen, maar het bedoelde gebruikersresultaat is niet
  bereikt.

Kwaliteitsbewaking schrijft eerst een onveranderlijke `Verification` en roept daarna
`recordEpicVerification(...)` op Productontwerp aan. Productontwerp controleert de epicversie en is
de enige schrijver van de epicstatus.

Het vervolg per uitkomst is:

| Uitkomst | Epic bij Productontwerp | Bericht aan Productplanning |
|---|---|---|
| **Geslaagd** | **Geslaagd** (`COMPLETED`) | geen |
| **Onvolledig** | terug naar **Actief** | `requestEpicGapPlanning(...)` met verificatie-ID |
| bouwfout in uitgevoerd storygedrag | terug naar of blijft **Actief** | `requestBugfix(...)` met bug- en verificatie-ID |
| **Niet aantoonbaar** | blijft **Controleren** | geen; Kwaliteitsbewaking plant later nieuw bewijswerk |
| **Geblokkeerd** | blijft **Controleren** | geen, tenzij aantoonbaar ontwikkelwerk nodig is |
| **Niet geslaagd** | **Niet geslaagd** (`NOT_SUCCESSFUL`) | geen; Productontwerp kan vanuit het resultaat een vervolgepic onderzoeken |

Productplanning krijgt dus geen generiek verificatieresultaat terug. Alleen een gericht plancommand
betekent dat zij nieuw ontwikkelwerk moet vormen. Gedrag binnen de bevroren scope wordt een
aanvullende story of bugfixstory binnen dezelfde epic. Alleen een nieuwe wens buiten scope of een
onjuiste productaanname kan later tot een nieuwe vervolgepic leiden.

## Bug, ontbrekende dekking of nieuwe productkans

Kwaliteitsbewaking classificeert een ontbrekend of onjuist resultaat vóór publicatie:

| Situatie | Publicatie | Vervolg |
|---|---|---|
| Gedrag stond in een uitgevoerde story maar is verkeerd gebouwd | `Bug` plus `Verification` | Kwaliteitsbewaking vraagt Productplanning om een bugfix |
| Gedrag viel duidelijk binnen de bevroren epic, maar er bestond nooit een story voor | `Verification` met ontbrekende dekking | Kwaliteitsbewaking vraagt Productplanning om aanvullend werk |
| Alles is geleverd, maar de gebruikersverbetering is nog niet bewezen | `Verification` met **Niet aantoonbaar** | epic blijft op **Controleren** |
| Alles werkt zoals ontworpen, maar de productaanname blijkt onjuist | `Verification` met **Niet geslaagd** en een `UserSignal` van categorie `QUALITY_PATTERN` | Productontwerp registreert de uitkomst en leert |
| Gewenst gedrag valt buiten de bevroren scope | `UserSignal` | Productontwerp kan een vervolgepic maken |

Kwaliteitsbewaking maakt in geen van deze gevallen zelf een story.

## Bugcontract en levenscyclus

Een gepubliceerde bug bevat minimaal:

- stabiel bug-ID, product-ID en versie;
- korte titel en gebruikersimpact;
- werkelijk en verwacht gedrag;
- reproduceerstappen en vereiste uitgangssituatie;
- geteste omgeving, productversie en tijdstip;
- screenshot, log, netwerkspoor of ander bewijs;
- ernst P0, P1, P2 of P3 met reden;
- relatie met story, epicversie, oplevering en soortgelijke bugs;
- eventuele bron-gebruikerssignalen;
- herstelstatus.

De herstelstatus is:

- **Nieuw** — bevinding bestaat intern maar is nog niet reproduceerbaar;
- **Uitvoerbaar** — reproduceerbaar en compleet genoeg voor een bugfix;
- **Gepland** — Productplanning heeft er een bugfixstory met status `TODO` voor gepubliceerd;
- **In herstel** — de bugfixstory heeft status `IN_PROGRESS`;
- **Hertesten** — de bugfixstory heeft status `DONE` en de fix is opgeleverd;
- **Opgelost** — de fix is in de juiste omgeving goedgekeurd;
- **Heropend** — de afwijking bestaat nog of is teruggekomen;
- **Ongeldig** — geen productafwijking, met zichtbare reden.

Productplanning koppelt een bugfixstory via `linkBugfixStory(...)`. Kwaliteitsbewaking kan
**Gepland**, **In herstel** en **Hertesten** daarna uit `StoryDetails` afleiden en blijft zelf de enige
schrijver van de duurzame bugstatus. Na een geslaagde bugfixhertest zet zij de bug op **Opgelost** en
maakt zij intern een nieuw idempotent `VERIFY_STORY`-workitem voor de oorspronkelijke story tegen de
nieuwe productversie. Daardoor vervangt een oude afgekeurde controle niet stilzwijgend het bewijs:
de oorspronkelijke storycriteria worden na de fix opnieuw actueel aangetoond voordat de epic naar
`VERIFYING` kan.

## Ontbrekende epicdekking

Een dekkingsgat is geen afzonderlijke entiteit meer. Een epicverificatie kan één of meer gestructureerde
dekkingsgaten bevatten met scope- of UX-verwijzing, gebruikersimpact, ontbrekend gedrag en bewijs.
Kwaliteitsbewaking roept `requestEpicGapPlanning(...)` aan met het verificatie-ID. Productplanning
maakt tijdens een processessie de aanvullende stories; een latere verificatie toont of het gat is
opgelost. Zo blijft het bewijs historisch intact zonder een tweede lifecycle naast epic en story.

## Wanneer Kwaliteitsbewaking draait

Gericht werk wordt gequeue'd door:

- Productplanning na een story-, bugfix- of volledige epicoplevering;
- de product-/overlegmodule na een kwaliteitszorg of relevant gebruikerssignaal.

Daarnaast kan de scheduler een run starten voor dagelijkse kernroutes, een verouderd kwaliteitsbeeld
of een periodieke kwaliteitscontrole. Een queuecommand is een snelle databasebewerking; alleen de latere
`runProcessSession()` mag nieuwe AI-taken aanvragen. De backloggrootte speelt hierbij geen rol.

## Fouten, hervatten en idempotentie

- Een storyverificatie is uniek voor story-ID, storyversie, oplevering, geteste productversie en
  omgeving.
- Een epicverificatie is uniek voor epic-ID, epicversie en geteste productversie.
- Een idempotente herhaling voor exact hetzelfde doel maakt geen duplicaat en wijzigt een reeds
  gepubliceerde verificatie niet.
- Een technische testfout wordt apart geregistreerd en niet als productbug gepubliceerd.
- Een sessie kan na een verlopen claim worden hervat met dezelfde inputmomentopname.
- Gedeeltelijk bewijs blijft intern tot reproductie en privacycontrole zijn afgerond.

## Eisen aan iedere implementatie

De MVP en iedere latere implementatie moeten garanderen dat:

- zij dezelfde `quality-api` implementeert en andere capabilities alleen via hun API-module
  gebruikt;
- iedere nieuwe `ProcessSession` de exacte `implementationId` en `implementationVersion` vastlegt;
- alleen `runProcessSession()` voor Kwaliteitsbewaking nieuwe AI-taken aanvraagt;
- maximaal één uitvoering tegelijk loopt en een wachtende sessie geen technische lock vasthoudt;
- ieder geclaimd workitem `DONE`, `BLOCKED` of `FAILED` wordt;
- een onbereikbare of kapotte testomgeving niet als productbug wordt gepubliceerd;
- iedere bug reproduceerbaar is en controleerbaar bewijs bevat;
- iedere verificatie exacte doel-, opleverings-, omgevings- en bronversies bevat;
- ontbrekende epicdekking binnen de bevroren scope wordt bewezen;
- ieder gebruikerssignaalonderzoek een expliciete uitkomst of blokkade krijgt;
- de eigen procesruntime iedere agent alleen het actuele geheugen van haar eigen rol geeft en de exact gelezen
  geheugenversies vastlegt;
- iedere AI-taak een vaste provider, model en configuratieversie heeft en via AI-uitvoering loopt;
- publieke output pas na contract-, privacy- en geheimencontrole verschijnt;
- iedere sessie waarin daadwerkelijk is getest precies één nieuwe onveranderlijke
  `QualitySnapshot` maakt;
- publicaties en vervolgcommands atomair of idempotent herstelbaar zijn.

## Wanneer een sessie klaar is

Een inhoudelijke sessie is klaar wanneer:

- iedere geclaimde opdracht en gekozen periodieke controle een resultaat of expliciete blokkade heeft;
- iedere publieke bug reproduceerbaar en van bewijs voorzien is;
- ieder dekkingsgat aantoonbaar binnen de bevroren epic valt en niet door een story wordt gedekt;
- iedere verificatie naar exacte epic-, story-, opleverings- en omgevingsversies verwijst;
- ieder onderzocht gebruikerssignaal naar exact signaal-ID en -versie verwijst en een zichtbaar
  onderzoeksresultaat of expliciete blokkade heeft;
- het kwaliteitsbeeld uit de gevalideerde resultaten is opgebouwd;
- na daadwerkelijk testwerk precies één nieuwe `QualitySnapshot` is opgeslagen;
- publicaties atomair en geversioneerd beschikbaar zijn;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.

## Gerelateerde documenten

- [Kwaliteitsbewaking — MVP](mvp.md)
- [Kwaliteitsbewaking — uitgebreide implementatie](uitgebreid.md)
- [Productontwerp-API](../productontwerp/api.md)
- [Productplanning-API](../productplanning/api.md)
- [Software Factory-dispatcher](../software-factory-dispatcher.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
