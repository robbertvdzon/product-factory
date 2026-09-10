# Voorstel — Product Advisor en Product Requests

Status: gerealiseerd op 2026-09-09 en automatisch hotfixen geactiveerd op 2026-09-10. Productie
gebruikt `PF_SOFTWARE_FACTORY_HOTFIX_ENABLED=true` met maximaal twee verzendpogingen per exacte
requestversie; bij een verloren eerste response is maximaal één dubbele story een geaccepteerd
risico.
Datum: 2026-09-09

## Samenvatting

Product Factory krijgt een productgebonden gesprekservaring waarin een product owner, zoals Marc
voor PvdD, zelfstandig met de AI-rol `PRODUCT_ADVISOR` kan overleggen. De advisor kan vragen over
het bestaande product beantwoorden, de broncode en actuele documentatie onderzoeken en de
geconfigureerde applicatie openen en testen.

Een gesprek hoeft niet tot ontwikkeling te leiden. Als er wel een wijziging uit voortkomt, maakt de
advisor een concreet voorstel dat de product owner eerst bevestigt. Product Factory kiest daarna één
van drie uitvoerroutes:

1. een kleine, eenduidige correctie wordt als bestaande Software Factory-hotfix uitgevoerd;
2. een duidelijke maar niet-kleine bug wordt rechtstreeks als gewone bugfixstory uitgevoerd;
3. een productverbetering gaat als gericht ontwerpverzoek naar Productontwerp en wordt een epic.

Een epic wordt pas voor planning beschikbaar nadat eerst de product owner en daarna de factory owner
dezelfde exacte epicversie hebben goedgekeurd. Vragen uit het ontwerp- en planningsproces worden
gericht aan de juiste persoon aangeboden, in plaats van aan één impliciete globale Stakeholder.

Alle applicatieontwikkeling uit dit voorstel vindt plaats in de repository `product-factory`. De
repositories `softwarefactory` en `pvdd` worden niet gewijzigd. Product Factory gebruikt alleen
bestaande Software Factory-ingangen en bestaande PvdD-bronnen en omgevingen.

## Aanleiding

PvdD wordt door Marc gebruikt en door Robbert technisch beheerd. Nieuwe wensen en gevonden fouten
lopen nu via Robbert, die ze samen met AI onderzoekt en laat ontwikkelen. Daardoor is Robbert ook
nodig voor productvragen en de eerste verkenning van iedere wens, terwijl Marc de meeste
productcontext en gebruikerservaring zelf heeft.

Product Factory kan al epics ontwerpen, epics handmatig laten goedkeuren, stories plannen en stories
naar Software Factory sturen. De huidige inrichting gaat echter uit van precies één globale
Stakeholder en biedt nog geen productgebonden chat, gerichte ontwerpaanvraag, goedkeuring door twee
verschillende personen of directe lichte route voor kleine fouten.

## Doel

Marc moet binnen Product Factory voor PvdD:

- een nieuw gesprek met `PRODUCT_ADVISOR` kunnen beginnen;
- kunnen vragen hoe bestaand gedrag werkt zonder een wijziging aan te vragen;
- een wens, probleem of bug samen met de advisor kunnen onderzoeken;
- de advisor de actuele applicatie kunnen laten bekijken en testen;
- een concreet wijzigingsvoorstel kunnen controleren en bevestigen;
- een kleine correctie zonder een volledige multi-agentketen kunnen laten uitvoeren;
- een gewone bugfix rechtstreeks naar Software Factory kunnen laten sturen;
- een grotere productverbetering gericht als epic laten uitwerken;
- vragen uit die uitwerking kunnen beantwoorden;
- de uiteindelijke epic als product owner kunnen goedkeuren of terugsturen;
- de voortgang en uitkomst van zijn verzoek kunnen volgen.

Robbert moet als factory owner:

- product owners en hun producttoegang kunnen beheren;
- alle gesprekken en verzoeken kunnen inzien;
- technische of ongewenste gevolgen van een epic kunnen beoordelen;
- een door Marc goedgekeurde epic kunnen goedkeuren of met uitleg terugsturen;
- globale configuratie, agenttoegang, schedules en technische bediening blijven beheren.

## Harde randvoorwaarden

- Er komen geen codewijzigingen in Software Factory.
- Er komen geen codewijzigingen in PvdD.
- Product Factory gebruikt Software Factory zoals die nu bestaat.
- Er komt geen extra diffcontrole of hotfixspecifieke veiligheidsstap in Software Factory.
- De bestaande Software Factory-hotfixketen blijft exact zoals hij is.
- Een AI-antwoord of AI-voorstel veroorzaakt nooit zelfstandig een externe mutatie. Een
  deterministisch Product Factory-command voert pas na menselijke bevestiging de gekozen route uit.
- Marc krijgt alleen toegang tot de producten waaraan hij expliciet gekoppeld is.
- Geheimen worden niet in gesprekken, prompts, databasevelden of logs opgenomen.

## Niet-doelen

- Geen Telegram-kanaal als primaire interface.
- Geen Product Factory-chat inbouwen in PvdD.
- Geen aparte login of PO-interface in Software Factory.
- Geen nieuwe Software Factory-agent, prompt, pipelinefase, controle of API.
- Geen automatische codewijzigingen door `PRODUCT_ADVISOR` zelf.
- Geen vervanging van Productontwerp, Productplanning of Kwaliteitsbewaking.
- Geen generiek ticketsysteem naast de bestaande productobjecten.

## Gekozen begrippen

### ProductConversation

Een `ProductConversation` is het duurzame gesprek tussen een gebruiker en `PRODUCT_ADVISOR` over
precies één product. Een gesprek kan uitsluitend informatief blijven en zonder wijziging worden
gesloten.

