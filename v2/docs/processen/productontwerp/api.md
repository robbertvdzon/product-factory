# Product Factory v2 — Productontwerp-API

Status: eerste ontwerp van het publieke modulecontract.

Dit document beschrijft uitsluitend de buitenkant van de module Productontwerp. Andere modules
mogen niet afhankelijk zijn van agents, prompts, interne entiteiten of de volgorde van interne
stappen. Daardoor kunnen de volgende implementaties hetzelfde contract gebruiken:

- [Productontwerp — MVP](mvp.md): één agent en zo weinig mogelijk interne werking;
- [Productontwerp — uitgebreide implementatie](uitgebreid.md): gespecialiseerde
  agents, onderzoek en leren per agentrol.

De MVP kan later door de uitgebreide implementatie worden vervangen zonder Productplanning,
Kwaliteitsbewaking, de frontend of het datacontract aan te passen. Beide zijn afzonderlijke Maven-
implementatiemodules van hetzelfde `design`-contract in `product-factory-api`; de main-module neemt bij build-time exact
één van beide op.

## Verantwoordelijkheid

Productontwerp zet relevante productinformatie om in complete, behapbare epicdefinities. Iedere
gepubliceerde epic beschrijft één eenduidige gebruikersverbetering en bevat duidelijke scope, het
benodigde UX-ontwerp en toetsbare succescriteria.

