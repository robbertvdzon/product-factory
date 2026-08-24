# Stap 9 — Volledige MVP-productflow

## Doel

Rond de losse capabilities af als één begrijpelijke en beheerbare productcyclus: van richting door
de Stakeholder tot gebouwde, gecontroleerde en zo nodig herstelde gebruikersverbetering.

## Globale scope

- Activeer de productgebonden `ProcessScheduleConfiguration`s van Productontwerp,
  Productplanning, Kwaliteitsbewaking en dispatcher zonder directe proces-naar-processtarts. Poll en
  claim ieder vervallen tijdstip idempotent, haal na downtime maximaal één run in en bereken daarna
  het eerstvolgende toekomstige tijdstip in de ingestelde IANA-tijdzone.
- Controleer alle queues, commands, statussen, idempotentie en eigenaarsgrenzen in de complete route.
- Maak het productoverzicht en de operationele weergave compleet voor de hele MVP.
- Voeg vaste end-to-endacceptatiescenario's toe voor de normale route, bugs, ontbrekende epicdekking,
  signalen, handmatige prioriteit, AI-taakfouten en externe leveringsfouten.
- Bewijs dat de normale mockscenario's volledig server-side draaien zonder laptopworker en dat een
  ontbrekend voorbereid mockantwoord zichtbaar en voorspelbaar faalt.
- Test de echte workergrens apart: na een workerherstart wordt een bestaande taakcontainer hervat,
  een al afgerond resultaat alsnog ingeleverd of een verdwenen poging veilig opnieuw beschikbaar
  gemaakt; een oude poging kan daarna niet meer geldig afronden.
- Bewijs dat een epic in de normale route pas naar `VERIFYING` gaat nadat iedere actuele story- en
  bugfixcontrole is geslaagd, bij `NEEDS_WORK` naar `ACTIVE` terugkeert en bij `BLOCKED` retrybaar
  op `VERIFYING` blijft.
- Bewijs dat kwaliteitsretries de vaste begrensde back-off volgen, zonder maximumpogingen zichtbaar
  blijven en via **Retry now** direct klaarstaan zonder een tweede gelijktijdige kwaliteitsrun.
- Bewijs dat een opgeleverde maar afgekeurde bugfixstory `DONE` blijft, dezelfde bug `OPEN` blijft
  en een volgende gewone bugfixstory kan ontstaan.
- Bewijs dat een door Software Factory geannuleerde story `CANCELLED` wordt en na afronding van het
  overige werk tot een complete feitelijke epicbeoordeling leidt.
- Bewijs dat annuleringsmarker en dispatchreservering gelijktijdige planning, annulering en levering
  eenduidig ordenen, ook wanneer een langdurige storing pas na epicannulering herstelt.
- Bewijs dat een terminale planningstaak dezelfde `IN_PLANNING` epic later hervat en geen
  verweesde claim maakt.
- Bewijs dat een achterlopende acceptatiedeployment `DEPLOYMENT_PENDING` blijft totdat de
  `deliveredCommitSha` werkelijk draait.
- Bewijs dat iedere procesmodule maximaal één sessie per product heeft en twee verschillende
  producten wel gelijktijdig kan verwerken.
- Bewijs dat alle geplande functies via zowel schedule als bevoegde UI/REST-start werken.
- Bewijs dat aan/uit, meerdere tijden per dag, meerdere regels met verschillende dagen en tijden,
  een interval, zomer-/wintertijd, wijziging tijdens een lopende sessie en maximaal één inhaalrun
  na downtime correct werken.
- Verifieer dat productie uitsluitend de drie MVP-procesimplementaties selecteert.
- Werk runbooks, rooktests en documentatie bij op basis van de werkelijk gebouwde route.

## Buiten scope

Geen van de implementaties uit `uitgebreid.md` wordt gebouwd, geselecteerd of gedeeltelijk
voorbereid. Optimalisaties op basis van productie-ervaring komen pas na deze complete MVP.
Externe notificaties via e-mail, Telegram of een andere dienst vallen eveneens buiten deze route;
operationele aandachtspunten zijn in de MVP zichtbaar in de UI.

## Specificaties

- [Overzicht](../overzicht.md)
- [Belangrijkste functionele ketenscenario's](../ketenscenarios.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Deployment en operatie](../platform/deployment-en-operatie.md)
- [Frontend](../stakeholder/frontend.md)
- De API- en MVP-documenten waarnaar stappen 5 tot en met 8 verwijzen

## Klaar wanneer

Een vast acceptatiescenario de volledige cyclus kan doorlopen en iedere overgang in de normale en
operationele UI verklaarbaar is. Na de acceptatiecontrole worden exact dezelfde artifacts naar
productie gepromoveerd en draait daar de complete MVP zonder uitgebreide procesimplementaties.
