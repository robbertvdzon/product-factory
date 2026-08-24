# Stap 4 — AI-uitvoering

## Doel

Maak één generieke route waarmee iedere latere module een complete AI-taak duurzaam kan laten
uitvoeren, zonder dat AI-uitvoering productobjecten of agentrollen hoeft te begrijpen.

## Globale scope

- Implementeer aanvragen, queueën, claimen, leasen, heartbeat, voortgang, afronding en veilig
  opnieuw beschikbaar maken van `AiTask`s.
- Bouw de HTTPS-interface voor de laptopworker en pas de lokale workerservice daarop aan.
- Ondersteun `CODEX`, `CLAUDE` en buiten productie `MOCKED`, gekozen via de bevroren taakconfiguratie.
- Maak slaapstand en tijdelijk ontbrekende heartbeats veilig met een hersteltermijn en fencing.
- Voeg de mockworker en bestuurbare acceptance-only scenario's toe.
- Maak taakstatus en veilige voortgang zichtbaar in de operationele frontendweergave.
- Activeer de gespreks- en notulenagent uit de product-/overlegmodule via dezelfde generieke
  AI-taakroute en de in stap 3 geregistreerde rollen en jobkeys.

## Buiten scope

AI-uitvoering maakt zelf geen notulen, epics, stories of verificaties. De module accepteert alleen
generieke taken. De product-/overlegmodule valideert en verwerkt zelf het overlegresultaat;
procesprompts en procesvalidatie van de drie intelligente processen horen in latere stappen.

## Specificaties

- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

Een generieke taak door de echte laptopworker en door de mockworker kan worden uitgevoerd, tijdelijk
workerverlies niet tot dubbele geldige afronding leidt en alle relevante scenario's op acceptatie
bestuurbaar zijn. Een overleg kan de gespreks- en notulenagent via deze route gebruiken. Dezelfde
servermodule staat veilig op productie.
