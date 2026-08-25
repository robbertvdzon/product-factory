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
| `PF_SESSION_SIGNING_SECRET` | ja | ja | nieuwe sleutel voor Product Factory-sessies |
| `PF_SOFTWARE_FACTORY_TOKEN` | ja | nee | gereserveerd; pas actief bij de dispatcher |
| `PF_AGENT_RUNTIME_URL` | nee | ja vanaf stap 4 | HTTPS-basis-URL van de Agent Runtime voor deze omgeving |
| `PF_AGENT_RUNTIME_TOKEN` | ja | ja vanaf stap 4 | gescopete Product Factory-consumentcredential; nooit een worker- of admincredential |
| `PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN` | ja | nee | alleen integratie/acceptatie voor gescopete Runtime-mockfixtures; nooit in productie |

Acceptatie krijgt geen productiesecrets. Productie weigert op te starten bij ontbrekende verplichte
waarden, uitgeschakelde authenticatie, een te korte sessiesleutel of niet-HTTPS publieke URLs.
Vanaf stap 4 controleert productie ook een HTTPS Runtime-URL en niet-lege consumentcredential.
Acceptatie mag uitsluitend de Agent Runtime-acceptatieomgeving met provider `MOCKED` aanspreken.

Projectcredentials die een AI-agent eventueel mag ontvangen staan niet in Product Factory-
`secrets.env`, database of OpenShift Secret. Zij bestaan uitsluitend als `project-credentials.env`
bij lokale Agent Runtime-workers. Product Factory leest alleen de door Runtime ontdekte namen en
bewaart per product en agentrol welke namen mogen worden aangevraagd.

## Sealed Secrets

`deploy/seal-secrets.sh` gebruikt standaard het rootbestand `secrets.env`, een gesloten lijst met
verplichte sleutels, tijdelijke bestanden via `mktemp` met rechten `0600` en cleanup via een trap.
Het script schrijft uitsluitend het SealedSecret. Een afwijkende bron, certificaat, namespace of
output wordt alleen via de expliciete `PF_SEAL_*`-variabelen gekozen.

Het plaintext bestand blijft altijd in de repositoryroot, gitignored en met rechten `0600`.
