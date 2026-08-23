# Product Factory v2 — Productplanning uitgebreide implementatie

Status: doelontwerp voor een latere uitgebreide implementatie.

Deze implementatie gebruikt exact de publieke [Productplanning-API](productplanning.md). Zij breidt
de [MVP](productplanning-mvp.md) intern uit met gespecialiseerde rollen, parallelle voorbereiding,
permanent geheugen per agentrol en een afzonderlijke kritiekstap. De
[Software Factory-dispatcher](software-factory-dispatcher.md) blijft dezelfde deterministische
adapter.

## Interne entiteiten

Naast de publieke module-entiteiten kan de uitgebreide implementatie gebruiken:

- `EpicCandidateSet` en `EpicSelectionAssessment` — vergelijking en onderbouwing bij epicselectie;
- `StoryDraft` — conceptstory vóór publicatie;
- `StoryUxSnapshot` — tijdelijk samengesteld zelfstandig UX-deel;
- `StoryCoverageMap` — relatie tussen epicscope, succescriteria en conceptstories;
- `StoryCandidateSet` en `PriorityAssessment` — vergelijking van bestaand en nieuw werk;
- `StoryOrderDraft` — voorgestelde productbrede `TODO`-volgorde;

Deze objecten steken de modulegrens niet over. Alleen `Story`, `PlanningWorkItem`, `ProcessSession`
en de vaste transportcontracten zijn voor andere onderdelen zichtbaar.

## Agents

Een inhoudelijke run kan vier vaste agentrollen gebruiken:

1. **Epicplanner** — beoordeelt beschikbare epics, geldige besluiten en gerichte workitems.
2. **Storymaker** — verdeelt iedere bevroren epic of bewezen ontbrekende dekking volledig in
   zelfstandige stories.
3. **Backlogplanner** — combineert productstories en bugfixstories en bepaalt de productbrede
   `sequenceNumber`s.
4. **Planningscriticus** — controleert dekking, storygrootte, afhankelijkheden, UX en prioriteitsreden.

Alleen `runProcessSession()` mag voor deze rollen AI-taken aanvragen. Niet iedere run hoeft alle
rollen te gebruiken: een zuivere herprioritering kan bijvoorbeeld zonder Storymaker.

Voor iedere taak leest Productplanning de betreffende `AiJobConfiguration` en geeft zij de complete
opaque taak met bevroren provider en model aan [AI-uitvoering](ai-uitvoering.md). AI-uitvoering kent
de planningsrollen niet. De processessie bewaart de taak-ID's, keert met `WAITING_FOR_AI` terug en
wordt later hervat.

Iedere rol heeft in [Agentgeheugen](agentgeheugen.md) haar eigen permanente geheugen. De procesruntime
leidt de vaste `AgentRoleKey` uit vertrouwde configuratie af en geeft een agent alleen actuele items
van die rol. De Epicplanner leest dus niet het geheugen van de Storymaker, Backlogplanner of
Planningscriticus. Rollen delen werk alleen via expliciete concepten en handoffs binnen de sessie.

## Verloop van één processessie

```text
claim PENDING workitems, beschikbare epics en inputversies
                      │
                      ▼
       Epicplanner beoordeelt werk en epics
                      │
             ┌────────┴────────┐
             ▼                 ▼
       stories per epic   bugfix/gat/reparatie
             └────────┬────────┘
                      ▼
       Backlogplanner maakt één TODO-volgorde
                      │
                      ▼
       Planningscriticus: akkoord of herstel
                      │
                      ▼
           atomair publiceren
```

De run verwerkt de bij de start geclaimde batch als één consistente momentopname. Onafhankelijke
epics, bugs en storydelen mogen via meerdere queuetaken parallel worden voorbereid. Iedere golf
eindigt tijdelijk als `WAITING_FOR_AI`; een volgende run hervat de sessie. Epicclaims, definitieve
kritiek, publicatie en globale ordening zijn sequentieel en atomair.

### Stap 1 — claimen en beoordelen

Applicatiecode claimt de modulebrede run en vaste batch. De Epicplanner ontvangt:

- alle `PENDING` workitems uit de batch;
- `AVAILABLE` epics en bestaande actieve epics;
- productopdracht en geldige besluiten;
- bestaande storyvolgorde;
- exacte bugs, verificaties en relevante omgevingsinformatie;
- zo nodig Git-code, documentatie en veilige applicatiecontext.

De Epicplanner maakt een `EpicSelectionAssessment`, bepaalt welke beschikbare epic of epics deze run
worden gepland en legt gewone prioriteitsredenen intern vast. Productplanning claimt iedere gekozen
exacte epicversie voordat storyvorming begint.

### Stap 2 — parallel stories vormen

Storymakers mogen onafhankelijk werken aan:

