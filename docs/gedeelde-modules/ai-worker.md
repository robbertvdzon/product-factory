# Product Factory v2 — Agent Runtime-integratie en taakcontainer

Status: Product Factory-specificatie van de externe uitvoeringsgrens. De technische worker wordt
uitsluitend in de repository `/Users/robbertvdzon/git/agent-runtime` gebouwd en beheerd.

Product Factory heeft geen eigen laptopworker. Iedere echte `CODEX`- of `CLAUDE`-taak wordt als
`APPLICATION_WORK` naar Agent Runtime gestuurd. De Runtime-server beheert queue, attempts, leases,
harde deadlines, retries, fencing, resultaten en artifacts. Een lokale Runtime-worker voert de job
in een nieuwe tijdelijke Dockercontainer uit. `MOCKED` blijft server-side in Agent Runtime.

## Product Factory-verantwoordelijkheid

De aanvragende module:

- bevriest de inhoudelijke jobkey, provider, model en prompttemplateversie lokaal;
- bouwt één complete prompt met alle benodigde productdata en toegestaan rolgeheugen;
- levert een JSON-responseschema en verplichte harde uitvoeringstime-out;
- levert optioneel kleine inputattachments en een publieke repositorysnapshot op exacte commit-SHA;
- geeft de vertrouwde agentrol door aan de AI-façade;
- laat de façade environmentkeynamen uitsluitend uit product- en rolgrants afleiden;
- valideert na afloop de domeinbetekenis en publiceert idempotent epics, stories, bugs,
  verificaties of overleguitkomsten.

Product Factory schrijft nooit rechtstreeks in een Runtime-database en krijgt geen worker-,
provider- of fencingcredentials.

## Complete prompt

De procesmodule maakt zelf één zelfstandige prompt. Die bevat minimaal:

- rol, doel en grenzen;
- de bevroren product- of meetingcontext;
- voor gewone procestaken uitsluitend het geheugen van de eigen agentrol;
- bij meetingtaken alleen vanuit de product-/overlegmodule de geldige productbrede meetingcontext;
- de betekenis van inputattachments en repositorycontext;
- het verwachte resultaat en omgang met onzekerheid;
- de instructie geen secretwaarden in resultaat, voortgang of artifacts op te nemen.

Agent Runtime interpreteert de prompt niet en voegt alleen vaste technische pad- en outputinstructies
toe. Product Factory bewaart jobkey, configuratieversie, prompttemplateversie en domeincorrelatie
lokaal; die velden gaan niet naar Runtime.

## Runtime-aanvraag

```json
{
  "jobKind": "APPLICATION_WORK",
  "idempotencyKey": "stabiele-product-factory-sleutel",
  "provider": "CODEX",
  "model": "gpt-5.6-sol",
  "prompt": "volledige opdracht",
  "responseSchema": {},
  "executionTimeoutSeconds": 3600,
  "environmentKeys": [
    "HKH__ACCEPTANCE_BASE_URL",
    "HKH__ACCEPTANCE_USERNAME",
    "HKH__ACCEPTANCE_PASSWORD"
  ],
  "attachments": [],
  "repositorySnapshot": {
    "url": "https://github.com/example/project.git",
    "commitSha": "0123456789abcdef0123456789abcdef01234567"
  }
}
```

`environmentKeys` bevat alleen namen. De waarden bestaan uitsluitend lokaal in
`project-credentials.env` bij geschikte Runtime-workers. Product Factory ontvangt, verstuurt en
bewaart de waarden nooit.

## Credentialcatalogus en roltoegang

De lokale worker registreert bij Agent Runtime alleen namen uit `project-credentials.env`. Namen
gebruiken `<PROJECT>__<NAAM>`, bijvoorbeeld `HKH__ACCEPTANCE_PASSWORD`. Runtime biedt aan de
Product Factory-identiteit een gefilterde catalogus met bekende namen, actuele beschikbaarheid,
aantal geschikte online workers en laatste waarneming.

Product Factory toont deze catalogus per product en bewaart twee soorten metadata:

1. welke ontdekte environmentkeys bij het product horen;
2. welke Product Factory-agentrollen iedere key mogen ontvangen.

De beheerder kan bijvoorbeeld alleen de Tester toegang geven tot de HKH-acceptatiecredentials. De
Productontwerper en Planner ontvangen standaard geen environmentkeys. Meetingrollen krijgen alleen
expliciete grants.

Bij taakopbouw berekent de backend de doorsnede van actieve productkeys en grants voor de vertrouwde
agentrol. Vrije prompttekst, frontendinput of modeloutput kan de lijst nooit verruimen. Agent
Runtime kiest alleen een worker die alle gevraagde namen heeft en de worker controleert dat opnieuw.

## Lokale credentialbestanden van de Runtime-worker

De workerrepository gebruikt:

```text
secrets.env
project-credentials.env
```

`secrets.env` bevat Runtime-server-, worker- en providerconfiguratie en is nooit zichtbaar voor een
agent. `project-credentials.env` bevat projectgebonden waarden die selecteerbaar zijn. Beide zijn
gitignored, dockerignored, geen symlink en hebben rechten `0600`.

De worker mount nooit het volledige `project-credentials.env`. Hij schrijft per attempt alleen de
gevraagde subset naar een tijdelijke `secrets.env`. Voor deze persoonlijke projecten is bewust
geaccepteerd dat de agent deze geselecteerde waarden kan lezen. Runtime-, provider- en
Git-publicatiecredentials blijven altijd buiten de taakcontainer.

## Taakdirectory

