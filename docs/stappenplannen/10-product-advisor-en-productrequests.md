# Stap 10 — Product Advisor en Product Requests

Implementatiestatus: gepland, nog niet uitgevoerd.

## Doel en eindtoestand

Bouw in Product Factory een productgebonden ingang waarmee een product owner zelfstandig met
`PRODUCT_ADVISOR` kan overleggen, vragen over het bestaande product kan stellen en een concreet
wijzigingsvoorstel kan laten maken.

Na menselijke bevestiging volgt een verzoek precies één van deze routes:

- `HOTFIX`: rechtstreeks als bestaande Software Factory-hotfix;
- `BUGFIX`: rechtstreeks als gewone Software Factory-bugfix;
- `EPIC_CANDIDATE`: gericht naar Productontwerp, gevolgd door goedkeuring van de product owner en
  daarna de factory owner.

Vragen uit de uitwerking komen bij de juiste gebruiker terecht. Een informatief gesprek kan zonder
request, story of epic worden gesloten.

Dit plan is geschreven als geordende bouwopdracht voor Software Factory. **Iedere story in dit plan
wijzigt uitsluitend de repository `product-factory`.** Software Factory is de uitvoerder, niet het
doelproduct. De repositories `softwarefactory` en `pvdd` blijven ongewijzigd.

## Normatieve bron

Het volledige functionele en technische toekomstontwerp staat in
[Product Advisor en Product Requests](../voorstellen/product-advisor-en-productrequests.md). Bij
verschil gaat dat voorstel voor en wordt dit stappenplan in dezelfde wijziging bijgewerkt.

Daarnaast blijven de bestaande actuele Product Factory-specificaties leidend voor modulegrenzen,
beveiliging, AI-uitvoering, deployment en testen:

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Product- en overlegmodule](../stakeholder/product-en-overleg-api.md)
- [Frontend](../stakeholder/frontend.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Agent Runtime-worker](../gedeelde-modules/ai-worker.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productontwerp MVP](../processen/productontwerp/mvp.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Software Factory-dispatcher](../processen/software-factory-dispatcher.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Configuratie en secrets](../platform/configuratie-en-secrets.md)

## Harde uitvoeringsregels

- Bewerk geen bestand buiten `/Users/robbertvdzon/git/product-factory`.
- Voeg geen endpoint, veld, test, prompt, migratie of configuratie toe aan Software Factory.
- Voeg geen code, testhook of speciaal AI-account toe aan PvdD.
- Gebruik voor gewone stories en bugfixes uitsluitend het bestaande Software Factory-v2-contract.
- Gebruik voor hotfixes uitsluitend de reeds bestaande gewone Software Factory-story-API met
  `hotfix=true`; zie de verplichte spike en story `PF-PA-016`.
- Voeg nergens een diffgroottecontrole of hotfixspecifieke controle achteraf toe.
- AI-output mag nooit zelfstandig een externe mutatie uitvoeren. Alleen een deterministisch
  Product Factory-command na goedkeuring van een exacte versie mag routeren of dispatchen.
- Iedere publieke mutatie controleert actor, producttoegang, verwachte versie en idempotentiesleutel.
- Iedere nieuwe duurzame structuur krijgt een voorwaartse Flywaymigratie en migratiesmoketest.
- Iedere story houdt actuele documentatie, contracttests, backendtests, frontendtests en waar nodig
  Testbedfixtures binnen dezelfde oplevering bij.
- Bestaande wijzigingen in de werkboom blijven behouden; een story raakt alleen eigen scope.

## Ingangseisen

- De stappen 1 tot en met 9 van het bestaande MVP zijn uitgevoerd en blijven groen.
- Product Factory kan Software Factory v2 bereiken met het bestaande integratietoken.
- PvdD heeft een publieke Git-URL in `ProductAssignment` en een bruikbare
  `TestableProductConfiguration`.
- De Runtime-worker kan de PvdD-repository op een exacte SHA uitchecken.
- Google SSO werkt voor het bestaande factory owner-account.
- De product owner beschikt over een door Google geverifieerd e-mailadres.
- Voor de automatische hotfixroute kan een geldig Software Factory-dashboardtoken als Product
  Factory-secret beschikbaar worden gemaakt. De spike moet bewijzen of dit operationeel
  acceptabel is.

## Volgorde en afhankelijkheden

```text
PF-PA-000 technische spike
        |
        v
PF-PA-001 identiteit en rollen
        |
        v
PF-PA-002 productautorisatie -> PF-PA-003 ledenbeheer-UI
        |
        v
PF-PA-004 gesprekdomein -> PF-PA-005 gespreks-UI
        |
        v
PF-PA-006 advisorrol en context -> PF-PA-007 asynchrone advisorbeurt
        |
        v
PF-PA-008 requestdomein -> PF-PA-009 voorstel en bevestiging
        |
        +--------------------------+--------------------------+
        |                          |                          |
        v                          v                          v
PF-PA-010 vragen          PF-PA-014 gewone bugfix      PF-PA-016 hotfix
        |
        v
PF-PA-011 designworkitem -> PF-PA-012 dubbele approval-backend
                                      |
                                      v
                              PF-PA-013 approval-UI

alle routes -> PF-PA-015 status en notificaties -> PF-PA-017 ketenafsluiting
```

