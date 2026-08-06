# Lokale ontwikkeling

1. Kopieer `secrets.env.example` naar het gitignored `secrets.env` en kies een lokaal
   databasewachtwoord.
2. Zorg dat `../product-factory-workspace` een checkout van de workspace-repository is.
3. Start de volledige omgeving met `./product-factory up`.
4. Open het dashboard op `http://localhost:8082`; lokale auth staat standaard uit.
5. Controleer runtime en dashboard via respectievelijk `:8080/actuator/health` en
   `:8081/actuator/health`.

Gebruik `./product-factory verify` voor Maven-, Modulith- en Flutterchecks. Applicaties lezen de
env-bestanden zelf als data; de shell hoeft ze niet te sourcen. Proces-environmentvariabelen met
prefix `PF_` hebben altijd voorrang.

Handmatig een product en interne storykandidaat maken:

```bash
curl -X POST http://localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"slug":"hkh-autopilot","name":"HKH Autopilot","mission":"Ontsluit geschiedenis","guardrails":"Bronnen zijn verplicht"}'
curl -X POST http://localhost:8080/api/story-candidates -H 'Content-Type: application/json' \
  -d '{"productSlug":"hkh-autopilot","title":"Kleine proef","description":"Toets één hypothese"}'
```
