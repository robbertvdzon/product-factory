# Technische basis

Dit document beschrijft de blijvende technische contracten voor de nieuwe Product Factory. Het is
geen uitvoeringsplan en bevat geen proces-, agent- of productlogica. De concrete bouwvolgorde staat
in [Stap 1 — Technische fundering](../stappenplannen/01-technische-fundering.md).

Het actuele sleutelcontract en de aantoonbare configuratieprioriteit staan in
[Configuratie en secrets](configuratie-en-secrets.md).

De nieuwe applicatie heet in code, configuratie en gebruikersinterface gewoon **Product Factory**.
`v2` is geen onderdeel van Maven-artifactnamen, packages, URLs, modulenamen of schermteksten. Alleen
een technisch geïsoleerde overgangsdatabase of PVC mag tijdelijk `_v2` in de naam dragen.

## Technologiebasis en versies

De backend gebruikt Java, Kotlin, Spring Boot, Maven en, binnen implementatiemodules, Spring
Modulith. De definitieve modulegrenzen staan in
[Maven en Spring Modulith](maven-en-spring-modulith.md). De webfrontend wordt volledig opnieuw
opgezet; v1-widgets, schermindeling en frontendcode zijn geen uitgangspunt.

Voor alle gebruikte toolchains gelden deze regels:

- versies worden centraal en expliciet vastgelegd;
- lokaal bouwen, CI en de Docker-build gebruiken compatibele Java-, Maven-, Kotlin-, Flutter- en
  Dartversies;
- een lockfile hoort bij de gekozen frontendtoolchain en wordt niet door een andere toolchain
  stilzwijgend herschreven;
- basisimages gebruiken een gecontroleerde versie of digest en geen onbegrensde `latest`-tag;
- een wijziging van een toolchainversie controleert ook de productieachtige containerbuild;
- afhankelijkheden en toolchains worden bewust bijgewerkt; een toevallige lokale upgrade bepaalt
  niet de productieversie.

De exacte versienummers worden bij de eerste implementatie centraal in de build vastgelegd. Dit
document neemt niet automatisch de v1-versies over.

## Configuratie

Lokale applicatieconfiguratie wordt in deze volgorde samengevoegd, waarbij een latere bron
voorrang heeft:

1. `properties.default.env` met commitbare, niet-geheime defaults;
2. `properties.env` met gitignored lokale overrides;
3. `secrets.env` met gitignored lokale secrets;
4. echte proces-environmentvariabelen voor CI, containers en OpenShift.

Alleen environmentvariabelen met de gekozen Product Factory-prefix worden als applicatieconfig
ingelezen. De configuratielader valideert namen en waarden en meldt bij startup concreet welke
verplichte sleutel ontbreekt, zonder secretwaarden te loggen.

`secrets.env` blijft tijdens de vervanging in de repositoryroot staan. Het bestand wordt niet door
Git gevolgd en wordt geleidelijk aangepast wanneer velden bijkomen, hernoemd worden of verdwijnen.
`secrets.env.example` bevat dezelfde sleutels met uitleg en veilige voorbeeldwaarden, maar nooit
echte credentials.

## Secrets

Productie ontvangt secrets via een Kubernetes Secret dat uit een gecommit Sealed Secret ontstaat.
Het onversleutelde bronbestand wordt nooit gecommit. Het seal-script:

- gebruikt standaard het lokale `secrets.env` als bron;
- heeft een gesloten lijst met verplichte sleutels;
- stopt als een verplichte waarde ontbreekt;
- gebruikt tijdelijke bestanden met beperkte rechten;
- verwijdert tijdelijke plaintext ook wanneer een stap faalt;
- schrijft uitsluitend het versleutelde manifest als commitbare output.

Lokale ontwikkeling, acceptatie en productie gebruiken verschillende credentials. Een
secretwijziging wijzigt niet vanzelf de podtemplate; de deploymentprocedure bevat daarom een
bewuste rollout van de deployments die de gewijzigde waarde lezen.

Algemene niet-geheime instellingen, zoals modelkeuzes en publieke Git-URLs, horen later in de
database. Wachtwoorden, sessiesleutels en externe tokens horen daar niet in.

## Database

### Productie

Productie gebruikt een nieuwe PostgreSQL-database, een nieuwe databasegebruiker, een nieuw
wachtwoord en een nieuwe persistente volumeclaim. Tijdens de overgang mogen database en PVC een
herkenbare naam zoals `productfactory_v2` dragen. Binnen de aparte database gebruikt Product
Factory het normale schema `public`.

