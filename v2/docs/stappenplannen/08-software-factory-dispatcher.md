# Stap 8 — Software Factory-dispatcher

## Doel

Verbind de geprioriteerde backlog met Software Factory zonder AI of inhoudelijke beslislogica in de
dispatcher te plaatsen.

## Globale scope

- Implementeer de dispatcherprovider achter de in stap 1 vastgelegde API en de duurzame
  `DeliveryAttempt`s.
- Maak `runDispatchSession()` zowel gepland als handmatig via UI en REST beschikbaar, met maximaal
  één modulebrede uitvoering tegelijk.
- Verwerk in één sessie alle geconfigureerde producten en verstuur per product alleen de bovenste
  uitvoerbare story wanneer Software Factory voor dat product geen open story heeft.
- Lever iedere story zelfstandig aan, inclusief acceptatiecriteria, UX en benodigde assets.
- Verwerk externe status en afronding idempotent via de publieke commands van Productplanning.
- Laat tijdelijke leveringsfouten met begrensde retries door de dispatcher zelf afhandelen en maak
  blijvende fouten operationeel zichtbaar.
- Behandel weigering van een contractgeldig storypakket als technische contractfout: blokkeer het
  product en meld het operationeel, maar wijzig geen storyinhoud en maak geen planningswerk.
- Gebruik in acceptatie de bestuurbare `MockSoftwareFactory` en voeg dispatcherstatus aan de UI toe.

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
de echte koppeling staat gecontroleerd op productie.