De nummering bepaalt de aanbevolen uitvoeringsvolgorde. `PF-PA-014` en `PF-PA-016` mogen na
`PF-PA-009` parallel in aparte worktrees worden uitgevoerd wanneer zij geen gedeelde bestanden
wijzigen. `PF-PA-017` begint pas wanneer alle eerdere stories op dezelfde hoofdbranch zijn
geïntegreerd.

## PF-PA-000 — Bewijs de bestaande externe ingangen

### Doel

Voorkom dat het domein op een onbewezen Software Factory- of Runtime-aanname wordt gebouwd. Deze
story levert alleen Product Factory-testcode, fixtures en een beslisdocument op.

### Werk

- Maak een kleine Product Factory-contractprobe voor het bestaande Software Factory-v2-status- en
  storycontract.
- Bewijs met een lokale stub dat een `ProductRequest`-ID en versie als v2 `sourceStoryId` en
  `sourceStoryVersion` kunnen worden gebruikt voor type `BUGFIX`.
- Bouw een geïsoleerde contractprobe voor de bestaande Software Factory
  `POST /api/v1/stories`-vorm met `hotfix=true` en `start=true`.
- Bewijs op een gecontroleerde omgeving dat deze call een story met de bestaande hotfixketen maakt.
- Bewijs dat `stories.list` een stabiele marker in de omschrijving kan terugvinden na een
  gesimuleerde verloren create-response.
- Leg de dertigdaagse levensduur en het vernieuwingsproces van het dashboardtoken vast.
- Dien een credentialloze Runtime-probetaak voor PvdD in die de exacte Git-SHA en actuele
  documentatie rapporteert.
- Bewijs met de bestaande veilige testconfiguratie dat browsergereedschap de bedoelde omgeving kan
  openen.

### Acceptatiecriteria

- Het spikeverslag bevat per aanname `BEWEZEN` of `GEBLOKKEERD` met reproduceerbaar bewijs.
- Er is geen wijziging in Software Factory of PvdD.
- Geen token- of credentialwaarde staat in Git, uitvoer, artifact of log.
- Een geblokkeerde hotfixprobe blokkeert alleen `PF-PA-016`; de gewone bugfix- en epicroute kunnen
  verder worden gebouwd.
- Zonder bewezen duurzame of operationeel geaccepteerde tokenroute wordt automatische hotfix niet
  als gereed aangemerkt.

### Afhankelijkheden

Geen.

## PF-PA-001 — Duurzame gebruikersidentiteit en rollen

### Doel

Vervang de impliciete globale Stakeholderidentiteit door een generiek usermodel met globale
factory owner-rol en productgebonden lidmaatschap, zonder bestaande toegang te verliezen.

### Werk

- Voeg `UserId`, `UserDetails`, globale rol `FACTORY_OWNER` en `ProductMembership` met rol
  `PRODUCT_OWNER` toe aan `product-factory-api`.
- Voeg tabellen, constraints en auditvelden toe voor gebruikers, globale rollen en
  productlidmaatschappen.
- Migreer het bestaande toegestane Stakeholderaccount idempotent naar `FACTORY_OWNER`.
- Koppel een Product Factory-sessie aan `UserId`, genormaliseerd e-mailadres en effectieve globale
  rollen.
- Stop met iedere ingelogde gebruiker automatisch `ROLE_STAKEHOLDER` te geven.
- Bewaar bestaande HttpOnly-cookie-, CSRF-, expiry-, logout- en Google-verificatie-eigenschappen.
- Lever read-only queries voor de actuele gebruiker en zijn lidmaatschappen.

### Acceptatiecriteria

- Een bestaand factory owner-account kan na migratie zonder handmatige databasewijziging inloggen.
- Een allowlisted maar nog niet toegewezen gebruiker kan authenticeren, maar ziet geen productdata.
- E-mailvergelijking is genormaliseerd en dubbele userrecords zijn onmogelijk.
- Sessie- of autorisatielogs bevatten geen Google ID-token, cookie of CSRF-token.
- Authenticatie- en migratietests dekken lege database, upgrade en herhaalde migratie.

### Afhankelijkheden

`PF-PA-000` moet aantonen dat de hoofdroute technisch haalbaar blijft.

## PF-PA-002 — Centrale productgebonden backendautorisatie

### Doel

Dwing af dat een product owner alleen toegewezen producten kan lezen en bedienen en dat een
factory owner Product Factory-brede bediening behoudt.

### Werk

- Introduceer één centrale autorisatieservice voor `canReadProduct`, `canActOnProduct` en
  `requireFactoryOwner`.
- Pas alle bestaande productgebonden HTTP-queries en commands toe op deze service.
- Filter productlijsten in de backend; laad niet eerst alles en filter niet alleen in Flutter.
- Bescherm globale AI-instellingen, credentials, schedules, productaanmaak, lidmaatschappen en
  technische operatie als factory owner-functies.
- Houd actorregistratie op bestaande commands intact maar gebruik de echte `UserId` als actor-ID.
- Definieer duidelijke `401` voor niet ingelogd en `403` voor ingelogd maar onbevoegd.

