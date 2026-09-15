# Instructies voor AI-agents

## Productieonderzoek: database en OpenShift eerst

Onderzoek productievragen standaard met gerichte, alleen-lezen databasequeries en OpenShift-logs,
events en deploymentstatus. Controleer vooraf cluster, namespace en database. Gebruik waar mogelijk
een read-only databaseaccount of expliciet read-only transactie; toon geen secrets of onnodige
persoonsgegevens. Gebruik de browser alleen wanneer zichtbare gebruikerservaring, frontendgedrag
of rolweergave daadwerkelijk onderdeel van de vraag is. Een onderzoeksopdracht geeft geen
toestemming om productiedata te wijzigen of testdata aan te maken.

## Productie bekijken als een gebruiker

De onderstaande productie-login is uitsluitend voor door Robbert begeleid onderzoek met Codex of
Claude, na zijn expliciete toestemming voor deze taak. Toestemming om in te loggen autoriseert geen
gegevenswijzigingen. Geef de productie-token nooit aan Agent Runtime-jobs en gebruik database- of
clustertoegang niet als omweg om automatische testers alsnog productietoegang te geven.

Als een onderzoek afhangt van wat een specifieke gebruiker of rol in Product Factory ziet, volg dan
het runbook [Agenttoegang en rolweergave](docs/platform/agenttoegang-en-rolweergave.md). Gebruik de
bestaande `PF_DEBUG_TOKEN` via `tools/copy-production-debug-token.sh`; lees, print, log of kopieer de
token nooit naar modelcontext, een opdracht, een URL of een bestand.

Open daarna `https://product-factory.vdzonsoftware.nl/debug-login`, plak de token rechtstreeks in
het gemaskeerde tokenveld en kies een bestaande actieve gebruiker en een rol die werkelijk aan die
gebruiker is toegekend. Verifieer de zichtbare balk met identiteit en rol. Gebruik tijdens onderzoek
bij voorkeur alleen leesacties en kies na afloop **Terug naar factory owner**. Een factory owner kan
dezelfde sessiegebonden weergave starten via **Beheer → Leden → Bekijken als**.
