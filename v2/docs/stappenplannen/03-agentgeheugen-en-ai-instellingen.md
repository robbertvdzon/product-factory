# Stap 3 — Agentgeheugen en AI-instellingen

## Doel

Voeg de gedeelde, duurzame context toe die latere agenttaken nodig hebben, zonder al AI-taken uit te
voeren.

## Globale scope

- Implementeer het versieerbare permanente geheugen per product en stabiele agentrol.
- Bewaak dat een agentrol uitsluitend haar eigen actuele geheugen kan lezen en wijzigen.
- Geef de Stakeholder via de UI toegang tot alle rolgeheugens, historie en correctiecommands.
- Implementeer de algemene AI-instellingen per `AiJobKey`, inclusief provider, model of mockprofiel,
  inschakeling en configuratieversie, als intern `settings`-onderdeel van `ai-execution-impl`.
- Toon deze globale instellingen in de bestaande frontendpagina **Instellingen → AI-modellen**, met
  een expliciete aanduiding dat zij voor alle producten gelden; maak geen losse algemene
  instellingenpagina.
- Neem `ai-execution-impl` vanaf deze stap als enige provider van de AI-uitvoeringscapability in de
  app op. Alleen het interne `settings`-onderdeel is al functioneel; de taakqueue en workerroute
  worden in stap 4 toegevoegd.
- Registreer de gespreks- en notulenrollen voor stap 4 en de MVP-agentrollen en MVP-jobkeys die in
  stappen 5 tot en met 7 nodig zijn.
- Voeg acceptatiedata en tests voor versiehistorie, autorisatie en configuratiewijzigingen toe.

## Buiten scope

Er worden nog geen AI-taken gequeue'd of door een worker uitgevoerd. Een poging via de al bestaande
taak-API faalt expliciet met `CapabilityNotAvailable`; zij wordt nooit stilzwijgend genegeerd. De
uitgebreide agentrollen en hun modelinstellingen worden nog niet geregistreerd.

## Specificaties

- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Frontend](../stakeholder/frontend.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)

## Klaar wanneer

De ene globale Stakeholder kan overleg- en MVP-rolgeheugen en AI-jobinstellingen veilig beheren, iedere wijziging is
historisch reconstrueerbaar en de rolgrenzen zijn automatisch getest. De versie is op acceptatie en
productie gedeployed.
