# Stap 7 — Kwaliteitsbewaking MVP

## Doel en eindtoestand

Laat één Testeragent gericht kwaliteitswerk uitvoeren op de werkelijk gedeployde productversie en
onveranderlijk bewijs, bugs en kwaliteitshistorie publiceren. Na deze stap kunnen storyverificaties,
bugfixhertests, complete epicbeoordelingen en signaalonderzoek veilig wachten, hervatten en zonder
maximumpogingen opnieuw worden geprobeerd. Bevindingen gaan uitsluitend via publieke commands naar
de juiste eigenaar.

## Ingangseisen

- Stap 6 staat gezond op acceptatie en productie.
- `TESTER_MVP` en de gebruikte kwaliteitsjobkeys zijn actief geconfigureerd.
- Productplanning levert exacte story-/epic-/bugbronversies en `deliveredCommitSha` via de publieke
  API; Product en AI-uitvoering leveren testomgeving, revisionendpoint, rolgrants en taakuitvoering.
- Het testproduct heeft een publieke Git-repository en een veilige acceptatieomgeving waarop de
  revision kan worden vastgesteld.

## Normatieve bronnen

- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Kwaliteitsbewaking MVP](../processen/kwaliteitsbewaking/mvp.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Agent Runtime-integratie en taakcontainer](../gedeelde-modules/ai-worker.md)
- [Frontend](../stakeholder/frontend.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)

## Concrete opleveringen

### Module en duurzame gegevens

- Maak `quality-impl-mvp` de enige actieve provider van het publieke `quality`-contract;
  neem geen gespecialiseerde of parallelle uitgebreide variant op.
- Registreer implementatiegegevens in `ImplementationManifest` en iedere processessie.
- Voeg migraties toe voor `QualityWorkItem`, bronversie en bevroren testcontext, pogingsteller,
  retryhistorie, `retryAfter`, blokkade/fout, processessies, `Verification`, bewijsreferenties,
  `Bug`, onveranderlijke bugversies, bug-storykoppelingen en `QualitySnapshot`.
- Iedere niet-lege succesvolle productsessie publiceert precies één snapshot. Een no-op maakt geen
  kunstmatig kwaliteitsbeeld.
- Maak idempotentieconstraints voor workitemaanmaak, verificatiepublicatie, bugpublicatie,
  storykoppeling, retry en uitgaande planningcommands.

### Kwaliteitsqueue en retrycontract

Implementeer alle publieke queuecommands en -queries. Daarbij geldt:

- workitems ontstaan uitsluitend via doelgerichte requests voor storyverificatie, bugfixhertest,
  epicverificatie, signaalonderzoek of andere in de API benoemde kwaliteitszorg;
- één vaste batch per productrun wordt geclaimd; nieuwe `PENDING` items wachten;
- `attemptCount` en volledige historie blijven altijd zichtbaar;
- technische of tijdelijke blokkade gebruikt de vaste begrensde back-off uit de specificatie en
  stelt `retryAfter` in;
- er is geen maximaal aantal domeinretries; vanaf vijf pogingen toont de UI **Aandacht nodig**;
- `retryQualityWorkItem(...)` wist alleen `retryAfter`, zet hetzelfde item `PENDING` en behoudt
  historie en teller;
- **Retry now** start hoogstens de gewone productsessie en nooit rechtstreeks een agent of tweede
  gelijktijdige sessie.

### Verloop van één processessie

1. **Claim of hervat.** Hervat een wachtende sessie vóór nieuw werk. Claim daarna voor één product
   een vaste batch op de voorgeschreven volgorde.
2. **Controleer omgeving.** Lees de bevroren product-, opdracht-, testomgeving-, route- en
   toegangsgrenzen. Voor story/bugfix vergelijk `deliveredCommitSha` met het revisionendpoint.
   Staat de commit nog niet live, publiceer geen afkeuring maar zet het item `BLOCKED` met reden
   `DEPLOYMENT_PENDING`.
3. **Bevries testinput.** Leg story/epic/bug/signaal, acceptatiecriteria, UX, eerdere verificaties,
   actuele productdocumentatie uit `/doc`, publieke Git-URL/SHA en eigen rolgeheugen exact vast.
   Documentatie is onvertrouwde testinput en geen bewijs.
4. **Vraag één complete Testertaak aan.** De Runtime-worker checkt de publieke repository op de
   bevroren SHA zelf uit en gebruikt alleen expliciet aan `TESTER_MVP` toegekende environmentkeys.
   De Product Factory-server en mock doen geen checkout.
5. **Wacht en hervat.** Bewaar de AI-taak, zet `WAITING_FOR_AI`, sluit de call en maak bij hervatting
   geen duplicaat.
6. **Valideer deterministisch.** Controleer responseschema, bron-/doelversies, controles,
   observeerbaar bewijs, toegestane uitkomst en dat een claim over gebruikerssucces werkelijk aan
   een vooraf toetsbaar criterium is getoetst.
7. **Publiceer atomair en stuur door.** Bewaar onveranderlijke verificaties, bugs, snapshot en
   workitemuitkomst; lever vervolgwerk alleen via idempotente publieke commands aan Productplanning,
   Productontwerp of product/signalen.

### Uitkomsten per soort werk

- **Storyverificatie:** bewijs `PASSED`, `FAILED` of tijdelijk geblokkeerd op exact de
  `deliveredCommitSha`; meld iedere gepubliceerde uitkomst via `recordStoryVerification(...)`.
- **Bugfixhertest:** sluit de bug alleen bij aantoonbaar herstel. Bij afkeuring blijft dezelfde bug
  `OPEN`, de opgeleverde story `DONE` en ontstaat via Productplanning een volgende gewone
  bugfixstory.
