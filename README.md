# Product Factory

Product Factory is een zelfstandige Kotlin-applicatie die productonderzoek, productkeuzes,
UX-ontwikkeling en storyvorming autonoom organiseert, voor elk product dat erin geregistreerd
staat. Zij bouwt zelf geen productcode, maar biedt stories aan de Software Factory aan, volgt de
uitvoering en verwerkt de resultaten in een volgende productiteratie. Een nieuw product wordt als
data toegevoegd, niet als code — zie [docs/product-template.md](docs/product-template.md) voor het
contract en de regels.

Lokale secrets staan steeds in een gitignored `secrets.env` in de repositoryroot, met een
gecommit `secrets.env.example`; proces-environmentvariabelen blijven de hoogste prioriteit voor CI
en OpenShift.

Goedgekeurde onderzoeksrapporten, UX-ontwerpen, productbeslissingen, roadmaps en storyvoorstellen
worden als leesbare bestanden versieerd in de aparte repository `product-factory-workspace`. De
eigen database van Product Factory bevat de operationele toestand, zoals runs, wachtrijen, kosten,
fouten en verwijzingen naar workspace-commits. Product Factory commit nooit rechtstreeks in een
productrepository; alleen Software Factory wijzigt de doelrepository van een product tijdens de
uitvoering van een story.

## Fase-2-runtime

De zelfstandige technische basis bestaat uit een Maven-reactor, Spring Modulith-runtime,
PostgreSQL/Flyway, agentworker, Google OIDC-dashboard en een begrensde workspace-publisher. Start
lokaal met `./product-factory up` en controleer alles met `./product-factory verify`.

De agentworker draait bewust als zelfstandig proces op de Mac. Hij maakt zelf een uitgaande,
geauthenticeerde WebSocket-verbinding met de OpenShift-dashboardbackend en start `codex exec` met
de bestaande ChatGPT/Codex-login van de macOS-gebruiker. Er worden geen OpenAI API-keys aan het
agentproces doorgegeven. De backend kan daardoor taken aanbieden zonder een poort op de Mac te
publiceren; tijdens slaapstand of uitschakelen rapporteert de backend de worker als offline.

- [Architectuurreferentie](docs/architecture/reference-baseline.md)
- [Eerste opzet voor Product Factory v2](docs/product-factory-v2-eerste-opzet.md)
- [Modulegrenzen](docs/architecture/modules.md)
- [Functioneel overzicht: wat doet een productcyclus precies](docs/architecture/functioneel-overzicht.md)
- [Productagents in shadow mode](docs/architecture/shadow-mode.md)
- [dependsOn-datamodel van storykandidaten](docs/architecture/dependson-datamodel.md)
- [Lokale ontwikkeling](docs/development/local-development.md)
- [OpenShift-deployment](docs/deployment/openshift.md)
- [Deployment, secrets en databasetoegang (lokaal en productie)](deploy/README.md)
- [Workspace-security](docs/security/workspace-credential.md)
- [Template voor een volgend product](docs/product-template.md)
