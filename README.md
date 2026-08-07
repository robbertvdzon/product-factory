# Product Factory

De Product Factory wordt een zelfstandige Kotlin-applicatie die productonderzoek, productkeuzes,
UX-ontwikkeling en storyvorming autonoom organiseert. Zij bouwt zelf geen productcode, maar biedt
stories aan de Software Factory aan, volgt de uitvoering en verwerkt de resultaten in een volgende
productiteratie.

De technische baseline van `hkh` en `hkh-autopilot` volgt het architectuurpatroon van
`personal-news-feed-by-claude-code`: een Kotlin/Spring Modulith-backend, afzonderlijke
Flutter-frontends, componentgerichte CI en OpenShift/GitOps. De Product Factory zelf volgt de
repository- en runtimeopzet van Software Factory, waaronder de Maven-reactor, expliciete Modulith-
grenzen, agentworker en dashboardmodules. Beide blauwdrukken worden bij de bootstrap op een
referentiecommit vastgezet; er wordt structuur overgenomen, geen businesscode gedeeld.

Lokale secrets staan steeds in een gitignored `secrets.env` in de repositoryroot, met een
gecommit `secrets.env.example`; proces-environmentvariabelen blijven de hoogste prioriteit voor CI
en OpenShift.

Goedgekeurde onderzoeksrapporten, UX-ontwerpen, productbeslissingen, roadmaps en storyvoorstellen
worden als leesbare bestanden versieerd in de aparte repository `product-factory-workspace`. De
eigen database van Product Factory bevat de operationele toestand, zoals runs, wachtrijen, kosten,
fouten en verwijzingen naar workspace-commits. Product Factory commit nooit rechtstreeks in een
productrepository; alleen Software Factory wijzigt `hkh` en `hkh-autopilot` tijdens de uitvoering
van een story.

De eerste proef bestaat uit twee varianten van de **Historische Kring Heemskerk App (HKH-app)**:
`hkh`, waarvan de productontwikkeling door de eigenaar wordt gestuurd, en `hkh-autopilot`, waarvan
de productontwikkeling na een gedeelde technische baseline autonoom door de Product Factory wordt
gestuurd. De Product Factory wordt nadrukkelijk generiek opgezet, zodat later meerdere producten
onafhankelijk kunnen worden toegevoegd.

Zie [docs/stappenplan.md](docs/stappenplan.md) voor de gefaseerde realisatie, afhankelijkheden,
eerste stories en kwaliteits- en autonomieregels.

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
- [Modulegrenzen](docs/architecture/modules.md)
- [Lokale ontwikkeling](docs/development/local-development.md)
- [OpenShift-deployment](docs/deployment/openshift.md)
- [Workspace-security](docs/security/workspace-credential.md)