De gebruiker ziet in de UI eenvoudig **Gesprekken** en **Nieuw gesprek**. De technische naam hoeft
niet in de gebruikersinterface te staan.

### ProductRequest

Een `ProductRequest` is een bevroren, door `PRODUCT_ADVISOR` opgesteld wijzigingsvoorstel dat uit
een gesprek voortkomt. Het bevat voldoende informatie om de gekozen vervolgroute te starten. Het
oorspronkelijke gesprek blijft als bron gekoppeld.

Het onderscheid voorkomt dat iedere vraag over het systeem automatisch als veranderverzoek of
backlogitem wordt behandeld.

### PRODUCT_ADVISOR

`PRODUCT_ADVISOR` is de stabiele technische `AgentRoleKey`. De gebruikersinterface noemt deze rol
**Productadviseur**.

De advisor:

- legt bestaand gedrag begrijpelijk uit;
- onderzoekt wensen en problemen samen met de gebruiker;
- stelt gerichte vervolgvragen;
- leest productdoel, grenzen, besluiten en relevante producthistorie;
- onderzoekt broncode en actuele documentatie op een exacte Git-revisie;
- gebruikt de geconfigureerde testomgeving binnen haar toegangsgrenzen;
- maakt zo nodig een voorstel voor een hotfix, bugfix of epicroute;
- benoemt onzekerheid en doet geen alsof-uitspraken over niet onderzocht gedrag.

De advisor:

- schrijft niet in de productrepository;
- maakt niet rechtstreeks een Software Factory-story;
- publiceert niet rechtstreeks een epic;
- keurt niets namens Marc of Robbert goed;
- krijgt geen Software Factory-beheerdersaccount of integratietoken in de agentcontainer.

## Gebruikers en bevoegdheden

De huidige globale Stakeholder wordt vervangen door expliciete gebruikers en productlidmaatschappen.
Google SSO blijft de authenticatiemethode.

### Rollen

| Rol | Reikwijdte | Betekenis |
|---|---|---|
| `PRODUCT_OWNER` | één product | Productinhoudelijke eigenaar, bijvoorbeeld Marc voor PvdD. |
| `FACTORY_OWNER` | Product Factory breed | Beheerder en eindverantwoordelijke, bijvoorbeeld Robbert. |

Een factory owner heeft toegang tot alle producten. Een product owner heeft uitsluitend toegang tot
producten waarvoor een actief `ProductMembership` bestaat. Namen worden nergens hardgecodeerd; Marc
en Robbert zijn de eerste gebruikers van het generieke model.

### Bevoegdheden

| Handeling | Product owner | Factory owner |
|---|---:|---:|
| Eigen producten en actuele productinformatie bekijken | ja | ja |
| Productgesprek starten en berichten sturen | ja | ja |
| Eigen wijzigingsvoorstel bevestigen | ja | ja |
| Vragen voor het eigen product beantwoorden | ja | ja |
| Hotfix of gewone bugfix vanuit een bevestigd verzoek starten | ja | ja |
| Epic productinhoudelijk goedkeuren | ja | ja, wanneer zelf product owner |
| Epic technisch/eindverantwoordelijk goedkeuren | nee | ja |
| Producttoegang en lidmaatschappen beheren | nee | ja |
| AI-modellen, credentials, schedules en globale instellingen beheren | nee | ja |
| Andere producten bekijken | nee | ja |

Autorisatie wordt altijd in de backend afgedwongen. Frontendzichtbaarheid is alleen presentatie.

## Vier mogelijke uitkomsten van een gesprek

### 1. Alleen een antwoord

Marc vraagt bijvoorbeeld waarom een berekening op een bepaalde manier werkt. De advisor onderzoekt
de code, documentatie en eventueel de applicatie en geeft antwoord. Het gesprek kan openblijven of
worden gesloten. Er ontstaat geen `ProductRequest`, epic of story.

### 2. Snelle correctie

Een kleine, eenduidige fout kan als hotfix rechtstreeks naar Software Factory. Voorbeelden zijn een
tikfout, een evident verkeerd label of een simpele lokale presentatiefout.

De advisor toont vóór bevestiging minimaal:

- titel;
- waargenomen fout;
- verwacht gedrag;
- wijze van reproduceren of waarnemen;
- concrete wijzigingsgrens;
- acceptatiecriteria;
- de waarschuwing dat Software Factory de verkorte hotfixketen gebruikt.

Na bevestiging door Marc maakt Product Factory een Software Factory-story met `hotfix=true`. Er is
geen aanvullende goedkeuring door Robbert. De bestaande Software Factory-keten bepaalt vervolgens
het verloop: één developer-run, bestaande deterministische verificatie, merge en deploy.

De keuze is gebaseerd op eenduidigheid en risico, niet alleen op het aantal regels. Een wijziging
aan autorisatiecode kan klein zijn en toch geen hotfix zijn. Aanpassingen aan AI-prompts,
disclaimers, politieke inhoud, privacygedrag, datamodellen, migraties, externe integraties of
belangrijke businessregels zijn nooit automatisch een snelle correctie.

Er komt geen controle achteraf die de omvang van de diff beoordeelt. De verantwoordelijkheid om het
juiste spoor te kiezen ligt vóór de overdracht bij de advisor en Marc.

### 3. Gewone bugfix

Een aantoonbare afwijking met meer impact of onzekerheid wordt als normale bugfixstory rechtstreeks
naar Software Factory gestuurd, zonder eerst een epic te maken. De story bevat minimaal:

- context en gebruikersimpact;
- reproduceerstappen;
- werkelijk en verwacht gedrag;
- relevante omgeving en bronrevisie;
- acceptatiecriteria;
- bekende grenzen en regressierisico's.

