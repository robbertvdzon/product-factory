# Stap 4 — AI-uitvoering

## Doel

Maak één generieke route waarmee iedere latere module een complete AI-taak duurzaam kan laten
uitvoeren, zonder dat AI-uitvoering productobjecten of agentrollen hoeft te begrijpen.

## Globale scope

- Implementeer aanvragen, queueën, claimen, leasen, heartbeat, voortgang, afronding en veilig
  opnieuw beschikbaar maken van `AiTask`s.
- Bouw de HTTPS-interface voor de laptopworker en pas de lokale workerservice daarop aan.
- Laat de worker iedere echte taak in een tijdelijke Dockeromgeving uitvoeren en een benodigde
  publieke Git-repository zelf op de bevroren commit-SHA uitchecken, zonder Git-schrijftoken.
- Ondersteun `CODEX`, `CLAUDE` en buiten productie `MOCKED`, gekozen via de bevroren
  taakconfiguratie. Alleen echte `CODEX`- en `CLAUDE`-taken gaan naar de laptopworker;
  `MOCKED` wordt volledig server-side afgehandeld.
- Maak slaapstand en tijdelijk ontbrekende heartbeats veilig met een hersteltermijn en fencing.
- Leg veilig gedrag vast voor uitgeschakelde AI-jobconfiguratie en geannuleerde taken, zodat geen
  productgebonden processessie verweesd achterblijft.
- Upload begrensde, gehashte bewijsartifacts via de worker-API en bewaar die voor de MVP als BLOB in
  de database; geef alleen lokale secretreferenties door en nooit plaintext in de taak.
- Geef iedere echte taakcontainer dezelfde vaste gereedschapskist: Bash, een schrijfbare tijdelijke
  worktree, read-only Gitgebruik, HTTPS/webzoekwerk, Chromium/Playwright, builds, tests en begrensde
  bewijsartifacts. De container krijgt geen hostmappen, Docker-socket, Git-schrijftoken,
  databasegegevens, clustercredentials of modulecommands.
- Laat de laptopworker na een herstart eerst zijn duurzame lokale uitvoeringsjournaal en gelabelde
  taakcontainers reconciliëren. Hervat een nog draaiende container, lever een al afgerond resultaat
  alsnog in en laat een verdwenen poging veilig opnieuw beschikbaar maken.
- Voeg de server-side mockexecutor, voorbereide mockantwoorden en bestuurbare acceptance-only
  endpoints toe. Een mocktaak gebruikt geen laptop, workerlease of Dockercontainer.
- Maak taakstatus en veilige voortgang zichtbaar in de operationele frontendweergave.
- Activeer de Meeting Agent en notulenagent uit de product-/overlegmodule via dezelfde generieke
  AI-taakroute en de in stap 3 geregistreerde rollen en jobkeys. Hun complete opaque taak bevat de
  actieve rolcatalogus, het productbrede meetingsnapshot en open Stakeholdervragen; AI-uitvoering
  begrijpt deze inhoud niet.
- Laat de Meeting Agent op een optioneel gekozen doelrol antwoorden zonder een echte procesagent te
  starten. Laat de notulenagent beantwoorde vragen registreren en blijvende lessen via de atomaire
  Agentgeheugen-meetingbatch over meerdere rollen verwerken.

## Buiten scope

AI-uitvoering maakt zelf geen notulen, epics, stories of verificaties. De module accepteert alleen
generieke taken. De product-/overlegmodule valideert en verwerkt zelf het overlegresultaat;
procesprompts en procesvalidatie van de drie intelligente processen horen in latere stappen.

## Specificaties

- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [AI-worker en taakcontainer](../gedeelde-modules/ai-worker.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

Een generieke echte taak kan door de laptopworker worden uitgevoerd en na een workerherstart
eenduidig worden hervat of opnieuw beschikbaar worden gemaakt. Een mocktaak kan zonder draaiende
laptop volledig server-side worden afgehandeld. Tijdelijk workerverlies leidt niet tot een hangende
taak of twee geldige afrondingen en alle relevante scenario's zijn op acceptatie bestuurbaar. Een
overleg kan de Meeting Agent en notulenagent via deze route gebruiken, gericht vanuit een gekozen
rol antwoorden, vragen afsluiten en controleerbare productbrede geheugenwijzigingen verwerken.
Dezelfde servermodule staat veilig op productie, waar `MOCKED` niet beschikbaar is.
