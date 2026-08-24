# Product- en overlegmodule — publieke API

Status: publiek contract voor productgegevens, gebruikerssignalen en overleggen.

## Doel en grens

De product-/overlegmodule bewaart de richting en invoer die de ene globale Stakeholder via de
gebruikersinterface aan Product Factory geeft. Zij is eigenaar en enige schrijver van `Product`,
`ProductAssignment`, `TestableProductConfiguration`, `ProcessScheduleConfiguration`, `UserSignal`
en `Meeting`.

Andere modules gebruiken uitsluitend deze API. Zij krijgen geen repositorytoegang en wijzigen deze
entiteiten alleen via betekenisvolle commands. De module maakt geen epics, stories, bugs,
verificaties of besluiten. Grote blijvende besluiten lopen via het Besluitenregister.

Er is precies één Stakeholder voor de volledige Product Factory en alle producten. De Stakeholder
is een externe actor en geen duurzame productentiteit. Het technische account hoort bij
authenticatie en autorisatie, niet bij dit domeinmodel.

## Publieke interface

```java
ProductId createProduct(CreateProductCommand command);
void updateProductAssignment(UpdateProductAssignmentCommand command);
void configureTestableProduct(ConfigureTestableProductCommand command);
void setProductDispatching(SetProductDispatchingCommand command);
void updateProcessSchedule(UpdateProcessScheduleCommand command);

ProductDetails getProduct(ProductId productId);
List<ProductDetails> findProducts();
ProductAssignmentDetails getProductAssignment(ProductId productId);
TestableProductDetails getTestableProduct(ProductId productId);
ProcessScheduleDetails getProcessSchedule(ProductId productId, ScheduledProcess process);
List<ProcessScheduleDetails> getProcessSchedules(ProductId productId);

UserSignalId submitUserSignal(SubmitUserSignalCommand command);
void markUserSignalInReview(MarkUserSignalInReviewCommand command);
void recordSignalInvestigation(RecordSignalInvestigationCommand command);
void linkSignalToEpic(LinkSignalToEpicCommand command);
UserSignalDetails getUserSignal(UserSignalId userSignalId);
List<UserSignalDetails> findUserSignals(UserSignalFilter filter);

MeetingId startMeeting(StartMeetingCommand command);
void recordMeetingMessage(RecordMeetingMessageCommand command);
void closeMeeting(CloseMeetingCommand command);
MeetingDetails getMeeting(MeetingId meetingId);
List<MeetingDetails> findMeetings(ProductId productId, MeetingStatus status);
```

Alle mutaties controleren actor, product, verwachte versie en idempotentiesleutel. Commands bieden
geen algemene setter en kunnen de state machine niet omzeilen.

## Product en productopdracht

`Product` bevat minimaal een stabiel product-ID, naam, status `ACTIVE` of `INACTIVE`,
`dispatchingEnabled`, aanmaakmoment en actuele versie. `findProducts()` maakt dit per product
uitleesbaar. De dispatcher-scheduler kiest geen producten op basis van een losse productquery, maar
claimt vervallen `SOFTWARE_FACTORY_DISPATCHER`-schema's. De dispatchersessie valideert daarna
nogmaals dat exact dat ene product actief is en dispatching aanstaat.

`ProductAssignment` bevat minimaal doelgroep, productdoel, harde grenzen en de publieke Git-URL.
`TestableProductConfiguration` bevat de acceptatieomgeving en eventueel veilige
productie-informatie, toegestane routes, testaccount- of secretreferenties en data- en
toegangsgrenzen. Iedere testbare omgeving heeft daarnaast een revisionendpoint en een vaste regel
om daaruit de werkelijk gedeployde Git-commit of release te lezen. Story- en bugfixverificatie kan
daardoor aantonen of de `deliveredCommitSha` al op de doelomgeving staat. DTO's bevatten nooit
secretwaarden, alleen referenties die de worker lokaal veilig kan oplossen.

De globale Stakeholder mag ieder product en de bijbehorende opdracht en testconfiguratie beheren.
Een proces leest steeds een exacte versie en legt die bronversie op zijn processessie vast.

## Procesconfiguratie en schedules

De Stakeholder beheert per product een afzonderlijk schedule voor:

- `PRODUCT_DESIGN` — roept `runProcessSession(productId)` op Productontwerp aan;
- `PRODUCT_PLANNING` — roept `runProcessSession(productId)` op Productplanning aan;
- `QUALITY_ASSURANCE` — roept `runProcessSession(productId)` op Kwaliteitsbewaking aan;
- `SOFTWARE_FACTORY_DISPATCHER` — roept `runDispatchSession(productId)` aan.

`ProcessScheduleConfiguration` bevat minimaal product-ID, proces, `enabled`, IANA-tijdzone,
schedulepatroon, berekende `nextRunAt`, wijzigingsmoment en versie. Het patroon ondersteunt:

- één of meer vaste tijden op gekozen weekdagen, bijvoorbeeld dagelijks om 08:00 en 20:00 of
  iedere maandag om 07:00;
- een vast interval in hele minuten, bijvoorbeeld ieder uur voor de dispatcher.