- de complete storyset per geclaimde epic;
- bewezen ontbrekende dekking binnen een bestaande epic;
- bugfixstories;
- reparatie van definitief inhoudelijk afgewezen, nog niet verzonden stories.

Iedere Storymaker levert `StoryDraft`s, zelfstandige `StoryUxSnapshot`s en een `StoryCoverageMap`.
De maps moeten samen ieder onderdeel van epicscope, UX en succescriteria aantoonbaar afdekken.

### Stap 3 — productbreed ordenen

De Backlogplanner ziet alle geldige conceptstories plus bestaande `TODO`- en `IN_PROGRESS`-stories.
Hij maakt één `StoryOrderDraft` op basis van onder meer gebruikerswaarde, urgentie, bugernst,
afhankelijkheden, Stakeholderprioriteit en leverbaarheid.

Een `IN_PROGRESS` story blijft staan en wordt niet onderbroken. Er ontstaat geen tweede roadmap of
duurzame backlogentiteit; de order draft is alleen voorbereiding op nieuwe `sequenceNumber`s.

### Stap 4 — kritiek en herstel

De Planningscriticus controleert minimaal:

- volledige dekking van iedere geclaimde epic;
- kleine, zelfstandig leverbare stories zonder onnodige overlap;
- complete acceptatiecriteria en zelfstandige UX;
- correcte bron- en versierelaties;
- uitvoerbare afhankelijkheden;
- uitlegbare prioriteitsredenen;
- één consistente productbrede volgorde;
- dat een herprioritering geen `IN_PROGRESS` werk stilzet.

Bij herstelbare tekortkomingen gaat één gerichte opdracht terug naar Storymaker of Backlogplanner.
Daarna keurt de criticus goed of blokkeert publicatie.

### Stap 5 — atomair publiceren

Na deterministische contractvalidatie schrijft Productplanning stories, storyversies,
`sequenceNumber`s en workitemresultaten atomair. Zij roept voor nieuw geplande epics daarna
idempotent `markEpicActive(...)` aan.

Een volgende planningsrun ziet nieuw verschenen workitems en epics. De dispatcher draait op haar
eigen schedule en wordt niet door de agents gestart.

## Intern leren

Een rol kan na succesvolle validatie een geheugenactie voor haar eigen rol voorstellen. Zo kan de
Storymaker een les over te grote stories bewaren en de Backlogplanner een les over
afhankelijkheden. Gewone applicatiecode valideert en schrijft toevoegen, vervangen of intrekken via
Agentgeheugen. Geen rol kan deze lessen in het geheugen van een andere rol plaatsen of lezen.

Rolgeheugen verandert nooit zelfstandig een epic, bug, besluit of reeds verzonden story.

Alle informatie die Software Factory nodig heeft, blijft in de gepubliceerde story staan. Het
interne geheugen is dus geen verborgen uitvoeringscontract.

## Dispatcher

De uitgebreide implementatie gebruikt exact dezelfde
[Software Factory-dispatcher](software-factory-dispatcher.md) als de MVP. Technische retries,
statussynchronisatie en `DeliveryAttempt`s lopen buiten de agents. Alleen een definitieve
inhoudelijke afwijzing maakt een `REPAIR_STORY`-workitem voor een latere intelligente run.

## Hervatten en interne idempotentie

- Iedere agenttaak verwijst naar dezelfde vaste inputmomentopname en naar de exact gelezen
  geheugenversies van haar eigen rol.
- Parallelle agents schrijven alleen concepten; nooit rechtstreeks publieke stories.
- Gedeeltelijke concepten kunnen worden hervat, maar zijn geen productwaarheid.
- Een inmiddels gewijzigde of niet meer geldige bronversie blokkeert publicatie.
- Een gerichte herstelronde gebruikt dezelfde bronversies en behoudt de auditrelatie.
- Nieuw queuewerk tijdens een run blijft voor de volgende sessie staan.

## Wanneer een sessie klaar is

Een uitgebreide planningsrun is klaar wanneer:

- ieder geclaimd workitem `DONE`, `BLOCKED` of `FAILED` is;
- iedere nieuwe story zelfstandig uitvoerbaar is;
- de coverage maps de volledige epicscope aantoonbaar afdekken;
- epic-, bug- en verificatieversies exact vastliggen;
- de Planningscriticus de inhoud en volgorde heeft goedgekeurd;
- productbrede `sequenceNumber`s consistent zijn;
- alle publieke output atomair en geversioneerd is opgeslagen.

## Gerelateerde documenten

- [Productplanning-API](productplanning.md)
- [Productplanning — MVP](productplanning-mvp.md)
- [Software Factory-dispatcher](software-factory-dispatcher.md)
- [Productontwerp-API](productontwerp.md)
- [Agentgeheugen](agentgeheugen.md)
- [AI-uitvoering](ai-uitvoering.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
