# Stappenplan Product Factory en HKH

## 1. Doel

We bouwen vier zelfstandig inzetbare systemen:

1. **Software Factory** — voert softwarestories uit: refinen, plannen, ontwikkelen, reviewen,
   testen, documenteren, mergen en deployen.
2. **Product Factory** — onderzoekt wat een product nodig heeft, maakt product- en UX-beslissingen,
   schrijft stories, beantwoordt productvragen en evalueert het resultaat.
3. **HKH** — de variant waarvan de productontwikkeling na de gezamenlijke baseline door de eigenaar
   wordt gestuurd.
4. **HKH Autopilot** — de variant waarvan de productontwikkeling na diezelfde baseline autonoom door
   Product Factory en Software Factory wordt uitgevoerd.

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
| 0 | Product Factory en beide HKH-repositories zijn veilig bouwbaar door Software Factory |
| 1 | HKH en HKH Autopilot hebben aantoonbaar dezelfde werkende technische basis |
| 2 | Product Factory heeft een zelfstandige technische basis |
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
- alleen bij een noodzakelijke externe handeling een mens inschakelen.

### Product Factory doet niet

- rechtstreeks code in een productrepository wijzigen;
- zelf commits, pull requests, merges of deployments uitvoeren;
- rechtstreeks de database van Software Factory lezen of wijzigen;
- ontwikkelvragen door dezelfde agent laten beantwoorden die de wijziging bouwt;
- historische feiten zonder herleidbare bron als waarheid publiceren;
- accounts, betaalde diensten, juridische overeenkomsten of secrets namens een mens regelen.

### Integratiegrens

Product Factory en Software Factory delen geen Kotlin-code, database of runtime. De koppeling is
een versievaste HTTP/OpenAPI-interface. Product Factory bewaart alleen de externe Software
Factory-storykey en de voor monitoring noodzakelijke snapshots.

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
Software Factory worden gebruikt.

### Werk

Voor alle drie repositories:

- basisdocumentatie en repositoryconventies toevoegen;
- `.factory/verification.yaml` toevoegen met revisiongebonden verificatiecommando's;
- GitHub Actions-workflow toevoegen met een altijd aanwezige check genaamd
  `Repository verification`;
- branch/buildbeleid documenteren;
- Maven-, Flutter- en Docker-caches gebruiken waar relevant;
- secrets uitsluitend via lokale of platformconfiguratie aanbieden;
- een docs-skeleton aanmaken voor architectuur, ontwikkeling, deployment en stories.

### Definition of done

- Een kleine README-wijziging kan door Software Factory worden opgepakt.
- De PR krijgt de check `Repository verification`.
- De check is groen en bewijsbaar gekoppeld aan de actuele PR-head.
- Software Factory kan de PR automatisch mergen.
- De deploysubtaak wordt voorlopig bewust en zichtbaar overgeslagen.

> Mogelijke eenmalige handeling: als GitHub een workflow die voor het eerst in dezelfde PR wordt
> toegevoegd niet als vereiste check accepteert, moet alleen de eerste bootstrap-PR handmatig
> worden gemerged. Vanaf de daaropvolgende story is de normale automatische poort actief.

## 6. Fase 1 — gelijke technische basis voor HKH en HKH Autopilot

### Doel

Een lege maar end-to-end werkende applicatiebasis die als hetzelfde vertrekpunt in `hkh` en
`hkh-autopilot` staat voordat de productontwikkeling uiteen gaat lopen.

### Gewenste structuur

```text
hkh/ en hkh-autopilot/
├── backend/                 Kotlin, Spring Boot, JDK 21
├── app/                     Flutter gebruikersapp: web en Android
├── admin/                   Flutter web-admin
├── packages/                gedeelde Dart-modellen/UI waar zinvol
├── deploy/                  OpenShift/Kustomize-manifests
├── docs/
├── .factory/
└── .github/workflows/
```

### Werk in kleine stories

