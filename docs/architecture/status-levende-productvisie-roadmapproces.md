# Overdracht: levende productvisie en roadmapproces

## Doel van dit document

Dit is de operationele overdracht bij
[`plan-levende-productvisie-roadmapproces.md`](plan-levende-productvisie-roadmapproces.md). Een nieuwe
AI-sessie kan hiermee de resterende productieacceptatie afmaken zonder eerdere conversatiecontext.
Lees desondanks eerst het volledige implementatieplan: dat document is normatief voor gedrag en
acceptatiecriteria; deze overdracht beschrijft de actuele realisatie en het resterende werk.

Snapshot: **2026-08-17, circa 22:21 CEST**. De twee genoemde productiesessies lopen zelfstandig
door, dus haal hun status altijd opnieuw op voordat je een conclusie trekt of code wijzigt.

## Samenvatting

- Fases 1 tot en met 7 zijn in code, migraties, contracten, backend, agentworker, dashboard en
  documentatie geïmplementeerd.
- `main` was bij deze snapshot schoon en gelijk aan `origin/main` op commit
  `6cd4cd755083d0a125b3e63ca02bffa12dca2dd1`.
- De laatste functionele commit is `66ccad91fc9f0b0a2a204d2346ecf906adf9aacc`
  (`fix: make critic correction contract explicit`). Commit `6cd4cd7` pint het bijbehorende
  runtime-image.
- Productie draait exact
  `ghcr.io/robbertvdzon/product-factory-runtime:sha-66ccad91fc9f0b0a2a204d2346ecf906adf9aacc`.
- Argo CD rapporteerde `Synced / Healthy`, de runtime had één ready replica en
  `/actuator/health` rapporteerde `UP`.
- De volledige lokale Maven-reactor is groen: **227 tests**, nul failures/errors.
- De volledige Fluttercontrole was groen: **452 tests**, `flutter analyze`, release-webbuild,
  goldens en de browser-DOM-semantiekcontrole.
- GitHub Actions is groen voor de laatste functionele commit:
  - repositoryverificatie: <https://github.com/robbertvdzon/product-factory/actions/runs/32064475681>;
  - runtime-image en promotie: <https://github.com/robbertvdzon/product-factory/actions/runs/32064475964>.
- Het lokale runtime-image is na de laatste wijziging opnieuw gebouwd als
  `product-factory-runtime:latest`. Agent- en dashboardimages zijn na hun laatste relevante
  wijzigingen eveneens opnieuw gebouwd.
- De lokale Mac-agentworker draait als LaunchAgent en kan vier taken parallel uitvoeren.
- De lokale Software Factory luisterde op `8080`, `9080` en `9090`; de frontend op `9080` gaf
  HTTP 200. Product Factory zelf is lokaal bewust niet op `8080` gestart, omdat daar de native
  Software Factory-runtime draait.

De implementatie mag nog **niet definitief afgerond** worden genoemd. Twee verse volledige
productiecycli moeten nog terminal groen eindigen en daarna moeten de productieacceptatiecriteria
expliciet worden gecontroleerd.

## Wat is gerealiseerd

### Fases 1 tot en met 7

- Additieve v2-contracten en Flyway-migratie V29.
- Productkeuze `roadmapProcessVersion`, met veilige standaard `legacy-v1` en per product
  `living-vision-v2`.
- Eén gedeeld productagnostisch procescontract, gesloten JSON-schema's, rolbevoegdheden en
  expliciete handoffs.
- Append-only inspiratie-, ideeën-, concept-, onderzoeks- en activatiecatalogi met productisolatie
  en versiegeschiedenis.
- Persistente DAG met retries, herstel na runtimeherstart, verplichte/optionele stappen en begrensde
  paralleliteit.
- Drie scouts, curator, maximaal twee parallelle UX-concepten, parallel haalbaarheidsonderzoek,
  UX-director, toekomststrateeg, onafhankelijke criticus, roadmapmanager en één atomair
  activatiepunt.
- Echte gegenereerde PNG-assets via de Mac-agentworker, mediaopslag en dashboardweergave.
- Eén begrensde correctieronde tussen criticus en strateeg; een blijvende afwijzing blokkeert
  activatie en laat de vorige actieve visie intact.
