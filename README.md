# Product Factory

Dit is de nieuwe technische basis van Product Factory. Release `0.1.0` bevat eerst de veilige
backend- en frontendfundering; functionele procesimplementaties worden daarna capability voor
capability toegevoegd.

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

De backendroute `GET /api/foundation` bevestigt dat de technische basis actief is. De frontend toont
bewust nog geen functionele processen.

De actuele architectuur en uitvoerplannen staan in [`docs`](docs/overzicht.md).
