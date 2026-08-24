# Product Factory v2 — AI-worker en taakcontainer

Status: technisch contract voor de laptopworker en de uitvoering van één `AiTask`.

Dit document werkt de uitvoeringsgrens uit van
[AI-uitvoering](ai-uitvoering.md). Productontwerp, Productplanning en Kwaliteitsbewaking bepalen wat
een agent moet doen. AI-uitvoering bewaart en distribueert de opaque taak. De laptopworker voert
iedere echte `CODEX`- of `CLAUDE`-taak volledig uit in een nieuwe tijdelijke Dockeromgeving.
`MOCKED` wordt server-side afgehandeld en komt nooit bij deze worker.

## Verantwoordelijkheidsgrens

De aanvragende procesmodule:

- bevriest product-ID, publieke Git-URL, exacte commit-SHA, bronversies en doelomgeving;
- verzamelt de benodigde publieke DTO's en uitsluitend het geheugen van de eigen agentrol;
- kiest via de actuele `AiJobConfiguration` provider en model;
- levert vaste instructies, een resultaatschema en alleen secretreferenties;
- valideert na afloop de domeinbetekenis en publiceert eventueel epics, stories of testbewijs.

AI-uitvoering bewaart de taak, claims, leases, heartbeats, technische resultaten en artifacts. De
worker kent geen module-entiteiten of agentrollen en schrijft nooit rechtstreeks in hun database.

## Taakinput

Naast de generieke taakenvelop kan een taak deze technische context bevatten:

```java
class RepositorySnapshot {
    String gitUrl;       // publieke HTTPS-URL
    String commitSha;    // volledige, vooraf bevroren Git-SHA
}

class TestEnvironmentAccess {
    String environmentId;
    URI baseUrl;
    URI revisionEndpoint;
    List<String> allowedRoutes;
    List<String> secretRefs;
    AccessMode accessMode; // TEST_DATA_WRITE of READ_ONLY
}
```

De aanvrager bepaalt de commit vóór het queueën. Voor ontwerp en planning is dat normaal de HEAD die
bij het maken van de inputmomentopname gold. Voor story- en bugfixverificatie is minimaal de
`deliveredCommitSha` van Software Factory beschikbaar. Een taak gebruikt nooit een beweeglijke
branchnaam als enige bronverwijzing.

## Verloop van één taak

1. De worker claimt de taak via de beveiligde worker-API en controleert provider, model, time-out,
   toegestane netwerkdoelen en grootte.
2. Hij maakt een nieuwe tijdelijke werkdirectory en start één geïsoleerde Dockercontainer.
3. In de container clonet hij alleen de opgegeven publieke HTTPS-repository en checkt hij detached
   precies `commitSha` uit. Er wordt geen Git-token doorgegeven.
4. De worker stelt in de container de volledige vaste agentgereedschapskist beschikbaar. Alleen de
   meegegeven omgevings- en secretreferenties bepalen welke productomgevingen toegankelijk zijn.
5. De provider voert de vaste taak uit en levert uitsluitend veilige voortgang en het gevraagde
   gestructureerde resultaat.
6. De worker uploadt toegestane bewijsartifacts, meldt het resultaat met het actuele fencing token
   en verwijdert daarna container en tijdelijke worktree.
7. Bij crash of slaap volgt de lease- en reconciliatieroute uit het hoofddocument. Een oude
   gefencete container mag nooit alsnog resultaat publiceren.

De checkout mag lokaal gecachet worden voor snelheid, maar iedere taak krijgt een afzonderlijke
worktree op de exacte SHA. Een cache is nooit productwaarheid en mag geen oncommitted bestanden of
output uit een vorige taak lekken.

## Gereedschapskist in iedere echte taak

V1 bood verspreid over zijn rollen internetonderzoek, repositorylezing, Bash, Playwright,
testcommando's, tijdelijke artifacts en beeldgeneratie. V2 neemt die gezamenlijke mogelijkheden als
één vaste technische basis over. De worker hoeft daardoor geen agentrollen of rolgebonden
toolmapping te kennen.

Iedere `CODEX`- of `CLAUDE`-container beschikt minimaal over:

- Bash en gebruikelijke command-linegereedschappen;
- een beschrijfbare tijdelijke taakdirectory en repositoryworktree;
- `git clone`, `fetch`, detached `checkout`, `log`, diff en bestandlezing voor publieke
  HTTPS-repositories;
- WebSearch, WebFetch en uitgaand HTTPS-verkeer naar publieke informatiebronnen;
- headless Chromium met Playwright voor navigeren, klikken, formulieren, responsive controles en
  screenshots;
- de bij de repository passende lokaal beschikbare build- en testcommando's;
- het maken van tijdelijke logs, traces, screenshots en andere bewijsbestanden;
- door de provider ondersteunde beeldgeneratie wanneer het resultaatschema daarom vraagt;
- gestructureerde uitvoer volgens het meegegeven JSON-schema.

