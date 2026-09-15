# Plan: actuele agentcontext en historisch productgeheugen

## Aanleiding

Product Factory bewaart terecht veel informatie: onderzoeksresultaten, productbesluiten,
overlegnotulen, roadmapthema's, afgehandelde onderzoeksvragen, storydossiers en evaluaties. De
huidige agentcontext maakt echter nog onvoldoende onderscheid tussen:

- informatie die **nu bindend of geldig** is;
- een hypothese of onderzoeksbevinding die nooit beleid is geworden;
- een besluit dat slechts voor een beperkte bronsoort of periode gold;
- en historische informatie die inmiddels vervangen of ingetrokken is.

Daardoor kan een model oude en nieuwe uitspraken proberen te combineren alsof ze beide geldig
zijn. Het concrete HKH-voorbeeld is de combinatie van de FamilySearch 110/95-jaarheuristiek met de
100/75/50-openbaarheidstermijnen voor Nederlandse burgerlijke-standakten. De eerste staat in
actieve productdocumentatie; de tweede is via een onderzoek en roadmapsessie als afgehandeld feit
en als primaire roadmaptoets vastgelegd. Zonder geldigheids- en scope-informatie kan een volgende
agent daar ten onrechte één algemeen privacybeleid van maken.

Dit plan wist de geschiedenis niet. Het introduceert een **actuele projectie** waaruit normale
agenttaken hun context krijgen, terwijl het volledige historische spoor beschikbaar blijft voor
audit, reconstructie en expliciet historisch onderzoek.

## Doel

Een normale Product Factory-agent ontvangt uitsluitend informatie die voor zijn taak actueel,
toepasselijk en voldoende gezaghebbend is. Achterhaalde informatie blijft bewaard, maar komt alleen
in beeld wanneer een taak expliciet om historie, herkomst of besluitvorming door de tijd heen
vraagt.

Het kernprincipe is:

> Opslag mag append-only zijn; de standaardcontext voor een agent is een gefilterde actuele
> projectie.

## Niet-doelen

- Historische onderzoeken, notulen of besluiten stilzwijgend verwijderen.
- De productvisie gebruiken als opslagplaats voor gedetailleerd uitvoeringsbeleid.
- Een taalmodel zelf laten bepalen welk beleidsstuk bindend is.
- Met alleen een promptregel proberen conflicterende of achterhaalde context op te lossen.
- Bestaande producthistorie met terugwerkende kracht herschrijven alsof eerdere keuzes nooit zijn
  gemaakt.

## Begrippen en gezagshiërarchie

### Soorten kennis

Iedere contextbron krijgt minimaal één type:

- `OWNER_DIRECTION`: expliciete richting of correctie van de producteigenaar;
- `VISION`: goedgekeurde, relatief stabiele productvisie;
- `POLICY`: actueel productbeleid of een productregel;
- `DECISION`: een genomen productbesluit;
- `TECHNICAL_CONSTRAINT`: aantoonbare actuele technische beperking;
- `RESEARCH_FINDING`: een brononderbouwde bevinding, nog geen beleid;
- `HEURISTIC`: een praktische vuistregel met beperkte bewijskracht;
- `ROADMAP_DIRECTION`: een langlopende prioriteit, geen productfeit;
- `OPEN_QUESTION`: nog onbeantwoorde vraag;
- `HISTORICAL_RECORD`: alleen voor reconstructie of audit.

### Geldigheidsstatus

Ieder beheerd kennisitem krijgt een status:

- `DRAFT`: nog niet als actuele context gebruiken;
- `ACTIVE`: opnemen wanneer scope en taak overeenkomen;
- `SUPERSEDED`: vervangen door een nieuwer item;
- `RETRACTED`: ingetrokken omdat het onjuist of niet langer wenselijk is;
- `EXPIRED`: niet meer geldig na een bekende datum of conditie;
- `HISTORICAL`: bewust alleen als geschiedenis bewaard.

`SUPERSEDED`, `RETRACTED`, `EXPIRED` en `HISTORICAL` worden nooit standaard aan cyclusrollen
meegegeven.

### Voorgestelde gezagshiërarchie

