# Stap 10 — Product Factory naar Agent Runtime v2

Implementatiestatus: uitgevoerd en op acceptatie en productie geverifieerd op 2026-09-09. Alle
Product Factory-AI-uitvoering gebruikt Runtime v2; de bewust tijdelijke compatibiliteitsvelden en
v1-ondersteuning aan Runtime-zijde blijven volgens het expand/contract-plan beschikbaar voor andere
consumers en een latere cleanuprelease.

## Doel

Migreer alle AI-uitvoering van Product Factory van Agent Runtime `/v1` naar `/v2`. Na deze stap
roept Product Factory geen AI-provider rechtstreeks aan en doet zij geen `/v1`-aanroepen meer.
Iedere AI-taak gebruikt expliciet één leverancier, model en uitvoeringswijze, verstuurt grote invoer
als hervatbare objectuploads, ontvangt gevalideerde JSON plus een expliciete artifactlijst en is in
de centrale Agent Runtime-kostenregistratie terug te vinden onder tenant `product-factory`.

Dit document is een zelfstandige uitvoeropdracht. De uitvoerende agent hoeft geen eerdere
gesprekscontext te kennen, maar moet wel alle repositories onder dezelfde Git-hoofdmap kunnen
lezen en wijzigen.

## Eindtoestand

De migratie is klaar wanneer:

- de zes bestaande Product Factory-jobtypes uitsluitend `/v2` gebruiken;
- iedere job `APPLICATION_WORK` plus `STRUCTURED_GENERATION` gebruikt;
- `vendorId`, `model` en `mode` altijd expliciet zijn en Agent Runtime nooit een fallbackmodel kiest;
- productie standaard `openai` / `gpt-5.6-sol` / `SUBSCRIPTION` gebruikt en acceptatie uitsluitend
  `mock` / `mock` / `MOCK` gebruikt;
- de volledige prompt als `text/markdown`-object is geüpload en niet inline of Base64 in de
  jobaanvraag staat;
- overige tekst-, Markdown-, JSON-, document- en afbeeldingsinputs dezelfde hervatbare
  uploadroute kunnen gebruiken;
- een verloren HTTP-response, procesrestart of onderbroken upload geen dubbele Runtime-job maakt;
- het gestructureerde resultaat via `GET /v2/jobs/{jobId}/result` wordt verwerkt en bestanden
  uitsluitend via de daarin teruggegeven artifactmetadata en download-URL's worden benaderd;
- Product Factory nooit in de gedeelde Runtime-fileshare kijkt;
- Product Factory per AI-taak de door Runtime gerapporteerde usage en kosten kan tonen en per
  lokale `jobKey` kan groeperen, zonder zelf providerprijzen te berekenen;
- de centrale Runtime-monitor kosten kan groeperen op tenant, leverancier, model, mode en
  `taskType`;
- veilige voortgang en `REASONING_SUMMARY` live of bijna live zichtbaar kunnen zijn, maar ruwe
  private chain-of-thought nergens wordt gevraagd, opgeslagen of getoond;
- de bestaande `/v1`-API van Agent Runtime beschikbaar blijft voor nog niet gemigreerde consumers;
- acceptatie, productie, rollback en contentretentie aantoonbaar werken.

## Repositories en bestanden die eerst gelezen moeten worden

Noem de map die de Git-repositories bevat hieronder `GIT_ROOT`. In de huidige werkruimte is dat
`/Users/robbertvdzon/git`.

Lees vóór de eerste wijziging minimaal:

### Product Factory

- `GIT_ROOT/product-factory/product-factory-api/src/main/kotlin/nl/vdzon/productfactory/api/ai/AiContract.kt`
- `GIT_ROOT/product-factory/ai-execution-impl/src/main/kotlin/nl/vdzon/productfactory/ai/AgentRuntimeClient.kt`
- `GIT_ROOT/product-factory/ai-execution-impl/src/main/kotlin/nl/vdzon/productfactory/ai/AiExecutionApplicationService.kt`
- `GIT_ROOT/product-factory/ai-execution-impl/src/main/kotlin/nl/vdzon/productfactory/ai/AiSettingsApplicationService.kt`
- de migraties `V5`, `V6`, `V12` en `V17` onder `ai-execution-impl/src/main/resources/db/migration`;
- alle aanroepen van `RequestAiTaskCommand` in `product-design-impl-mvp`,
  `product-planning-impl-mvp`, `quality-impl-mvp` en `product-factory-app`;
- `product-factory-app/src/main/kotlin/nl/vdzon/productfactory/memory/MemoryAndAiHttpApi.kt`;
- `product-factory-app/src/main/kotlin/nl/vdzon/productfactory/config/RuntimeConfigurationGuard.kt`;
- `product-factory-app/src/main/kotlin/nl/vdzon/productfactory/testbed/AcceptanceSafetyGuard.kt`;
- `product-factory-frontend/lib/memory_ai_management.dart` en de bijbehorende tests;
- `deploy/base`, `deploy/overlays/acceptance`, `deploy/overlays/production` en `.github/workflows`;
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md),
  [AI-worker](../gedeelde-modules/ai-worker.md),
  [integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md),
  [configuratie en secrets](../platform/configuratie-en-secrets.md),
  [deployment en operatie](../platform/deployment-en-operatie.md) en
  [stap 4](04-ai-uitvoering.md).

### Agent Runtime

- `GIT_ROOT/agent-runtime/agent-runtime-contracts/src/main/kotlin/nl/vdzon/agentruntime/contracts/v2/V2Contracts.kt`
- `GIT_ROOT/agent-runtime/agent-runtime-contracts/src/main/resources/openapi/agent-runtime-v2.yaml`
- `GIT_ROOT/agent-runtime/agent-runtime-server/src/main/kotlin/nl/vdzon/agentruntime/server/v2`;
- `GIT_ROOT/agent-runtime/agent-runtime-worker/src/main/kotlin/nl/vdzon/agentruntime/worker/V2Worker.kt`;
- `GIT_ROOT/agent-runtime/agent-runtime-server/src/main/kotlin/nl/vdzon/agentruntime/server/mock/MockExecution.kt`;
- de v2-specificatie, deploymentmanifests en relevante tests in die repository.

