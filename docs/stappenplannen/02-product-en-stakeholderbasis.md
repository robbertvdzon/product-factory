# Stap 2 — Product- en stakeholderbasis

## Doel en eindtoestand

Maak Product Factory zonder AI-processen bruikbaar voor de ene globale Stakeholder. Na deze stap
kan zij producten en hun richting beheren, signalen en vragen volgen, overleggen vastleggen,
blijvende besluiten beheren en de vier toekomstige uitvoeringsritmes configureren. Alle gegevens
zijn duurzaam, geversioneerd en alleen via publieke commands en queries toegankelijk.

De schedules worden in deze stap volledig opgeslagen, gevalideerd en weergegeven. De technische
scheduler start nog niets automatisch; die activering hoort bij stap 9. Overleggen worden al via
het definitieve publieke contract afgehandeld, maar nog zonder Meeting Agent of notulenagent.

## Ingangseisen

- Stap 1 staat gezond op acceptatie en productie.
- `product-factory-api` bevat de publieke contractpackages voor product/overleg en besluiten.
- Authenticatie, actorbepaling, optimistic locking, Flyway, Testbed en de frontend-API-client uit
  stap 1 zijn beschikbaar.
- De uitvoerder heeft alle onderstaande normatieve bronnen volledig gelezen en vergelijkt die eerst
  met de aanwezige contracten. Contractafwijkingen worden vóór de implementatie gecorrigeerd.

## Normatieve bronnen

- [Overzicht](../overzicht.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Besluitenregister](../gedeelde-modules/besluitenregister.md)
- [Product- en overleg-API](../stakeholder/product-en-overleg-api.md)
- [Overleggen met de Stakeholder](../stakeholder/overleggen.md)
- [Frontend](../stakeholder/frontend.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)

## Concrete opleveringen

### Modules en composition root

- Maak `product-impl` de enige actieve provider van de product-/overlegcapability.
- Maak `decisions-impl` de enige actieve provider van het Besluitenregister.
- Laat beide implementaties alleen van `product-factory-api` en technische platformdelen afhangen;
  zij krijgen geen repository- of tabeltoegang tot elkaar.
- Registreer artifact, variant, versie en broncommit van beide capabilities in het
  `ImplementationManifest`.

### Duurzame gegevens en migraties

Voeg voorwaartse Flywaymigraties, constraints en indexen toe voor:

- `Product` met stabiel ID, naam, `ACTIVE`/`INACTIVE`, `dispatchingEnabled`, versie en auditvelden;
- geversioneerde `ProductAssignment` met doelgroep, productdoel, harde grenzen en publieke Git-URL;
- geversioneerde `TestableProductConfiguration` met omgevingen, veilige routes, data-/toegangsgrenzen
  en revisionendpoint, maar zonder opgeslagen secretwaarden;
- precies één geversioneerde `ProcessScheduleConfiguration` per product en `ScheduledProcess`;
- `UserSignal` met onveranderlijke broninhoud, status, onderzoeksuitkomst en resultaatkoppelingen;
- `StakeholderQuestion` met vertrouwde vragende rol, bronprocessessie, context, gekoppelde objecten,
  antwoordbron en status;
- `Meeting`, agenda, gekoppelde objecten, deelnemers, append-only berichten, notulen en expliciete
  doorwerkingsresultaten;
- `Decision`, onveranderlijke `DecisionDetails`-versies, intrekkingen en vervangingsrelaties;
- idempotentiesleutels, verwachte versies en unieke constraints die dubbele commands en overlappende
  geldigheidsperioden voorkomen.

Maak voor ieder nieuw product transactioneel vier uitgeschakelde scheduleconfiguraties zonder
`nextRunAt`. Seed in acceptatie uitsluitend vaste synthetische producten, signalen, vragen,
overleggen en besluiten; productie krijgt geen demodata.

### Product-, signaal- en schedulelogica

