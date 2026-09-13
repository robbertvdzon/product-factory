# Product Factory

Product Factory bouwt en bewaakt producten vanuit één globale Stakeholderbediening. De huidige
release bevat de technische fundering, de product- en stakeholderbasis en gecontroleerd
agentgeheugen: productopdrachten, testomgevingen, signalen, agentvragen, overleggen, besluiten,
vier configureerbare schedules, zes vertrouwde agentrollen, globale AI-modelinstellingen en
duurzame AI-uitvoering via de gedeelde Agent Runtime. Meeting Agent en notulenagent gebruiken
dezelfde outbox-, status-, resultaat-, artifact- en credentialgrantgrens als Productontwerp MVP.
De actieve Productontwerper bevriest product-, bron-, Git- en geheugenversies, onderzoekt concrete
externe bronnen, bewaart UX-screenshots en houdt onrijpe epics op `NEEDS_RESEARCH` tijdens een
begrensde, automatisch hervatte ontwerpcyclus. De actieve Planner claimt uitsluitend exacte
gereedverklaarde epicversies, publiceert complete zelfstandige stories en beheert de productbrede backlog,
herplanning, annulering en atomaire dispatchreservering. De actieve Tester controleert de werkelijk
gedeployde revision, publiceert reproduceerbaar bewijs en bugs en bewaart onbeperkte retryhistorie
plus onveranderlijke kwaliteitsbeelden.
De actieve dispatcher reserveert steeds hoogstens één uitvoerbare story, bevriest het volledige
storypakket en verwerkt Software Factory-v2-statussen idempotent. Een verloren response, retry of
lokale bevestigingsfout kan daardoor geen tweede externe story veroorzaken. Product owners hebben
per toegewezen product duurzame gesprekken met Product Advisor, geversioneerde verzoeken, gerichte
epicuitwerking, persoonlijke acties en versiegebonden approvals. Factory owners beheren gebruikers
en productlidmaatschappen. De directe hotfixadapter is aanwezig maar blijft fail-closed totdat
markerherstel door de bestaande Software Factory-lijstendpoint productief is bewezen.
De productmodule claimt geactiveerde procesritmes atomair, haalt na downtime maximaal één run in
en start uitsluitend de publieke ontwerp-, planning-, kwaliteits- of dispatcherfunctie. De
volledige technische MVP-keten en alle negentien vaste afwijkende scenario's zijn in Testbeddataset
`complete-mvp-v1` aantoonbaar.

## Vereisten

- JDK 21 (geen andere hoofdversie);
- Maven 3.9;
- Flutter 3.44.6 met de bijbehorende Dart-SDK;
- Docker met Compose voor de latere productieachtige lokale omgeving.

Op macOS selecteert de lokale CLI zelf de geïnstalleerde JDK 21. Maven Enforcer laat iedere build
vroeg en duidelijk falen wanneer Maven toch met een andere Java-hoofdversie draait.

```bash
./product-factory verify
./product-factory backend
./product-factory frontend
```

De backendroute `GET /api/foundation` bevestigt de actieve basis. Onder
`GET /api/foundation/implementations` staan ook `agent-memory-impl` en de actieve
`agent-runtime-outbox-v2`-variant van `ai-execution-impl`, `product-advisor-v1` en de actieve
`product-design-impl-mvp`-, `product-planning-impl-mvp`-, `quality-impl-mvp`- en
`software-factory-dispatcher-impl`-providers. De frontend biedt onder **Beheer**
rolgebonden geheugen, peildatumhistorie, budgetten, globale AI-modellen, alleen-naamsgebonden
agenttoegang en veilige AI-taakoperatie via de normale geauthenticeerde sessie en CSRF-beveiliging.
Schedules worden duurzaam gevalideerd, weergegeven en in productie alleen uitgevoerd wanneer de
Stakeholder ze voor het betreffende product bewust heeft geactiveerd. Acceptatie houdt automatische
starts uit en gebruikt een bestuurbare klok en Test Control.
Iedere productpagina begint met wat er nu loopt of vastzit. **Overzicht** toont de lopende epic als
epic-reis (ontwerp → planning → bouw → verificatie → eventuele bugfix en hertest → afgerond), waar
die epic op wacht en sinds wanneer, open aandachtspunten en het automatiseringsritme van de laatste
24 uur. Een epic openen toont de volledige tijdlijn uit stories, leveringen, verificaties, bugs en
mislukte of geblokkeerde processessies (`GET /api/epics/{epicId}/progress`). Onder **Ontwerp**
staan epics per fase met de handmatige ontwerpstart; onder **Planning** wat nu bij Software Factory
ligt, de backlog, per epic ingeklapte opgeleverde en geannuleerde stories en de dispatcher; onder
**Kwaliteit** eerst open bugs, dan werk, verificaties met storytitels en Testersessies. Afgeronde
processessies zijn overal ingeklapt en “succesvolle no-op”-runs worden niet als regel getoond.
**Beheer → Operatie** filtert alle processessies server-side per proces en uitkomst
(`?limit=&before=&excludeNoOps=`); `GET /api/products/{productId}/live` levert lopende sessies,
schema's en uurtellingen. No-op-sessies ouder dan 48 uur en schedule-runs ouder dan 7 dagen worden
periodiek opgeruimd.

De actuele architectuur en uitvoerplannen staan in [`docs`](docs/overzicht.md). Het operationele
overzicht en de bewijsregistratie staan in het
[`MVP-operatierunbook`](docs/platform/mvp-operatie-runbook.md) en het
[`MVP-bewijsrecord`](docs/mvp-bewijsrecord.md).
De Product Advisor-bediening en operationele grens staan in het
[`Product Advisor-runbook`](docs/platform/product-advisor-runbook.md) en het
[`Product Advisor-bewijsrecord`](docs/product-advisor-bewijsrecord.md).