Controleer de actuele code opnieuw voordat er wordt gebouwd. De contractdetails hieronder zijn de
vereiste doeltoestand; als de Runtime inmiddels een gelijkwaardige capability onder een andere
naam heeft, gebruik dan het actuele OpenAPI-contract en werk dit document in dezelfde commit bij.

## Huidige situatie en concrete problemen

Product Factory gebruikt nu `/v1` en heeft de volgende eigenschappen:

- `AiProvider` bevat `CODEX`, `CLAUDE` en `MOCKED`; een afzonderlijke uitvoeringswijze ontbreekt;
- prompt en attachments worden in één JSON-request gezet; attachments worden Base64 gecodeerd;
- de transactionele outbox bewaart die volledige v1-request als JSON;
- resultaat- en artifactverwerking gebruiken v1-DTO's en v1-downloadroutes;
- model- en environmentkeycatalogi komen uit `/v1/models` en `/v1/environment-keys`;
- acceptatie misbruikt het modelveld als scenario/mockprofiel;
- de beheer-UI toont alleen provider en model;
- outputartifacts mogen nu door de agent een willekeurige bestandsnaam krijgen;
- langdurig gebruikte UX-artifacts worden alleen via Runtime geproxied, terwijl Runtime-v2-content
  standaard na een bewaartermijn kan worden verwijderd.

Agent Runtime v2 lost de grote REST-body op met hervatbare uploads, maar mist op het moment van dit
plan nog drie Product Factory-voorwaarden: een v2-uitvoeringscatalogus, een v2-environmentcatalogus
en een gerichte v2-mockfixture-API. Deze voorwaarden worden in fase 0 achterwaarts compatibel
toegevoegd. Product Factory mag pas omschakelen als ze op de doelomgeving zijn gedeployd.

## Vaste ontwerpbesluiten

### Eén soort generatie

Alle huidige Product Factory-AI-jobs zijn `STRUCTURED_GENERATION`. Tekst is geen aparte taaksoort:
een puur tekstueel antwoord gebruikt een verplicht JSON-schema met bijvoorbeeld één veld `text`.
Iedere aanvraag heeft dus een niet-null `resultSchema`.

De lokale `jobKey` blijft het inhoudelijke type opdracht:

| Product Factory-jobkey | Runtime jobKind | Runtime taskType |
|---|---|---|
| `MEETING.CONVERSE` | `APPLICATION_WORK` | `STRUCTURED_GENERATION` |
| `MEETING.SUMMARIZE` | `APPLICATION_WORK` | `STRUCTURED_GENERATION` |
| `PRODUCT_DESIGN.CREATE_EPIC` | `APPLICATION_WORK` | `STRUCTURED_GENERATION` |
| `PLANNING.SELECT_WORK` | `APPLICATION_WORK` | `STRUCTURED_GENERATION` |
| `PLANNING.SLICE_EPIC` | `APPLICATION_WORK` | `STRUCTURED_GENERATION` |
| `QUALITY.VERIFY_EPIC` | `APPLICATION_WORK` | `STRUCTURED_GENERATION` |

Agent Runtime rapporteert centraal `taskType`. Product Factory kan daarnaast lokaal op `jobKey`
groeperen door de lokale taakcorrelatie met de door Runtime gerapporteerde usage te combineren. Voeg
geen `operationKey` of `workloadKey` aan het Runtime-contract toe.

### Expliciete uitvoering zonder fallback

Vervang `AiProvider` in Product Factory door deze drie dimensies:

```json
{
  "vendorId": "openai",
  "model": "gpt-5.6-sol",
  "mode": "SUBSCRIPTION"
}
```

Alle drie velden zijn verplicht in configuratie, taak, audit en Runtime-request. Geldige modes zijn
`SUBSCRIPTION`, `API` en `MOCK`; Product Factory ondersteunt het contract generiek, maar verstuurt
geen `API`-job voordat daar bewust een Product Factory-configuratie voor is gekozen.

Migratiemapping voor bestaande data:

| Oud | Nieuw `vendorId` | Nieuw `mode` | Model |
|---|---|---|---|
| `CODEX` | `openai` | `SUBSCRIPTION` | bestaand model |
| `CLAUDE` | `anthropic` | `SUBSCRIPTION` | bestaand model |
| `MOCKED` | `mock` | `MOCK` | altijd `mock` |

Een mock heeft dus juist wel een leverancier en model: beide zijn `mock`. Agent Runtime mag een
onbeschikbare combinatie alleen afwijzen; nooit stil een ander model, vendor of mode kiezen.

### Invoer via objectuploads

Upload voor iedere job altijd de volledige prompt als:

```json
{
  "filename": "prompt.md",
  "mimeType": "text/markdown",
  "name": "prompt",
  "role": "PROMPT"
}
```

De inline `instruction` blijft kort en vast, bijvoorbeeld:

> Lees de volledige opdracht uit invoerobject `prompt`, behandel alle andere invoerobjecten volgens
> hun rol en retourneer uitsluitend JSON volgens het responseschema.

Upload ook attachments, waaronder grote `.txt`, `.md`, `.json`, documenten en afbeeldingen. De
job-JSON bevat alleen object-ID's en metadata, nooit Base64. Behoud tijdens deze migratie de huidige
lokale veiligheidslimieten totdat een concrete Product Factory-use-case verruiming nodig maakt;
v2 verwijdert de transportbeperking, niet de noodzaak van begrenzing.

### Resultaat en bestanden

Niet iedere job hoeft een resultaatbestand te maken. Het normale antwoord is gevalideerde JSON met
een huidige Runtime-limiet van 1 MiB. `GET /v2/jobs/{jobId}/result` retourneert dat JSON-resultaat,
de artifactlijst, usage en kosten. Product Factory gebruikt dit resultaat wel degelijk als de
inhoudelijke uitkomst.

Grote binaire of tekstuele uitvoer hoort in een vooraf gedeclareerd artifact. Als toekomstige JSON
naar een groot document verwijst, verwijst het alleen met de logische artifactnaam; het document
zelf staat bijvoorbeeld als `text/markdown`-artifact in de result-artifactlijst. Voeg geen
download-URL door modeloutput toe en laat een model geen bestandsnamen verzinnen.

