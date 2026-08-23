# Product Factory v2 — Productontwerp MVP

Status: voorstel voor de eerste implementatie.

Deze implementatie gebruikt exact de publieke [Productontwerp-API](productontwerp.md). Zij is
bewust klein: één algemene AI-agent doet het inhoudelijke ontwerpwerk. Er is geen droombeeld, geen
zelfstandig onderzoeksproces en geen groep gespecialiseerde agents.

Het doel van de MVP is eerst betrouwbaar complete epics te maken. Pas wanneer de praktijk een
concreet tekort laat zien, hoeft een onderdeel uit de
[uitgebreide implementatie](productontwerp-uitgebreid.md) te worden toegevoegd.

## Uitgangspunten

- Eén processessie gebruikt één agentrol: **Productontwerper**.
- De agent beoordeelt alle relevante beschikbare input in één samenhangende context.
- De mogelijke inhoudelijke uitkomst is: geen epic, één nieuwe epic of één nieuwe versie van een
  nog `AVAILABLE` epic.
- De agent maakt probleem, scope, UX, succescriteria en risico's zelf compleet.
- De agent maakt nooit stories.
- Gewone applicatiecode verzamelt input, valideert het contract en schrijft atomair naar de database.
- Er worden geen interne onderzoeksvragen, kansenlijsten of productdromen duurzaam opgeslagen.

Niet ieder signaal of iedere observatie hoeft een epic te worden. Een no-op is beter dan een
onduidelijke epic zonder aantoonbare gebruikersverbetering.

## Agent

De MVP heeft één type agent:

### Productontwerper

De Productontwerper:

- begrijpt productdoel, doelgroep en geldige besluiten;
- verwerkt nieuwe of gewijzigde gebruikerssignalen;
- bekijkt relevante stories, verificaties en kwaliteitsontwikkeling;
- leest zo nodig code en documentatie uit de publieke Git-repository;
- bekijkt zo nodig acceptatie en veilige delen van productie;
- kiest één duidelijke gebruikersverbetering;
- maakt de complete epicscope en het volledige benodigde UX-ontwerp;
- formuleert toetsbare succescriteria;
- geeft aan welke aannames, bronnen, risico's en afhankelijkheden gelden;
- levert gestructureerd een `EpicDraft` of een gemotiveerde no-op terug.

`EpicDraft` is alleen een tijdelijk object tijdens de sessie en krijgt in de MVP geen eigen tabel.

## Minimale duurzame gegevens

De MVP bewaart binnen Productontwerp alleen wat nodig is voor het publieke contract en betrouwbare
uitvoering:

- `Epic` en haar onveranderlijke versies;
- `ProcessSession` met inputversies, uitkomst, fout en publicatie-ID;
- de minimale technische lock en idempotentiegegevens voor maximaal één actieve run.

De agentcontext, het tijdelijke epicconcept en technische modelresponse mogen voor diagnose aan de
processessie worden gekoppeld volgens het geldende privacy- en bewaarbeleid. Zij zijn geen publieke
productentiteiten en worden niet als zelfstandig productgeheugen gebruikt.

Het permanente geheugen van de rol `PRODUCT_DESIGNER_MVP` staat niet in Productontwerp, maar in de
gedeelde module [Agentgeheugen](agentgeheugen.md). De procesruntime voegt vóór de agenttaak alleen de
actuele items van deze rol toe en registreert de exact gelezen geheugenversies. De agent kan geen
andere rolnaam of ander rolgeheugen kiezen.

De procesruntime leest voor `PRODUCT_DESIGN.CREATE_EPIC` provider en model uit Algemene instellingen en
vraagt daarna een complete taak aan bij [AI-uitvoering](ai-uitvoering.md). De processessie bewaart
het taak-ID, wordt `WAITING_FOR_AI` en keert terug. Een volgende run verwerkt het resultaat; de
laptopworker kent de Productontwerper-rol niet.

## Verloop van één processessie

```text
claim modulebrede run
        │
        ▼
kies één product met relevante input
        │
        ▼
maak één vaste inputmomentopname
        │
        ▼
queue één complete AiTask
        │
        ▼
WAITING_FOR_AI · latere run hervat
        │
        ├── geen goede epic ──> gemotiveerde no-op
        │
        └── EpicDraft
                │
                ▼
      deterministische validatie
                │
                ▼
  atomair publiceren als Epicversie
```

### Stap 1 — run claimen

Applicatiecode claimt de modulebrede processessie. Als al een run actief is, volgt het gedrag uit
het publieke contract. Er start nog geen agent.

### Stap 2 — product en input kiezen

Applicatiecode kiest één product met nieuwe relevante input of een verlopen periodieke controle. De
keuze gebruikt bronversies uit eerdere processessies zodat ongewijzigde input niet steeds als nieuw
wordt aangeboden.