- Atomaire activatie van toekomstige visie en roadmapwijzigingen.
- Dashboardondersteuning voor proceskeuze, sessiegrafiek, portfolio, idee-/concepthistorie en media.
- Idempotente migratie van legacy-visiegegevens en operationele rollback naar `legacy-v1`, waarbij
  v2-historie behouden blijft.
- Functionele, technische, architectuur-, ontwikkel- en deploymentdocumentatie.

### Productieproblemen die al zijn gevonden en opgelost

De productie-E2E's waren bewust onderdeel van de verificatie en hebben de volgende generieke
problemen zichtbaar gemaakt:

| Commit | Oplossing |
|---|---|
| `ea3a790` | Providercompatibele gesloten roadmap-schema's. |
| `6721557` | Veilige werkelijke parallelle agentuitvoering. |
| `7ddb054` | Hervatten van onderbroken roadmapsessies bij runtime-opstart. |
| `adedef4` | Transactieve, idempotente graafinitialisatie. |
| `f47f3a7` | Curator mag bestaande ideeën vanuit actuele ontdekking verfijnen. |
| `aab4a2a` | Onherstelbare verplichte fout maakt de sessie terminal. |
| `6c34022` | Productieformaat gegenereerde beelden tot 5 MiB per beeld. |
| `2f4a2ab` | Exact één begrensde criticuscorrectieronde. |
| `ead1b13` | Concrete UX-directorrevisies worden toegepast. |
| `9d8f252` | Grote beeldresultaten passen door de WebSocket-transportgrens. |
| `57af766` | Deterministische isolatie van de concurrency-integratietest. |
| `614a5a8` | Binaire assetinhoud wordt uit downstream handoffprompts gehouden. |
| `0444e6e` | Parallel haalbaarheidsonderzoek koppelt alleen aan curatorideeën en gebruikt `conceptKey=null`. |
| `66ccad9` | Correctie en herbeoordeling behandelen ieder criticusverzoek expliciet. |

Verzwak deze beveiligingen niet om een productiecyclus kunstmatig te laten slagen. Een inhoudelijk
correcte criticusblokkade is een geldige mislukte sessie; los alleen een aantoonbaar proces-,
contract-, transport- of persistentieprobleem op.

## Actuele lopende productiecycli

### HKH Autopilot

- Product: `hkh-autopilot`
- Sessie: `roadmap-session-hkh-autopilot-0023`
- Snapshotstatus: `RUNNING`
- Stappen: 3 `COMPLETED`, 1 `RUNNING`, 9 `PENDING`
- Actief bij de snapshot: `vision-curator`, scope `session`, poging 1
- Geen `errorMessage`.

### Product Factory

- Product: `product-factory`
- Sessie: `roadmap-session-product-factory-0013`
- Snapshotstatus: `RUNNING`
- Stappen: 4 `COMPLETED`, 4 `RUNNING`, 5 `PENDING`
- Actief bij de snapshot, allemaal poging 1:
  - `feasibility`, `selection-1`;
  - `feasibility`, `selection-2`;
  - `ux-concept`, `selection-1`;
  - `ux-concept`, `selection-2`.
- Geen `errorMessage`.

De productiecycli worden uitgevoerd door lokale `codex --search exec`-processen onder de
Mac-agentworker. Houd de Mac wakker en stop of herstart de agentworker niet zolang taken actief
zijn, tenzij een aantoonbaar vastgelopen proces dat noodzakelijk maakt.

## Eerdere productieproeven en hun betekenis

- `roadmap-session-product-factory-0012` faalde bij haalbaarheid doordat een agent een
  `conceptKey` uit de parallelle UX-tak gebruikte. Dit is generiek opgelost in `0444e6e`; sessie
  `0013` is de eerste volledige herproef met die fix.
- `roadmap-session-hkh-autopilot-0022` bereikte roadmapmanagement, maar activatie werd terecht door
  de criticus geblokkeerd: een capability stond nog op `PROVEN` terwijl het bewijs slechts een deel
  van de claim droeg. De correctieronde had niet ieder revisieverzoek toegepast. De vorige actieve
  visie bleef intact, waarmee de negatieve atomaire acceptatieproef slaagde. De correctieprompt is
  generiek aangescherpt in `66ccad9`; sessie `0023` is de herproef.
- Oudere mislukte sessies waren diagnostische runs vóór de hierboven genoemde fixes. Probeer ze
  niet te hervatten en verwijder ze niet: ze zijn nuttig auditbewijs van append-only historie en
  foutisolatie.

