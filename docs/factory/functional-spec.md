# Functional Spec

De Product Factory laat producten autonoom doorontwikkelen: per product draaien productcycli
(shadow iterations) waarin agents onderzoek doen, storykandidaten schrijven en die — als het product op
autonoom staat — als stories naar de Software Factory sturen.

## De overzichtspagina

De Flutter-webapp (`dashboard-frontend`) heeft één hoofdscherm: het productoverzicht. Het ververst zichzelf
elke 5 seconden en bestaat van boven naar beneden uit:

1. **Metric-tegels** — totalen voor producten, interne storykandidaten, workspace-publicaties,
   shadow-iteraties en Software Factory-stories. Deze tellers tonen altijd het *totaal*, ook als de lijst
   eronder is ingekort.
2. **Producten** — per product missie, status, ontwikkelmodus en knoppen voor pauzeren/hervatten,
   instellingen en 'Start productcyclus nu'. Volgorde: zoals de backend hem levert (op slug).
3. **Productcycli en onderzoekssessies** — per cyclus status, huidige rol, **starttijd** en
   **doorlooptijd**, aantal kandidaten en of de cyclus doorgezet mag worden. Klikken opent de
   detaildialoog met voortgang, artifacts en het productdossier.
4. **Software Factory-stories** — de leveringen met externe storykey, status en fase.
5. **Benodigde access tokens** — openstaande handmatige acties, af te melden met een toelichting.
6. **Storywachtrij** — storykandidaten verdeeld over Fout / Bezig / In wachtrij / Klaar.
7. **Workspace** — gepubliceerde artifacts, klikbaar om de inhoud te tonen.

### Start- en doorlooptijd van een productcyclus

- Starttijd = `startedAt`; is die leeg, dan `createdAt`.
- Doorlooptijd = `completedAt - startedAt`, leesbaar als `2u 13m`, `4m 12s` of `35s`.
- Loopt de cyclus nog, dan staat er `loopt nog: <tijd sinds start>`; die waarde loopt mee met de
  auto-refresh.
- Is de cyclus nog niet gestart, dan staat er geen doorlooptijd.
- Datum en tijd staan in de lokale tijdzone van de browser als `dd-MM-yyyy HH:mm`, nooit als ruwe
  ISO-string.

### Lijstbeperking met de 'Meer'-knop

Alle lijsten op de overzichtspagina (producten, productcycli, Software Factory-stories, access tokens,
elke subsectie van de storywachtrij en workspace-publicaties) tonen standaard **5 items**. Staat er meer
klaar, dan verschijnt eronder een knop **'Meer (nog N)'** die er telkens **10** bij toont; de knop
verdwijnt zodra alles zichtbaar is. Elke sectie heeft een eigen, onafhankelijke teller, en die teller
overleeft de auto-refresh: een uitgeklapte lijst blijft uitgeklapt en nieuwe items verschijnen bovenaan.
Lijsten met een bruikbaar tijdstempel staan gesorteerd op nieuwste eerst; workspace-publicaties hebben geen
tijdstempel en houden de volgorde van de backend.

## Testerafspraken

Een testerresultaat bereikt alleen `tested` met compleet groen machinebewijs uit
`.factory/verification.yaml` voor exact dezelfde HEAD/worktree-tree. Missing bewijs/config, onbekende
versie, tool-missing, timeout, non-zero en revisionmismatch leveren altijd `test-rejected` op;
pre-existing, flaky en omgevingsfouten zijn nooit groen.
