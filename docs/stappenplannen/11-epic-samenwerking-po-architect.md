# Stap 11 — Epicontwikkeling met PO en architect

Status: klaar als overdrachtsplan; implementatie niet gestart. Datum: 2026-09-13.

## Opdracht voor de uitvoerende AI-agent

Implementeer de [specificatie](../voorstellen/epic-samenwerking-po-architect.md) en
[UX](../ux/epic-samenwerking/README.md) in de bestaande Product Factory. Maak de volledige
menselijke PO-/architectflow en de autonome variant werkend. Een PO zoals Marc werkt zelfstandig
aan functionele epics, ziet schermontwerpen en volgt vragen en implementatiestatus. Een architect
zoals Robbert beoordeelt korte concrete technische impact en het AI-gebruik van het product,
inclusief budgetten en uitzonderingen. De factory owner beheert de factory en is geen extra
epicgoedkeurder.

Dit plan beschrijft toekomstig implementatiewerk; de aanwezigheid van documenten en een prototype
is geen bewijs dat een stap of capability al is gerealiseerd.

## Bronnen en startcontrole

Lees de specificatie en UX volledig, inclusief prototypebeperkingen en migratieregels.
Inspecteer vervolgens actuele code, contracten en geldende ADR's. Relevant zijn de actuele
Product Advisor-specificatie, de publieke ontwerp-/planningcontracten, het voortgangsmodel,
AI-uitvoering, autorisatie en het bestaande integratie-/acceptatietestbeleid.

- Inventariseer gitstatus en behoud bestaand werk.
- Gebruik Java 21, Maven en de Flutterversie van de repository.
- Noteer huidige rollen, rolwisselgedrag, epicinhouds- en statusversies, approvals, vraagroutering,
  planningclaims en dispatchcontroles. Controleer in het bijzonder de versienummers bij
  statusovergangen en de virtuele approvalstatussen in epicqueries.
- Leg data-eigendom en migratiegedrag vast vóór implementatie.
- Werk in `product-factory`; het PvdD-voorbeeld is synthetische testdata. Nieuwe externe
  integratieverplichtingen worden expliciet als ontbrekende contractmogelijkheden beschreven.

## Uitvoervolgorde

### PF-EA-01 — Productrollen en besturingsbeleid

Voeg `ARCHITECT` als productrol toe aan contracten, opslag, authenticatie, gebruikersbeheer en
effectieve rolkeuze. Ondersteun meerdere rollen per gebruiker/product. Voeg geversioneerd beleid
voor menselijke/automatische PO- en architectverantwoordelijkheid en uitzonderingen toe.

Oplevering: beheerscherm, backendautorisatie en migraties. Een factory owner is niet vanzelf
architect; een PO ziet geen operationeel beheer. Intrekken van een rol werkt ook voor bestaande
sessies en deep links. Automatische besturing heeft expliciet mandaat.

### PF-EA-02 — Epicimpact en product-AI-beoordeling

Breid het geversioneerde epicdossier uit met risicoprofiel, korte impactregels, product-AI-impact,
bewijs en onzekerheden. Gebruik bestaande repo-/besluitcontext. Breid AI-prompts, responseschema's
en deterministische validatie samen uit. Bewaar UX-inventaris en artifacts.

Oplevering: leesbare assessment-DTO's, opslag, gerichte AI-onderzoekstaken en de zes basiscategorieën
uit het ontwerp. ‘Onbekend’ blokkeert onterechte automatische vrijgave. Raming van product-AI
onderscheidt frequentie, aantal documenten, retries, eenmalige verwerking en structurele last.

### PF-EA-03 — Versiegebonden beoordelingen en uitvoeringspoorten

Vervang de vaste PO/factory-ownerketen door afgeleide beoordelingseisen. Ondersteun menselijk
akkoord, automatisch besluit binnen mandaat, wijzigingsverzoek en onderzoeksverzoek. Modelleer
inhoudsversie en statusovergangen zodanig dat oude approvals niet op nieuwe inhoud gelden.

Oplevering: centrale publieke commands/queries van de eigenaar, atomaire planningclaim, een
betrouwbare dispatchcontrole en audit van actor/rol/beleid/inhoud. Voeg architectnotificaties
toe. Alleen de frontendknoppen veranderen is onvoldoende.

### PF-EA-04 — Marcs werkplek en gezamenlijke uitwerking

Implementeer PO-landing, Mijn epics, Nieuw idee, gesprek naast dossier, schermen, feedback op een
onderdeel, versieverschillen en functioneel akkoord. Gebruik bestaande ProductConversation,
ProductRequest en DesignWorkItem in één doorlopende ervaring. Maak het overnemen van informatie
tussen losse schermen overbodig.