V2 vereist vooraf gedeclareerde logische artifactnamen. Gebruik daarom voor Productontwerp maximaal
vijftig optionele PNG-slots `ux-01` tot en met `ux-50`. JSON-velden zoals `outputArtifactName` en
`artifactName` gebruiken exact zo'n logische naam. Een initiële schermset mag maximaal 25 schermen
met ieder `DESKTOP` en `MOBILE` genereren; een revisie mag maximaal 50 nieuwe of vervangende
afbeeldingen opleveren. Bestaande `KEEP`-artifacts worden niet opnieuw geschreven. Vergelijk altijd
de JSON-referenties met de artifactmanifestlijst voordat een epic wordt gepubliceerd.

Voor Kwaliteitsbewaking gelden dezelfde regels. Voeg alleen declaraties toe als het proces echt
nieuwe evidencebestanden moet produceren, gebruik vaste slots zoals `evidence-01` tot en met
`evidence-50` en laat evidence-JSON naar de logische naam verwijzen. Externe bron-URL's blijven een
ander expliciet type bewijs en mogen niet als Runtime-artifact worden voorgedaan.

Meeting- en planningsjobs declareren geen bestanden zolang hun schema's alleen JSON nodig hebben.

### Duurzame versus tijdelijke artifacts

Runtime-jobcontent is uitvoeringscontent en wordt door Agent Runtime volgens haar retentiebeleid
opgeruimd. UX-afbeeldingen die in een epic en later in stories worden gebruikt zijn Product
Factory-domeindata en moeten langer leven dan de Runtime-job.

Kopieer daarom vóór publicatie ieder door het domein geaccepteerd artifact streamend via de Runtime-
download-URL naar een Product Factory-beheerde artifactstore. Gebruik een eigen map/PVC en eigen
metadata; mount of doorzoek nooit de interne Runtime-objectmap. Dezelfde fysieke 16-TB-fileshare mag
als onderliggende OpenShift-opslag worden gebruikt als de access mode dat ondersteunt, maar gebruik
een afzonderlijk claim/subpad en applicatiegrens.

Na succesvolle kopie verwijzen `ArtifactReference.uri`-waarden naar de bestaande Product Factory-
downloadroute. Leg SHA-256, grootte, MIME-type, logische naam en Runtime-object-ID vast. Publiceer
geen epic of kwaliteitsresultaat als een vereist domeinartifact niet duurzaam is gekopieerd en op
hash is gecontroleerd.

Voeg een geplande opruimtaak toe die:

- lokale inputbytes wist zodra de Runtime-job aantoonbaar is geaccepteerd;
- verlaten lokale uploadcorrelaties na een veilige termijn verwijdert;
- tijdelijke, nergens gepubliceerde outputkopieën verwijdert;
- duurzame domeinartifacts pas verwijdert als geen enkele actuele of historische domeinversie er
  nog naar verwijst en de vastgelegde bewaartermijn is verstreken.

Laat Agent Runtime zelf verlopen, niet aan jobs gekoppelde uploads en eigen uitvoeringscontent
opruimen. Product Factory mag na succesvolle duurzame overname desgewenst
`DELETE /v2/jobs/{jobId}/content` aanroepen, maar alleen na hashcontrole en alleen wanneer operatie-
logs/inputs volgens het overeengekomen retentiebeleid niet meer nodig zijn. Jobmetadata, usage en
kosten blijven in Runtime bestaan.

### Status, events en reasoning

De duurzame backend blijft periodiek `GET /v2/jobs/{jobId}` en
`GET /v2/jobs/{jobId}/events?afterSequence=...` gebruiken. Dat is robuust na restarts en vereist
geen open verbinding.

SSE (`GET /v2/jobs/{jobId}/event-stream`) is één langdurige HTTP-respons waarop de server nieuwe
events direct pusht; het is geen long polling. Gebruik SSE optioneel voor een snellere operationele
UI en reconnect met `Last-Event-ID`. De databaseprojectie en gewone eventpolling blijven de bron
voor correctheid.

Toon alleen veilige status, progress, tool-events en `REASONING_SUMMARY`. Raw chain-of-thought is
niet betrouwbaar beschikbaar en mag niet als contracteis worden opgenomen. Sla gelimiteerde,
geschoonde samenvattingen op; log nooit prompts, secrets of volledige tooloutput.

### Netwerk en opslag

Product Factory en Agent Runtime draaien in hetzelfde OpenShift-cluster. Een expliciet toegestane
interne service-URL via `http://...svc.cluster.local` is daarom geldig. Pas de guards aan zodat zij
de concrete interne acceptatie- en productiehosts toestaan. Accepteer niet willekeurig iedere
externe `http`-URL. Voeg voor deze migratie geen verplichte TLS- of encryption-at-rest-laag toe.

## Vereist v2-consumercontract

Gebruik het actuele OpenAPI-bestand als technische bron. Onderstaande requests maken de voor deze
migratie vereiste semantiek expliciet.

### Upload reserveren, hervatten en afronden

```http
POST /v2/uploads
Authorization: Bearer <product-factory-consumer-token>
Content-Type: application/json

{
  "filename": "prompt.md",
  "mimeType": "text/markdown",
  "sizeBytes": 12345,
  "sha256": "<64 lowercase hex tekens>"
}
```

Verwacht `201 Created` met minimaal `uploadId`, `objectId`, `chunkSizeBytes`, `uploadUrl`, `offset`,
`sizeBytes`, `state` en `expiresAt`.

Stuur bytes in blokken naar exact de teruggegeven `uploadUrl`:

```http
PATCH <uploadUrl>
Authorization: Bearer <product-factory-consumer-token>
Content-Type: application/offset+octet-stream
Upload-Offset: <huidige offset>

<ruwe bytes; geen Base64>
```

Een geslaagde PATCH geeft `204 No Content` en de nieuwe `Upload-Offset`. Vraag na een onzekere
response eerst met `HEAD <uploadUrl>` de serveroffset op. Rond pas af als offset gelijk is aan
`sizeBytes`:

```http
POST /v2/uploads/{uploadId}/complete
Authorization: Bearer <product-factory-consumer-token>
```