Marc bevestigt de exacte story. Product Factory verstuurt hem daarna als `BUGFIX` via de bestaande
Software Factory-v2-integratie. Software Factory gebruikt zijn gewone volledige storyketen.

### 4. Productverbetering

Nieuw gedrag, een nieuwe mogelijkheid of een wijziging die een productkeuze vereist, wordt een
gericht ontwerpverzoek. Productontwerp verwerkt dit tot een complete epic. De epic volgt daarna de
goedkeurings- en planningsroute uit dit voorstel.

## Beslisregels voor de vervolgroute

De advisor doet een voorstel; Marc ziet de keuze en kan deze laten aanpassen voordat hij bevestigt.

| Vraag | Uitkomst |
|---|---|
| Is alleen uitleg nodig? | Antwoord, geen ProductRequest. |
| Is werkelijk gedrag aantoonbaar anders dan reeds bedoeld? | Bugroute. |
| Is de bug klein, lokaal, eenduidig en laag-risico? | Snelle correctie/hotfix. |
| Is het een duidelijke bug maar niet geschikt voor hotfix? | Gewone bugfix. |
| Is nieuw of gewijzigd productgedrag nodig? | Productverbetering/epic. |
| Zijn probleem of gewenste uitkomst nog onduidelijk? | Gesprek voortzetten, nog niets versturen. |

Een urgent verzoek is niet automatisch een hotfix. Een hotfix is een verkorte uitvoerroute voor
klein en duidelijk werk.

## Gespreksverloop

```text
Marc start een gesprek voor PvdD
              |
              v
PRODUCT_ADVISOR onderzoekt vraag, code, documentatie en applicatie
              |
       +------+-------------------+-------------------+
       |                          |                   |
       v                          v                   v
   antwoord                  meer vragen        wijziging kansrijk
       |                          |                   |
       +------ gesprek -----------+                   v
                                             concreet voorstel
                                                    |
                                             Marc bevestigt
                                                    |
                       +----------------------------+------------------+
                       |                            |                  |
                       v                            v                  v
                    hotfix                     bugfixstory       ontwerpverzoek
                       |                            |                  |
                       +------ Software Factory ---+                  v
                                                               Productontwerp
```

Ieder bericht van Marc maakt een asynchrone AI-taak. Er blijft dus niet permanent een dure agent
draaien. Product Factory bewaart het gesprek en stelt bij iedere beurt gecontroleerd de benodigde
context samen. De UI toont `Bezig`, `Wacht op jou`, `Voorstel gereed`, `Geblokkeerd` of `Gesloten`.

Als een AI-taak technisch mislukt, blijft het gebruikersbericht bewaard en kan exact dezelfde beurt
idempotent opnieuw worden geprobeerd. Een tweede klik maakt geen tweede voorstel of externe story.

## Context en gereedschap van PRODUCT_ADVISOR

Product Factory stelt de taakinput samen uit vertrouwde gegevens en bevriest de gebruikte versies:

- `ProductAssignment`: doelgroep, productdoel, harde grenzen en Git-URL;
- geldige `Decision`s;
- relevante epics, stories, bugs, verificaties en gebruikerssignalen;
- het actuele geheugen van uitsluitend `PRODUCT_ADVISOR` voor dit product;
- de volledige gesprekshistorie of een gecontroleerde samenvatting plus recente berichten;
- een exacte Git-URL en commit-SHA;
- `TestableProductConfiguration` met veilige routes en toegangsgrenzen;
- de vaste Product Factory-procesregels en uitvoerroutes.

De bestaande Agent Runtime-worker checkt de exacte repositoryrevisie read-only uit in een tijdelijke
taakcontainer. Daardoor kan de advisor zowel broncode als de actuele productdocumentatie lezen. De
rol krijgt via de bestaande credentialgrantfunctie alleen de productcredentials die een factory
owner expliciet aan `PRODUCT_ADVISOR` heeft toegekend.

De advisor mag met browsergereedschap de geconfigureerde acceptatieomgeving bekijken en bedienen.
Acceptatie is de standaard voor handelingen die data kunnen wijzigen. Productie wordt alleen
read-only of via expliciet veilige testroutes gebruikt. Onderzochte routes, omgeving en relevante
waarnemingen komen terug in het antwoord of wijzigingsvoorstel.

Broncode, webpagina's, documenten en gebruikersberichten zijn onvertrouwde context. Zij kunnen de
vaste rolgrenzen of toegestane Product Factory-commands niet wijzigen.

## ProductRequest

Een verzoek ontstaat pas wanneer de advisor een concreet wijzigingsvoorstel heeft gemaakt. De
inhoud wordt geversioneerd. Marc keurt altijd één exacte versie goed; een inhoudelijke aanpassing
maakt een nieuwe versie en laat een eerdere goedkeuring vervallen.

### Minimale gegevens

- request-ID en product-ID;
- bronconversation-ID en aanvrager;
- versie en status;
- soort `HOTFIX`, `BUGFIX` of `EPIC_CANDIDATE`;
- titel en korte samenvatting;
- probleem en gebruikersimpact;
- huidig en gewenst gedrag;
- onderzoeksbewijs en gebruikte bronrevisie;
- acceptatiecriteria;
- expliciete scope en grenzen;
- aanmaak- en wijzigingsmoment;
- goedkeuringsrecord van Marc;
- na routering: gekoppeld Software Factory-storykey of Product Factory-epic-ID.

### Levenscyclus

```text
DRAFT -> AWAITING_REQUESTER_APPROVAL -> APPROVED -> ROUTING -> ROUTED
   |                 |                     |            |
   +------------> CANCELLED <--------------+            +-> FAILED
```

