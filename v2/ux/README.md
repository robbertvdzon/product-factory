# Product Factory — UX-concept

Dit is een klikbaar UX-concept voor de Product Factory. Het laat zien hoe de publieke productdata
uit de v2-documentatie als één rustige, begrijpelijke applicatie kan worden gepresenteerd. Het is
geen frontendimplementatie en introduceert geen nieuwe domeinentiteiten.

## Bekijken

Open `index.html` rechtstreeks in een browser, of start vanuit deze map een eenvoudige statische
webserver:

```bash
python3 -m http.server 8080
```

Open daarna `http://localhost:8080`.

## Ontwerpkeuzes

- De Stakeholder ziet eerst productvoortgang en gebruikerswaarde; technische queues staan apart.
- Epic, backlog, kwaliteit en signalen zijn vier verschillende perspectieven op dezelfde publieke
  productwaarheid.
- De backlog blijft een geordende lijst van open stories en wordt nergens als tweede object
  voorgesteld.
- Handmatige acties gebruiken precies de publieke commands uit de specificatie, maar tonen in de
  UI gewone mensentaal.
- Een dispatchreservering verschijnt tijdelijk als **Wordt verstuurd**, zonder extra storystatus.
- Kwaliteitsretries staan met de meeste pogingen bovenaan en hebben een duidelijke **Retry now**-
  actie.
- Procesruns en AI-taken staan in **Operatie**, zodat de gewone productschermen rustig blijven.
- Het ontwerp schaalt van een brede desktopweergave naar 320 CSS-pixels en blijft bruikbaar bij
  tekstvergroting.

## Schermen in het prototype

- **Overzicht** — productdoel, actuele epic, levering, kwaliteit en recente signalen.
- **Planning** — epics, de berekende backlog en storydetails inclusief UX-overdracht.
- **Kwaliteit** — kwaliteitshistorie, retrybaar testwerk, bugs en verificaties.
- **Inbox** — onveranderlijke gebruikerssignalen met status en doorwerking.
- **Besluiten** — actuele grote besluiten en toegang tot de historie.
- **Overleggen** — gesprekken, notulen en expliciete acties.
- **Operatie** — processessies, AI-taken, workerstatus en dispatcher.

De getoonde inhoud is synthetische HKH-voorbeelddata en dient alleen om de informatiehiërarchie en
interacties te beoordelen.