### Acceptatiecriteria

- Een product owner krijgt via directe HTTP-aanroepen geen data van een ander product.
- Een product owner kan geen globale instellingen of productlidmaatschappen wijzigen.
- Een factory owner behoudt alle bestaande product- en beheerfuncties.
- Iedere betrokken controller heeft positieve en negatieve autorisatietests.
- Bestaande processessies en technische schedulercommands blijven onder vertrouwde systeemactoren
  functioneren.

### Afhankelijkheden

`PF-PA-001`.

## PF-PA-003 — Ledenbeheer en productgescopete navigatie

### Doel

Laat een factory owner product owners beheren en geef iedere gebruiker een UI die uitsluitend
toegestane functies toont.

### Werk

- Voeg onder beheer een ledenoverzicht toe met gebruiker, e-mailadres, globale rol,
  productlidmaatschap, status en historie.
- Voeg commands en UI toe om een product owner aan een product te koppelen of toegang in te trekken.
- Vereis een expliciete bevestiging en reden bij intrekken.
- Laat de productselector uitsluitend toegestane producten tonen.
- Verberg factory owner-secties voor product owners zonder daarop als beveiliging te vertrouwen.
- Toon een begrijpelijke lege toestand wanneer een gebruiker nog geen product heeft.
- Maak routes bookmarkbaar en herstel selectie na reload volgens de bestaande frontendregels.

### Acceptatiecriteria

- Robbert kan Marc via de UI aan PvdD koppelen.
- Marc ziet na een nieuwe sessie PvdD en geen ander product of beheerfunctie.
- Intrekken maakt nieuwe backendtoegang onmiddellijk onmogelijk en verwijdert geen historie.
- Frontendtests dekken factory owner, product owner, geen lidmaatschap en ingetrokken lidmaatschap.

### Afhankelijkheden

`PF-PA-002`.

## PF-PA-004 — ProductConversation en append-only berichten

### Doel

Maak een duurzaam, productgebonden gesprek dat informatief kan blijven en onafhankelijk is van
meetings, signalen, requests, epics en stories.

### Werk

- Voeg publieke ID's, DTO's, filters en commands toe voor `ProductConversation` en
  `ProductConversationMessage`.
- Bewaar gesprekstitel, maker, product, status, versie, timestamps en append-only berichten.
- Ondersteun afzenders `USER`, `PRODUCT_ADVISOR` en `SYSTEM` als vertrouwde enumwaarden.
- Ondersteun `OPEN`, `PROCESSING`, `WAITING_FOR_USER`, `PROPOSAL_READY`, `BLOCKED` en `CLOSED` met
  betekenisvolle overgangscommands.
- Maak berichttoevoeging idempotent en orden op een duurzame sequentie, niet alleen timestamp.
- Voeg get/find-queries met product- en gebruikersautorisatie toe.
- Voeg REST-contracten toe zonder interne prompt-, queue- of agentadministratie te publiceren.

### Acceptatiecriteria

- Een gesprek kan zonder request worden gestart, gebruikt en gesloten.
- Herhaalde berichtcalls met dezelfde idempotentiesleutel maken één bericht.
- Gelijktijdige geldige berichten krijgen een unieke stabiele volgorde of een zichtbaar
  versieconflict; er gaat niets stil verloren.
- Alleen bevoegde productgebruikers kunnen gesprek en berichten lezen.
- Domein-, repository-, contract- en PostgreSQL-migratietests zijn groen.

### Afhankelijkheden

`PF-PA-002`.

## PF-PA-005 — Gesprekkenlijst en chatscherm

### Doel

Maak Productgesprekken voor een niet-technische product owner bruikbaar voordat AI wordt
aangesloten.

### Werk

- Voeg **Gesprekken** en **Nieuw gesprek** toe aan de product owner-navigatie.
- Bouw een lijst met titel, laatste activiteit, actuele status en maker.
- Bouw een chatscherm met selecteerbare berichten, invoerveld, verzendstatus en foutafhandeling.
- Poll alleen de zichtbare conversatie en voorkom widgetupdates wanneer inhoud gelijk blijft.
- Behoud scrollpositie en getypte invoer bij stille refresh.
- Ondersteun sluiten en een duidelijke read-only gesloten toestand.
- Toon een generieke placeholder voor latere voorstelkaarten zonder al ProductRequest te simuleren.

### Acceptatiecriteria

- Marc kan vanaf 320px breed en op desktop een gesprek starten en berichten toevoegen.
- Reload, browser terug/vooruit en een bookmark herstellen product en gesprek.
- Technische fout, autorisatiefout en wachtstatus zijn duidelijk verschillend.
- De UI toont geen termen als prompt, AI-taak-ID, database of queue.
- Flutter analyze, widgettests en relevante browsertest zijn groen.

### Afhankelijkheden

`PF-PA-003` en `PF-PA-004`.

## PF-PA-006 — Registreer PRODUCT_ADVISOR en stel veilige context samen

### Doel

Maak `PRODUCT_ADVISOR` een configureerbare Product Factory-agentrol met uitsluitend de benodigde
productcontext en credentials.

### Werk

