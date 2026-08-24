# Stap 5 — Productontwerp MVP

## Doel

Laat één Productontwerperagent alle relevante productinput verwerken tot complete, duidelijke en
behapbare epics met UX-ontwerp. Productontwerp maakt geen stories.

## Globale scope

- Implementeer uitsluitend de MVP-provider achter de in stap 1 vastgelegde Productontwerp-API.
- Implementeer geplande en handmatige `runProcessSession()` met maximaal één actieve sessie.
- Geef de MVP-agent de voorgeschreven read-only input, haar eigen geheugen en de centraal gekozen
  AI-configuratie.
- Gebruik in deze tussenstap alleen input van reeds geactiveerde capabilities. De API-contracten van
  Productplanning en Kwaliteitsbewaking bestaan al, maar hun input is pas beschikbaar en wordt pas
  aangesloten wanneer hun implementaties in stap 6 en 7 actief worden.
- Valideer en bewaar complete epics, inclusief scope, gebruikersverbetering, succescriteria en UX.
- Ondersteun het verbeteren van nog beschikbare epics en het bevriezen van opgepakte versies.
- Voeg epicoverzicht, detail, sessiestatus en acceptatiescenario's aan de UI toe.

## Buiten scope

Gebruik geen droom-, onderzoeks- of andere gespecialiseerde agents uit de uitgebreide implementatie.
Stories, planning, kwaliteitsbewaking en dispatching volgen in latere stappen. Hun publieke
contractpackages in `product-factory-api` zijn al aanwezig, maar hebben in deze release geen actieve
implementatieprovider.

## Specificaties

- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productontwerp MVP](../processen/productontwerp/mvp.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

Een schedule of bevoegde handmatige start met één MVP-agent een geldige epic kan maken en die epic
volledig zichtbaar is. Botsende starts en AI-wachttijd worden correct afgehandeld. De stap draait op
acceptatie en productie zonder uitgebreide Productontwerp-implementatie.
