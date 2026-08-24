# Product Factory v2 — AI-uitvoering

Status: eerste ontwerp van de ondersteunende module, queue en laptopworker.

AI-uitvoering is de enige technische route waarlangs Product Factory een AI-taak laat uitvoeren.
De module bewaart taken en uitvoeringspogingen duurzaam, deelt werk uit aan een worker en accepteert
voortgang en resultaten. De worker op de laptop onderhoudt geen blijvende WebSocketverbinding meer,
maar haalt werk via beveiligde HTTPS long polling op.

De capability bestaat uit een Maven-API en één implementatiemodule. Andere implementaties gebruiken
uitsluitend de API; alleen de main-module neemt `ai-execution-impl` op. De implementatie bevat ten
minste de gescheiden interne Spring Modulith-onderdelen `settings` en `task-execution`. Er zijn geen
aparte Maven-modules nodig voor algemene instellingen.

De module is volledig generiek. Zij kent geen Productontwerper, Planner, Tester, overlegrol, epic,
story of andere productbetekenis. De aanroeper levert een complete, onveranderlijke taak met alle
benodigde data, de gekozen provider, het gekozen model en het verwachte uitvoercontract.

## Verantwoordelijkheid

AI-uitvoering is eigenaar en enige schrijver van:

- `AiJobConfiguration` — de globale provider- en modelkeuze per opaque `AiJobKey`;
- `AiTask` — de duurzame queueopdracht en actuele taakstatus;
- `AiTaskAttempt` — één geclaimde uitvoeringspoging met lease en fencing token;
- `AiTaskResult` — het ene geaccepteerde, onveranderlijke eindresultaat;
- `AiWorkerSession` — de operationele registratie van een workerproces en zijn capabilities.

De module:

- valideert de technische taakenvelop;
- bewaart iedere aanvraag idempotent in de database;
- deelt alleen passende taken uit aan bevoegde workers;
- bewaakt claims, heartbeats, hersteltermijnen en harde time-outs;
- accepteert alleen updates met het actuele attempt-ID en fencing token;
- valideert een resultaat tegen het opgegeven JSON-schema wanneer dat aanwezig is;
- bewaart veilige operationele voortgang zonder chain-of-thought;
- maakt status en resultaten read-only beschikbaar aan de aanvrager en operations.

De module doet nadrukkelijk niet het volgende:

- een agentrol kiezen of herkennen;
- rolgeheugen ophalen of toegangsrechten tussen rollen bepalen;
- beslissen welk AI-model geschikt is voor een producttaak;
- productcontext, epics, stories, bugs of verificaties ophalen;
- een domeinresultaat inhoudelijk goedkeuren of publiceren;
- zelf nieuwe AI-taken verzinnen;
- rechtstreeks in een procesmodule schrijven.

## Verdeling van verantwoordelijkheden

| Onderdeel | Verantwoordelijkheid |
|---|---|
| aanvragende procesmodule | bepaalt wat de agent moet doen, verzamelt alle input en het eigen rolgeheugen, kiest een `AiJobKey` en valideert later de domeinuitkomst |
| AI-uitvoering — intern `settings` | vertaalt de opaque `AiJobKey` naar actuele provider en model zonder de rol- of productbetekenis te kennen |
| AI-uitvoering — intern `task-execution` | bewaart en distribueert de complete taak, bewaakt uitvoering en levert het technische resultaat terug; kiest zelf nooit provider of model |
| laptopworker of mockworker | claimt een taak, start precies de gevraagde provider en rapporteert heartbeat, voortgang en resultaat |
| Agentgeheugen | levert uitsluitend aan de vertrouwde aanvragende rol haar eigen actuele geheugen; AI-uitvoering kent de rol niet |

## Algemene AI-jobinstellingen

De algemene instellingen zijn een intern onderdeel van de AI-uitvoeringscapability. Zij staan
duurzaam in de database en zijn niet productspecifiek. Iedere inhoudelijke agentjob heeft een
stabiele `AiJobKey`, bijvoorbeeld:

- `PRODUCT_DESIGN.CREATE_EPIC`;
- `PLANNING.SLICE_EPIC`;
- `QUALITY.VERIFY_EPIC`;
- `MEETING.CONVERSE`;
- `MEETING.SUMMARIZE`.

Een jobkey benoemt een soort opdracht en is geen agentrol. Eén rol kan meerdere jobkeys gebruiken en
dezelfde technische AI-uitvoering hoeft de betekenis van de key niet te begrijpen.

