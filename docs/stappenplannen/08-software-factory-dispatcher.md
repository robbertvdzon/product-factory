# Stap 8 — Software Factory-dispatcher

## Doel en eindtoestand

Verbind de geprioriteerde backlog deterministisch met Software Factory. Na deze stap wordt per
product hoogstens één uitvoerbare story tegelijk verstuurd, worden externe `OPEN`, `DONE` en
`CANCELLED` idempotent verwerkt en veroorzaken netwerkfouten, crashes of herhalingen nooit een
tweede externe story. De dispatcher bevat geen AI, inhoudelijke planning of herprioritering.

## Ingangseisen

- Stap 7 staat gezond op acceptatie en productie.
- Productplanning biedt de definitieve atomaire reserverings- en leveringcommands.
- Kwaliteitsbewaking kan storyverificatie en bugfixhertest ontvangen.
- De Software Factory-v2-base-URL en het bestaande gescopete productietoken zijn beschikbaar via
  het gesloten configuratie-/secretpad, zonder waarden te tonen.
- `GET /api/integrations/v2/status` en de vereiste v2-routes zijn vooraf met een veilige read-only
  contractsmoke gecontroleerd. Een ontbrekend v2-contract blokkeert de echte productieactivatie.

## Normatieve bronnen

- [Software Factory-dispatcher](../processen/software-factory-dispatcher.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Configuratie en secrets](../platform/configuratie-en-secrets.md)
- [Frontend](../stakeholder/frontend.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)

## Concrete opleveringen

### Module, configuratie en gegevens

- Implementeer de enige dispatcherprovider achter het publieke dispatchercontract en registreer
  artifact, versie en broncommit in `ImplementationManifest`.
- Voeg `PF_SOFTWARE_FACTORY_URL` als niet-geheim toe. Gebruik uitsluitend
  `https://dashboard.vdzonsoftware.nl/api/integrations/v2` in productie, tenzij de normatieve
  specificatie bewust wordt gewijzigd.
- Activeer pas in deze stap `PF_SOFTWARE_FACTORY_MODE=REAL` en het bestaande
  `PF_SOFTWARE_FACTORY_TOKEN`. Breid alleen de gesloten keylijst van `deploy/seal-secrets.sh` uit;
  maak geen tweede secretpad en verplaats of toon `secrets.env` niet.
- Productie faalt gesloten bij ontbrekende URL/token, onveilige URL of v1-configuratie. Acceptatie
  gebruikt alleen `MockSoftwareFactory` en bezit geen productietoken.
- Voeg migraties toe voor dispatchersessies, `DeliveryAttempt`, bevroren storypakket/hash,
  reserverings- en externe idempotentiesleutel, externe `storyKey`, statusprojectie,
  poging/retrytijd, foutclassificatie en herstelbare uitgaande commandeffecten.
- Unieke constraints garanderen maximaal één logisch deliveryattempt en één externe story per
  reservering/idempotentiesleutel.

### Publieke dispatcher-API

- Implementeer `runDispatchSession(productId)` voor zowel schedule als bevoegde handmatige UI/REST.
- Implementeer get/find van sessies en deliveryattempts met product-, status- en periodefilters,
  nieuwste eerst.
- Per product mag maximaal één dispatchercall actief zijn; verschillende producten mogen parallel.
- Een run verwerkt precies één product en is een succesvolle zichtbare no-op wanneer product
  inactief is, dispatching uitstaat, extern al open werk bestaat of geen story uitvoerbaar is.
- Een handmatige botsing geeft 409; een geplande botsing wordt later als overgeslagen vastgelegd.

### Verloop van één dispatchersessie

1. **Valideer product en configuratie.** Lees actief/dispatching en controleer adaptermode zonder
   secrets te loggen.
2. **Reconcileer eerst.** Zoek bestaande lokale attempts extern op hun idempotentiesleutel en
   verwerk `OPEN`, `DONE` of `CANCELLED` voordat nieuw werk wordt gekozen.
3. **Controleer open extern werk.** Verstuur niets wanneer Software Factory voor dit product al een
   open story heeft.
4. **Reserveer atomair.** Vraag Productplanning de bovenste uitvoerbare `TODO`-story te reserveren.
   De dispatcher leest geen planningstabellen en kiest niet zelf uit een eerder gelezen lijst.
5. **Bevries het pakket.** Bewaar exacte storyversie, acceptatiecriteria, UX, assets en volledige
   transportpayload vóór de externe call.
6. **Herstel vóór retry.** Zoek altijd eerst extern met dezelfde idempotentiesleutel. Bij aantoonbaar
   afwezig werk herbevestigt Productplanning de reservering tegen de actuele epicannulering. Bij
   onbekende externe toestand wordt niet aangemaakt en niet lokaal geannuleerd.
7. **Maak idempotent aan.** Verstuur exact het v2-request. Een verloren response of crash leidt bij
   hervatting via dezelfde sleutel naar dezelfde externe `storyKey`.
8. **Bevestig lokaal.** Meld verzending via het publieke planningscommand. Een tijdelijke lokale
   fout na externe aanmaak wordt met dezelfde keys hervat.
9. **Verwerk terminale status.** `DONE` vereist `deliveredCommitSha` en roept developed aan;
   `CANCELLED` roept cancelled aan. Productplanning maakt vervolgens het juiste kwaliteitswerk.

### Exact extern transportcontract

- Gebruik uitsluitend de v2-routes uit de dispatcherspecificatie; geen v1-route, vraag- of
  answer-endpoint.
