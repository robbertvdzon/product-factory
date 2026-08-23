# Stap 9 — Volledige MVP-productflow

## Doel

Rond de losse capabilities af als één begrijpelijke en beheerbare productcyclus: van richting door
de Stakeholder tot gebouwde, gecontroleerde en zo nodig herstelde gebruikersverbetering.

## Globale scope

- Activeer en stem de schedules van Productontwerp, Productplanning, Kwaliteitsbewaking en dispatcher
  op elkaar af zonder directe proces-naar-processtarts.
- Controleer alle queues, commands, statussen, idempotentie en eigenaarsgrenzen in de complete route.
- Maak het productoverzicht en de operationele weergave compleet voor de hele MVP.
- Voeg vaste end-to-endacceptatiescenario's toe voor de normale route, bugs, ontbrekende epicdekking,
  signalen, handmatige prioriteit, workeruitval en externe leveringsfouten.
- Verifieer dat productie uitsluitend de drie MVP-procesimplementaties selecteert.
- Werk runbooks, rooktests en documentatie bij op basis van de werkelijk gebouwde route.

## Buiten scope

Geen van de implementaties uit `uitgebreid.md` wordt gebouwd, geselecteerd of gedeeltelijk
voorbereid. Optimalisaties op basis van productie-ervaring komen pas na deze complete MVP.

## Specificaties

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Deployment en operatie](../platform/deployment-en-operatie.md)
- [Frontend](../stakeholder/frontend.md)
- De API- en MVP-documenten waarnaar stappen 5 tot en met 8 verwijzen

## Klaar wanneer

Een vast acceptatiescenario de volledige cyclus kan doorlopen en iedere overgang in de normale en
operationele UI verklaarbaar is. Na de acceptatiecontrole worden exact dezelfde artifacts naar
productie gepromoveerd en draait daar de complete MVP zonder uitgebreide procesimplementaties.
