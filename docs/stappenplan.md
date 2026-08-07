# Stappenplan Product Factory en HKH

## 1. Doel

We bouwen vier zelfstandig inzetbare systemen en één afzonderlijk productdossier:

1. **Software Factory** — voert softwarestories uit: refinen, plannen, ontwikkelen, reviewen,
   testen, documenteren, mergen en deployen.
2. **Product Factory** — onderzoekt wat een product nodig heeft, maakt product- en UX-beslissingen,
   schrijft stories, beantwoordt productvragen en evalueert het resultaat.
3. **HKH** — de variant waarvan de productontwikkeling na de gezamenlijke baseline door de eigenaar
   wordt gestuurd.
4. **HKH Autopilot** — de variant waarvan de productontwikkeling na diezelfde baseline autonoom door
   Product Factory en Software Factory wordt uitgevoerd.
5. **Product Factory Workspace** — een Git-repository met de goedgekeurde onderzoeks-, UX-,
   beslis-, roadmap- en storyartefacten per product. Dit is geen applicatie en geen Software
   Factory-buildtarget.

Beide HKH-varianten starten vanaf inhoudelijk en technisch dezelfde baseline: backend,
gebruikersfrontend, adminfrontend, database, CI, APK en OpenShift-deployment. Alleen identifiers die
moeten verschillen om beide varianten naast elkaar te draaien — namespace, image, URL, database,
OAuth-configuratie en Android-package-id — mogen afwijken.

HKH Autopilot is de eerste praktijktest voor autonome productontwikkeling, maar Product Factory
bevat geen HKH-specifieke businesslogica. Een volgend product moet via configuratie en productdata
kunnen worden toegevoegd, zonder nieuwe Product Factory-code te schrijven.

## 2. Volgorde op hoofdlijnen

| Fase | Resultaat |
|---|---|
| 0 | De drie applicatierepositories zijn veilig bouwbaar en de workspace is ingericht |
| 1 | HKH en HKH Autopilot hebben aantoonbaar dezelfde werkende technische basis |
| 2 | Product Factory heeft een zelfstandige technische basis en een veilige workspace-koppeling |
| 3 | Eén Cloudflare-wildcard routeert productie en previews; beide HKH-varianten hebben branchomgevingen, een eerste databasefeature en een gekozen opslagpad |
| 4 | Product Factory kan stories aanbieden en volgen via een stabiele API |
| 5 | Product Factory ondersteunt meerdere configureerbare producten |
| 6 | Productagents doen onderzoek en ontwerpen in shadow mode |
| 7 | Product Factory maakt en begeleidt autonoom kleine stories |
| 8 | De gelijke baseline wordt bevroren en de twee ontwikkelpaden worden gesplitst |
| 9 | De eigenaar ontwikkelt HKH; Product Factory ontwikkelt HKH Autopilot en vergelijkt resultaten |

Elke fase moet aantoonbaar werken voordat de volgende autonomie krijgt. Technische autonomie wordt
stapsgewijs vrijgegeven; het productdoel blijft vanaf het begin algemeen.

## 3. Vaste grenzen

### Product Factory doet wel

- product- en gebruikersonderzoek;
- onderzoek naar databronnen en vergelijkbare toepassingen;
- kansen, hypotheses en productbeslissingen bijhouden;
- UX-flows, wireframes en prototypes maken;
- maximaal drie kleine stories per productiteratie voorstellen;
- stories via een machine-API bij Software Factory indienen;
- story-, subtaak-, fout-, vraag- en deploystatus volgen;
- productvragen zelfstandig beantwoorden;
- resultaten evalueren en productgeheugen bijwerken;
- goedgekeurde productartefacten naar `product-factory-workspace` committen;
- alleen bij een noodzakelijke externe handeling een mens inschakelen.

### Product Factory doet niet

- rechtstreeks code of documentatie in een productrepository wijzigen;
- commits, pull requests of merges uitvoeren buiten `product-factory-workspace`;
- zelf applicatiedeployments uitvoeren;
- rechtstreeks de database van Software Factory lezen of wijzigen;
- ontwikkelvragen door dezelfde agent laten beantwoorden die de wijziging bouwt;
- historische feiten zonder herleidbare bron als waarheid publiceren;
- accounts, betaalde diensten, juridische overeenkomsten of secrets namens een mens regelen.

### Integratiegrens

Product Factory en Software Factory delen geen Kotlin-code, database of runtime. De koppeling is
een versievaste HTTP/OpenAPI-interface. Product Factory bewaart alleen de externe Software
Factory-storykey en de voor monitoring noodzakelijke snapshots.

### Opslaggrens

- `product-factory` bevat uitsluitend de code, configuratiesjablonen en technische documentatie van
  de Product Factory zelf;
- `product-factory-workspace` is de leesbare en versieerbare bron voor goedgekeurde
  productartefacten;
- de Product Factory-database bevat operationele toestand: runs, planning, locks, retries, status,
  kosten, externe storykeys en verwijzingen naar workspace-pad en commit-SHA;
- tijdelijke agentresultaten blijven in de database totdat de criticus ze accepteert of verwerpt;
- grote binaire artefacten worden vooralsnog niet door Product Factory opgeslagen; als daar later
  een concrete behoefte voor ontstaat, volgt daarvoor een afzonderlijke opslagbeslissing;
- Software Factory beheert zijn eigen uitvoeringsdata en schrijft via stories in de
  productrepositories.

Een agentrun maakt niet voor iedere gedachte een commit. Na validatie door de criticus publiceert
Product Factory één samenhangende workspace-wijziging. De database en workspace verwijzen via een
run-ID en commit-SHA wederzijds naar elkaar.

## 4. Algemene productvisie voor HKH

> De HKH-app maakt de geschiedenis en het erfgoed van Heemskerk toegankelijk, vindbaar en
> beleefbaar. De app verbindt mensen met historische plaatsen, personen, gebeurtenissen, verhalen
> en bronnen en helpt hen de omgeving, thuis en onderweg, op een betekenisvolle en verrassende
> manier te ontdekken. De precieze vormen van zoeken, ontdekken en beleven worden iteratief
> ontwikkeld op basis van onderzoek, beschikbare bronnen en gebruik.

Deze tekst stuurt de Product Factory, maar schrijft nog geen kaart, camera, AR, chatbot, route of
andere specifieke oplossing voor. Dezelfde visie geldt na de splitsing voor zowel `hkh` als
`hkh-autopilot`, zodat het verschil in ontwikkeling niet door een andere beginopdracht wordt
veroorzaakt.

## 5. Fase 0 — repositories bouwbaar maken

### Doel

`hkh`, `hkh-autopilot` en `product-factory` kunnen veilig als zelfstandige targetrepositories door
Software Factory worden gebruikt. `product-factory-workspace` is afzonderlijk ingericht voor
menselijk leesbare productartefacten, zonder applicatiebuild of deployment.

### Werk

Voor de drie applicatierepositories:

- basisdocumentatie en repositoryconventies toevoegen;
- `.factory/verification.yaml` toevoegen met revisiongebonden verificatiecommando's;
- GitHub Actions-workflow toevoegen met een altijd aanwezige check genaamd
  `Repository verification`;
- branch/buildbeleid documenteren;
- Maven-, Flutter- en Docker-caches gebruiken waar relevant;
- secrets uitsluitend via lokale of platformconfiguratie aanbieden;
- een docs-skeleton aanmaken voor architectuur, ontwikkeling, deployment en stories.

Voor `product-factory-workspace`:

```text
product-factory-workspace/
├── README.md
├── products/
│   ├── hkh/
│   │   ├── product-vision.md
│   │   ├── roadmap.md
│   │   ├── research/
│   │   ├── ux/
│   │   ├── decisions/
│   │   ├── stories/
│   │   └── evaluations/
│   └── hkh-autopilot/
│       ├── product-vision.md
│       ├── roadmap.md
│       ├── research/
│       ├── ux/
│       ├── decisions/
│       ├── stories/
│       └── evaluations/
├── shared/
│   ├── templates/
│   └── research-methods/
└── .github/workflows/workspace-validation.yml
```

- Markdowntemplates met verplichte metadata voor productslug, artefacttype, run-ID, datum, status
  en bronnen toevoegen;
- link-, structuur-, metadata- en duplicate-ID-validatie toevoegen;
- documenteren dat `products/hkh` door de eigenaar wordt beheerd en
  `products/hkh-autopilot` door Product Factory;
