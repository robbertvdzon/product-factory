# Lokale ontwikkeling

1. Kopieer `secrets.env.example` naar het gitignored `secrets.env` en kies een lokaal
   databasewachtwoord. Zie [deploy/README.md](../../deploy/README.md#database-verbinden) voor hoe
   je met `psql` verbinding maakt met zowel deze lokale database als met productie, en waar alle
   credentials (inclusief `PF_DB_PASSWORD`) precies vandaan komen.
2. Zorg dat `../product-factory-workspace` een checkout van de workspace-repository is.
3. Start de volledige omgeving met `./product-factory up`.
4. Open het dashboard op `http://localhost:8082`; lokale auth staat standaard uit.
5. Controleer runtime en dashboard via respectievelijk `:8080/actuator/health` en
   `:8081/actuator/health`.

Gebruik `./product-factory verify` voor Maven-, Modulith- en Flutterchecks. Applicaties lezen de
env-bestanden zelf als data; de shell hoeft ze niet te sourcen. Proces-environmentvariabelen met
prefix `PF_` hebben altijd voorrang.

Handmatig een product en interne storykandidaat maken:

```bash
curl -X POST http://localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"slug":"voorbeeld-product","name":"Voorbeeldproduct","mission":"Ontsluit relevante kennis","status":"active","developmentMode":"autonomous"}'
curl -X POST http://localhost:8080/api/story-candidates -H 'Content-Type: application/json' \
  -d '{"productSlug":"voorbeeld-product","title":"Kleine proef","description":"Toets één hypothese"}'
curl 'http://localhost:8080/api/story-candidates?productSlug=voorbeeld-product'
```

Zie [het producttemplate](../product-template.md) voor alle instellingen en veiligheidsregels.

## Agentworker op de Mac

De worker gebruikt `codex exec` en de bestaande abonnementslogin uit `~/.codex/auth.json`. Maak
één afzonderlijk, willekeurig bridgetoken en zet dezelfde waarde lokaal en via het SealedSecret:

```bash
openssl rand -hex 32
# zet de uitvoer als PF_AGENT_WORKER_TOKEN in secrets.env
./deploy/seal-secrets.sh
```

Publiceer eerst backend en SealedSecret. Start daarna eenmalig in de voorgrond voor controle:

```bash
./product-factory agent-worker-once
```

De productie-URL is standaard
`wss://product-factory-api.vdzonsoftware.nl/agent-worker`. De verbinding wordt vanaf de Mac
opgebouwd; Cloudflare of de router hoeft geen verbinding naar de Mac te kunnen openen. De worker
verwijdert `OPENAI_API_KEY` en `CODEX_API_KEY` uit ieder Codex-subproces, zodat de opgeslagen
ChatGPT-login bepalend blijft.

Installeer hem daarna als macOS LaunchAgent, in dezelfde stijl als de Software Factory:

```bash
./product-factory agent-worker-install
./product-factory agent-worker-status
tail -f work/agentworker.log
```

Gebruik `agent-worker-restart` na een lokale code-update en `agent-worker-uninstall` om de
LaunchAgent te verwijderen. Een LaunchAgent houdt geen terminalvenster open en herstart na een
crash of nieuwe login. De Mac moet wel ingeschakeld en wakker zijn; een WebSocket kan slaapstand
niet overleven en reconnect automatisch zodra macOS weer actief is.

De geauthenticeerde dashboard-API biedt `GET /api/agent-worker/status`,
`POST /api/agent-worker/tasks` en
`GET /api/agent-worker/tasks/{runId}?productSlug=<slug>`. Taakdispatch is
asynchroon: de POST retourneert `202`, waarna het resultaat via het statusendpoint gevolgd wordt.

## Shadow-iteratie uitvoeren

Controleer eerst met `./product-factory agent-worker-status` dat de Mac-worker draait. Start daarna
via **Start productcyclus nu** in het dashboard of rechtstreeks op de runtime. Een handmatige start
vereist zowel de effectieve opdracht als de bijpassende gesloten herkomst:

```bash
curl -X POST http://localhost:8080/api/products/hkh-autopilot/cycles \
  -H 'Content-Type: application/json' \
  -d '{"focus":"Bepaal autonoom de belangrijkste nog onbeantwoorde productvraag op basis van missie, bestaand dossier en eerdere iteraties.","manualStartOrigin":"AUTONOMOUS_DEFAULT"}'
curl 'http://localhost:8080/api/shadow-iterations?productSlug=hkh-autopilot'
```

Gebruik voor een eigen vraag `manualStartOrigin: "OWNER_INPUT"` en 1 tot en met 300 tekens in
`focus` na trimmen. De runtime trimt die invoer eenmaal en wijst ontbrekende, onbekende of niet bij
de opdracht passende herkomst af. De response bevat `manualStartOrigin`; automatische, hervatte en
historische cycli houden daar `null`.

Een lopende cyclus handmatig annuleren kan via de bestaande runtime-route:

```bash
curl -X POST \
  'http://localhost:8080/api/shadow-iterations/<iteration-id>/cancel?productSlug=hkh-autopilot' \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Lokale testannulering"}'
```

Bij succes bevat zowel deze response als de lijst- en detailresponse een optioneel
`decision`-object. Het relevante deel van de response ziet er zo uit:

```json
{
  "status": "FAILED",
  "completedAt": "2026-08-12T12:34:56.789Z",
  "decision": {
    "iterationId": "<iteration-id>",
    "actorType": "HUMAN",
    "mechanism": "MANUAL_CANCELLATION",
    "reasonCode": "MANUALLY_CANCELLED",
    "decidedAt": "2026-08-12T12:34:56.789Z"
  }
}
```

`decidedAt` is exact gelijk aan `completedAt`. De vrije `reason` blijft backward compatible als
`errorMessage`, maar wordt niet in het privacy-minimale beslisrecord opgenomen. Historische cycli
zonder record houden `decision: null`; er is geen backfill.

De runtime verstuurt iedere rol via de interne, met `PF_AGENT_WORKER_TOKEN` beveiligde bridge naar
de dashboardbackend. Dezelfde token moet dus in runtime, dashboardbackend en lokale agentworker
staan; hij wordt niet doorgegeven aan het Codex-subproces. Zie
[shadow-mode.md](../architecture/shadow-mode.md) voor rollen, validatie en opslaggrenzen.