Implementeer alle commands en queries uit de product-/overleg-API. Daarbij geldt minimaal:

- alleen de globale geauthenticeerde Stakeholder mag productopdracht, testconfiguratie,
  dispatchinstelling en schedules beheren;
- iedere mutatie controleert actor, product, verwachte versie en idempotentiesleutel;
- de broninhoud van een signaal blijft onveranderlijk; status en koppelingen veranderen alleen via
  `markUserSignalInReview`, `recordSignalInvestigation` en `linkSignalToEpic`;
- filters leveren ook historie wanneer geen actuele-statusfilter is opgegeven;
- een schedule gebruikt óf een niet-lege set week/dag/tijdregels óf één interval in hele minuten;
  mengvormen, ongeldige lokale tijden en lege ingeschakelde patronen worden afgewezen;
- de backend ontdubbelt gelijke dag/tijdcombinaties en berekent `nextRunAt` in de gekozen IANA-zone;
- uitschakelen bewaart het patroon en wist `nextRunAt`; opnieuw inschakelen kiest alleen een
  toekomstig tijdstip;
- een schedulewijziging raakt geen lopende sessie en handmatige starts blijven onafhankelijk;
- de claim-/pollingadapter voor automatische starts mag intern worden voorbereid en getest, maar is
  tot stap 9 uitgeschakeld.

### Vragen en overleggen zonder AI

- Sta `askStakeholder` alleen toe vanuit vertrouwde procescode die rol, product, processessie en
  idempotentiesleutel invult. Vrije invoer kan geen agentrol nabootsen.
- Voeg iedere open vraag automatisch toe aan de agenda/context van een bestaand of volgend overleg
  voor hetzelfde product; agendering beantwoordt of verwijdert de vraag niet.
- Valideer bij beantwoording dat vraag, meeting en exact Stakeholderbericht bij hetzelfde product
  horen. Bewaar meeting- en berichtbron onveranderlijk.
- Ondersteun `REQUESTED`, `OPEN` en `CLOSED`, append-only berichten en het idempotent vastleggen van
  notulen en expliciete doorwerking.
- Laat de Stakeholder in deze tussenrelease de uitkomst zonder AI registreren via dezelfde
  doelgerichte commands die de notulenafhandeling in stap 4 gebruikt.
- Schrijf een overleguitkomst nooit rechtstreeks in tabellen van besluiten of toekomstige
  procesmodules. Bewaar per command zichtbaar `SUCCEEDED`, `FAILED` of aandacht nodig.

### Besluitenregister

- Implementeer `createDecision`, `reviseDecision`, `withdrawDecision` en
  `supersedeDecisions` als doelgerichte, idempotente commands.
- Bewaar tekstwijzigingen als nieuwe halfopen geldigheidsversies; overschrijf nooit historie.
- Maak intrekken zonder opvolger en atomair vervangen door een nieuw besluit inhoudelijk en
  technisch verschillende operaties.
- Implementeer zowel de geldige momentopnamequery als het volledige archief, inclusief herkomst,
  actor, geldigheidsperioden, intrekkingsreden en opvolgers.
- Accepteer alleen grote, blijvende keuzes. Een epic, story, prioriteit, statusovergang, signaal of
  agentles wordt niet als besluit opgeslagen.

### HTTP-API en frontend

Publiceer alleen dunne HTTP-adapters boven de publieke commands en queries. Voeg in de frontend toe:

- productkeuze, product aanmaken en beheer van opdracht, status, testomgeving en dispatching;
- **Signalen** met broninhoud, filters, status, onderzoek en resultaatkoppelingen;
- **Vragen van agents** met rol, context, bronprocessessie, leeftijd en status;
- overleglijst en -detail met agenda, berichten, gekoppelde objecten, vragen, notulen en doorwerking;
- **Beheer → Besluiten** met huidige geldigheid, peildatum, volledig archief, intrekken en vervangen;
- **Instellingen → Automatisering** met de vier processen, gewone dag/tijdregels of interval,
  tijdzone, `nextRunAt`, aan/uit en versieconflicten; toon nergens een cronveld;