```java
class AiJobConfiguration {
    String jobKey;
    AiProvider provider;       // MOCKED, CODEX of CLAUDE
    String model;
    int version;
    boolean enabled;
    Instant updatedAt;
    ActorRef updatedBy;
}
```

De publieke instellingeninterface is:

```java
AiJobConfigurationDetails getAiJobConfiguration(AiJobKey jobKey);
List<AiJobConfigurationDetails> getAiJobConfigurations();
void updateAiJobConfiguration(UpdateAiJobConfigurationCommand command);
```

De ene globale Stakeholder of een bevoegde beheerder kan provider en model in het scherm
**Algemene instellingen** wijzigen. Een proces leest de configuratie vlak voordat het een taak
aanvraagt en zet `provider`, `model`, `jobKey` en `configurationVersion` als vaste waarden op de
`AiTask`.

Een al gequeue'de of lopende taak verandert dus nooit mee met een instellingenwijziging. Alleen een
nieuwe taak gebruikt de nieuwe provider of het nieuwe model. Een retry van dezelfde technische taak
behoudt eveneens de oorspronkelijke momentopname; bewust opnieuw uitvoeren met andere instellingen
vereist een nieuwe taak-ID en idempotentiesleutel.

Het interne `task-execution` controleert alleen dat de aangeleverde provider en het model technisch
geldig en toegestaan zijn. Het vraagt de configuratie niet zelf op en interpreteert de jobkey niet.

## Publieke module-interface voor aanvragers

```java
AiTaskId requestAiTask(RequestAiTaskCommand command);
AiTaskDetails getAiTask(AiTaskId taskId);
AiTaskResultDetails getAiTaskResult(AiTaskId taskId);
List<AiTaskDetails> findAiTasks(AiTaskFilter filter);
void cancelAiTask(CancelAiTaskCommand command);
```

`requestAiTask(...)` start geen providerproces. Het valideert en bewaart alleen een `QUEUED` taak en
retourneert direct. De unieke combinatie van aanvragende module, processessie en
idempotentiesleutel voorkomt dubbele taken.

Alle queries controleren dat de aanvrager het product en de processessie mag zien. Een procesmodule
kan alleen taken lezen die zij zelf heeft aangevraagd; operations en frontend gebruiken een aparte
bevoegde read-only projectie.

`cancelAiTask(...)` annuleert een nog niet geclaimde taak direct. Bij een lopende taak registreert
het command een annuleringsverzoek. De worker ziet dit bij de volgende heartbeat en stopt het lokale
providerproces zo snel mogelijk. Een resultaat dat daarna arriveert wordt niet meer als succesvol
geaccepteerd.

## Complete taakenvelop

```java
class RequestAiTaskCommand {
    String productId;
    String requestingModule;
    String processSessionId;
    String idempotencyKey;
    String jobKey;                    // alleen voor audit, niet geïnterpreteerd
    int jobConfigurationVersion;
    AiProvider provider;              // MOCKED, CODEX of CLAUDE
    String model;
    String instructionVersion;
    String instructions;
    JsonNode input;
    String responseSchema;            // optioneel JSON Schema
    List<AiTaskAttachment> attachments;
    Duration executionTimeout;
    int maxAttempts;
}
```

De aanvrager levert één volledige momentopname. Daarin staan alle productgegevens,
bronversies, eigen rolgeheugenversies, handoffs en toegestane omgevingsinformatie die de taak nodig
heeft. De worker hoeft nooit terug te bellen naar Productontwerp, Productplanning,
Kwaliteitsbewaking of Agentgeheugen.

`productId`, `requestingModule` en `processSessionId` worden uit de vertrouwde aanroepcontext
gecontroleerd en kunnen niet door vrije taakinhoud of modeloutput worden vervalst.

AI-uitvoering bewaart `input` en `instructions` als opaque data. Zij mag generieke grootte-, schema-,
privacy- en malwarecontroles doen, maar trekt geen productconclusies uit de inhoud.

Attachments bevatten metadata, hash en een begrensde objectreferentie. Grote binaire gegevens staan
niet als Base64 in de taaktabel. Secrets en toegangstokens staan niet in `instructions` of `input`;
waar nodig gebruikt de worker een kortlevende, taakgebonden secretreferentie.

## Datamodel

### AiTask