De sessie leest één vaste momentopname van:

- productopdracht en geldige besluiten;
- open of gewijzigde gebruikerssignalen;
- bestaande epics en hun status;
- relevante stories en verificaties;
- het huidige kwaliteitsbeeld en zo nodig de historie;
- Git-code en documentatie wanneer die nodig zijn om de huidige situatie te begrijpen;
- acceptatie en eventueel veilige, read-only productie-informatie;
- het actuele geheugen van de rol `PRODUCT_DESIGNER_MVP`.

Alle bron-ID's, geheugenversies en de gelezen commit-SHA worden bij de processessie vastgelegd.

### Stap 3 — één agenttaak aanvragen en later hervatten

De volledige momentopname, inclusief eigen rolgeheugen, gekozen provider, model en responseschema,
gaat als één opaque `AiTask` naar AI-uitvoering. De opdracht is niet om zoveel mogelijk epics te
maken, maar om de belangrijkste aantoonbare en behapbare gebruikersverbetering volledig uit te
werken. De huidige run publiceert nog niets en keert wachtend terug.

Een volgende `runProcessSession()` leest het onveranderlijke `AiTaskResult`. Als het nog niet klaar
is, blijft de sessie wachten zonder een tweede taak te maken.

De agent retourneert volgens een vast schema:

- `NO_EPIC`, met een korte controleerbare reden; of
- `CREATE_EPIC`, met een complete nieuwe epic; of
- `REVISE_AVAILABLE_EPIC`, met epic-ID, verwachte versie en complete vervangende inhoud.

De agent kan geen statusovergang of databasewijziging rechtstreeks uitvoeren.

### Stap 4 — deterministisch valideren

Gewone code controleert minimaal:

- alle verplichte velden uit het Epiccontract;
- precies één duidelijke gebruikersverbetering;
- expliciete scope in en uit;
- volledig UX-ontwerp, inclusief belangrijke toestanden;
- toetsbare succescriteria;
- afwezigheid van stories of een vooraf gemaakte backlog;
- product-ID, bronrelaties en geldige besluiten;
- dat een herziene epic nog steeds `AVAILABLE` is en de verwachte versie heeft.

Een technisch mislukte uitvoering krijgt binnen dezelfde `AiTask` een begrensde nieuwe attempt van
AI-uitvoering; de procesmodule maakt daarvoor geen duplicerende taak.
Een inhoudelijk ongeldig concept wordt niet gepubliceerd. In de MVP start daarvoor geen tweede
critic-agent; de sessie eindigt met een zichtbare validatiefout en kan later opnieuw draaien.

### Stap 5 — publiceren en input afhandelen

Bij geldige output schrijft Productontwerp de nieuwe epicversie en sessie-uitkomst in één
transactie. Een herziene versie maakt de vorige beschikbare versie `SUPERSEDED`. Als de epic
intussen door Productplanning is geclaimd, faalt de publicatie gesloten.

Verwerkte gebruikerssignalen worden daarna via de publieke commands van de productmodule aan de
gepubliceerde epic gekoppeld. Er gaat geen request naar Productplanning; haar eigen schedule ontdekt
de `AVAILABLE` epic.

## Wat bewust niet in de MVP zit

- geen droombeeld of geversioneerde verre productrichting;
- geen markt-, gebruikers- en toekomstonderzoek door aparte agents;
- geen onderzoeksdossiers, hypotheses of bewijsclaims als duurzame entiteiten;
- geen extra intern leergeheugen naast het centrale geheugen van de eigen agentrol;
- geen parallelle agenttaken;
- geen UX-agent, technisch verkenner of Epiccriticus;
- geen meerdere soorten ontwerpsessies;
- geen autonome Factory-besluiten vanuit intern onderzoek.

Bronnen en aannames die nodig zijn om een epic te begrijpen, staan wel in de epic zelf. Het weglaten
van intern onderzoek verlaagt dus niet de eisen aan de publieke epic.

## Wanneer de MVP voldoende is

De MVP is bruikbaar zolang één agent consequent:

- complete, niet-overlappende epics maakt;
- voldoende goede UX opneemt;
- de bestaande applicatie correct begrijpt;
- belangrijke tegenspraak en risico's niet mist;
- epics maakt die Productplanning zonder aanvullende uitleg kan opdelen.

Terugkerende, meetbare tekortkomingen bepalen welk uitgebreid onderdeel als eerste nodig is. Een
extra agentrol wordt niet alleen toegevoegd omdat die conceptueel aantrekkelijk klinkt.

## Gerelateerde documenten

- [Productontwerp-API](productontwerp.md)
- [Productontwerp — uitgebreide implementatie](productontwerp-uitgebreid.md)
- [Agentgeheugen](agentgeheugen.md)
- [AI-uitvoering](ai-uitvoering.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
