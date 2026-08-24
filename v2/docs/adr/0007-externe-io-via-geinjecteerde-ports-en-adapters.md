# 0007 - Externe I/O via geïnjecteerde ports en adapters

- Status: Accepted
- Datum: 2026-08-24

## Context

Beide bestaande codebases schermen externe techniek steeds meer af achter injecteerbare
interfaces. Voorbeelden zijn `GoogleIdTokenVerifier`, AI-clients, repositories en de injecteerbare
`Clock` in Personal News Feed, en `DeploymentStatusProbe`, `AgentRuntime`, trackerports en diverse
GitHub-, proces- en bridgeadapters in Software Factory.

Software Factory controleert daarnaast met `tools/check-composition-roots` dat direct gebruik van
`System.getenv`, `ProcessBuilder` en `HttpClient.new...` alleen voorkomt op geregistreerde exacte
paden met een benoemde eigenaar en reden. Daardoor kan technische I/O niet ongemerkt midden in
businesslogica ontstaan.

Product Factory v2 praat met AI-workers, Software Factory, Git, databases, de klok, het
bestandssysteem en mogelijk lokale processen. Zonder vaste grens worden deze afhankelijkheden
moeilijk te testen en verspreiden time-outs, credentials en protocolkeuzes zich door de code.

## Decision

Externe I/O loopt via ports en adapters:

- de service die een externe capability nodig heeft, hangt af van een kleine interface die het
  benodigde gedrag beschrijft;
- de concrete HTTP-, database-, Git-, bestands-, klok- of procesadapter leeft in de owning
  implementatiemodule of composition root;
- adapters krijgen configuratie, clients, credentials en een `Clock` geïnjecteerd en lezen niet
  ad hoc globale procesconfiguratie;
- directe aanmaak van HTTP-clients, processen of andere systeemgrenzen is alleen toegestaan in een
  expliciet geregistreerde composition root of adapter;
- time-outs, begrensde retries, foutvertaling, secretredactie en veilige logging horen bij de
  adapter of een gedeelde technische policy;
- tests vervangen de buitenwereld met een fake of simulator die dezelfde port of hetzelfde echte
  protocol implementeert; productiecode bevat geen `if (test)`-pad.

De port staat aan de kant van de capability die het gedrag nodig heeft. Een externe SDK- of
HTTP-responsetype wordt niet het interne application contract.

## Consequences

- Businessservices zijn deterministisch te testen zonder netwerk, lokale CLI of echte secrets.
- Een externe provider of protocolimplementatie kan worden vervangen zonder de use-case te
  herschrijven.
- Alle plekken met systeemtoegang zijn inventariseerbaar en gericht te beveiligen.
- Er komen extra interfaces en adapters bij. Interfaces worden daarom capabilitygericht en klein
  gehouden; niet iedere interne klasse krijgt automatisch een interface.
- Een adapterfout moet worden vertaald naar een betekenisvolle application- of domeinfout en mag
  niet als provider-specifiek type door alle lagen lekken.
