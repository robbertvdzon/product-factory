# Product Factory v2 — Productplanning MVP

Status: voorstel voor de eerste implementatie.

Deze implementatie gebruikt exact de publieke [Productplanning-API](api.md). Eén
algemene Planner-agent kiest epics, maakt stories, verwerkt herstelwerk en bepaalt de backlogvolgorde.
De vaste [Software Factory-dispatcher](../software-factory-dispatcher.md) staat hier los van en gebruikt
geen agent.

Zij wordt gebouwd als `product-planning-impl-mvp`. De ene main-module neemt dit artifact of de
uitgebreide variant op, nooit beide.

## Uitgangspunten

- Eén processessie gebruikt één agentrol: **Planner**.
- Dezelfde agent mag binnen de sessie meerdere opeenvolgende gestructureerde stappen uitvoeren.
- Alle bij de start voor hetzelfde product geclaimde `PlanningWorkItem`s worden in één vaste
  inputmomentopname beoordeeld.
- Normaal kiest een run één nieuwe `AVAILABLE` hoofdepic; dit is geen technische limiet. Dezelfde
  agent mag meerdere urgente epics uit dezelfde momentopname kiezen wanneer dat nodig is.
- De agent levert de complete storyset voor een gekozen epic, niet alleen de eerstvolgende stories.
- De agent maakt ook de volledige productbrede volgorde van alle `TODO`-stories.
- Gewone applicatiecode claimt, valideert, schrijft en roept modulecommands aan.
- Er is geen extra intern planningsgeheugen en er zijn geen gespecialiseerde agents of duurzame
  drafts; de Planner gebruikt wel het centrale geheugen van haar eigen agentrol.

Meerdere epics mogen tegelijk actief zijn en gericht werk voor verschillende epics wordt in
dezelfde run verwerkt. Een urgente epic kan dus tussendoor worden geclaimd zonder een andere actieve
epic af te sluiten.

## Agent

De MVP heeft één type agent:

### Planner

De Planner:

- vergelijkt beschikbare epics met productdoel, besluiten en bestaand werk;
- kiest zo nodig één of meer epics om te claimen;
- verwerkt bugfix-, dekkingsgat-, prioriteits- en herplanwerk;
- verdeelt iedere gekozen epic volledig in kleine, zelfstandig uitvoerbare stories;
- neemt het relevante bevroren UX-ontwerp zelfstandig op in iedere story waarop UX van toepassing
  is;
- controleert dat de stories samen de volledige oplossing en alle acceptatiecriteria afdekken;
- combineert productstories en bugfixstories tot één productbrede `TODO`-volgorde;
- geeft per belangrijke prioriteitskeuze een korte, controleerbare reden;
- retourneert één gestructureerd `PlanningDraft`.

`PlanningDraft` is alleen een tijdelijk object in de processessie en krijgt geen eigen tabel.

## Minimale duurzame gegevens

De MVP bewaart binnen Productplanning alleen de gegevens die het publieke contract vereist:

- `PlanningWorkItem`;
- `Story` en onveranderlijke storyversies;
- `ProcessSession`;
- minimale technische lock- en idempotentiegegevens.

Agentcontext en tijdelijk planconcept mogen volgens het bewaarbeleid aan de processessie worden
gekoppeld voor diagnose. Zij zijn geen publieke entiteiten en vormen geen zelfstandig
planningsgeheugen.

Het permanente geheugen van de rol `PLANNER_MVP` staat in
[Agentgeheugen](../../gedeelde-modules/agentgeheugen.md). De procesruntime voegt alleen de actuele items van deze rol aan de
agenttaak toe en registreert de exact gelezen versies. De Planner kan geen geheugen van een andere
rol opvragen.

Voor iedere Planner-taak leest de procesruntime de betreffende `AiJobConfiguration`, neemt provider en
model als vaste taakwaarden over en gebruikt [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md). Een sessie met open
taken krijgt `WAITING_FOR_AI`; een volgende run hervat haar zonder dubbele taak.

## Verloop van één processessie