Oplevering: responsive Flutter-schermen en echte, duurzame revisies na AI-feedback. De browser-
prototypecode wordt niet als vervangende frontend ingevoerd. Functionele/UX-details zijn leidend;
gebruik bestaande Fluttercomponenten en Product Factory-theming.

### PF-EA-05 — Architectwerkplek en productafspraken

Implementeer Te beoordelen, Epicimpact, een toelichting/AI-gesprek per impactpunt en beslisacties.
Maak product-AI-beleid, grenzen en uitzonderingen door de architect bewerkbaar. Toon bij ieder
besluit precies welke inhoud/impact wordt beoordeeld en wat nog ontbreekt.

Oplevering: architect kan het voorbeeld ‘iedere tien minuten AI’ beoordelen zonder de volledige
functionele epic te lezen. Een wijzigingsverzoek levert een nieuwe epicversie en gerichte
terugkoppeling aan Marc op. Opnieuw goedkeuren is werkelijk nodig als de inhoud is veranderd.

### PF-EA-06 — Vragen tijdens planning/bouw en PO-voortgang

Koppel vragen aan rol, product, epic/story en procesfase. Toon ze op Mijn werk, Vragen en in
epiccontext met één gedeelde antwoordstatus. Laat antwoorden het juiste werk hervatten.
Implementeer leesbare stories en de reis tot verificatie/oplevering, inclusief bugs en hertests.

Oplevering: de PO kan een functioneel afgeronde epic blijven volgen. Nieuwe technische impact
gaat terug naar de architect. Pauzeer aantoonbaar afhankelijk werk; behoud identiteit van
geclaimde/verstuurde storypakketten. Gebruik alleen beschikbare Software Factory-signalen voor
vragen uit bouw; verzin geen nieuwe callback die daar niet bestaat.

### PF-EA-07 — Factorybeheer, autonome keten en migratie

Behoud runs, planning, kwaliteit en configuratie in de factory-ownerwerkplek. Verifieer expliciet
dat geen menselijke PO/request-/architectapproval overblijft in de autonome productflow binnen
mandaat. Test de omschakeling van bestaande producten en wachtende/lopende epics.

Oplevering: een samenhangend systeem met duidelijke rolgrenzen, een veilige migratie en een
volledig automatisch scenario. Geen echte productconfiguratie of roltoewijzing wordt door
voorbeelddata hardgecodeerd.

## Acceptatiematrix

Elke rij vraagt bewijs met relevante backend-/frontend-/integratie- of Testbedtests. Gebruik
synthetische gebruikers en gemockte AI; browsercontrole van het prototype vervangt die tests niet.