- Registreer stabiele `AgentRoleKey("PRODUCT_ADVISOR")`, weergavenaam en rolgrenzen.
- Voeg een globale AI-jobconfiguratie voor `PRODUCT_ADVISOR.CONVERSE` toe.
- Maak rolgeheugen volgens het bestaande versieerbare geheugencontract beschikbaar.
- Maak projectcredentialgrants voor deze rol beheerbaar; standaard heeft de rol geen grants.
- Bouw een contextassembler met productopdracht, geldige besluiten, relevante publieke
  productobjecten, advisorrolgeheugen, conversatiecontext, exacte Git-URL/SHA en testconfiguratie.
- Leg gebruikte bron-, geheugen-, config- en prompttemplateversies vast.
- Gebruik de bestaande Runtime `RepositorySnapshot`; de Product Factory-server checkt zelf geen
  repository uit.
- Markeer broncode, documentatie, browserinhoud en gebruikersberichten expliciet als onvertrouwde
  context in de vaste opdracht.

### Acceptatiecriteria

- De rol verschijnt in de bestaande rol- en AI-jobcatalogus.
- Alleen expliciet aan `PRODUCT_ADVISOR` toegekende credentials worden aan Runtime beschikbaar
  gemaakt; waarden blijven overal geheim.
- Een contextsnapshot bevat een volledige exacte SHA en geen mutable branch-only verwijzing.
- Geheugen van andere rollen ontbreekt aantoonbaar.
- Een onbevoegd product of ontbrekende veilige testconfiguratie faalt gesloten en leesbaar.

### Afhankelijkheden

`PF-PA-004` en de bewezen Runtime-route uit `PF-PA-000`.

## PF-PA-007 — Asynchrone advisorbeurt met antwoord of vervolgvraag

### Doel

Laat ieder gebruikersbericht exact één hervatbare AI-beurt veroorzaken die een brongetrouw antwoord
of een concrete vervolgvraag publiceert.

### Werk

- Bouw `ProductAdvisorOrchestrator` volgens de bestaande duurzame AI-uitvoeringsgrens.
- Reserveer één logische advisorbeurt per onbeantwoord gebruikersbericht.
- Vraag een complete `APPLICATION_WORK`-taak aan met repository- en browsergereedschap.
- Zet het gesprek op `PROCESSING`, retourneer direct en hervat via scheduler/polling zonder thread of
  database-lock vast te houden.
- Valideer een strikt resultaat met minimaal `message`, `outcome` en gebruikte waarnemingen.
- Sta in deze story alleen `ANSWER` en `ASK_FOLLOW_UP` toe; wijzigingsvoorstellen volgen later.
- Publiceer het advisorbericht en de nieuwe gesprekstatus atomair en idempotent.
- Ondersteun begrensde retry, terminale `BLOCKED` en een bevoegde **Opnieuw proberen**-actie.

### Acceptatiecriteria

- Een informatievraag kan eindigen zonder ProductRequest, epic of story.
- Herhaald hervatten maakt geen tweede AI-taak en geen dubbel advisorbericht.
- Een technisch mislukte taak verliest het gebruikersbericht niet.
- De advisor kan in een Testbedscenario aantoonbaar code en documentatie op de bevroren SHA lezen.
- Een browseronderzoek vermeldt de gebruikte omgeving zonder credentials of gevoelige data.
- Promptinjectie-fixtures kunnen geen Product Factory-command of externe dispatch afdwingen.

### Afhankelijkheden

`PF-PA-005` en `PF-PA-006`.

## PF-PA-008 — Geversioneerd ProductRequest-domein

### Doel

Bewaar een wijzigingsvoorstel los van het gesprek en bind iedere menselijke bevestiging aan één
exacte inhoudsversie.

### Werk

- Voeg `ProductRequestId`, type `HOTFIX|BUGFIX|EPIC_CANDIDATE`, statussen en publieke DTO's toe.
- Bewaar request, onveranderlijke versies, bronconversation, aanvrager, onderzoek, Git-SHA,
  acceptatiecriteria, scope, grenzen en vervolgkoppeling.
- Implementeer `propose`, `revise`, `approve`, `cancel`, `markRouting`, `markRouted` en
  `markRoutingFailed` als betekenisvolle commands.
- Bewaar iedere approval met user-ID, requestversie en tijdstip.
- Laat revisie een nieuwe versie maken en een approval op de vorige versie niet meenemen.
- Garandeer per requestversie en route-idempotentiesleutel maximaal één routeringsoperatie.
- Voeg get/find-queries toe voor gebruiker, product, status, type en periode.

### Acceptatiecriteria

- Een ProductRequest ontstaat niet door alleen een normaal gesprek te voeren.
- De oorspronkelijke conversatie blijft onveranderd en gekoppeld.
- Een oude requestversie kan na revisie niet worden goedgekeurd of gerouteerd.
- Dubbele approval of routing met dezelfde sleutel heeft één effect.
- Een gekoppelde externe story of epic kan niet door een tweede vervolgkoppeling worden vervangen.

### Afhankelijkheden

`PF-PA-004` en `PF-PA-002`.

## PF-PA-009 — Advisorvoorstel, voorstelkaart en bevestiging

### Doel