- geen Maven-, Flutter-, container- of OpenShift-build toevoegen;
- de repository niet opnemen in `projects.yaml` van Software Factory.

### Definition of done

- Een kleine README-wijziging kan door Software Factory worden opgepakt.
- De PR krijgt de check `Repository verification`.
- De check is groen en bewijsbaar gekoppeld aan de actuele PR-head.
- Software Factory kan de PR automatisch mergen.
- De deploysubtaak wordt voorlopig bewust en zichtbaar overgeslagen.
- De workspace is rechtstreeks als Markdown te bekijken en heeft een groene, snelle
  `Workspace validation`-check.
- Een workspace-wijziging start geen build of deployment van een applicatie.

> Mogelijke eenmalige handeling: als GitHub een workflow die voor het eerst in dezelfde PR wordt
> toegevoegd niet als vereiste check accepteert, moet alleen de eerste bootstrap-PR handmatig
> worden gemerged. Vanaf de daaropvolgende story is de normale automatische poort actief.

## 6. Fase 1 — gelijke technische basis voor HKH en HKH Autopilot

### Doel

Een lege maar end-to-end werkende applicatiebasis die als hetzelfde vertrekpunt in `hkh` en
`hkh-autopilot` staat voordat de productontwikkeling uiteen gaat lopen. De structurele referentie
is de repository `personal-news-feed-by-claude-code`, die in Software Factory als `personal-feed`
is geregistreerd. Bij de bootstrap wordt de gebruikte referentiecommit vastgelegd; latere
wijzigingen aan Personal News Feed veranderen de HKH-baseline niet stilzwijgend.

We volgen het architectuurpatroon en de ontwikkelconventies, niet de nieuwsfeed-businesslogica,
data of productnaam.

### Gewenste structuur

```text
hkh/ en hkh-autopilot/
├── backend/                 Kotlin, Spring Boot, Spring Modulith, Maven en JDK 21
├── frontend/                Flutter gebruikersapp: web en Android
├── frontend-admin/          afzonderlijke Flutter web-admin
├── deploy/                  OpenShift/Kustomize/ArgoCD-manifests
├── docker-compose.dev.yml   lokale PostgreSQL
├── secrets.env.example      commitbaar secretsjabloon zonder echte waarden
├── secrets.env              lokale secrets, verplicht gitignored
├── docs/
├── .factory/
└── .github/workflows/
```

De mapnamen zijn HKH-specifiek, maar de componentgrenzen volgen Personal News Feed: één backend,
afzonderlijke Flutter-apps, zelfstandige componentbuilds, downloadbare APK, containerimages en een
GitOps-deploymentstructuur. We nemen geen toevallige historische nesting of nieuwsfeednamen over.

### Verplichte backendarchitectuur

- Kotlin, JDK 21, Maven, Spring Boot en Spring Modulith;
- één `@Modulithic` Spring Boot-entrypoint;
- top-level packages zijn bedrijfsmodules, geen globale lagen;
- iedere module groepeert waar nodig `api`, `domain` en `infrastructure` onder zijn eigen grens;
- module-API's en events zijn de enige toegestane koppeling tussen modules;
- database-implementaties en externe clients blijven achter een modulepoort;
- `ApplicationModules`-architectuurtest draait in iedere `mvn verify` en accepteert geen bekende
  overtredingen als startschuld;
- unit-, integratie- en end-to-endtests volgen hetzelfde testpatroon als Personal News Feed:
  de echte applicatie starten en alleen externe afhankelijkheden vervangen;
- een document `docs/architecture/reference-baseline.md` bevat repository, commit-SHA,
  overgenomen patronen en iedere bewust gekozen afwijking.

### Secrets en lokale configuratie

- beide repositories gebruiken in de root exact `secrets.env` voor lokale secrets;
- `secrets.env` en `deploy/secrets-cluster.env` zijn gitignored;
- `secrets.env.example` en `deploy/secrets-cluster.env.example` worden zonder echte waarden
  gecommit en documenteren alle ondersteunde sleutels;
- proces-environmentvariabelen hebben voorrang op `secrets.env`, zodat dezelfde applicatie zonder
  lokaal bestand in CI en OpenShift draait;
- het env-bestand wordt als data geparseerd en nooit met `source` of `export $(cat ...)` als
  shellcode uitgevoerd;
- niet-geheime defaults staan in Spring-configuratie; secrets krijgen geen bruikbare
  productiedefault;
- lokale startcommando's laden `secrets.env` automatisch vanuit de repositoryroot;
- clustersecrets worden via dezelfde naamgeving aan OpenShift aangeboden en nooit als plaintext
  manifest gecommit;
- beide HKH-varianten gebruiken dezelfde env-varnamen en voorbeeldbestanden; alleen waarden en
  noodzakelijke runtime-identiteit verschillen.

De huidige Personal News Feed-referentie gebruikt lokaal nog een bestand `.env`. HKH wijkt daar
bewust van af: de door ons gekozen standaard is root-`secrets.env`, overeenkomstig de
Software Factory-conventie. Deze afwijking wordt in `reference-baseline.md` vastgelegd, zodat een
latere implementatiestory niet alsnog `.env` introduceert.

### Werk in kleine stories

1. **HKH repository bootstrap**
   - Personal News Feed als gepinde structuurreferentie vastleggen;
   - rootstructuur, buildbestanden, docs en verificatie;
   - geen productfunctionaliteit.
2. **Backend-basis**
   - Kotlin/Spring Boot-applicatie met `@Modulithic`;
   - eerste expliciete bedrijfsmodules en een groene modulegrenzentest;
   - `/actuator/health` en `/api/version`;
   - OpenAPI en uniforme foutafhandeling;
   - eerste unit- en integratietest.
3. **Lokale configuratie- en secretsbasis**
   - root `secrets.env.example` en gitignored `secrets.env`;
   - veilige env-parser met environment override;
   - dezelfde variabelen voor lokale runtime, CI en OpenShift;
   - tests voor ontbrekende, ongeldige en overschreven configuratie.
4. **Database-basis**
   - PostgreSQL-configuratie;
   - Flyway;
   - lokaal via Docker Compose;
   - nog geen uitgebreid historisch datamodel.
5. **Gebruikersapp-basis**
   - Flutter-web en Android uit dezelfde codebase;
   - configurabele backend-URL;
   - startscherm, laadstatus en foutstatus;
   - verbinding met health/version.
6. **Admin-basis**
   - afzonderlijke Flutter-webapp;
   - Google OIDC aan de clientzijde en tokenverificatie in de backend;
   - e-mailallowlist/rollen;
   - nog geen inhoudelijk beheer.
7. **CI en artefacten**
   - backend-, gebruikersapp- en adminverificatie;
   - images bij een groene `main`;
   - downloadbare release-APK;
   - componenten worden alleen gebouwd als hun paden wijzigen.
8. **OpenShift-basis**
   - backend, gebruikerswebapp en adminwebapp als losse deployments;
   - configuratie en secrets buiten Git;
   - ArgoCD/Kustomize-structuur;
   - live- en versiecontrole.
9. **Baseline overzetten naar HKH Autopilot**
   - dezelfde bronstructuur, versies, tests en verificatie overnemen;
   - alleen noodzakelijke runtime-identiteit aanpassen;
   - geen productfunctionaliteit of autonome optimalisatie toevoegen.
10. **Baseline-pariteit aantonen**
   - dezelfde functionele contracttests tegen beide deployments draaien;
   - dezelfde Modulith- en secretsconfiguratietests in beide repositories draaien;
   - dependency- en toolchainversies vergelijken;
   - afwijkingen documenteren en beperken tot runtime-identiteit;
   - in beide repositories dezelfde tag `comparison-baseline-v1` zetten.

### Definition of done

- Beide varianten hebben een backend, gebruikerswebapp en adminwebapp op afzonderlijke OpenShift-
  resources.
- Beide gebruikersapps zijn als afzonderlijke APK te downloaden en naast elkaar te installeren.
- Beide admins zijn alleen na geldige Google-authenticatie bereikbaar.
- Database-migraties zijn gelijkwaardig en herhaalbaar.
- Beide backends zijn aantoonbaar Spring Moduliths en hebben geen modulegrensovertredingen.
- Beide repositories gebruiken dezelfde veilige root-`secrets.env`-conventie zonder echte secrets
  in Git.
