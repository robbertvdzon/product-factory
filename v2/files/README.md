# Technische overnamebestanden uit v1

Deze map is de tijdelijke, gecontroleerde meeneemdoos voor de nieuwbouw van Product Factory. Zij
bevat kopieën van alleen die technische v1-bestanden en patronen die mogelijk bruikbaar zijn bij
stap 1. De actieve originelen zijn niet verplaatst en blijven staan totdat de technische fundering
ze bewust vervangt.

De bestanden in deze map zijn geen kant-en-klare v2-implementatie en geen volledige v1-back-up. Git
bewaart de volledige v1-geschiedenis. Een uitvoerende agent beoordeelt ieder bestand opnieuw,
herschrijft waar nodig en plaatst het pas daarna op zijn definitieve pad.

## Inhoud en bestemming

| Kopie | Herkomst in v1 | Beoogde bestemming | Gebruik |
|---|---|---|---|
| `.gitignore` | `/.gitignore` | `/.gitignore` | opschonen en de bescherming van lokale bestanden behouden |
| `.dockerignore` | `/.dockerignore` | `/.dockerignore` | aanpassen aan de nieuwe buildcontext |
| `.github/workflows/**` | `/.github/workflows/**` | `/.github/workflows/**` | structuur en deploymentlessen gebruiken; workflows herschrijven |
| `deploy/**` | `/deploy/**` | `/deploy/**` | Kustomize-, OpenShift-, Sealed Secret- en backuppatronen beoordelen en herschrijven |
| `secrets.env.example` | `/secrets.env.example` | `/secrets.env.example` | nieuw publiek secretcontract documenteren zonder waarden |
| `properties.default.env` | `/properties.default.env` | `/properties.default.env` | volledig herschrijven voor nieuwe niet-geheime defaults |
| `docker-compose.yml` | `/docker-compose.yml` | `/docker-compose.yml` | lokale composition root opnieuw opbouwen |
| `quality/detekt.yml` | `/quality/detekt.yml` | `/quality/detekt.yml` | behouden als Kotlin en Detekt worden gebruikt |
| `product-factory` | `/product-factory` | `/product-factory` | eenvoudige lokale CLI opnieuw opbouwen |
| `dashboard-frontend/nginx.conf` | `/dashboard-frontend/nginx.conf` | nieuwe frontend-Nginxconfiguratie | cache-, security- en SPA-patronen opnieuw toepassen |
| `tools/agent-worker-launchagent` | `/tools/agent-worker-launchagent` | `/tools/agent-worker-launchagent` of opvolger | alleen installatie-, restart- en logconcept hergebruiken |

## Bewuste uitzonderingen

- `secrets.env` staat uitsluitend in de repositoryroot, blijft gitignored en wordt nooit naar deze
  map gekopieerd of gecommit.
- Functionele broncode, oude domeinmodellen, prompts, migraties, tests en v1-documentatie worden niet
  meegenomen.
- Gegenereerde bestanden en werkdata zoals `target`, `build`, `.dart_tool`, `work` en logs worden
  niet meegenomen.
- `.factory` en `tools/verify` worden niet meegenomen.

## Gebruik tijdens stap 1

1. Controleer per regel of het patroon nog nodig is.
2. Verplaats alleen de bruikbare kopie naar haar definitieve locatie.
3. Herschrijf haar voor de nieuwe structuur en verifieer het resultaat zelfstandig.
4. Verwijder de oude actieve v1-versie wanneer de vervanging veilig actief is.
5. Verwijder `v2/files` nadat alle regels zijn afgehandeld; laat geen tijdelijke v2-map achter.

Wijzig niet stilzwijgend zowel een actief v1-bestand als de kopie. Als een noodzakelijke
infrastructuurcorrectie vóór stap 1 ook in de overnamebron thuishoort, werk de kopie bewust in
dezelfde commit bij.