- detailpagina's met bron, status, versie en historie.

De frontend schrijft nooit in moduletabellen, berekent `nextRunAt` niet zelf en toont alleen acties
die in de huidige status zijn toegestaan. De actie **Nu starten** mag al zichtbaar zijn, maar geeft
voor nog niet geïmplementeerde procescapabilities expliciet `CapabilityNotAvailable`.

### Testbed en operationele weergave

- Voeg een versieerbare acceptatiedataset toe met minimaal twee producten, verschillende schedules,
  een open/verwerkt signaal, een open/beantwoorde vraag, een open/gesloten overleg en
  actief/ingetrokken/vervangen besluiten.
- Laat reset uitsluitend via de acceptance-only Test Control API lopen en maak die route in
  productie onmogelijk.
- Toon in Operatie de actieve implementaties en scheduleconfiguraties; AI-, proces- en
  dispatcherweergaven blijven herkenbaar niet beschikbaar.

## Uitvoeringsvolgorde

1. Maak een gap-analyse tussen `product-factory-api` en de normatieve contracten en pas de API plus
   contracttests aan.
2. Voeg implementatiemodules, composition-rootselectie en manifestregistratie toe.
3. Voeg migraties, repositories en acceptatieseed toe; bewijs migratie op H2 en PostgreSQL.
4. Implementeer eerst product, opdracht, testconfiguratie, schedules en signalen.
5. Implementeer daarna vragen en overleggen zonder AI.
6. Implementeer het Besluitenregister en de gecontroleerde overlegdoorwerking.
7. Voeg HTTP-adapters, frontendpagina's en autorisatie toe.
8. Voeg Testbedscenario's en operationele projecties toe.
9. Rond alle automatische verificatie af en push de samenhangende release naar `main`.

## Verplichte automatische bewijzen

- domeintests voor iedere statusovergang, versieconflict en idempotente herhaling;
- tijdberekeningstests voor meerdere regels, intervallen, ongeldige tijden, zomer-/wintertijd en
  aan/uit zonder automatische start;
- besluitentests voor peildatum, halfopen geldigheid, revisie, intrekken en atomair vervangen;
- overlegtests voor append-only berichten, automatische agendering en geldige antwoordbron;
- modulegrens- en autorisatietests die directe cross-module writes en ongeautoriseerde mutaties
  uitsluiten;
- REST-contracttests en frontendtests voor de nieuwe pagina's en fouttoestanden;
- Testbedreset en PostgreSQL-migratiesmoke;
- releasecontrole volgens de vaste afronding in [README](README.md).

## Aanbevolen commitgrenzen

1. contractcorrecties en implementatieskeletten;
2. migraties en product-/schedule-/signaaldomein;
3. vragen, overleggen en besluiten;
4. HTTP, frontend, Testbed en operationele weergave;
5. tests, documentatie en releasecorrecties.

Iedere commit bouwt. Tijdelijke providers mogen alleen binnen de branch bestaan en komen niet op
`main` terecht.

## Buiten scope

Agentgeheugen, AI-taakuitvoering, epics, stories, kwaliteitswerk en Software Factory-dispatching
worden niet geïmplementeerd. Er start nog geen proces automatisch. Een notulenagent of Meeting Agent
wordt niet gesimuleerd in de browser; handmatige overlegregistratie gebruikt wel het definitieve
domeincontract.

## Definitie van klaar

Stap 2 is pas klaar wanneer de Stakeholder via de normale UI ieder bovenstaande object kan maken,
lezen en waar toegestaan wijzigen; historie en brongegevens intact blijven; alle automatische
bewijzen groen zijn; en dezelfde release gezond op acceptatie en productie staat. Schedules zijn
dan volledig configureerbaar en uitleesbaar, maar aantoonbaar nog niet automatisch actief.
