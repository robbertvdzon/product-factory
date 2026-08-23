# Stap 6 — Productplanning MVP

## Doel

Laat één Planner-agent beschikbare epics en gericht herstelwerk omzetten in zelfstandige stories en
één geordende backlog.

## Globale scope

- Implementeer de publieke Productplanning-API en uitsluitend de MVP-implementatiemodule.
- Implementeer de `PlanningWorkItem`-queue en geplande of handmatige `runProcessSession()`.
- Laat de planner een exacte epicversie claimen en opdelen in complete product- of bugfixstories.
- Bewaar storystatus en `sequenceNumber`; bereken de backlog als alle `TODO`- en `IN_PROGRESS`-stories.
- Ondersteun de publieke, snelle commands voor levering, afronding, herstelwerk en handmatige
  prioriteitswijziging.
- Voeg backlog-, story-, planningwerk- en sessieweergaven plus acceptatiescenario's toe.

## Buiten scope

De uitgebreide plannerrollen worden niet gebouwd. Deze stap verstuurt nog geen story naar Software
Factory en voert nog geen intelligente kwaliteitscontrole uit.

## Specificaties

- [Productplanning-API](../processen/productplanning/api.md)
- [Productplanning MVP](../processen/productplanning/mvp.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

Een beschikbare epic via één MVP-agent een volledige, geordende storyset oplevert, gericht
planningwerk idempotent wordt verwerkt en de Stakeholder de backlog veilig kan herprioriteren. De
stap is op acceptatie en productie gedeployed.