- Dezelfde baseline-acceptatietests zijn voor beide varianten groen.
- Verschillen zijn beperkt tot gedocumenteerde runtime-identiteit.
- Een wijziging aan één component bouwt niet onnodig alle andere componenten.
- Software Factory kan build, merge en deploy betrouwbaar volgen.

Na deze fase worden de definitieve deploydoelen, livecomponenten, APK-packagegegevens en
release-retentie voor zowel `hkh` als `hkh-autopilot` in de lokale `projects.yaml` van Software
Factory geconfigureerd.

### Baseline-regel na de splitsing

Na tag `comparison-baseline-v1` worden productfeatures niet automatisch tussen beide repositories
gekopieerd. Alleen een kritieke beveiligings-, platform- of compliancefix mag bewust op beide
worden toegepast; zo'n gedeelde fix wordt in het vergelijkingslogboek gemarkeerd.

## 7. Fase 2 — technische basis Product Factory

### Doel

Een zelfstandige runtime waarvan de repository-, build-, Modulith-, agent-, dashboard-,
configuratie-, test- en deploymentopzet bewust op Software Factory is gebaseerd. Bij de bootstrap
wordt de gebruikte Software Factory-commit vastgelegd. Product Factory hergebruikt de bewezen
structuur en architectuur, maar blijft een eigen codebase zonder Maven-dependency, gedeelde
database of gedeelde runtimecomponenten.

### Gewenste structuur

```text
product-factory/
├── pom.xml
├── productfactory-contracts/
├── productfactory-common/
├── productfactory/              Spring Modulith-hoofdruntime
├── agentworker/
├── dashboard-backend/
├── dashboard-frontend/
├── architecture/
├── quality/
├── deploy/
├── docker/
├── Dockerfile.agent
├── docs/
├── tools/
├── product-factory             lokale beheer- en start-CLI
├── .factory/
├── .github/workflows/
├── properties.default.env
├── properties.env              lokale overrides, gitignored
├── secrets.env.example
└── secrets.env                 lokale secrets, gitignored
```

### Architectuurprincipes

- dezelfde root-Maven-reactoropzet als Software Factory, met gecentraliseerde versies en aparte
  modules voor contracts, common, runtime, agentworker en dashboard-backend;
- Kotlin, JDK 21, Spring Boot, Maven en Spring Modulith, initieel op dezelfde compatibele
  toolchainversies als de gepinde Software Factory-referentie;
- één `@Modulithic` entrypoint in `productfactory`;
- iedere top-level bedrijfsmodule heeft expliciete `@ApplicationModule`-metadata met
  fail-closed `allowedDependencies` en zonder wildcard;
- gedeelde module-API's worden alleen via expliciete named interfaces of contractmodules
  aangeboden;
- een `ApplicationModules.verify()`-test en aanvullende negatieve architectuurtests draaien in
  iedere Maven-verificatie;
- eigen PostgreSQL-database en Flyway-migraties;
- eigen agentworker, agentimage en agentresultaatcontract;
- eigen Google OIDC-dashboard;
- eigen OpenShift-namespace, images, secrets en versies;
- een apart Git-credential met alleen schrijfrecht op `product-factory-workspace`;
- geen Git-schrijfcredential voor `hkh`, `hkh-autopilot` of andere productrepositories;
- configuratieprefix `PF_`;
- packages onder `nl.vdzon.productfactory`;
- geen Maven-dependency op Software Factory-artifacts;
- dezelfde componentgerichte `.factory/verification.yaml`-opzet, stabiele GitHub required check,
  Docker-buildpatronen, kwaliteitsprofielen en Kustomize/ArgoCD-indeling als Software Factory;
- afwijkingen van de Software Factory-blauwdruk worden gemotiveerd in
  `docs/architecture/reference-baseline.md`.

### Configuratie en secrets

Product Factory volgt dezelfde gelaagde configuratieconventie als Software Factory:

1. `properties.default.env` — gecommit, alle niet-geheime defaults;
2. `properties.env` — gitignored, lokale niet-geheime overrides;
3. `secrets.env` — gitignored, lokale tokens, credentials en connection strings;
4. echte proces-environmentvariabelen — hoogste prioriteit voor CI en OpenShift.

Daarnaast gelden dezelfde veiligheidsregels: `secrets.env.example` is volledig maar bevat geen
echte waarden, de parser behandelt env-bestanden als data, verplichte configuratie faalt vroeg met
alleen sleutelnamen in de foutmelding, waarden worden nooit gelogd en parser/precedence hebben
gerichte tests. Product Factory krijgt een eigen implementatie onder `nl.vdzon.productfactory`;
de loadercode van Software Factory wordt niet als gedeelde library gebruikt.

### Eerste interne modules

- `product` — productdefinitie, missie en guardrails;
- `iteration` — geplande productcycli;
- `research` — bronnen en bevindingen;
- `opportunity` — kansen en hypotheses;
- `ux` — flows en ontwerp-artefacten;
- `decision` — autonome beslissingen en motivatie;
- `story` — kandidaten, prioritering en externe koppeling;
- `monitoring` — Software Factory-status volgen;
- `humanaction` — noodzakelijke menselijke handelingen;
- `agentruntime` — containers, timeouts en completion;
- `knowledge` — productgeheugen;
- `workspace` — artefacten renderen, valideren, publiceren en commit-SHA's registreren;
- `dashboard`, `config`, `support` en `web`.

De modules worden niet automatisch één-op-één gekopieerd van Software Factory. Alleen modules die
een Product Factory-verantwoordelijkheid hebben worden aangemaakt, maar hun grenzen, package-
metadata, afhankelijkheidsrichting en testborging volgen hetzelfde patroon.

### Workspace-publicatie

- ieder product krijgt een vaste directory op basis van zijn slug;
- alleen een aparte publishercomponent bezit het workspace-credential;
- agents leveren gestructureerde resultaten aan en voeren zelf geen Git-commando's uit;
- de publisher maakt een branch `product-factory/<product-slug>/<run-id>` en één samenhangende
  commit met conventionele commitnaam;
- de publisher opent een pull request, wacht op `Workspace validation` en laat deze daarna
  automatisch mergen;
- een retry gebruikt dezelfde run-ID en mag geen tweede artefact of pull request opleveren;
- de database registreert run-ID, artefactpaden, contenthash, pull request en uiteindelijke
  commit-SHA;
- bij een mergeconflict wordt opnieuw vanaf de actuele hoofdbranch opgebouwd; productinhoud wordt
  nooit stilzwijgend overschreven.

### Definition of done

- Runtime en dashboard draaien lokaal en op OpenShift.
- Database en migraties zijn zelfstandig.
- De Maven-reactor en componentverificatie hebben dezelfde vorm als Software Factory.
- De Modulith-verificatie is fail-closed en volledig groen zonder allowlist met startschuld.
- Lokale start gebruikt root-`secrets.env`; dezelfde configuratie kan volledig via environment in
  CI en OpenShift worden aangeleverd.
- Er kan handmatig een productrecord en een interne storykandidaat worden vastgelegd.
- Een goedgekeurd testartefact kan idempotent in `product-factory-workspace` worden gepubliceerd en
  vanuit het dashboard worden geopend.
- Het gebruikte credential kan aantoonbaar niet naar een productrepository schrijven.
- Er worden nog geen automatische externe stories aangemaakt.
- Software Factory kan Product Factory als normale targetrepository bouwen.

## 8. Fase 3 — wildcard-routing, branchpreviews, eerste databasefeature en opslagkeuze

### Doel en vaste volgorde

Deze fase wordt bewust in vier opeenvolgende stappen uitgevoerd:

1. gebruik één Cloudflare-wildcard voor bestaande productieapplicaties en de branchpreviews van
   Personal News Feed;
2. geef iedere pull-requestbranch van `hkh` en `hkh-autopilot` een volledige, tijdelijke
   OpenShift-namespace met vooralsnog een eigen lege database en automatische cleanup;
3. bewijs het databasepad met één kleine verticale functionaliteit: een beheerder voegt een
   laatste-nieuwsbericht toe en gebruikers zien de nieuwste berichten;
4. maak daarna de productieopslag duurzaam en geef iedere branch een eigen database die de backend
   automatisch vult met deterministische, representatieve previewdata.

LVM Storage wordt in deze fase niet geïnstalleerd. Voor stap 4 is de bestaande SSD-gebonden
`local-path`-storageclass het uitgangspunt; pas als capaciteit of gebruik daar later aanleiding toe
geeft, volgt een afzonderlijke nieuwe opslagkeuze.

