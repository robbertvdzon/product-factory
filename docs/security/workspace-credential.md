# Workspace-credential en publicatiegrens

Alleen `WorkspacePublisher` bezit het workspace-token. Agenttaken retourneren gestructureerde
`AgentArtifact`-objecten en voeren zelf geen Git-commando's uit. De publisher:

1. accepteert alleen een productslug, relatieve Markdown-locatie en unieke run-ID;
2. controleert dat origin exact `product-factory-workspace` is;
3. maakt `product-factory/<product-slug>/<run-id>` vanaf de actuele hoofdbranch;
4. schrijft één commit en maakt één pull request;
5. schakelt auto-merge in, zodat `Workspace validation` de merge bewaakt;
6. registreert contenthash, PR-URL en commit-SHA in de eigen database;
7. retourneert bij retry met dezelfde inhoud hetzelfde resultaat en weigert afwijkende inhoud.

Een gerichte test bewijst dat repositories `hkh-autopilot` en `product-factory` door de guard
worden geweigerd. Git-credentials voor productrepositories bestaan niet in de configuratie.
