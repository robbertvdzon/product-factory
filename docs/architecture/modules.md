# Modulith-architectuur

`productfactory` bevat één `@Modulithic` composition root. Iedere top-level bedrijfsmodule heeft
expliciete `@ApplicationModule`-metadata. `ApplicationModules.verify()` en een aanvullende
conventietest draaien bij iedere Maven-verificatie.

De modules `product`, `story` en `workspace` bevatten de productconfiguratie en begrensde
workspacepublicatie. `iteration` bezit de shadow-cyclus en mag uitsluitend de expliciete API's van
`product`, `agentruntime` en `workspace` gebruiken. `agentruntime` registreert de duurzame
runstatus; de WebSocket-transportadapter blijft in de afzonderlijke dashboardbackend. De overige
gereserveerde modules blijven gesloten totdat een volgende fase daar gedrag aan toevoegt.

De contracts-module bevat uitsluitend wire-DTO's voor runtime, dashboard en agentworker. Common
bevat alleen de zelfstandig geïmplementeerde configuratielader. Geen van beide verwijst naar
Software Factory-artifacts.

## Lokale agentruntime

`dashboard-backend` bezit uitsluitend de WebSocket-transportadapter en houdt maximaal één
geauthenticeerde Mac-workerverbinding actief. `agentworker` bezit reconnect, heartbeat,
single-flight taakuitvoering en de Codex-CLI-procesgrens. De gedeelde frames staan in
`productfactory-contracts`; er wordt geen Software Factory-code hergebruikt.

De transportstatus van een actieve agenttaak staat tijdelijk in het geheugen van de
dashboardbackend. Iteratie, stappen, gevalideerde resultaten en eindstatus staan duurzaam in
PostgreSQL. Een verbroken workerverbinding of backendrestart laat de huidige iteratie daarom
fail-closed eindigen; automatische hervatting en een duurzame wachtrij horen bij de begrensde
autonomie van een volgende fase.
