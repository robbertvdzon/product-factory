# Worklog product-192

## Stappenplan

- [x] Bestaande acceptatie-, preview-, opslag- en frontendconfiguratie inventariseren.
- [x] Gesloten fixturecatalogus, validatie en transactionele idempotente opslag implementeren.
- [x] Acceptatie-activering en datasetscheiding configureren zonder preview of productie te wijzigen.
- [x] Acceptatiegebonden frontendmelding implementeren.
- [x] Backend- en frontendtests voor determinisme, isolatie, veiligheid en toegankelijkheid toevoegen.
- [x] Self-review uitvoeren en documentatie van de implementatiekeuzes bijwerken.
- [x] Het volledige factory-vangnet succesvol uitvoeren.

## Uitvoering

De developer-run is gestart met het lezen van de taak- en factory-instructies. De implementatie wordt
begrensd tot de bestaande tabellen en API-contracten; fixtures en melding worden uitsluitend via een
expliciete acceptatiemarkering geactiveerd.

De bestaande synthetische startup routeert nu op een expliciete datasetsoort. PR-previews houden hun
bestaande `hkh-autopilot`-seed; alleen de standing acceptatieomgeving laadt de versie `acceptance-product-
factory-cycles-v1`. Een verse acceptatiedatabase krijgt binnen dezelfde transactie een gepauzeerde lokale
`product-factory`-FK-context. Een reeds bestaand product blijft ongemoeid, zodat de bestaande reconcile van
vaste projectvelden niet met fixture-idempotentie botst.

De fixturecatalogus bevat vier cycli, één handmatig beslisrecord, twee kandidaten en twee voltooide
leveringen met vaste identifiers en UTC-tijden. Voor opslag vergelijkt de validator recursief alle velden en
waarden met de gesloten catalogus. De opslag controleert ook gereserveerde unieke sleutels en valideert na de
insert opnieuw; iedere afwijking rolt de volledige transactie terug. De voltooide leveringen zijn al als
bevestigd en geëvalueerd gemarkeerd, waardoor bestaande reconciliatie- en evaluatiepaden ze niet oppakken.

De Flutter-build heeft een standaard uitgeschakelde `ACCEPTANCE_DATASET`-define. Alleen de afzonderlijk
getagde acceptance-build zet die aan en toont direct onder `Productoverzicht` de statische, responsieve en
semantische scenariomelding. Productie en PR-preview erven de veilige standaard `false`.

Gerichte controles: 23 backendtests en 4 frontendtests zijn groen. De eerste losse Maven-modulecheck was
niet representatief doordat lokale contractartefacten verouderd waren; de herhaalde schone reactorcheck met
afhankelijke modules was groen.

Volledig vangnet afgerond: `mvn -B --no-transfer-progress clean verify` bouwde alle zes reactormodules met
0 failures en 0 errors; `flutter analyze` meldde geen issues; `flutter test` sloot af met 296 geslaagde tests.
Na de laatste plaatsingsreview (melding letterlijk direct na de overzichtskop) zijn analyze en alle 296
frontendtests opnieuw succesvol uitgevoerd.
