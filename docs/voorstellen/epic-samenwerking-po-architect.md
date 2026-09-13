# Epicontwikkeling met een product owner en architect

Datum: 2026-09-13. Status: overdrachtsontwerp voor implementatie; nog niet gebouwd.

Dit document legt de met de factory owner besproken richting en het positief ontvangen
UX-ontwerp vast. Het bevat ook concrete voorgestelde implementatiekeuzes; waar keuzes niet
expliciet zijn besproken, zijn die hieronder als ontwerpkeuze gemarkeerd.

## Start hier als implementerende AI-agent

1. Lees dit document volledig.
2. Bekijk de [UX-specificatie en schermbeelden](../ux/epic-samenwerking/README.md) en open het
   [klikbare prototype](../ux/epic-samenwerking/index.html) in een browser.
3. Volg [stap 11: implementatie en verificatie](../stappenplannen/11-epic-samenwerking-po-architect.md).
4. Inspecteer de actuele code. Bouw voort op Product Advisor, epicversies, UX-artifacts,
   persoonlijke vragen, planning, voortgang en bestaande autorisatie.

Voor deze uitbreiding vervangt dit ontwerp de oude verplichte keten **PO → factory owner**
uit [Product Advisor en Product Requests](product-advisor-en-productrequests.md). De factory owner
is geen inhoudelijke epicbeoordelaar. Bestaande specificaties blijven leidend voor alle overige
modulegrenzen, uitvoering, beveiliging en integraties. Werk de actuele specificaties bij zodra
de implementatie werkelijk verandert; presenteer dit voorstel niet als bestaande functionaliteit.

## 1. Doel

Marc moet als product owner van PvdD zelfstandig met Product Factory functioneel kunnen bedenken
wat er moet gebeuren, dat samen met AI uitwerken tot een complete epic en de uitvoering volgen.
Robbert hoeft daar niet tussen te zitten. Robbert wordt als architect betrokken bij relevante
technische impact, product-AI, budgetten en uitzonderingen.

Dezelfde ontwerp- en uitvoerflow moet volledig automatisch door AI kunnen verlopen voor het
autonome HKH-project. In de repository heet dat project `hkh-autopilot`; de gebruiker noemde het
ook `hkh-autofactory`. Dit is geen opdracht om het project te hernoemen. HKH en PvdD krijgen
menselijke productrollen. Namen en product-ID's in deze documenten zijn voorbeelden, geen seeds.

## 2. Expliciet afgesproken verantwoordelijkheden

| Rol | Verantwoordelijkheid | Dagelijkse werkplek |
|---|---|---|
| Product owner, per product | Doel, functionaliteit, scope, prioriteit, UX en functioneel akkoord; vragen tijdens ontwerp, planning en bouw beantwoorden | Mijn werk, Mijn epics, Vragen |
| Architect, per product | Architectuur, AI-gebruik van het gewijzigde product, productbudgetten, productbeleid en uitzonderingen | Te beoordelen, Epicimpact, Productafspraken |
| Factory owner, globaal | Gebruikers en roltoewijzing, factory-inrichting, globale AI-uitvoering van de factory, runs, planning, kwaliteit en operatie | Factoryoverzicht, Runs, Planning, Kwaliteit, Instellingen |

- De factory owner krijgt geen impliciet PO- of architectakkoord en geen verplichte
  eindgoedkeuring. Productbesluiten gaan naar de bevoegde productrol.
- Eén persoon kan meerdere rollen hebben. Robbert kan zowel architect als factory owner zijn.
  Een besluit registreert in welke rol en voor welk product hij handelde.
- Een productlidmaatschap kan zowel `PRODUCT_OWNER` als `ARCHITECT` bevatten. Leid de rol niet
  alleen af uit het bestaan van een lidmaatschap.
- Marc ziet uitsluitend toegewezen producten. Een architect krijgt uitsluitend de benodigde
  toegang tot toegewezen producten; globale beheerbevoegdheden ontstaan niet uit deze rol.
- De factory owner behoudt breed operationeel inzicht. Productinhoud bekijken is iets anders
  dan haar namens de PO of architect goedkeuren.
- Het AI-gebruik in dit ontwerp betreft **AI binnen PvdD/HKH**, niet de tokens die Product
  Factory verbruikt om epics te ontwerpen of software te laten bouwen.

## 3. Productbeleid: menselijk of automatisch

Voorgestelde implementatiekeuze: configureer afzonderlijk wie de PO-verantwoordelijkheid en
architectverantwoordelijkheid invult: `HUMAN` of `AI`. De UI kan presets aanbieden voor
‘Menselijke PO en architect, met AI’ en ‘Automatische PO en architect’.