`ROUTED` betekent alleen dat het verzoek duurzaam is overgedragen. De actuele uitvoering wordt
gelezen bij het gekoppelde vervolgobject en niet als tweede waarheid in `ProductRequest` gekopieerd.

## Gerichte route naar Productontwerp

Productontwerp heeft nu geen inkomende werkqueue en kiest zelf ontwerpwerk. Om een bevestigd
`EPIC_CANDIDATE` gericht te verwerken, krijgt Productontwerp binnen Product Factory een duurzaam
`DesignWorkItem`.

Het workitem bevat minimaal:

- product-ID;
- ProductRequest-ID en exacte versie;
- doel `CREATE_EPIC_FROM_PRODUCT_REQUEST`;
- idempotentiesleutel;
- status `PENDING`, `IN_PROGRESS`, `WAITING_FOR_AI`, `WAITING_FOR_ANSWER`, `DONE` of `FAILED`;
- gekoppeld epic-ID zodra gepubliceerd.

Een command als `requestEpicFromProductRequest(...)` bewaart alleen het workitem. Het start geen
agent. Een handmatige of geplande `runProcessSession(productId)` van Productontwerp claimt het
gerichte werk vóór autonoom ontwerpwerk. Daarmee blijven de bestaande queue- en sessieregels
intact.

Productontwerp ontvangt de goedgekeurde requestversie als belangrijke aanleiding, maar blijft
verantwoordelijk voor een volledige epic: probleem, oplossing, richting, UX waar nodig,
acceptatiecriteria, onderzoek en behapbaarheid. Het ProductRequest wordt niet klakkeloos als epic
gekopieerd.

## Vragen aan Marc en Robbert

De bestaande `StakeholderQuestion` krijgt een expliciete geadresseerde:

- `requestedRespondentUserId` voor precies één gebruiker;
- product-ID voor autorisatie;
- bronrol, bronprocessessie en gekoppeld request/epic/story;
- status en antwoordhistorie.

Vragen tijdens de productinhoudelijke uitwerking van een request gaan standaard naar de aanvrager,
dus bij PvdD naar Marc. Vragen over technisch eigenaarschap of een afwijzing door de factory owner
kunnen expliciet naar Robbert gaan. Alleen de geadresseerde of een factory owner mag antwoorden.

Een antwoord hervat niet onmiddellijk willekeurige agentcode. Het wordt duurzaam opgeslagen en de
betreffende processessie neemt het bij een volgende handmatige of geplande run mee.

De Product Factory-UI toont een inbox **Vragen aan jou**. Een badge en in-appmelding zijn voor de
eerste versie voldoende. E-mail of Telegram kan later als notificatiekanaal worden toegevoegd
zonder de inhoudelijke workflow te veranderen.

## Dubbele epicgoedkeuring

Voor epics die uit een ProductRequest voortkomen geldt handmatige, opeenvolgende goedkeuring:

```text
AWAITING_PRODUCT_OWNER_APPROVAL
              |
        Marc keurt goed
              v
AWAITING_FACTORY_OWNER_APPROVAL
              |
      Robbert keurt goed
              v
          AVAILABLE
```

Marc beoordeelt of probleem, oplossing en gebruikersgedrag kloppen. Robbert beoordeelt onder meer
technische gevolgen, grenzen, onderhoudbaarheid en of hij de ontwikkeling wil laten uitvoeren.

Beide personen kunnen de epic met een verplichte vrije tekstreden terugsturen naar
`NEEDS_REFINEMENT`. Productontwerp maakt daarna een nieuwe epicversie. Alle goedkeuringen horen bij
de oude versie en gelden niet voor de nieuwe versie.

Een factory owner kan de product owner-goedkeuring niet stilzwijgend invullen. Voor een product
waar Robbert beide rollen vervult, zijn het nog steeds twee expliciete acties of wordt per product
bewust een ander goedkeuringsbeleid ingesteld.

Na de tweede goedkeuring wordt de epic `AVAILABLE`. De bestaande Productplanning-sessie kan hem
claimen, in stories verdelen en via de bestaande dispatcher naar Software Factory sturen.

## Software Factory-routes zonder Software Factory-wijziging

### Gewone epics en bugfixes

Stories uit een epic blijven via de bestaande v2-integratie en Software Factory-dispatcher lopen.
Een rechtstreeks ProductRequest van soort `BUGFIX` kan hetzelfde bestaande v2-contract gebruiken:
het request-ID en de requestversie fungeren dan als `sourceStoryId` en `sourceStoryVersion`.

Product Factory bewaart idempotent delivery attempts en leest de bestaande publieke statussen
`OPEN`, `DONE` en `CANCELLED` terug. Er is geen nieuw contract nodig.

### Hotfixes

Software Factory ondersteunt `hotfix=true` al bij zijn gewone story-aanmaak
`POST /api/v1/stories`. De bestaande machine-tot-machine endpoints
`/api/integrations/v1/stories` en `/api/integrations/v2/stories` zetten hotfix echter intern altijd
op `false`. Daardoor kan het huidige Product Factory-integratietoken niet worden gebruikt om een
hotfix te maken.

Onder de harde randvoorwaarde dat Software Factory niet wordt gewijzigd, gebruikt Product Factory
voor hotfixes daarom de bestaande gewone story-aanmaak-API met een apart geconfigureerd, geldig
Software Factory dashboard-bearertoken. Product Factory zet daarbij:

- `hotfix=true`;
- `start=true`;
- de PvdD-project- en repositorygegevens;
- titel en complete omschrijving uit de goedgekeurde requestversie;
- passende bestaande notificatie-events.

Product Factory neemt een stabiele requestmarker in de omschrijving op en controleert bij een retry
eerst de bestaande storylijst, zodat een verloren response niet onnodig een tweede story maakt.
Deze idempotentie wordt volledig aan Product Factory-zijde geïmplementeerd.