```text
claim of hervat productrun en PENDING workitems
             │
             ▼
lees eerst geclaimde IN_PLANNING epic, anders AVAILABLE epics
             │
             ▼
queue selectie-AiTask
             │
             ▼
WAITING_FOR_AI · latere run hervat
             │
             ▼
Planner-resultaat kiest relevante epic(s)
             │
             ▼
applicatie claimt exacte epicversie(s)
             │
             ▼
queue plan-AiTask(s)
             │
             ▼
WAITING_FOR_AI · latere run hervat
             │
             ▼
deterministische contract- en dekkingscontrole
             │
             ▼
atomair publiceren en workitems afronden
```

### Stap 1 — claimen en input vastzetten

Applicatiecode claimt of hervat de run voor het opgegeven product. Een bestaande processessie en
haar reeds geclaimde `IN_PLANNING` epic gaan altijd voor nieuw werk. Alleen zonder onafgeronde
sessie claimt zij de op dat moment `PENDING` workitems van dit product en leest zij één vaste
momentopname van beschikbare epics, bestaande open stories, productopdracht, geldige besluiten,
exacte bug- en verificatiebronnen en het actuele geheugen van `PLANNER_MVP`.

Git, acceptatie en veilige productie-informatie worden alleen toegevoegd als zij nodig zijn om
bestaande routes, schermen of technische grenzen te begrijpen. Alle gebruikte bron- en
geheugenversies en de bevroren commit-SHA komen op de processessie. De worker checkt die SHA zelf
uit in de tijdelijke Dockeromgeving.

### Stap 2 — werk beoordelen en epic claimen

De Planner ziet de hele momentopname en retourneert eerst:

- welk gericht workitem welk planresultaat nodig heeft;
- welke `AVAILABLE` epic of epics deze run moet plannen;
- of alleen herprioritering nodig is;
- welke bronnen ontbreken en daarom een workitem blokkeren.

Applicatiecode roept voor iedere gekozen epic `claimEpicForPlanning(...)` aan. Mislukt een claim door
een versiewijziging of gelijktijdige statusovergang, dan wordt voor die epic niets gepubliceerd. De
overige epics en geclaimde workitems kunnen nog wel worden afgehandeld.

### Stap 3 — dezelfde agentrol maakt het complete plan

Een volgende complete `AiTask` voor dezelfde Plannerrol en processessie ontvangt de bevestigde
epicversie en maakt vervolgens:

- de volledige storyset voor de gekozen epic;
- gerichte bugfix- en dekkingsgatstories;
- de definitieve volgorde van alle bestaande en nieuwe `TODO`-stories.

Bij een `REPRIORITIZE_EPIC` kan het resultaat uitsluitend een nieuwe volgorde zijn als er al complete
stories bestaan. Een `IN_PROGRESS` story wordt niet herschikt of onderbroken.

Iedere inhoudelijke Planner-stap wordt als complete opaque taak gequeue'd. Sequentiële stappen
worden over meerdere korte hervattingen van dezelfde processessie uitgevoerd; er blijft nooit een
serverthread wachten op een laptopworker of server-side mockresultaat.

### Stap 4 — deterministisch valideren

Gewone code controleert minimaal:

- alle verplichte velden uit het Storycontract;
- een korte, enkelregelige `title` en een `summary` van maximaal twee korte zinnen die niet met de
  volledige story botsen;
- exacte epic-, bug- en verificatieversies;
- volledige dekking van oplossing, acceptatiecriteria en eventuele UX van iedere nieuw geplande
  epic;
- geen onbedoelde overlap of onbeantwoorde afhankelijkheid;
- zelfstandige acceptatiecriteria en, waar van toepassing, UX per story;
- toegestane storystatusovergangen;
- unieke productbrede `sequenceNumber`s ten opzichte van alle `TODO`- en `IN_PROGRESS`-stories,
  waarbij alleen `TODO`-stories worden herordend;
- afwezigheid van een annuleringsmarker voor iedere epic waarvoor nieuwe stories worden
  gepubliceerd;