1. **HKH repository bootstrap**
   - rootstructuur, buildbestanden, docs en verificatie;
   - geen productfunctionaliteit.
2. **Backend-basis**
   - Spring Boot-applicatie;
   - `/actuator/health` en `/api/version`;
   - OpenAPI en uniforme foutafhandeling;
   - eerste unit- en integratietest.
3. **Database-basis**
   - PostgreSQL-configuratie;
   - Flyway;
   - lokaal via Docker Compose;
   - nog geen uitgebreid historisch datamodel.
4. **Gebruikersapp-basis**
   - Flutter-web en Android uit dezelfde codebase;
   - configurabele backend-URL;
   - startscherm, laadstatus en foutstatus;
   - verbinding met health/version.
5. **Admin-basis**
   - afzonderlijke Flutter-webapp;
   - Google OIDC aan de clientzijde en tokenverificatie in de backend;
   - e-mailallowlist/rollen;
   - nog geen inhoudelijk beheer.
6. **CI en artefacten**
   - backend-, gebruikersapp- en adminverificatie;
   - images bij een groene `main`;
   - downloadbare release-APK;
   - componenten worden alleen gebouwd als hun paden wijzigen.
7. **OpenShift-basis**
   - backend, gebruikerswebapp en adminwebapp als losse deployments;
   - configuratie en secrets buiten Git;
   - ArgoCD/Kustomize-structuur;
   - live- en versiecontrole.
8. **Baseline overzetten naar HKH Autopilot**
   - dezelfde bronstructuur, versies, tests en verificatie overnemen;
   - alleen noodzakelijke runtime-identiteit aanpassen;
   - geen productfunctionaliteit of autonome optimalisatie toevoegen.
9. **Baseline-pariteit aantonen**
   - dezelfde functionele contracttests tegen beide deployments draaien;
   - dependency- en toolchainversies vergelijken;
   - afwijkingen documenteren en beperken tot runtime-identiteit;
   - in beide repositories dezelfde tag `comparison-baseline-v1` zetten.

### Definition of done

- Beide varianten hebben een backend, gebruikerswebapp en adminwebapp op afzonderlijke OpenShift-
  resources.
- Beide gebruikersapps zijn als afzonderlijke APK te downloaden en naast elkaar te installeren.
- Beide admins zijn alleen na geldige Google-authenticatie bereikbaar.
- Database-migraties zijn gelijkwaardig en herhaalbaar.
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

Een zelfstandige runtime die qua structuur herkenbaar is ten opzichte van Software Factory, maar
geen code of runtimecomponenten daarvan hergebruikt.

### Gewenste structuur

```text
product-factory/
├── pom.xml
├── productfactory-contracts/
├── productfactory-common/
├── productfactory-runtime/
├── agentworker/
├── dashboard-backend/
├── dashboard-frontend/
├── deploy/
├── docker/
├── docs/
└── tools/
```

### Architectuurprincipes

- Kotlin, JDK 21, Spring Boot, Maven en Spring Modulith;
- eigen PostgreSQL-database en Flyway-migraties;
- eigen agentworker, agentimage en agentresultaatcontract;
- eigen Google OIDC-dashboard;
- eigen OpenShift-namespace, images, secrets en versies;
- configuratieprefix `PF_`;
- packages onder `nl.vdzon.productfactory`;
- geen Maven-dependency op Software Factory-artifacts.

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
- `dashboard`, `config`, `support` en `web`.

### Definition of done

- Runtime en dashboard draaien lokaal en op OpenShift.
- Database en migraties zijn zelfstandig.
- Er kan handmatig een productrecord en een interne storykandidaat worden vastgelegd.
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
- productcontext strikt scheiden in iedere query en agentrun;
- pauzeren per product zonder de hele runtime stil te leggen;
- template voor het toevoegen van een volgend product;
- tests met minimaal twee fictieve producten om onbedoelde HKH-koppeling te voorkomen.

### Definition of done