Het huidige Software Factory dashboardtoken verloopt na dertig dagen en is een gebruikerssessie,
geen machinecredential. Daarom geldt als operationele randvoorwaarde:

- het token wordt als Product Factory-secret beheerd en nooit aan `PRODUCT_ADVISOR` gegeven;
- de UI toont tijdig dat het token ontbreekt of niet meer geldig is;
- een verlopen token zet het request op `FAILED`/opnieuw uitvoerbaar en maakt geen gewone story als
  stilzwijgende fallback;
- de factory owner vernieuwt het token zolang Software Factory bewust ongewijzigd moet blijven.

Dit is de enige kwetsbare koppeling in het voorstel. Zonder periodiek dashboardtoken, handmatige
story-aanmaak of een toekomstige Software Factory-contractwijziging kan Product Factory met de
huidige externe interfaces geen echte hotfix automatisch aanmaken. Dit moet in de eerste technische
spike worden bewezen voordat de hotfixroute als productief gereed geldt.

## Functionele gebruikersinterface

### Navigatie voor Marc

Marc ziet na login alleen PvdD en de voor hem relevante onderdelen:

- **Overzicht** — actuele verbetering, open verzoeken en vragen;
- **Gesprekken** — gesprekken met de Productadviseur;
- **Verzoeken** — bevestiging en voortgang van wijzigingsvoorstellen;
- **Epics** — epics die uit zijn verzoeken voortkomen en op zijn goedkeuring wachten;
- **Stories** — leesbare uitvoeringsstatus;
- **Vragen aan jou** — open vragen uit advisor, ontwerp of planning.

Technische operatie, AI-modelbeheer, schedules, credentials en andere producten blijven verborgen
én backendmatig verboden.

### Gespreksscherm

Het gespreksscherm bevat:

- chronologische berichten;
- zichtbare status van de actuele advisorbeurt;
- gebruikte omgeving en bronrevisie wanneer relevant;
- eventuele bijlagen of screenshots;
- een voorstelkaart zodra de advisor voldoende informatie heeft;
- knoppen **Aanpassen**, **Bevestigen** en **Niet uitvoeren**;
- na bevestiging een koppeling naar ProductRequest en het vervolgobject.

De advisor mag tijdens een gesprek van voorgestelde route veranderen. Alleen de laatst bevestigde,
exacte voorstelversie wordt uitgevoerd.

### Goedkeuringsscherm

Een epicdetail toont de volledige bevroren epic, de bronrequest, open vragen, versie, beide
goedkeuringsstatussen en eerdere terugstuurredenen. De knoppen zijn rol- en statusafhankelijk.

Marc kan pas goedkeuren als de epic op product owner-goedkeuring wacht. Robbert kan pas goedkeuren
nadat Marc dezelfde versie heeft goedgekeurd.

## Voorgestelde moduleverdeling binnen Product Factory

Er is geen nieuwe repository nodig. De voorkeur is uitbreiding van bestaande modules:

| Module | Wijziging |
|---|---|
| `product-factory-api` | Publieke DTO's en commands voor gebruikers, lidmaatschappen, gesprekken, requests, gerichte vragen en designworkitems. |
| `product-impl` | Eigenaarschap van `ProductMembership`, `ProductConversation`, berichten en `ProductRequest`; productgebonden autorisatieregels. |
| `product-factory-app` | HTTP-API, sessie-identiteit met rollen, `ProductAdvisorOrchestrator`, scheduler/resume en samengestelde readmodellen. |
| `ai-execution-impl` | Nieuwe jobkey(s) voor advisorbeurten en bestaande async taakafhandeling hergebruiken. |
| `agent-memory-impl` | Registratie van rol `PRODUCT_ADVISOR` en productgebonden geheugen/grants. |
| `product-design-impl-mvp` | `DesignWorkItem`, gerichte claimvolgorde en nieuwe dubbele epicgoedkeuring. |
| `product-planning-impl-mvp` | Gerichte vragen adresseren; gewone epicplanning blijft verder gelijk. |
| `software-factory-dispatcher-impl` | Product Factory-zijdige directe dispatch van goedgekeurde bugfixes en hotfixes, delivery attempts en statuskoppeling. |
| `product-factory-frontend` | Product owner-navigatie, chat, verzoeken, vragen en tweestapsgoedkeuring. |

Een afzonderlijke Mavenmodule `product-advisory-impl` is pas zinvol als de gesprekscapability groot
genoeg wordt om `product-impl` onoverzichtelijk te maken. Voor de eerste versie voorkomt hergebruik
van de bestaande product-/overleggrens onnodige modulecomplexiteit.

## Publieke commands en queries op hoofdlijnen

De definitieve namen worden tijdens implementatie afgestemd op de bestaande contractstijl. De
bedoelde grens is:

```text
UserId createOrActivateUser(...)
void assignProductOwner(...)
void revokeProductMembership(...)
List<ProductMembershipDetails> findMemberships(...)

ProductConversationId startProductConversation(...)
void addUserMessage(...)
void closeProductConversation(...)
ProductConversationDetails getProductConversation(...)
List<ProductConversationSummary> findProductConversations(...)

void publishAdvisorReply(...)
ProductRequestId proposeProductRequest(...)
void reviseProductRequest(...)
void approveProductRequest(...)
void cancelProductRequest(...)
ProductRequestDetails getProductRequest(...)
List<ProductRequestDetails> findProductRequests(...)

DesignWorkItemId requestEpicFromProductRequest(...)
void approveEpicAsProductOwner(...)
void approveEpicAsFactoryOwner(...)
void requestEpicRefinement(...)

void dispatchDirectBugfix(...)
void dispatchHotfix(...)
DirectDeliveryDetails getDirectDelivery(...)
```

