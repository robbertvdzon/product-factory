# Stap 6 — Productplanning MVP

Implementatiestatus: uitgevoerd. De actieve provider is `product-planning-impl-mvp`; migratie V8,
de tweefasen-Plannerflow, workitems, story/backlogcontract, dispatchreservering, snelle
leveringsgrenzen, REST/UI en Testbed 0.6.0 zijn onderdeel van dezelfde release. De automatische
bewijzen staan in `ProductPlanningMvpIntegrationTest` en de vaste repositoryverificatie.

## Doel en eindtoestand

Laat één Planneragent beschikbare epics en gericht planningswerk omzetten in complete,
zelfstandig uitvoerbare stories en één stabiele productbrede backlog. Na deze stap zijn planning,
herplanning, bugfix- en dekkingswerk, prioriteit, annulering en de grens voor latere dispatch
duurzaam en idempotent. Er wordt nog niets naar Software Factory verstuurd.

## Ingangseisen

- Stap 5 staat gezond op acceptatie en productie.
- `PRODUCT_DESIGNER_MVP` publiceert epics via de publieke API en `PLANNER_MVP` plus alle gebruikte
  planningsjobkeys zijn actief geconfigureerd.
- AI-uitvoering kan wachten en hervatten; Productontwerp-, product-, besluiten-, geheugen- en
  overlegqueries zijn beschikbaar.
- Kwaliteitscontracten bestaan, maar de echte kwaliteitsprovider wordt pas in stap 7 aangesloten.

## Normatieve bronnen

- [Productplanning-API](../processen/productplanning/api.md)
- [Productplanning MVP](../processen/productplanning/mvp.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Frontend](../stakeholder/frontend.md)

## Concrete opleveringen

### Module en duurzame gegevens

- Maak `product-planning-impl-mvp` de enige actieve provider van het publieke `planning`-contract;
  neem geen uitgebreide plannerrollen of provider op.
- Registreer implementatiegegevens in `ImplementationManifest` en iedere processessie.
- Voeg migraties toe voor `PlanningWorkItem`, workitembron en -versie, `Story`, onveranderlijke
  storyversies, dependencies, productbrede `sequenceNumber`, actuele verificatiereferentie,
  Software Factory-koppeling, `deliveredCommitSha`, processessies en idempotentiegegevens.
- Voeg een duurzame annuleringsmarker per epic toe, ook wanneer nog geen story bestaat.
- Voeg de atomaire dispatchreservering met idempotentiesleutel en herbevestigingsstatus toe. Dit is
  nog geen externe `DeliveryAttempt`; die hoort bij stap 8.
- Bewaar herstelbare uitgaande commandeffecten voor onder meer epicstatus en bug-storykoppeling.

### Publieke planningcommands en -queries

Implementeer het volledige publieke contract, waaronder:

- `runProcessSession(productId)` als enige planningsingang die AI-taken mag aanvragen;
- workitemcommands voor nieuw epicwerk, bugfix, ontbrekende dekking, oplevering, geannuleerde
  dependency en handmatige herprioritering;
- queries voor stories, backlog, processessies, workitems en `findBugs(...)` met de vereiste filters;
- atomaire `reserveNextStoryForDispatch(...)` en `revalidateDispatchReservation(...)`;
- snelle, agentloze commands voor dispatched, developed, cancelled en
  `recordStoryVerification(...)`;
- `cancelStoriesForEpic(...)`, die eerst de marker bewaart, niet-gereserveerde `TODO`-stories
  annuleert en een gereserveerde story volgens het externe-onzekerheidscontract laat afhandelen.

Alle commands controleren product, bron-ID/-versie, storyversie, status, actor en
idempotentiesleutel. Geen command geeft vrije schrijftoegang tot storyinhoud of volgorde.

### Verloop van één processessie

1. **Hervat vóór nieuw werk.** Een bestaande sessie en geclaimde `IN_PLANNING` epic hebben altijd
   voorrang. Een terminale AI-fout laat die claim zichtbaar staan en nieuw werk passeert haar niet.
2. **Claim een vaste batch.** Claim de op dat moment `PENDING` workitems voor één product en bevries
   beschikbare epics, open stories, productopdracht, besluiten, bug-/verificatiebronnen, Git-SHA en
   alleen het actuele geheugen van `PLANNER_MVP`.
3. **Laat de Planner kiezen.** Vraag een complete selectietaak aan. De sessie wacht zonder thread en
   hervat zonder duplicaat. Applicatiecode claimt iedere gekozen exacte epicversie via
   Productontwerp.
4. **Laat de Planner volledig plannen.** Gebruik dezelfde rol voor de complete storyset, gericht
   herstelwerk en de definitieve volgorde van alle bestaande en nieuwe `TODO`-stories.
5. **Valideer deterministisch.** Controleer het volledige Storycontract, epic-/bugbronversies,
   volledige epicdekking, UX, acceptatiecriteria, afhankelijkheden, annuleringsmarker, toegestane
   typen/statussen en unieke productbrede volgorde. Alleen `TODO` mag herordend worden.
6. **Publiceer atomair.** Schrijf stories, versies, dependencies, volgorde en workitemresultaten in
   één transactie. Rond herstelbare modulecommands met dezelfde idempotentiesleutels af en zet de
   epic daarna `ACTIVE`.

Een sessie heeft maximaal één onafgeronde logische instantie per product; verschillende producten
werken parallel. Nieuwe queue-items na de vaste batch wachten op een volgende sessie. Een
inhoudelijk ongeldig plan wordt niet gedeeltelijk gepubliceerd.

### Verplicht Story- en backlogcontract

