# Configuratie en secrets

Product Factory voegt lokale configuratie in vaste volgorde samen. Iedere volgende bron overschrijft
de vorige:

1. `properties.default.env` — commitbare, niet-geheime defaults;
2. `properties.env` — gitignored lokale overrides;
3. `secrets.env` — gitignored lokale secrets;
4. proces-environmentvariabelen — CI, containers en OpenShift.

Alle applicatiesleutels beginnen met `PF_`. Ongeldige sleutelnamen en ongeldige omgevingswaarden
stoppen startup zonder waarden te loggen.

## Actueel contract

| Sleutel | Geheim | Verplicht in productie | Doel |
|---|---:|---:|---|
| `PF_ENVIRONMENT` | nee | ja | `local`, `acceptance` of `production` |
| `PF_BACKEND_PORT` | nee | nee | lokale HTTP-poort |
| `PF_PUBLIC_FRONTEND_URL` | nee | ja | publieke URL van de frontend |
| `PF_PUBLIC_BACKEND_URL` | nee | ja | publieke URL van de API |
| `PF_AUTH_REQUIRED` | nee | ja | moet in productie `true` zijn en in acceptatie `false` |
| `PF_DB_URL` | ja | ja | JDBC-URL van de omgevingsdatabase |
| `PF_DB_USERNAME` | ja | ja | afzonderlijke databasegebruiker per omgeving |
| `PF_DB_PASSWORD` | ja | ja | afzonderlijk databasewachtwoord per omgeving |
| `PF_GOOGLE_CLIENT_ID` | ja | ja | audience voor Google-login |
| `PF_STAKEHOLDER_EMAILS` | ja | ja | gesloten allowlist van Stakeholder-e-mailadressen |
| `PF_FACTORY_OWNER_EMAILS` | ja | ja | subset met globale `FACTORY_OWNER`-rechten; overige toegelaten gebruikers krijgen alleen expliciete productlidmaatschappen |
| `PF_SESSION_SIGNING_SECRET` | ja | ja | nieuwe sleutel voor Product Factory-sessies |
| `PF_SOFTWARE_FACTORY_MODE` | nee | ja | `DISABLED` vóór stap 8, `MOCKED` in acceptatie en `REAL` voor de echte productieadapter |
| `PF_SOFTWARE_FACTORY_URL` | nee | ja vanaf stap 8 | HTTPS-basis-URL van het Software Factory v2-contract; productie gebruikt `https://dashboard.vdzonsoftware.nl/api/integrations/v2` |
| `PF_SOFTWARE_FACTORY_TOKEN` | ja | ja vanaf stap 8 | Bearer-token voor de echte adapter; dezelfde waarde heet aan Software Factory-zijde `SF_PRODUCT_FACTORY_TOKEN` |
| `PF_SOFTWARE_FACTORY_DASHBOARD_URL` | nee | ja voor hotfixstatus | interne basis-URL van de bestaande dashboard-story-API |
| `PF_SOFTWARE_FACTORY_DASHBOARD_TOKEN` | ja | ja voor hotfixstatus | kortlevend, doelgebonden dashboardtoken; nooit aan Agent Runtime of Product Advisor doorgeven |
| `PF_SOFTWARE_FACTORY_HOTFIX_ENABLED` | nee | ja | featureguard; productie gebruikt `true` met maximaal twee verzendpogingen per exacte requestversie |
| `PF_AGENT_RUNTIME_URL` | nee | ja vanaf stap 4 | HTTPS-basis-URL van de Agent Runtime voor deze omgeving |
| `PF_AGENT_RUNTIME_TOKEN` | ja | ja vanaf stap 4 | gescopete Product Factory-consumentcredential; nooit een worker- of admincredential |
| `PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN` | ja | nee | alleen integratie/acceptatie voor gescopete Runtime-mockfixtures; nooit in productie |
| `PF_AGENT_RUNTIME_API_VERSION` | nee | tijdelijk ja | gecontroleerde `v1|v2`-omschakeling; acceptatie en na promotie productie gebruiken `v2` |
| `PF_AI_VENDOR_ID` | nee | ja | expliciete standaardvendor; productie `openai`, acceptatie `mock` |
| `PF_AI_MODEL` | nee | ja | expliciet model; productie `gpt-5.6-sol`, acceptatie `mock` |
| `PF_AI_EXECUTION_MODE` | nee | ja | `SUBSCRIPTION`, `API` of `MOCK`; productie weigert `MOCK` |
| `PF_AI_ARTIFACT_STORAGE_PATH` | nee | ja | mountpad van de afzonderlijke Product Factory-artifact-PVC |
| `PF_TESTBED_RUNTIME_FIXTURE_RESET_ENABLED` | nee | nee | wist in acceptatie Runtime-fixtures tijdens Testbedreset; standaard `true` |