### Stap 1 — één Cloudflare-wildcard

`*.vdzonsoftware.nl` wordt de algemene HTTP(S)-bestemming van de Cloudflare Tunnel en wijst naar
de interne OpenShift-ingressrouter. OpenShift kiest daarna op basis van de oorspronkelijke
hostnaam de declaratieve `Route` en service:

```text
*.vdzonsoftware.nl
        │
        ▼
router-internal-default.openshift-ingress.svc.cluster.local:80
        │
        ├── Route host=news.vdzonsoftware.nl
        │      └── frontend.personal-news-feed.svc.cluster.local
        ├── Route host=hkh.vdzonsoftware.nl
        │      └── frontend.hkh.svc.cluster.local
        └── Route host=pnf-pr-42.vdzonsoftware.nl
               └── frontend.pnf-pr-42.svc.cluster.local
```

- iedere productieapplicatie declareert `Route.spec.host` in de eigen Git-repository;
- Personal News Feed-previews krijgen eveneens een Git-managed OpenShift Route;
- nieuwe OpenShift-webapps vereisen daarna geen nieuwe Cloudflare-route;
- Cloudflare behoudt de `Host`-header en bereikt de router cluster-intern via HTTP; de publieke
  verbinding en de Cloudflare Tunnel blijven versleuteld en de betreffende OpenShift Routes staan
  voor dit interne pad op `insecureEdgeTerminationPolicy: Allow`;
- een onbekende wildcardhost heeft geen bijpassende OpenShift Route, geeft een routerfout en
  bereikt geen applicatie;
- bestaande exacte Cloudflare-routes worden pas één voor één verwijderd nadat de corresponderende
  OpenShift Route via de wildcard is getest;
- de Newsfeed-previewrouter wordt pas verwijderd nadat productie en minimaal één gelijktijdige
  Newsfeed-preview aantoonbaar via OpenShift ingress werken;
- uitzonderingen blijven mogelijk voor diensten buiten OpenShift, niet-HTTP-protocollen of een
  bewust afwijkend beveiligingsbeleid.

#### Status stap 1 — afgerond op 7 augustus 2026

- de Cloudflare Tunnel bevat alleen `*.vdzonsoftware.nl` naar
  `http://router-internal-default.openshift-ingress.svc.cluster.local:80` en de verplichte
  catch-all;
- alle bestaande productiehosts hebben een Git-managed `Route.spec.host` en zijn publiek met
  `200` getest; de Product Factory-health-API is eveneens via de wildcard getest;
- alle specifieke Cloudflare-applicatieregels zijn na een gecontroleerde canary verwijderd;
- Personal News Feed-preview PR 211 is end-to-end opgebouwd en was via
  `pnf-pr-211.vdzonsoftware.nl` `Synced`, `Healthy` en publiek bereikbaar;
- de tijdelijke PR, namespace en canary zijn na de test verwijderd;
- de oude Newsfeed-previewrouter is uit Git en OpenShift verwijderd;
- een onbekende wildcardhost geeft `503` en bereikt geen applicatie.

### Stap 2 — branchomgevingen voor beide HKH-varianten

Iedere open pull request van `hkh` en `hkh-autopilot` krijgt een eigen volledige omgeving. Met
“alle branches” wordt hier iedere branch met een open pull request bedoeld; losse, niet-gepubliceerde
lokale branches gebruiken geen clusterresources.

| Repository | Namespace | Gebruikerspreview | Adminpreview |
|---|---|---|---|
| `hkh` | `hkh-pr-<N>` | `hkh-pr-<N>.vdzonsoftware.nl` | `hkh-admin-pr-<N>.vdzonsoftware.nl` |
| `hkh-autopilot` | `hkh-autopilot-pr-<N>` | `hkh-autopilot-pr-<N>.vdzonsoftware.nl` | `hkh-autopilot-admin-pr-<N>.vdzonsoftware.nl` |

De eerste previewversie bevat per namespace:

- de backend van de pull-requestbranch;
- de Flutter-gebruikersfrontend van die branch;
- de Flutter-adminfrontend van die branch;
- een eigen PostgreSQL-pod met `emptyDir`, eigen credentials en een lege database waarop Flyway
  alle migraties uitvoert;
- expliciete OpenShift Routes voor gebruiker en admin;
- uitsluitend previewsecrets en een previewmarker die een productiedatabaseverbinding blokkeert;
- een preview-only testbeheerder. Dynamische previewhosts worden niet één voor één aan de
  productie-Google OAuth-client toegevoegd.

De ApplicationSet maakt de namespace bij een open pull request en verwijdert hem na sluiten of
mergen. Een aanvullende sweeper ruimt achtergebleven namespaces op als GitHub/ArgoCD-events zijn
gemist. Cleanup verwijdert daarmee ook de tijdelijke database, routes, secrets en testdata.

De lifecycle wordt generiek ingericht voor Newsfeed, HKH en HKH Autopilot. Daarbij gelden harde
veiligheids- en herstelregels:

- alleen namespaces met een exact toegestaan patroon én een expliciet preview-eigenaarslabel mogen
  automatisch worden verwijderd;
- de actuele open pull requests zijn de uiteindelijke bron van waarheid; alleen het ontbreken van
  een ArgoCD Application is vanwege races onvoldoende bewijs;
- vóór creatie wordt opnieuw gecontroleerd of de pull request nog open is, zodat een namespace niet
  vlak na merge opnieuw wordt aangemaakt;
- cleanup wacht een korte graceperiode en vereist twee opeenvolgende controles voordat een
  verweesde namespace wordt verwijderd;
- cleanup verwijdert namespace en eventuele externe databasebranch/-kopie als één idempotente
  lifecycle-operatie en blijft een half afgeronde cleanup opnieuw proberen;
- een periodieke reconciler controleert ook previews die al vóór de huidige controller bestonden;
- metrics en logs tonen actieve previews, verwijderde previews, mislukte cleanup en de ouderdom van
  verweesde namespaces zonder secretwaarden te loggen;
- tests simuleren expliciet sluiten tijdens provisioning, een gemist GitHub-event, een tijdelijke
  GitHub-storing, dubbele cleanup en een ongeldige namespaceprefix.

#### Status stap 2 — afgerond op 7 augustus 2026

- `hkh-previews` en `hkh-autopilot-previews` volgen iedere open pull request en maken de afgesproken
  namespaces, gebruikersroutes en adminroutes;
- beide repositories bouwen voor een pull request backend, gebruikersfrontend en adminfrontend op
  de exacte head-SHA; een post-merge build houdt `main` als branch uitgecheckt en publiceert de
  GitOps image-pins;
- iedere preview gebruikt uitsluitend een eigen `emptyDir`-PostgreSQL, vaste niet-productie-
  credentials en een previewmarker; de backend weigert previewmodus met een externe database-URL;
- de adminfrontends gebruiken in previewmodus een backend-gecontroleerde testbeheerder, zonder
  dynamische hosts aan de productie-Google OAuth-client toe te voegen;
- echte PR's 12 en 13 van zowel HKH als HKH Autopilot zijn met alle vier publieke hosts,
  backendversie, lege database, Flyway-migratie en adminlogin `Synced` en `Healthy` getest;
- de generieke `preview-reconciler` gebruikt GitHub als bron van waarheid, een expliciet
  eigenaarslabel, exacte namespacepatronen, een graceperiode, opeenvolgende observaties en een
  laatste PR-check voor delete; GitHub-fouten stoppen alle mutaties;
- de reconciler publiceert health/metrics en gestructureerde logs, heeft gerichte tests voor de
  lifecycle-races en gebruikt een door CI gepinde image-SHA;
- sluiten/mergen verwijdert eerst alle ArgoCD-resources en daarna de namespace; zes oude verweesde
  Newsfeed-namespaces (`pnf-pr-203`, `206`, `207`, `208`, `209`, `210`) zijn via dezelfde veilige
  reconciliatie opgeruimd;
- de Newsfeed Neon-labeller controleert GitHub vóór branchcreatie, verwijdert een externe branch
  pas nadat de namespace veilig weg is en is als vaste image-SHA uitgerold.

### Stap 3 — eerste verticale databasefunctionaliteit: laatste nieuws

De eerste databasefunctionaliteit blijft bewust klein en gebruikt dezelfde contracten in `hkh` en
`hkh-autopilot`:

