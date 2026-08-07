# Modulith-architectuur

`productfactory` bevat één `@Modulithic` composition root. Iedere top-level bedrijfsmodule heeft
expliciete `@ApplicationModule`-metadata. `ApplicationModules.verify()` en een aanvullende
conventietest draaien bij iedere Maven-verificatie.

De modules `product`, `story` en `workspace` bevatten de eerste werkende use-cases. De overige
fase-2-modules zijn als gesloten grenzen gereserveerd en krijgen pas gedrag wanneer een volgende
fase dat nodig heeft. Zo ontstaan geen generieke lagen die latere domeinen ongemerkt koppelen.

De contracts-module bevat uitsluitend wire-DTO's voor runtime, dashboard en agentworker. Common
bevat alleen de zelfstandig geïmplementeerde configuratielader. Geen van beide verwijst naar
Software Factory-artifacts.

## Lokale agentruntime

`dashboard-backend` bezit uitsluitend de WebSocket-transportadapter en houdt maximaal één
geauthenticeerde Mac-workerverbinding actief. `agentworker` bezit reconnect, heartbeat,
single-flight taakuitvoering en de Codex-CLI-procesgrens. De gedeelde frames staan in
`productfactory-contracts`; er wordt geen Software Factory-code hergebruikt.

De eerste technische snede houdt taakstatus in het geheugen van de dashboardbackend. Dit is
geschikt om verbinding, auth en Codex-uitvoering veilig te bewijzen. Voordat nachtelijke autonome
runs worden geactiveerd verhuist de duurzame wachtrij naar de `agentruntime`-module en PostgreSQL,
zodat een backendrestart of slapende Mac geen taak kan verliezen.
