# Agenttoegang en rolweergave

Dit runbook is bedoeld voor AI-agents die in productie precies moeten vaststellen wat een bepaalde
Product Factory-gebruiker met een bepaalde rol ziet. De weergave is sessiegebonden: gebruikers,
roltoekenningen en productlidmaatschappen worden er niet door gewijzigd.

## Beveiligingscontract

- `PF_DEBUG_TOKEN` blijft in het OpenShift Secret en de backendpod.
- De token mag nooit in terminaluitvoer, modelcontext, broncode, documentatie, een browser-URL,
  requestbody, screenshot of log verschijnen.
- De browser verstuurt de token uitsluitend als `X-PF-Debug-Token` naar
  `POST /api/auth/debug-session` vanaf de exacte productie-Origin.
- Zonder gekozen gebruiker opent een verborgen, beschermd technisch factory-owneraccount.
- Met een gebruiker en rol accepteert de backend alleen een bestaand actief account en een rol die
  werkelijk aan dat account is toegekend.
- De zichtbare balk **Je werkt nu als ...** is het bewijs van de effectieve identiteit en rol.

## Veilige browserprocedure voor een AI-agent

1. Controleer dat de `oc`-context met het productiecluster is verbonden.
2. Kopieer de token rechtstreeks van de backendpod naar het macOS-klembord:

   ```bash
   tools/copy-production-debug-token.sh
   ```

   Het script toont alleen een succesmelding. Gebruik nooit `printenv PF_DEBUG_TOKEN`, `echo` of
   een andere opdracht die de waarde in tooluitvoer kan laten verschijnen.

3. Open `https://product-factory.vdzonsoftware.nl/debug-login`. Deze route blijft bruikbaar als de
   browser al een Product Factory-sessie heeft.
4. Focus **Debug-token** en plak met de normale plaktoets. Laat browserautomatisering de
   klembordwaarde niet lezen.
5. Laat **Gebruiker** leeg voor technische factory-ownertoegang, of vul het e-mailadres van de
   gebruiker in die onderzocht moet worden.
6. Kies bij een specifieke gebruiker de gewenste toegewezen rol: **Product owner**,
   **Architect** of **Factory owner**.
7. Kies **Sessie openen** en verifieer de zichtbare identiteit, rol, productkeuze en navigatie.
8. Onderzoek het probleem met leesacties. Als een wijziging nodig is, behandel die als een gewone
   productiehandeling met de autorisatie die voor de taak geldt.
9. Kies na een rolweergave **Terug naar factory owner**. Laat geen bekeken gebruikerssessie achter
   in een gedeelde browsertab.

Een reeds ingelogde factory owner kan stap 2 tot en met 6 overslaan en via
**Beheer → Leden → Bekijken als** een gebruiker en diens toegewezen rol kiezen.

## Technische controle zonder browser

Een agent kan controleren of de tokeningang werkt zonder de token of sessieresponse uit te voeren.
Voer de aanvraag in de backendpod uit, schrijf de response tijdelijk in die pod en rapporteer alleen
of de verwachte technische identiteit aanwezig is. Het productiebeeld gebruikt `wget`; de
tokenwaarde wordt uitsluitend door de shell in de pod geïnterpoleerd.

Gebruik deze technische controle niet als vervanging voor de browsercontrole wanneer de vraag gaat
over zichtbaarheid, navigatie, rechten of rolgebonden UX.

## Problemen herkennen

- `403 De request-origin is niet toegestaan`: gebruik exact
  `https://product-factory.vdzonsoftware.nl` als Origin en open de productie-frontend.
- `401 Account is niet actief`: de gekozen gebruiker bestaat maar is uitgeschakeld.
- `401 Deze rol is niet ... toegekend`: kies een rol uit de werkelijke toekenningen van de gebruiker.
- Geen rolbalk na aanmelden: de sessie is niet als een andere identiteit geopend; controleer het
  gekozen e-mailadres en de rol.
- Geen toegang tot het verwachte product: controleer het actieve productlidmaatschap van de gekozen
  gebruiker; voeg voor observatie geen tijdelijke rechten toe.

