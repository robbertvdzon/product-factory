# Stap 4 — AI-uitvoering via Agent Runtime

## Doel

Maak één generieke Product Factory-façade waarmee iedere latere module een complete AI-taak
duurzaam via de gedeelde Agent Runtime kan laten uitvoeren, zonder dat Product Factory een eigen
technische queue, laptopworker of attemptadministratie bouwt.

## Voorwaarde buiten deze repository

De Agent Runtime implementeert eerst het vereenvoudigde `APPLICATION_WORK` v2-contract uit
[`agent-runtime/docs/application-work-v2.md`](https://github.com/robbertvdzon/agent-runtime/blob/main/docs/application-work-v2.md),
waaronder:

- één complete `prompt` in plaats van `instructions` plus `input`;
- kleine Base64-inputattachments en file-based outputartifacts;
- alleen environmentkeynamen in jobs en databases;
- workerontdekking vanuit lokaal `project-credentials.env`;
- een gefilterde environmentcatalogus-API;
- onafhankelijke harde attempt-time-outs in server en worker;
- volledige v2-OpenAPI-responsecontracten;
- server-side `MOCKED` buiten productie.

Product Factory mag tijdelijk tegen Runtime v1 ontwikkelen met een adapter, maar noemt stap 4 pas
gereed wanneer de productieaansluiting v2 gebruikt. Product Factory wijzigt de Agent Runtime-code
niet vanuit deze stap.

## Globale scope

- Behoud `AiJobConfiguration` als Product Factory-instelling per opaque `AiJobKey`; jobkey,
  configuratieversie en prompttemplateversie blijven lokaal.
- Pas het bestaande `product-factory-api`-AI-contract aan: voeg `agentRole`, één `prompt`,
  `promptTemplateVersion`, inputattachments en een verplichte uitvoeringstime-out toe; verwijder
  `instruction`, `inputJson`, `TestEnvironmentAccess` en vrije credentialreferences.
- Bouw een geïnjecteerde HTTPS-client voor Agent Runtime v2 met time-outs, veilige foutvertaling,
  bearer-authenticatie en volledige contracttests.
- Bewaar lokaal alleen domeincorrelatie en een transactionele outbox: product, module, sessie of
  meeting, agentrol, jobkey/configuratie-audit, Runtime-idempotentiesleutel en Runtime-job-ID.
- Dien iedere lokale taak idempotent als `APPLICATION_WORK` in en reconcileer status, veilige
  voortgang, resultaat en artifacts zonder een serverthread open te houden.
- Maak geen `AiTaskAttempt`, `AiWorkerSession`, lease, fencing token, worker-API, technische retryqueue
  of eigen artifact-BLOB-opslag in Product Factory.
- Map Runtime-hoofdstatussen naar `PENDING_SUBMISSION`, `QUEUED`, `WAITING_FOR_WORKER`, `RUNNING`,
  `SUCCEEDED`, `FAILED` en `CANCELLED`; toon interne Runtime-fasen alleen als read-only operatieinfo.
- Laat Runtime technische retries beheren. Een Product Factory-module maakt pas na een terminale
  uitkomst bewust een nieuwe logische taak.
- Implementeer annulering als lokale reden plus idempotente Runtime-cancelcall en blijf reconciliëren
  tot terminale bevestiging.
- Lees de gefilterde Runtime-catalogus met ontdekte environmentkeynamen en beschikbaarheid.
- Bewaar per product welke ontdekte keys functioneel bij het product horen en welke agentrollen ze
  mogen ontvangen. Bewaar nooit waarden.
- Leid `environmentKeys` uitsluitend backend-side af uit product, vertrouwde agentrol en actieve
  grants. Frontend, prompt en model kunnen de selectie niet verruimen.
- Voeg onder productinstellingen een scherm toe voor bekende/beschikbare keys en de koppeling aan
  rollen. Toon ontbrekende workerconfiguratie zonder waarden.
- Geef kleine inputattachments begrensd door naar Runtime en presenteer outputartifactreferenties
  binnen Product Factory-autorisatie.
- Laat `MOCKED` door Agent Runtime-acceptatie uitvoeren via dezelfde lokale outbox, statusvertaling,
  responseschema- en domeinverwerking als echte jobs.
- Activeer Meeting Agent en notulenagent via dezelfde façade; hun prompts bevatten de geldige
  meetingcontext maar krijgen alleen credentials van hun expliciete rolgrants.
- Maak Runtime-jobstatus, externe correlatie en veilige progress zichtbaar in de operationele
  frontend; verwijs voor worker- en attemptdiagnose naar de Runtime-monitor.

## Configuratie

Voeg toe:

| Sleutel | Omgeving | Doel |
|---|---|---|
| `PF_AGENT_RUNTIME_URL` | lokaal, acceptatie, productie | basis-URL van de bijbehorende Runtime-server |
| `PF_AGENT_RUNTIME_TOKEN` | lokaal en productie | gescopete Product Factory-consumentcredential |
| `PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN` | alleen integratie/acceptatie wanneer nodig | afzonderlijk gescopete mockfixturecredential, nooit admin of worker |

Verwijder de gereserveerde `PF_AGENT_WORKER_TOKEN`; Product Factory bezit geen worker. Productie
vereist HTTPS, een niet-lege consumentcredential en weigert `MOCKED`. Acceptatie mag alleen de
acceptatie-Runtime en `MOCKED` gebruiken en krijgt geen productie-, worker- of admincredential.

### Bestaande codecontracten die in deze stap wijzigen

- `product-factory-api/.../api/ai/AiContract.kt`: pas `RequestAiTaskCommand`, `AiTaskStatus` en
  `AiTaskDetails` aan en voeg de environmentcatalogus- en rolgrantcontracten toe zoals gespecificeerd
  in [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md).
- `product-factory-api/.../api/product/ProductContract.kt`: verwijder
  `TestEnvironmentConfiguration.credentialReferences`; credentialnamen horen niet meer bij de
  testomgeving-DTO.
- `AcceptanceSafetyGuard`: vervang de controle op een leeg `PF_AGENT_WORKER_TOKEN` door fail-closed
  validatie van de acceptatie-Runtime-URL, provider `MOCKED` en gescopete consument/test-control-
  credentials. Alleen deze Runtime-mutaties worden door de acceptance-egressgate toegestaan.
- `RuntimeConfigurationGuard`, `secrets.env.example`, Sealed Secret-input en deploymentconfiguratie:
  voeg de drie Runtime-sleutels toe en verwijder de oude workercredential.
- Bestaande tests die `PF_AGENT_WORKER_TOKEN` zetten of een eigen mockexecutor/worker veronderstellen
  worden samen met deze contractwijziging vervangen.

## Database en migraties

Voeg Product Factory-tabellen toe voor:

- `AiJobConfiguration`;
- lokale `AiTask`-correlatie en statusprojectie;
- transactionele Runtime-outbox met bevroren indieningspayload;
- eenmaal geaccepteerde Runtime-resultaatreferentie en veilige terminale fout;
- `ProductEnvironmentVariable` met uitsluitend naam en metadata;
- `AgentEnvironmentVariableGrant` per product en agentrol.

Voeg nadrukkelijk geen tabellen toe voor worker, attempt, lease, fencing token of lokale
projectcredentialwaarden. Idempotentie bewaakt maximaal één extern Runtime-job-ID per lokale taak.

## Acceptatie en teststrategie

- Unit tests van procesmodules gebruiken een kleine in-memory fake van de Product Factory-
  `AiExecutionService`.
- Adaptercontracttests draaien tegen een HTTP-stub die exact Runtime v2 implementeert.
- Integratie- en UI-acceptatietests gebruiken de echte Agent Runtime-acceptatieserver met
  `MOCKED`; een laptopworker is niet nodig.
- Testbed bereidt Runtime-mockantwoorden met scenario-, jobkey-, product- en stapcorrelatie voor via
  een afzonderlijk gescopete test-controlroute.
- Test indiening met verloren response, dubbele dispatch, statusreconciliatie, resultaatreplay,
  annulering en tijdelijk onbereikbare Runtime.
- Test dat een proces nooit zelf environmentkeys kan toevoegen en dat alleen actieve product- en
  rolgrants in de externe aanvraag belanden.
- Test catalogusdrift: bekende maar offline key, onbekende key, worker die later online komt en een
  verwijderde key.
- Test inputattachmentlimieten en veilige outputartifactautorisatie.
- Agent Runtime-contracttests bewijzen harde time-out, slaap/recovery, fencing, credentialselectie,
  path traversal en artifactcollectie; Product Factory dupliceert die technische tests niet.
- Een bewust gestarte productiesmoke dient één echte, credentialloze testjob in en bewijst de route
  tot en met Codex of Claude zonder domeindata te publiceren.

## Buiten scope

- technische workerimplementatie, Dockercontainer, providercredentials, queue, attempts, leases,
  harde deadlinebewaking en artifactopslag in Agent Runtime;
- secretwaarden beheren of tonen in Product Factory;
- agentrollen of jobkeys naar Agent Runtime verplaatsen;
- inhoudelijke prompts en domeinvalidatie van Productontwerp, Productplanning en
  Kwaliteitsbewaking, behalve de Meeting Agent/notulenactivatie die bij deze stap hoort;
- een OpenShift-native providerworker: echte Codex/Claude-uitvoering blijft voorlopig via de
  gedeelde lokale Runtime-worker lopen.

## Specificaties

- [AI-uitvoering via Agent Runtime](../gedeelde-modules/ai-uitvoering.md)
- [Agent Runtime-integratie en taakcontainer](../gedeelde-modules/ai-worker.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Frontend](../stakeholder/frontend.md)
- [Agent Runtime APPLICATION_WORK v2](https://github.com/robbertvdzon/agent-runtime/blob/main/docs/application-work-v2.md)

## Klaar wanneer

Een Product Factory-proces kan via lokale outbox en Runtime v2 exact één echte of gemockte
`APPLICATION_WORK`-job indienen, zonder eigen worker- of attemptcode. Een verloren response maakt
geen dubbele job. Status, annulering, resultaat en artifacts worden duurzaam gereconcilieerd en een
wachtende processessie hervat zonder open thread.

De frontend kan per product ontdekte environmentkeynamen aan agentrollen koppelen, toont actuele
workerbeschikbaarheid en verstuurt nooit waarden. Een echte job ontvangt alleen de backend-side
afgeleide subset. Een onbekende key blokkeert zichtbaar; een bekende offline key laat de Runtime-job
wachten. Agent Runtime bewijst dat een harde
attemptdeadline ook bij worker- of laptopproblemen niet kan worden verlengd.

`MOCKED` draait volledig server-side in Runtime-acceptatie; productie weigert het. Product Factory
kan volledig op OpenShift draaien en heeft geen eigen laptopservice, terwijl echte providerprocessen
voorlopig door de gedeelde lokale Runtime-worker worden uitgevoerd.
