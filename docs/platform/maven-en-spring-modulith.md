# Product Factory v2 — Maven en Spring Modulith

Status: technische architectuurkeuze voor modulegrenzen en vervangbare implementaties.

Product Factory gebruikt twee niveaus van modulariteit met elk een eigen doel:

- **Maven-modules** vormen de harde grens tussen één gedeeld publiek API-contract,
  capability-implementaties en de uitvoerbare applicatie;
- **Spring Modulith** structureert en controleert uitsluitend de binnenkant van een
  implementatiemodule wanneer die intern ingewikkeld genoeg is.

Er is één uitvoerbare `product-factory-app` en één publieke Maven-module `product-factory-api`.
Daarin staan vanaf het begin alle publieke interfaces en DTO's, gegroepeerd per capabilitypackage.
Deze ene module voorkomt cyclische Maven-dependencies tussen afzonderlijke API-artifacts wanneer
capabilities over en weer commands en DTO's nodig hebben. Bij het bouwen bevat de app voor iedere
op dat moment geactiveerde capability exact één implementatie. Een contract kan in een eerdere
MVP-stap dus al bestaan zonder dat de bijbehorende capability al actief is. De applicatie draait
nooit twee Productontwerp-implementaties tegelijk.

## Hoofdstructuur

```text
product-factory-parent
│
├── product-factory-api
│   └── capabilitypackages: product, design, planning, quality, memory,
│                           ai, decisions en dispatcher
│
├── product-design-impl-mvp
├── product-design-impl-advanced
│
├── product-planning-impl-mvp
├── product-planning-impl-advanced
│
├── quality-impl-mvp
├── quality-impl-advanced
│
├── agent-memory-impl
├── ai-execution-impl
│   ├── intern Modulith-onderdeel: settings
│   └── intern Modulith-onderdeel: task-execution
├── decisions-impl
├── product-impl
├── software-factory-dispatcher-impl
│
└── product-factory-app
```

De exacte artifactnamen mogen nog veranderen. De grens is belangrijker dan de naam: iedere
capability heeft een eigen package met een klein publiek contract in `product-factory-api` en één
of meer verwisselbare implementatiemodules. Een eenvoudige capability kan maar één implementatie
hebben.

Alle publieke contracten bestaan al in de technische fundering in die ene API-module.
Implementatiemodules worden pas in hun eigen MVP-stap toegevoegd en door de app geactiveerd. Een
consumer sluit input of commands uit een latere capability pas aan zodra daarvoor een echte
provider actief is. Productie gebruikt geen no-op- of mockimplementatie om een ontbrekende
capability te verbergen.

## Dependencyregels

```text
product-factory-api        -X-> alle implementatiemodules
implementatiemodule        ──> product-factory-api
implementatiemodule        -X-> implementatiemodules van andere capabilities
product-factory-app        ──> exact één implementatie per geactiveerde capability
```

Alleen `product-factory-app` mag dependencies op implementatie-artifacts hebben. Daardoor kan code
uit Productontwerp bijvoorbeeld wel het publieke planningpackage in `product-factory-api` lezen of
commands daarop aanroepen, maar nooit een Planner-repository, Spring-bean of interne klasse
importeren.

Maven Enforcer- en architectuurtests bewaken minimaal:

- geen dependency van `product-factory-api` op een `*-impl-*` artifact;
- geen implementation-to-implementation dependency;
- exact één implementatieprovider per in die build geactiveerde capability;
- geen ontbrekende implementatieprovider voor een geactiveerde capability;
- geen API-type dat naar een implementatiepackage, JPA-entiteit of intern Spring-component verwijst.

De gedeelde API-module heft het functionele eigenaarschap niet op. Package- en contracttests
bewaken dat ieder publiek command bij precies één capability hoort en dat implementaties alleen hun
eigen duurzame objecten schrijven. Een mogelijke wederzijdse verwijzing tussen publieke DTO's is
binnen één Maven-module geen buildcyclus.

## Inhoud van de API-module

`product-factory-api` bevat alleen wat een andere Maven-module werkelijk mag kennen:

- publieke application-serviceinterfaces;
- commands en commandresultaten;
- read-only DTO's en filters;
- stabiele ID-, status- en enumtypen;
- eventueel expliciete publieke events;
- betekenisvolle publieke fouten of foutcodes.

De API-module bevat niet:

- Spring Modulith-configuratie;
- repositories of database-entiteiten;
- JPA-annotaties of migraties;
- agentrollen, prompts of interne analyses;
- interne state machines;
- concrete Spring-beans;
- implementatiespecifieke tabellen of clients.