- Bij menselijke besturing maakt AI voorstellen en adviezen; het toepasselijke menselijke
  akkoord wordt niet door AI nagebootst.
- Bij automatische besturing mogen AI-rollen inhoudelijke keuzes en beoordelingen maken
  binnen het vastgelegde mandaat. Applicatiecode valideert beleid en publiceert het besluit.
- De autonome flow mag nergens onvoorwaardelijk wachten op een menselijk requestakkoord of
  een menselijke epicapproval. Ook een via ProductRequest ontstane epic volgt het productbeleid.
- Leg productbeleid, architectuurafspraken en mandaat geversioneerd vast. Het beleid bepaalt
  welke wijzigingen automatisch afhandelbaar zijn en welke een uitzondering zijn.
- Buiten het mandaat volgt de ingestelde uitzonderingsroute. Ontbreekt een bevoegde beslisser
  of passend mandaat, dan blijft het betrokken werk zichtbaar geblokkeerd. Escaleer niet
  stilzwijgend naar de factory owner en ken geen automatische onbeperkte bevoegdheid toe.

## 4. Eén werkplek van idee tot oplevering

### 4.1 Binnenkomst en idee

Na bestaande authenticatie landt Marc op **Mijn werk**. Daar staan zijn open vragen,
lopende epics en recente opleveringen. **Nieuw idee** vraagt gewone taal, geen technisch formulier.
Een informatief gesprek hoeft geen epic te worden.

Product Advisor leest relevante productcontext, broncode en besluiten, stelt gerichte vragen
en helpt scope en succescriteria bepalen. Gesprek, verzoek en latere epic blijven verbonden.
Een requestbevestiging om uitwerking te starten is geen definitief functioneel epicakkoord.
Hergebruik de bestaande request/workitemroute zonder een extra los ticketsysteem te introduceren.

### 4.2 Uitwerking en feedback

Naast het gesprek groeit een leesbaar epicdossier. Marc kan reageren op de functionele uitwerking,
een specifiek UX-scherm, risico of open vraag. AI maakt een nieuwe versie en toont wat veranderde.
De implementatie moet werkelijk revisies en artifacts bijwerken; de vaste voorbeeldantwoorden
in het prototype zijn geen productiegedrag.

AI stelt Marc functionele vragen en de architect technische vragen. Laat Marc niet bepalen
of er een tabelmigratie nodig is. Toon van achtergrondwerk een begrijpelijke voortgang en een
herstelactie bij fouten; hervat na refresh of nieuwe login hetzelfde gesprek en dezelfde taken.

### 4.3 Functioneel akkoord en beoordeling

Marc beoordeelt werking, scope, acceptatiecriteria en UX en geeft **Functioneel akkoord** op een
exacte epicversie. Als de impact binnen de productafspraken valt, is geen handmatig architectakkoord
nodig. Toon wel dat de beoordeling volgens die afspraken is afgehandeld.

Bij relevante architectuur- of product-AI-impact krijgt de architect een persoonlijke actie.
Het systeem legt uit waarom deze nodig is. Na alle toepasselijke beoordelingen kan planning
automatisch beginnen. Er is geen factory-ownerstap.

### 4.4 Planning, bouw en vragen

Een epic die Marc functioneel heeft afgerond blijft zichtbaar met haar **implementatiestatus**.
‘Functioneel akkoord’ betekent niet ‘opgeleverd’.

De UI toont de reis **Uitwerken → Goedkeuren → Planning → Bouwen & testen → Opgeleverd**,
de actuele stap, wie aan zet is en waarom. Dezelfde vragen staan op Mijn werk, Vragen en de
betrokken epic/story; het zijn projecties van één duurzaam vraagobject.

Vragen kunnen ontstaan tijdens ontwerp, planning, bouw of verificatie. Een vraag heeft minimaal
product, epic, eventuele story, bronfase, vraagtekst, context, gevraagde rol, antwoordstatus en
gevolg voor de voortgang. Een antwoord wordt versiegebonden en idempotent opgeslagen en hervat
het betrokken werk. Bewaar geschiedenis, voorkom dubbele vragen/notificaties en wijs opnieuw
toe als een lidmaatschap vervalt. De factory owner kan de roltoewijzing herstellen zonder de
inhoudelijke beslissing over te nemen.