Verwacht een `ObjectView` met status `READY` en controleer ID, SHA-256, grootte en MIME-type.

### Job aanmaken

```http
POST /v2/jobs
Authorization: Bearer <product-factory-consumer-token>
Content-Type: application/json

{
  "idempotencyKey": "pf-<stabiele lokale sleutel>",
  "jobKind": "APPLICATION_WORK",
  "taskType": "STRUCTURED_GENERATION",
  "execution": {
    "vendorId": "openai",
    "model": "gpt-5.6-sol",
    "mode": "SUBSCRIPTION"
  },
  "input": {
    "instruction": "Lees de volledige opdracht uit invoerobject prompt, behandel alle andere invoerobjecten volgens hun rol en retourneer uitsluitend JSON volgens het responseschema.",
    "objects": [
      {"objectId": "<uuid>", "name": "prompt", "role": "PROMPT"}
    ]
  },
  "output": {
    "resultSchema": {"type": "object"},
    "artifacts": []
  },
  "environmentKeys": [],
  "executionTimeoutSeconds": 1800
}
```

`execution`, `input`, `output` en het volledige responseschema zijn altijd aanwezig. Voeg
`repositorySnapshot` toe waar Productontwerp of Kwaliteitsbewaking al een HTTPS-repository plus
exacte commit-SHA bevriest. Voeg voor `APPLICATION_WORK` geen `repositoryRequest` toe.

Een succesvolle create geeft `202 Accepted` met `JobView`. Dezelfde idempotency key plus exact
dezelfde request moet dezelfde job teruggeven; dezelfde key plus een andere request moet
`409 IDEMPOTENCY_CONFLICT` geven.

### Status, resultaat, events, annulering en downloads

Implementeer en contracttest minimaal:

- `GET /v2/jobs/{jobId}`;
- `GET /v2/jobs/{jobId}/result`;
- `GET /v2/jobs/{jobId}/attempts` voor usage van mislukte of herprobeerde uitvoeringen;
- `GET /v2/jobs/{jobId}/events?afterSequence=<n>&limit=<n>`;
- optioneel `GET /v2/jobs/{jobId}/event-stream` voor SSE;
- `POST /v2/jobs/{jobId}/cancel`;
- de `downloadUrl` uit ieder `OutputObjectView`, inclusief `Range`/`206` zodat grote artifacts
  streamend en hervatbaar kunnen worden overgenomen.

Gebruik een download-URL als opaque, tenant-geauthenticeerde relatieve URL. Bouw hem niet zelf op en
haal geen bestand rechtstreeks van disk.

## Fase 0 — Blokkerende uitbreidingen in Agent Runtime

Voer deze fase uit in `GIT_ROOT/agent-runtime`, met additive contracts en zonder `/v1` te wijzigen of
te verwijderen. Release deze wijzigingen eerst naar acceptatie en productie.

### 0.1 Uitvoeringscatalogus voor consumers

Voeg een tenant-gefilterde consumerroute toe:

```http
GET /v2/execution-options?taskType=STRUCTURED_GENERATION
```

Elke entry bevat minimaal:

```json
{
  "execution": {"vendorId": "openai", "model": "gpt-5.6-sol", "mode": "SUBSCRIPTION"},
  "taskTypes": ["STRUCTURED_GENERATION"],
  "available": true,
  "matchingOnlineWorkers": 1,
  "lastSeenAt": "2026-01-01T00:00:00Z"
}
```

Baseer beschikbaarheid op v2-workerregistraties en heartbeats, pas de tenantallowlist toe en neem de
server-side `mock/mock/MOCK`-mogelijkheid alleen buiten productie op. Dit is een catalogus, geen
fallbackmechanisme.

### 0.2 Environmentkeycatalogus voor v2

Voeg een tenant-gefilterde consumerroute toe:

```http
GET /v2/environment-keys?project=HKH
```

Gebruik v2-workerregistraties als bron en retourneer naam, projectprefix, beschikbaarheid,
`matchingOnlineWorkers` en `lastSeenAt`. Geef nooit een secretwaarde terug. Valideer dat de prefix
binnen de tenantpolicy valt. De bestaande v1-catalogus is geen geldige bron voor v2-workers.

### 0.3 Gerichte v2-mockfixtures

De generieke v2-mock die lege waarden uit een JSON-schema maakt, is onvoldoende voor Product
Factory-scenario's. Voeg een acceptance-only route toe onder `/v2/test-control/mocks`, met list,
create, delete en clear. Gebruik een eigen `TEST_CONTROL`-rol/token die alleen deze routes mag
aanroepen; gebruik niet het Runtime-admin- of worker-token.

De createbody bevat minimaal `tenantId`, exacte `idempotencyKey`, exact één van `result`,
`outputSequence` of `errorCode`, optioneel `errorMessage`, `delayMillis` en optionele
`outputArtifactNames`. Een artifactnaam moet vooraf in de job zijn gedeclareerd. De mockexecutor
maakt voor genoemde namen een klein, deterministisch geldig bestand van een toegestaan MIME-type;
grote binaire fixtures hoeven niet Base64 door de test-control-REST-route.

Een Product Factory-mockjob consumeert exact één best passende fixture. Ontbreekt die, dan eindigt
de job zichtbaar met `NO_MOCK_RESPONSE_CONFIGURED`; genereer voor deze tenant niet stil een generiek
schemaresultaat. `outputSequence` blijft bruikbaar om eerst ongeldige en daarna geldige structured
output te testen.

### 0.4 Runtime-bewijs voor fase 0

Voeg contract-, security-, repository- en integratietests toe voor:

- tenantisolatie van beide catalogi;
- geen fallback bij een onbekende vendor/model/mode/task-combinatie;
- online/offline workerberekening;
- alleen keynamen uit toegestane prefixes;
- test-control-token heeft geen admin-, worker- of consumerrechten en andersom;
- gerichte fixture, ontbrekende fixture, foutfixture, delay, invalid-output-correctie en artifacts;
- alle bestaande v1-tests blijven groen;
- OpenAPI bevat alle request-, response-, fout- en authenticatiecontracten.