De nieuwe applicatie:

- gebruikt geen v1-tabellen;
- kopieert geen v1-Flywaymigraties of Flyway-history;
- begint haar eigen migratiereeks bij `V1`;
- maakt alleen tabellen die de nieuwe implementatie werkelijk gebruikt;
- migreert standaard geen functionele v1-data;
- gebruikt een begrensde connectionpool en expliciete timeouts;
- stopt wanneer de database of een verplichte migratie niet veilig beschikbaar is.

De oude productiedatabase en PVC worden niet door de nieuwe migraties aangepast. Verwijdering
daarvan is een afzonderlijke, expliciete opruimactie nadat een laatste backup is gecontroleerd.

### Acceptatie en integratietests

Acceptatie gebruikt een resetbare in-memory database met vaste synthetische data. Integratietests
gebruiken eveneens een nieuwe in-memory database per test of testsuite. De relevante nieuwe
migraties worden daar ook uitgevoerd. Omdat een in-memory database niet alle PostgreSQLdetails
bewijst, controleert een gerichte productieachtige migratietest daarnaast een echte tijdelijke
PostgreSQL-database.

De verdere regels voor datasets, mocks en reset staan in
[Integratie- en acceptatietesten](integratie-en-acceptatietesten.md).

## Flywayveiligheid

Productiemigraties falen gesloten:

- `Flyway clean` is in productie uitgeschakeld en niet bereikbaar via foutafhandeling;
- een validatie- of checksummismatch verwijdert of herschrijft geen productiedata;
- een mislukte migratie stopt startup en wordt als operationele fout zichtbaar;
- productie voert geen automatische schema-reparatie uit;
- migraties zijn voorwaarts gericht en horen bij de applicatieversie die wordt gedeployed.

Een destructieve reset is alleen toegestaan voor een aantoonbaar wegwerpbare testomgeving. De
implementatie controleert dan de daadwerkelijk gebruikte datasource en het toegestane schema; een
omgevingsnaam of profiel alleen is geen voldoende beveiliging. Voor de normale in-memory
acceptatiedatabase is een nieuwe lege database per reset de voorkeursroute.

## Authenticatie en sessies

De Stakeholder gebruikt in productie Google-login. De backend, niet de frontend, is de
autoritatieve beveiligingsgrens.

De backend controleert bij een Google ID-token minimaal:

- de handtekening via de officiële Google JWK-set;
- de toegestane issuer;
- de geconfigureerde audience/client-id;
- de verloopdatum;
- `email_verified`;
- het e-mailadres tegen een expliciete allowlist.

Na geldige login geeft Product Factory een eigen, begrensde sessie uit. De voorkeursvorm voor de
webapp is een `Secure`, `HttpOnly`, `SameSite` cookie. Het lokale profiel mag alleen expliciet een
niet-secure variant gebruiken. Cookieauthenticatie controleert toegestane origins en beschermt
muterende requests tegen CSRF.

Alle productdata en muterende routes zijn standaard beveiligd. Alleen login, logout, health en een
beperkte versiequery mogen publiek zijn. Productie start niet wanneer authenticatie verplicht is
maar client-id, allowlist of sessiesleutel ontbreekt. Tokens, cookies en volledige persoonlijke
profielen worden niet gelogd.

Acceptatie schakelt authenticatie expliciet uit en toont dat op iedere pagina. Productie kan niet
met het acceptatieprofiel, uitgeschakelde authenticatie of synthetische testbediening starten.

Het frontendgedrag voor login, cache en versies staat in [Frontend](../stakeholder/frontend.md).

## Afbakening

Dit document voegt geen extra eisen uit v1 toe voor:

- revisiongebonden Factory-bewijs of worktree-attestatie;
- oude statusmachines, storyafhankelijkheden of agentbeveiligingsconstructies;
- v1-frontendwidgets, schermopbouw of toegankelijkheidstestsets;
- PR-previewdatabases of v1-compatibiliteitslagen.

Die onderdelen worden niet als technische erfenis meegenomen. Bestaande nieuwe proces- en
testdocumenten blijven zelfstandig normatief voor hun eigen onderwerp.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Stap 1 — Technische fundering](../stappenplannen/01-technische-fundering.md)
- [Deployment en operatie](deployment-en-operatie.md)
- [Frontend](../stakeholder/frontend.md)
- [Maven en Spring Modulith](maven-en-spring-modulith.md)
- [Integratie- en acceptatietesten](integratie-en-acceptatietesten.md)
