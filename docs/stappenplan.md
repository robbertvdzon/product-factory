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
| 3 | Product Factory kan stories aanbieden en volgen via een stabiele API |
| 4 | Product Factory ondersteunt meerdere configureerbare producten |
| 5 | Productagents doen onderzoek en ontwerpen in shadow mode |
| 6 | Product Factory maakt en begeleidt autonoom kleine stories |
| 7 | De gelijke baseline wordt bevroren en de twee ontwikkelpaden worden gesplitst |
| 8 | De eigenaar ontwikkelt HKH; Product Factory ontwikkelt HKH Autopilot en vergelijkt resultaten |

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
- grote binaire artefacten staan in S3-compatibele objectopslag, met metadata en een stabiele link
  vanuit de workspace;
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

## 8. Fase 3 — koppeling met Software Factory

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

## 9. Fase 4 — meerdere producten als kernmodel

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

## 10. Fase 5 — productonderzoek en UX in shadow mode

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

## 11. Fase 6 — autonome storycyclus

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

## 12. Fase 7 — baseline bevriezen en ontwikkelpaden splitsen

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

## 13. Fase 8 — parallelle productontwikkeling en vergelijking

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

## 14. Daarna — leren en opschalen

Na de eerste verticale slice:

- privacyvriendelijke gebruikssignalen en foutmetingen toevoegen;
- no-result-zoekopdrachten en mislukte gebruikersflows analyseren;
- onderzoek en productbeslissingen laten reageren op echte signalen;
- bronconnectors voor publiek erfgoed gefaseerd toevoegen;
- eventuele camera-, locatie-, audio-, route-, tijdlijn- of AR-concepten alleen als hypothese testen;
- na HKH Autopilot een ander echt product onboarden om generiek gedrag te bewijzen;
- budget, scheduler en WIP per product verfijnen;
- herstel, back-up, retentie en incidentrunbooks voltooien.

## 15. Eerste uitvoerbare storyvolgorde

Deze volgorde is bedoeld als startbacklog voor Software Factory. Iedere regel wordt een afzonderlijke,
kleine story; combineer ze niet tot één grote bootstrapstory.

De eenmalige directory-, template- en validatie-inrichting van `product-factory-workspace` gebeurt
in fase 0 buiten Software Factory, omdat deze repository bewust geen buildtarget is. Vanaf story 15
publiceert de Product Factory daar zelf uitsluitend gevalideerde productartefacten.

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
| 19 | Software Factory | Versievaste idempotente Product Factory-integratie-API |
| 20 | Product Factory | Software Factory-client en story/statusreconciliatie |
| 21 | Product Factory | Multi-productmodel met ontwikkelmodus en beide HKH-varianten |
| 22 | Product Factory | Researcher, bronmodel en workspace-publicatie in shadow mode |
| 23 | Product Factory | Product Owner, UX Designer, Critic en Story Writer in shadow mode |
| 24 | Product Factory | Autonome vraagbeantwoording en HumanAction-beleid |
| 25 | Product Factory | Begrensde autonome storypublicatie met WIP één |
| 26 | Beide HKH-varianten | Baseline bevriezen en handmatig/autonoom ontwikkelpad activeren |
| 27 | HKH Autopilot via Product Factory | Eerste autonome productiteratie en verticale functionaliteit |

## 16. Beslispunten die geen productinput vereisen

De agents mogen zelfstandig beslissen over:

- interne package- en klassennamen binnen de afgesproken architectuur;
- exacte schermindeling en navigatie op basis van UX-onderzoek;
- technische bibliotheken als ze actief, passend en vervangbaar zijn;
- opsplitsing van een kandidaat in kleinere stories;
- volgorde binnen een iteratie;
- defaults die goedkoop, omkeerbaar en veilig zijn;
- afwijzen van een idee dat onvoldoende bewijs of productwaarde heeft.

Zij leggen deze keuzes wel vast. Alleen de expliciete HumanAction-categorieën uit fase 6 worden aan
een mens voorgelegd.