Fase 0 is pas klaar na een geauthenticeerde contractsmoke op de echte acceptatie- en
productie-Runtime. Test-control moet in productie `404` geven.

## Fase 1 — Product Factory-contract en database uitbreiden

### 1.1 Publiek Kotlin-contract

Pas `AiContract.kt` aan:

- voeg `AiExecutionMode { SUBSCRIPTION, API, MOCK }` en een value/data class voor
  `vendorId`, `model`, `mode` toe;
- verwijder `AiProvider` uit de uiteindelijke publieke API;
- maak `responseSchema` verplicht;
- voeg een expliciete inputrol toe aan `AiInputAttachment` en sta tekst/Markdown toe;
- voeg `AiOutputArtifactDeclaration(name, required, mimeTypes, maxBytes)` toe;
- voeg de outputdeclaraties toe aan `RequestAiTaskCommand`;
- laat `AiJobConfigurationDetails`, updatecommands, taakdetails en catalogusentries altijd de drie
  executionvelden bevatten;
- voeg een gelimiteerde event-/progressprojectie en een Runtime-usageweergave toe aan de query-API;
- verander artifactdownloads van `ByteArray` naar streaming (`Resource`, `InputStream` of
  `StreamingResponseBody`) zodat een groot bestand niet volledig in heap wordt geladen.

Werk alle compile-time callsites in dezelfde wijziging bij. Houd één Product Factory-façade; laat
procesmodules geen Runtime-DTO's of HTTP-client kennen.

### 1.2 Voorwaartse migratie zonder rollback te breken

Controleer eerst het hoogste Flywaynummer; op het moment van dit plan is dat `V18`, dus gebruik dan
`V19`. Volg expand/migrate/contract:

1. voeg `vendor_id` en `execution_mode` toe aan jobdefinitie, jobconfiguratie, taak en modelcatalogus;
2. behoud in deze release de oude `provider`-kolommen zodat het vorige productie-image nog kan
   starten;
3. backfill volgens de mappingtabel en zet mockmodellen op `mock`;
4. voeg geldige checks, not-nullvoorwaarden en samengestelde sleutels voor
   `(vendor_id, model, execution_mode)` toe;
5. laat de nieuwe applicatie tijdens de overgang de oude providerkolom compatibel dual-write'en;
6. verwijder oude kolommen en v1-code pas in een latere cleanuprelease nadat de v2-release stabiel
   is en rollback naar het oude image niet meer nodig is.

Voeg daarnaast tabellen toe voor:

- immutable lokale inputobjecten met taak-ID, logische naam, filename, MIME, rol, bytes of veilige
  stagingreferentie, grootte en SHA-256;
- uploadcorrelatie met `upload_id`, `object_id`, bevestigde offset, state, `expires_at` en timestamps;
- gelimiteerde Runtime-events met laatste verwerkte sequence;
- usage-/kostensnapshot per taak en attempt, inclusief quality, metrics, costs en moment van ophalen;
- Product Factory-beheerde artifacts met lokale ID, Runtime-job/object-ID, logische naam, filename,
  MIME, grootte, SHA-256, storage key, status en lifecyclemetadata;
- expliciete domeinreferenties naar duurzame artifacts, zodat opruimen niet op JSON-tekstzoeken
  berust.

Sla grote bytes niet Base64 op. Als een database-`BYTEA` alleen voor de huidige kleine interne
inputs wordt gebruikt, wis die na geaccepteerde indiening. Gebruik voor toekomstige grote externe
uploads direct een streaming stagingstore.

### 1.3 Idempotency en requestfingerprint

Neem in de lokale fingerprint minimaal op: jobkey, product/session, executionselectie,
prompttemplateversie, SHA's en rollen van alle inputs, responseschema, repositorysnapshot,
environmentkeys, timeout en outputdeclaraties. Object-ID's van Runtime horen niet bij de inhoudelijke
fingerprint, maar de eenmaal verkregen object-ID's worden wel duurzaam hergebruikt voor iedere
herhaling van exact dezelfde Runtime-create.

## Fase 2 — V2-client met echte streaming bouwen

Vervang de v1-DTO's in `AgentRuntimeClient.kt` door lokale DTO's die één-op-één met het actuele v2-
OpenAPI-contract corresponderen. Genereer desgewenst een client uit OpenAPI, maar houd foutvertaling
en de Product Factory-interface bewust klein en testbaar.

Implementeer:

- upload create, HEAD, PATCH, complete en best-effort DELETE;
- idempotente jobcreate waarbij exact dezelfde request na een verloren response opnieuw kan worden
  verstuurd;
- status, result, attempts, events, cancel en contentdelete;
- execution- en environmentcatalogi;
- artifactdownload met `Range`, stream-copy, maximale grootte, hashcontrole en hervatting;
- connect-, request- en idle-time-outs per soort call;
- veilige parse van Runtime-foutcode zonder remote responsebody in logs te lekken.

Gebruik REST voor kleine besturings-JSON en streaming HTTP voor bytes. SSE is alleen voor events en
niet voor fileupload of -download.

## Fase 3 — Outbox en herstelalgoritme ombouwen

`requestAiTask` blijft één lokale databasetransactie en doet geen netwerkcall. Bewaar de bevroren
inhoud, metadata, fingerprint en outboxopdracht. De dispatcher voert per taak exact dit algoritme
uit:

1. Staat al een definitieve `CreateJobRequest` in de outbox, verstuur dan exact diezelfde request
   opnieuw en sla alle uploadstappen over. De bestaande Runtime-idempotency garandeert dat dit
   dezelfde job teruggeeft als een eerdere response verloren ging.
2. Doorloop anders de immutable lokale inputobjecten in vaste volgorde.
3. Heeft een input een niet-verlopen upload-ID, vraag met HEAD de actuele offset op.
4. Upload ontbrekende bytes vanaf die offset in de door Runtime geadviseerde chunkgrootte.
5. Rond de upload af en bewaar het gecontroleerde READY-object-ID.
6. Is een upload verlopen of definitief verdwenen voordat de definitieve jobrequest is bevroren,
   maak dan alleen voor dat inputobject een nieuwe upload.