```java
class AiTask {
    String id;
    String productId;
    String requestingModule;
    String processSessionId;
    String idempotencyKey;
    String jobKey;
    int jobConfigurationVersion;
    AiProvider provider;
    String model;
    String instructionVersion;
    JsonNode executionEnvelope;
    String responseSchema;
    AiTaskStatus status;
    int attemptCount;
    int maxAttempts;
    Duration executionTimeout;
    Instant createdAt;
    Instant availableAt;
    Instant completedAt;
}
```

De taakstatussen zijn:

- `QUEUED` — beschikbaar of vanaf `availableAt` beschikbaar voor een passende worker;
- `RUNNING` — er bestaat één actuele geclaimde poging;
- `SUCCEEDED` — precies één resultaat is geaccepteerd;
- `FAILED` — geen retry meer toegestaan of een niet-herstelbare fout;
- `CANCELLED` — bewust gestopt en niet meer uitvoerbaar.

### AiTaskAttempt

```java
class AiTaskAttempt {
    String id;
    String taskId;
    int attemptNumber;
    String workerSessionId;
    String fencingTokenHash;
    AiTaskAttemptStatus status;
    Instant claimedAt;
    Instant startedAt;
    Instant lastHeartbeatAt;
    Instant leaseUntil;
    Instant recoveryUntil;
    String progressPhase;
    Integer progressPercentage;
    String safeProgressMessage;
    Instant finishedAt;
    String failureCode;
    String safeFailureMessage;
}
```

Een fencing token wordt alleen bij claimen aan de worker getoond; de database bewaart de hash. Alle
updates vereisen task-ID, attempt-ID en het actuele token. Een oude of dubbele worker kan daardoor
geen nieuwere poging afronden.

De pogingstatussen zijn `CLAIMED`, `RUNNING`, `SUSPECTED`, `COMPLETED`, `FAILED`, `ABANDONED` en
`FENCED`.

### AiTaskResult

```java
class AiTaskResult {
    String taskId;
    String attemptId;
    JsonNode output;
    List<AiResultArtifact> artifacts;
    Instant completedAt;
}
```

Er bestaat maximaal één geaccepteerd resultaat per taak. Het resultaat wordt na technische
validatie onveranderlijk. De aanvragende procesmodule bepaalt daarna of de inhoud als epic, story,
bug, verificatie of andere domeinoutput geldig is.

### AiWorkerSession

`AiWorkerSession` bevat alleen worker-ID, unieke bootsessie-ID, capabilities, starttijd,
`lastSeenAt`, status en eventuele eindtijd. Capabilities noemen ondersteunde providers en eventueel
modelgrenzen, maar nooit agentrollen.

Een kleine interne onderhoudstaak van AI-uitvoering controleert periodiek verlopen leases,
hersteltermijnen en `availableAt`. Zij start geen AI, maar voert uitsluitend de hierboven beschreven
statusovergangen en retries uit.

## Pull-interface voor workers

De laptopworker en mockworker gebruiken een afzonderlijke, beveiligde technische API:

```java
WorkerSession openWorkerSession(OpenWorkerSessionCommand command);
ReconcileResult reconcileWorker(ReconcileWorkerCommand command);
ClaimedAiTask claimNextTask(ClaimNextAiTaskCommand command);
void markAiTaskStarted(MarkAiTaskStartedCommand command);
void heartbeatAiTask(HeartbeatAiTaskCommand command);
void reportAiTaskProgress(ReportAiTaskProgressCommand command);
void completeAiTask(CompleteAiTaskCommand command);
void failAiTaskAttempt(FailAiTaskAttemptCommand command);
```

De worker leest nooit rechtstreeks uit de database. `claimNextTask(...)` is een HTTPS-long-poll die
bijvoorbeeld maximaal twintig seconden wacht. De worker roept hem alleen aan wanneer hij lokale
capaciteit heeft. De server claimt atomair de oudste passende `QUEUED` taak op basis van provider,
capabilities en `availableAt`.

Een worker mag meerdere taken parallel uitvoeren, maar het door hem gemelde en servermatig begrensde
maximum bepaalt hoeveel actieve claims hij krijgt. De server pusht geen taak en houdt geen socket
als permanent pushkanaal open.

## Heartbeat en veilige voortgang

Heartbeat en inhoudelijke voortgang zijn bewust gescheiden:

- `heartbeatAiTask(...)` bewijst alleen dat de worker en het lokale providerproces nog leven en
  verlengt de lease;
- `reportAiTaskProgress(...)` bewaart optioneel fase, percentage en een korte veilige melding en
  verlengt eveneens de lease.

