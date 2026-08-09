# product-factory-10 - Voeg read-only blokkeerreden-veld toe aan storykandidaat-data op basis van bestaand dependson_resolution-artefact

## Story

Voeg read-only blokkeerreden-veld toe aan storykandidaat-data op basis van bestaand dependson_resolution-artefact

<!-- refined-by-factory -->

## Samenvatting
Storykandidaten in het dashboard laten straks ook zien of ze geblokkeerd zijn door een niet-opgeloste afhankelijkheid (`dependsOn`), en zo ja waarom. Deze informatie bestaat al in de achtergrond (vastgelegd toen de kandidaat werd aangemaakt), maar is nu niet zichtbaar via de data die de storywachtrij voedt. Deze wijziging voegt alleen een leeslaag toe: er wordt niets nieuws berekend of gewijzigd, alleen bestaande, al opgeslagen informatie toegankelijk gemaakt. Er verandert niets aan hoe kandidaten worden gemaakt of goedgekeurd.

## Scope
- Geverifieerd (geautomatiseerd, zie onderzoek hieronder): de bestaande API-response voor storykandidaten bevat `blocked`/`blockedReason` nog niet. Bron: `StoryCandidateController.list()` (`productfactory/.../story/StoryCandidateApi.kt`) leest uitsluitend uit `story_candidate` (+ `shadow_iteration` voor `sequence_number`) en kent geen join met `shadow_iteration_artifact`/`dependson_resolution`. De DTO `StoryCandidateView` (`productfactory-contracts/.../Contracts.kt`) heeft geen `blocked`/`blockedReason`-veld. Deze bevinding moet letterlijk terugkomen in de commit-/PR-beschrijving.
- Uit te breiden pad: `StoryCandidateController.list()` in `productfactory/src/main/kotlin/nl/vdzon/productfactory/story/StoryCandidateApi.kt` en `StoryCandidateView` in `productfactory-contracts/src/main/kotlin/nl/vdzon/productfactory/contracts/Contracts.kt`. Dit is de daadwerkelijke bron van de storyqueue-data (`dashboard-backend`'s `/api/story-candidates` proxyt dit ongewijzigd door als generieke map, dus nieuwe velden op `StoryCandidateView` stromen automatisch door zonder wijziging in `dashboard-backend`).
- Twee nieuwe, uitsluitend afgeleide velden op `StoryCandidateView`: `blocked: Boolean` en `blockedReason: String?`.
- Afleiding: per storykandidaat (`story_candidate.id` = `backlogId`, gekoppeld via `story_candidate.iteration_id`) het bestaande `dependson_resolution`-artefact opzoeken (`shadow_iteration_artifact` waar `artifact_type = 'dependson_resolution'`, kolom `content_json`, geschreven door `ShadowIterationEngine.dependsOnResolutionLog`), daarin het array-item met matchende `backlogId` vinden, en `blocked` overnemen. `blockedReason` wordt samengesteld uit de `rawValue`s van alle `dependsOn[]`-items met `resolved == false` in dat item, als "verwijzing naar onbekende sleutels: X, Y" (of `null` als er geen zijn / kandidaat niet geblokkeerd is).
- Puur read-only: geen wijziging aan `resolveDependencyReferences`/`persistValidatedResults`/`ShadowIterationEngine`, geen nieuwe kolom, geen nieuwe schrijfoperatie, geen wijziging aan endpoint-URL of -methode van `/api/story-candidates`.
- Geen wijziging in `dashboard-frontend` vereist (geen UI-eis in deze story).

## Acceptance criteria
- De commit-/PR-beschrijving documenteert expliciet dat `blocked`/`blockedReason` nog niet in de bestaande `/api/story-candidates`-response aanwezig waren vóór deze wijziging.
- `StoryCandidateView` bevat de twee nieuwe alleen-lezen velden `blocked: Boolean` en `blockedReason: String?`, gevuld via een read-only opzoeking op het bestaande `dependson_resolution`-artefact (geen nieuwe mutable kolom, geen wijziging aan de resolve-logica, geen nieuwe schrijfoperatie).
- Voor een storykandidaat waarvan het gekoppelde `dependson_resolution`-artefact-item `blocked: true` bevat met meerdere onopgeloste `dependsOn`-sleutels, bevat `blockedReason` alle betreffende onbekende sleutels in één samengevoegde, leesbare tekst (bijv. "verwijzing naar onbekende sleutels: X, Y"); geverifieerd met een geautomatiseerde test met een geseed artefact dat meerdere geblokkeerde sleutels voor één kandidaat bevat.
- Voor een storykandidaat zonder gekoppeld geblokkeerd `dependson_resolution`-artefact-item (geen artefact voor de iteratie, geen matchend `backlogId`, of `blocked: false` in het artefact) retourneert de API `blocked: false` en `blockedReason: null`; geverifieerd met een geautomatiseerde test.
- De uitbreiding wijzigt geen bestaande endpoint-URL's of -methoden en breekt geen bestaande consumer; alle bestaande backend-tests voor `/api/story-candidates` (o.a. `ProductFactoryApiTest.kt`) blijven slagen.
- Er is minstens één nieuwe geautomatiseerde unit- of integratietest toegevoegd (bijv. in `ProductFactoryApiTest.kt`, waar de storyqueue-endpoint daadwerkelijk getest wordt) die het nieuwe veld valideert voor zowel de geblokkeerde als de niet-geblokkeerde situatie.

## Aannames
- Koppeling tussen een `story_candidate`-rij en het bijbehorende `dependson_resolution`-artefact-item verloopt via `story_candidate.iteration_id` (naar `shadow_iteration_artifact.iteration_id`) en vervolgens `backlogId == story_candidate.id` binnen de JSON-array in `content_json`; er is geen directe `candidate_key`-kolom op `story_candidate` om op te matchen.
- Bevestigd door onderzoek: in de huidige pipeline wordt een kandidaat met `blocked == true` nooit in `story_candidate` opgeslagen (`persistValidatedResults` slaat geblokkeerde kandidaten expliciet over) — een geblokkeerde kandidaat verschijnt dus helemaal niet in de storyqueue. Voor alle bestaande/toekomstige rijen die de storyqueue-API vandaag teruggeeft, zal `blocked` in de praktijk dus altijd `false` zijn; het veld is desalniettemin correct en betekenisvol als read-only afgeleide, en de acceptatiecriteria voor de geblokkeerde situatie worden getest met direct geseede fixture-data (artefact + kandidaat-rij), niet via de volledige productiepijplijn. Deze aanname/beperking wordt in de commit-/PR-beschrijving vermeld.
- `dashboard-backend` hoeft niet aangepast te worden: het proxyt storykandidaten al als generieke `Map`, dus nieuwe velden op `StoryCandidateView` stromen automatisch door.
- Geen frontend-wijziging is vereist door deze story; het tonen van `blocked`/`blockedReason` in de UI valt buiten scope.

## Eindsamenvatting

I have everything needed. Now producing the final summary.

**Eindsamenvatting — product-factory-10: Read-only blokkeerreden-veld voor storykandidaten**

**Wat is gebouwd**
`StoryCandidateView` (productfactory-contracts) heeft twee nieuwe read-only velden gekregen: `blocked: Boolean` (default `false`) en `blockedReason: String?` (default `null`). In `StoryCandidateController.list()` (productfactory) worden deze gevuld door per storykandidaat het bestaande, al opgeslagen `dependson_resolution`-artefact (`shadow_iteration_artifact`) op te zoeken via de iteratie, het array-item met matchende `backlogId` te vinden, en de `rawValue`'s van onopgeloste `dependsOn`-referenties samen te voegen tot een leesbare tekst ("verwijzing naar onbekende sleutels: X, Y"). JSON-parsing is defensief: een ontbrekend artefact, lege array of onverwachte structuur breekt de response niet, alleen een warn-log.

**Keuzes**
- Vóór de wijziging is expliciet geverifieerd en gedocumenteerd dat `blocked`/`blockedReason` nog niet in de bestaande `/api/story-candidates`-response zaten.
- Puur een leeslaag: geen wijziging aan `resolveDependencyReferences`, `persistValidatedResults`, `ShadowIterationEngine`, endpoint-URL/-methode, database-schema of `dashboard-backend` (die proxyt `StoryCandidateView` al generiek door, dus de nieuwe velden stromen automatisch mee zonder aanpassing daar).
- `dashboard-frontend` is niet aangepast; er was geen UI-eis in deze story.

**Getest**
- Twee nieuwe testcases in `ProductFactoryApiTest.kt` met geseede fixture-data: (1) een kandidaat met meerdere onopgeloste `dependsOn`-sleutels → samengevoegde `blockedReason`; (2) een kandidaat zonder gekoppeld geblokkeerd artefact-item → `blocked:false`/`blockedReason:null`.
- Vangnet `mvn -B --no-transfer-progress clean verify` (en aanvullend `-Pquality`) draait groen: alle modules, 57 tests in de module, 0 failures/errors.
- Tester heeft de diff onafhankelijk geïnspecteerd, dezelfde vangnet-gate gedraaid (groen) en een preview-smoketest (`/actuator/health` → 200) uitgevoerd; geen bugs gevonden, geen codewijzigingen nodig.

**Bewust niet gedaan**
- Geen frontend-wijziging om `blocked`/`blockedReason` daadwerkelijk in de UI te tonen — buiten scope van deze story.
- Er is een bekende beperking: in de huidige pipeline slaat `persistValidatedResults` geblokkeerde kandidaten expliciet over, dus `blocked` is via de storyqueue-API in productiedata praktisch altijd `false`. De geblokkeerde situatie is daarom getest met direct geseede fixture-data, niet via de volledige productiepijplijn. Dit is een bewuste, gedocumenteerde keuze en geen tekortkoming van deze story.

<!-- deploy-summary:start -->
Storykandidaten in het systeem kunnen voortaan ook laten zien of ze geblokkeerd zijn doordat ze verwijzen naar een onbekende afhankelijkheid, en waarom. Er verandert verder niets aan hoe kandidaten worden aangemaakt of goedgekeurd, en gebruikers merken in het huidige scherm nog geen zichtbaar verschil. Deze informatie is nu wel beschikbaar voor toekomstig gebruik, bijvoorbeeld in een later te bouwen weergave.
<!-- deploy-summary:end -->