- Een tweede fictief product kan zonder codewijziging worden toegevoegd.
- Runs, kennis, kandidaten en stories van producten lekken niet naar elkaar.
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
- webinhoud is onvertrouwde input en nooit een instructiebron;
- iedere bevinding heeft URL, raadpleegdatum en korte onderbouwing;
- runtime valideert agentoutput voordat deze wordt opgeslagen;
- agents kunnen alleen interne kandidaten maken.

### Definition of done

- Minimaal drie volledige shadow-iteraties zijn uitgevoerd.
- Elke iteratie levert onderzoek, beslissingen, UX en maximaal drie samenhangende kandidaten.
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
6. Selecteer nul tot maximaal drie kleine stories.
7. Zet slechts één story tegelijk op `start-next` bij Software Factory.
8. Beantwoord productvragen via een aparte `QUESTION_RESOLVER`.
9. Volg build, test, merge en deploy.
10. Evalueer het resultaat en werk geheugen en prioriteiten bij.

### Guardrails

- maximaal drie nieuwe stories per product per etmaal, geen verplicht quotum;
- WIP-limiet één per targetrepository;
- de runtime weigert storypublicatie voor producten met ontwikkelmodus `manual` of `observe-only`;
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
- beide baseline-tags en commit-SHA's registreren;
- technische verschillen automatisch rapporteren;
- twee zelfstandige databases, deployments, URL's en APK's bevestigen;
- Product Factory voor `hkh` op `observe-only` zetten;
- Product Factory voor `hkh-autopilot` op `autonomous` zetten;
- splitsingsdatum en vergelijkingsregels vastleggen;
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
- Resultaten en beslissingen zijn per variant gescheiden en vergelijkbaar.
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

| Volgorde | Target | Storyresultaat |
|---:|---|---|
| 1 | HKH | Repositoryconventies, docs, verificatieconfig en required GitHub-check |
| 2 | HKH | Kotlin/Spring Boot-backend met health/version en tests |
| 3 | HKH | PostgreSQL/Flyway en lokale ontwikkelomgeving |
| 4 | HKH | Flutter-gebruikersapp met backendconnectiviteit |
| 5 | HKH | Flutter-adminbasis en Google-tokenverificatieseam |
| 6 | HKH | Componentgerichte CI, images en downloadbare APK |
| 7 | HKH | OpenShift/Kustomize/ArgoCD-basis en deployverificatie |
| 8 | HKH Autopilot | Repositorybootstrap en gecontroleerde overname van de volledige HKH-basis |
| 9 | Beide HKH-varianten | Pariteitstest, identiteitsverschillen vastleggen en baseline taggen |
| 10 | Product Factory | Repositoryconventies, docs, verificatieconfig en required GitHub-check |
| 11 | Product Factory | Zelfstandige Maven/Spring Boot/Modulith-basis |
| 12 | Product Factory | Eigen PostgreSQL/Flyway en product-/iteratieskeleton |
| 13 | Product Factory | Eigen agentworker en duurzaam resultaatcontract |
| 14 | Product Factory | Dashboard-backend, Flutter-dashboard en Google-loginbasis |
| 15 | Product Factory | OpenShift-deployment en versie/deployverificatie |
| 16 | Software Factory | Versievaste idempotente Product Factory-integratie-API |
| 17 | Product Factory | Software Factory-client en story/statusreconciliatie |
| 18 | Product Factory | Multi-productmodel met ontwikkelmodus en beide HKH-varianten |
| 19 | Product Factory | Researcher en bronmodel in shadow mode |
| 20 | Product Factory | Product Owner, UX Designer, Critic en Story Writer in shadow mode |
| 21 | Product Factory | Autonome vraagbeantwoording en HumanAction-beleid |
| 22 | Product Factory | Begrensde autonome storypublicatie met WIP één |
| 23 | Beide HKH-varianten | Baseline bevriezen en handmatig/autonoom ontwikkelpad activeren |
| 24 | HKH Autopilot via Product Factory | Eerste autonome productiteratie en verticale functionaliteit |

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
