# Stap 6 — Productplanning MVP

## Doel

Laat één Planner-agent beschikbare epics en reeds beschikbaar gericht werk omzetten in zelfstandige
stories en één geordende backlog.

## Globale scope

- Implementeer uitsluitend de MVP-provider achter de in stap 1 vastgelegde Productplanning-API.
- Implementeer de `PlanningWorkItem`-queue en geplande of handmatige
  `runProcessSession(productId)`, met maximaal één sessie per product.
- Laat de planner een exacte epicversie claimen en opdelen in complete product- of bugfixstories.
- Geef iedere story een opgeslagen korte titel en samenvatting van maximaal twee korte zinnen naast
  het volledige Storycontract.
- Bewaar storystatus en `sequenceNumber`; bereken de backlog als alle `TODO`- en `IN_PROGRESS`-stories.
- Ondersteun de publieke, snelle commands voor levering, bugfix- en dekkingswerk en handmatige
  prioriteitswijziging.
- Koppel iedere bugfixstory vóór uitvoerbaarheid met `linkBugfixStory(bugId, storyId)` en gebruik
  uitsluitend de storytypen `PRODUCT_STORY` en `BUGFIX`.
- Bewaar bij annulering ook zonder bestaande stories een marker die latere publicatie en
  dispatchreservering blokkeert.
- Hervat een geclaimde `IN_PLANNING` epic altijd vóór nieuw werk en laat een terminale technische
  AI-fout nooit een verweesde epic achterlaten.
- Definieer dependencies als voldaan bij `DONE`; laat een `CANCELLED` dependency automatisch gericht
  herplanningswerk voor nog open afhankelijke stories maken.
- Voeg de benodigde `findBugs(...)`-query en velden voor `deliveredCommitSha` aan het contract toe.
- Sluit de reeds actieve Productontwerp-implementatie aan. De Kwaliteitsbewaking-API bestaat, maar
  kwaliteitsinputs en -commands worden pas gebruikt nadat haar implementatie in stap 7 actief is.
- Voeg backlog-, story-, planningwerk- en sessieweergaven plus acceptatiescenario's toe.

## Buiten scope

De uitgebreide plannerrollen worden niet gebouwd. Deze stap verstuurt nog geen story naar Software
Factory en voert nog geen intelligente kwaliteitscontrole uit. De Kwaliteitsbewaking-API heeft in
deze release nog geen actieve implementatieprovider.

## Specificaties

- [Productplanning-API](../processen/productplanning/api.md)
- [Productplanning MVP](../processen/productplanning/mvp.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

Een beschikbare epic via één MVP-agent een volledige, geordende storyset oplevert, gericht
planningwerk idempotent wordt verwerkt en de Stakeholder de backlog veilig kan herprioriteren. De
stap is op acceptatie en productie gedeployed.