Alle mutaties bevatten actor, verwachte versie en idempotentiesleutel. Commands controleren
lidmaatschap en status in dezelfde transactie.

## Implementatieplan

### Stap 0 — technische spike op de bestaande grenzen

Doel: de twee externe aannames bewijzen voordat domein- en UI-werk erop worden gebouwd.

Werk:

1. Maak vanuit een lokale Product Factory-testadapter met een geldig dashboardtoken een tijdelijke
   Software Factory-story met `hotfix=true` via de bestaande `/api/v1/stories`.
2. Bewijs dat de aangemaakte story werkelijk de bestaande hotfixketen krijgt.
3. Bewijs storyterugvinden via de stabiele requestmarker en `stories.list` na een gesimuleerde
   verloren response.
4. Leg vast hoe het dertigdaagse token in secrets wordt geplaatst en vernieuwd.
5. Bewijs dat een `ProductRequest` als bronobject via v2 als gewone `BUGFIX` kan worden verstuurd en
   gevolgd, zonder Productplanning-story.
6. Bewijs dat de bestaande Runtime-worker voor PvdD zowel de repository op exacte SHA als de
   geconfigureerde applicatie kan onderzoeken met de rolcredentials.

Oplevering: een kort spikeverslag, geautomatiseerde contractfixtures in Product Factory en een
expliciet besluit `haalbaar` of `geblokkeerd` per route. Er wordt niets in Software Factory of PvdD
aangepast.

### Stap 1 — gebruikers en productlidmaatschappen

Werk:

1. Voeg duurzame gebruikersidentiteit op genormaliseerd Google-e-mailadres toe.
2. Voeg globale factory owner-rol en productgebonden `ProductMembership` toe.
3. Migreer de bestaande toegestane Stakeholder naar factory owner zonder toegang te verliezen.
4. Laat authenticatiesessies user-ID en effectieve rollen leveren in plaats van iedere gebruiker
   `ROLE_STAKEHOLDER` te geven.
5. Voeg centrale backendautorisatie voor productqueries en -commands toe.
6. Voeg beheer-UI toe waarmee Robbert Marc aan PvdD koppelt.
7. Filter navigatie, productkeuze en readmodellen op effectieve toegang.

Verificatie:

- Marc kan PvdD zien maar geen ander product;
- Marc krijgt `403` op factorybeheer, ook via directe HTTP-aanroep;
- Robbert behoudt toegang tot alle bestaande functies;
- intrekken van lidmaatschap beëindigt nieuwe toegang zonder historie te verwijderen.

### Stap 2 — gesprekken en berichten

Werk:

1. Voeg `ProductConversation` en append-only berichten toe met Flywaymigratie.
2. Voeg commands, queries en HTTP-endpoints toe.
3. Bouw lijst- en detailscherm met nieuwe-gesprekactie.
4. Bewaar status, versies, actor en timestamps.
5. Voeg idempotentie en optimistic locking toe.
6. Ondersteun sluiten en opnieuw openen alleen via expliciete commands.

Verificatie:

- twee gelijktijdige berichtinzendingen maken geen dubbele of verloren berichten;
- een gebruiker zonder productlidmaatschap kan gesprek noch bericht lezen;
- reload en polling behouden de volledige volgorde en actuele status.

### Stap 3 — PRODUCT_ADVISOR

Werk:

1. Registreer `PRODUCT_ADVISOR` in de rolcatalogus.
2. Voeg een configureerbare AI-jobkey voor een advisorbeurt toe.
3. Bouw `ProductAdvisorOrchestrator` naar het bestaande Meeting AI-patroon.
4. Stel per beurt de vertrouwde product-, conversatie-, repository- en omgevingscontext samen.
5. Laat Runtime een exacte repository-SHA uitchecken en browsergereedschap gebruiken.
6. Definieer een strikt outputcontract voor antwoord, vervolgvraag en optioneel voorstel.
7. Publiceer de AI-uitkomst alleen na deterministische schemavalidatie.
8. Voeg retry, hervatten en zichtbare technische foutstatus toe.
9. Maak rolgeheugen en credentialgrants voor de advisor beheersbaar door de factory owner.

Verificatie:

- informatieve vraag levert antwoord zonder ProductRequest;
- advisor noemt aantoonbaar de gebruikte SHA en omgeving;
- promptinjectie uit repository of webpagina kan geen command uitvoeren;
- credentials verschijnen niet in taakinput, resultaat of logging;
- een mislukte beurt kan zonder dubbel bericht worden hervat.

### Stap 4 — ProductRequest en voorstelbevestiging

Werk:

1. Voeg geversioneerde `ProductRequest` en de drie requestsoorten toe.
2. Maak voorstelpublicatie een vertrouwd intern command van de advisororchestrator.
3. Voeg voorstelkaart en revisie-/bevestigingsflow toe.
4. Laat iedere inhoudelijke wijziging eerdere goedkeuring ongeldig maken.
5. Koppel request duurzaam aan bronconversation en aanvrager.
6. Voeg requestlijst, detail en statusprojectie toe.

Verificatie:

- AI-output alleen start geen ontwikkeling;
- alleen een bevoegde gebruiker kan de exacte actuele versie bevestigen;
- dubbele bevestiging is idempotent;
- een oude voorstelversie kan niet na een revisie worden uitgevoerd.

### Stap 5 — vragen gericht adresseren

Werk:

1. Breid `StakeholderQuestion` uit met geadresseerde gebruiker en relevante koppelingen.
2. Migreer bestaande vragen naar de bestaande factory owner.
3. Laat Advisor, Productontwerp en Productplanning de geadresseerde via vertrouwde code kiezen.
4. Bouw **Vragen aan jou** met badge, detail en antwoordactie.
5. Neem antwoorden bij het hervatten van de juiste processessie mee.