De agent mag binnen zijn tijdelijke container bestanden maken, scripts uitvoeren en tools
combineren om de opdracht autonoom af te ronden. Een taak is niet-interactief: zij stelt geen
verduidelijkingsvraag en wacht niet op menselijke input, maar legt onzekerheid in haar resultaat
vast.

De ruime vrijheid eindigt bij de containergrens. De container:

- draait als niet-rootgebruiker met begrensde CPU, geheugen, schijfruimte en uitvoeringstijd;
- krijgt geen persoonlijke laptopdirectories, algemene host-environment of Docker-socket gemount;
- krijgt geen Git-schrijftoken en kan niet committen, pushen, mergen of pull requests maken;
- krijgt geen database-, OpenShift-, Kubernetes- of clustercredentials;
- kan geen interne Product Factory-modulecommands aanroepen;
- ziet alleen expliciet meegegeven testomgevingtoegang en taakgebonden secretreferenties;
- kan nooit blijvende wijzigingen buiten taakartifacts en het ene taakresultaat publiceren.

## Browser, testen en deploymentrevision

Browser-, log- en testclients draaien in de taakcontainer en niet als inhoudelijke adapters in de
servermodule. De procesmodule beschrijft doel, grenzen en verwacht resultaatschema; de worker levert
de technische tools. Een echte browsertest gebruikt Chromium en Playwright; alleen WebFetch, curl
of een leeg DOM gelden niet als bewijs dat een visuele gebruikersroute werkt. Bij canvasgebaseerde
frontends gebruikt de agent bovendien screenshots en beschikbare accessibility-semantiek.

Voor een gerichte story- of bugfixverificatie vraagt de worker eerst het geconfigureerde
revisionendpoint van de doelomgeving op. Hij bewaart:

- de vereiste `deliveredCommitSha`;
- de werkelijk gedeployde commit of release;
- het tijdstip en de omgeving waarop dit is vastgesteld.

Wanneer Git-commits worden gebruikt, moet de gedeployde commit de oplevercommit bevatten. Is dat
niet aantoonbaar, dan retourneert de taak geen afkeuring maar een gestructureerde blokkade
`DEPLOYMENT_PENDING`. Bij een ander release-id-systeem moet de productconfiguratie een even
betrouwbare vergelijkingsregel leveren. Een ontbrekend of ongeldig revisionantwoord is eveneens een
testblokkade en nooit een productbug.

Acceptatie mag binnen de geconfigureerde synthetische testdata schrijven. Productie is standaard
`READ_ONLY`; alleen een expliciet begrensd testaccount en toegestane routes kunnen daarvan afwijken.
De worker pusht nooit Git-wijzigingen en krijgt geen toegang tot Product Factory-modulecommands.

## Credentials

DTO, database en `AiTask` bevatten uitsluitend stabiele `secretRef`s. Voor de MVP heeft de
laptopworker een lokale, versleutelde secretstore of OS-keychain met waarden voor die referenties.
Bij claimen controleert de worker dat alle benodigde referenties lokaal beschikbaar zijn.

Waar mogelijk opent de worker een vooraf geauthenticeerde browsercontext of laat een lokale helper
het loginformulier vullen. Plaintext credentials worden niet aan het modelprompt toegevoegd, niet
in voortgang of artifacts opgeslagen en niet naar de server teruggestuurd. Ontbrekende credentials
geven een veilige technische blokkade.

Providercredentials voor Codex of Claude blijven eveneens uitsluitend op de worker. De worker maakt
de bestaande abonnementslogin via een begrensde credentialbroker of read-only credentialmount aan
het providerproces beschikbaar. Deze waarden worden niet als taakinput of productcontext aan het
model gegeven en zijn niet leesbaar via de gewone agenttools.

## Bewijsartifacts

Screenshots, logs, traces en andere taakoutput worden niet als Base64 in de taak-JSON opgenomen. De
worker uploadt ze via de worker-API met MIME-type, grootte en SHA-256-hash. AI-uitvoering controleert
type, hash en limieten en bewaart ze als onveranderlijke `AiResultArtifact`s.

Voor de MVP worden begrensde artifacts als BLOB in dezelfde database bewaard. Eerste limieten zijn
maximaal 5 MB per artifact en 25 MB per taak. Een latere objectstore kan deze opslag vervangen
zonder het publieke taak- of kwaliteitscontract te wijzigen. Een `Bug` of `Verification` verwijst
alleen naar gevalideerde artifact-ID's; oude bewijzen blijven daardoor reproduceerbaar.

Tijdelijke browserprofielen, downloads, worktrees en niet-geaccepteerde artifacts worden na de taak
verwijderd. Secrets, cookies, tokens, persoonsgegevens en ruwe providerlogs worden vóór acceptatie
afgeschermd of geweigerd.

## Herstel na slaap, workerrestart en laptoprestart