- Flyway maakt een tabel voor laatste-nieuwsberichten met stabiele ID, titel, berichttekst,
  publicatiemoment, aanmaakmoment en maker;
- de backend biedt een publieke lees-API die berichten nieuwste-eerst teruggeeft;
- de backend biedt een beveiligde admin-API om één bericht toe te voegen;
- alleen een geldig geauthenticeerd en geautoriseerd adminaccount mag toevoegen;
- `hkh-admin` krijgt een formulier met titel en berichttekst, duidelijke validatie en een zichtbare
  succes- of foutstatus;
- de gewone HKH-app toont alle berichten nieuwste-eerst en heeft een laad-, lege en foutstatus;
- unit-, repository-, API-, Flutter- en end-to-endtests dekken toevoegen en teruglezen;
- dezelfde functionele contracttest draait tegen HKH en HKH Autopilot om de gelijke baseline te
  bewaken.

Deze functionaliteit mag in de tijdelijke previews al volledig worden gebruikt. Zolang stap 4 nog
niet klaar is, wordt de productieomgeving niet gebruikt voor nieuws dat duurzaam bewaard moet
blijven.

#### Status stap 3 — afgerond op 7 augustus 2026

- HKH en HKH Autopilot hebben dezelfde nieuwe Spring Modulith-module `news`, met een Flyway
  V2-migratie, JDBC-repository en nieuwste-eerst-sortering in PostgreSQL;
- `GET /api/news` is publiek leesbaar en `POST /api/admin/news` hergebruikt fail-closed dezelfde
  Google- en preview-authenticatie als de bestaande beheerlogin;
- beide adminapps hebben een gevalideerd formulier voor titel en berichttekst met duidelijke
  bezig-, succes- en foutstatus; beide gebruikersapps tonen laad-, lege-, fout- en gevulde
  nieuwstoestanden;
- integratietests starten echte PostgreSQL 16-containers, voeren beide Flyway-migraties uit en
  bewijzen autorisatie, validatie, toevoegen en nieuwste-eerst teruglezen; Fluttertests bewaken
  daarnaast HTTP-contracten en schermgedrag;
- PR 14 van beide repositories is op de exacte branch-SHA `Synced` en `Healthy` getest; de gedeelde
  previewcontracttest heeft in iedere eigen database een beheerbericht gepubliceerd en publiek
  teruggelezen;
- beide PR's zijn gemerged, alle productie-images zijn op de merge-SHA gepind, Flyway rapporteert
  schema V2 en de nieuwe APK's zijn gepubliceerd;
- de previewreconciler heeft na merge de twee tijdelijke PR 14-omgevingen volgens de graceperiode
  opgeruimd.

Nieuws in productie blijft tot stap 4 technisch vluchtig doordat PostgreSQL nog `emptyDir` gebruikt.
Dit is zichtbaar gemaakt in plaats van als duurzame opslag te presenteren.

### Stap 4 — persistente PostgreSQL en deterministische previewdata

De opslagkeuze voor deze stap is bewust eenvoudig:

- PostgreSQL blijft de enige datastore; foto's, scans, audio, video, S3 en andere objectstorage
  vallen buiten deze fase en worden alleen opnieuw onderzocht als daar later een concrete behoefte
  voor ontstaat;
- productie-PostgreSQL van HKH, HKH Autopilot en Product Factory krijgt een persistente PVC op de
  SSD via de bestaande `local-path`-storageclass;
- inventariseer en documenteer vóór de migratie de werkelijke SSD-locatie, capaciteit en
  herstelprocedure in een korte ADR;
- LVM Storage en een PostgreSQL-operator zijn geen voorwaarde en worden in deze stap niet
  geïnstalleerd;
- vervang de productie-`emptyDir` zonder de JDBC- en Flyway-contracten van de applicaties te
  veranderen;
- maak iedere nacht per productiedatabase een gecomprimeerde `pg_dump` met checksum naar een eigen
  map op de externe HDD, met afgesproken retentie;
- bewijs periodiek met een restore naar een tijdelijke database dat de dumps werkelijk bruikbaar
  zijn; de externe HDD is een tweede opslagmedium, maar geen bescherming tegen verlies van de hele
  fysieke locatie;
- als SQL-data later niet meer passend op de SSD kan worden opgeslagen, volgt dan pas een nieuwe
  opslagbeslissing.

Iedere pull-requestpreview krijgt een eigen kleine, verwijderbare PostgreSQL-PVC op de SSD. Dit is
geen fysieke kopie van productie en er wordt geen productie-inhoud naar previews geëxporteerd. De
database wordt opgebouwd door Flyway en daarna automatisch gevuld door de backend:

1. Flyway voert eerst alle normale schema- en datamigraties van de branch uit.
2. Een Kotlin-component `PreviewDataSeeder` start daarna alleen wanneer de deployment expliciet
   `HKH_RUNTIME_MODE=preview`, een PR-nummer en de juiste previewmarker meegeeft.
3. De seeder controleert aanvullend fail-closed dat de verbonden database een previewdatabase is;
   bij twijfel stopt de backend zonder data te wijzigen.
4. De seeder maakt vaste, inhoudelijk kloppende scenario's met stabiele ID's en datums. De eerste
   set bevat meerdere laatste-nieuwsberichten voor sortering, lange tekst en relevante randgevallen;
   latere sets kunnen bijvoorbeeld onderling verbonden personen, gebouwen, gebeurtenissen en
   bronnen bevatten.
5. Een kleine `preview_seed_history` registreert welke benoemde seedsets, zoals `news-v1` of
   `buildings-v1`, zijn toegepast. Nieuwe tabellen of velden krijgen in dezelfde wijziging een
   nieuwe of bijgewerkte seedset.
6. Iedere backendstart reconcilieert de seedsets idempotent: ontbrekende sets worden toegevoegd,
   herkenbare seedrecords mogen worden aangevuld, dubbele records ontstaan niet en handmatig via de
   app ingevoerde previewdata wordt niet overschreven.

Dezelfde seederlogica krijgt een optioneel preview-only beheerendpoint
`POST /api/admin/preview/test-data/ensure`. Dit endpoint gebruikt exact dezelfde idempotente service,
vereist preview-adminrechten en bestaat niet in productie. Automatisch seeden bij backendstart is
de normale route; het endpoint is alleen bedoeld om de verwachte testdata expliciet opnieuw te
controleren of aan te vullen.

Database, PVC, rol en credentials zijn uniek per repository en pull-requestnummer. Na iedere push
kan de branch zijn eigen nieuwe Flyway-migraties en seedversies uitvoeren. Bij het sluiten van de
pull request verwijdert de bestaande previewreconciler de volledige namespace en daarmee ook de
previewdatabase en PVC.

Testcontainers blijft daarnaast in build en CI een aparte laag: iedere integratietestrun start een
verse PostgreSQL-container en voert Flyway uit. Een volledige OpenShift-preview vervangt deze snelle
integratietests niet.

### Werk in kleine stories

1. Sluit de Cloudflare-wildcard met een tijdelijke host veilig aan op OpenShift ingress.
2. Migreer de bestaande productiehosts en Newsfeed-previews één voor één naar Git-managed
   OpenShift Routes en verwijder daarna de Newsfeed-previewrouter.
3. Maak een ApplicationSet en previewoverlay voor `hkh-pr-<N>` met backend, beide frontends en een
   eigen lege PostgreSQL-database.
4. Maak dezelfde branchomgeving voor `hkh-autopilot-pr-<N>`.
5. Repareer eerst de Newsfeed-lifecycle-race en voeg daarna generieke, idempotente cleanup en een
   sweeper voor Newsfeed en beide soorten HKH-previewnamespace toe.
6. Maak de Flyway-migratie, repository en GET/POST-API voor laatste nieuws.
7. Voeg in de HKH-admin het formulier voor een laatste-nieuwsbericht toe.
8. Toon in de HKH-gebruikersapp alle laatste-nieuwsberichten nieuwste-eerst.
9. Breng dezelfde verticale functionaliteit gecontroleerd over naar HKH Autopilot en bewijs
   contractpariteit.
10. Inventariseer de SSD- en HDD-locaties en leg de gekozen PVC-, back-up- en herstelopzet vast
    zonder LVM Storage te installeren.
11. Migreer de drie productieapplicaties gecontroleerd van `emptyDir` naar een eigen persistente
    PostgreSQL-PVC op de SSD.