De API-module blijft bij voorkeur ook vrij van Spring Framework. Beanregistratie, transacties,
scheduling en persistence horen bij de implementatie.

## Inhoud van een implementatiemodule

Een implementatiemodule:

- implementeert de publieke interfaces van haar capabilitypackage in `product-factory-api`;
- is enige schrijver van de duurzame objecten die bij die capability horen;
- gebruikt andere capabilities uitsluitend via hun publieke contract in `product-factory-api`;
- bevat repositories, transacties, scheduling en technische adapters die intern eigendom zijn;
- registreert haar publieke implementatiebeans via Spring Boot auto-configuration;
- verbergt alle overige packages voor de composition root en andere Maven-modules;
- legt haar implementatie-ID en versie vast op iedere nieuwe processessie.

De main-module hoeft geen concrete implementatieklasse in broncode te importeren. Het gekozen
implementation-artifact levert auto-configuration waarmee de publieke API-beans worden
geregistreerd. Voor een geactiveerde capability faalt de composition-test zonder precies één
provider en start de applicatie niet. Een nog niet geactiveerd capabilitycontract registreert geen
bean en mag in een tussenstap zonder implementatie in `product-factory-api` staan.

## Spring Modulith binnen een implementatiemodule

Spring Modulith definieert niet de harde grens tussen Productontwerp, Productplanning en
Kwaliteitsbewaking; die grens bestaat al op Maven-niveau. Spring Modulith wordt binnen een
implementatie gebruikt om de interne functionele delen overzichtelijk te houden.

Een uitgebreide Productontwerp-implementatie kan bijvoorbeeld intern zijn opgebouwd als:

```text
product-design-impl-advanced
└── nl.vdzon.productfactory.design.advanced
    ├── session
    ├── context
    ├── research
    ├── ux
    ├── epicdrafting
    └── publication
```

Deze interne application modules mogen eigen interne interfaces en events hebben. Zij zijn geen
nieuwe hoofdprocessen en hun typen verschijnen niet in het publieke `design`-package.

`ai-execution-impl` gebruikt dezelfde aanpak voor de interne onderdelen `settings` en
`task-execution`. `settings` bezit `AiJobConfiguration`; `task-execution` verwerkt alleen al
samengestelde taken met een bevroren provider en model en kiest die waarden nooit zelf. Beide horen
bij dezelfde Maven-capability en delen uitsluitend expliciete interne interfaces.

Iedere implementatiemodule krijgt haar eigen Modulith-verificatietest. Die controleert:

- geen cycli tussen interne application modules;
- toegang tot interne packages alleen volgens de vastgelegde interfaces;
- expliciet toegestane interne afhankelijkheden;
- geïsoleerde moduletests waar dat nuttig is.

De module-local verificatie gebruikt de package-root van de betreffende implementatie. De
`product-factory-app` hoeft de API-JAR's niet als Spring Modulith-modules te behandelen. Wanneer
runtime-documentatie of observability ook de interne modules moet tonen, registreert de
implementatie haar rootpackages expliciet; dit verandert de Maven-grens niet.

Een kleine implementatie hoeft niet kunstmatig veel interne Modulith-modules te krijgen. De Maven-
grens blijft ook geldig wanneer de MVP intern maar één of twee functionele packages heeft.

## Eén main-module en build-time selectie

`product-factory-app` is de enige Spring Boot composition root. Maven-profielen kiezen bij het
bouwen welke verwisselbare implementatie als dependency wordt opgenomen, bijvoorbeeld:

```text
mvn verify -Pproduct-design-mvp
mvn verify -Pproduct-design-advanced
```

De profielnamen en commando's zijn nog geen definitief buildcontract, maar deze invarianten staan
vast:

- de keuze gebeurt tijdens de build, niet met een runtime-switch in de database;
- precies één Productontwerp-implementatie staat op de runtime-classpath;
- een build met nul of twee Productontwerp-implementaties faalt;
- dezelfde regel geldt wanneer later ook Planning of Kwaliteitsbewaking verwisselbaar wordt;
- het gebouwde artifact bevat een leesbaar `ImplementationManifest` met artifact, variant, versie
  en broncommit per capability.

De UI toont dit manifest read-only in de operationele en acceptatieweergave. Daardoor is altijd
zichtbaar of een sessie door bijvoorbeeld `product-design-mvp` of `product-design-advanced` is
gemaakt.

## MVP vervangen en terug kunnen schakelen