Verificatie:

- Marc ziet en beantwoordt PvdD-vragen die aan hem zijn gericht;
- andere product owners zien die vragen niet;
- Robbert kan als factory owner ondersteunen zonder de geadresseerde stilzwijgend te wijzigen;
- beantwoording hervat exact het gekoppelde werk en geen willekeurige andere sessie.

### Stap 6 — gerichte epicroute

Werk:

1. Voeg `DesignWorkItem` en publieke queuecommands toe aan Productontwerp.
2. Routeer een goedgekeurde `EPIC_CANDIDATE` idempotent naar één workitem.
3. Laat `runProcessSession(productId)` gericht werk vóór autonoom ontwerpwerk claimen.
4. Voeg de ProductRequest-versie toe aan de bevroren ontwerpsnapshot.
5. Publiceer en koppel de ontstane epic.
6. Toon ontwerpstatus en vragen bij het verzoek.

Verificatie:

- één bevestigd request maakt maximaal één actief designworkitem;
- een herstart hervat dezelfde sessie;
- een epic verwijst naar de exacte requestversie;
- Productontwerp kan aanvullende vragen stellen voordat de epic gereed is.

### Stap 7 — dubbele epicgoedkeuring

Werk:

1. Voeg de twee goedkeuringsfasen en duurzame approvalrecords toe.
2. Implementeer afzonderlijke rolgebonden goedkeuringscommands.
3. Laat terugsturen altijd reden en verwachte epicversie vereisen.
4. Laat een revisie alle approvals van de vorige versie historisch bewaren maar ongeldig maken.
5. Pas Productplanning aan zodat alleen `AVAILABLE` na beide approvals claimbaar is.
6. Bouw de product owner- en factory owner-acties in epicdetail en hun inboxen.

Verificatie:

- Robbert kan niet vóór Marc goedkeuren;
- planning kan geen halfgoedgekeurde epic claimen;
- afwijzen maakt een gerichte nieuwe ontwerpcyclus;
- na revisie moeten beide rollen de nieuwe versie beoordelen.

### Stap 8 — directe gewone bugfix

Werk:

1. Maak van een goedgekeurde `BUGFIX` een volledig v2-deliverypackage.
2. Gebruik request-ID en versie als stabiele bronidentiteit.
3. Hergebruik de dispatcheradapter, outbox, delivery attempts en retryclassificatie.
4. Synchroniseer bestaande `OPEN`, `DONE` en `CANCELLED` status.
5. Toon Software Factory-storykey en actuele status bij request en gesprek.

Verificatie:

- een gewone bugfix heeft geen epic nodig;
- een retry maakt geen tweede Software Factory-story;
- Product Factory kan na restart de status blijven synchroniseren;
- de story wordt als gewone `BUGFIX`, niet als hotfix uitgevoerd.

### Stap 9 — directe hotfix

Werk:

1. Voeg een afzonderlijke Product Factory-adapter voor de bestaande dashboard-story-API toe.
2. Lees het dashboardtoken uitsluitend uit secretconfiguratie.
3. Bouw de payload met `hotfix=true` en `start=true` uit de goedgekeurde requestversie.
4. Voeg stabiele requestmarker, preflight lookup en idempotente delivery attempt toe.
5. Toon een duidelijke fout en herhaalactie bij verlopen of ontbrekend token.
6. Voeg een operationele tokenstatus toe zonder tokenwaarde te tonen.
7. Documenteer het dertigdaagse vernieuwingsproces in het Product Factory-runbook.

Verificatie:

- een hotfixrequest maakt een echte hotfixstory;
- de Software Factory-story bevat exact de bevestigde omschrijving;
- een verloren response plus retry maakt geen tweede story;
- een ongeldig token maakt geen gewone story als fallback;
- er is geen code, configuratiemigratie of testwijziging in de Software Factory-repository.

### Stap 10 — samenhangende UX, notificaties en acceptatie

Werk:

1. Voeg dashboardkaarten voor open gesprekken, verzoeken, vragen en approvals toe.
2. Voeg in-appnotificaties toe voor vraag, voorstel gereed, epicgoedkeuring en uitvoeringsuitkomst.
3. Maak alle routes responsive en bookmarkbaar volgens de bestaande frontendregels.
4. Voeg Testbedscenario's voor de volledige ketens toe.
5. Werk de actuele Product Factory-documentatie pas bij wanneer functionaliteit werkelijk is
   geïmplementeerd.
6. Voeg operationele dashboards toe voor wachtende AI-taken en delivery failures.

Verificatie:

- de acceptatiescenario's hieronder zijn groen;
- backendtests, Modulithcontrole, migratiesmoketest en frontendtests slagen;
- Product Factory kan met ongewijzigde Software Factory- en PvdD-versies worden uitgerold;
- Marc kan de primaire routes gebruiken zonder technische schermen nodig te hebben.

## Acceptatiescenario's

### Informatieve vraag

1. Marc logt in en ziet alleen PvdD.
2. Hij start een gesprek en vraagt hoe bestaand gedrag werkt.
3. De advisor onderzoekt de exacte repositoryversie en zo nodig acceptatie.
4. Marc ontvangt een brongetrouw antwoord.
5. Het gesprek wordt gesloten zonder ProductRequest, epic of story.

### Kleine tekstfout

1. Marc meldt een tikfout.
2. De advisor verifieert de fout en stelt een hotfix voor.
3. Marc ziet scope en acceptatiecriterium en bevestigt de exacte versie.
4. Product Factory maakt via de bestaande Software Factory-story-API een story met `hotfix=true`.
5. Software Factory doorloopt zijn bestaande hotfixketen.
6. Product Factory toont storykey en uitkomst in het verzoek.