12. Richt dagelijkse gecomprimeerde PostgreSQL-dumps met checksum en retentie op de externe HDD in
    en bewijs een herstel naar een tijdelijke database.
13. Bouw in beide HKH-backends de deterministische, versieerbare en idempotente
    `PreviewDataSeeder`, inclusief preview-only `ensure`-endpoint en productieguards.
14. Geef iedere HKH-preview een eigen verwijderbare PVC en laat Flyway plus de seeder aantoonbaar
    meegroeien met een branch die het databaseschema wijzigt.
15. Borg Testcontainers-, Flyway-, seed-, productieguard- en restoretests componentgericht in CI.

### Definition of done

- Productie en Personal News Feed-previews lopen via één Cloudflare-wildcard en Git-managed
  OpenShift Routes.
- Iedere open pull request van HKH en HKH Autopilot heeft een eigen bereikbare gebruikers- en
  adminpreview, backend, namespace en database; die is in stap 2 nog leeg en wordt vanaf stap 4
  automatisch met deterministische previewdata gevuld.
- Sluiten of mergen van een pull request verwijdert de volledige previewomgeving; de sweeper ruimt
  aantoonbaar een verweesde testnamespace op.
- Een PR die tijdens provisioning wordt gemerged laat geen opnieuw aangemaakte namespace achter;
  een tijdelijke fout bij GitHub of ArgoCD verwijdert geen preview van een nog open PR.
- Na de afgesproken graceperiode bestaan er geen `pnf-pr-*`, `hkh-pr-*` of
  `hkh-autopilot-pr-*`-namespaces zonder bijbehorende open pull request.
- Een beheerder kan in beide HKH-varianten een laatste-nieuwsbericht toevoegen en een gebruiker
  ziet alle berichten nieuwste-eerst.
- De uiteindelijke productieopslag is persistent en hersteld na een gecontroleerde podrestart.
- Iedere preview krijgt een eigen PostgreSQL-PVC; Flyway en de Kotlin-seeder leveren automatisch
  dezelfde bruikbare, deterministische beginsituatie zonder productiegegevens te kopiëren.
- Een bestaande preview krijgt na een schema-uitbreiding alleen de ontbrekende seedversie, zonder
  dubbele records of verlies van handmatig ingevoerde previewdata.
- De seeder en het `ensure`-endpoint kunnen aantoonbaar niet tegen productie worden uitgevoerd.
- Een dagelijkse dump vanaf de SSD naar de externe HDD is succesvol naar een tijdelijke database
  teruggezet en functioneel gecontroleerd.
- Er is in deze fase geen S3- of andere objectstorage ingericht.
- Er is in deze fase geen LVM Storage geïnstalleerd of impliciet als definitieve keuze aangenomen.

## 9. Fase 4 — koppeling met Software Factory

### Doel

Product Factory kan idempotent stories indienen, volgen en productvragen beantwoorden zonder enige
database- of codekoppeling.

### Benodigde machine-API

Minimaal:

```text
POST /api/integrations/v1/stories
GET  /api/integrations/v1/stories/{key}
GET  /api/integrations/v1/stories/{key}/subtasks
GET  /api/integrations/v1/stories/{key}/questions
POST /api/integrations/v1/stories/{key}/questions/{id}/answer
POST /api/integrations/v1/stories/{key}/comments
GET  /api/integrations/v1/events?after=<cursor>
```

### Contracteisen

- bearer-authenticatie met een beperkt serviceaccount;
- versie in het URL-pad;
- `Idempotency-Key` bij storyaanmaak;
- externe referentie naar Product Factory-iteratie en kandidaat;
- startmodus `draft`, `start` of `start-next`;
- status van story, subtaken, vragen, fout, PR, merge en deploy;
- cursor-gebaseerde events of eerst een betrouwbare pollingvariant;
- geen generieke endpoint waarmee willekeurige fasen kunnen worden gemanipuleerd.

### Product Factory-kant

- gegenereerde of handgeschreven client achter een eigen poort;
- `story_link` met lokale kandidaat-ID en externe Software Factory-key;
- idempotente statusreconciliatie;
- retry met backoff;
- duidelijk onderscheid tussen tijdelijk wachten, productvraag, fout en voltooid;
- dashboardlinks naar de Software Factory-story.

### Definition of done

- Een handmatig goedgekeurde kandidaat wordt exact één Software Factory-story.
- Een netwerkretry maakt geen dubbele story.
- Alle subtaken en de eindstatus zijn in Product Factory zichtbaar.
- Een testvraag kan via Product Factory worden beantwoord en de story gaat verder.

## 10. Fase 5 — meerdere producten als kernmodel

### Doel

Beide HKH-varianten worden configuratie en data, geen hardgecodeerde uitzonderingen.

### Productdefinitie

Elk product bevat minimaal:

- stabiele ID, slug en naam;
- algemene missie en productomschrijving;
- Software Factory-projectkey en targetrepositorynaam;
- workspace-directory en toegestane schrijfpaden;
- workspace-eigenaarschap `owner` of `product-factory`;
- optionele live- en preview-URL's;
- status `draft`, `active`, `paused` of `archived`;
- ontwikkelmodus `manual`, `autonomous` of `observe-only`;
- iteratieschema en tijdzone;
- maximaal aantal stories per cyclus;
- WIP-limiet;
- AI-leverancier, model en dag-/maandbudget;
- escalatiebeleid;
- bron-, privacy-, toegankelijkheids- en kwaliteitsregels;
- eigen onderzoek, productgeheugen en beslisgeschiedenis.

### Werk

- generieke producttabellen en repositories;
- productbeheer in het dashboard;
- `hkh` als handmatig/observe-only vergelijkingsproduct;
- `hkh-autopilot` als eerste autonoom gestuurd product;
- `hkh` met workspace-eigenaarschap `owner` en `hkh-autopilot` met eigenaarschap
  `product-factory` configureren;
- productcontext strikt scheiden in iedere query en agentrun;
- workspace-paden strikt uit de geregistreerde productslug afleiden en path traversal weigeren;
- pauzeren per product zonder de hele runtime stil te leggen;
- template voor het toevoegen van een volgend product;
- tests met minimaal twee fictieve producten om onbedoelde HKH-koppeling te voorkomen.

### Definition of done

- Een tweede fictief product kan zonder codewijziging worden toegevoegd.
- Runs, kennis, kandidaten en stories van producten lekken niet naar elkaar.
- Een productrun kan uitsluitend bestanden binnen zijn toegestane workspace-directory publiceren.
- Product Factory kan geen workspace-wijziging voor `hkh` publiceren.
- Iedere variant kan zelfstandig worden gepauzeerd en hervat.
- Product Factory kan voor `hkh` geen story publiceren zolang de ontwikkelmodus niet `autonomous`
  is.

### Status fase 5 — afgerond op 7 augustus 2026

- Flyway beheert een generieke productdefinitie met stabiele ID, planning, budgetten, regels,
  repositorykoppeling, ontwikkelmodus en workspacebeleid; onderzoek, geheugen en beslissingen zijn
  afzonderlijke productgebonden tabellen;
- `hkh` en `hkh-autopilot` zijn uitsluitend initiële data: dezelfde API kan zonder codewijziging
  volgende producten registreren;
- iedere query voor runs, kennis, storykandidaten en workspace-publicaties vereist expliciet een
  productcontext; integratietests met `castle-guide` en `archive-explorer` bewaken isolatie;
- de centrale productpolicy blokkeert een gepauzeerd product, story-publicatie buiten
  `autonomous`, workspace-publicatie bij eigenaarschap `owner` en ieder pad buiten de allowlist;
- het dashboard toont de belangrijkste instellingen, kan generieke producten toevoegen en kan
  ieder product onafhankelijk pauzeren of hervatten;
- [docs/product-template.md](product-template.md) beschrijft het volledige contract en de veilige
  ingebruikname van een volgend product.

## 11. Fase 6 — productonderzoek en UX in shadow mode

### Doel

Agents maken aantoonbaar bruikbare productvoorstellen, maar Product Factory stuurt nog niets naar
Software Factory.

### Agentrollen

1. `RESEARCHER` — onderzoekt gebruikersbehoeften, data en bestaande toepassingen.
2. `PRODUCT_OWNER` — verbindt bevindingen aan missie en prioriteiten.
3. `UX_DESIGNER` — maakt flows, wireframes en interactiehypotheses.
4. `CRITIC` — zoekt gaten, onbetrouwbare bronnen, juridische risico's en onnodige complexiteit.
5. `STORY_WRITER` — maakt kleine, toetsbare storykandidaten.