7. Bouw de definitieve `CreateJobRequest` deterministisch uit de opgeslagen READY-object-ID's en
   bewaar de volledige request transactioneel in de outbox voordat de eerste POST wordt verstuurd.
8. Verstuur `POST /v2/jobs`.
9. Is de response mogelijk verloren, plan dan een retry van exact de opgeslagen request; maak geen
   nieuwe uploads, wijzig geen object-ID en maak geen nieuwe logische job.
10. Wijst Runtime de opgeslagen request definitief af met `INPUT_OBJECT_NOT_READY`, dan heeft de
    idempotencycontrole van Runtime eerst vastgesteld dat er nog geen bestaande job is. Alleen dan
    mag Product Factory de definitieve request vrijgeven, het betrokken object opnieuw uploaden en
    een nieuwe request met dezelfde inhoudelijke fingerprint opbouwen.
11. Na geaccepteerde create: sla job-ID en status op, markeer de outbox verzonden en wis lokale
    inputbytes volgens het retentiebeleid.

Voorkom parallelle dispatch van dezelfde rij met databaseclaiming/locking. Een transportretry is
geen nieuwe AI-attempt. Runtime is eigenaar van provider-, technische en invalid-structured-output-
attempts; Product Factory toont alleen het gerapporteerde aantal en maakt uitsluitend na een
terminale domeinbeslissing een nieuwe logische taak.

Annulering vóór create markeert de lokale taak terminaal en verwijdert best-effort ongebonden
uploads. Annulering na create gebruikt de Runtime-cancelroute en blijft reconciliëren tot een
terminale Runtime-status.

## Fase 4 — Resultaat, artifacts, events en usage verwerken

### 4.1 Resultaat

Bij `SUCCEEDED`:

1. haal `JobResultView` exact eenmaal idempotent op;
2. valideer het JSON nogmaals tegen het bevroren lokale schema;
3. valideer artifactnaam, MIME, grootte, SHA en declaratie;
4. kopieer domeinartifacts streamend naar de Product Factory-artifactstore;
5. schrijf resultaat, artifactmanifest en usage in één idempotente lokale resultaattransactie;
6. laat daarna de wachtende procesmodule hervatten.

Een onbekend of extra artifact wordt niet gepubliceerd. Een ontbrekend vereist artifact blokkeert
de taak zichtbaar. Een optioneel, niet gemaakt artifact is geldig.

### 4.2 Usage en kosten

Bewaar de Runtime-response als snapshot en toon minimaal:

- lokale task/session, product en `jobKey`;
- `vendorId`, `model`, `mode` en `taskType`;
- aantal Runtime-attempts en usagekwaliteit;
- input-, cached input-, output- en reasoningtokens voor zover gerapporteerd;
- directe/berekende/toegewezen kosten, status, bedrag en valuta;
- `UNAVAILABLE` expliciet als onbekend, nooit als nul.

Haal bij `FAILED` en `CANCELLED` ook `/attempts` op, omdat mislukte pogingen wel usage kunnen hebben.
Som alleen reeds door Runtime berekende waarden van dezelfde valuta/kind/status; bereken geen prijs
uit tokens in Product Factory. Subscriptionkosten kunnen later in Runtime worden gereconcilieerd;
label de lokale kopie daarom als snapshot en link voor de actuele waarheid naar Runtime.

Voeg in Operatie een compacte filter/aggregatie toe per periode, product, `jobKey`, vendor, model en
mode. De centrale cross-projecttotalen blijven in Agent Runtime onder `/v2/usage/summary` en
`/v2/management/usage/summary`.

### 4.3 Events

Bewaar alleen events die de Product Factory-operator nodig heeft. Begrens tekst en retentie. Maak de
eventcursor restartbestendig en idempotent op `(runtime_job_id, sequence)`. De UI mag via SSE sneller
bijwerken, maar moet na reconnect altijd de persistente status/events opnieuw kunnen ophalen.

## Fase 5 — Alle jobproducenten aanpassen

Pas alle `RequestAiTaskCommand`-aanroepen aan en voeg een test toe die faalt als een bekende jobkey
geen schema of expliciete executionselectie heeft.

### Meetings

- `MEETING.CONVERSE` en `MEETING.SUMMARIZE` houden hun bestaande domeinschema's;
- upload de volledige opgebouwde prompt als `prompt.md`;
- declareer geen artifacts;
- behoud idempotente meetingcorrelatie en hervatting.

### Productontwerp

- upload prompt en bevroren context als objecten; voorkom onnodige duplicatie als de context al in
  de prompt zit;
- behoud `repositorySnapshot` met exacte commit;
- declareer `ux-01` tot en met `ux-50` optioneel, `image/png`, ieder maximaal 5 MiB;
- werk prompt, JSON-schema en validatie bij naar logische slotnamen zonder extensie;
- eis dat iedere ADD/REPLACE exact één werkelijk teruggegeven artifact gebruikt en omgekeerd;
- kopieer geaccepteerde UX-artifacts duurzaam vóór epicpublicatie;
- verhoog `PROMPT_TEMPLATE_VERSION`.

### Productplanning

- `PLANNING.SELECT_WORK` en `PLANNING.SLICE_EPIC` blijven JSON-only;
- upload prompts en context; declareer geen outputartifacts;
- blijf bestaande UX-artifactreferenties uit het Product Factory-domein gebruiken, niet tijdelijke
  Runtime-URL's.

### Kwaliteitsbewaking

- behoud de bevroren repositorysnapshot;
- maak in het schema onderscheid tussen een extern bewijs-URL en een Runtime-artifactnaam;
- declareer alleen de vaste `evidence-*`-slots die de prompt werkelijk mag produceren;
- valideer iedere artifactreferentie tegen het manifest en kopieer gepubliceerd bewijs duurzaam;
- verhoog de prompttemplateversie.

### Retry vanuit het domein

De bestaande ontwerp-/kwaliteitvelden met namen zoals `ai_attempt` beschrijven een nieuwe bewuste
Product Factory-taak na een terminale uitkomst. Noem dit in UI en documentatie `taakpoging` of
`nieuwe AI-taak`, zodat het niet wordt verward met technische Runtime-attempts. Alleen Runtime
herprobeert providerfouten of ongeldige structured output binnen dezelfde job.