Bij tegenstrijdigheid geldt, binnen dezelfde scope:

1. een expliciete, nog geldige correctie of richting van de eigenaar;
2. de goedgekeurde productvisie;
3. actief, expliciet productbeleid en de actuele functionele specificatie;
4. actieve productbesluiten;
5. de actuele roadmaprichting;
6. onderzoeksbevindingen;
7. voorstellen of interpretaties van agentrollen;
8. historische informatie.

Deze hiërarchie betekent niet dat de visie ieder detail bepaalt. Een specifiek actief beleidsitem
kan binnen zijn eigen scope concreter zijn dan de visie, zolang het er niet mee strijdt.

## Gewenst datamodel

Introduceer één centraal, versieerbaar kennisregister, bijvoorbeeld `product_knowledge_item`, met
minimaal:

- `id` en `product_slug`;
- `kind`;
- `title` en `content`;
- `status`;
- `scope_type` en `scope_value`;
- `authority` en `source_reference`;
- `effective_from` en optioneel `effective_until`;
- `supersedes_id` of `superseded_by_id`;
- `correction_reason`;
- `created_at`, `activated_at` en `retired_at`;
- een stabiele `topic_key` waarmee meerdere versies over hetzelfde onderwerp worden verbonden.

Voorbeelden van scope zijn `ALL_SOURCES`, `DUTCH_CIVIL_REGISTRY`, `GENEALOGICAL_RECORDS`,
`PUBLIC_METADATA`, `OBJECT_MEDIA` en `ADMIN_UI`. Scopewaarden zijn geen vrije prozatekst maar een
gecontroleerd type, eventueel aangevuld met leesbare toelichting.

Een nieuwe versie overschrijft de oude rij niet. Activering van de nieuwe versie zet de vorige
actieve versie binnen dezelfde `topic_key` atomair op `SUPERSEDED` en legt de relatie vast.

## Actuele contextprojectie

Voeg in de `knowledge`-module een `ProductContextResolver` toe. Deze component bouwt voor iedere
agenttaak een begrensd `CurrentProductContext` op basis van:

- product;
- rol en taaktype;
- onderwerp/focus;
- toepasselijke scope;
- status `ACTIVE`;
- ingangs- en einddatum;
- gezag en maximale contextomvang.

Het resultaat is geen dump van alle tabellen of bestanden, maar een geordend dossier met vaste
secties:

1. visie en eigenaarsturing;
2. toepasselijk actief beleid;
3. toepasselijke actieve productbesluiten;
4. actuele technische beperkingen;
5. relevante roadmaprichting;
6. recente, relevante onderzoeksbevindingen;
7. open vragen en expliciete onzekerheden.

Ieder opgenomen item bevat een ID, soort, scope en bronverwijzing. Daardoor kan een agent zijn
redenering herleidbaar maken en kan de runtime controleren of een vermeende harde regel werkelijk
uit een bindende bron komt.

## Omgang met repositorydocumentatie

### Actieve branch is de actuele projectie

Actieve functionele en technische documentatie op de hoofdbranch hoort alleen het huidige gedrag
en huidige beleid te beschrijven. Een achterhaalde passage mag daar worden verwijderd of vervangen:
Git bewaart de volledige oude versie en commitgeschiedenis. Dat is geen geschiedvervalsing, maar
normaal versiebeheer.

Historische storydossiers en worklogs kunnen blijven bestaan, maar krijgen een machineleesbare
status of worden buiten de standaardcontext geplaatst. Een documentmanifest, bijvoorbeeld
`products/<slug>/context-manifest.yaml`, benoemt welke documenten normatief en actueel zijn. Alleen
die documenten worden in de standaard agentcontext opgenomen.

### Afgeschermde taakworkspace

Niet-onderzoekende rollen krijgen bij voorkeur geen volledige workspacecheckout meer, maar een
gegenereerde taakdirectory met:

- het actuele contextdossier;
- alleen de voor die taak toegestane actieve documenten;
- de relevante actuele story- of codecontext;
- geen historische onderzoeksdossiers of vervangen policies.