### Grotere bug

1. Marc meldt reproduceerbaar verkeerd gedrag.
2. De advisor stelt een gewone bugfix voor omdat de wijziging niet laag-risico genoeg is voor een
   hotfix.
3. Marc bevestigt.
4. Product Factory verstuurt de bugfix via v2 naar Software Factory.
5. Er ontstaat geen epic, maar wel een volledige Software Factory-uitvoering.

### Nieuwe productverbetering

1. Marc bespreekt een nieuwe wens met de advisor.
2. Na voldoende verkenning bevestigt hij een `EPIC_CANDIDATE`.
3. Productontwerp claimt het gerichte workitem en werkt een epic uit.
4. Een ontbrekend productantwoord verschijnt bij **Vragen aan jou** van Marc.
5. Na antwoord hervat Productontwerp dezelfde uitwerking.
6. Marc keurt de gereedstaande epicversie goed.
7. Robbert keurt dezelfde versie goed of stuurt haar met reden terug.
8. Na beide goedkeuringen wordt de epic beschikbaar voor de bestaande planning- en dispatchroute.

### Autorisatiescheiding

1. Marc probeert rechtstreeks een ander product of factoryinstelling op te vragen.
2. De backend weigert dit, onafhankelijk van de frontend.
3. Robbert kan dezelfde beheerinformatie wel openen.
4. Auditgegevens tonen wie iedere bevestiging, beantwoording en goedkeuring uitvoerde.

## Risico's en beheersing

| Risico | Beheersing binnen Product Factory |
|---|---|
| Advisor classificeert te snel als hotfix | Voorstel eerst zichtbaar bevestigen; vaste uitsluitingscategorieën in instructie en validatie. |
| AI voert ongewenst een mutatie uit | Agenttaak heeft geen mutatiecommands; alleen backendcommands na gebruikersactie. |
| Gesprekscontext wordt te groot | Duurzame berichten plus gecontroleerde samenvatting; recente berichten blijven letterlijk beschikbaar. |
| Product owner ziet gegevens van ander product | Productmembership bij iedere backendquery en ieder command afdwingen en testen. |
| Epic verandert na goedkeuring | Goedkeuring bindt aan exact versienummer; revisie maakt nieuwe goedkeuring verplicht. |
| Dubbele externe story na timeout | V2-idempotentie voor gewone bugs; Product Factory-marker en preflight lookup voor hotfix. |
| Hotfixtoken verloopt | Zichtbare tokenstatus, geen stille fallback en operationeel vernieuwingsrunbook. |
| Browsertest verandert productiegegevens | Acceptatie standaard; productie alleen binnen geconfigureerde veilige routes en grenzen. |
| Productvraag wordt onnodig ontwikkelwerk | `ProductConversation` blijft zelfstandig; request ontstaat pas bij expliciet voorstel. |

## Nog te nemen productbeslissingen

Deze punten blokkeren het ontwerpdocument niet, maar moeten vóór of tijdens de genoemde stap worden
vastgelegd:

1. Mag een product meerdere product owners hebben, en moet dan één of iedere product owner een epic
   goedkeuren? Het voorstel gaat uit van één aangewezen aanvrager/goedkeurder per request.
2. Mag Robbert als factory owner namens een tijdelijk afwezige product owner antwoorden of alleen
   ondersteunen? Het voorstel staat antwoorden toe, maar geen stilzwijgende productgoedkeuring.
3. Blijven notificaties in versie één uitsluitend in-app of is e-mail meteen nodig?
4. Hoe wordt het dertigdaagse Software Factory-dashboardtoken operationeel vernieuwd? Als dat niet
   acceptabel is, moet hotfixaanmaak handmatig blijven zolang Software Factory niet mag wijzigen.
5. Mag Marc een reeds verstuurde gewone bugfix of hotfix annuleren, of is annuleren na dispatch
   uitsluitend een factory owner-actie?

## Definition of Done voor het totale voorstel

Het voorstel is gerealiseerd wanneer:

- Marc met Google kan inloggen en uitsluitend PvdD ziet;
- hij duurzame gesprekken met `PRODUCT_ADVISOR` kan voeren;
- de advisor aantoonbaar PvdD-code, actuele documentatie en de geconfigureerde applicatie kan
  onderzoeken;
- een gesprek zonder wijziging kan eindigen;
- een bevestigd verzoek exact één van de drie ontwikkelroutes volgt;
- een hotfix zonder Software Factory-wijziging als echte bestaande hotfix wordt aangemaakt;
- een gewone bugfix zonder epic via de bestaande v2-integratie wordt uitgevoerd;
- een productverbetering gericht bij Productontwerp terechtkomt;
- vragen uit de uitwerking bij Marc terechtkomen en het antwoord dezelfde sessie hervat;
- een epic pas na goedkeuring van Marc én Robbert beschikbaar wordt voor planning;
- alle autorisatie-, idempotentie-, herstel- en acceptatiescenario's automatisch zijn bewezen;
- alleen de Product Factory-repository codewijzigingen bevat;
- actuele documentatie en operationele runbooks overeenkomen met de werkelijk opgeleverde werking.

## Geraakte repositories

| Repository | Codewijziging | Gebruik |
|---|---:|---|
| `product-factory` | ja | Volledige feature, UI, domein, agents, autorisatie en integratieadapters. |
| `softwarefactory` | nee | Bestaande v2-bugfixroute en bestaande dashboard-hotfixroute. |
| `pvdd` | nee | Bestaande Git-bron, documentatie en geconfigureerde testomgeving. |