## Fase 6 — Instellingen, REST, frontend en operatie

Pas de Product Factory-REST-contracten en Flutter-UI atomair aan:

- instellingen tonen en wijzigen `vendorId`, `model`, `mode`, enabled en versie;
- de modelkiezer gebruikt `/v2/execution-options` en kiest altijd een volledige exacte combinatie;
- acceptatie toont `mock / mock / MOCK` en geen scenarionaam in het modelveld;
- takenlijst en detail tonen executionselectie, Runtime-job-ID, status, attemptcount, veilige
  progress, events, usage en kostensnapshot;
- artifactlinks lopen via de Product Factory-downloadcontroller en streamen met correct MIME-type,
  filename, `Content-Length`, ETag en Range-ondersteuning;
- Operatie biedt filters en totalen per lokale `jobKey` en executiondimensies;
- verwijs voor cross-consumerkosten en technische attemptdetails naar de Runtime-monitor.

Houd bestaande Product Factory-API-clients waar praktisch een overgangsrelease compatibel door oude
responsevelden tijdelijk read-only mee te geven. Nieuwe updatebodies moeten de drie v2-velden
vereisen; accepteer geen ambigu oud provider-only request dat een mode zou moeten raden.

## Fase 7 — Acceptatie-fixtures ombouwen

Pas `AcceptanceAiSettingsFixtureContributor` aan zodat elke jobconfiguratie exact
`mock/mock/MOCK` gebruikt. Scenario-identiteit hoort niet meer in `model`.

Implementeer de in de bestaande specificaties beschreven Product Factory Test Control-façade onder
`/api/test-control/ai/mock-responses`. Die façade:

- bestaat alleen in het acceptanceprofiel;
- gebruikt uitsluitend `PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN`;
- vertaalt scenario, versie, jobkey, product en stap naar de exacte Runtime-idempotency key;
- valideert fixture-JSON tegen het echte jobschema vóór verzending;
- kan gerichte resultaten, invalid-output-sequences, fouten, delays en genoemde artifacts
  klaarzetten, opvragen en wissen;
- logt geen token, prompt of gevoelige fixtureinhoud.

Productie registreert deze controller/service niet en heeft geen test-control-secret.

## Fase 8 — Configuratie en deployment

Voeg tijdelijk `PF_AGENT_RUNTIME_API_VERSION=v1|v2` toe voor gecontroleerde omschakeling. Default in
de eerste expandrelease is `v1`; acceptatie wordt eerst `v2`, daarna productie. Verwijder de flag en
v1-client in een latere cleanuprelease.

Pas minimaal aan:

- `RuntimeConfigurationGuard` en `AcceptanceSafetyGuard`;
- voorbeeldconfiguratie en secretdocumentatie;
- acceptance- en productie-ConfigMaps/Sealed Secrets;
- NetworkPolicies voor de interne Runtime-service;
- Product Factory artifact-PVC/mount en storagepad;
- probes en resourcegrenzen voor streaming zonder grote heapbuffers.

Productie vereist een Product Factory-consumertoken en weigert `MOCK`. Acceptatie vereist
`mock/mock/MOCK`, een apart test-control-token, geen admin-/worker-token en de expliciete
acceptatie-Runtime. De interne HTTP-service-URL is toegestaan zoals onder ontwerpbesluiten staat.

Plaats geen OpenAI- of Anthropic-API-key in Product Factory. Subscription- en API-credentials horen
uitsluitend bij Agent Runtime/haar workers.

## Fase 9 — Verplichte tests

### Product Factory unit- en contracttests

Test minimaal:

- alle zes jobkeys leveren een verplicht geldig schema en exacte executionselectie;
- prompt en Markdown/textattachments staan als objectrefs in job-JSON en nooit als Base64;
- upload create/PATCH/HEAD/complete, verkeerde offset, verlopen upload en hervatting;
- upload- en downloadstreams gebruiken begrensde buffers en Range;
- verloren create-response leidt door herhaling van exact dezelfde opgeslagen POST tot exact één
  Runtime-job;
- restart halverwege upload en restart tussen complete en jobcreate;
- dezelfde idempotency key met gewijzigde inhoud faalt;
- outboxdispatchers kunnen dezelfde taak niet parallel dubbel indienen;
- status, cancel, result, events, attempts, usage en alle foutcodes worden veilig gemapt;
- `UNAVAILABLE` usage wordt niet nul;
- geen modelfallback en productie weigert mock;
- artifactdeclaraties, onbekend artifact, ontbrekend vereist artifact, MIME/grootte/hashfout en
  onderbroken duurzame kopie;
- Productontwerp publiceert pas na duurzame artifactkopie en gebruikt alleen `ux-*`-namen;
- Kwaliteitsbewijs maakt correct onderscheid tussen externe URL en Runtime-artifact;
- opruimen verwijdert geen nog gerefereerde domeinartifacts;
- oude databasegegevens worden correct gemapt en het vorige image kan nog op het expandschema
  starten.

### Agent Runtime-contracttests

Bewijs naast fase 0:

- maximaal resultaat en artifactlimieten;
- result bevat de volledige artifactmanifestlijst en usage;
- download-URL is tenantgeïsoleerd en ondersteunt Range;
- incomplete uploads worden na retentie verwijderd;
- jobcontentcleanup verwijdert geen job-, usage- of kostenmetadata;
- v1 blijft functioneel voor andere consumers.

### Integratie en Testbed

- draai alle vaste Product Factory-Testbedscenario's, niet alleen de happy flow;
- test een ongeldige structured response gevolgd door een geldige correctie binnen dezelfde
  Runtime-job;
- test provider/technische fout, ontbrekende mockfixture, delay, annulering en herhaalbare
  domeinretry;
- test een Productontwerpresultaat met meerdere PNG-artifacts en een kwaliteitsresultaat met bewijs;
- test catalogusdrift en offline worker;
- bewijs in Runtime-usage dat de job onder tenant `product-factory` met de juiste vendor, model,
  mode en taskType staat;
- bewijs in Product Factory dat dezelfde taak onder de juiste lokale `jobKey` wordt getoond.

## Fase 10 — Documentatie atomair actualiseren