## Operationele topologie

- Repository: `/Users/robbertvdzon/git/product-factory`
- Productworkspaces worden door de bestaande Factory beheerd; wijzig geen workspace buiten de
  productscope van een agenttaak.
- Productie namespace: `product-factory`
- Argo CD application: `product-factory` in namespace `argocd`
- Runtime Service: `service/runtime`, interne poort `8080`
- Lokale agentworker LaunchAgent: `nl.vdzon.product-factory-agentworker`
- Lokale Software Factory-repository: `/Users/robbertvdzon/git/softwarefactory`
- De productie-PostgreSQL gebruikt momenteel `emptyDir`. **Herstart of verwijder de postgres-pod
  niet**: podvervanging kan productiedata wissen. Een runtime-rollout vervangt de postgres-pod niet.
- Gebruik geen secrets in prompts, logs of dit document. Bestaande `oc`, `gh`, Docker en LaunchAgent
  sessies waren al geauthenticeerd.

## Status opnieuw ophalen

Een bestaande port-forward kan na een runtime-rollout dood zijn. Start zo nodig een nieuwe op een
vrije poort; hieronder wordt `18081` gebruikt:

```bash
oc port-forward -n product-factory service/runtime 18081:8080
```

Gebruik in een tweede terminal:

```bash
curl -fsS http://127.0.0.1:18081/actuator/health | jq .

show_session() {
  slug="$1"
  session_id="$2"
  curl -fsS \
    "http://127.0.0.1:18081/api/products/$slug/roadmap/sessions/$session_id" | jq .
  curl -fsS \
    "http://127.0.0.1:18081/api/products/$slug/roadmap/living-vision/sessions/$session_id/steps" \
    | jq '[.[] | {role, scopeKey, status, attempt, errorMessage}]'
}

show_session hkh-autopilot roadmap-session-hkh-autopilot-0023
show_session product-factory roadmap-session-product-factory-0013
```

Controleer de worker en daadwerkelijk actieve CLI-agents zonder processen te wijzigen:

```bash
./product-factory agent-worker-status
ps -Ao pid,ppid,etime,command | rg '[c]odex --search exec'
tail -n 100 work/agentworker.log
```

## Resterend werk, in verplichte volgorde

### 1. Laat beide huidige sessies terminal worden

- Monitor met intervallen van 15–30 seconden; start geen tweede sessie voor hetzelfde product.
- `COMPLETED` is alleen geldig als alle 13 stappen `COMPLETED` of expliciet `SKIPPED` zijn en de
  stap `activation` zelf `COMPLETED` is.
- Bij `FAILED`: leg eerst sessie- en stapfout vast. Inspecteer alleen de relevante, compacte handoff;
  dump geen grote base64-payloads.
- Bepaal of de fout een terechte productinhoudelijke blokkade of een softwaredefect is. Een terechte
  criticusafwijzing bewijst de negatieve route maar levert nog geen positieve volledige cyclus op;
  start dan, na analyse, een nieuwe sessie zonder de gate te verzwakken.

Compacte foutdiagnose:

```bash
slug='<productslug>'
session_id='<sessie-id>'
curl -fsS \
  "http://127.0.0.1:18081/api/products/$slug/roadmap/sessions/$session_id" \
  | jq '{status,errorMessage,summary}'
curl -fsS \
  "http://127.0.0.1:18081/api/products/$slug/roadmap/living-vision/sessions/$session_id/steps" \
  | jq '.[] | select(.status=="FAILED") | {role,scopeKey,attempt,errorMessage}'
```

Voor criticusdiagnose, zonder de volledige strategie te printen:

```bash
curl -fsS \
  "http://127.0.0.1:18081/api/products/$slug/roadmap/living-vision/sessions/$session_id/steps" \
  | jq '.[] | select(.role=="vision-critic") |
      {status,errorMessage,approved:.handoff.payload.approved,
       correctionRound:.handoff.payload.correctionRound,
       revisionRequests:.handoff.payload.revisionRequests,
       summary:.handoff.summary}'
```

### 2. Los alleen echte defecten generiek op

Als een actuele sessie een nieuw softwaredefect toont:

1. reproduceer en bepaal de oorzaak;
2. voeg een regressietest toe;
3. hardcode geen productnaam, domein, bron of huidige sessie-ID in productiecode of prompts;
4. behoud v1-API's en `legacy-v1`;
5. behoud de onafhankelijke criticus, maximaal één correctieronde en atomaire activatie;
6. draai minimaal de gerichte test en daarna `mvn -B --no-transfer-progress clean verify`;
7. draai frontendchecks alleen opnieuw als frontend of gedeelde zichtbare contracten wijzigen;
8. bouw ieder geraakt lokaal Docker-image opnieuw;
9. commit en push rechtstreeks naar `main`, zoals door de eigenaar expliciet gevraagd;
10. wacht op groene GitHub-verificatie en imagepromotie;
11. pull de automatisch gemaakte deploymentcommit voordat je verder commit;
12. laat Argo CD verversen en verifieer het exacte SHA-image;
13. start voor beide producten een verse sessie als hun vorige positieve proef niet meer tegen het
    actuele runtime-SHA liep.

Gebruik voor een runtimewijziging lokaal:

```bash
mvn -B --no-transfer-progress clean verify
docker build -f productfactory/Dockerfile -t product-factory-runtime:latest .
git add <uitsluitend bedoelde bestanden>
git commit -m '<concrete boodschap>'
git push origin main
```

Volg CI:

```bash
gh run list --branch main --limit 6 \
  --json databaseId,workflowName,status,conclusion,headSha,url
gh run watch <run-id> --exit-status --interval 5
```

Na de automatische imagepromotie:

```bash
git pull --ff-only origin main
oc annotate application product-factory -n argocd \
  argocd.argoproj.io/refresh=hard --overwrite
oc rollout status deployment/runtime -n product-factory --timeout=180s
oc get deployment runtime -n product-factory \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
oc get application product-factory -n argocd \
  -o jsonpath='{.status.sync.status}{" "}{.status.health.status}{"\n"}'
```

Een runtime-rollout verbreekt de port-forward; start die daarna opnieuw.

### 3. Bewijs positieve productieactivatie

Voor iedere succesvol voltooide sessie:

- sessiestatus is `COMPLETED` en heeft `completedAt` en een samenvatting;
- `activation` is `COMPLETED`;
- iedere vereiste rol is `COMPLETED`;
- eventuele optionele scoutfout is alleen `SKIPPED` volgens het procescontract;
- de criticus heeft `approved=true`;
- bij een correctie is `correctionRound=1` en bestaat geen tweede correctieronde;
- UX-concepten hebben mobiele én desktopversies met echte media-assets;
- onderzoeksresultaten verwijzen uitsluitend naar ideeën/capabilities binnen hetzelfde product;
- roadmapmanageroutput bevat bij discovery-epics zowel bewijsdoel als besliscriterium;
- de nieuwe actieve visie heeft `createdBySessionId` gelijk aan de succesvolle sessie;
- visiehistorie is één versie gegroeid en oudere versies zijn intact;
- portfolio, concept-, bewijs- en roadmapdata zijn alleen onder het bedoelde product zichtbaar.

Nuttige read-only API's:

```bash
curl -fsS "http://127.0.0.1:18081/api/products/$slug/roadmap/vision" | jq .
curl -fsS "http://127.0.0.1:18081/api/products/$slug/roadmap/vision/history" | jq .
curl -fsS "http://127.0.0.1:18081/api/products/$slug/roadmap/epics" | jq .
curl -fsS "http://127.0.0.1:18081/api/products/$slug/roadmap/themes" | jq .
curl -fsS \
  "http://127.0.0.1:18081/api/products/$slug/roadmap/living-vision/portfolio" | jq .
```

De `/themes`-route is een backward-compatible alias en moet dezelfde bestaande roadmap blijven
leveren. Media-inhoud is beschikbaar via
`/api/products/{slug}/media/{mediaId}/content`; via het dashboard bestaat daarnaast de
living-vision-media-proxy.

### 4. Rond negatieve, isolatie-, migratie- en rollbackbewijzen af

- Bewaar de eerdere mislukte sessies en toon aan dat hun sessie-ID niet de actieve visie heeft
  gemaakt. HKH-sessie `0022` is het belangrijkste negatieve criticusbewijs.