Stories zijn voor Marc leesbaar: titel, bedoelde verbetering, acceptatiecriteria, status en open
vragen. Technische logs, prompts, tokens, dispatchknoppen en SQL-details staan niet in zijn
dagelijkse schermen. Een vraag blokkeert alleen aantoonbaar afhankelijk werk. Als de huidige
planner nog slechts een complete epic kan blokkeren, moet de implementatie dat expliciet
ondersteunen of de bredere blokkade eerlijk tonen; claim geen gedeeltelijke voortgang die
de backend niet kan leveren.

### 4.5 Oplevering

Toon wat er voor Marc veranderd is en een link naar de beschikbare productomgeving.
Onderscheid levering van code, beschikbaarheid op acceptatie, geslaagde verificatie en
beschikbaarheid in productie. Alleen ‘Software Factory DONE’ is geen bewijs van een werkende
of productief beschikbare epic. Gebruik bestaande revision- en verificatiegegevens.
Open bugs, hertests en mislukte controles blijven begrijpelijk zichtbaar. Marc kan vanuit een
oplevering een vervolgidee of probleem melden met de eerdere epic als context.

## 5. Epicdossier

| Onderdeel | Minimale inhoud |
|---|---|
| Functioneel | Probleem, doelgroep, gewenste werking, scope, grenzen en testbare acceptatiecriteria |
| UX | Gebruikersflow; relevante hoofd-, detail-, lege en fouttoestanden; desktop/mobiel waar van toepassing |
| Risicoprofiel | Per risico: kans, impact, maatregel, resterende onzekerheid en verantwoordelijke rol |
| Architectuurimpact | Korte impactregels per categorie met onderbouwing en benodigde besluiten |
| Product-AI-impact | Verandering in doel, triggers, frequentie, volume, model/provider, gegevens, betrouwbaarheid en kosten |
| Gereedheid en beoordelingen | Open vragen, ontbrekend bewijs, benodigde akkoorden en exacte versie |

Proportioneel uitwerken: een eenvoudige toevoeging vraagt geen groot rapport. ‘Geen wijziging’
of ‘niet van toepassing’ heeft een korte reden. ‘Onbekend’ blijft expliciet en mag niet
automatisch als laag risico of geen impact worden behandeld.

Behoud de bestaande UX-inventaris en artifactlevenscyclus. Iedere revisie verantwoordt behouden,
vervangen en verwijderde schermen. Feedback verwijst naar epicversie, schermsleutel, viewport
en artifact, zodat een opmerking niet ongemerkt op een ander scherm terechtkomt.

## 6. Architect: impact eerst, functionele details op verzoek

De architect moet binnen één scherm begrijpen wat technisch verandert, zonder het volledige
functionele dossier te lezen. Iedere regel heeft categorie, korte uitspraak en beoordelingsstatus.
Uitklappen opent bewijs, alternatieven, onzekerheden en een gesprek met AI over dat onderdeel.

Voorbeelden van vereiste formuleringen en categorieën:

| Categorie | Voorbeelden |
|---|---|
| Database | ‘Eén optioneel veld erbij; backwards compatible’ of ‘Dit product krijgt voor het eerst een SQL-database’ |
| Migratie | ‘Tabel moet worden gemigreerd; bestaande gegevens worden omgezet’ of ‘Geen backfill nodig’ |
| Externe systemen | ‘Nieuwe koppeling met systeem X’ of ‘Bestaande Agent Runtime; geen nieuwe koppeling’ |
| Frontend | ‘Nieuwe frontendapplicatie’ of ‘Eén scherm in de bestaande frontend’ |
| Toegang | ‘Een deel van de applicatie komt achter een login’ of ‘Bestaande login en rechten blijven gelden’ |
| Product-AI | ‘Iedere tien minuten een AI-analyse’ of ‘Alleen nieuwe documentversies worden geanalyseerd’ |

Dit is een uitbreidbare categoriecatalogus. Infrastructuur, opslag, beschikbaarheid en gedeelde
componenten moeten ook zichtbaar worden als de epic ze raakt. Gebruik geen algemene score als
vervanging voor concrete impact.

Een menselijke architectbeoordeling is vereist bij een afwijking van het productbeleid,
een nieuwe architectuurgrens, migratie met relevante gevolgen, nieuwe afhankelijkheid,
nieuw afgeschermd deel, nieuwe AI-toepassing of materiële groei in AI-gebruik. Een additief
databaseveld dat aantoonbaar binnen bestaande afspraken past kan automatisch worden afgehandeld.
De architect bepaalt dit beleid; AI stelt de feitelijke impact vast en onderbouwt die.

Acties: **Onderzoek met AI**, **Akkoord op deze versie**, **Vraag aanpassing**, **Vraag onderzoek**.
Leg de reden vast. Onderzoek of een wijzigingsverzoek is geen akkoord. Een akkoord onder
voorwaarden geldt alleen als de voorwaarden concreet in de beoordeelde versie en uitvoering
zijn verwerkt; anders blijft werk geblokkeerd totdat dat is gebeurd.