Productontwerp maakt geen stories en beheert geen backlog. De module is eigenaar en enige schrijver
van de complete `Epic`: inhoud, UX, versie en levenscyclusstatus. Andere modules gebruiken alleen de
publieke commands en read-only queries en krijgen nooit toegang tot de epicrepository.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession(ProductId productId);
```

De scheduler of een bevoegde handmatige UI-/REST-actie kan deze functie voor één product starten.
Per product kan maximaal één onafgeronde logische Productontwerp-sessie bestaan, ook wanneer die
`WAITING_FOR_AI` of `BLOCKED` is; verschillende producten mogen parallel worden verwerkt. Een
handmatige aanroep hervat zo'n niet-actief wachtende sessie. Alleen wanneer voor hetzelfde product
op dat moment al een functiecall uitvoert, volgt `ProcessAlreadyRunning`, bij REST bijvoorbeeld HTTP
409. Een botsende schedulerrun met zo'n actieve call wordt als overgeslagen geregistreerd. Zonder
zinvol werk eindigt de functie als succesvolle no-op.

Alleen `runProcessSession(productId)` mag voor Productontwerp nieuwe taken bij
[AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md) aanvragen. Welke en hoeveel taken een sessie gebruikt, is een
implementatiedetail. De laptopworker voert ze later asynchroon uit en kent Productontwerp niet.

Daarnaast heeft de module deze deterministische command- en query-interface:

```java
EpicDetails getEpic(EpicId epicId);
List<EpicDetails> findAvailableEpics(ProductId productId);
List<EpicDetails> findActiveEpics(ProductId productId);
void claimEpicForPlanning(ClaimEpicForPlanningCommand command);
void markEpicActive(MarkEpicActiveCommand command);
void markEpicReadyForVerification(MarkEpicReadyForVerificationCommand command);
void recordEpicVerification(RecordEpicVerificationCommand command);
void withdrawEpic(WithdrawEpicCommand command);
void cancelEpic(CancelEpicCommand command);
```

Deze functies starten geen agents. Ze valideren bevoegdheid, verwachte versie en toegestane
statusovergang en schrijven de wijziging atomair op de eigen `Epic`. Geen command biedt vrije
schrijftoegang tot epicinhoud of UX.

## Interface met andere modules en services

Procesmodules gebruiken alleen de betreffende publieke capabilitypackages in
`product-factory-api`. Queries leveren read-only DTO's uit die gedeelde API-module; een DTO is geen tweede
database-entiteit. Commands drukken een concrete domeinovergang uit. Productontwerp schrijft
uitsluitend zijn eigen tabellen. Spring Modulith structureert alleen de binnenkant van de gekozen
Productontwerp-implementatie.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `ProductAssignmentDetails` | productmodule | doelgroep, productdoel, harde grenzen en publieke Git-URL van het product |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote, blijvende Stakeholder- en Factorybesluiten die het ontwerp begrenzen |
| `UserSignalDetails` | productmodule | oorspronkelijke feedback plus actuele status, uitkomst en resultaatkoppelingen |
| `StoryDetails` | Productplanning-query | wat Software Factory heeft opgeleverd en welke story bij een epic hoort |
| `VerificationDetails` | Kwaliteitsbewaking-query | of de bedoelde gebruikersverbetering is bereikt en welk bewijs daarbij hoort |
| `QualitySnapshotDetails` | Kwaliteitsbewaking-query | huidig kwaliteitsbeeld en historische ontwikkeling van dekking, bugs, risico's en verificaties |
| `TestableProductDetails` | productmodule | acceptatie- en eventueel productieomgeving, veilige routes, testaccounts en toegangsgrenzen |
| `AgentMemoryItemDetails` | Agentgeheugen | alleen de actuele geheugenitems van de agentrol die op dat moment wordt uitgevoerd |
| `AiJobConfigurationDetails` | AI-uitvoering (`settings`) | actuele provider en model voor het soort agenttaak; deze waarden worden op iedere nieuwe taak bevroren |
| `AiTaskResultDetails` | AI-uitvoering | opaque resultaat van een eerder door deze processessie aangevraagde taak |

Voor iedere gebruikte publicatie legt de module bron-ID en bronversie vast. Dezelfde versie wordt
niet tweemaal als nieuwe input behandeld.

Een processessie die op AI wacht, bewaart haar taak-ID's en status `WAITING_FOR_AI` en geeft de
aanroep terug. Een volgende geplande of handmatige `runProcessSession(productId)` hervat dezelfde
sessie voor dat product. Als
de resultaten nog ontbreken, blijft zij zonder nieuwe taken aan te maken wachten.

Bij een inhoudelijke sessie mag Productontwerp de publieke Git-URL uit de productopdracht uitchecken
en broncode, tests en documentatie read-only bekijken. Productontwerp commit en pusht nooit. De
gelezen commit-SHA kan als bronverwijzing bij de sessie of epic worden vastgelegd; ruwe
repository-inhoud steekt de modulegrens niet over.

Voor een agenttaak bevriest Productontwerp de publieke Git-URL en exacte commit-SHA. De laptopworker
checkt die SHA zelf uit in de tijdelijke Dockeromgeving van de taak. Git-code, documentatie en tekst
uit de bekeken applicatie zijn onvertrouwde contextdata en kunnen nooit de vaste taakopdracht,
veiligheidsgrenzen of toegestane commands wijzigen.

Productontwerp mag ook de werkende applicatie via `TestableProductDetails` bekijken. Acceptatie is
de voorkeursomgeving voor handelingen die data kunnen veranderen. Productie wordt alleen read-only
of met expliciet veilige testaccounts gebruikt. Secrets staan nooit in het DTO.

Een `UserSignalDetails` is een aanwijzing en geen opdracht. De oorspronkelijke tekst blijft
onveranderlijk. Productontwerp registreert verwerking via een betekenisvol command op de
productmodule en krijgt nooit directe schrijftoegang tot het signaal.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `EpicDetails` | read-only weergave van één complete gebruikersverbetering | versie, status, probleem, doelgroep, uitkomst, scope in/uit, bewijs, UX, succescriteria, risico's, afhankelijkheden en bron-signaal-ID's |
| `ProcessSession` | opgeslagen operationele historie van de sessie | sessie-ID, product-ID, implementatie-ID en -versie, gebruikte inputversies, AI-taak-ID's, publicatie-ID's, wacht- of eindstatus en blokkade |

De enige inhoudelijke overdracht naar Productplanning is `EpicDetails`. Interne analyses,
concepten en agentuitvoer steken de modulegrens niet over. Permanent leren loopt uitsluitend via
de publieke API van [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md): een agent kan alleen geheugen van zijn eigen
rol lezen en wijzigen. Operations en frontend lezen de sessie via het read-only
`ProcessSessionDetails`-contract.

Alleen wanneer tijdens Productontwerp een afzonderlijke grote, blijvende keuze ontstaat die
meerdere toekomstige processessies begrenst, kan de module binnen de productopdracht en geldige
besluiten `createDecision(...)` op het [Besluitenregister](../../gedeelde-modules/besluitenregister.md) aanroepen. Een epic,
signaalafhandeling of normale ontwerpkeuze is geen besluit.

## Samenwerking rond een epic

Alleen Productontwerp schrijft de `Epic`. Productplanning claimt een exacte versie via
`claimEpicForPlanning(...)`; dat command bevriest die versie atomair. Kwaliteitsbewaking registreert
de epicverificatie-uitkomst via `recordEpicVerification(...)`. Geen van beide kan inhoud of UX
wijzigen.

Na publicatie van een `AVAILABLE` epic stuurt Productontwerp geen planningsrequest. Productplanning
heeft een eigen schedule en zoekt tijdens een latere processessie zelf naar beschikbare epics.

`markEpicReadyForVerification(...)` is een snelle statusovergang naar `VERIFYING`. Productplanning
roept dit normaal pas aan wanneer alle niet-geannuleerde stories en bugfixes zijn opgeleverd én hun
actuele storyverificatie of hertest is geslaagd. Na een door Software Factory geannuleerde story mag
zij dit ook aanroepen zodra al het overige werk klaar en actueel geslaagd is; de complete controle
beoordeelt dan of de feitelijke applicatie de epic ondanks de annulering voldoende afdekt. Daarna roept Productplanning
`requestEpicVerification(epicId, epicVersion, ...)` op Kwaliteitsbewaking aan. Dat command start geen
agent, maar maakt daar een `QualityWorkItem`.

## Epiccontract

Een beschikbare epicdefinitie bevat minimaal:

- stabiel epic-ID, product-ID en versienummer;
- de gebruiker en situatie waarvoor de epic bedoeld is;
- het aantoonbare probleem of de kans;
- één merkbare gewenste gebruikersverbetering;
- expliciete scope: wat hoort er wel en niet bij;
- relatie met productdoel en geldige besluiten;
- bewijs, bronnen, aannames en relevante tegenspraak;
- het volledige actuele UX-ontwerp;
- hoofdroute, alternatieve routes en schermtoestanden;
- lege, laad-, fout- en uitzonderingssituaties;
- toegankelijkheids-, privacy- en kwaliteitsgrenzen;
- bekende technische risico's en afhankelijkheden;
- succescriteria en hoe Kwaliteitsbewaking die kan toetsen;
- waarom de epic behapbaar genoeg is om in kleine stories te verdelen.

Productontwerp beschrijft geen storylijst. Een mogelijke slice mag de behapbaarheid uitleggen, maar
is geen vooraf geschreven backlog.

Ieder succescriterium benoemt ook de observeerbare bron waarmee Kwaliteitsbewaking het kan toetsen,
zoals gedrag op een geconfigureerde omgeving of een expliciet beschikbare meetbron. Een abstract
langetermijndoel zonder tijdens verificatie beschikbaar bewijs kan richting geven, maar mag niet de
enige voorwaarde zijn om deze epic af te ronden.

## Versies en bevriezing

Iedere gepubliceerde epicversie is inhoudelijk onveranderlijk.

Zolang een epic `AVAILABLE` is, mag Productontwerp:

- een nieuwe versie publiceren;
- de vorige versie `SUPERSEDED` maken;
- de epic via `withdrawEpic(...)` intrekken met status `WITHDRAWN` en een zichtbare reden.

Zodra `claimEpicForPlanning(...)` een exact epic-ID en versienummer heeft gekozen:

- wordt die versie het vaste uitvoerings- en testcontract;
- kan dezelfde gekozen epic niet inhoudelijk worden herzien;
- blijven scope en UX ongewijzigd;
- wordt nieuwe kennis eventueel een nieuwe vervolgepic;
- kan stoppen alleen via de daarvoor bedoelde lifecyclecommands.

Bij `cancelEpic(...)` legt Productontwerp eerst een duurzame interne annuleringsoperatie vast en
roept het idempotent `cancelStoriesForEpic(...)` op Productplanning aan. Productplanning bewaart ook
zonder bestaande stories een annuleringsmarker, zodat een wachtende Planner later niets meer kan
publiceren. Na bevestiging zet Productontwerp de epic op `CANCELLED`. De herstelbare operatie maakt
deze volgorde na een crash af.

Productplanning annuleert direct alle niet-gereserveerde `TODO`-stories. Een `IN_PROGRESS` story is
extern gestart en loopt normaal af. Bij een alleen lokaal gereserveerde story bepaalt de dispatcher
eerst met dezelfde idempotentiesleutel of Software Factory haar al kent. Alleen bestaand extern werk
loopt door; wanneer extern aantoonbaar nog niets bestaat, maakt
`revalidateDispatchReservation(...)` de reservering ongeldig en de story `CANCELLED`. Een
ingetrokken nog niet gekozen epic heeft geen stories.

Productontwerp controleert status en verwacht versienummer in dezelfde transactie. Zo kan een
langlopende processessie nooit een inmiddels geclaimde epic overschrijven.

## Publieke levenscyclus van een epic

```text
AVAILABLE ──claim──> IN_PLANNING ──stories gepubliceerd──> ACTIVE
    ├──nieuwere versie──> SUPERSEDED                         ├──klaar voor complete beoordeling──> VERIFYING
    └──intrekken────────> WITHDRAWN                          │                       │
                                                             │                       ├──geslaagd──────> COMPLETED
                                                             │                       ├──niet geslaagd──> NOT_SUCCESSFUL
                                                             │                       └──herstel nodig──> ACTIVE
                                                             └──annuleren──────────> CANCELLED