Laat de advisor na voldoende onderzoek een gestructureerd voorstel doen en laat Marc dit aanpassen,
bevestigen of verwerpen zonder dat AI zelf ontwikkeling start.

### Werk

- Breid het advisor-outputcontract uit met optionele `PROPOSE_CHANGE` en een volledig concept voor
  één van de drie requestsoorten.
- Valideer verplichte velden, exacte product- en broncontext, acceptatiecriteria en routekeuze
  deterministisch.
- Leg uitsluitingscategorieën voor hotfix vast: autorisatie, privacy, datamodel, migratie,
  dependency, externe integratie, belangrijke businessregel, AI-prompt, disclaimer en politieke
  inhoud.
- Publiceer een geldige voorstelversie en zet het gesprek op `PROPOSAL_READY`.
- Bouw de voorstelkaart met soort, probleem, verwacht gedrag, scope, criteria en waarschuwing bij
  hotfix.
- Voeg **Aanpassen**, **Bevestigen** en **Niet uitvoeren** toe.
- Laat **Aanpassen** als nieuw gebruikersbericht een nieuwe advisorbeurt veroorzaken; overschrijf de
  oude versie niet.
- Laat **Bevestigen** uitsluitend het ProductRequest goedkeuren; routering gebeurt door latere
  stories.

### Acceptatiecriteria

- AI-output alleen maakt geen epic en geen Software Factory-story.
- Marc ziet alle contractinhoud voordat hij een exacte versie bevestigt.
- Een inhoudelijk ongeldig voorstel wordt niet gepubliceerd en veroorzaakt geen domeineffect.
- Een gewijzigde voorstelversie vereist nieuwe bevestiging.
- Hotfixwaarschuwing vermeldt de verkorte bestaande Software Factory-keten.
- De advisor kan na vervolgonderzoek van voorgestelde route veranderen voordat Marc bevestigt.

### Afhankelijkheden

`PF-PA-007` en `PF-PA-008`.

## PF-PA-010 — Vragen aan een expliciete gebruiker

### Doel

Routeer vragen van advisor, Productontwerp en Productplanning naar Marc of Robbert in plaats van één
impliciete globale Stakeholder.

### Werk

- Breid `StakeholderQuestion` uit met `requestedRespondentUserId` en optionele request-, epic- en
  storykoppelingen.
- Migreer bestaande open en historische vragen naar de bestaande factory owner als geadresseerde.
- Valideer dat de geadresseerde een actieve product owner of factory owner is.
- Laat vragen uit een ProductRequest standaard naar de requestaanvrager gaan.
- Behoud rol, processessie, context, status, versie en antwoordhistorie.
- Voeg **Vragen aan jou** toe met badge, lijst, detail en directe antwoordactie.
- Sta antwoord toe aan de geadresseerde en een factory owner, maar registreer de werkelijke actor.
- Zorg dat een opgeslagen antwoord door exact de gekoppelde wachtende sessie wordt gelezen.

### Acceptatiecriteria

- Marc ziet alleen aan hem gerichte vragen binnen toegestane producten.
- Een andere product owner kan de vraag niet lezen of beantwoorden.
- Antwoord op vraag A hervat niet per ongeluk workitem of sessie B.
- Vragen blijven na restart zichtbaar en antwoorden zijn idempotent.
- Oude vragen blijven leesbaar na migratie.

### Afhankelijkheden

`PF-PA-002`, `PF-PA-008` en `PF-PA-009`.

## PF-PA-011 — Gerichte DesignWorkItem-queue

### Doel

Laat een goedgekeurde `EPIC_CANDIDATE` gericht door Productontwerp verwerken, zonder een agent
rechtstreeks vanuit het requestcommand te starten.

### Werk

- Voeg `DesignWorkItemId`, doel `CREATE_EPIC_FROM_PRODUCT_REQUEST`, statussen en publieke
  commands/queries toe.
- Bewaar product, request-ID, exacte requestversie, idempotentiesleutel, claim, attempts,
  sessiekoppeling en gepubliceerd epic-ID.
- Implementeer `requestEpicFromProductRequest(...)` als snel queuecommand zonder AI-aanvraag.
- Laat `runProcessSession(productId)` eerst een bestaande sessie hervatten, daarna gericht werk
  claimen en pas daarna autonoom ontwerpwerk kiezen.
- Voeg de volledige goedgekeurde requestversie en bronconversationreferentie toe aan de bevroren
  ontwerpsnapshot.
- Laat Productontwerp zo nodig een gerichte vraag uit `PF-PA-010` publiceren en veilig wachten.
- Koppel de gepubliceerde epic en markeer request/workitem idempotent als gerouteerd/afgerond.

### Acceptatiecriteria

- Eén goedgekeurde requestversie maakt maximaal één actief workitem en één epic.
- Een queuecommand start geen AI-taak.
- Een crash of restart hervat hetzelfde workitem en dezelfde processessie.
- Gericht werk heeft voorrang op nieuw autonoom ontwerpwerk maar onderbreekt geen lopende sessie.
- De epic voldoet aan het volledige bestaande Epiccontract en is geen blinde kopie van het request.

### Afhankelijkheden

`PF-PA-010`.

## PF-PA-012 — Dubbele epicgoedkeuring in backend en domein

### Doel