De MVP en uitgebreide implementatie gebruiken exact dezelfde publieke API. Productplanning,
Kwaliteitsbewaking, frontend en andere consumers veranderen daarom niet wanneer Productontwerp wordt
vervangen.

Terugschakelen naar MVP gebeurt door een nieuwe appbuild met de MVP-dependency te deployen. Het is
geen runtime-toggle en er draaien niet tijdelijk twee schrijvers op dezelfde productdata.

Om terugschakelen veilig te houden:

- gebruiken beide implementaties hetzelfde duurzame schema en dezelfde betekenis voor publieke
  entiteiten zoals `Epic` en `ProcessSession`;
- zijn migraties additief zolang terugschakelen ondersteund moet blijven;
- verwijdert of hernoemt de uitgebreide implementatie geen tabel of kolom die de MVP nodig heeft;
- mogen implementatie-private tabellen worden toegevoegd en door de andere variant worden genegeerd;
- bevat iedere processessie `implementationId`, `implementationVersion` en de relevante
  inputversies;
- worden lopende processessies vóór een implementatiewissel afgerond of gecontroleerd beëindigd;
- hervat een andere implementatie nooit blind een `WAITING_FOR_AI`-sessie van een onbekende
  implementatieversie.

Als de interne modellen later zo sterk uiteenlopen dat één duurzaam schema niet meer verstandig is,
is een expliciete datamigratie nodig. Dat verschil mag niet achter dezelfde API worden verborgen
alsof terugschakelen gratis is.

## Vergelijken op acceptatie

Er is maar één applicatie tegelijk nodig. Product Factory Testbed maakt een eerlijke sequentiële
vergelijking mogelijk:

1. deploy de build met de MVP-implementatie;
2. reset acceptatie naar een bekende dataset- en scenarioversie;
3. voer de vaste scenario's uit en exporteer de publieke resultaten en operationele metingen;
4. deploy de build met de uitgebreide implementatie;
5. reset naar exact dezelfde dataset- en scenarioversie;
6. voer dezelfde scenario's opnieuw uit;
7. vergelijk epics, UX, fouten, doorlooptijd, AI-taken en overige vooraf gekozen criteria.

Het vergelijkingsrapport staat buiten de productwaarheid van beide runs. De uitgebreide
implementatie wordt niet automatisch de winnaar: als de MVP betere of betrouwbaardere output geeft,
kan de volgende productiebuild opnieuw de MVP selecteren.

## Testlagen

Iedere verwisselbare capability krijgt bij voorkeur een gedeelde contracttestkit naast
`product-factory-api`. Zowel MVP als uitgebreid moet daarmee dezelfde publieke invarianten
bewijzen, bijvoorbeeld:

- dezelfde commands, conflictsituaties en idempotentie;
- dezelfde publieke statussen en DTO-betekenis;
- dezelfde eigenaarschaps- en read-only grenzen;
- maximaal één onafgeronde logische uitvoering per product, met parallelle uitvoering voor
  verschillende producten;
- correcte implementatie-identiteit op nieuwe sessies.

Daarbovenop bestaan:

- implementatiespecifieke unit- en Spring Modulith-tests;
- repository- en migratiecompatibiliteitstests;
- één app-compositiontest die exact één provider per API controleert;
- integratietests met Product Factory Testbed;
- sequentiële UI-acceptatiescenario's tegen dezelfde dataset.

## Invarianten

- `product-factory-api` heeft geen Spring Modulith en kent geen implementaties.
- Alleen de main-module heeft Maven-dependencies op implementatiemodules.
- Implementatiemodules lezen alle publieke capabilitycontracten uitsluitend via
  `product-factory-api`.
- Er is precies één publieke API-module; er bestaan geen onderling afhankelijke capability-API-
  artifacts.
- Spring Modulith moduleert alleen de binnenkant van een implementatiemodule.
- Eén appbuild bevat per capability exact één actieve implementatie.
- De implementatiekeuze is buildinformatie en geen productinstelling.
- MVP en uitgebreid mogen nooit gelijktijdig dezelfde productentiteiten schrijven.
- Publieke contracts en duurzame data blijven compatibel zolang terugschakelen wordt ondersteund.
- Iedere sessie is herleidbaar tot de exacte implementatie en versie.
- Dezelfde acceptatiescenario's kunnen na elkaar tegen verschillende builds worden uitgevoerd.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](integratie-en-acceptatietesten.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productontwerp — MVP](../processen/productontwerp/mvp.md)
- [Productontwerp — uitgebreide implementatie](../processen/productontwerp/uitgebreid.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
