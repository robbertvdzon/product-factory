# AI-testomgevingen

Epics krijgen globaal een oplopend nummer en de referentie `epic-N`, over alle producten heen.
Bestaande epics worden chronologisch genummerd. Verwijderde nummers worden niet opnieuw gebruikt.
De API retourneert `epicNumber` en `reference`; `getEpic` accepteert UUID en `epic-N`.

Kwaliteitswerk test uitsluitend de geconfigureerde acceptatieomgeving. `TestEnvironmentConfiguration.login`
bevat een niet-productiecredentialnaam (`PROJECT__ACCEPTANCE_AGENT_TOKEN`), `identity`, relatief
`endpoint`, `tokenHeader` en optioneel `role`. Credentials worden apart aan `TESTER_MVP` toegekend.
Ontbrekende actieve/zichtbare toekenning blokkeert de test. Productie-loginconfiguratie wordt geweigerd.
De controle op de draaiende revision vindt voor én na de test plaats.

Acceptatie gebruikt nu echte applicatieauthenticatie met een eigen synthetisch testaccount. Open
`/api/auth/agent-login` of POST naar `/api/auth/agent-session` met `X-AI-Access-Token` en `{"email":"acceptance-tester@product-factory.invalid"}`.
De sessie gebruikt dezelfde cookies, CSRF-controle en rollen als normaal. De token staat alleen in
de acceptatie-SealedSecret en als `PF__ACCEPTANCE_AGENT_TOKEN` bij de worker.
Productie behoudt de bestaande debug-login, uitsluitend na toestemming voor de huidige begeleide taak.

## PR op acceptatie

De bestaande releaseworkflow kan handmatig een succesvolle `Repository verification`-run plus de
exacte volledige Git-revision ontvangen. `acceptance_only` staat standaard aan; `hold_minutes`
reserveert acceptatie maximaal 60 minuten. Gedurende die run delen deployments dezelfde wachtrij,
zonder een actieve test te annuleren. De workflow controleert voor en na de reservering de versie
van frontend en backend en promoveert deze handmatige testdeployment nooit naar productie.
Gebruik de control plane/een begeleide taak voor dispatch; geef geen GitHub- of clustertoken aan
een testerjob. Een nieuwere deployment buiten deze workflow maakt het testbewijs ongeldig.