Maak een epic uit een ProductRequest pas `AVAILABLE` nadat eerst de aangewezen product owner en
daarna een factory owner dezelfde versie hebben goedgekeurd.

### Werk

- Voeg statussen `AWAITING_PRODUCT_OWNER_APPROVAL` en `AWAITING_FACTORY_OWNER_APPROVAL` toe of leg
  een equivalente, eenduidige statusprojectie vast zonder dubbele waarheid.
- Voeg onveranderlijke `EpicApprovalRecord`s met rol, user-ID, epicversie en tijdstip toe.
- Implementeer afzonderlijke commands voor product owner- en factory owner-goedkeuring.
- Laat Productontwerp een request-epic eerst voor product owner-goedkeuring publiceren.
- Laat de eerste approval naar factory owner-goedkeuring gaan en de tweede naar `AVAILABLE`.
- Laat beide rollen met verplichte reden `requestEpicRefinement(...)` gebruiken.
- Laat een nieuwe epicversie alle approvals van de vorige versie historisch behouden maar niet
  erven.
- Laat Productplanning uitsluitend `AVAILABLE` claimen; voeg regressietests tegen halfgoedgekeurde
  epics toe.
- Behoud het bestaande goedkeuringsgedrag voor epics die niet uit deze nieuwe route komen, tenzij
  productconfiguratie expliciet hetzelfde tweestapsbeleid kiest.

### Acceptatiecriteria

- Een factory owner kan niet vóór de product owner dezelfde epicversie goedkeuren.
- Een product owner kan niet de factory owner-stap uitvoeren.
- Productplanning kan geen epic in een van beide wachtstatussen claimen.
- Terugsturen plus revisie vereist twee nieuwe approvals.
- Gelijktijdige approval en revisie eindigt in één geldige toestand zonder verloren historie.

### Afhankelijkheden

`PF-PA-011` en `PF-PA-002`.

## PF-PA-013 — Epicapproval-UI voor Marc en Robbert

### Doel

Maak de twee verschillende beoordelingen begrijpelijk en uitvoerbaar zonder operationele kennis.

### Werk

- Toon bij epicdetail bronrequest, exacte versie, volledige epicinhoud en beide approvalstatussen.
- Toon Marc alleen **Productinhoud goedkeuren** of **Terugsturen** wanneer hij aan de beurt is.
- Toon Robbert alleen **Eindgoedkeuring geven** of **Terugsturen** nadat Marc dezelfde versie heeft
  goedgekeurd.
- Vereis een vrije reden bij terugsturen en toon die bij de volgende versie.
- Voeg persoonlijke approvalitems toe aan **Vragen aan jou** of een eenduidige actielijst.
- Toon historische approvals per versie zonder ze als nog geldig te presenteren.
- Houd bestaande epicdetailacties voor annuleren, intrekken en herprioriteren correct
  rolgescheiden.

### Acceptatiecriteria

- Marc en Robbert zien ieder een begrijpelijke taak op het juiste moment.
- Een oude browsertab kan door expected-versioncontrole geen achterhaalde epic goedkeuren.
- Na terugsturen is zichtbaar wie dit deed, waarom en welke nieuwe versie wordt verwacht.
- Frontend- en API-integratietests dekken de complete tweestapsroute en beide afwijspaden.

### Afhankelijkheden

`PF-PA-012` en `PF-PA-010`.

## PF-PA-014 — Rechtstreekse gewone bugfix via Software Factory v2

### Doel

Voer een bevestigde duidelijke bug uit zonder epic, maar mét de bestaande volledige Software
Factory-storyketen en v2-idempotentie.

### Werk

- Voeg een router toe die alleen een goedgekeurde actuele requestversie van type `BUGFIX` claimt.
- Bouw een volledig v2-storypakket uit requestinhoud, productconfiguratie en relevante artifacts.
- Gebruik request-ID als `sourceStoryId`, requestversie als `sourceStoryVersion` en type `BUGFIX`.
- Hergebruik de bestaande Software Factory-v2-client, foutclassificatie, outbox en
  delivery-attemptpatronen.
- Geef gewone bugfixes nooit `hotfix`-semantiek en roep de dashboard-story-API niet aan.
- Synchroniseer `OPEN`, `DONE` met verplichte commit-SHA en `CANCELLED` naar een read-only
  vervolgprojectie bij ProductRequest.
- Toon storykey en actuele externe status in request- en conversatiedetail.

### Acceptatiecriteria

- Een bevestigde bugfix maakt zonder epic exact één Software Factory-story.
- Een timeout, verloren response, crash of retry levert dezelfde externe storykey.
- De story is extern een gewone volledige bugfix en geen hotfix.
- Een v2-contractfout wordt zichtbaar geblokkeerd en past de requestinhoud niet automatisch aan.
- Product Factory kan na restart de externe status verder synchroniseren.

### Afhankelijkheden

`PF-PA-009` en de v2-uitkomst van `PF-PA-000`.

## PF-PA-015 — Persoonlijke status, actielijst en in-appnotificaties

### Doel

Laat Marc en Robbert zonder technische schermen zien welke gesprekken, requests, vragen,
goedkeuringen en leveringen aandacht vragen.

### Werk

