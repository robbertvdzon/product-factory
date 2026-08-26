# Product Factory v2 — AI-uitvoering via Agent Runtime

Status: doelontwerp voor stap 4; de bestaande Kotlin-API bevat nog het oude contract en moet tijdens
de implementatie van deze stap worden aangepast.

AI-uitvoering is de Product Factory-façade naar de gedeelde Agent Runtime in
`/Users/robbertvdzon/git/agent-runtime`. Product Factory bouwt geen eigen technische AI-queue,
laptopworker, attemptadministratie, leasebewaking, fencing, providercontainer of artifactopslag.
Agent Runtime is de enige eigenaar van die technische uitvoering.

Product Factory blijft eigenaar van:

- globale provider- en modelinstellingen per stabiele `AiJobKey`;
- prompttemplates en de complete, bevroren prompt per domeintaak;
- de koppeling tussen product, processessie of meeting en het externe Runtime-job-ID;
- idempotente indiening via een lokale outbox;
- de toekenning van ontdekte project-environmentvariabelen aan Product Factory-agentrollen;
- domeinvalidatie en verwerking van het technische resultaat;
- zichtbare blokkering en hervatting van processessies en meetings.

Agent Runtime kent geen Productontwerper, Planner, Tester, overlegrol, product, epic, story of bug.
Product Factory stuurt een complete `APPLICATION_WORK`-job en verwerkt later het opaque technische
resultaat. Het vereenvoudigde externe contract staat in
[`agent-runtime/docs/jobs-en-uitvoering.md`](https://github.com/robbertvdzon/agent-runtime/blob/main/docs/jobs-en-uitvoering.md).

## Verdeling van verantwoordelijkheden

| Onderdeel | Verantwoordelijkheid |
|---|---|
| aanvragende procesmodule | bepaalt agentrol en jobkey, verzamelt toegestane domeininput en geheugen, bouwt de inhoudelijke prompt en valideert het resultaat |
| product-/overlegmodule | bouwt meetingprompts met de geldige rolcatalogus, meetingsnapshot en Stakeholdervragen en verwerkt antwoorden/notulen |
| AI-uitvoering — instellingen | bewaart `AiJobConfiguration` en bevriest provider, model en configuratieversie voor een nieuwe lokale taak |
| AI-uitvoering — Runtime-façade | bewaart lokale correlatie/outbox, leidt toegestane environmentkeynamen af, maakt exact één Runtime-job en vertaalt status/resultaat |
| Agent Runtime-server | beheert technische queue, mocks, attempts, leases, harde deadlines, retries, fencing, resultaten en artifacts |
| lokale Agent Runtime-worker | ontdekt projectcredentialnamen, selecteert per job alleen gevraagde waarden en voert Codex of Claude in Docker uit |
| aanvragende domeinmodule | keurt de technische JSON-uitkomst inhoudelijk goed en publiceert idempotent domeinoutput |

## Algemene AI-jobinstellingen

Iedere inhoudelijke Product Factory-job houdt een stabiele `AiJobKey`, bijvoorbeeld:

- `PRODUCT_DESIGN.CREATE_EPIC`;
- `PLANNING.SLICE_EPIC`;
- `QUALITY.VERIFY_EPIC`;
- `MEETING.CONVERSE`;
- `MEETING.SUMMARIZE`.

Een jobkey benoemt een Product Factory-opdracht en wordt niet naar Agent Runtime gestuurd. Product
Factory gebruikt hem om provider/model te kiezen, een prompttemplate te selecteren, mocks te
correleren en operationele historie te groeperen.

```java
class AiJobConfiguration {
    String jobKey;
    AiProvider provider;
    String model;
    int version;
    boolean enabled;
    Instant updatedAt;
    ActorRef updatedBy;
}
```

De globale Stakeholder of beheerder wijzigt deze instellingen onder **Instellingen → AI-modellen**.
Een nieuwe lokale taak bewaart jobkey, provider, model, configuratieversie en prompttemplateversie
als Product Factory-auditgegevens. Alleen provider en model gaan naar Agent Runtime; de versies
blijven lokaal.

Een instellingenwijziging verandert geen bestaande taak of Runtime-job. Bij `enabled = false`
vraagt een proces geen nieuwe taak aan en wordt de domeinsessie zichtbaar `BLOCKED` met
`AI_JOB_DISABLED`. Uitschakelen annuleert bestaande Runtime-jobs niet stilzwijgend.

## Publieke Product Factory-interface

Procesmodules gebruiken uitsluitend de Product Factory-API en kennen het HTTP-contract van Agent
Runtime niet:

```java
AiTaskId requestAiTask(RequestAiTaskCommand command);
void cancelAiTask(AiTaskId taskId, String reason);
AiTaskDetails getAiTask(AiTaskId taskId);
AiTaskResultDetails getAiTaskResult(AiTaskId taskId);
List<AiTaskDetails> findAiTasks(AiTaskFilter filter);
```

`requestAiTask(...)` maakt atomair een lokale correlatie plus outboxrecord en retourneert direct.
Een dispatcher biedt de job daarna via HTTPS idempotent aan Agent Runtime aan. Zo hoeft geen
database-transactie over twee applicaties te bestaan. Bij een verloren response gebruikt iedere
retry exact dezelfde Runtime-idempotentiesleutel en krijgt dezelfde externe job terug.

Een reconciler leest statussen en resultaten via de Runtime-API. Een processessie houdt nooit een
serverthread open terwijl AI draait. Zij bewaart `AiTaskId`, gaat naar `WAITING_FOR_AI` en verwerkt
een terminale uitkomst tijdens een volgende gewone procesrun.

## Vereiste wijziging van het bestaande Kotlin-contract

`product-factory-api/.../api/ai/AiContract.kt` bestaat al, maar beschrijft nog de oude eigen
workergrens. Stap 4 moet contract, tests en documentatie samen aanpassen.

Doelvorm van `RequestAiTaskCommand`:

```java
class RequestAiTaskCommand {
    AiJobKey jobKey;
    ProductId productId;
    String requesterCapability;
    ProcessSessionId requesterSessionId;
    String agentRole;
    AiProvider provider;
    String model;
    long configurationVersion;
    String promptTemplateVersion;
    String prompt;
    String responseSchema;
    RepositorySnapshot repository;
    List<AiInputAttachment> attachments;
    Duration executionTimeout;
    String idempotencyKey;
}
```

`productId`, `requesterSessionId` en `repository` mogen leeg zijn voor taaktypen waarvoor zij niet
gelden. Wijzigingen ten opzichte van de huidige interface:

- `instruction` en `inputJson` worden één complete `prompt`;
- `agentRole` wordt verplicht zodat de façade zelf de credentialgrants kan bepalen;
- `promptTemplateVersion`, `attachments` en `executionTimeout` worden toegevoegd;
- `TestEnvironmentAccess` en vrije `credentialReferences` verdwijnen uit de aanvraag;
- een procesmodule mag nooit zelf `environmentKeys` aanleveren;
- `jobKey` en `configurationVersion` blijven lokale auditgegevens maar gaan niet naar Runtime;
- Runtime-velden zoals `jobProfile`, `resourceRequests` en `consumerContext` komen niet in de
  Product Factory-API.

`AiInputAttachment` bevat bestandsnaam, MIME-type en bytes of een begrensde Product Factory-
artifactreferentie. De Runtime-adapter encodeert kleine inputbestanden pas aan de externe grens als
Base64. Outputartifacts blijven `ArtifactReference`s in `AiTaskResultDetails`.

De lokale taakstatussen worden:

```text
PENDING_SUBMISSION
QUEUED
WAITING_FOR_WORKER
RUNNING
SUCCEEDED
FAILED
CANCELLED
```

`CLAIMED`, `SUSPECTED`, `ABANDONED`, leases en attempts zijn Runtime-details en geen Product
Factory-domeinstatus meer. Hun veilige fase en historie mogen read-only in een operationele
projectie zichtbaar zijn.

`AiTaskDetails` krijgt minimaal `runtimeJobId`, `runtimePhase`, `attemptCount`, veilige voortgang en
de lokale correlatievelden. `AiTaskResultDetails` blijft technisch gevalideerde JSON plus
artifactreferenties en terminale foutinformatie tonen.

## Extern Runtime-request

De adapter vertaalt een lokale taak naar het minimale actuele `/v1`-request:

```json
{
  "jobKind": "APPLICATION_WORK",
  "idempotencyKey": "stabiele-product-factory-sleutel",
  "provider": "CODEX",
  "model": "gpt-5.6",
  "prompt": "complete prompt",
  "responseSchema": {},
  "executionTimeoutSeconds": 3600,
  "environmentKeys": ["HKH__ACCEPTANCE_USERNAME"],
  "attachments": [],
  "repositorySnapshot": null
}
```

Product Factory stuurt niet: jobkey, product-ID, processessie-ID, configuratieversie,
prompttemplateversie, agentrol of andere domeincorrelatie. Die gegevens blijven bij het lokale
`AiTask` en de aanvragende domeinsessie.

Retries en prioriteit zijn Runtime-policy en geen vrije aanvraagvelden. Een bewuste nieuwe
inhoudelijke poging na een terminale job krijgt een nieuwe lokale taak en idempotentiesleutel.

## Lokale correlatie en outbox

Product Factory bewaart geen tweede technische queue, maar wel duurzame integratiestatus:

```java
class AiTask {
    String id;
    String productId;
    String requestingModule;
    String requestContextId;
    String agentRole;
    String jobKey;
    int jobConfigurationVersion;
    String promptTemplateVersion;
    AiProvider provider;
    String model;
    String promptHash;
    String runtimeIdempotencyKey;
    String runtimeJobId;
    AiTaskStatus status;
    String runtimePhase;
    Instant createdAt;
    Instant submittedAt;
    Instant completedAt;
}
```

De exacte prompt en benodigde input moeten herleidbaar blijven voor een netwerkretry. Privacybeleid
bepaalt of de prompt zelf of een versleutelde/duurzame payload bij de outbox staat; een hash alleen
is niet genoeg zolang indiening nog niet is gelukt. Na succesvolle indiening blijft de bevroren
momentopname beschikbaar zolang productaudit dat vereist.

Product Factory maakt geen `AiTaskAttempt`, `AiWorkerSession`, fencing token, lease of technische
retryrecord. Het Runtime-job-ID en de Runtime-events zijn daarvoor de bron.

## Project-environmentvariabelen en agentrollen

Secretwaarden bestaan alleen in `project-credentials.env` op lokale Runtime-workers. De worker
registreert via Agent Runtime uitsluitend namen zoals:

```text
HKH__ACCEPTANCE_BASE_URL
HKH__ACCEPTANCE_USERNAME
HKH__ACCEPTANCE_PASSWORD
```

Product Factory leest via de beveiligde Runtime-catalogus welke namen bekend en op online workers
beschikbaar zijn. Product Factory bewaart zelf de functionele toekenning:

```java
class ProductEnvironmentVariable {
    String id;
    String productId;
    String key;
    String description;
    TargetEnvironment environment;
    boolean active;
}

class AgentEnvironmentVariableGrant {
    String productEnvironmentVariableId;
    String agentRole;
}
```

Deze records bevatten nooit waarden. Een product kan bekende keys uit één of meer projectprefixes
selecteren. De frontend toont per key of minstens één passende online worker hem momenteel heeft.

Benodigde publieke beheerinterface, toe te voegen tijdens stap 4:

```java
List<AvailableEnvironmentKeyDetails> findAvailableEnvironmentKeys(String projectPrefix);
List<ProductEnvironmentVariableDetails> getProductEnvironmentVariables(ProductId productId);
void configureProductEnvironmentVariables(ConfigureProductEnvironmentVariablesCommand command);
List<AgentEnvironmentVariableGrantDetails> getAgentEnvironmentVariableGrants(ProductId productId);
void updateAgentEnvironmentVariableGrants(UpdateAgentEnvironmentVariableGrantsCommand command);
```

Een backend-aanroep leidt `environmentKeys` uitsluitend af uit `productId`, de vertrouwde
`agentRole`, actieve productvariabelen en actieve grants. Een frontend, vrije prompt of modeloutput
kan de selectie niet wijzigen. Ontbrekende keys blokkeren de lokale taak zichtbaar met
`REQUIRED_ENVIRONMENT_KEY_UNKNOWN`; er wordt geen alternatief secret gekozen. Een bekende key die
alleen tijdelijk niet op een online worker beschikbaar is, mag wel worden ingediend en blijft bij
Runtime `WAITING_FOR_WORKER` totdat een passende worker verschijnt. Een `MOCKED`-job heeft geen
credentials nodig en gebruikt in acceptatiescenario's standaard geen environmentkeys.

Meetingagents krijgen alleen grants voor hun expliciete meetingrol. Een productbreed
meetingsnapshot geeft niet automatisch credentialtoegang tot andere rollen.

## Attachments en artifacts

Kleine inputattachments worden aan de Runtime-grens Base64 gecodeerd, maximaal 10 bestanden,
2 MB gedecodeerd per bestand en 10 MB per job. Product Factory valideert naam, MIME-type en grootte
vóór indiening; Agent Runtime valideert opnieuw.

Outputartifacts worden door de agent als bestanden gemaakt. De Runtime-worker verzamelt en uploadt
ze en het Runtime-resultaat bevat alleen metadata en artifact-ID's. Product Factory downloadt of
proxy't een artifact uitsluitend binnen de autorisatie van de bijbehorende lokale taak. De eerste
limieten zijn 5 MB per artifact, 25 MB per job en 25 outputbestanden.

## Harde time-out, herstel en annulering

`executionTimeout` is verplicht per lokale taak en gaat als seconden naar Runtime. Runtime berekent
per echte attempt een harde deadline vanaf claimen. Server en worker dwingen die onafhankelijk af;
heartbeat, slaap en recovery verlengen de deadline niet. Een verlopen attempt accepteert geen late
progress, artifacts of resultaat en eindigt technisch met `EXECUTION_TIMEOUT` of een door Runtime
geplande retry.

Product Factory implementeert geen eigen technische retry zolang dezelfde Runtime-job actief of
herstelbaar is. Na terminale Runtime-fout bepaalt de domeinmodule of dezelfde processessie zichtbaar
blokkeert of bewust een nieuwe logische taak nodig heeft.

`cancelAiTask(...)` bewaart lokaal eerst de reden en roept daarna idempotent de Runtime-cancelroute
aan. Product Factory blijft reconciliëren totdat Runtime terminale `CANCELLED` meldt. Alleen de
eigenaar van de processessie of meeting bepaalt de domeinvervolgstap; geen wachtende context raakt
verweesd.

## `MOCKED` en acceptatie

`MOCKED` wordt door de Agent Runtime-server in integratie of acceptatie uitgevoerd en bereikt geen
worker. Product Factory gebruikt dezelfde lokale outbox, externe jobroute, statusvertaling,
responseschemavalidatie en domeinverwerking als bij echte AI.

Product Factory bevat geen eigen mockexecutor of `AiWorkerSession`. Vaste Product Factory-fixtures
blijven gekoppeld aan lokale `scenarioKey`, `scenarioVersion`, `jobKey`, product en stap. Het
acceptatie-Testbed bereidt via een afzonderlijk gescopete Runtime test-control-integratie het juiste
externe mockantwoord voor. Productie weigert `MOCKED`; acceptatie krijgt nooit het Runtime-admin- of
workertoken.

## Processessies wachten zonder thread

Wanneer een proces een taak heeft aangevraagd:

1. bewaart de procesmodule het lokale `AiTaskId`;
2. zet zij de sessie op `WAITING_FOR_AI`;
3. geeft de servercall normaal terug;
4. outbox en reconciler dienen in en volgen de Runtime-job;
5. een volgende geplande of handmatige procesrun ziet of het resultaat beschikbaar is;
6. alleen de procesmodule valideert en publiceert de domeinuitkomst.

Er bestaat maximaal één onafgeronde logische processessie per procesmodule en product. Een
wachtende sessie houdt geen lock of HTTP-call open. De Runtime-worker kan nooit zelf een Product
Factory-proces of vervolgtaak starten.

## Beveiliging en geaccepteerde risicoafweging

- Product Factory bewaart nooit projectcredentialwaarden, alleen namen en grants.
- Het Runtime-consumenttoken staat als Product Factory-secret op OpenShift en geeft alleen toegang
  tot de eigen `APPLICATION_WORK`-jobs en gefilterde environmentcatalogus.
- Runtime-, worker-, provider- en Git-publicatiecredentials zijn nooit selecteerbaar.
- Een echte agent krijgt wel de expliciet voor zijn product en rol geselecteerde waarden in een
  tijdelijke `secrets.env`. Voor deze persoonlijke projecten is bewust geaccepteerd dat de agent
  die waarden technisch kan lezen; een promptwaarschuwing is geen harde isolatie.
- Product Factory neemt secretwaarden nooit op in prompt, lokale taak, event, progress, resultaat of
  artifact.
- Gitcontext is publiek, HTTPS, read-only en vastgezet op een volledige commit-SHA.
- Ruwe providerlogs en chain-of-thought worden niet opgeslagen.

## Invarianten

- Product Factory bouwt geen eigen laptopworker of tweede technische AI-queue.
- Agent Runtime is de enige eigenaar van attempts, leases, deadlines, fencing, retries en artifacts.
- `AiJobKey`, configuratieversie, prompttemplateversie en domeincorrelatie blijven lokaal.
- Agent Runtime ontvangt één complete prompt en interpreteert geen Product Factory-domein.
- Environmentkeywaarden blijven op lokale workers; databases bevatten alleen namen.
- Alleen de vertrouwde Product Factory-backend bepaalt keys uit product- en rolgrants.
- Per lokale taak bestaat maximaal één extern Runtime-job-ID.
- Een technisch geldig resultaat wordt pas domeinwaarheid na validatie door de aanvragende module.
- Productie weigert `MOCKED`.

## Gerelateerde documenten

- [Agent Runtime-integratie en taakcontainer](ai-worker.md)
- [Agentgeheugen](agentgeheugen.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Frontend](../stakeholder/frontend.md)
- [Agent Runtime — jobs en uitvoering](https://github.com/robbertvdzon/agent-runtime/blob/main/docs/jobs-en-uitvoering.md)
