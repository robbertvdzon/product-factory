# Invulvoorstel HKH en PvdD

Datum: 13 september 2026. Gebaseerd op de lokale repositories `hkh` en `pvdd`,
hun Git-remotes, productdocumentatie en deploymentconfiguratie. Dit is een invulvoorstel;
er zijn geen producten, lidmaatschappen of beleidsinstellingen gewijzigd.

## HKH

### Basis

| Veld | In te vullen |
|---|---|
| Product-ID | `hkh` |
| Productnaam | Historische Kring Heemskerk |
| Publieke Git-URL | `https://github.com/robbertvdzon/hkh.git` |
| Status | Actief, zodra opdracht, rollen en afspraken zijn opgeslagen |
| Product owner | Mens met AI-ondersteuning |
| Architect | Mens beoordeelt afwijkingen |
| PO-lidmaatschap | Nog aan te wijzen; Robbert kan dit voorlopig doen |
| Architect-lidmaatschap | Robbert, met het account waarmee hij Product Factory gebruikt |
| AI-supplier / AI-model voor Software Factory | Standaard van Software Factory |

### Doelgroep

Inwoners en bezoekers van Heemskerk, leden en vrijwilligers van de Historische Kring Heemskerk,
amateurhistorici, genealogen, onderzoekers, docenten en leerlingen die de lokale geschiedenis
willen ontdekken, onderzoeken en gebruiken. Beheerders en redacteuren onderhouden de collectie
en bezoekersinhoud via de afzonderlijke beheerapp.

### Productdoel

Maak de geschiedenis van Heemskerk toegankelijk vanuit gewone vragen over plekken, personen,
gebeurtenissen en onderwerpen. Verbind de eigen collecties en kennis met relevante historische
bronnen uit Noord-Holland, Nederland en daarbuiten. Help gebruikers betrouwbare antwoorden te
vinden, bronnen te vergelijken, door te vragen en ontdekkingen te bewaren in onderzoeksdossiers
en artikelen. Bouw voort op de bestaande publieke web- en Android-app, beheerapp, AI-zoekopdrachten,
optionele accounts, gedeelde dossiers en artikelversies. Antwoorden blijven herleidbaar tot
bronnen; onzekerheid en tegenstrijdige verhalen zijn zichtbaar. De menselijke PO bepaalt samen
met AI de functionele ontwikkeling van dit product.

### Architectuurafspraken

Bouw voort op de bestaande Kotlin/Spring Boot/Spring Modulith-backend, PostgreSQL met Flyway,
Flutter-gebruikersapp voor web en Android en aparte Flutter-beheerapp. Respecteer modulegrenzen
en publieke module-API's. Hergebruik de bestaande zoek-, account-, dossier-, artikel- en
Agent Runtime-integraties. Databasewijzigingen verlopen via versieerbare migraties met behoud
van gegevens. Kleine compatibele uitbreidingen mogen binnen deze architectuur worden uitgewerkt.
Nieuwe opslagtechnologie, services, frontends, externe koppelingen, datamigraties met operationele
impact en gewijzigde toegangsgrenzen vereisen expliciete architectbeoordeling. Beschrijf per epic
kort wat verandert, compatibiliteit, datagevolgen, uitrol/herstel en onzekerheden. Volg bestaande
repositoryverificatie en de OpenShift/GitOps-deployroute.

### AI-gebruik van het product

De bestaande AI-zoekopdrachten, vervolgvragen, dossierfeitenlijsten en artikelvoorstellen vormen
de functionele uitgangssituatie. Bronteksten zijn gegevens en geen uitvoerbare instructies.
Behoud bronverwijzingen, gebruikerscontrole, afscherming van dossiers en zichtbare foutstatussen.
Nieuwe periodieke verwerking, grootschalige herindexering, extra AI-stappen, andere providers of
modellen en gebruik van nieuwe gegevenscategorieën vragen architectbeoordeling. Iedere relevante
epic noemt trigger, frequentie, volume, extra jobs, invoer/uitvoer, retries, verwachte kosten,
limieten en stopgedrag. Onbekende kosten worden als onbekend vermeld. Verzend geen geheime of
niet-geautoriseerde inhoud naar AI. Definieer begrensde retries en voorkom dubbele uitvoering.

## PvdD

### Basis