- Bouw een productgebonden persoonlijke actielijst uit bestaande publieke projecties.
- Toon minimaal: advisor wacht, voorstel gereed, vraag open, productapproval nodig,
  factory-approval nodig, routing mislukt en externe uitvoering terminaal.
- Voeg in-appnotificaties toe voor nieuwe vraag, voorstel gereed, approvaloverdracht en
  storyuitkomst.
- Maak notificatie-idempotentie gebaseerd op domeingebeurtenis en ontvanger.
- Laat gelezen/ongelezen presentatie geen inhoudelijke domeinstatus wijzigen.
- Link ieder item rechtstreeks naar conversation, request, question, epic of externe storystatus.
- Houd technische attempts en veilige foutdetails beschikbaar voor factory owners onder Operatie.

### Acceptatiecriteria

- Marc ziet alleen acties voor zijn producten en Robbert ziet factory owner-acties.
- Polling of restart maakt geen dubbele notificaties.
- Een gelezen notificatie sluit geen vraag of approvaltaak.
- Alle links herstellen product en detail na reload.
- De eerste versie vereist geen e-mail, Telegram of pushdienst.

### Afhankelijkheden

`PF-PA-009`, `PF-PA-010`, `PF-PA-013` en `PF-PA-014`. De hotfixstatus uit `PF-PA-016` wordt daarna
aan dezelfde projectie toegevoegd.

## PF-PA-016 — Rechtstreekse hotfix via de bestaande dashboard-story-API

### Doel

Voer een door Marc bevestigde kleine correctie uit via de bestaande Software Factory-hotfixmodus,
zonder Software Factory te wijzigen en zonder epic of gewone agentketen.

### Werk

- Implementeer in `software-factory-dispatcher-impl` een afzonderlijke Product Factory-adapter voor
  de bestaande Software Factory `POST /api/v1/stories` en `stories.list`.
- Lees een geldig dashboard-bearertoken uit een nieuwe gesloten Product Factory-secretkey; geef het
  nooit door aan Runtime of `PRODUCT_ADVISOR`.
- Valideer vóór claim dat requesttype `HOTFIX` is, Marc de actuele versie heeft goedgekeurd en de
  advisorvalidatie geen uitgesloten categorie bevat.
- Bouw de externe payload deterministisch met `hotfix=true`, `start=true`, project/repository,
  titel, volledige omschrijving en bestaande notificatie-events.
- Voeg een stabiele, niet-geheime ProductRequest-marker aan de omschrijving toe.
- Zoek vóór create en iedere retry via `stories.list` naar die marker.
- Bewaar lokale delivery attempts, response, storykey, foutsoort en retrystatus.
- Maak ontbrekend, verlopen of geweigerd token zichtbaar; val nooit stil terug naar gewone bugfix.
- Toon tokenstatus en laatste succesvolle controle zonder tokenwaarde in Operatie.
- Documenteer het huidige dertigdaagse vernieuwingsproces in het Product Factory-runbook.
- Voeg geen diffcontrole of hotfixspecifieke controle na uitvoering toe.

### Acceptatiecriteria

- Een bevestigde snelle correctie maakt exact één story met de bestaande hotfixketen.
- Software Factory ontvangt exact de bevestigde requestinhoud plus alleen technische bronmetadata.
- Een verloren create-response gevolgd door retry vindt dezelfde story terug.
- Een ongeldig token resulteert in een herstelbare zichtbare Product Factory-fout en geen externe
  story.
- Marc hoeft geen Software Factory-account of -interface te gebruiken.
- Geen bestand in `softwarefactory` of `pvdd` is gewijzigd.
- De bestaande Software Factory-verificatie, merge en deploy bepalen de uitkomst zonder extra
  Product Factory-diffinspectie.

### Afhankelijkheden

`PF-PA-009` en een positief, operationeel geaccepteerd hotfixresultaat uit `PF-PA-000`.

## PF-PA-017 — Volledige keten, Testbed, documentatie en releasebewijs

### Doel

Sluit de losse slices af als één aantoonbaar werkende product owner-route en werk de actuele
Product Factory-documentatie pas daarna bij.

### Werk

- Voeg vaste Testbedscenario's toe voor informatievraag, vervolgvraag, hotfix, gewone bugfix,
  productverbetering, gerichte vraag, dubbele approval, afwijzing/revisie en autorisatiescheiding.
- Voeg ketentests toe voor restart tijdens advisorbeurt, designworkitem en externe dispatch.
- Voeg race-tests toe voor dubbele bevestiging, approval versus revisie en deliveryretry.
- Test minimaal twee producten en twee product owners om cross-productlekkage uit te sluiten.
- Werk `docs/overzicht.md`, processen, API's, frontend, configuratie, runbooks en diagrammen bij naar
  de werkelijk geïmplementeerde toestand.
- Registreer nieuwe actieve rol, jobkey en eventuele implementatievariant in
  `ImplementationManifest`.
- Werk voorbeeldconfiguratie en gesloten secretkeylijst bij zonder waarden.
- Voeg een bewijsrecord toe met mapping van ieder acceptatiecriterium naar test of operationele
  controle.