| ID | Scenario | Verwacht resultaat |
|---|---|---|
| EA-01 | Marc logt in | Alleen toegewezen producten; Mijn werk, Mijn epics, Vragen; geen technische beheeracties |
| EA-02 | Marc bespreekt een idee | Bestaand gesprek wordt duurzaam hervat; dossier groeit zonder handmatig overtypen |
| EA-03 | Feedback op mobiel UX-scherm | Nieuwe versie met herleidbare feedback; andere artifacts verdwijnen niet stilzwijgend |
| EA-04 | Epic past binnen productafspraken | Na functioneel akkoord automatisch planbaar, zonder menselijke architect- of factory-ownerstap |
| EA-05 | Additief compatibel databaseveld | Korte impactregel met bewijs; geen verplichte handmatige review als beleid dit toestaat |
| EA-06 | Tabelmigratie, eerste SQL-database, nieuwe koppeling/frontend/login | Elk geval toont concrete impact en vereist toepasselijke architectbeoordeling |
| EA-07 | AI iedere tien minuten, bestaande provider | Architectactie wegens product-AI; geen verwarring met Factory-agentgebruik |
| EA-08 | AI-volume/kosten onbekend | Onzekerheid zichtbaar; geen verzonnen raming of automatische vrijgave |
| EA-09 | Architect vraagt onderzoek | Onderzoekstaak/reden bewaard; review blijft open; opnieuw starten dupliceert niet |
| EA-10 | Architect wijzigt functionele werking | Nieuwe inhoudsversie; Marc ziet verschil en geeft opnieuw akkoord |
| EA-11 | Oude approval na inhoudsrevisie | Geldt niet voor nieuwe inhoud; oude beslissing blijft in historie |
| EA-12 | Alleen status verandert | Geldig inhoudelijk akkoord gaat niet verloren door een technisch versienummer |
| EA-13 | Goedkeuring tegelijk met revisie/planningclaim | Geen verouderde of ongeautoriseerde uitvoering; versieconflict is herstelbaar |
| EA-14 | Vraag tijdens planning | Persoonlijke actie en epic/story tonen dezelfde vraag; antwoord hervat betrokken werk één keer |
| EA-15 | Nieuwe technische impact tijdens planning/bouw | Architectactie; betrokken toekomstige dispatch geblokkeerd; lopend extern werk eerlijk zichtbaar |
| EA-16 | PO is klaar, bouw loopt | Epic blijft zichtbaar met stories en juiste implementatiestatus |
| EA-17 | Software Factory DONE, verificatie nog open | Geen voortijdige melding dat de epic is geslaagd of in productie beschikbaar is |
| EA-18 | Bug/hertest na levering | PO ziet begrijpelijke reden en voortgang; geen technische logdump |
| EA-19 | Verificatie geslaagd en doelomgeving beschikbaar | Oplevering toont wijziging, omgeving en bruikbare link |
| EA-20 | Robbert wisselt architect/factory owner | Alleen werkelijk toegekende rolbevoegdheden; besluit registreert rol en product |
| EA-21 | Directe API-call, deep link of ingetrokken rol | Backend weigert onbevoegde inzage/mutatie; geen vertrouwen op verborgen knoppen |
| EA-22 | Autonoom HKH, epic binnen mandaat | AI doorloopt uitwerking, beoordeling, planning en uitvoering zonder menselijk akkoord |
| EA-23 | Buiten mandaat / ontbrekende menselijke architect | Uitlegbare blokkade en juiste uitzonderingsroute; geen impliciete factory-ownerapproval |
| EA-24 | Migratie met oude factory-ownerapprovals | Historie behouden; geen automatische architectrol of onbedoelde dispatchvrijgave |
| EA-25 | Herstart, dubbele callback/antwoord of netwerkfout | Bestaande taken/vragen/approvals hervatten idempotent |
| EA-26 | 320px, toetsenbord, 200% zoom | Inhoud en primaire acties bereikbaar; juiste focus, labels en leesbare foutmeldingen |

## Verificatie en klaarcriterium

- Gebruik de bestaande `./product-factory verify` als complete lokale verificatie voor de
  uiteindelijke implementatie. Voer tijdens deelstappen gerichte relevante tests uit.
- Verifieer nieuwe Flywaymigraties vanaf leeg én vanaf de vorige release met bestaande data.
- Breid onder meer ProductAdvisor-, ProductDesign-, ProductPlanning-integratietests en de
  Flutter-tests van gesprekken, werkplek en autorisatie uit; kies de actuele testlocaties.
- Maak Testbedscenario's voor de menselijke keten, architectrevisie, product-AI-uitzondering,
  late impact en volledig autonome keten. Bewijs dat backendpoorten ook buiten de UI werken.
- Bekijk de echte gebouwde frontend op desktop en mobiel. Controleer ook lege, wachtende,
  mislukte, ingetrokken-toegang- en versieconflicttoestanden.
- Werk actuele specificaties, runbooks, rol-/API-documentatie, UX-index en bewijsrecord bij.
- Rapporteer gerealiseerde scenario's, migratiegedrag en eventuele integratiebeperkingen.
  Een geslaagde mocktest is geen bewijs van echte productieoplevering.

Committen, pushen, deployen of echte gebruikers/producten configureren volgt de autorisatie van
de daadwerkelijke implementatieopdracht. Dit overdrachtsdocument voert geen van die handelingen uit.

## Kopieerbare startopdracht

> Implementeer stap 11 in de Product Factory-repository. Lees eerst
> `docs/voorstellen/epic-samenwerking-po-architect.md`,
> `docs/stappenplannen/11-epic-samenwerking-po-architect.md` en
> `docs/ux/epic-samenwerking/README.md`. Open ook het bijbehorende `index.html`-prototype
> en bekijk de schermbeelden. Bouw voort op de bestaande code en werk de zeven deelstappen
> uit tot een volledige, geteste flow. De architect beheert architectuur, product-AI,
> budgetten en uitzonderingen; de factory owner is geen epicgoedkeurder. Zorg zowel voor
> de menselijke PO-/architectflow als de volledig autonome variant. Gebruik de
> acceptatiematrix om te bewijzen wat werkt en houd de actuele documentatie in lijn met
> de implementatie. De prototypegegevens en AI-antwoorden zijn uitsluitend voorbeelden.