Voor de `RESEARCHER`, die wel actuele code en productdocumentatie moet kunnen inspecteren, komt een
expliciete actieve-documentenlijst. Historische mappen worden niet als normale kennisbron
aangeboden. Als onderzoek naar besluitgeschiedenis nodig is, wordt daarvoor een afzonderlijke
taakmodus gebruikt.

Alleen promptinstructies zijn hierbij onvoldoende: de bestandenset en opgehaalde databasecontext
moeten technisch worden begrensd.

## Historie raadplegen

Historie blijft bereikbaar via een apart read-only pad, bijvoorbeeld:

- `historicalContext(productSlug, topicKey)`;
- een dashboardactie “Toon besluitgeschiedenis”;
- of een expliciete agenttaak `historical-product-research`.

De historische weergave toont de volledige keten:

```text
item A — ACTIVE van datum 1 tot datum 2
item B — SUPERSEDED item A op datum 2, met reden
item C — RETRACTED op datum 3, met correctiebron
item D — huidig ACTIVE beleid
```

Historische resultaten worden visueel en in agentprompts duidelijk gemarkeerd als niet-bindend.

## Besluiten activeren en corrigeren

### Promotie is expliciet

Een onderzoeksbevinding wordt nooit automatisch beleid. Een roadmapsessie mag:

- een bevinding als onderzoek vastleggen;
- een open beleidsvraag toevoegen;
- een thema prioriteren;
- maar geen privacy-, juridisch of ander risicovol beleid activeren zonder expliciete
  eigenaarbevestiging of vooraf geconfigureerde autoriteit.

### Correctiepad

Het dashboard krijgt acties voor:

- “Vervang door nieuwe versie”;
- “Trek in”;
- “Beperk toepassingsgebied”;
- “Markeer als historisch”.

Iedere actie vereist een korte reden en toont vooraf welke agentcontext daardoor verandert. Een
correctie wordt zelf een duurzaam, auditbaar item.

## Gefaseerde uitvoering

### Fase 0 — inventarisatie en observatie

- Breng alle huidige contextbronnen en injectiepunten in kaart.
- Log tijdelijk per agentrun welke contextitems en bestanden zijn aangeboden.
- Identificeer conflicterende actieve uitspraken en onbegrensde historische bronnen.
- Wijzig nog geen selectiegedrag.

**Klaar wanneer:** voor een willekeurige cyclus achteraf exact zichtbaar is uit welke databaseitems
en documenten iedere rol context ontving.

### Fase 1 — status en correcties voor bestaande geheugenbronnen

- Voeg status, scope en vervangingsrelaties toe aan afgehandelde onderzoeksvragen en besluiten.
- Voeg update/intrek-API's toe; de huidige alleen-toevoegen-API is onvoldoende.
- Migreer bestaande items conservatief: twijfelgevallen worden niet automatisch `ACTIVE` beleid,
  maar `DRAFT` of `HISTORICAL`.
- Voeg dashboardondersteuning en auditlog toe.

**Klaar wanneer:** een fout afgehandeld feit kan worden vervangen zonder verwijdering en verschijnt
daarna niet meer in de normale roadmapcontext.

### Fase 2 — centrale contextresolver

- Implementeer `ProductContextResolver` en een vast context-DTO.
- Vervang losse SQL- en stringcontext in cyclus-, overleg-, roadmap- en vraagresolverprompts.
- Dwing taak-, scope-, status- en tijdfiltering af.
- Leg context-ID's bij iedere agentrun vast.

**Klaar wanneer:** geen agentrol nog rechtstreeks een onbegrensde verzameling historische
databaseartefacten als beleidscontext ontvangt.

### Fase 3 — actieve repositorycontext

- Introduceer het actieve-documentenmanifest.
- Werk de workspace-/agentworkergrens bij zodat alleen actieve documenten standaard beschikbaar
  zijn.
- Verplaats actuele specificaties naar canonieke documenten; laat geschiedenis in Git en in het
  kennisregister bestaan.
- Voeg validatie toe die voorkomt dat twee actieve normatieve documenten over hetzelfde onderwerp
  zonder expliciete conflictmarkering bestaan.