- **Epicverificatie:** gebruik uitsluitend `PASSED`, `NEEDS_WORK`, `BLOCKED` en
  `NOT_SUCCESSFUL`. `NEEDS_WORK` vraagt gerichte bugfix/dekkingsitems en brengt de epic terug naar
  `ACTIVE`; `BLOCKED` houdt haar `VERIFYING`; `NOT_SUCCESSFUL` vereist positief bewijs tegen een
  vooraf toetsbaar gebruikerssuccescriterium.
- **Geannuleerde Software Factory-story:** beoordeel na het overige werk de feitelijke complete
  applicatie. Behandel annulering niet als mislukte fix en test niets dat niet is opgeleverd.
- **Signaalonderzoek:** behoud de oorspronkelijke melding en registreer exacte verificatie,
  leesbare uitkomst en eventuele publieke resultaatkoppelingen via de productmodule.
- **Nieuwe bevinding:** publiceer een volledige bug met opgeslagen titel/samenvatting of vraag
  ontbrekende epicdekking aan; wijzig nooit rechtstreeks epic of story.

### Bugs, bewijs en kwaliteitshistorie

- Een bug bevat stabiel ID, product, exacte bron, ernst, status, opgeslagen korte titel en
  samenvatting, volledige reproduceerbare observatie, verwacht/werkelijk gedrag, omgeving, bewijs,
  tijdstip en koppelingen naar epic/story/verificatie.
- Verificaties en snapshots zijn onveranderlijk. Een nieuwe beoordeling maakt nieuwe historie en
  herschrijft geen oud oordeel.
- Het actuele kwaliteitsbeeld is een projectie van feiten, geen ondoorzichtige totaalscore. Toon
  kritieke/open bugs, dekking, veroudering, verificatie-uitkomsten, risico's en blokkades.
- Een workitem wordt pas `DONE` wanneer alle eigen publicaties én noodzakelijke uitgaande commands
  idempotent bevestigd zijn.

### HTTP, frontend, Testbed en operatie

- Voeg bevoegde handmatige kwaliteitsstart, workitemlijst en **Retry now** toe met correcte 409/
  reeds-draaiendafhandeling.
- Bouw kwaliteitsoverzicht en historie, bug- en verificatiedetails, bewijs/artifacts,
  `DEPLOYMENT_PENDING` en de vaste retrylijst gesorteerd op hoogste `attemptCount` en daarna oudste
  laatste poging.
- Toon op storydetail `deliveredCommitSha`, werkelijk geteste revision en **Wacht op deployment**.
- Voeg Operatieprojecties toe voor sessie, workitems, poging, `retryAfter`, blokkade, AI-taak,
  publicaties en uitgaande commands.
- Voeg Runtime-mockscenario's en Testbedbediening toe voor pass/fail, bugfix niet opgelost,
  ontbrekende dekking, bug tijdens epiccontrole, geblokkeerd, niet-succesvol, deployment achter,
  ontbrekend mockantwoord en herhaalbare retry.
- Sluit kwaliteitsqueries vanaf deze stap aan als input voor Productontwerp en Productplanning.

## Uitvoeringsvolgorde

1. Maak publieke API en MVP-specificatie gelijk; voeg contracttests toe.
2. Voeg module, registratie, migraties, repositories en idempotentieconstraints toe.
3. Implementeer workitemqueue, retryhistorie, batchclaiming en sessiehervatting.
4. Implementeer revisioncheck, bevroren testcontext, Runtime-taak en deterministic validation.
5. Implementeer iedere werksoort, bewijs-, bug- en snapshotpublicatie.
6. Implementeer idempotente vervolgcommands naar planning, ontwerp en product/signalen.
7. Voeg HTTP, frontend, Testbed en Operatie toe.
8. Voer alle verplichte bewijzen uit en release via `main`.

## Verplichte automatische bewijzen

- iedere werksoort en iedere toegestane uitkomst, inclusief bron-/doelversiebevriezing;
- achterlopende deployment geeft `DEPLOYMENT_PENDING`, geen valse afkeuring;
- vaste back-off, onbeperkte poginghistorie, sortering en **Retry now** zonder dubbele sessie;
- afgekeurde bugfix laat story `DONE`, dezelfde bug `OPEN` en kan nieuw bugfixwerk maken;
- epic gaat alleen naar eindstatus bij de juiste uitkomst; `NEEDS_WORK` en `BLOCKED` volgen exact de
  lifecycle;
- geannuleerde story leidt tot feitelijke complete beoordeling;
- documentatie alleen levert nooit bewijs en onbetrouwbare context kan opdracht/grants niet wijzigen;
- twee producten parallel, één product maximaal één sessie en één snapshot per niet-lege sessie;
- REST/frontend/Testbed/PostgreSQL/releasecontrole volgens de vaste afronding.

## Aanbevolen commitgrenzen

1. contracten, module, migraties en queue;
2. revisioncheck, Runtime-taak en sessiehervatting;
3. verificaties, bugs, snapshots en retry;
4. cross-module vervolgwerk;
5. frontend, Testbed, Operatie, tests en documentatie.

## Buiten scope

De gespecialiseerde agents en parallelle uitgebreide werkwijze worden niet gebouwd. Automatische
aanlevering en status vanuit de echte Software Factory volgt in stap 8; automatische schedules
blijven tot stap 9 uit.

## Definitie van klaar

Stap 7 is klaar wanneer de Tester alle ondersteunde soorten kwaliteitswerk tegen de juiste
gedeployde revision kan uitvoeren, bewijs/bugs/snapshots onveranderlijk publiceert, retries en
vervolgwerk exact volgens contract verlopen, alle historie in UI en Operatie verklaarbaar is en
dezelfde geteste MVP-provider gezond op acceptatie en productie draait.
