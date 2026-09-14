# Instructies voor AI-agents

## Productie bekijken als een gebruiker

Als een onderzoek afhangt van wat een specifieke gebruiker of rol in Product Factory ziet, volg dan
het runbook [Agenttoegang en rolweergave](docs/platform/agenttoegang-en-rolweergave.md). Gebruik de
bestaande `PF_DEBUG_TOKEN` via `tools/copy-production-debug-token.sh`; lees, print, log of kopieer de
token nooit naar modelcontext, een opdracht, een URL of een bestand.

Open daarna `https://product-factory.vdzonsoftware.nl/debug-login`, plak de token rechtstreeks in
het gemaskeerde tokenveld en kies een bestaande actieve gebruiker en een rol die werkelijk aan die
gebruiker is toegekend. Verifieer de zichtbare balk met identiteit en rol. Gebruik tijdens onderzoek
bij voorkeur alleen leesacties en kies na afloop **Terug naar factory owner**. Een factory owner kan
dezelfde sessiegebonden weergave starten via **Beheer → Leden → Bekijken als**.