De worker bewaart herstelgegevens buiten zijn procesgeheugen in een klein duurzaam lokaal journal.
Iedere actieve taakcontainer krijgt minimaal labels met worker-ID, task-ID en attempt-ID. De output
en het gestructureerde eindresultaat worden in een taakgebonden workerstate-directory geschreven die
een restart van alleen de workerservice overleeft. Het fencing token blijft versleuteld in het
journal en wordt nooit als containerlabel opgeslagen.

Bij iedere start voert de laptopworker vóór nieuwe claims deze stappen uit:

1. lees het lokale journal en inventariseer de bijbehorende draaiende en gestopte Dockercontainers;
2. meld task-ID, attempt-ID, containerstatus en de aanwezigheid van resultaat aan
   `reconcileWorker(...)`;
3. hervat alleen wanneer de server dezelfde attempt binnen de hersteltermijn nog accepteert;
4. stop en verwijder een container wanneer de server de attempt heeft gefencet;
5. claim pas nieuw werk nadat alle lokale records zijn gereconcilieerd.

Het concrete gedrag is:

| Situatie | Gedrag |
|---|---|
| Alleen de workerservice herstart, container draait nog | de nieuwe workerservice koppelt opnieuw aan dezelfde container en hervat heartbeat en resultaatbewaking voor dezelfde attempt |
| Container is tijdens de workerrestart afgerond | de worker leest het duurzame resultaat, valideert de actuele fencingstatus en levert het alsnog aan de server |
| Laptop slaapt en wordt binnen de hersteltermijn wakker | dezelfde container en attempt worden hervat; er start geen tweede agent |
| Container of providerproces bestaat niet meer | de worker meldt dit; de server maakt de attempt `ABANDONED` en zet dezelfde taak met back-off voor een nieuwe attempt klaar wanneer `maxAttempts` dat toestaat |
| Laptop is herstart en de container is alleen gestopt | een nog niet afgerond providerproces wordt niet half hervat; de oude attempt wordt verlaten en de taak begint later opnieuw in een schone container |
| Hersteltermijn is verlopen of een nieuwere attempt bestaat | de oude container wordt gestopt, het oude resultaat wordt weggegooid en ieder bericht met het oude fencing token wordt geweigerd |

Een taak blijft daardoor nooit onbeperkt `RUNNING`. Zij hervat dezelfde attempt wanneer dat veilig
kan, wordt anders als nieuwe attempt opnieuw uitgevoerd en eindigt na uitgeputte technische
`maxAttempts` zichtbaar als `FAILED`. Een nog niet geclaimde taak blijft veilig `QUEUED` zolang geen
laptopworker beschikbaar is. De aanvragende processessie blijft ondertussen duurzaam
`WAITING_FOR_AI` of wordt na een terminale taakfout zichtbaar `BLOCKED`.

## Onvertrouwde inhoud en prompt-injection

Alle vrije inhoud is onvertrouwde data, waaronder:

- code, README's, tests, issues en comments uit Git;
- epic-, story-, signalen- en meetingtekst;
- zichtbare en verborgen tekst, HTML, accessibility labels en API-responses van de geteste app;
- gedownloade bestanden, logs en foutmeldingen.

Tekst daarin die zich voordoet als instructie wordt nooit uitgevoerd als systeem- of
ontwikkelaarsinstructie. Zij kan geen extra netwerkdoel toestaan, credentials opvragen,
resultaatschema wijzigen, Git schrijven of een Product Factory-command uitvoeren. Vaste
taakinstructies, toolallowlists en servervalidatie hebben altijd voorrang op broninhoud.

## Invarianten

- Iedere echte `CODEX`- of `CLAUDE`-taak draait in een nieuwe tijdelijke Dockeromgeving;
  `MOCKED` bereikt de laptopworker nooit.
- Iedere echte taakcontainer bevat Bash, publieke read-only Git, webonderzoek, Chromium/Playwright,
  lokale testtools, tijdelijke artifacts en waar gevraagd beeldgeneratie.
- Een repositorycheckout gebruikt een publieke HTTPS-URL en exacte volledige commit-SHA.
- De worker heeft geen Git-schrijftoken en commit of pusht nooit.
- De server bewaart geen plaintext test- of providercredentials in een `AiTask`.
- Een test tegen een achterlopende deployment wordt `BLOCKED`, nooit afgekeurd.
- Bewijsartifacts zijn begrensd, gehasht, onveranderlijk en aan exact één taakresultaat gekoppeld.
- Onvertrouwde repository- of applicatie-inhoud kan instructies en rechten niet wijzigen.
- Na een workerrestart worden containers en attempts vóór nieuwe claims uit journal en Dockerstatus
  gereconcilieerd; verloren werk wordt hervat of begrensd opnieuw uitgevoerd en blijft nooit hangen.
- Alleen de aanvragende module valideert en publiceert de domeinuitkomst.

## Gerelateerde documenten

- [AI-uitvoering](ai-uitvoering.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