- Stuur bij aanmaak alleen `title`, één volledige `description` en binaire `attachments` volgens
  het externe contract. Bouw `description` deterministisch uit de rijke interne story, inclusief
  volledige acceptatiecriteria en UX.
- Stuur geen client-side `contentHash`, Product Factory-status of interne domeinvelden die het
  externe contract niet kent.
- Voeg geen eigen MIME-allowlist of integratiespecifieke limiet toe buiten de twee normatieve
  contracten; map contractgeldige assets zonder inhoudelijke aanpassing.
- Accepteer extern alleen `OPEN`, `DONE` en `CANCELLED`; `DONE` zonder volledige commit-SHA is een
  contractfout en wordt niet lokaal als oplevering verwerkt.

### Fouten en herstel

- Tijdelijke transport- en beschikbaarheidsfouten krijgen begrensde dispatcherretries met zichtbare
  volgende retry; zij maken geen planworkitem en wijzigen geen storyinhoud.
- Configuratie- of autorisatiefouten blokkeren het product zichtbaar totdat configuratie is
  hersteld; blijf niet onbeperkt agressief retryen.
- Weigering van een contractgeldig pakket of onverwachte response is een technische contractbreuk:
  blokkeer dispatch voor dat product, bewaar veilige diagnose en maak geen aangepaste story.
- Een fout na externe aanmaak wordt door extern opzoeken hersteld. Nooit opnieuw aanmaken op basis
  van alleen het ontbreken van een lokale `storyKey`.
- Annulering van een epic en een oude reservering volgen altijd het herbevestigingscontract uit
  Productplanning; een dagenoude retry wint niet stilzwijgend van de annulering.

### HTTP, frontend, Testbed en operatie

- Voeg **Nu starten** voor dispatcher toe via dezelfde publieke functie als de toekomstige schedule.
- Toon in Planning externe status, `storyKey`, deliveryattempt, reservering, blijvende blokkade,
  volgende retry en bij `DONE` de `deliveredCommitSha`.
- Toon in Operatie sessies, attempts, idempotentiesleutel/hash zonder gevoelige payload,
  externe referentie, status, retry, veilige fout en uitgaande lokale commandstatus.
- Implementeer `MockSoftwareFactory` met exact hetzelfde interne adaptercontract en dezelfde
  request-/responsevormen als de echte adapter.
- Voeg Test Control-acties toe voor afronden met commit-SHA, annuleren, volgende call tijdelijk laten
  falen, verloren response simuleren, contractbreuk en reset. Deze routes bestaan niet in productie.

## Uitvoeringsvolgorde

1. Verifieer het externe v2-contract read-only en maak publieke/interne contracten gelijk.
2. Voeg provider, manifestregistratie, configuratieguards en secretkeyvalidatie toe.
3. Voeg migraties, sessies, deliveryattempts, constraints en herstelbare effecten toe.
4. Implementeer eerst `MockSoftwareFactory` en de contracttestsuite.
5. Implementeer reserveren, pakketmapping, idempotente create/reconcile en statusverwerking.
6. Implementeer de echte HTTP-adapter en draai dezelfde contracttests tegen een v2-stub.
7. Voeg HTTP, frontend, Operatie en Testbedbediening toe.
8. Bewijs in acceptatie de volledige mockroute.
9. Activeer productieconfiguratie via de normale release en voer een gecontroleerde credentialloze
   contractsmoke en één expliciet daarvoor bedoelde storylevering uit.

## Verplichte automatische en operationele bewijzen

- alleen de bovenste uitvoerbare story wordt gereserveerd en er is maximaal één open externe story
  per product;
- dubbele start, verloren response, crash vóór/na lokale bevestiging en herhaling leveren dezelfde
  `storyKey` en geen dubbel attachment;
- annulering en reserveringsherbevestiging zijn race-safe, ook na langdurige storing;
- `DONE` zonder SHA faalt, geldige `DONE` maakt exact één kwaliteitsworkitem en `CANCELLED` geen
  storytest maar zo nodig een feitelijke epicbeoordeling;
- tijdelijke, auth/configuratie- en contractfouten volgen ieder hun eigen herstelbeleid en maken
  nooit planwerk;
- mock en echte adapter voldoen aan dezelfde contracttests;
- productie-`GET /status` meldt `connected=true` en `apiVersion=2`, zonder secretwaarde te tonen;
- een identieke gecontroleerde herhaling geeft HTTP 200 met dezelfde `storyKey`;
- REST/frontend/Testbed/PostgreSQL/releasecontrole volgens de vaste afronding.

## Aanbevolen commitgrenzen

1. contracten, configuratie en provider;
2. migraties, sessie en deliveryattempt;
3. mockadapter en contracttests;
4. dispatch/reconcile/status en echte adapter;
5. frontend, Testbed, Operatie, documentatie en releasecorrecties.

## Buiten scope

De dispatcher start geen agents, plant of herprioriteert geen stories en wijzigt geen storyinhoud.
Er komt geen tweede Factory-integratie, vraag-/antwoordroute of geavanceerde dispatchstrategie.
Automatische scheduleclaims blijven tot stap 9 uit.

## Definitie van klaar

Stap 8 is klaar wanneer storypakketten aantoonbaar één voor één en idempotent naar Software Factory
v2 gaan, externe afronding/annulering de planning en kwaliteit correct bijwerkt, alle fout- en
crashroutes zonder duplicaat herstellen, de mockroute op acceptatie werkt, de echte v2-koppeling op
productie gecontroleerd verbonden is en dezelfde release op beide omgevingen gezond staat.