De normale UI toont menselijke dagen en tijden en geen cronexpressie. De tijdzone is expliciet en
standaard `Europe/Amsterdam`, zodat zomer- en wintertijd volgens die zone worden berekend.
`updateProcessSchedule(...)` wijzigt alleen toekomstige starts, annuleert geen lopende sessie en
verandert niets aan handmatige bediening.

`createProduct(...)` maakt voor de vier processen een uitgeschakelde configuratie zonder
`nextRunAt`. De eerste keer inschakelen vereist een geldig patroon. Uitschakelen bewaart het patroon
voor later maar maakt `nextRunAt` leeg; opnieuw inschakelen berekent vanaf dat moment uitsluitend
een toekomstig tijdstip. Zo start een nieuw product nooit onverwacht automatisch.

De technische scheduler pollt vervallen `nextRunAt`s, claimt iedere combinatie van schedule-ID en
gepland tijdstip hooguit eenmaal en roept alleen de gewone publieke runfunctie aan. Na downtime
wordt een gemist schema hooguit eenmaal ingehaald; eerdere gemiste tijdstippen worden niet allemaal
nagespeeld. Daarna wordt direct het eerstvolgende toekomstige tijdstip berekend. Voor een `INACTIVE`
product wordt geen proces gestart. De dispatcher controleert daarnaast zoals altijd
`dispatchingEnabled`.

De scheduleradapter en het atomische zoeken en claimen van vervallen schema's horen intern bij de
productimplementatie. Andere modules krijgen daarvoor geen repositorytoegang en ook geen algemene
publieke setter. Na een geldige claim kent de adapter alleen product-ID, proces en gepland tijdstip
en roept hij de publieke run-API van dat proces aan.

Een botsing met een al uitvoerende call volgt de bestaande regel: de scheduler registreert de
geplande start als overgeslagen en forceert geen tweede uitvoering. Een niet-actief wachtende
logische sessie, bijvoorbeeld `WAITING_FOR_AI`, wordt door de geplande call juist veilig hervat.
Een uitgeschakeld schedule verhindert alleen automatische starts; **Nu starten** blijft beschikbaar.

## UserSignal

De oorspronkelijke melding van een `UserSignal` is na aanmaak onveranderlijk. Het signaal bevat
minimaal product-ID, categorie, bron, tekst, aanmaakmoment, status en resultaatkoppelingen. De
publieke statussen zijn:

- `OPEN` — ingediend en nog niet in behandeling;
- `IN_REVIEW` — door Productontwerp of Kwaliteitsbewaking opgepakt;
- `PROCESSED` — onderzocht of verwerkt, met een zichtbare uitkomst en zo nodig koppelingen.

`recordSignalInvestigation(...)` registreert de exacte `Verification` en de leesbare uitkomst.
`linkSignalToEpic(...)` koppelt een exact epic-ID en epicversie. Geen van beide wijzigt de
oorspronkelijke melding. Een signaal is richting of bewijs, maar nooit rechtstreeks een besluit,
bug, epic of planningopdracht. `findUserSignals(...)` ondersteunt minimaal filteren op product-ID,
status, categorie, urgentie, bron en periode. Zonder statusfilter levert de query ook verwerkte
signalen, zodat de frontend één controleerbare historie kan tonen. De normale procesinput gebruikt
een filter op `OPEN` en `IN_REVIEW` en behandelt `PROCESSED` dus niet opnieuw.

## Meeting

Een `Meeting` bevat product-ID, aanleiding, agenda, gekoppelde objecten, deelnemers, berichten,
status, notulen en de expliciete doorwerking. De statussen zijn `REQUESTED`, `OPEN` en `CLOSED`.

Een overleg kan vanaf stap 4 complete taken bij AI-uitvoering aanvragen voor de gespreks- en
notulenagent. Dat gebeurt buiten de drie intelligente processessies. De overlegafhandeling leest
voor iedere taak uitsluitend het eigen rolgeheugen en de globale `AiJobConfiguration` en levert een
complete opaque taak aan AI-uitvoering. AI-uitvoering kent de overlegrollen niet.

`closeMeeting(...)` legt notulen en iedere afzonderlijke doorwerking idempotent vast. De module
voert die doorwerking alleen uit via het publieke command van de entiteiteigenaar. Een transcript
wijzigt nooit stilzwijgend productdata.

## Invarianten

- Er bestaat één globale Stakeholder voor alle producten.
- Iedere entiteit heeft binnen deze module één repository en één schrijver.
- Broninhoud van een `UserSignal` en vastgelegde meetingberichten worden niet overschreven.
- Algemene AI-instellingen horen bij AI-uitvoering en niet bij deze module.
- De frontend gebruikt exact dezelfde commands en queries als andere aanroepers.
- Per product en `ScheduledProcess` bestaat precies één geversioneerde scheduleconfiguratie.
- Een schedule start uitsluitend de bestaande publieke runfunctie en bevat geen product- of
  agentlogica.
- Overlegagents schrijven nooit rechtstreeks in een andere module.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Frontend](frontend.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Besluitenregister](../gedeelde-modules/besluitenregister.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
