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

## Automatische verwerking

Productie controleert ieder actief product iedere tien seconden. In Productinstellingen kan de
factory owner het hele product pauzeren; er zijn geen schema's of afzonderlijke dispatcherschakelaars.
Lopende AI-taken en extern werk mogen afronden. Na hervatten wordt aanwezig werk opnieuw gecontroleerd.
Lege controles schrijven geen sessiehistorie en starten geen AI. Het laatste controlemoment staat
in `pf_automation_state`; `pf_automation_process` bewaart maximaal vier actuele processtatussen
per product. Technische fouten gebruiken oplopende wachttijden tot tien minuten en worden zichtbaar
in de instellingen. Een inhoudelijke blokkade wacht op gewijzigde input.

De databaselease voorkomt dubbele automatische controles en verloopt na vijf minuten bij een
uitgevallen worker. Goedkeuringen, afhankelijkheden en bestaande dispatch-idempotentie blijven
verplicht. Migratie V37 activeert de nieuwe verwerking voor bestaande producten; inactieve producten
worden overgeslagen. De oude schedules blijven alleen voor historische compatibiliteit bewaard.

## Applicatieherstart en correlatie

Processessies, AI-outbox, planningeffecten, kwaliteitsworkitems, dispatchattempts en schedulerruns
zijn duurzaam. Laat na een herstart eerst de normale reconcilers en automatische controles lopen. Zoek een fout
met de veilige correlatie-ID en de operationele IDs; log nooit tokens, environmentkeywaarden of
volledige prompts/resultaten. Escaleer pas na controle dat dezelfde duurzame rij niet meer via de
publieke hervatfunctie vooruit kan.

## Omgevingsgrenzen

Acceptatie gebruikt server-side Runtimefixtures, de stateful MockSoftwareFactory, uitgeschakelde
automatische verwerking en Test Control. Productie gebruikt de echte Runtime en Software Factory,
weigert Test Control en `MOCKED`, vereist Google-authenticatie en controleert alle actieve, niet-gepauzeerde producten iedere tien seconden.

## Productiebewijs 14 september 2026

Release `85362de024c71132aff739511ecd1494500760b2` is via de geslaagde
[repositorycontrole](https://github.com/robbertvdzon/product-factory/actions/runs/34856564716) en
[releasepipeline](https://github.com/robbertvdzon/product-factory/actions/runs/34856893377) op
acceptatie en productie gezet. Lokaal slaagden 196 backendtests, 55 Fluttertests, analyse,
frontendbuild en cachetest. Beide omgevingen slaagden voor de rooktest met deze exacte revision.

In productie is als factory owner gecontroleerd dat de schema's en aparte dispatcherschakelaar
vervangen zijn door één projectpauze. Pauzeren en hervatten van HKH werkten via de UI; na afloop
stonden alle drie actieve producten weer aan. De controlerij van HKH toonde onder andere
`14:46:10.481738Z` met volgende controle `14:46:20Z`.

Tussen de uitrol om 14:43 UTC en de controle om 14:46 UTC ontstond precies één nieuwe
dispatchersessie: de eerste HKH-story werd automatisch `IN_PROGRESS` met externe ID `hkh-221`.
De tweede story bleef `TODO`. Lege controles en ongewijzigd lopend extern werk leverden geen
nieuwe schedulerruns of AI-taken op. Bestaande historie is behouden.

Bij HKH Autopilot ontstond één ontwerpblokkade `DESIGN_INPUT_INVALID` met melding
“De complete AI-prompt is leeg of te groot.” Deze inhoudelijke fout bleef zichtbaar en werd
niet steeds opnieuw gestart. Het verkleinen van die ontwerpcontext is afzonderlijk vervolgwerk;
de dispatcher en de controles van de andere producten bleven werken.