De huidige normatieve documenten beschrijven nog v1, Base64 en `AiProvider`. Werk ten minste alle
bestanden uit de leeslijst bij. Zoek repositorybreed op:

```text
/v1
AiProvider
CODEX
CLAUDE
MOCKED
contentBase64
Base64
PF_AI_PROVIDER
/job/output/artifacts
```

Niet elke treffer hoeft weg: historische migratietekst en de tijdelijke rollbackadapter mogen v1
benoemen. Actuele specificaties, voorbeelden, API-contracten en operationele instructies moeten wel
de werkelijk gebouwde v2-situatie beschrijven.

Leg ook vast:

- welke content tijdelijk in Runtime staat en welke Product Factory duurzaam overneemt;
- concrete retentie- en herstelprocedures;
- uitleg dat SSE server-push is en polling de duurzame fallback blijft;
- uitleg dat alleen reasoning summaries zichtbaar zijn, geen raw chain-of-thought;
- waar centrale kosten en lokale jobkeykosten worden bekeken;
- hoe een nieuwe consumer of nieuwe Product Factory-jobkey zonder codewijziging aan de Runtime-
  tenant-/catalogusoverzichten verschijnt.

## Fase 11 — Releasevolgorde, productie en rollback

Werk op `main` zoals voor deze repositories gebruikelijk, maar push alleen groene, samenhangende
commits. De migratie wordt in deze volgorde uitgerold:

1. Release de additive Agent Runtime fase-0-contracten; laat `/v1` actief.
2. Voer de Runtime-contractsmokes uit op acceptatie en productie.
3. Release Product Factory expandmigratie plus dual-compatible code met de flag nog op `v1`.
4. Verifieer dat het vorige Product Factory-image nog kan starten op het nieuwe schema.
5. Zet Product Factory-acceptatie op `v2` en doorloop alle Testbedscenario's.
6. Controleer uploads, artifacts, events en usage handmatig in beide operationele schermen.
7. Zet productie op `v2` en voer één echte, credentialloze, niet-publicerende
   `openai/gpt-5.6-sol/SUBSCRIPTION`-smoke uit.
8. Bewijs via metrics/logs en codezoekactie dat Product Factory-productie geen `/v1` meer aanroept.
9. Observeer minimaal één normale schedulecyclus van ieder actief Product Factory-proces.
10. Verwijder pas in een latere release de v1-client, featureflag, dual-write en oude providerkolom.

Rollback vóór stap 10 bestaat uit terugzetten van de Product Factory-flag naar `v1` of redeployen
van het vorige immutable image. De additive databasekolommen blijven staan en Agent Runtime v1
blijft beschikbaar. Verwijder bij rollback geen v2-jobs of artifacts; laat reconciliatie na herstel
op basis van de opgeslagen correlaties doorgaan.

## Lokale en CI-verificatie

Voer vanuit `GIT_ROOT/product-factory` minimaal uit:

```bash
mvn -B --no-transfer-progress verify
cd product-factory-frontend
flutter pub get
flutter analyze
flutter test
```

Voer daarnaast de PostgreSQL-migratiesmoke, deploymentverificatie en de gerichte smoke uit met de
in de repository aanwezige scripts. Voer vanuit `GIT_ROOT/agent-runtime` de volledige eigen build,
contracttests, OpenAPI-validatie en deploymentchecks uit. Gebruik de repository-README en workflows
voor de exacte actuele commando's.

## Aanbevolen commitgrenzen

1. Agent Runtime v2-catalogi.
2. Agent Runtime test-control-rol, gerichte mocks en OpenAPI/tests.
3. Product Factory expandmigratie en publieke executioncontracten.
4. V2-upload/jobclient en outboxherstel.
5. Resultaat, events, usage en duurzame artifactstore.
6. Meetings, Productontwerp, Productplanning en Kwaliteitsbewaking.
7. REST, Flutter, Testbed en configuratie.
8. Normatieve documentatie, regressies en releasecorrecties.
9. Latere cleanup van v1-compatibiliteit na bewezen stabiliteit.

## Indicatieve omvang

Reken voor één ervaren agent, inclusief tests en twee repositories, op ongeveer 10–16 gerichte
werkdagen:

| Onderdeel | Indicatie |
|---|---:|
| Agent Runtime-voorwaarden en mocks | 2–3 dagen |
| Product Factory-contract, migratie en v2-client | 3–4 dagen |
| herstel, events, usage en artifactlifecycle | 2–4 dagen |
| jobproducenten, frontend, Testbed en documentatie | 2–3 dagen |
| acceptatie, productie en herstelcorrecties | 1–2 dagen |

Dit is een planningsindicatie, geen reden om delen van de definitie van klaar over te slaan.

## Buiten scope

- andere consumers naar v2 migreren;
- Product Factory rechtstreeks met OpenAI, Anthropic of een andere AI-provider laten praten;
- providercredentials in Product Factory beheren;
- raw chain-of-thought verzamelen of tonen;
- een model- of providerfallback invoeren;
- technische worker-, lease-, fencing- of providerretrylogica in Product Factory bouwen;
- de bestaande Software Factory-dispatchergrens wijzigen, behalve waar gedeelde artifactreferenties
  compile- of contractmatig moeten worden aangepast;
- de Agent Runtime-interne fileshare rechtstreeks mounten of uitlezen vanuit Product Factory.

## Definitie van klaar

Deze stap is pas klaar als alle verplichte tests groen zijn, de normatieve documentatie de code
weerspiegelt, Agent Runtime v1 aantoonbaar beschikbaar blijft, Product Factory-acceptatie alle vaste
scenario's via v2 doorloopt en dezelfde Product Factory-release gezond op productie staat.

Een productiejob moet end-to-end aantonen: expliciete `openai/gpt-5.6-sol/SUBSCRIPTION`-selectie,
hervatbare promptupload, exact één idempotente Runtime-job, gevalideerd structured resultaat,
event-/statusreconciliatie, correcte artifactovername waar van toepassing en usage/kosten zichtbaar
in zowel de centrale Runtime-context als de lokale Product Factory-jobcontext. Daarna bestaan in
Product Factory-productieverkeer geen v1-job-, catalogus- of artifactcalls meer.
