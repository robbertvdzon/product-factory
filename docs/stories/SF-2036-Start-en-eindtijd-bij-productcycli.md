# SF-2036 - Start en eindtijd bij productcycli

## Story

Start en eindtijd bij productcycli

<!-- refined-by-factory -->

## Samenvatting

In het dashboard zie je nu een lange lijst met alle productcycli en andere overzichten, zonder tijdsinformatie. Deze story voegt twee dingen toe.

Ten eerste zie je bij elke productcyclus wanneer die gestart is (datum en tijd) en hoe lang die in totaal geduurd heeft. Loopt een cyclus nog, dan zie je hoe lang die al bezig is.

Ten tweede worden de lijsten korter: standaard toon je de 5 nieuwste items, met een knop 'Meer' om er telkens 10 bij te laden. Dat geldt voor de productcycli en voor de andere lijsten op het overzicht, zodat de pagina overzichtelijk blijft.

## Scope

In scope (alleen de Flutter-frontend, `dashboard-frontend/lib/main.dart`):

1. **Start- en doorlooptijd bij productcycli** — In de lijst "Productcycli en onderzoekssessies" (en in de detaildialoog `IterationSessionDialog`) per cyclus tonen:
   - startdatum + starttijd, gebaseerd op `startedAt`; is die leeg, dan `createdAt` als fallback;
   - doorlooptijd = `completedAt - startedAt`; is `completedAt` leeg (cyclus loopt nog of is nooit afgerond), dan de tijd tot nu, herkenbaar als "loopt nog";
   - is `startedAt` leeg én de cyclus is nog niet gestart, dan geen doorlooptijd tonen (streepje of leeg).
2. **Gedeelde formatteerhelpers** in de frontend voor datum/tijd en voor duur, herbruikbaar door alle secties.
3. **Beperkte lijstweergave met 'Meer'-knop** voor de overzichtslijsten op de overzichtspagina: Producten, Productcycli en onderzoekssessies, Software Factory-stories, Benodigde access tokens, Storywachtrij (per subsectie Fout/Bezig/In wachtrij/Klaar) en Workspace-publicaties.
   - Standaard 5 items zichtbaar; elke klik op 'Meer' toont 10 items extra.
   - De knop verdwijnt zodra alles zichtbaar is; bij ≤5 items is er geen knop.
   - Bij de knop staat hoeveel er nog verborgen zijn (bv. "Meer (nog 12)").
   - Elke sectie heeft zijn eigen, onafhankelijke teller.
4. **Sortering nieuwste eerst** voor de lijsten die een bruikbaar tijdstempel hebben (productcycli, stories, deliveries, human actions, publicaties), zodat "de laatste 5" ook echt de laatste 5 zijn — nu wordt er per product geaggregeerd zonder globale sortering.
5. **Aanvullen `docs/factory/`** met concrete repo-informatie (deze docs zijn nog template-stubs): in `technical-spec.md` de stack van dashboard-frontend/-backend en de build/test-commando's, in `functional-spec.md` een korte beschrijving van de overzichtspagina en zijn lijsten.

Buiten scope:

- Server-side paginering: er komen géén `limit`/`offset`-parameters op de backend-endpoints; het beperken gebeurt in de frontend op de al opgehaalde data.
- Wijzigingen aan het datamodel of database (`startedAt`/`completedAt` bestaan al).
- Zoeken, filteren of sorteren-door-de-gebruiker.
- Nieuwe dependencies (zoals `intl`); formatteren gebeurt met bestaande Dart-middelen.

## Acceptance criteria