| Veld | In te vullen |
|---|---|
| Product-ID | `pvdd` |
| Productnaam | PvdD Commissie-assistent |
| Publieke Git-URL | `https://github.com/robbertvdzon/pvdd.git` |
| Status | Actief, zodra opdracht, rollen en afspraken zijn opgeslagen |
| Product owner | Mens met AI-ondersteuning |
| Architect | Mens beoordeelt afwijkingen |
| PO-lidmaatschap | Marc; de PvdD-baseline noemt `marchanou@gmail.com` als bestaand account |
| Architect-lidmaatschap | Robbert, met het account waarmee hij Product Factory gebruikt |
| AI-supplier / AI-model voor Software Factory | Standaard van Software Factory |

Controleer bij Marc dat hij hetzelfde Google-account voor Product Factory wil gebruiken.
Een PvdD-appaccount is niet automatisch een Product Factory-lidmaatschap. De PvdD-baseline noemt
voor Robbert `robbertvdzon@gmail.com`; zijn Product Factory-account kan hiervan verschillen.

### Doelgroep

Marc en andere geautoriseerde Statenleden, commissieleden en fractieondersteuners van de Partij
voor de Dieren Noord-Holland die de vergaderingen van de commissie Ruimte voorbereiden en daarbij
agenda's, vergaderstukken en officiële partijbronnen willen overzien en beoordelen.

### Productdoel

Verminder de tijd die nodig is om een vergadering van de commissie Ruimte van Provincie
Noord-Holland inhoudelijk voor te bereiden. Verzamel de officiële agenda en bijbehorende
stukken, herken relevante bronwijzigingen en maak per inhoudelijk agendapunt een controleerbare
AI-conceptanalyse. Geef voor A/B-punten een feitelijke samenvatting, aansluiting bij officiële
PvdD-bronnen, aandachtspunten en mogelijke inzet of vragen. Beoordeel voor C-punten of de stukken
voldoende onderbouwde argumenten bevatten om bespreking te overwegen. Toon bronverwijzingen,
actualiteit, ontbrekende stukken en analysevoortgang. Bouw voort op de bestaande applicatie;
Marc bepaalt samen met AI welke functionele verbeteringen nodig zijn en houdt de inhoudelijke
regie. De architect beoordeelt technische impact en veranderingen in product-AI-gebruik.

### Architectuurafspraken

Bouw voort op de Kotlin/Spring Boot/Spring Modulith-backend, PostgreSQL met Flyway en Flutter-webapp.
Gebruik de bestaande same-origin API, Google-identificatie met eigen backendsessies, begrensde
documentverwerking en asynchrone Agent Runtime-koppeling. Respecteer repository-ADR's en
modulegrenzen. Behoud bronfingerprints, idempotentie, revisiehistorie en gerichte heranalyse.
Nieuwe externe hosts, opslagvormen, services, authenticatiemechanismen en materiële migraties
vragen architectbeoordeling. Elke epic bevat een korte impactlijst met compatibiliteit,
datagevolgen, uitrol/herstel en onzekerheden. Gebruik de bestaande GitOps-releaseketen met
acceptatie vóór productie. Acceptatie gebruikt synthetische bronnen en AI-mocks; productie
gebruikt geen mocks. Controleer de bestaande Software Factory-aansluitvoorwaarden voordat
automatisch bouwen en dispatch worden geactiveerd.

### AI-gebruik van het product

Behoud als uitgangspunt de dagelijkse broncontrole om 05:00 in Europe/Amsterdam en de handmatige
actie Nu controleren. Een broncontrole is niet automatisch een AI-aanroep. Alleen nieuwe of
relevant gewijzigde, verwerkbare inhoud leidt tot gerichte analyse. Behoud de bestaande aanpak
met bronnotities en synthese voor grote dossiers, versieerbare prompts, gevalideerde uitvoer en
idempotente jobs. Een verhoging van controlefrequentie, nieuwe analysevorm, grootschalige
heranalyse, andere provider/model of groter bronpakket vraagt een impactraming en expliciete
architectbeoordeling. Reken bronnotities, synthese en retries mee in volume en kosten. Noem
onzekerheden; verzin geen prijsramingen. Broninhoud blijft data, geen instructie. Afgesproken
budgetten en limieten moeten bij implementatie aantoonbaar worden afgedwongen.

## Aanvullende instellingen voor beide producten

