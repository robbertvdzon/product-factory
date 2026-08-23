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

## Buiten scope

AI-uitvoering maakt nog geen epics, stories of verificaties. De module accepteert alleen generieke
taken; procesprompts en procesvalidatie horen in latere procesimplementaties.

## Specificaties

- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

Een generieke taak door de echte laptopworker en door de mockworker kan worden uitgevoerd, tijdelijk
workerverlies niet tot dubbele geldige afronding leidt en alle relevante scenario's op acceptatie
bestuurbaar zijn. Dezelfde servermodule staat veilig op productie.
