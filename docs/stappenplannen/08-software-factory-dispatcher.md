# Stap 8 — Software Factory-dispatcher

## Doel

Verbind de geprioriteerde backlog met Software Factory zonder AI of inhoudelijke beslislogica in de
dispatcher te plaatsen.

## Globale scope

- Implementeer de dispatcherprovider achter het in stap 1 vastgelegde dispatchercontract in
  `product-factory-api` en de duurzame `DeliveryAttempt`s.
- Implementeer de echte HTTP-adapter uitsluitend tegen
  `https://dashboard.vdzonsoftware.nl/api/integrations/v2` en de routevormen uit de
  dispatcherspecificatie. Gebruik geen v1-route of answer-endpoint.
- Voeg `PF_SOFTWARE_FACTORY_URL` toe als niet-geheime configuratie en activeer in productie pas in
  deze stap `PF_SOFTWARE_FACTORY_MODE=REAL` en het al in `secrets.env` bewaarde
  `PF_SOFTWARE_FACTORY_TOKEN`. Breid daarvoor de gesloten sleutellijst van het bestaande
  `deploy/seal-secrets.sh` uit; introduceer geen ander encryptiepad en verplaats `secrets.env` niet.
- Maak `runDispatchSession(productId)` zowel gepland als handmatig via UI en REST beschikbaar, met
  maximaal één uitvoering per product en parallelle sessies voor verschillende producten.
- Verwerk in één sessie precies één product en verstuur alleen de bovenste uitvoerbare story wanneer
  Software Factory voor dat product geen open story heeft.
- Reserveer die story eerst atomair bij Productplanning, zodat annulering en verzending een
  eenduidige volgorde hebben en een eerder gelezen `TODO`-story niet alsnog buiten de state machine
  wordt verstuurd.
- Zoek vóór iedere retry eerst extern op dezelfde idempotentiesleutel. Herbevestig een aantoonbaar
  nog niet aangemaakte reservering tegen de actuele epicanulering en verstuur haar niet wanneer de
  epic inmiddels is gestopt.
- Lever iedere story zelfstandig aan, inclusief acceptatiecriteria, UX en benodigde assets.
- Map die rijke interne story deterministisch naar het kleine externe request met `title`, één
  volledige `description` en alleen binaire `attachments`. Stuur geen client-side `contentHash` en
  voeg geen integratiespecifieke limiet of MIME-allowlist toe.
- Verwerk externe verzending, afronding en annulering idempotent via de publieke commands van
  Productplanning. Een extern geannuleerde story wordt lokaal `CANCELLED` en leidt na het overige
  werk tot een complete feitelijke epicbeoordeling.
- Beperk het Software Factory-contract tot accepteren en status `OPEN`, `DONE` of `CANCELLED`;
  vereis bij `DONE` een `deliveredCommitSha` en bied geen vragen- of antwoordroute.
- Laat tijdelijke leveringsfouten met begrensde retries door de dispatcher zelf afhandelen en maak
  blijvende fouten operationeel zichtbaar.
- Behandel weigering van een contractgeldig storypakket als technische contractfout: blokkeer het
  product en meld het operationeel, maar wijzig geen storyinhoud en maak geen planningswerk.
- Gebruik in acceptatie de bestuurbare `MockSoftwareFactory` en voeg dispatcherstatus aan de UI toe.
- Voeg contracttests toe die de mock en echte adapter tegen dezelfde v2-request- en responsevormen
  toetsen. Rond af met een gecontroleerde echte story, een attachment en een identieke herhaling
  die dezelfde Software Factory-story retourneert.

## Buiten scope

De dispatcher start geen agents, herprioriteert geen backlog en wijzigt geen stories rechtstreeks.
Een geavanceerde dispatchstrategie of tweede externe Factory-integratie hoort niet bij de MVP.

## Specificaties

- [Software Factory-dispatcher](../processen/software-factory-dispatcher.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)

## Klaar wanneer

Stories aantoonbaar per product één voor één naar de echte Software Factory kunnen gaan, afgeronde
leveringen de planning correct bijwerken en fouten geen dubbele externe story veroorzaken. Geplande,
handmatige UI- en REST-starts volgen hetzelfde contract. De mockscenario's werken op acceptatie en
de echte koppeling staat gecontroleerd op productie. `GET /status` retourneert daar `connected=true`
en `apiVersion=2`; de herhaalde end-to-endaanmaak geeft HTTP 200 met dezelfde `storyKey` en zonder
tweede story of dubbel attachment.