Iedere story bevat minimaal stabiel ID, product, exacte epic- of bugbronversie, type
`PRODUCT_STORY` of `BUGFIX`, status, opgeslagen korte titel en samenvatting, volledige zelfstandige
beschrijving, relevante UX en assets, testbare acceptatiecriteria, dependencies, versie en
`sequenceNumber`.

- De backlog is de berekende lijst van alle `TODO`- en `IN_PROGRESS`-stories op productbrede
  volgorde; er is geen tweede backlog- of roadmapentiteit.
- Dependencies zijn pas voldaan bij `DONE`. `CANCELLED` blokkeert open afhankelijke stories en maakt
  idempotent `REPLAN_CANCELLED_DEPENDENCY`.
- Een bugfixstory wordt pas uitvoerbaar nadat `linkBugfixStory(bugId, storyId)` door
  Kwaliteitsbewaking is bevestigd. Per bug is maximaal één gekoppelde `TODO`/`IN_PROGRESS`-poging.
- Een eerdere `DONE` of `CANCELLED` bugfixpoging blijft historie en blokkeert een volgende niet.

### Snelle levering- en verificatieovergangen

Bouw nu al de deterministische grens die stap 7 en 8 gebruiken:

- reserveren kiest alleen de bovenste uitvoerbare `TODO`-story en zet geen vijfde publieke
  storystatus; de UI projecteert dit als **Wordt verstuurd**;
- dispatched maakt de story `IN_PROGRESS` en bewaart de externe referentie;
- developed vereist een volledige `deliveredCommitSha`, maakt de story `DONE` en vraagt via de
  publieke kwaliteits-API storyverificatie of bugfixhertest aan;
- cancelled maakt extern gestart werk `CANCELLED`, vraagt geen storytest aan en kan na al het
  overige geslaagde werk een feitelijke complete epicbeoordeling aanvragen;
- `recordStoryVerification(...)` verandert `DONE` niet, maar bewaart de actuele uitkomst en vraagt
  alleen epicverificatie wanneer alle actuele gerichte controles geslaagd zijn en geen herstelwerk
  resteert;
- bij afgekeurde of geblokkeerde controle blijft de epic `ACTIVE`.

Gebruik tot stap 7 contractfakes om deze overgangen volledig te testen. Activeer geen tijdelijke
kwaliteitsprovider op productie.

### HTTP, frontend, Testbed en operatie

- Voeg bevoegde handmatige planningstart toe via REST/UI, inclusief hervatting, 409 bij actieve call
  en succesvolle no-op.
- Bouw **Planning** met de berekende backlog, titel/samenvatting, type, status, dependencies,
  reservering, prioriteitsreden, geannuleerde stories apart en later zichtbare commit-/teststatus.
- Bouw story- en processessiedetail met volledige inhoud, bronversies, AI-taken, publicaties en
  fouten. Bied handmatige herprioritering met verplichte reden aan.
- Voeg Testbedscenario's toe voor volledige epicplanning, urgente herprioriteit, bugfix,
  dekkingswerk, geannuleerde dependency, annuleringsmarker, ongeldige agentoutput, wachtende taak en
  terminale taakfout.
- Toon workitems, behouden epicclaim, sessiestatus en reservering in Operatie.

## Uitvoeringsvolgorde

1. Breng API en MVP-specificatie exact gelijk en voeg contracttests toe.
2. Voeg module, registratie, migraties, repositories en alle constraints toe.
3. Implementeer workitemqueue, sessieclaiming, hervatting en epicclaim.
4. Implementeer Plannerprompt, AI-stappen, deterministic validation en atomische publicatie.
5. Implementeer annulering, dependencies, buglink en gerichte workitemcommands.
6. Implementeer dispatchreservering en snelle levering-/verificatieovergangen tegen contractfakes.
7. Voeg HTTP, frontend, Testbed en Operatie toe.
8. Voer de verplichte bewijzen uit en release via `main`.

## Verplichte automatische bewijzen

- volledige storydekking van oplossing, UX en alle epicacceptatiecriteria;
- unieke volgorde, alleen `TODO` herordenen en stabiele `IN_PROGRESS`-positie;
- hervatten van dezelfde `IN_PLANNING` epic na terminale taakfout;
- atomische publicatie en geen gedeeltelijke stories bij ongeldig resultaat of modulefout;
- idempotente workitems, buglink, annuleringsmarker en cross-module commands;
- race tussen planning, epicannulering en dispatchreservering heeft één geldige uitkomst;
- geannuleerde dependency maakt gericht herplanningswerk en wordt niet als voldaan gezien;
- twee producten kunnen parallel, één product niet dubbel;
- REST/frontend/Testbed/PostgreSQL/releasecontrole volgens de vaste afronding.

## Aanbevolen commitgrenzen

1. contracten, module en migraties;
2. workitems, sessies, Plannerflow en storypublicatie;
3. backlog, dependencies, annulering en buglink;
4. dispatchreservering en snelle statuscommands;
5. frontend, Testbed, Operatie, tests en documentatie.

## Buiten scope

Er komen geen gespecialiseerde plannerrollen of duurzame drafts uit `uitgebreid.md`. De dispatcher
doet nog geen externe call en Kwaliteitsbewaking voert nog geen test uit. Automatische schedules
blijven tot stap 9 uit.

## Definitie van klaar

Stap 6 is klaar wanneer één Planneragent een exacte epicversie volledig in zelfstandige stories
kan verdelen, gericht werk en prioriteit idempotent verwerkt, de productbrede backlog correct en
uitlegbaar is, annulering en reservering race-safe zijn, fouten dezelfde claim hervatten en dezelfde
geteste MVP-provider gezond op acceptatie en productie draait.