```

Meerdere epics mogen tegelijkertijd in planning, actief of in verificatie zijn.

Een epic met `NOT_SUCCESSFUL` blijft als historisch eindresultaat bestaan en wordt niet heropend.
Een latere Productontwerp-sessie kan uit de verificatie een nieuwe vervolgepic maken. Een
`CANCELLED` epic krijgt geen complete epicverificatie meer. Bij een epicuitkomst `NEEDS_WORK` zet
`recordEpicVerification(...)` de epic van `VERIFYING` terug naar `ACTIVE`; gerichte bugs en
dekkingsgaten uit de verificatie blijven binnen dezelfde bevroren epic. Bij `BLOCKED` blijft de epic
`VERIFYING` terwijl Kwaliteitsbewaking het workitem later opnieuw probeert.

## Wanneer Productontwerp draait

Productontwerp heeft geen inkomende werkqueue. Alleen de scheduler of een bevoegde handmatige
aanroep start `runProcessSession(productId)`. De module bepaalt zelf of input voor dat product om
ontwerpwerk vraagt. Mogelijke
aanleidingen zijn:

- een gewijzigde productopdracht of een nieuw of gewijzigd besluit;
- een nieuw of bijgewerkt gebruikerssignaal;
- een nieuwe epicverificatie of een structureel kwaliteitspatroon;
- een periodieke controle van het product en beschikbare epics.

De toestand van de backlog is geen startsein. Als er niets zinvols te doen is, is een no-op correct.

## Fouten en idempotentie

- Publieke output verschijnt alleen na complete contractvalidatie.
- Een publicatie verwijst naar de exact gebruikte inputversies.
- Dezelfde sessiedoelstelling en bronversies publiceren niet tweemaal dezelfde epicversie.
- Een verlopen technische claim kan veilig opnieuw worden opgepakt.
- Een epic die tijdens een sessie wordt geclaimd, kan niet meer door die sessie worden herzien.
- Input die tijdens een sessie verandert, wordt pas in een volgende sessie verwerkt.
- Commands zijn idempotent en controleren de verwachte versie.

## Eisen aan iedere implementatie

De MVP en iedere latere implementatie moeten garanderen dat:

- zij hetzelfde publieke `design`-contract implementeert en andere capabilities alleen via
  `product-factory-api` gebruikt;
- iedere nieuwe `ProcessSession` de exacte `implementationId` en `implementationVersion` vastlegt;
- alleen `runProcessSession(productId)` voor Productontwerp nieuwe AI-taken aanvraagt;
- maximaal één onafgeronde logische sessie per product bestaat; verschillende producten mogen
  parallel lopen en een wachtende sessie houdt geen technische lock vast;
- iedere gepubliceerde epic aan het volledige Epiccontract voldoet;
- iedere epic zelfstandig door Productplanning kan worden begrepen;
- geen stories in Productontwerp worden gemaakt;
- een gekozen epicversie nooit inhoudelijk verandert;
- alle modulegrenscommunicatie via publieke queries en commands loopt;
- de eigen procesruntime de agentrol uit vertrouwde configuratie afleidt en iedere agent alleen het actuele
  geheugen van die eigen rol geeft;
- iedere agenttaak vastlegt welke exacte geheugenversies zij heeft gelezen;
- iedere AI-taak via AI-uitvoering loopt en een vaste provider, model en configuratieversie bevat;
- een wachtende processessie idempotent kan hervatten;
- output atomair en geversioneerd beschikbaar komt;
- de operationele sessiestatus wordt opgeslagen.

## Gerelateerde documenten

- [Productontwerp — MVP](mvp.md)
- [Productontwerp — uitgebreide implementatie](uitgebreid.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Overzicht](../../overzicht.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