- Vergelijk beide portfolioresponses en controleer dat elk object zijn eigen `productSlug` draagt.
- Verifieer de idempotente legacy-migratie via tests; productie is eerder tweemaal gemigreerd en de
  tweede migratie voegde niets dubbel toe. Herhaal geen muterende migratie zonder reden.
- Test operationele rollback pas nadat geen sessie voor het gekozen product actief is. Zet via
  `PUT /api/products/{slug}/settings` alleen `roadmapProcessVersion` tijdelijk op `legacy-v1`,
  controleer dat de legacy-roadmap-API blijft werken en dat v2-historie blijft bestaan, en zet het
  veld direct terug op `living-vision-v2`. Leg aantallen/historie voor en na vast.
- Laat een product nooit per ongeluk op `legacy-v1` achter.

Voorbeeld van de minimale instellingmutatie:

```bash
curl -fsS -X PUT \
  "http://127.0.0.1:18081/api/products/$slug/settings" \
  -H 'Content-Type: application/json' \
  -d '{"roadmapProcessVersion":"legacy-v1"}' | jq .roadmapProcessVersion

# Controleer legacy-API en v2-historie, herstel daarna altijd:
curl -fsS -X PUT \
  "http://127.0.0.1:18081/api/products/$slug/settings" \
  -H 'Content-Type: application/json' \
  -d '{"roadmapProcessVersion":"living-vision-v2"}' | jq .roadmapProcessVersion
```

### 5. Eindcontrole van lokaal, Git en OpenShift

- `git status --short --branch` is schoon en gelijk aan `origin/main`.
- Alle toepasselijke checks uit `.factory/verification.yaml` zijn groen.
- Alle geraakte lokale images zijn opnieuw gebouwd.
- `./product-factory agent-worker-status` is gezond en er zijn geen achtergebleven agentprocessen
  van terminale sessies.
- Software Factory blijft lokaal bereikbaar; neem poort `8080` niet over met Product Factory.
- Productie-Argo is `Synced / Healthy`, deployments hebben hun gewenste ready replica's en runtime
  health is `UP`.
- Het gedeployde runtime-image verwijst naar de laatste functionele commit, niet naar een oudere tag.
- Beide producten staan op `living-vision-v2`.
- Rapporteer de definitieve sessie-ID's, visieversies, testtotalen, CI-links, image-SHA's en het
  expliciete bewijs per cross-cutting criterium.

## Relevante code en tests

- Procescontract, rollen en prompts:
  `productfactory/src/main/kotlin/nl/vdzon/productfactory/roadmap/LivingVisionFoundation.kt`
- DAG, retries, correctieronde en activatie:
  `productfactory/src/main/kotlin/nl/vdzon/productfactory/roadmap/RoadmapProcessOrchestrator.kt`
- Gesloten schema's:
  `productfactory/src/main/kotlin/nl/vdzon/productfactory/roadmap/LivingVisionSchemas.kt`
- Catalogus/API:
  `productfactory/src/main/kotlin/nl/vdzon/productfactory/roadmap/api/` en
  `LivingVisionController.kt`
- Atomaire activatie:
  `productfactory/src/main/kotlin/nl/vdzon/productfactory/roadmap/RoadmapProcessOrchestrator.kt`
  en `LivingVisionActivator`
- Migratie: `productfactory/src/main/resources/db/migration/V29__living_vision_v2.sql`
- Agentbeeldtransport: `agentworker/` en `dashboard-backend/`
- Dashboard: `dashboard-frontend/lib/roadmap.dart`, `dashboard-frontend/lib/main.dart` en
  `dashboard-backend/src/main/kotlin/nl/vdzon/productfactory/dashboard/RoadmapApi.kt`
- Belangrijkste E2E/regressietest:
  `productfactory/src/test/kotlin/nl/vdzon/productfactory/roadmap/RoadmapProcessOrchestratorTest.kt`
- Overige roadmaptests: `productfactory/src/test/kotlin/nl/vdzon/productfactory/roadmap/`
- Verificatiecontract: `.factory/verification.yaml`

## Bekende valkuilen

- Grote beelden zijn toegestaan tot 5 MiB per stuk, maximaal zes per agentresultaat. Verlaag de
  WebSocketbuffer of het limiet niet zonder de grote-resultaatintegratietest aan te passen.
- Handoffs mogen metadata en media-ID's bevatten, maar geen `base64Content` of lokaal
  `temporaryPath`; anders kan de OS-argumentlimiet opnieuw worden geraakt.
