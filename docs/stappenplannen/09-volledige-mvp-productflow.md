# Stap 9 — Volledige MVP-productflow en specificatieafsluiting

## Doel en eindtoestand

Rond de losse capabilities af als één begrijpelijke, geautomatiseerde en beheerbare productcyclus:
van richting door de Stakeholder via ontwerp, planning en levering tot aantoonbare kwaliteit en zo
nodig herstel. Activeer de in stap 2 geconfigureerde schedules, bewijs alle ketenscenario's en sluit
iedere normatieve eis uit de MVP-specificatieset aantoonbaar af.

Na deze stap is geen aanvullende implementatiestap nodig om het beschreven MVP te laten werken.
Alleen de drie documenten `uitgebreid.md`, expliciet genoemde toekomstige optimalisaties en de in
dit plan benoemde Stakeholdercontroles blijven buiten de MVP.

## Ingangseisen

- Stappen 1 tot en met 8 staan gezond op acceptatie en productie.
- De actieve providers zijn exact `product-design-impl-mvp`, `product-planning-impl-mvp` en
  `quality-impl-mvp`, plus de echte gedeelde capabilities en dispatcher.
- Iedere capability heeft haar eigen gerichte contract-, domein-, integratie- en Testbedtests groen.
- Agent Runtime-acceptatie ondersteunt server-side `MOCKED`; productie ondersteunt echte
  credentialloze jobs; Software Factory v2 is verbonden.
- Er zijn geen tijdelijke providers, v1-adapters, lokale mockexecutors of handmatige databasewrites
  nodig voor de normale route.

## Normatieve bronnen

Deze stap sluit de volledige MVP-specificatieset uit [README](README.md) af. De belangrijkste
ketenbronnen zijn:

