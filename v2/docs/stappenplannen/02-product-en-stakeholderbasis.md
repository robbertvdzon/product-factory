# Stap 2 — Product- en stakeholderbasis

## Doel

Maak de Product Factory bruikbaar voor de Stakeholder, nog zonder intelligente productprocessen.
Deze stap levert de publieke productbasis waarop alle latere modules voortbouwen.

## Globale scope

- Implementeer `Product`, `ProductAssignment` en `UserSignal` met hun publieke commands en queries.
- Implementeer het Besluitenregister, inclusief actuele besluiten, historie, intrekken en vervangen.
- Implementeer overleggen, notulen en de gecontroleerde verwerking van stakeholderuitkomsten.
- Voeg de bijbehorende product-, inbox-, besluiten- en overlegschermen aan de frontend toe.
- Toon herkomst, status en historie via de publieke module-API's; schrijf nooit vanuit de UI in
  moduletabellen.
- Voeg vaste acceptatiescenario's toe en deploy de afgeronde stap naar acceptatie en productie.

## Buiten scope

Agentgeheugen, AI-uitvoering, epics, stories, kwaliteitswerk en Software Factory-dispatching worden
nog niet geïmplementeerd. Een eventuele notulenagent wacht op stap 4; tot die tijd kan de
overleguitkomst zonder AI via dezelfde publieke commands worden geregistreerd.

## Specificaties

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Besluitenregister](../gedeelde-modules/besluitenregister.md)
- [Overleggen met de Stakeholder](../stakeholder/overleggen.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

De Stakeholder kan in de UI het product beheren, signalen indienen en volgen, overleggen vastleggen
en geldige of historische besluiten bekijken en wijzigen. Dit werkt met synthetische data op
acceptatie en met duurzame data en authenticatie op productie.