## 7. AI-gebruik van het product

Het voorbeeld ‘elke tien minuten AI aanroepen’ vraagt architectbeoordeling, ook wanneer
de bestaande architectuur en provider ongewijzigd blijven.

Leg minimaal vast:

- Wat triggert AI nu en straks; onderscheid broncontrole, geplande start en echte AI-job.
- Frequentie, documenten/items per start, taken per item, piekbelasting en retrybeleid.
- Eenmalige migratie/backfill/heranalyse versus structureel dagelijks gebruik.
- Geschatte tokens en kosten als bandbreedte, met bron, aannames, valuta, meetdatum en onzekerheid.
- Provider/model, datastromen, caching, deduplicatie en maximaal toegestaan verbruik.
- Foutgedrag, resultaatvalidatie, bronherleidbaarheid en benodigde menselijke controle.

Tienminutenintervallen betekenen 144 mogelijke starts per dag, niet vanzelf 144 AI-jobs.
Bij D documenten en één job per document is een onvoorwaardelijke bovengrens 144 × D jobs
per dag, exclusief retries. Zonder volumes en prijzen wordt geen exact kostenbedrag verzonnen.
Een mogelijke oplossing is iedere tien minuten op wijzigingen controleren en alleen gewijzigde
documentversies analyseren. De architect besluit of dit verantwoord is.

Productafspraken bevatten instelbare absolute en relatieve grenzen en de uitzonderingsroute.
Een ontbrekende grens betekent geen automatisch onbeperkt mandaat. De PO kan geen extra budget
of providergebruik autoriseren. De architect kan dat wel, met versie en reden.

Deze uitbreiding moet vooraf de impact en afspraken zichtbaar en afdwingbaar maken in de
ontwikkelflow. Werkelijke runtimebegrenzing in PvdD vereist productondersteuning: een opgeslagen
Factory-instelling begrenst geen AI-jobs in PvdD vanzelf. Leg benodigde productaanpassingen in
de epic/stories vast. Gebruik beschikbare meetgegevens als bewijs; presenteer ontbrekende
producttelemetrie niet als nul gebruik. Een nieuw centraal meetplatform valt buiten deze eerste
implementatie; dit is geen vervanging door bewaking van Factory-agenttokens.

## 8. Versies, akkoorden en nieuwe impact tijdens uitvoering

Voorgestelde implementatiekeuze: bewaar gereedheid, impact en vereiste beoordelingen afzonderlijk
van de levenscyclusstatus. Vermijd één statusenum voor iedere combinatie van open akkoorden.

- Een beoordeling verwijst naar product, epicinhoudsversie, relevante impact-/beleidsversie,
  rol, menselijke gebruiker of vertrouwde AI-actor, beslissing, reden en tijdstip.
- Statusovergangen zonder inhoudswijziging mogen goedkeuring niet onbedoeld ongeldig maken.
  De huidige code maakt ook statusversies: ontwerp expliciet hoe inhoudsversie en procesversie
  van elkaar worden onderscheiden of gekoppeld.
- Een inhoudelijke revisie houdt oude besluiten als historie. Neem geen akkoord stilzwijgend
  over. Conservatieve eerste versie: iedere nieuwe inhoudsversie vraagt opnieuw toepasselijke
  akkoorden; optimalisatie naar onderdelen kan later met expliciet bewijs.
- Bij functionele gevolgen van een architectwijziging ziet Marc een korte voor/na-samenvatting
  en geeft hij opnieuw functioneel akkoord. De architect beoordeelt de gewijzigde impact.
- De autoritatieve backend bewaakt gereedheid en actuele akkoorden bij planningclaim en
  dispatch, ook bij concurrente revisie, intrekken van bevoegdheden of beleidswijzigingen.
  UI-labels of AI-uitvoer alleen mogen geen uitvoering vrijgeven.
- Nieuwe impact in planning/bouw wordt als wijzigingsverzoek aan de bron-epic gekoppeld.
  Houd de al geclaimde versie onveranderlijk en blokkeer betrokken toekomstige dispatches.
  Hervat pas tegen de juiste geactualiseerde besluiten en storyversies.
- Een reeds verstuurde story is niet ongedaan gemaakt door een lokale statuswijziging.
  Leg vast welk werk al loopt en gebruik bestaande annulering/herstelmogelijkheden.
  Beloof geen nieuwe Software Factory-callback als het bestaande contract die niet biedt.
