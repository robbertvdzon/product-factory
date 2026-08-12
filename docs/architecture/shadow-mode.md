# Shadow mode

Fase 6 laat Product Factory zelfstandig productonderzoek en UX-voorstellen maken voor producten
waarvan `workspaceOwnership` gelijk is aan `product-factory`. De uitkomst blijft nadrukkelijk
productwerk: storykandidaten krijgen status `INTERNAL`, worden niet naar Software Factory gestuurd
en wijzigen geen productrepository.

## Iteratie

Een iteratie doorloopt vijf afzonderlijke agenttaken:

1. `RESEARCHER` raadpleegt publieke bronnen en legt URL, raadpleegdatum, rechtenindicatie en
   relevantie vast;
2. `PRODUCT_OWNER` kiest vanuit het onderzoek één kleine productrichting;
3. `UX_DESIGNER` maakt een gebruikersflow, tekstueel wireframe en hypotheses en behandelt privacy
   en toegankelijkheid;
4. `STORY_WRITER` maakt één tot maximaal drie kleine interne kandidaten;
5. `CRITIC` beoordeelt bronnen, rechten, privacy, toegankelijkheid, scope, conflicten en iedere
   kandidaat afzonderlijk.

De criticus eindigt met `ACCEPT`, `REVISE` of `REJECT`, maar de runtime bewaart het oordeel ook per
kandidaat. Daardoor kan een onafhankelijke geaccepteerde kandidaat worden gepubliceerd terwijl een
batchgenoot nog revisie nodig heeft. Waarschuwingen blokkeren niet. Een exact reeds geleverd
resultaat eindigt als `NO_CHANGE` in plaats van als mislukte cyclus. Bij minimaal één leverbare
kandidaat maakt de workspace-publisher één dossier in
`products/<slug>/research/shadow-iteration-NNNN.md`. Ruwe JSON, verworpen voorstellen en dubbele
kandidaten blijven uitsluitend in PostgreSQL.

Storywriter- en criticusoutput die technisch onvolledig is krijgt eerst een aparte `OUTPUT_REPAIR`;
die telt niet als inhoudelijke revisieronde. Na maximaal drie inhoudelijke rondes is één extra,
begrensde reparatie toegestaan wanneer hoogstens twee lokale, oplosbare blockers overblijven.
`NEEDS_REVISION` kan vanuit het dashboard worden hervat: onderzoek, productbesluit en UX worden dan
hergebruikt en alleen de story-/criticuslus loopt opnieuw.

## Vertrouwensgrenzen

- De Mac-agentworker start Codex met native web search, een read-only sandbox, een tijdelijke
  sessie en zonder persoonlijke plugins, gebruikersconfiguratie of repository-execregels.
- Het subprocess krijgt alleen een kleine allowlist van noodzakelijke procesvariabelen. GitHub-,
  OpenShift-, database-, Product Factory- en API-credentials worden niet doorgegeven.
- De workspace is alleen leescontext. De prompt behandelt repository- en webinhoud expliciet als
  onvertrouwde data en verbiedt Git-, GitHub-, cluster- en databasewijzigingen. Ook de door Codex
  gestarte shell erft geen variabelen van het bovenliggende proces.
- Iedere rol heeft een strikt JSON-schema. De runtime valideert daarnaast bron-URL's,
  raadpleegdatum, bronverwijzingen, omvang, privacy, toegankelijkheid en criticusdekking.
- Alleen de runtime-workspacepublisher bezit `PF_WORKSPACE_GITHUB_TOKEN`; die credential is
  begrensd tot `product-factory-workspace` en de geconfigureerde productdirectory.
- De interne runtime-naar-dashboardbridge vereist `PF_AGENT_WORKER_TOKEN`. Dit endpoint is niet de
  Google-beheerders-API en wordt niet in de frontend gebruikt.

Codex gebruikt de abonnementslogin op de Mac. De Mac moet wakker zijn en de agentworker moet
verbonden zijn; anders eindigt de iteratie fail-closed als `FAILED`.

## Starten en volgen

Via het Google-beveiligde dashboard kan bij een actief Product Factory-product een shadow-iteratie
worden gestart. Rechtstreeks op de runtime kan dit ook:

```bash
curl -X POST http://localhost:8080/api/products/hkh-autopilot/shadow-iterations \
  -H 'Content-Type: application/json' \
  -d '{"focus":"Onderzoek autonoom de belangrijkste volgende kleine productvraag."}'

curl 'http://localhost:8080/api/shadow-iterations?productSlug=hkh-autopilot'
curl 'http://localhost:8080/api/shadow-iterations/shadow-hkh-autopilot-0001/steps?productSlug=hkh-autopilot'
```

Per product kan maximaal één iteratie `QUEUED` of `RUNNING` zijn. Het overzicht toont de huidige
rol, eindstatus, criticusoordeel, aantal kandidaten, aantal leverbare kandidaten, revisierondes,
uitkomstreden en de workspace-PR. Een nieuwe iteratie krijgt
eerdere geaccepteerde uitkomsten en bestaande kandidaten als context. Exact gelijke titel- en
omschrijvingcombinaties worden bovendien via een stabiele fingerprint geblokkeerd.

## Opgeslagen gegevens

PostgreSQL bewaart iteratie- en stapstatus, gevalideerde roloutput, bronnen, beslissingen, UX,
interne kandidaten, criticusoordeel en workspaceverwijzingen. Dit is het operationele geheugen.
Git bevat alleen het door de criticus geaccepteerde, leesbare dossier met hetzelfde run-ID. De
workspace-PR wordt met auto-merge aangeboden en blijft door `Workspace validation` bewaakt.

Shadow mode bevat bewust geen scheduler en geen Software Factory-publicatie. Die bevoegdheden
horen pas bij fase 7.
