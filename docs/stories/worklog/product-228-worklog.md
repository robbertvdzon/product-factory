# product-228 — Developer-worklog

## Stappenplan

- [x] Story, issuefeedback en factory-regels lezen; checkout en bestaande wijzigingen inventariseren.
- [x] Bestaande handmatige cyclusstart en previewdeploy analyseren tegen de acceptance criteria.
- [x] Ontbrekende implementatie en tests toevoegen, inclusief strikt begrensd previewherstel.
- [x] Gerichte tests draaien en bevindingen herstellen.
- [x] Volledig vangnet uit `docs/factory/development.md` zonder timeout uitvoeren.
- [x] Eigen review doen en verificatiebewijs vastleggen.

## Uitvoering

De developer-run is gestart naar aanleiding van testerfeedback dat de HEAD-frontend al was uitgerold,
maar de nieuwe runtime door een Flyway-checksummismatch op V25 niet startte. De bestaande storywijziging
wordt eerst integraal gecontroleerd; herstel moet beperkt blijven tot een aantoonbaar wegwerpbare
per-PR-previewdatabase en productie en acceptatie fail-closed laten.

De samenhangende storywijziging is op de actuele `main`-basis hersteld: een nullable en gesloten
`manual_start_origin`, strikte servervalidatie, een product-row-lock voor maximaal één start en
event, productscope in runtime en proxy, en de toegankelijke Flutterdialoog met één gedeelde
effectieve opdracht. Historische en automatische cycli houden `null`; compacte regels leiden geen
herkomst af. De bestaande detaildialoog toont alleen bekende opgeslagen herkomst.

Het previewherstel gebruikt een eigen `FlywayMigrationStrategy`. Normale migratie blijft het eerste
pad. Alleen een echte `FlywayValidateException` in de door `PreviewRuntimeConfig` vooraf fail-closed
gevalideerde dataset `PR_PREVIEW` schakelt `clean` lokaal in en bouwt het wegwerpschema opnieuw op.
Productie (`NONE`) en standing acceptance (`ACCEPTANCE`) werpen dezelfde fout door en behouden hun
data. De waarschuwing bevat alleen het niet-gevoelige PR-nummer.

Gerichte verificatie:

- backend: 12 tests, 0 failures/errors/skips (manual start, migratie, proxyroutes en Flywayherstel);
- Flutter: 7 storytests groen;
- echte Flutter-Web-DOM-test: exact één benoemde `alertdialog`, exitcode 0.

Volledig vangnet, alle agent-runnable commando's met exitcode 0:

- `mvn -B --no-transfer-progress clean verify`: 196 tests, 0 failures/errors/skips, `BUILD SUCCESS`;
- `flutter analyze`: geen issues;
- `flutter test`: 441 tests groen;
- versioned Flutter-Web-DOM-test: groen;
- Docker Engine-runner: 3 tests groen;
- frontend-image met veilige defaults en met expliciete metadata: beide 19/19 stappen geslaagd.

Eigen review controleerde de volledige diff op productscope, dubbele starts/events, inconsistent
trimmen, vrije tekst in fouten/logging/telemetrie, afgeleide historische herkomst, previewgrenzen,
conflictmarkers, whitespacefouten en onverwachte lockfilewijzigingen. Er zijn geen open bevindingen.
De niet-agent-runnable `agent-image-build` blijft conform `.factory/verification.yaml` voor CI.

## Reviewer — eerste ronde

- [blocker] Het destructieve Flyway-herstel controleert alleen dat `PreviewRuntimeConfig.dataset`
  `PR_PREVIEW` is voordat `clean()` wordt uitgevoerd. Die dataset is afgeleid van `PF_DB_URL`, maar
  `PreviewFlywayMigrationStrategy` valideert niet dat de daadwerkelijke Flyway-datasource en de te
  schonen schema's dezelfde gevalideerde wegwerpdatabase zijn. De test maakt de target-ontkoppeling
  concreet: `prPreviewConfig()` declareert de preview-Postgres-URL, terwijl de doorgegeven Flyway op
  een H2-datasource draait en die H2-database toch wordt opgeschoond. Bind de vrijgave van `clean()`
  fail-closed aan de JDBC-URL én schemas uit `flyway.configuration` en voeg een negatieve test toe
  waarin een PR-previewmarker met een afwijkende Flyway-target nooit `clean()` uitvoert.

Het agentworker-bewijs is verder geldig: de actuele HEAD-tree
`4613209d4beb06ddf1f253b81cab6dfc56ff3418` is gelijk aan `Tested worktree tree` en alle zeven
agent-runnable verificatiecommando's staan op `passed`.

## Developer — reviewherstel tweede ronde

- [x] Reviewerfeedback, factory-regels en actuele checkout opnieuw controleren.
- [x] Effectieve Flyway-JDBC-target en schema's fail-closed aan de gevalideerde PR-preview binden.
- [x] Positieve en negatieve hersteltests uitbreiden en gericht uitvoeren.
- [x] Volledig vangnet uit `docs/factory/development.md` zonder timeout uitvoeren.
- [x] Eigen review doen en definitief verificatiebewijs vastleggen.

Deze ronde herstelt uitsluitend de blocker uit reviewercomment 3592. De vrijgave van `clean()` moet
niet alleen op de omgevingsclassificatie vertrouwen, maar ook bewijzen dat Flyway zelf exact de
gevalideerde wegwerp-previewdatabase en de toegestane schema's target.

De herstelstrategie vergelijkt daarom de JDBC-URL uit de metadata van de daadwerkelijke
Flyway-datasource bytegelijk met de reeds gevalideerde `PF_DB_URL`. Daarnaast moet Flyway expliciet
alleen `public` als default- en cleanschema configureren. Bij ontbrekende of afwijkende metadata,
URL of schema wordt de oorspronkelijke validatiefout doorgeworpen en blijft `clean()` onbereikbaar.
Gerichte tests bewijzen zowel succesvol herstel van een overeenkomstige testtarget als behoud van
de oude tabel bij een afwijkende JDBC-target en bij een afwijkend schema: 14 tests, 0 failures/errors.

De eerste volledige Maven-run vond na de main-merge twee migraties op V27: de productmediamigratie
uit main en de handmatige-startmigratie uit deze story. Conform de mergeconventie blijft main op V27
en is de storymigratie naar de eerstvolgende vrije versie V28 verplaatst; de volgende volledige run
wordt opnieuw met `clean verify` opgebouwd zodat geen oud migratiebestand in buildoutput achterblijft.

Definitief volledig vangnet, alle agent-runnable commando's met exitcode 0:

- `mvn -B --no-transfer-progress clean verify`: 198 tests, 0 failures/errors/skips, `BUILD SUCCESS`;
- `flutter analyze`: geen issues;
- `flutter test`: 441 tests groen;
- versioned Flutter-Web-DOM-test: benoemde `alertdialog` bewezen;
- Docker Engine-runner: 3 tests groen;
- frontend-image met veilige defaults en met expliciete metadata: beide 19/19 stappen geslaagd.

De afsluitende review controleerde de volledige wijziging op het bereik van `clean()`, exacte
datasource-URL- en schemavergelijking, gesloten falen bij ontbrekende metadata, migratievolgorde,
gevoelige data in logging, conflictmarkers, whitespacefouten en onverwachte buildwijzigingen. De
productie- en acceptatiepaden behouden de oorspronkelijke Flyway-validatiefout; alleen de al
gevalideerde PR-preview met een exact overeenkomstige effectieve target kan worden opgeschoond.