- dat alleen geclaimde workitems als afgehandeld worden gemarkeerd.

Een technisch mislukte uitvoering krijgt binnen dezelfde `AiTask` een begrensde nieuwe attempt van
AI-uitvoering. Eindigt die taak definitief als `FAILED`, dan wordt de processessie zichtbaar
`BLOCKED` en maakt een latere productrun na back-off een nieuwe taak voor dezelfde epicclaim. De
epic blijft `IN_PLANNING` en wordt niet door nieuw werk gepasseerd. Een inhoudelijk ongeldig concept
wordt niet gepubliceerd. Er start in de MVP geen
aparte critic-agent; de fout blijft zichtbaar op de processessie en betrokken workitems.

### Stap 5 — atomair publiceren

Productplanning schrijft stories, storyversies, nieuwe volgorde en workitemresultaten atomair. Voor
een nieuwe epic roept zij daarna `markEpicActive(...)` aan. Een modulecommand dat tijdelijk faalt,
kan met dezelfde idempotentiesleutel worden hervat.

Bij een bugfixstory bewaart dezelfde transactie een intern uitgaand commandeffect voor
`linkBugfixStory(bugId, storyId)`. De koppeling moet bevestigd zijn voordat de story uitvoerbaar is
en het bijbehorende `PlanningWorkItem` `DONE` wordt. Een tijdelijke commandfout wordt idempotent
hervat. Per bug mag maximaal één gekoppelde story tegelijk `TODO` of `IN_PROGRESS` zijn; een eerdere
`DONE`- of `CANCELLED`-story blokkeert een volgende poging niet. Productplanning publiceert
uitsluitend productstories en bugfixstories.

De eigen schedule van de dispatcher vindt later de eerste uitvoerbare `TODO`-story. De planner start
de dispatcher niet.

Een dependency is pas voldaan wanneer de bronstory `DONE` is. Verificatie hoeft daarvoor niet klaar
te zijn. Annulering van een dependency maakt haar afhankelijke `TODO`-stories niet uitvoerbaar en
zet idempotent `REPLAN_CANCELLED_DEPENDENCY` klaar voor een volgende productrun.

## Wat bewust niet in de MVP zit

- geen aparte Epicplanner, Storymaker, Backlogplanner of Planningscriticus;
- geen parallelle agents; dezelfde Planner verwerkt eventueel meerdere epics sequentieel;
- geen duurzame candidate sets, coverage maps of order drafts;
- geen extra intern planningsgeheugen naast het centrale geheugen van de eigen agentrol;
- geen tweede agent die storydekking of prioritering beoordeelt;
- geen kunstmatig voorraadniveau of backlogmaximum.

Deze vereenvoudiging verlaagt niet de eisen aan stories, epicdekking, versiebevriezing of de
atomische productbrede volgorde.

## Dispatcher

De MVP gebruikt ongewijzigd de [Software Factory-dispatcher](../software-factory-dispatcher.md).
Retry, idempotentie en opleverstatus worden volledig door die deterministische adapter afgehandeld
en starten geen Planner-agent. Software Factory accepteert ieder contractgeldig storypakket; een
weigering is een technische contractfout en verandert nooit de storyinhoud.

## Wanneer de MVP voldoende is

De MVP is bruikbaar zolang één Planner consequent:

- epics volledig en zonder grote overlap in stories verdeelt;
- iedere story zelfstandig uitvoerbaar maakt;
- UX en acceptatiecriteria compleet overneemt;
- bugs en dekkingsgaten correct vertaalt;
- een uitlegbare, stabiele productbrede volgorde maakt.

Meetbare problemen met contextgrootte, epicdekking, inconsistent storyniveau of prioritering bepalen
welke gespecialiseerde rol als eerste nodig is.

## Gerelateerde documenten

- [Productplanning-API](api.md)
- [Productplanning — uitgebreide implementatie](uitgebreid.md)
- [Software Factory-dispatcher](../software-factory-dispatcher.md)
- [Productontwerp-API](../productontwerp/api.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