- [Overzicht](../overzicht.md)
- [Belangrijkste functionele ketenscenario's](../ketenscenarios.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [Deployment en operatie](../platform/deployment-en-operatie.md)
- [Frontend](../stakeholder/frontend.md)
- alle API- en MVP-documenten waarnaar stappen 2 tot en met 8 verwijzen;
- alle geldende ADR's.

## Concrete opleveringen

### 1. Activeer de technische scheduler

Implementeer en activeer de interne scheduleradapter van de productmodule:

- poll vervallen `nextRunAt`s zonder tabellen van procesmodules te lezen;
- claim iedere combinatie van schedule-ID en gepland tijdstip atomair en hooguit eenmaal;
- roep na een claim uitsluitend de publieke functie van `PRODUCT_DESIGN`, `PRODUCT_PLANNING`,
  `QUALITY_ASSURANCE` of `SOFTWARE_FACTORY_DISPATCHER` aan;
- start niets voor een `INACTIVE` product en laat dispatcher aanvullend `dispatchingEnabled`
  controleren;
- registreer een botsing met een actieve call als overgeslagen; een niet-actieve
  `WAITING_FOR_AI`-sessie wordt juist hervat;
- haal na downtime maximaal één gemiste run per schedule in en bereken daarna direct het eerste
  toekomstige tijdstip;
- bereken in de ingestelde IANA-zone correct over zomer-/wintertijd, niet-bestaande en dubbele
  lokale tijdstippen;
- laat een wijziging tijdens een lopende sessie alleen toekomstige starts beïnvloeden;
- houd handmatige UI/REST-starts beschikbaar wanneer een schedule uitstaat.

Acceptatie heeft automatische starts door de omgevingsgrens standaard uit. Schedulergedrag wordt
daar met een bestuurbare klok en expliciete Test Control-acties getest. Productie activeert alleen
de schedules die de Stakeholder per product bewust op `enabled` heeft gezet.

### 2. Sluit alle capabilityovergangen aan

Controleer en herstel de volledige commandketen zonder directe proces-naar-processtart:

```text
productinput/signaal/besluit
  → Productontwerp publiceert Epic
  → Productplanning claimt Epic en publiceert Stories
  → dispatcher reserveert en levert één Story
  → Software Factory meldt DONE/CANCELLED
  → Productplanning maakt QualityWorkItem
  → Kwaliteitsbewaking publiceert Verification/Bug/Snapshot
  → Productplanning maakt herstelwerk of vraagt epicverificatie
  → Productontwerp sluit de Epic af of zet haar terug
```

Ieder pijltje gebruikt een publiek command met bronversie en idempotentiesleutel. De aanroepende
module start de volgende agent niet; de eigen schedule of bevoegde handmatige actie verwerkt het
nieuwe queuewerk. Een crash tussen lokale transactie en commandbevestiging wordt via een duurzaam
uitgaand effect hervat.

### 3. Maak product- en operationele UI compleet

Werk de frontend af zodat de hele route zonder databasekennis te volgen en bedienen is:

- rustig productoverzicht met doel, belangrijkste actieve epic, extern uitgevoerde story en concrete
  aandachtspunten;
- aparte volledige schermen voor Ontwerp, Planning, Kwaliteit, Signalen, Overleggen, Besluiten,
  Agentgeheugen en Instellingen;
- detailpagina's met opgeslagen titel/samenvatting, volledige inhoud, bron- en vervolgrelaties,
  versie, status en bewijs;
- **Automatisering** met menselijke scheduleweergave, `nextRunAt`, **Nu starten** en uitleg van
  uitgeschakeld, wachtend, overgeslagen en lopend;
- **Operatie** met minimaal Processessies, Werkqueues, AI-uitvoering, Dispatcher en Versies;
- zichtbare aandacht voor terminale AI-fout, uitgeschakelde job, retry, `DEPLOYMENT_PENDING`,
  contractfout, offline environmentkey en blijvende dispatchblokkade;
- acceptance-only Testbedbediening en banner; geen Test Control-route of `MOCKED`-bediening in
  productie.

Controleer dat iedere actie hetzelfde publieke command gebruikt als scheduler of andere modules en
dat operationele projecties nooit domeinstatus rechtstreeks wijzigen.

### 4. Bewijs alle 19 ketenscenario's

Maak in Testbed vaste, versieerbare scenario's met beginsituatie, fixtureversie, expliciete acties,
verwachte publieke toestanden en zichtbare einduitkomst. Automatiseer minimaal:

| Nr. | Scenario | Verplicht bewijs |
|---:|---|---|
| 1 | Happy flow | Productinput wordt epic, complete stories, één-voor-één levering, storytests, epictest en `COMPLETED`. |
| 2 | Geprioriteerde backlog | Complete storysets vormen één unieke productbrede volgorde. |
| 3 | Stakeholder geeft ander werk voorrang | Alleen `TODO` wijzigt en reden/historie blijven zichtbaar. |
| 4 | Opgeleverde story afgekeurd | Story blijft `DONE`; bug/herstelwerk ontstaat en epic blijft `ACTIVE`. |
| 5 | Bugfix lost probleem niet op | Dezelfde bug blijft `OPEN`; volgende gewone bugfixstory kan ontstaan. |
| 6 | Software Factory annuleert story | Story wordt `CANCELLED`; na overig werk volgt feitelijke epicbeoordeling. |
| 7 | Tijdelijk niet testbaar | Workitem bewaart poging/back-off en **Retry now** maakt geen dubbele sessie. |
| 8 | Ontbrekende epicdekking | Gerichte dekkingsstories ontstaan binnen dezelfde bevroren epic. |
| 9 | Bug tijdens epiccontrole | Bug en bugfixwerk ontstaan; epic gaat via `NEEDS_WORK` terug naar `ACTIVE`. |
| 10 | Epiccontrole geblokkeerd | Epic blijft `VERIFYING`; hetzelfde workitem wordt retrybaar. |
| 11 | Gebruikersdoel niet bereikt | Positief bewijs leidt tot terminale `NOT_SUCCESSFUL`, niet tot stil herstel. |
| 12 | Stakeholder stopt epic | Marker, stories en reservering volgen de atomaire annuleringsvolgorde. |
| 13 | Software Factory tijdelijk onbereikbaar | Retry/extern opzoeken geeft exact één externe story. |
| 14 | Planningstaak terminaal mislukt | Epic blijft `IN_PLANNING` en dezelfde claim wordt vóór nieuw werk hervat. |
| 15 | Oplevercommit nog niet live | Kwaliteit blijft `BLOCKED` met reden `DEPLOYMENT_PENDING` tot revision gelijk is. |
| 16 | Twee producten gelijktijdig | Per module één sessie per product; verschillende producten lopen parallel. |
| 17 | Dependency geannuleerd | Dependency geldt niet als voldaan en gericht herplanningswerk ontstaat. |
| 18 | Eigen automatisch ritme per proces | Aan/uit, regels, interval, tijdzone, DST, wijziging en één inhaalrun kloppen. |
| 19 | Agentvraag in overleg | Agenda, bronantwoord, rolgericht gesprek, notulen en hervatte procescontext kloppen. |

Gebruik voor scenario's 1 tot en met 19 de gewone publieke UI/REST/processfuncties. Test Control mag
alleen externe fixtures, tijd en synthetische beginsituatie sturen; zij mag geen gewenste
domeinstatus rechtstreeks in moduletabellen zetten.

### 5. Bewijs technische herstel- en veiligheidsgrenzen

Voeg ketenbrede tests toe voor:

- verloren responses en replay op alle lokale outbox-/commandeffectgrenzen;
- Agent Runtime server-side `MOCKED` zonder lokale worker; een ontbrekend fixtureantwoord faalt
  voorspelbaar en zichtbaar;
- echte Runtime-workerherstart, hervatting/late resultaatinlevering en fencing worden door Runtime-
  contracttests bewezen; Product Factory bewijst alleen correcte status- en domeinhervatting;
- harde AI-time-out, cancel en terminale fout zonder verweesde processessie;
- annuleringsmarker versus dispatchreservering tijdens een langdurige externe storing;
- exacte `deliveredCommitSha` versus deploymentrevision;
- environmentkeys uitsluitend uit actieve product-/rolgrants en nooit als waarde in database, UI,
  logs, prompt, progress, resultaat of artifactmetadata;
- acceptatie-egress alleen naar de acceptatie-Runtime en MockSoftwareFactory; productie weigert
  acceptance-only routes, fixtures en `MOCKED`;
- herstel na applicatieherstart terwijl sessies wachten, workitems geclaimd zijn of externe
  bevestiging ontbreekt.

### 6. Werk beheer, observability en runbooks af

- Laat health/readiness de database en noodzakelijke externe configuratie correct onderscheiden:
  een tijdelijke externe storing maakt de applicatie niet onnodig onstartbaar, maar staat zichtbaar
  in Operatie.
- Voeg veilige gestructureerde logs, metrics en correlatie-ID's toe voor schedulerclaim,
  processessie, AI-taak, workitem, deliveryattempt en externe job/story.
- Leg runbooks vast voor geblokkeerde sessie, AI-job uitgeschakeld, onbekende/offline key,
  kwaliteitsretry, achterlopende deployment, dispatchcontractfout en externe storing.
- Werk configuratievoorbeelden, secretsleutellijst, Testbedscenario's, API-documentatie en
  `ImplementationManifest` bij op de werkelijk actieve MVP.
- Verwijder tijdelijke featureflags, adapters, placeholders en dode code die alleen tussenstappen
  mogelijk maakten.

## Specificatieafsluiting

### Dekkingsmatrix per documentgroep

Vul vóór afronding een reviewbaar bewijsrecord in de PR- of releasenotities in. Per normatieve
sectie noteert dit record: eigenaarstap, implementatiepad, automatisch test-/scenario-ID of
operationeel bewijs, en uitkomst. Gebruik deze minimale eigenaarsverdeling:

| Documentgroep | Primaire stap | Stap 9 controleert |
|---|---:|---|
| technische basis, configuratie, secrets, deployment en operatie | 1, 4, 8 | actieve config, veilige grenzen, immutable promotie, health en runbooks |
| Maven/composition en publieke modulegrenzen | 1–8 | exact één provider per actieve capability en geen interne cross-module toegang |
| integratie-/acceptatietestbed | 1–9 | vaste datasets, echte adapters waar vereist, Test Control alleen op acceptatie |
| product, signalen, vragen, overleggen en frontendbasis | 2, 4 | volledige UI/commands, meetingagents en ketendoorwerking |
| Besluitenregister | 2, 4 | geldigheid, historie en gecontroleerde meeting-/Factoryregistratie |
| Agentgeheugen | 3, 4 | rolisolatie, meetinguitzonderingen, audit en daadwerkelijk gebruikte versies |
| AI-uitvoering en Runtime-integratie | 3, 4 | outbox, grants, mocks, echte route, herstel, time-out en artifacts |
| Productontwerp API + MVP | 5 | compleet contract, sessieflow, lifecycle en keteninput/-output |
| Productplanning API + MVP | 6 | stories, backlog, workitems, reservering, annulering en herstel |
| Kwaliteitsbewaking API + MVP | 7 | alle werksoorten, bewijs, retries, bugs en snapshots |
| Software Factory-dispatcher | 8 | v2-transport, idempotentie, status en fout-/crashherstel |
| overzicht, entiteiten en ketenscenario's | 9 | eigenaarschap, alle 19 scenario's en complete normale route |
| geldende ADR's | 1–9 | code en documentatie volgen de beslissing; afwijkingen hebben een nieuwe ADR |

### Auditprocedure

1. Genereer een lijst van alle headings en normatieve uitspraken (`moet`, `mag niet`, `alleen`,
   `altijd`, `precies`, `maximaal`, `invariant`) in de MVP-specificatieset.
2. Koppel iedere uitspraak aan exact één rij in het bewijsrecord; meerdere bewijzen per eis mogen.
3. Markeer uitsluitend `IMPLEMENTED`, `VERIFIED` of `NOT_APPLICABLE` met concrete reden en bron.
   `TODO`, `UNKNOWN`, impliciete aanname of een link naar alleen documentatie blokkeert de stap.
4. Controleer omgekeerd dat publieke codevelden/statussen en zichtbare UI-acties nog in de
   specificaties voorkomen; verwijder onbedoelde extra domeinconcepten of documenteer ze normatief.
5. Controleer alle relatieve links, voorbeeldconfiguratie, API-schema's en statusnamen op drift.
6. Laat de volledige regressiesuite en alle 19 scenario's opnieuw draaien op de te releasen commit.

`NOT_APPLICABLE` is alleen geldig voor een expliciete omgevingsvariant of een aantoonbaar door de
bron uitgesloten situatie. Een ontbrekende implementatie is nooit `NOT_APPLICABLE`.

## Uitvoeringsvolgorde

1. Maak vóór codewerk de eerste dekkingsmatrix en lijst alle nog onbewezen MVP-eisen.
2. Sluit ontbrekende capabilitycommands, projecties en fout-/herstelroutes af.
3. Implementeer en test de scheduler met bestuurbare klok; activeer hem volgens de omgevingsgrens.
4. Maak frontend, Operatie en Testbed compleet.
5. Automatiseer en draai ketenscenario's 1 tot en met 19.
6. Voer de technische herstel- en veiligheidsproeven uit.
7. Werk runbooks, configuratie, manifest en documentatie bij en verwijder tussenstapcode.
8. Voer de normatieve audit uit tot geen open of onbekende rij resteert.
9. Laat de volledige build en regressiesuite groen worden en push de release naar `main`.
10. Controleer de automatische acceptatie- en productiepromotie, identieke digests, health en
    revisionweergave; voeg het definitieve bewijsrecord toe aan de release.

## Verplichte releasebewijzen

- alle gerichte tests uit stappen 1 tot en met 8 blijven groen;
- alle 19 ketenscenario's slagen met vaste scenario- en datasetversies;
- scheduler- en ketenconcurrentietests slagen met minimaal twee producten;
- de bewijsrecord bevat geen `TODO`, `UNKNOWN` of onbewezen normatieve MVP-eis;
- `ImplementationManifest` toont uitsluitend de drie MVP-procesproviders en de bedoelde gedeelde
  providers;
- acceptatie draait server-side mocks en productie weigert alle acceptance-only mogelijkheden;
- de releaseworkflow promoot exact dezelfde backend- en frontenddigests en beide Argo-apps zijn
  `Synced` en `Healthy` op dezelfde bronrevisie.

## Aanbevolen commitgrenzen

1. eerste dekkingsmatrix en ontbrekende capabilityafsluiting;
2. scheduler en concurrency/herstel;
3. complete frontend, Operatie en Testbed;
4. ketenscenario's 1–10;
5. ketenscenario's 11–19 en technische herstelproeven;
6. runbooks, normatieve audit, opschoning en releasecorrecties.

## Buiten scope en Stakeholdercontroles

- Geen implementatie uit `uitgebreid.md` wordt gebouwd, geselecteerd of gedeeltelijk voorbereid.
- Productieoptimalisatie op basis van latere gebruikservaring en externe notificaties via e-mail,
  Telegram of andere diensten vallen buiten de MVP.
- Een handmatige browsertest, handmatige productiebackup, controle op 320px/200%-weergave en een
  volledige menselijke eindcontrole worden door de Stakeholder uitgevoerd als die dat wenselijk
  vindt. Zij zijn geen blokkade voor deze technische stap en worden niet als ontbrekende
  specificatie geregistreerd.

## Definitie van klaar

Stap 9 is pas klaar wanneer de normale route én alle afwijkende ketenscenario's via publieke
grenzen werken, iedere overgang in de inhoudelijke en operationele UI verklaarbaar is, schedules
veilig actief zijn, en de specificatieaudit nul open MVP-eisen oplevert. De volledige automatische
bewijsset is groen en exact dezelfde immutable artifacts draaien gezond op acceptatie en productie.

Op dat moment is alles uit de normatieve MVP-specificatieset geïmplementeerd. De enige bewust niet
geïmplementeerde ontwerpen zijn de expliciet uitgesloten uitgebreide procesvarianten en toekomstige
optimalisaties.