1. In de sectie "Productcycli en onderzoekssessies" toont elke rij de startdatum en -tijd van de cyclus.
2. Elke rij toont de doorlooptijd van de cyclus; voor een afgeronde cyclus is dat het verschil tussen start en afronding, leesbaar geformatteerd (bv. `2u 13m`, `4m 12s`, `35s`).
3. Voor een nog lopende cyclus toont de rij de tijd sinds de start met een duidelijke aanduiding dat hij nog loopt; deze waarde loopt mee met de bestaande auto-refresh.
4. Voor een cyclus die nog niet gestart is (`startedAt` leeg) toont de rij de aanmaaktijd als starttijd en geen doorlooptijd.
5. Datum en tijd worden getoond in de lokale tijdzone van de browser in een vast, leesbaar formaat (bv. `08-08-2026 19:13`), niet als ruwe ISO-string.
6. De lijst met productcycli toont bij het openen maximaal 5 items, de nieuwste bovenaan.
7. Onder een ingekorte lijst staat een 'Meer'-knop met het aantal resterende items; elke klik toont 10 items extra en de knop verdwijnt zodra alles zichtbaar is.
8. Dezelfde beperking + 'Meer'-knop geldt voor Producten, Software Factory-stories, Benodigde access tokens, elke subsectie van de Storywachtrij en Workspace-publicaties, elk met een eigen onafhankelijke teller.
9. De auto-refresh (elke 5 s) laat een uitgeklapte lijst uitgeklapt: na een refresh zijn nog steeds evenveel items zichtbaar als voor de refresh.
10. Nieuwe items die tijdens een refresh binnenkomen verschijnen bovenaan de betreffende lijst zonder dat de gebruiker de 'Meer'-knop opnieuw hoeft te gebruiken voor wat al zichtbaar was.
11. De metric-tegels bovenaan blijven het totale aantal items tonen, niet het aantal zichtbare items.
12. `docs/factory/technical-spec.md` en `docs/factory/functional-spec.md` bevatten concrete repo-informatie (stack, build/test-commando's, beschrijving van de overzichtspagina) in plaats van de template-tekst.
13. De bestaande frontend-tests draaien groen en er is minimaal één widgettest die de 'Meer'-knop en de duurformattering afdekt.

## Aannames

- "Productcyclus" in de frontend = shadow iteration (`ShadowIterationView`); de al bestaande velden `createdAt`, `startedAt` en `completedAt` zijn de bron voor start- en doorlooptijd. Er wordt geen nieuw duur-veld aan de API toegevoegd; de frontend rekent het verschil zelf uit.
- "En voor de stories en andere lijsten net zo" slaat op alle overzichtslijsten op de overzichtspagina; secties binnen de detaildialoog (stappen, artifacts, dossier) blijven ongewijzigd, omdat die al inklapbaar zijn.
- 5 initieel en telkens +10 zijn vaste waarden in de frontend, geen instelling.
- Voor de lijst "Producten" bestaat geen zinvolle "laatste 5"-volgorde-wens; daar blijft de huidige volgorde (op slug) staan en geldt alleen de 5/+10-beperking.
- Duurweergave gebruikt maximaal twee eenheden (uren+minuten, minuten+seconden of alleen seconden) om de rij compact te houden.
- Omdat alle data toch al in één call binnenkomt, is client-side afkappen functioneel gelijkwaardig aan server-side paginering en houdt het de wijziging klein en risicoloos.

## Eindsamenvatting

# Eindsamenvatting SF-2036 — Start- en eindtijd bij productcycli

## Wat is gebouwd

**1. Start- en doorlooptijd bij productcycli**
Elke productcyclus in de lijst "Productcycli en onderzoekssessies" én in de detaildialoog toont nu de starttijd (`gestart <datum tijd>`) en de doorlooptijd:
- afgeronde cyclus: verschil tussen start en afronding, compact geformatteerd (`2u 13m`, `4m 12s`, `35s`);
- lopende cyclus: `loopt nog: <tijd sinds start>`, meelopend met de bestaande auto-refresh van 5 s;
- nog niet gestarte cyclus: aanmaaktijd als starttijd, geen doorlooptijd.

Datum/tijd staat overal in een vast, lokaal formaat (`dd-MM-yyyy HH:mm`); ook de tijdstempels bij stappen en artifacts in de detaildialoog zijn niet langer ruwe ISO-strings.

**2. Gedeelde formatteerhelpers**
Nieuw `lib/formatting.dart` met `parseInstant`, `formatDateTime`, `formatDuration`, `iterationTiming` en `sortedByNewestFirst` — defensief, zodat een leeg of onleesbaar tijdstempel nooit een lijst laat crashen.

**3. Kortere lijsten met 'Meer'-knop**
Nieuw `lib/limited_list.dart`: standaard 5 items zichtbaar, elke klik op 'Meer' toont er 10 bij, met het resterende aantal op de knop (`Meer (nog 12)`) die verdwijnt zodra alles zichtbaar is. Toegepast op Producten, Productcycli, Software Factory-stories, Benodigde access tokens, alle vier subsecties van de Storywachtrij en Workspace-publicaties — elk met een eigen teller. De tellers staan buiten de `FutureBuilder`, zodat een refresh de uitklapstand behoudt. De metric-tegels bovenaan blijven de totalen tonen.

**4. Sortering nieuwste eerst** vóór het afkappen voor iteraties, stories, deliveries en human actions.

**5. Factory-docs gevuld** — `technical-spec.md`, `functional-spec.md` en `development.md` bevatten nu de echte stack, module-indeling, verificatiecommando's en een beschrijving van de overzichtspagina in plaats van template-tekst.

## Gemaakte keuzes

- **Geen nieuwe dependency** (`intl`): voor één vast formaat volstaan bestaande Dart-middelen.
- **Client-side beperken**, geen server-side paginering — alle data komt toch al in één call binnen.
- **Toolchain-blocker (uit review)**: `pubspec.lock` eiste Dart `>=3.10.0-0` terwijl het frontend-Docker-image nog op Flutter 3.35.0 stond. Opgelost door de image-base gelijk te trekken met CI (`ghcr.io/cirruslabs/flutter:3.44.0`) in plaats van de lock terug te draaien — terugdraaien bleek niet houdbaar, want `flutter pub get` op de CI-toolchain herschrijft de lock direct weer. Aanvullend is `dashboard-frontend-image-build` opgenomen in `.factory/verification.yaml` zodat dit gat in het vangnet zichtbaar is.
- **Teller begrensd** (`nextVisibleCount`): een lijst die tussen refreshes krimpt en weer groeit klapt niet verder open dan de gebruiker heeft aangeklikt.
- **Producten** houden hun bestaande volgorde (op slug), conform de aannames in de story.

## Wat is getest

- `flutter test` (dashboard-frontend): **25/25 groen** — unittests voor parsen/formatteren/duur/timing/sortering plus widgettests voor 5 zichtbaar, resterend aantal, +10 per klik, knop verdwijnt en uitklapstand overleeft een refresh.
- `flutter analyze`: **No issues found!**
- `mvn -B clean verify` (root): **BUILD SUCCESS**, 0 failures, 0 errors.
- `flutter build web`: slaagt.
- Alle 13 acceptatiecriteria zijn door reviewer én tester expliciet nagelopen en afgedekt.

## Bewust niet gedaan / aandachtspunten

- **Workspace-publicaties krijgen geen 'nieuwste eerst'-sortering**: het contract heeft daar geen tijdstempel. Alleen de 5/+10-beperking is toegepast.
- **Geen browser-E2E/screenshots**: er is geen preview-URL geconfigureerd en geen browser in de container. De Docker-imagebuilds staan als `agentRunnable: false` en blijven CI-dekking.
- **Open, niet-blokkerende punten voor een volgende story**: `pubspec.yaml` staat nog op `sdk: ^3.9.0` terwijl de lock `>=3.10.0-0` eist; de commandotabel in `technical-spec.md` mist `dashboard-frontend-image-build`; de doc-comment van `sortedByNewestFirst` claimt stabiliteit die `List.sort` in Dart niet garandeert. De widgettests draaien op een eigen harness, niet op `OverviewPage` zelf — de bedrading van de sectiesleutels is alleen door lezen geverifieerd.

<!-- deploy-summary:start -->
Bij elke productcyclus in het dashboard zie je nu wanneer die is gestart en hoe lang die heeft geduurd; loopt een cyclus nog, dan zie je hoe lang die al bezig is. De overzichtslijsten zijn korter geworden: je ziet de 5 nieuwste items en kunt er met één klik telkens 10 bij laden. Daardoor blijft de overzichtspagina rustig en zie je het belangrijkste meteen bovenaan.
<!-- deploy-summary:end -->