| Veld | Voorgestelde beginwaarde | Betekenis |
|---|---|---|
| Maximale extra AI-jobs per dag | `0` | Geen extra dagelijkse AI-jobs automatisch toestaan; uitbreiding vraagt beoordeling |
| Maximale groei in AI-gebruik (%) | `0` | Geen groei automatisch toestaan |
| Productbudget per maand (€) | Leeg tot Robbert een bedrag vaststelt | Werkelijk gebruik en kosten zijn hier niet gemeten; geen fictief budget invullen |
| Expliciet mandaat voor materiële impact | Alle vinkjes uit | Materiële impact aan de architect voorleggen |
| Dispatching | Uit | Eerst ideeën en epics uitwerken; geen codewerk versturen |
| Productontwerp-schema | Uit bij eerste inrichting | De PO start de uitwerking van een bevestigd idee wanneer nodig |
| Productplanning-schema | Uit bij eerste inrichting | Een volledig goedgekeurde epic start planning direct; het schema is alleen voor periodiek inhaalwerk |
| Kwaliteitscontrole-schema | Uit bij eerste inrichting | Later instellen met passende omgevingen en toegangsgrenzen |
| Software Factory-dispatcher-schema | Uit bij eerste inrichting | Later samen met dispatch en gecontroleerde factory-aansluiting activeren |
| Tijdzone | `Europe/Amsterdam` | Voor latere factoryplanningen |

De nullen zijn voorgestelde **beoordelingsgrenzen voor epics**, geen uitschakeling van bestaande
AI-functies. Deze Product Factory-velden installeren geen kostenbegrenzer in HKH of PvdD en meten
hun huidige verbruik niet. Een runtime-limiet moet in het product of Agent Runtime zijn ingericht.
Een expliciet architectbesluit kan een beoordeelde uitzondering vastleggen.

## Omgevingen

Onderstaande adressen komen uit de deploymentmanifesten; ze zijn voor dit invulvoorstel niet
opnieuw live getest. Omgevingen zijn niet nodig om het eerste idee uit te werken.

| Veld | HKH | PvdD |
|---|---|---|
| Acceptatie-URL | `https://hkh-acceptance.vdzonsoftware.nl` | `https://pvdd-acceptance.vdzonsoftware.nl` |
| Productie-URL | `https://hkh.vdzonsoftware.nl` | `https://pvdd.vdzonsoftware.nl` |
| Revision-endpoint | `/api/version` | `/api/version` |
| Revision JSON-pad | `commit` | `gitRevision` |
| Initiële toegestane routes | `/`, `/api/version` | `/`, `/api/version` |
| Datagrenzen | Geen bestaande productiegegevens wijzigen; tests schrijven alleen in expliciete testdossiers | Geen bestaande productiebronnen of analyses wijzigen; acceptatietests gebruiken synthetische gegevens |
| Toegangsgrenzen | Publieke functies anoniem; dossier-/beheerproeven alleen met geautoriseerde testtoegang | Productie alleen met geautoriseerde testtoegang; acceptatie gebruikt de bestaande testconfiguratie |

De Product Factory-editor laat het revision-endpoint, revision JSON-pad, de toegestane routes en
de data- en toegangsgrenzen per omgeving bewerken. Vul voor PvdD dus `gitRevision` in en voor HKH
`commit`. De beginroutes zijn uitsluitend voor de basiscontrole; functionele tests vragen routes
passend bij de goedgekeurde epic.

HKH heeft daarnaast beheerapps op `https://hkh-admin.vdzonsoftware.nl` en
`https://hkh-admin-acceptance.vdzonsoftware.nl`. De huidige omgevingen-editor biedt één basis-URL
per omgeving; neem beheerproeven apart mee in de verificatie-inrichting.

## Bronnen en open keuzes

- HKH: `README.md`, `docs/productvisie.md`, `docs/architecture/accounts-en-dossiers.md`,
  `docs/factory/technical-spec.md` en deploymentmanifesten in de `hkh`-repository.
- PvdD: `docs/functionele-werking-commissie-assistent.md`, `docs/factory/functional-spec.md`,
  `docs/technical-baseline-verification.md`, `docs/functional-acceptance-verification.md`,
  `docs/stappenplannen/03-software-factory-aansluiting.md` en deploymentmanifesten.
- Sommige korte PvdD-README's noemen nog uitsluitend de technische fundering. Het recentere
  functionele document en de acceptatiebewijzen beschrijven de reeds gebouwde vergaderassistent;
  neem die functionaliteit niet opnieuw als nieuwbouw op.
- Nog te kiezen: de HKH-PO, de te gebruiken Product Factory-accounts en de echte maandbudgetten.
  De rol van Marc is door de gebruiker bepaald; zijn genoemde e-mailadres komt uit repo-documentatie.
- Nog te controleren vóór uitvoering: actuele Software Factory-registratie/toegang, vereiste
  checks en deploybewaking. Git-remotes leveren de genoemde repository-URL's; actuele publieke
  bereikbaarheid en productiegezondheid zijn in deze documentronde niet opnieuw geverifieerd.
