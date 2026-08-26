# Product Factory

Product Factory bouwt en bewaakt producten vanuit één globale Stakeholderbediening. De huidige
release bevat de technische fundering, de product- en stakeholderbasis en gecontroleerd
agentgeheugen: productopdrachten, testomgevingen, signalen, agentvragen, overleggen, besluiten,
vier configureerbare schedules, vijf vertrouwde agentrollen en globale AI-modelinstellingen.
AI-taakuitvoering en de productprocessen worden in de volgende capabilities toegevoegd.

## Vereisten

- JDK 21 (geen andere hoofdversie);
- Maven 3.9;
- Flutter 3.44.6 met de bijbehorende Dart-SDK;
- Docker met Compose voor de latere productieachtige lokale omgeving.

Op macOS selecteert de lokale CLI zelf de geïnstalleerde JDK 21. Maven Enforcer laat iedere build
vroeg en duidelijk falen wanneer Maven toch met een andere Java-hoofdversie draait.

```bash
./product-factory verify
./product-factory backend
./product-factory frontend
```

De backendroute `GET /api/foundation` bevestigt de actieve basis. Onder
`GET /api/foundation/implementations` staan ook `agent-memory-impl` en het settings-only deel van
`ai-execution-impl`. De frontend biedt onder **Beheer** rolgebonden geheugen, peildatumhistorie,
budgetten en globale AI-modellen via de normale geauthenticeerde sessie en CSRF-beveiliging.
AI-taakaanvragen falen tot stap 4 expliciet met HTTP 501 en maken geen taak- of outboxrecord.
Schedules worden al duurzaam gevalideerd en weergegeven, maar starten tot stap 9 niets automatisch.

De actuele architectuur en uitvoerplannen staan in [`docs`](docs/overzicht.md). Het operationele
overzicht voor deze capability staat in
[`docs/platform/agentgeheugen-en-ai-instellingen-runbook.md`](docs/platform/agentgeheugen-en-ai-instellingen-runbook.md).