### Veiligheidsmodel

- onderzoeksagents hebben browser- en read-only repositorytoegang;
- zij hebben geen Software Factory-token, GitHub-write-token of cluster-writecredentials;
- alleen de publishercomponent heeft een Git-credential, uitsluitend voor
  `product-factory-workspace`;
- webinhoud is onvertrouwde input en nooit een instructiebron;
- iedere bevinding heeft URL, raadpleegdatum en korte onderbouwing;
- runtime valideert agentoutput voordat deze wordt opgeslagen of naar de workspace gepubliceerd;
- agents kunnen alleen interne kandidaten maken.

### Definition of done

- Minimaal drie volledige shadow-iteraties zijn uitgevoerd.
- Elke iteratie levert onderzoek, beslissingen, UX en maximaal drie samenhangende kandidaten.
- Iedere geaccepteerde iteratie levert één herleidbare workspace-commit of pull request op; ruwe
  gedachten en verworpen concepten worden niet gecommit.
- Onderzoeksbestanden bevatten bron-URL, raadpleegdatum, rechtenindicatie en run-ID.
- Dubbele of conflicterende kandidaten worden herkend.
- Een criticus kan een kandidaat verwerpen of terugsturen.
- Er is geen menselijke productbeslissing nodig geweest.

### Status fase 6 — afgerond op 7 augustus 2026

- de runtime voert vijf strikt gescheiden rollen uit (`RESEARCHER`, `PRODUCT_OWNER`,
  `UX_DESIGNER`, `STORY_WRITER` en `CRITIC`), valideert hun gestructureerde output en bewaart
  stappen, artefacten, bronnen, UX, beslissingen en kandidaten duurzaam in PostgreSQL;
- de Mac-agentworker start Codex fail-closed met native webonderzoek, read-only werkruimte,
  geïsoleerde configuratie en een minimale omgevingsvariabelen-allowlist; GitHub-, cluster-,
  database- en Product Factory-credentials bereiken de onderzoeksagents niet;
- vier volledige productie-iteraties (`shadow-hkh-autopilot-0004` tot en met `0007`) hebben elk
  alle vijf rollen en vijf gevalideerde artefacten afgerond. Zij legden respectievelijk 6, 6, 7 en
  8 bronnen met URL, raadpleegdatum en rechtenindicatie vast en maakten samen zes kandidaten;
- de onafhankelijke criticus gaf alle vier iteraties `REVISE`. Daardoor bleven alle zes kandidaten
  intern `REJECTED`, ontstond geen workspace-commit en werd geen enkele kandidaat naar Software
  Factory gepubliceerd. Dit bewijst het bedoelde veilige shadow-gedrag zonder menselijke
  productbeslissing;
- integratietests bewaken daarnaast `ACCEPT`, `REVISE`, duplicaatdetectie, productisolatie en de
  regel dat uitsluitend een geaccepteerd, niet-duplicaat dossier via één herleidbare workspace-PR
  mag worden gepubliceerd;
- de eerste drie fail-closed proefruns brachten drie productieproblemen aan het licht en leidden
  tot regressietests voor Java-tijdserialisatie en socketreconnectie, relatieve workspacepaden en
  het sluiten van stdin van het Codex-subproces. De vier daaropvolgende iteraties liepen zonder
  technische fout of retry volledig door.

## 12. Fase 7 — autonome storycyclus

### Doel

Product Factory mag uitsluitend voor producten met ontwikkelmodus `autonomous` zelf onderbouwde
stories laten uitvoeren en begeleidt ze tot na deployment.

### Cyclus

1. Bekijk live product, repository, eerdere beslissingen en lopend werk.
2. Rond eerst actieve stories en vragen af.
3. Doe gericht onderzoek naar de belangrijkste huidige onzekerheid.
4. Maak of actualiseer UX en producthypothese.
5. Laat de criticus scope, bronkwaliteit, rechten, privacy en toegankelijkheid controleren.
6. Publiceer geaccepteerd onderzoek, UX en beslissingen als één workspace-wijziging.
7. Selecteer nul tot maximaal drie kleine stories en leg deze in de workspace vast.
8. Wacht tot de workspace-validatie groen is en registreer de commit-SHA.
9. Zet slechts één story tegelijk op `start-next` bij Software Factory, met een verwijzing naar die
   commit-SHA en de relevante artefactpaden.
10. Beantwoord productvragen via een aparte `QUESTION_RESOLVER`.
11. Volg build, test, merge en deploy.
12. Evalueer het resultaat en publiceer de evaluatie in productgeheugen en workspace.

### Guardrails

- maximaal drie nieuwe stories per product per etmaal, geen verplicht quotum;
- WIP-limiet één per targetrepository;
- de runtime weigert storypublicatie voor producten met ontwikkelmodus `manual` of `observe-only`;
- de runtime weigert workspace-publicatie als het workspace-eigenaarschap niet `product-factory`
  is;
- een story mag pas worden aangeboden nadat de bijbehorende workspace-wijziging is gemerged;
- alleen de publishercomponent mag uitsluitend naar `product-factory-workspace` schrijven;
- workspace-validatie controleert metadata en voorkomt schrijven buiten de productdirectory;
- geen nieuwe story bij een open fout of mislukte deployment;
- dagelijks en maandelijks AI-kostenplafond;
- stop na herhaalde identieke fouten;
- software kan autonoom deployen; gegenereerde historische beweringen vereisen aantoonbare bronnen;
- beslissingen zijn herleidbaar naar onderzoek en productregels.

### Menselijke escalatie

Alleen een `HumanAction` bij:

- account, OAuth-client of API-key aanmaken;
- betaalde dienst of budgetverhoging;
- juridische of licentieovereenkomst;
- DNS, certificaat of externe productieconfiguratie;
- geheim of productiecredential invoeren;
- onomkeerbare externe handeling;
- expliciete wijziging van de productmissie.

Een HumanAction bevat exacte stappen, reden, eventuele kosten, blokkadestatus en een automatische
controle waarmee Product Factory kan vaststellen dat de handeling klaar is.

## 13. Fase 8 — baseline bevriezen en ontwikkelpaden splitsen

### Doel

Een eerlijk en reproduceerbaar beginpunt vastleggen. Vanaf dit moment stuurt de eigenaar de
productontwikkeling van `hkh`, terwijl Product Factory uitsluitend de productontwikkeling van
`hkh-autopilot` bestuurt.

### Werk

- dezelfde algemene productvisie in beide repositories vastleggen;
- beide baseline-tags en commit-SHA's in database en workspace registreren;
- technische verschillen automatisch rapporteren;
- twee zelfstandige databases, deployments, URL's en APK's bevestigen;
- Product Factory voor `hkh` op `observe-only` zetten;
- Product Factory voor `hkh-autopilot` op `autonomous` zetten;
- splitsingsdatum en vergelijkingsregels vastleggen;
- de splitsingsbeslissing en nulmeting als workspace-artefact publiceren;
- voorkomen dat Product Factory stories voor `hkh` kan indienen.

### Definition of done

- Beide varianten slagen voor dezelfde baseline-acceptatietests.
- Beide varianten hebben de tag `comparison-baseline-v1`.
- Het dashboard toont duidelijk wie ieder productpad bestuurt.
- Autonome storypublicatie voor `hkh` wordt technisch geweigerd.
- HKH Autopilot kan zelfstandig zijn eerste productiteratie starten.

## 14. Fase 9 — parallelle productontwikkeling en vergelijking

### Verdeling

- **`hkh`** — productkeuzes en nieuwe stories worden door de eigenaar bepaald.
- **`hkh-autopilot`** — Product Factory onderzoekt, kiest, schrijft stories, beantwoordt vragen en
  evalueert; Software Factory bouwt en deployt.

Een aannemelijke eerste verticale functionaliteit voor beide paden is het kunnen beheren, vinden en
bekijken van één historische locatie met verhaal, afbeelding, bron en rechteninformatie. Dit is
geen gedeelde verplichte backlog: beide productpaden mogen vanuit dezelfde visie tot een andere
eerste oplossing komen.

### Vergelijkingssignalen