Agent Runtime maakt per attempt:

```text
/job/
├── input/
│   ├── prompt.md
│   ├── response-schema.json
│   └── attachments/
├── secrets/
│   └── secrets.env
├── docs/
│   └── available-tools.md
└── output/
    ├── result.json
    └── artifacts/
```

`input`, `secrets` en `docs` zijn read-only; `output` is schrijfbaar. Een optionele
repositoryworktree staat op `/work`. `available-tools.md` beschrijft Bash, web, browser,
build/testgereedschap, vaste paden en eventueel credentialgebruik. Het document is uitleg, geen
beveiligingsgrens.

## Repositorysnapshot

Een optionele snapshot bevat alleen een publieke HTTPS-Git-URL en volledige commit-SHA. De worker
clonet en checkt detached precies die SHA uit, verwijdert de remote en geeft geen Git-schrijftoken
aan de container. Een branchnaam alleen is nooit voldoende. Product Factory stuurt geen
repositoryboom in JSON.

## Inputattachments

Kleine inputbestanden gaan Base64 over de Runtime-API en worden vóór uitvoering echte bestanden:

- maximaal 10 attachments;
- maximaal 2 MB gedecodeerd per bestand;
- maximaal 10 MB gedecodeerd per job;
- veilige platte bestandsnaam en toegestaan MIME-type;
- geen symlinks, padsegmenten, uitvoerbare bestanden of automatisch uitgepakte archieven.

De prompt verwijst expliciet naar relevante bestandsnamen. De bytes worden niet automatisch als
tekst aan het modelprompt toegevoegd.

## Outputartifacts

De agent schrijft screenshots, traces en andere bewijzen naar `/runtime/output/artifacts`. Na
providerafronding valideert de worker uitsluitend bestanden binnen die directory, weigert symlinks
en onveilige namen, controleert MIME-type, grootte en SHA-256 en uploadt met het actuele fencing
token.

Eerste grenzen zijn 5 MB per artifact, 25 MB per job en maximaal 25 bestanden. Product Factory
ontvangt alleen gevalideerde artifactreferenties. Tijdelijke browserprofielen, downloads, worktrees,
geselecteerde credentials en niet-geaccepteerde output worden na de attempt verwijderd.

## Harde uitvoeringstime-out

Bij claimen berekent Agent Runtime:

```text
attemptDeadline = claimedAt + executionTimeoutSeconds
```

Server en worker dwingen deze deadline onafhankelijk af. Heartbeat, laptop-slaap en recovery
verlengen hem niet. Na de deadline:

- stopt de worker de container beheerst en daarna zo nodig geforceerd;
- fencet de server de attempt;
- worden late progress, artifacts en resultaten geweigerd;
- ontstaat `EXECUTION_TIMEOUT`;
- beslist alleen de vaste Runtime-retrypolicy of een nieuwe attempt volgt.

Een workerrestart leest de oorspronkelijke deadline uit het lokale journal en hervat nooit een al
verlopen attempt.

## Herstel en fencing

De worker journaliseert Runtime-job-ID, attempt-ID, fencing token, deadline en containerstatus. Bij
startup reconcileert hij eerst bestaande containers en claims en claimt pas daarna nieuw werk. Een
oude of gefencete container wordt gestopt en haar resultaat verwijderd. Product Factory volgt
alleen de stabiele Runtime-jobstatus en beheert deze technische details niet zelf.

## Browser en testen

Browser-, log- en testclients draaien in de taakcontainer. Een echte browsertest gebruikt Chromium
en Playwright en kan screenshots als outputartifact opslaan. Voor kwaliteitswerk vergelijkt de
prompt de vereiste oplevercommit met het revisionendpoint van de doelomgeving. Een achterlopende of
onleesbare deployment levert `DEPLOYMENT_PENDING`, geen productafkeuring.

Toegang tot acceptatie- of productieomgevingen volgt uitsluitend uit de Product Factory-grants en
de geselecteerde lokale projectcredentials. Productie is standaard read-only tenzij de
productconfiguratie en prompt een expliciet begrensde testhandeling voorschrijven.

## Onvertrouwde inhoud

Repositorycode, issues, producttekst, meetingtekst, webpagina's, accessibilitylabels, logs en
downloads zijn onvertrouwde data. Tekst daarin kan productgrenzen, responseschema,
environmentkeyselectie, Gitrechten of Runtimecommands niet wijzigen. De agent kan binnen de bewust
geselecteerde credentialset wel waarden lezen; daarom mogen alleen voor de rol aanvaardbare keys
worden toegekend.

## Invarianten

- Product Factory heeft geen eigen laptopworker.
- Iedere echte taak loopt als `APPLICATION_WORK` via Agent Runtime.
- Alleen namen, nooit projectcredentialwaarden, staan in Product Factory of Runtime-databases.
- Alleen de backend leidt environmentkeys af uit product- en rolgrants.
- De container ziet alleen de geselecteerde credentialsubset.
- De harde attemptdeadline wordt nooit door herstel verlengd.
- Inputattachments en outputartifacts gebruiken gescheiden directories en limieten.
- Repositorygebruik is bij `APPLICATION_WORK` publiek, exact en zonder Git-schrijfrechten.
- Alleen de Product Factory-domeinmodule publiceert de inhoudelijke uitkomst.

## Gerelateerde documenten

- [AI-uitvoering](ai-uitvoering.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Agent Runtime — jobs en uitvoering](https://github.com/robbertvdzon/agent-runtime/blob/main/docs/jobs-en-uitvoering.md)