- UX en haalbaarheid lopen parallel. Haalbaarheid mag daarom nooit een UX-`conceptKey` aannemen en
  gebruikt `conceptKey=null` totdat een latere rol beide takken samenbrengt.
- De criticus moet de `correctedStrategy` herbeoordelen en ieder eerder `revisionRequest`
  afzonderlijk controleren. Gooi de oorspronkelijke kritiek of correctieartefacten niet weg.
- De imageworkflow pusht zelf een deploymentcommit naar `main`. Pull die commit voor een volgende
  push om een non-fast-forward of verloren imagepin te voorkomen.
- Een `Synced` Argo-status kan kort nog op de vorige Git-revisie slaan; controleer altijd ook de
  concrete deployment-image-tag.
- Een rollout beëindigt een podgebonden `oc port-forward`.
- Start geen lokale Product Factory-compose op poort `8080` zolang Software Factory daar draait.

## Definitie van klaar voor de opvolgende AI-sessie

Stop pas wanneer alle volgende punten aantoonbaar waar zijn:

1. fases 1–7 en alle cross-cutting criteria uit het plan zijn groen;
2. ten minste één volledige `living-vision-v2`-productiesessie per geconfigureerd product eindigt
   positief met atomaire activatie op de laatste runtimeversie;
3. de negatieve criticusroute laat aantoonbaar de vorige actieve visie intact;
4. legacy-API's, migratie, rollback en productscheiding zijn geverifieerd;
5. backend, frontend, migratie, architectuur en E2E zijn groen;
6. lokale Software Factory, lokale agentworker, lokale relevante images en OpenShift-productie zijn
   gezond;
7. alle eigen wijzigingen, inclusief bijgewerkte overdracht/eindstatus, zijn gecommit en naar
   `main` gepusht;
8. de eindrapportage noemt geen criterium groen zonder concreet bewijs.

## Kant-en-klare prompt voor een nieuwe AI-sessie

> Werk in `/Users/robbertvdzon/git/product-factory`. Lees eerst
> `docs/architecture/plan-levende-productvisie-roadmapproces.md` en daarna
> `docs/architecture/status-levende-productvisie-roadmapproces.md` volledig. Neem de beschreven
> overdracht over en actualiseer eerst uitsluitend read-only de git-, CI-, agentworker-, OpenShift-
> en roadmapsessiestatus. De implementatie van fases 1–7 staat al op `main`; rond nu alle resterende
> productieacceptatie af. Monitor eerst `roadmap-session-hkh-autopilot-0023` en
> `roadmap-session-product-factory-0013` tot terminal. Diagnoseer bij een fout de echte oorzaak,
> voeg voor ieder softwaredefect een generieke regressietest toe en hardcode geen productnamen,
> domeinen, bronnen of sessie-ID's in productiecode of prompts. Behoud backward compatibility,
> productisolatie, de onafhankelijke criticus, exact één begrensde correctieronde en atomaire
> activatie. Verzwak geen bewijs- of validatiegate om een E2E groen te maken.
>
> Draai na wijzigingen het volledige toepasselijke vangnet, bouw ieder geraakt lokaal Docker-image
> opnieuw, commit en push rechtstreeks naar `main`, wacht op groene GitHub Actions en imagepromotie,
> pull de automatische deploymentcommit en verifieer in OpenShift het exacte SHA-image, Argo
> `Synced/Healthy` en runtime `UP`. Start daarna zo nodig verse volledige sessies tegen die laatste
> runtime. Bewijs vervolgens positieve activatie voor beide producten, behoud van de actieve visie
> bij een mislukte sessie, append-only historie, echte UX-media, productscheiding, legacy-API's,
> idempotente migratie en rollback naar `legacy-v1` plus herstel naar `living-vision-v2`. Houd de
> lokale Software Factory op poort 8080 gezond, houd de Mac-agentworker actief en verwijder of
> herstart de productie-postgres-pod niet. Stop pas wanneer ieder fase- en cross-cutting criterium
> aantoonbaar groen is, alles op `main` staat en de laatste versie gezond op productie draait.
> Werk aan het einde dit statusdocument bij naar een definitieve opleverstatus en rapporteer
> sessie-ID's, testtotalen, CI-links, commits, image-SHA's en productie-health.