- doorlooptijd van idee tot werkende deployment;
- aantal herstelrondes, regressies en mislukte deployments;
- AI-, infrastructuur- en menselijke tijd/kosten;
- kwaliteit van tests, toegankelijkheid, privacy en bronverantwoording;
- samenhang en begrijpelijkheid van UX;
- hoeveelheid daadwerkelijk gebruikte functionaliteit;
- onderhoudbaarheid en snelheid van latere wijzigingen.

De vergelijking is informatief, geen wedstrijd op aantallen features of regels code. Verschillen
in scope en gemaakte aannames worden naast de cijfers vastgelegd.

### Definition of done

- De eigenaar kan onafhankelijk stories voor `hkh` laten uitvoeren of zelf ontwikkelen.
- Product Factory doorloopt zonder productinput een volledige iteratie voor `hkh-autopilot`.
- Product Factory beantwoordt productvragen voor HKH Autopilot zelfstandig.
- Resultaten en beslissingen zijn per variant in de workspace gescheiden en vergelijkbaar.
- Iedere autonome story verwijst naar de workspace-commit waarop de productbeslissing is gebaseerd.
- Alleen echte HumanActions worden aan de eigenaar gemeld.

## 15. Daarna — leren en opschalen

Na de eerste verticale slice:

- privacyvriendelijke gebruikssignalen en foutmetingen toevoegen;
- no-result-zoekopdrachten en mislukte gebruikersflows analyseren;
- onderzoek en productbeslissingen laten reageren op echte signalen;
- bronconnectors voor publiek erfgoed gefaseerd toevoegen;
- eventuele camera-, locatie-, audio-, route-, tijdlijn- of AR-concepten alleen als hypothese testen;
- na HKH Autopilot een ander echt product onboarden om generiek gedrag te bewijzen;
- budget, scheduler en WIP per product verfijnen;
- herstel, back-up, retentie en incidentrunbooks voltooien.

## 16. Eerste uitvoerbare storyvolgorde

Deze volgorde is bedoeld als startbacklog voor Software Factory. Iedere regel wordt een afzonderlijke,
kleine story; combineer ze niet tot één grote bootstrapstory.

De eenmalige directory-, template- en validatie-inrichting van `product-factory-workspace` gebeurt
in fase 0 buiten Software Factory, omdat deze repository bewust geen buildtarget is. Vanaf story 15
publiceert de Product Factory daar zelf uitsluitend gevalideerde productartefacten.

Stories 1 tot en met 18 vormen de reeds gekozen technische baselines. De eerstvolgende uitvoering
begint bij story 19: eerst wildcard-routing, daarna lege HKH-branchomgevingen, vervolgens de
laatste-nieuwsfunctionaliteit en pas daarna de duurzame database-inrichting. De functionele
Product Factory-koppeling en verdere autonomie volgen daarop.

| Volgorde | Target | Storyresultaat |
|---:|---|---|
| 1 | HKH | Personal News Feed-referentie vastleggen en overeenkomstige repositorybasis maken |
| 2 | HKH | Kotlin/Spring Boot/Modulith-backend met modulegrenzentest, health en version |
| 3 | HKH | Veilige root-`secrets.env`-configuratie, voorbeeldbestand en precedencetests |
| 4 | HKH | PostgreSQL/Flyway en lokale ontwikkelomgeving |
| 5 | HKH | Afzonderlijke Flutter-gebruikersapp met backendconnectiviteit |
| 6 | HKH | Afzonderlijke Flutter-adminbasis en Google-tokenverificatie |
| 7 | HKH | Componentgerichte CI, images en downloadbare APK |
| 8 | HKH | OpenShift/Kustomize/ArgoCD-basis en deployverificatie |
| 9 | HKH Autopilot | Repositorybootstrap en gecontroleerde overname van de volledige HKH-basis |
| 10 | Beide HKH-varianten | Modulith-, secrets- en functionele pariteit aantonen en baseline taggen |
| 11 | Product Factory | Software Factory-referentie vastleggen en overeenkomstige Maven-reactor maken |
| 12 | Product Factory | Spring Modulith-runtime met expliciete fail-closed modulegrenzen en tests |
| 13 | Product Factory | Gelaagde properties- en root-`secrets.env`-configuratie met precedencetests |
| 14 | Product Factory | Eigen PostgreSQL/Flyway en product-/iteratieskeleton |
| 15 | Product Factory | Veilige workspace-publisher, validatie en commit-SHA-registratie |
| 16 | Product Factory | Eigen agentworker en duurzaam resultaatcontract |
| 17 | Product Factory | Dashboard-backend, Flutter-dashboard en Google-loginbasis |
| 18 | Product Factory | OpenShift-deployment en versie/deployverificatie |
| 19 | Infrastructuur | Cloudflare-wildcard met een tijdelijke host veilig op OpenShift ingress aansluiten |
| 20 | Infrastructuur/Newsfeed | Productiehosts en Newsfeed-previews naar Git-managed Routes migreren en de oude previewrouter verwijderen |
| 21 | HKH | Per open pull request een namespace met backend, frontends en een eigen lege database maken |
| 22 | HKH Autopilot | Dezelfde volledige branchomgeving met eigen lege database maken |
| 23 | Infrastructuur | Newsfeed-race repareren en veilige generieke cleanup/sweeper voor alle previewomgevingen inrichten |
| 24 | HKH | Flyway-schema, repository en beveiligde lees-/schrijf-API voor laatste nieuws maken |
| 25 | HKH | In de admin een laatste-nieuwsbericht kunnen toevoegen |
| 26 | HKH | In de gebruikersapp alle laatste-nieuwsberichten nieuwste-eerst tonen |
| 27 | HKH Autopilot | De verticale laatste-nieuwsslice gecontroleerd overnemen met dezelfde contracten |
| 28 | Beide HKH-varianten | Contract-, Flutter- en end-to-endpariteit voor laatste nieuws aantonen |
| 29 | Infrastructuur | SSD- en HDD-opslag inventariseren en de PVC-, back-up- en herstelkeuze zonder LVM vastleggen |
| 30 | HKH | Productiedatabase naar een persistente PostgreSQL-PVC op de SSD migreren |
| 31 | HKH Autopilot | Productiedatabase naar een afzonderlijke persistente PostgreSQL-PVC op de SSD migreren |
| 32 | Product Factory | Operationele PostgreSQL-database op de SSD persistent maken met behoud van bestaande status |
| 33 | Infrastructuur | Dagelijkse gecomprimeerde PostgreSQL-dumps naar de externe HDD met retentie en restore-oefening inrichten |
| 34 | Beide HKH-varianten | Deterministische versieerbare Kotlin-seeder, previewguards en preview-only ensure-endpoint bouwen |
| 35 | Infrastructuur | Iedere preview een eigen verwijderbare SSD-PVC geven en Flyway plus previewseeding valideren |
| 36 | Alle drie applicaties | Testcontainers-, Flyway-, seed-, productieguard- en restoretests componentgericht in CI borgen |
| 37 | Software Factory | Versievaste idempotente Product Factory-integratie-API |
| 38 | Product Factory | Software Factory-client en story/statusreconciliatie |
| 39 | Product Factory | Multi-productmodel met ontwikkelmodus en beide HKH-varianten |
| 40 | Product Factory | Researcher, bronmodel en workspace-publicatie in shadow mode |
| 41 | Product Factory | Product Owner, UX Designer, Critic en Story Writer in shadow mode |
| 42 | Product Factory | Autonome vraagbeantwoording en HumanAction-beleid |
| 43 | Product Factory | Begrensde autonome storypublicatie met WIP één |
| 44 | Beide HKH-varianten | Baseline bevriezen en handmatig/autonoom ontwikkelpad activeren |
| 45 | HKH Autopilot via Product Factory | Eerste autonome productiteratie en verticale functionaliteit |

## 17. Beslispunten die geen productinput vereisen

De agents mogen zelfstandig beslissen over:

- interne package- en klassennamen binnen de afgesproken architectuur;
- exacte schermindeling en navigatie op basis van UX-onderzoek;
- technische bibliotheken als ze actief, passend en vervangbaar zijn;
- opsplitsing van een kandidaat in kleinere stories;
- volgorde binnen een iteratie;
- defaults die goedkoop, omkeerbaar en veilig zijn;
- afwijzen van een idee dat onvoldoende bewijs of productwaarde heeft.

Zij leggen deze keuzes wel vast. Alleen de expliciete HumanAction-categorieën uit fase 7 worden aan
een mens voorgelegd.
