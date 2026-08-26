# Product Factory

Product Factory bouwt en bewaakt producten vanuit één globale Stakeholderbediening. De huidige
release bevat de technische fundering, de product- en stakeholderbasis en gecontroleerd
agentgeheugen: productopdrachten, testomgevingen, signalen, agentvragen, overleggen, besluiten,
vier configureerbare schedules, vijf vertrouwde agentrollen, globale AI-modelinstellingen en
duurzame AI-uitvoering via de gedeelde Agent Runtime. Meeting Agent en notulenagent gebruiken
dezelfde outbox-, status-, resultaat-, artifact- en credentialgrantgrens als Productontwerp MVP.
De actieve Productontwerper bevriest product-, bron-, Git- en geheugenversies en publiceert complete,
onveranderlijke epics met een duurzame, hervatbare processessie. De actieve Planner claimt exacte
epicversies, publiceert complete zelfstandige stories en beheert de productbrede backlog,
herplanning, annulering en atomaire dispatchreservering. De actieve Tester controleert de werkelijk
gedeployde revision, publiceert reproduceerbaar bewijs en bugs en bewaart onbeperkte retryhistorie
plus onveranderlijke kwaliteitsbeelden.

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
`agent-runtime-outbox-v1`-variant van `ai-execution-impl` en de actieve
`product-design-impl-mvp`-, `product-planning-impl-mvp`- en `quality-impl-mvp`-providers. De frontend biedt onder **Beheer**
rolgebonden geheugen, peildatumhistorie, budgetten, globale AI-modellen, alleen-naamsgebonden
agenttoegang en veilige AI-taakoperatie via de normale geauthenticeerde sessie en CSRF-beveiliging.
Schedules worden al duurzaam gevalideerd en weergegeven, maar starten tot stap 9 niets automatisch.
Onder **Producten → Ontwerp** kan de Stakeholder dezelfde publieke ontwerpfunctie handmatig starten
of hervatten en epics, versie-inhoud, bronnen, AI-taken, no-ops en blokkades volgen. Onder
**Producten → Planning** staan backlog, stories, dependencies, prioriteitsredenen, workitems en
processessies; daar kan planning worden gestart of gericht worden herpland. Onder **Kwaliteit**
staan het actuele kwaliteitsbeeld, verificaties, bugs, bewijs, retries en Testersessies.

De actuele architectuur en uitvoerplannen staan in [`docs`](docs/overzicht.md). Het operationele
overzicht voor deze capability staat in
[`docs/platform/agentgeheugen-en-ai-instellingen-runbook.md`](docs/platform/agentgeheugen-en-ai-instellingen-runbook.md).
