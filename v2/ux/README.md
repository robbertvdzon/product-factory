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

## Schermafbeeldingen

De map [screenshots](screenshots/README.md) bevat vaste desktop- en mobiele referentiebeelden die
rechtstreeks uit dit prototype zijn opgenomen. Gebruik ze bij de frontendbouw voor visuele richting,
schermhiërarchie en responsive gedrag. Het klikbare prototype en de functionele documentatie blijven
leidend wanneer een screenshot daarvan afwijkt.

## Ontwerpkeuzes

- Het overzicht toont alleen het productdoel, het actuele werk en maximaal enkele aandachtspunten.
- Productontwerp heeft een eigen scherm voor epics en bijbehorende UX; Planning blijft daardoor een
  rustige, geordende storylijst.
- Epic, backlog, kwaliteitswerk en signalen zijn vier verschillende perspectieven op dezelfde
  publieke productwaarheid.
- De backlog blijft een geordende lijst van open stories en wordt nergens als tweede object
  voorgesteld.
- Handmatige acties gebruiken precies de publieke commands uit de specificatie, maar tonen in de
  UI gewone mensentaal.
- Onder **Instellingen → Automatisering** kan de Stakeholder per product en proces meerdere
  regels met eigen weekdagen en tijden, of één vast interval instellen. Zo passen zowel dagelijks
  07:00 en 20:00 als maandag 09:00 plus vrijdag 21:00. De UI toont geen cronexpressies;
  uitschakelen laat **Nu starten** beschikbaar.
- Een dispatchreservering verschijnt tijdelijk als **Wordt verstuurd**, zonder extra storystatus.
- Kwaliteit groepeert testwerk, bugs, verificaties en historie in rustige deelweergaven. Statistieken
  blijven secundair.
- Kwaliteitsretries staan met de meeste pogingen bovenaan en hebben een duidelijke **Retry now**-actie.
- **Signalen** is geen aparte inboxentiteit: het is een eenvoudige lijst van `UserSignal`s en hun
  zichtbare verwerking.
- Procesruns, queues, AI-taken, dispatcherhistorie en versies staan in **Operatie**, zodat de gewone
  productschermen rustig blijven.
- Minder dagelijkse onderdelen staan onder **Beheer**: Instellingen, besluiten, Agentgeheugen,
  Operatie en de acceptance-only testbediening. Instellingen groepeert de productconfiguratie,
  procesautomatisering en globale AI-modellen op één pagina.
- Het ontwerp schaalt van een brede desktopweergave naar 320 CSS-pixels en blijft bruikbaar bij
  tekstvergroting.

## Schermen in het prototype

- **Overzicht** — productdoel, actuele epic en story, plus concrete aandachtspunten.
- **Ontwerp** — epics per levenscyclusstatus en epicdetails inclusief UX-ontwerp.
- **Planning** — de berekende backlog en storydetails inclusief UX-overdracht.
- **Kwaliteit** — actuele en afgeronde QualityWorkItems, bugs, verificaties en kwaliteitshistorie.
- **Signalen** — onveranderlijke gebruikerssignalen met bron, context, status en doorwerking.
- **Overleggen** — agenda, gesprek, notulen en de status van iedere expliciete actie.
- **Beheer** — ingang naar Instellingen, besluiten, geheugen en techniek.
- **Instellingen** — product, omgevingen, dispatching en schedules voor het gekozen product, plus
  duidelijk gemarkeerde Product Factory-brede AI-modellen; met een expliciete terugknop naar Beheer.
- **Besluiten** — actuele grote besluiten, peildatum, versies, intrekkingen en opvolgers.
- **Agentgeheugen** — geheugen en contextbudget per rol, inclusief versiehistorie en correcties.
- **Operatie** — processessies met uitkomst, werkqueues, AI-taken, workerstatus, dispatcherhistorie en
  actieve implementatieversies.
- **Acceptatietesten** — alleen op acceptatie: datasets resetten en vaste mockscenario's bedienen.

De getoonde inhoud is synthetische HKH-voorbeelddata en dient alleen om de informatiehiërarchie en
interacties te beoordelen.
