# MVP-operatierunbook

Dit runbook beschrijft herstelacties voor de volledige MVP-keten. Gebruik altijd eerst de
productpagina **Operatie** om product-ID, processessie, AI-taak, workitem, deliveryattempt,
externe ID, foutcode en correlatie-ID vast te stellen. Wijzig geen moduletabellen handmatig.

## Geblokkeerde processessie

Controleer de zichtbare blokkadereden en de laatst gebruikte bronversies. Los een ontbrekende
productconfiguratie of terminale externe fout op en start daarna hetzelfde proces met **Nu
starten**. Een `WAITING_FOR_AI`-sessie wordt hervat; een actieve call geeft bewust een overgeslagen
schedulerrun. Maak alleen een nieuwe sessie wanneer de bestaande sessie een terminale eindstatus
heeft.

Productplanning hervat een terminale AI-taak automatisch en probeert een technische taakfout
maximaal drie keer met dezelfde bevroren context. Handmatig hervatten is pas nodig wanneer die
begrensde retryreeks als `BLOCKED` eindigt of wanneer configuratie eerst moest worden hersteld.

## AI-job uitgeschakeld of terminaal mislukt

Controleer in **Instellingen → AI-jobs** of de job actief is en een productiegeschikte provider en
model gebruikt. Activeer of corrigeer de configuratie via de gewone beheeractie. Hervat vervolgens
het eigen proces; de duurzame claim en broncontext blijven leidend. `MOCKED` is uitsluitend op
acceptatie toegestaan. Een ontbrekend mockantwoord is daar een zichtbare fixturefout en mag niet
door een lokale worker worden opgevangen.

## Onbekende of offline environmentkey

Controleer in Agent Runtime of de product- en rolgrant actief is en of een geschikte worker de key
online meldt. Voeg credentials alleen in Runtime toe en nooit in Product Factory, een prompt,
resultaat, artifact of log. Herstart na herstel dezelfde AI-taak volgens het Runtimecontract; maak
geen vervangende domeinclaim.

## Kwaliteitscontrole retrybaar of deployment achter

Bij een tijdelijke testblokkade gebruikt **Retry now** hetzelfde workitem en bewaart het alle
pogingen. Bij `DEPLOYMENT_PENDING` vergelijk je de volledige `deliveredCommitSha` met het veld
`commit` van het geconfigureerde revisionendpoint. Wacht op de echte rollout en probeer hetzelfde
workitem opnieuw. Markeer de story of epic niet handmatig als getest.

## Dispatchcontractfout

Open de deliveryattempt en controleer foutcode, storyKey, packagehash en externe ID. Een
contractfout blijft geblokkeerd totdat Product Factory en Software Factory hetzelfde v2-contract
spreken. Na correctie hervat je de dispatcher; dezelfde idempotentiesleutel en packagehash moeten
worden hergebruikt. Maak niet handmatig een tweede externe story.

## Software Factory tijdelijk onbereikbaar

Een netwerk- of 5xx-fout maakt de applicatie niet onready. De attempt bewaart de begrensde volgende
retry. Na herstel zoekt de dispatcher eerst op storyKey voordat hij opnieuw creëert. Bij een
verloren create-response moet daardoor dezelfde externe story worden gevonden. `DONE` en
`CANCELLED` worden feitelijk verwerkt; een annuleringsmarker gaat altijd vóór nieuwe reservering.

## Scheduler en gemiste starts

Productie pollt alleen schedules die de Stakeholder per product heeft geactiveerd. Na downtime
claimt iedere schedule maximaal één gemist tijdstip en berekent direct het eerste toekomstige
tijdstip. Controleer recente automatische starts op status `SUCCEEDED`, `SKIPPED` of `FAILED`.
Een gewijzigde regel geldt pas voor toekomstige starts. Een uitgeschakelde schedule verhindert
geen handmatige **Nu starten**-actie.

## Applicatieherstart en correlatie

Processessies, AI-outbox, planningeffecten, kwaliteitsworkitems, dispatchattempts en schedulerruns
zijn duurzaam. Laat na een herstart eerst de normale reconcilers en schedules lopen. Zoek een fout
met de veilige correlatie-ID en de operationele IDs; log nooit tokens, environmentkeywaarden of
volledige prompts/resultaten. Escaleer pas na controle dat dezelfde duurzame rij niet meer via de
publieke hervatfunctie vooruit kan.

## Omgevingsgrenzen

Acceptatie gebruikt server-side Runtimefixtures, de stateful MockSoftwareFactory, uitgeschakelde
automatische schedules en Test Control. Productie gebruikt de echte Runtime en Software Factory,
weigert Test Control en `MOCKED`, vereist Google-authenticatie en voert uitsluitend bewust per
product geactiveerde schedules uit.