- Een inhoudelijk kleine bugfix mag de beoordelingsregels niet omzeilen wanneer toch nieuwe
  architectuur- of product-AI-impact wordt ontdekt. Behoud bestaande hotfixintegratie; stuur
  werk buiten de bestaande lichte route naar de passende epicbeoordeling.

## 9. Technische aansluiting

Relevante bestaande bestanden, opnieuw te controleren vóór implementatie:

| Onderdeel | Bestaande ingang |
|---|---|
| Rollen en gesprekken | `product-factory-api/.../api/advisor/ProductAdvisorContract.kt` |
| Authenticatie en lidmaatschappen | `product-factory-app/.../auth/ProductAuthorizationService.kt`, `UserIdentityRepository.kt`, `UserManagementController.kt` |
| Request- en epicapprovals | `product-factory-app/.../advisor/ProductAdvisorApplicationService.kt`, `ProductAdvisorHttpApi.kt` |
| Productbeleid | `product-factory-api/.../api/product/ProductContract.kt`, `product-impl/.../ProductApplicationService.kt` |
| Epiccontract | `product-factory-api/.../api/design/DesignContract.kt` |
| Ontwerp, validatie en versies | `product-design-impl-mvp/.../ProductDesignMvpService.kt` |
| Planning/dispatch | `product-planning-impl-mvp`, `software-factory-dispatcher-impl` |
| Voortgang en vragen | `product-factory-app/.../progress/EpicProgressService.kt`, bestaande advisor-/productservices |
| Frontend | `product-factory-frontend/lib/product_conversations.dart`, `product_workspace.dart`, `application_shell.dart`, `user_management.dart` |

Voorgestelde concepten: `EpicImpactAssessment`, `ProductAiImpact`, `ProductGovernancePolicy` en
`EpicReviewRequirement/Decision`. Dit zijn ontwerpnamen, geen verplichte nieuwe modules.
Definieer eigendom en publieke DTO's vóór persistence. Respecteer
[ADR 0009](../adr/0009-module-eigendom-over-persistence-en-flywaymigraties.md); kopieer geen
cross-module SQL-patroon alleen omdat het in de huidige advisorcode voorkomt.

De vertrouwde AI-rolcatalogus, prompts, schema's, geheugen en grants moeten expliciet aansluiten
op nieuwe beoordelingstaken. De advisor blijft het aanspreekpunt voor Marc; gespecialiseerde
UX- en technische taken kunnen gericht worden gebruikt. Er is geen eis dat ieder idee altijd
een vaste groep extra agents doorloopt.

## 10. Migratie en grenzen

- Voeg voorwaartse migraties toe; wijzig geen al uitgevoerde Flywaybestanden.
- Migraties kennen niet automatisch een architectlidmaatschap toe aan iedere factory owner.
- Bewaar historische factory-ownerapprovals als historie, niet als nieuwe architectbesluiten.
- Zorg vóór omschakeling per product voor expliciete rollen/mandaten. Maak bestaande wachtende
  epics niet plotseling uitvoerbaar doordat een oude approvalvoorwaarde vervalt.
- Behoud bestaande geclaimde epicversies en dispatch-idempotentie. Leg migratiegedrag voor
  lopende en nog niet begonnen epics vast en test het met een bestaande dataset.
- Deze implementatieopdracht betreft Product Factory. Verander geen code of deployment van
  PvdD, HKH, Software Factory of Agent Runtime om het voorbeeld werkend te laten lijken.
- Echte accounts, uitnodigingen, productaanmaak, roltoewijzingen en activeren van schedules
  zijn aparte operationele handelingen; documentatie schrijven voert die niet uit.

## 11. Wat het prototype wel en niet bepaalt

Het prototype bepaalt de besproken informatiehiërarchie, rolervaring, korte impactweergave en
doorklikflows. Het is geen bron van backendlogica, bewezen PvdD-impact, actuele deploymentstatus
of hardgecodeerde voorbeeldantwoorden. De knop ‘Bekijk als’ en de momentenkeuze zijn hulpmiddelen
voor de ontwerpverkenning; bouw geen vrije rol- of fasewisseling in de productie-UI.

In echte software wisselt Robbert alleen tussen rollen die hij bezit. Procesmomenten komen uit
de backend. Login sluit aan op bestaande authenticatie. Laad-, lege-, fout-, toegang-geweigerd-
en versieconflicttoestanden moeten ook worden gebouwd; zie de UX-specificatie.

De [acceptatiematrix in het stappenplan](../stappenplannen/11-epic-samenwerking-po-architect.md#acceptatiematrix)
is de controlelijst voor een volledige oplevering.