**Klaar wanneer:** een vervangen beleidszin die alleen in Git-historie of een historisch dossier
staat niet door een normale productcyclus kan worden opgehaald.

### Fase 4 — autorisatie en promotieworkflow

- Classificeer welke besluiten eigenaarbevestiging vereisen.
- Laat overleggen concrete voorgestelde kenniswijzigingen opleveren, niet alleen vrije notulen.
- Laat de eigenaar de contextdiff bevestigen.
- Laat roadmap- en productcycli alleen bevestigde items als bindend gebruiken.

**Klaar wanneer:** een onderzoeker of Product Owner niet zelfstandig een algemene juridische of
privacyregel actief kan maken.

### Fase 5 — historische raadpleging

- Voeg historie-API en dashboardweergave toe.
- Voeg een expliciete historische agenttaak toe.
- Toon vervangingsketen, correctiereden en bronverwijzingen.

**Klaar wanneer:** geschiedenis volledig reconstrueerbaar blijft zonder deel uit te maken van de
standaard uitvoeringscontext.

## Migratie van het HKH-voorbeeld

Gebruik het conflict rond privacyregels als eerste migratietest:

1. registreer de 110/95-regel als `HEURISTIC` met de juiste beperkte scope;
2. registreer 100/75/50 als `RESEARCH_FINDING` over openbaarheid van Nederlandse
   burgerlijke-standakten, niet als algemene AVG-vrijgave;
3. trek of vervang het te brede afgehandelde roadmapfeit;
4. corrigeer roadmapthema 0003 zodat het geen onbevestigd algemeen beleid bevat;
5. maak één expliciet actueel privacy-/rechtenbeleid of laat de relevante vraag open;
6. controleer dat een nieuwe HKH-cyclus alleen de actuele, toepasselijke versie ontvangt;
7. controleer dat de volledige oude beslisketen via de historische weergave beschikbaar blijft.

Deze migratie hoort pas plaats te vinden nadat eigenaar en team de actuele inhoud hebben bepaald;
dit plan schrijft die beleidskeuze niet voor.

## Teststrategie

- Unit-tests voor status-, scope-, tijd- en gezagsfiltering.
- Integratietests waarin een actief item een ouder item vervangt.
- Test dat ingetrokken en historische items niet in een normale prompt voorkomen.
- Test dat dezelfde items wel in expliciete historische context voorkomen.
- Test dat een onderzoek niet automatisch tot actief beleid promoveert.
- Test op twee conflicterende actieve items binnen dezelfde scope: contextbouw faalt veilig en
  vraagt om correctie in plaats van zelf te kiezen.
- Test dat context-ID's duurzaam bij de agentrun zijn opgeslagen.
- End-to-endtest met het HKH-voorbeeld en de twee verschillende jaartalregels.

## Succescriteria

- Iedere agentuitkomst is achteraf te koppelen aan de exacte contextversie die zij ontving.
- Vervangen of ingetrokken kennis komt in nul normale cyclusprompts voor.
- Historische kennis blijft volledig raadpleegbaar.
- Onderzoeksbevindingen worden niet als bindend beleid gepresenteerd.
- Een eigenaarcorrectie werkt vanaf de eerstvolgende taak zonder oude documenten te verwijderen.
- Conflicterend actief beleid leidt tot een expliciete blokkade of eigenaarvraag, niet tot een door
  het model verzonnen compromis.

## Risico's en aandachtspunten

- Te agressief filteren kan relevante implementatiegeschiedenis verbergen. Daarom blijft een
  expliciet historisch pad nodig.
- Handmatige classificatie van bestaand materiaal kost tijd; automatische migratie mag oude tekst
  niet zonder bewijs tot actief beleid promoveren.
- Scopewaarden kunnen te algemeen worden. Begin met een kleine gecontroleerde taxonomie en breid
  alleen met concrete gevallen uit.
- Contextselectie zelf wordt productkritische code en vereist dezelfde audit- en testdiscipline als
  publicatie en levering.
- Een model met onbeperkte web- of repositorytoegang kan historische tekst alsnog vinden. De
  taakworkspace en documenttoegang moeten daarom technisch worden begrensd; een promptverbod alleen
  biedt geen garantie.
