# Product Factory

Product Factory bouwt en bewaakt producten vanuit één globale Stakeholderbediening. De huidige
release bevat de technische fundering plus de product- en stakeholderbasis: productopdrachten,
testomgevingen, signalen, agentvragen, overleggen, besluiten en vier configureerbare schedules.
AI- en productprocessen worden in de volgende capabilities toegevoegd.

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
`GET /api/foundation/implementations` staan de gekozen product- en besluitenimplementaties; de
frontend biedt het functionele beheer via de normale geauthenticeerde sessie en CSRF-beveiliging.
Schedules worden al duurzaam gevalideerd en weergegeven, maar starten tot stap 9 niets automatisch.

De actuele architectuur en uitvoerplannen staan in [`docs`](docs/overzicht.md).