Een worker stuurt standaard iedere dertig seconden een heartbeat, ook wanneer Codex of Claude geen
nieuwe tekst produceert. Voortgang bevat nooit prompts, ruwe providerlogs, tokens, persoonsgegevens
of chain-of-thought. De frontend toont hooguit bijvoorbeeld **model gestart**, **input verwerken**,
**resultaat valideren** of **tijdelijk geen heartbeat**.

## Slapen, crashen en leases

Een server kan niet betrouwbaar onderscheiden of een laptop slaapt, het workerproces is gecrasht of
het netwerk tijdelijk weg is. Daarom leidt een gemiste heartbeat niet direct tot een nieuwe
AI-uitvoering.

De standaardtijden zijn configureerbaar, met als eerste veilige waarden:

- heartbeat iedere 30 seconden;
- lease verloopt na 2 minuten zonder heartbeat;
- daarna 30 minuten hersteltermijn;
- pas na de hersteltermijn mag een poging `ABANDONED` worden;
- de harde taak-time-out blijft afzonderlijk gelden.

De overgang is:

```text
RUNNING
   │ lease verlopen
   ▼
SUSPECTED ──worker herstelt binnen termijn──> RUNNING met dezelfde attempt
   │ hersteltermijn verlopen
   ▼
ABANDONED ──attempts beschikbaar──> AiTask opnieuw QUEUED
           └─geen attempts meer──> AiTask FAILED
```

De laptopworker bewaart lokaal een klein duurzaam journal met task-ID, attempt-ID, fencing token en
status van het providerproces. Na start, reconnect of wakker worden voert hij altijd eerst
`reconcileWorker(...)` uit en claimt hij pas daarna nieuw werk.

Tijdens reconciliatie geldt:

1. leeft het oude providerproces nog, is de attempt nog `SUSPECTED`, valt zij binnen de
   hersteltermijn en is geen nieuwe poging geclaimd, dan kan de server dezelfde attempt en lease
   herstellen;
2. is de hersteltermijn verstreken, de attempt al `ABANDONED` of inmiddels een nieuwere poging
   geclaimd, dan wordt de oude attempt `FENCED` en moet de worker het oude proces stoppen en de
   output weggooien;
3. bestaat het lokale proces niet meer, dan geeft de worker dit door en kan de server de poging
   eerder `ABANDONED` maken;
4. een resultaat met een verlopen of oud fencing token wordt altijd geweigerd.

Hiermee is de uitvoering **at-least-once**, niet mathematisch exactly-once. Bij een crash op precies
het verkeerde moment kan een model tweemaal gestart zijn. Daarom mogen AI-taken zelf geen externe
productwijzigingen doen. Alleen de procesmodule publiceert na één geaccepteerd taakresultaat
idempotent domeinoutput.

Als er geen andere geschikte worker online is, heeft onmiddellijk heropenen geen voordeel. De taak
blijft dan zichtbaar `SUSPECTED` tot de hersteltermijn verloopt of dezelfde worker terugkomt. Dit
maakt normaal slapen minder duur zonder herstel na een echte crash onmogelijk te maken.

## Retrybeleid

Een poging kan eindigen door:

- herstelbare worker-, netwerk- of providerfout;
- leaseverlies of crash;
- harde time-out;
- ongeldig technisch resultaat;
- niet-herstelbare configuratie- of authenticatiefout;
- bewuste annulering.

Alleen herstelbare fouten en verlaten attempts mogen binnen `maxAttempts` opnieuw naar `QUEUED`, met
begrensde backoff. Een domeininvalide maar technisch geldige AI-uitkomst is geen automatische retry
van AI-uitvoering: de procesmodule beslist of zij tijdens een volgende `runProcessSession()` een
nieuwe, gerichte hersteltaak aanvraagt.

## MOCKED-provider

`MOCKED` volgt exact dezelfde publieke queue en workerprotocollen als `CODEX` en `CLAUDE`. Er staan
geen `if (acceptance)`-vertakkingen in Productontwerp, Productplanning of Kwaliteitsbewaking.

- **Unit tests van procesmodules** kiezen `provider = MOCKED` en gebruiken een in-memory fake van de
  publieke AI-uitvoeringsinterface met vooraf ingestelde taakstatussen en resultaten.
- **Integratietests** starten de echte AI-uitvoeringsmodule en queue plus `MockAiWorker` uit Product
  Factory Testbed. De worker claimt `MOCKED`-taken via de echte worker-API en stuurt
  deterministische fixtures terug.
