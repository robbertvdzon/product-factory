# Stappenplan Product Factory en HKH

## 1. Doel

We bouwen drie zelfstandig inzetbare systemen:

1. **Software Factory** — voert softwarestories uit: refinen, plannen, ontwikkelen, reviewen,
   testen, documenteren, mergen en deployen.
2. **Product Factory** — onderzoekt wat een product nodig heeft, maakt product- en UX-beslissingen,
   schrijft stories, beantwoordt productvragen en evalueert het resultaat.
3. **HKH-app** — het eerste product dat door deze combinatie wordt ontwikkeld.

HKH is de eerste praktijktest, maar Product Factory bevat geen HKH-specifieke businesslogica. Een
volgend product moet via configuratie en productdata kunnen worden toegevoegd, zonder nieuwe
Product Factory-code te schrijven.

## 2. Volgorde op hoofdlijnen

| Fase | Resultaat |
|---|---|
| 0 | Beide repositories zijn veilig bouwbaar door Software Factory |
| 1 | HKH heeft een lege maar volledig werkende technische basis |
| 2 | Product Factory heeft een zelfstandige technische basis |
| 3 | Product Factory kan stories aanbieden en volgen via een stabiele API |
| 4 | Product Factory ondersteunt meerdere configureerbare producten |
| 5 | Productagents doen onderzoek en ontwerpen in shadow mode |
| 6 | Product Factory maakt en begeleidt autonoom kleine stories |
| 7 | HKH krijgt via die cyclus een eerste bruikbare verticale functionaliteit |
| 8 | De cyclus wordt op bewijs, kwaliteit en meerdere producten doorontwikkeld |

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
andere specifieke oplossing voor.

## 5. Fase 0 — repositories bouwbaar maken

### Doel

Zowel `hkh` als `product-factory` kan veilig als targetrepository door Software Factory worden
gebruikt.

### Werk

Voor beide repositories:

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

## 6. Fase 1 — technische basis HKH

### Doel

Een lege maar end-to-end werkende applicatie die technisch als basis voor HKH dient.

### Gewenste structuur

```text
hkh/
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

### Definition of done

- Backend, gebruikerswebapp en adminwebapp draaien onafhankelijk op OpenShift.
- De gebruikersapp is als APK te downloaden en te installeren.
- Admin is alleen na geldige Google-authenticatie bereikbaar.
- Database-migraties zijn herhaalbaar.
- Een wijziging aan één component bouwt niet onnodig alle andere componenten.
- Software Factory kan build, merge en deploy betrouwbaar volgen.

Na deze fase worden de definitieve HKH-deploydoelen, livecomponenten, APK-packagegegevens en
release-retentie in de lokale `projects.yaml` van Software Factory geconfigureerd.

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

HKH wordt configuratie en data, geen hardgecodeerde uitzondering.

### Productdefinitie

Elk product bevat minimaal:

- stabiele ID, slug en naam;
- algemene missie en productomschrijving;
- Software Factory-projectkey en targetrepositorynaam;
- optionele live- en preview-URL's;
- status `draft`, `active`, `paused` of `archived`;
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
- HKH als eerste seed/productconfiguratie;
- productcontext strikt scheiden in iedere query en agentrun;
- pauzeren per product zonder de hele runtime stil te leggen;
- template voor het toevoegen van een volgend product;
- tests met minimaal twee fictieve producten om onbedoelde HKH-koppeling te voorkomen.

### Definition of done

- Een tweede fictief product kan zonder codewijziging worden toegevoegd.
- Runs, kennis, kandidaten en stories van producten lekken niet naar elkaar.
- HKH kan zelfstandig worden gepauzeerd en hervat.

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

Product Factory mag zelf onderbouwde stories laten uitvoeren en begeleidt ze tot na deployment.

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

## 12. Fase 7 — eerste werkende HKH-functionaliteit

### Doel

De volledige product-naar-softwarelus bewijzen met een zeer kleine verticale functionaliteit.

De Product Factory bepaalt op basis van onderzoek de precieze UX. Een aannemelijk klein
validatiescenario is:

- admin kan één historische locatie met titel, tekst, bron en rechteninformatie vastleggen;
- gebruikersapp kan beschikbare locaties tonen;
- een gebruiker kan een detail met verhaal, afbeelding en bronverwijzing openen;
- basiszoeken op titel of plaats werkt;
- onbekende of onbevestigde informatie wordt niet als feit gepresenteerd.

Dit scenario is geen definitieve productspecificatie. Het is een minimale verticale doorsnede die
database, admin, API, app, bronverantwoording, deployment en autonome storybegeleiding tegelijk
bewijst.

### Definition of done

- De functionaliteit is door Product Factory onderzocht en als hypothese vastgelegd.
- De stories zijn automatisch via Software Factory gebouwd en gedeployed.
- Product Factory heeft vragen zelfstandig beantwoord.
- Het resultaat is in webapp en APK aantoonbaar bruikbaar.
- Bronnen en rechten zijn zichtbaar en machineleesbaar opgeslagen.
- De evaluatie heeft een concrete volgende keuze opgeleverd.

## 13. Fase 8 — leren en opschalen

Na de eerste verticale slice:

- privacyvriendelijke gebruikssignalen en foutmetingen toevoegen;
- no-result-zoekopdrachten en mislukte gebruikersflows analyseren;
- onderzoek en productbeslissingen laten reageren op echte signalen;
- bronconnectors voor publiek erfgoed gefaseerd toevoegen;
- eventuele camera-, locatie-, audio-, route-, tijdlijn- of AR-concepten alleen als hypothese testen;
- een tweede echt product onboarden om generiek gedrag te bewijzen;
- budget, scheduler en WIP per product verfijnen;
- herstel, back-up, retentie en incidentrunbooks voltooien.

## 14. Eerste uitvoerbare storyvolgorde

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
| 8 | Product Factory | Repositoryconventies, docs, verificatieconfig en required GitHub-check |
| 9 | Product Factory | Zelfstandige Maven/Spring Boot/Modulith-basis |
| 10 | Product Factory | Eigen PostgreSQL/Flyway en product-/iteratieskeleton |
| 11 | Product Factory | Eigen agentworker en duurzaam resultaatcontract |
| 12 | Product Factory | Dashboard-backend, Flutter-dashboard en Google-loginbasis |
| 13 | Product Factory | OpenShift-deployment en versie/deployverificatie |
| 14 | Software Factory | Versievaste idempotente Product Factory-integratie-API |
| 15 | Product Factory | Software Factory-client en story/statusreconciliatie |
| 16 | Product Factory | Generiek multi-productmodel met HKH als eerste product |
| 17 | Product Factory | Researcher en bronmodel in shadow mode |
| 18 | Product Factory | Product Owner, UX Designer, Critic en Story Writer in shadow mode |
| 19 | Product Factory | Autonome vraagbeantwoording en HumanAction-beleid |
| 20 | Product Factory | Begrensde autonome storypublicatie met WIP één |
| 21 | HKH via Product Factory | Eerste kleine verticale productfunctionaliteit |

## 15. Beslispunten die geen productinput vereisen

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
