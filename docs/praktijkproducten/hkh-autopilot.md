# Eerste praktijkproduct — HKH Autopilot

## Doel van dit document

`hkh-autopilot` is het eerste echte product waarmee Product Factory tijdens de ontwikkeling en bij
de afsluitende MVP-ketentest wordt gebruikt. Dit document legt de gedeelde, niet-geheime
productconfiguratie en veiligheidsgrenzen vast, zodat stappen 2 tot en met 9 niet ieder hun eigen
aannames maken.

Dit is geen hardgecodeerde productdefault en geen productieseed. Het product wordt vanaf stap 2 via
de gewone publieke Product Factory-commands of UI aangemaakt en beheerd. Acceptatie blijft voor
geautomatiseerde tests synthetische datasets gebruiken. Externe dispatching en schedules blijven
uitgeschakeld totdat de betreffende implementatiestap ze bewust activeert.

## Identiteit en repository

| Veld | Waarde |
|---|---|
| Stabiel product-ID | `hkh-autopilot` |
| Weergavenaam | `Historisch Heemskerk Autopilot` |
| Publieke Git-repository | `https://github.com/robbertvdzon/hkh-autopilot.git` |
| Doelbranch | `main` |
| Eerste schone basis voor de nieuwe Product Factory | `af902c33728cb2363be9e47707c80ee3365b2c42` |
| Historische back-up van de vorige Factory-flow | branch `main-by-old-productfactory` |

De productopdracht gebruikt de actuele productvisie en specificaties uit de repository als bron.
Samengevat maakt het product de geschiedenis van Heemskerk toegankelijk in bredere historische
context. Bronherleidbaarheid, meerstemmigheid, toegankelijkheid, privacy en gecontroleerd hergebruik
zijn harde inhoudelijke grenzen. De precieze tekst wordt bij productaanmaak via de gewone
Stakeholderbediening vastgelegd en blijft daarna geversioneerd.

## Testbare omgevingen

| Omgeving | Gebruikersfrontend | Beheerfrontend | Revisionendpoint |
|---|---|---|---|
| Acceptatie | `https://hkh-autopilot-acceptance.vdzonsoftware.nl` | `https://hkh-autopilot-admin-acceptance.vdzonsoftware.nl` | `GET /api/version` op dezelfde host |
| Productie | `https://hkh-autopilot.vdzonsoftware.nl` | `https://hkh-autopilot-admin.vdzonsoftware.nl` | `GET /api/version` op dezelfde host |

Het revisionendpoint retourneert JSON met het veld `commit`. Product Factory leest daaruit exact
de volledige Git-SHA en vergelijkt die met `deliveredCommitSha`. Het veld `version` is alleen
weergavemetadata en is niet de revisionbron.

De gebruikers- en beheerfrontends proxyen `/api/**` en `/actuator/**` same-origin naar hun eigen
backend. Daardoor gebruikt een test altijd de host van het werkelijk geteste scherm en niet een
losse interne backendroute.

## Toegangs- en datagrenzen

- Acceptatie is de primaire omgeving voor geautomatiseerde browser- en mutatietests. De
  beheerfrontend gebruikt daar de afgeschermde preview-adminmodus en uitsluitend geïsoleerde
  acceptatiedata.
- Productie is voor autonome controles standaard read-only. Toegestaan zijn bereikbaarheid,
  publieke gebruikersflows, `GET /api/version` en healthcontrole zonder gegevenswijziging.
- Productiebeheer vereist Google-login met een toegestaan account. Een autonome taak voert daar
  geen mutatie uit en omzeilt de login nooit. Een ruimere productiehandeling vereist vooraf een
  expliciet begrensde Stakeholderopdracht.
- Secrets, tokens, sessies en credentialwaarden worden niet in deze configuratie, Product Factory,
  prompts, resultaten of artifacts opgeslagen. Agent Runtime levert alleen expliciet verleende
  `HKH__*`-projectcredentials aan een geschikte worker.
- Privacy- en publicatiegrenzen uit de HKH-specificaties blijven fail-closed. Synthetische
  acceptatiedata mag niet als productie- of persoonsgegevens worden behandeld.

## Gebruik per ontwikkelstap

- **Stap 2:** maak het product via het publieke contract aan en leg opdracht, omgevingen,
  revisionregel en uitgeschakelde schedules vast. Gebruik geen automatische productieseed.
- **Stappen 3–7:** gebruik het product-ID voor rolgeheugen, AI-instellingen, Runtime-grants en
  procescontext. Automatiseer externe scenario's met Testbed en Runtime `MOCKED`; houd echte
  Software Factory-dispatching uit.
- **Stap 8:** verifieer het dispatchercontract eerst tegen `MockSoftwareFactory`. Gebruik voor de
  gecontroleerde echte proef uitsluitend een bewust gekozen HKH-story met een stabiele
  idempotentiesleutel.
- **Stap 9:** gebruik HKH voor de afsluitende echte keten. Controleer vóór kwaliteitswerk via
  `/api/version` dat de opgeleverde commit werkelijk op acceptatie staat. Activeer schedules en
  dispatching alleen bewust via de normale Stakeholderbediening.

## Veranderlijke operationele toestand

Live deploymentrevisions, actuele Argo CD-status, gekozen AI-modellen, Runtime-workerbeschikbaarheid
en schedule-tijden horen niet als vaste waarde in dit document. Product Factory leest of beheert
die via de daarvoor bedoelde publieke interfaces. Een wijziging van een URL, revisionregel,
repository of veiligheidsgrens wordt hier en in de geversioneerde productconfiguratie samen
bijgewerkt.