- **Acceptatie** draait dezelfde stateful `MockAiWorker` als zelfstandig Testbed-onderdeel. De waarde
  in `model` is daar een stabiel mockprofiel, bijvoorbeeld `quality-success-v1`.

De mockworker kiest een fixture op basis van het geconfigureerde mockprofiel en optionele
testcorrelatie in de opaque input. Ook mockresultaten moeten aan het responseschema voldoen en
doorlopen leases, idempotentie en resultaatacceptatie.

De mockworker kan niet alleen succes teruggeven. Vaste scenario's kunnen langzaam werk, schemafout,
providerfout, time-out, annulering, ontbrekende heartbeat, slaap, crash, reconciliatie en een laat
resultaat met een oud fencing token simuleren. Daarmee gebruiken automatische integratietests en
handmatige UI-acceptatie precies dezelfde queuegrens en scenariofixtures. De volledige
omgevingsopzet staat in [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md).

Productieconfiguratie bevat een allowlist van providers en weigert `MOCKED` bij het opslaan van
algemene instellingen en bij het aanvragen van een taak. Zo kan een verkeerde instelling niet
stilletjes een productieproces met fictieve output laten doorgaan.

## Processessies wachten zonder thread

Een proces houdt geen serverthread, databaseclaim of HTTP-call open terwijl een AI-taak draait.
Wanneer een `runProcessSession()` één of meer taken heeft aangevraagd:

1. bewaart de procesmodule de taak-ID's op haar `ProcessSession`;
2. zet zij de sessie op `WAITING_FOR_AI`;
3. geeft de functie normaal terug;
4. een volgende geplande of handmatige aanroep hervat dezelfde sessie;
5. zolang resultaten ontbreken, blijft de sessie wachtend en eindigt die aanroep als no-op;
6. bij beschikbare resultaten valideert de procesmodule de inhoud en vervolgt zij haar eigen flow.

Er draait nog steeds maximaal één uitvoering van `runProcessSession()` tegelijk per procesmodule.
Een wachtende logische sessie houdt echter geen technische lock vast. Een handmatige aanroep tijdens
een werkelijk actieve uitvoering krijgt HTTP 409; een handmatige aanroep bij `WAITING_FOR_AI`
probeert veilig dezelfde sessie te hervatten.

Alleen `runProcessSession()` mag voor Productontwerp, Productplanning of Kwaliteitsbewaking nieuwe
AI-taken aanvragen. De laptopworker voert uitsluitend bestaande taken uit en kan nooit zelf een
proces, agentjob of vervolgstap starten.

## Beveiliging

- Iedere worker heeft een eigen intrekbare credential; er is geen gedeeld algemeen bridgetoken.
- De API autoriseert worker, provider, capabilities en maximaal parallelisme.
- De worker draait iedere taak in een nieuwe tijdelijke werkdirectory en niet in
  `product-factory-workspace`.
- Providercredentials blijven uitsluitend op de worker en staan nooit in de database of taak.
- De worker geeft alleen expliciet toegestane environmentvariabelen door aan Codex of Claude.
- Een worker kan geen taak voor een niet-ondersteunde provider claimen.
- Resultaat- en attachmentgroottes zijn begrensd en hashes worden gecontroleerd.
- Ruwe providerlogs en chain-of-thought worden niet als voortgang of resultaat opgeslagen.
- Iedere statuswijziging is herleidbaar tot taak, attempt, worker en tijdstip.

## Invarianten

- Alle echte AI-uitvoering loopt via een duurzame `AiTask`.
- AI-uitvoering kent geen agentrollen of productentiteiten.
- Iedere taak bevat een vaste provider, model, configuratieversie en instructieversie.
- Algemene instellingen bepalen de waarden voor nieuwe taken, niet voor bestaande taken.
- Een worker leest de queue uitsluitend via de publieke worker-API en nooit rechtstreeks uit de
  database.
- Per taak bestaat maximaal één actuele attempt en maximaal één geaccepteerd resultaat.
- Iedere workerupdate vereist het actuele fencing token.
- Een gemiste heartbeat veroorzaakt eerst `SUSPECTED`, niet onmiddellijk een retry.
- Reconnect en wakker worden beginnen altijd met reconciliatie.
- AI-taken hebben geen externe schrijfrechten; domeinpublicatie gebeurt idempotent door de
  aanvragende module.
- `MOCKED` gebruikt dezelfde queuegrens en is technisch uitgesloten in productie.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Frontend](../stakeholder/frontend.md)
- [Agentgeheugen](agentgeheugen.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