- Draai de volledige Mavenreactor, PostgreSQL-migraties, Modulithcontrole, Flutter analyze/tests,
  frontendproductiebouw en alle bestaande regressies.
- Release via de bestaande immutable artifact- en promotieroute en controleer acceptatie en
  productie op dezelfde digests.

### Verplichte ketenscenario's

1. Marc logt in, ziet alleen PvdD, stelt een informatievraag en sluit zonder wijziging.
2. Advisor onderzoekt een tikfout; Marc bevestigt; één bestaande hotfixstory wordt uitgevoerd.
3. Advisor classificeert een grotere bug; Marc bevestigt; één gewone v2-bugfix ontstaat zonder epic.
4. Marc bevestigt een productverbetering; gericht designworkitem maakt een complete epic.
5. Productontwerp stelt Marc een vraag; zijn antwoord hervat exact dezelfde uitwerking.
6. Marc keurt epicversie N goed; Robbert keurt dezelfde versie goed; planning kan haar claimen.
7. Robbert stuurt versie N terug; versie N+1 vereist opnieuw Marc en Robbert.
8. Marc probeert een ander product en globale instellingen te lezen; backend geeft `403`.
9. Een verloren externe response of applicatieherstart maakt geen dubbele request, epic of story.
10. Een verlopen hotfixtoken geeft een zichtbare herstelactie en nooit een stille gewone story.

### Acceptatiecriteria

- Alle zeventien voorgaande storycriteria zijn aantoonbaar groen op de geïntegreerde branch.
- De bestaande MVP-scenario's blijven groen.
- Alleen Product Factory bevat codewijzigingen voor deze feature.
- Marc kan de primaire route gebruiken zonder Operatie of Software Factory te openen.
- Robbert behoudt volledige beheersbaarheid en de verplichte tweede epicapproval.
- De actuele documentatie beschrijft alleen werkelijk opgeleverde functionaliteit.
- Acceptatie en productie draaien gezond op dezelfde bronrevisie en artifactdigests.

### Afhankelijkheden

`PF-PA-001` tot en met `PF-PA-016`.

## Verplichte verificatie per Software Factory-story

Iedere story uit dit plan is pas klaar wanneer minimaal het volgende voor haar geraakte scope groen
is:

1. de Mavenreactor compileert en alle relevante backendtests slagen;
2. Spring Modulith-verificatie toont geen nieuwe verboden moduleafhankelijkheid;
3. nieuwe migraties slagen vanaf leeg en boven op de laatst gereleasete database;
4. Flutter analyze, gerichte widgettests en waar relevant frontendproductiebouw slagen;
5. iedere externe grens heeft contracttests met een fake of stub;
6. iedere autorisatiemutatie heeft zowel positieve als negatieve HTTP-tests;
7. idempotentie, expected-versionconflict en restart/herstel zijn voor de story bewezen;
8. geen credentialwaarde verschijnt in Git, logs, fixtures, snapshots of foutmeldingen;
9. de geraakte actuele documentatie en het toekomst-/stappenplan spreken de code niet tegen;
10. `git diff --check` is schoon en er zijn geen wijzigingen buiten de afgesproken repository.

## Aanbevolen leveringsstrategie

- Geef Software Factory per keer precies één `PF-PA`-story met dit document en het gekoppelde
  voorstel als context.
- Laat iedere story vanaf de actuele hoofdbranch in een eigen worktree uitvoeren.
- Integreer in nummervolgorde, behalve de expliciet paralleliseerbare directe routes.
- Laat geen story alvast tabellen, statussen of UI voor een latere story half implementeren.
- Voer na iedere geïntegreerde story de volledige relevante regressiesuite uit.
- Activeer productiegedrag pas wanneer de betreffende UI, backendautorisatie, herstelroute en
  operationele zichtbaarheid in dezelfde slice gereed zijn.
- Houd de hotfixadapter achter een Product Factory-feature/configuratieguard totdat `PF-PA-016` en
  het productieve tokenrunbook zijn bewezen.

## Buiten scope van dit stappenplan

- Wijzigingen aan Software Factory of PvdD.
- Telegram-, e-mail- of pushnotificaties.
- Een eigen AI-agent in Software Factory.
- Een Product Factory-widget in PvdD.
- Meerdere parallelle agents per advisorbeurt.
- Automatische goedkeuring namens Marc of Robbert.
- Een diffgrootteguard of aanvullende hotfixcontrole na ontwikkeling.
- Een duurzame machinecredential voor de Software Factory-dashboard-API; die zou een toekomstige
  Software Factory-wijziging vereisen en valt onder de huidige randvoorwaarde buiten scope.

## Definitie van klaar

Stap 10 is klaar wanneer een product owner uitsluitend binnen toegewezen producten duurzame
gesprekken met `PRODUCT_ADVISOR` kan voeren; vragen zonder wijziging kan afronden; een exacte hotfix,
bugfix of epickandidaat kan bevestigen; vragen uit gerichte uitwerking kan beantwoorden; en een epic
samen met de factory owner versiegebonden kan goedkeuren. De drie uitvoerroutes zijn idempotent,
herstelbaar en zichtbaar, alle regressie- en ketenbewijzen zijn groen, en uitsluitend Product
Factory is voor deze functionaliteit gewijzigd.
