# Stap 4 — AI-uitvoering

Implementatiestatus: uitgevoerd en later gemigreerd naar Agent Runtime v2.

Deze stap introduceerde de lokale `AiTask`, versieerbare jobconfiguratie, transactionele outbox,
statusreconciliatie, annulering, agentgeheugenaudit en operationele UI. De actuele normatieve
werking staat in [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md) en
[Agent Runtime-worker](../gedeelde-modules/ai-worker.md).

De oorspronkelijke v1-transportkeuzes uit stap 4 zijn door
[Stap 10 — Product Factory naar Agent Runtime v2](10-agent-runtime-v2-migratie.md) vervangen:

- iedere job heeft expliciet `vendorId`, `model` en `mode`;
- alle huidige jobs zijn `APPLICATION_WORK`/`STRUCTURED_GENERATION` met verplicht JSON-schema;
- prompt en attachments gaan als hervatbare objectuploads, niet inline of Base64;
- resultaat, artifactmanifest, events, attempts, usage en kosten komen uit `/v2`;
- gepubliceerde artifacts worden door Product Factory duurzaam en streamend overgenomen;
- acceptatie gebruikt uitsluitend `mock/mock/MOCK` via een afzonderlijk test-control-token;
- productie gebruikt standaard `openai/gpt-5.6-sol/SUBSCRIPTION` en bevat geen providercredential.

Tijdens de gecontroleerde omschakeling blijft een v1-adapter achter
`PF_AGENT_RUNTIME_API_VERSION=v1|v2` beschikbaar als rollback. Deze compatibiliteit is geen actueel
ontwerpcontract en wordt pas na bewezen productiestabiliteit in een afzonderlijke cleanuprelease
verwijderd.

## Blijvende acceptatiecriteria

- taak, lokale inputs en outbox ontstaan atomair;
- herhaling of een verloren response maakt geen dubbele Runtime-job;
- procesrestart hervat uploads, dispatch, events en resultaatverwerking;
- iedere taak bewaart de exacte execution-, configuratie-, prompt- en contextsnapshot;
- alleen veilige voortgang en reasoning-samenvattingen zijn zichtbaar;
- onbekende usage wordt niet als nul weergegeven en Product Factory berekent geen providerprijs;
- productie weigert MOCK en acceptatie weigert productie-, admin- en workercredentials;
- de bestaande domeinprocessen wachten en hervatten zonder database-lock of synchrone AI-call.

Verificatie loopt via de volledige Mavenreactor, PostgreSQL-upgrades, Fluttertests,
deploymentoverlays, Runtime-contracttests en de acceptatie-/productiesmokes uit stap 10.
