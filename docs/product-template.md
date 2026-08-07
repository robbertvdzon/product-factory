# Nieuw product toevoegen

Een volgend product wordt als data toegevoegd; er is geen codewijziging of nieuwe runtime nodig.
Gebruik het beheerdashboard of stuur onderstaand contract naar `POST /api/products`.

```json
{
  "slug": "voorbeeld-product",
  "name": "Voorbeeldproduct",
  "mission": "De blijvende productmissie.",
  "description": "Wat het product voor wie mogelijk maakt.",
  "guardrails": "Grenzen waarbinnen de Product Factory beslist.",
  "softwareFactoryProjectKey": "voorbeeld-product",
  "targetRepositoryName": "voorbeeld-product",
  "allowedWritePaths": ["research", "product-memory", "decisions", "ux", "roadmap", "stories"],
  "workspaceOwnership": "product-factory",
  "liveUrl": "https://voorbeeld-product.vdzonsoftware.nl",
  "previewUrlPattern": "https://voorbeeld-product-pr-{number}.vdzonsoftware.nl",
  "status": "draft",
  "developmentMode": "manual",
  "iterationSchedule": "0 3 * * *",
  "timezone": "Europe/Amsterdam",
  "maxStoriesPerCycle": 3,
  "wipLimit": 1,
  "aiProvider": "codex",
  "aiModel": "default",
  "dailyBudgetCents": 0,
  "monthlyBudgetCents": 0,
  "escalationPolicy": "Wanneer is menselijk handelen echt noodzakelijk?",
  "sourceRules": "Welke eisen gelden voor bronnen en onzekerheid?",
  "privacyRules": "Welke persoonsgegevens mogen worden verwerkt?",
  "accessibilityRules": "Welke toegankelijkheidseisen zijn verplicht?",
  "qualityRules": "Wanneer is een productstap aantoonbaar goed?"
}
```

## Regels

- De slug wordt na creatie niet gewijzigd en vormt samen met de stabiele ID de productidentiteit.
- De workspace-directory wordt door de backend altijd afgeleid als `products/<slug>`; een client
  kan geen eigen directory kiezen.
- Alleen relatieve paden zonder `.` of `..` zijn toegestaan. Publicatie kan uitsluitend onder een
  van `allowedWritePaths` plaatsvinden.
- `workspaceOwnership: owner` blokkeert iedere workspace-publicatie door Product Factory.
- `status: paused` blokkeert nieuwe runs, kennis, kandidaten en publicaties voor alleen dit product.
- Een externe story kan pas worden gepubliceerd wanneer status `active` én ontwikkelmodus
  `autonomous` zijn.
- Begin met `draft` en `manual`. Zet een product pas na controle op `active` en eventueel
  `autonomous`.

Alle productgebonden lees-API's vereisen de slug in het pad of als `productSlug`-queryparameter.
Hierdoor is een onbegrensde query over runs, kennis, stories of workspace-publicaties niet
beschikbaar.