Acceptatie krijgt geen productiesecrets. Productie weigert op te starten bij ontbrekende verplichte
waarden, uitgeschakelde authenticatie, een te korte sessiesleutel of niet-HTTPS publieke URLs.
Vanaf stap 4 controleert productie ook een HTTPS of expliciet toegestane interne Runtime-URL,
niet-lege consumentcredential en exacte non-MOCK-uitvoering. Acceptatie vereist de vaste Agent
Runtime-acceptatieomgeving, `v2`, `mock/mock/MOCK` en een afzonderlijk test-control-token.

Vanaf stap 8 controleert productie bovendien dat `PF_SOFTWARE_FACTORY_MODE=REAL`, de Software
Factory-URL exact HTTPS gebruikt en `PF_SOFTWARE_FACTORY_TOKEN` niet leeg is. Acceptatie vereist
`PF_SOFTWARE_FACTORY_MODE=MOCKED`, gebruikt uitsluitend `MockSoftwareFactory` en bevat geen echte
Software Factory-URL of -token. Bij `DISABLED` worden geen dispatcher-endpoints, schedules of
externe calls geactiveerd. De concrete routes en transportmapping staan in
[Software Factory-dispatcher](../processen/software-factory-dispatcher.md#extern-http-contract).

Projectcredentials die een AI-agent eventueel mag ontvangen staan niet in Product Factory-
`secrets.env`, database of OpenShift Secret. Zij bestaan uitsluitend als `project-credentials.env`
bij lokale Agent Runtime-workers. Product Factory leest alleen de door Runtime ontdekte namen en
bewaart per product en agentrol welke namen mogen worden aangevraagd.

Product Factory bevat evenmin een OpenAI- of Anthropic-API-key. Subscription- en API-credentials
blijven uitsluitend bij Agent Runtime/haar workers. Het artifactvolume is Product Factory-
domeinopslag en is nadrukkelijk niet de interne Runtime-objectstore.

## Sealed Secrets

`deploy/seal-secrets.sh` gebruikt standaard het rootbestand `secrets.env`, een gesloten lijst met
verplichte sleutels, tijdelijke bestanden via `mktemp` met rechten `0600` en cleanup via een trap.
Het script schrijft uitsluitend het SealedSecret. Een afwijkende bron, certificaat, namespace of
output wordt alleen via de expliciete `PF_SEAL_*`-variabelen gekozen.

Het plaintext bestand blijft altijd in de repositoryroot, gitignored en met rechten `0600`.
Vanaf stap 8 bevat de gesloten sleutellijst van hetzelfde script ook
`PF_SOFTWARE_FACTORY_TOKEN`, `PF_FACTORY_OWNER_EMAILS` en
`PF_SOFTWARE_FACTORY_DASHBOARD_TOKEN`. Er komt geen tweede seal-script of alternatieve locatie voor
`secrets.env`. Het aparte rotatiehulpmiddel maakt alleen het dashboardtoken opnieuw aan en gebruikt
dezelfde productie-SealedSecretlocatie; zie het [Product Advisor-runbook](product-advisor-runbook.md).
