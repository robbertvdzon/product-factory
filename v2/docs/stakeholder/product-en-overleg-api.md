# Product- en overlegmodule — publieke API

Status: publiek contract voor productgegevens, gebruikerssignalen en overleggen.

## Doel en grens

De product-/overlegmodule bewaart de richting en invoer die de ene globale Stakeholder via de
gebruikersinterface aan Product Factory geeft. Zij is eigenaar en enige schrijver van `Product`,
`ProductAssignment`, `TestableProductConfiguration`, `ProcessScheduleConfiguration`, `UserSignal`,
`StakeholderQuestion` en `Meeting`.

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

StakeholderQuestionId askStakeholder(AskStakeholderCommand command);
void recordStakeholderAnswer(RecordStakeholderAnswerCommand command);
void withdrawStakeholderQuestion(WithdrawStakeholderQuestionCommand command);
StakeholderQuestionDetails getStakeholderQuestion(StakeholderQuestionId questionId);
List<StakeholderQuestionDetails> findStakeholderQuestions(StakeholderQuestionFilter filter);

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
schedulepatroon, berekende `nextRunAt`, wijzigingsmoment en versie. Het patroon is precies één van:

- een niet-lege lijst `WeeklyScheduleRule`s. Iedere regel bevat één of meer weekdagen en één of meer
  geldige lokale tijden. Eén regel kan bijvoorbeeld iedere dag om 07:00 en 20:00 betekenen; twee
  andere regels kunnen maandag om 09:00 en vrijdag om 21:00 betekenen;
- één vast interval in hele minuten, bijvoorbeeld ieder uur voor de dispatcher.

Dag/tijdregels en een interval worden niet binnen dezelfde configuratie gemengd. Gelijke
dag/tijdcombinaties worden bij validatie ontdubbeld. Een lokale tijd moet bestaan en tussen `00:00`
en `23:59` liggen. `nextRunAt` is steeds het vroegste toekomstige tijdstip uit alle regels, berekend
door de backend.

De normale UI toont menselijke regels met dagen en tijden en geen cronexpressie. De tijdzone is
expliciet en standaard `Europe/Amsterdam`, zodat zomer- en wintertijd volgens die zone worden
berekend.
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

Een `StakeholderQuestion` bevat een tijdelijke vraag van één vertrouwd vastgelegde agentrol,
context, bronprocessessie, gekoppelde objecten en status `OPEN`, `ANSWERED` of `WITHDRAWN`. Bij een
antwoord bewaart zij antwoordtekst, meeting-ID, berichtreferentie en tijdstip. De vraag is geen
permanent geheugenitem. `findStakeholderQuestions(...)` kan minimaal filteren op product, vragende
rol en status. Open vragen worden automatisch onderdeel van de agenda en context van een bestaand
of volgend overleg voor dat product.

`askStakeholder(...)` wordt alleen vanuit vertrouwde procescode aangeroepen. Die code vult product,
vragende rol, processessie en idempotentiesleutel in; vrije agentoutput kan geen rol nabootsen. Het
command start geen overleg, processessie of AI-taak. Een agent kan een nog open eigen vraag via
dezelfde vertrouwde context intrekken wanneer zij niet meer relevant is.

`recordStakeholderAnswer(...)` is alleen geldig vanuit de gecontroleerde notulenafhandeling. Het
command vereist een nog open vraag, een meeting van hetzelfde product en een exact bericht van de
Stakeholder als antwoordbron. Een technische retry met dezelfde idempotentiesleutel verandert het
antwoord niet en maakt geen tweede beantwoording.

Een overleg kan vanaf stap 4 complete taken bij AI-uitvoering aanvragen voor de gespreks- en
notulenagent. Dat gebeurt buiten de drie intelligente processessies. De overlegafhandeling vraagt
bij Agentgeheugen met een vertrouwde `MeetingExecutionContext` de actieve rolcatalogus en één
snapshot van alle actuele rolgeheugens van precies dit product op. Zij combineert dit met open
Stakeholdervragen, relevante publieke productgegevens en de globale `AiJobConfiguration` en levert
een complete opaque taak aan AI-uitvoering. AI-uitvoering kent de overlegrollen en deze bijzondere
leesbevoegdheid niet.

Een meetingbericht van de Stakeholder kan een optionele `targetAgentRole` hebben. De Meeting Agent
gebruikt de rolbeschrijving en het actuele geheugen om expliciet vanuit die rol te antwoorden. Hij
is één super-agent en start geen echte Productontwerp-, Planner- of Testeragent. Zijn antwoord
registreert `senderRole = MEETING_AGENT` en optioneel `representedAgentRole`, zodat de auditbron
eerlijk blijft.

`closeMeeting(...)` legt notulen en iedere afzonderlijke doorwerking idempotent vast. De module
markeert beantwoorde `StakeholderQuestion`s met de exacte meeting- en berichtbron en voert andere
doorwerking alleen uit via het publieke command van de entiteiteigenaar. Voor blijvende lessen mag
de notulenagent via één gevalideerde batch geheugen van meerdere actieve rollen toevoegen,
vervangen of intrekken. Deze wijzigingen hebben geen extra menselijke goedkeuringsstap, maar zijn
append-only, aan het meeting-ID gekoppeld en achteraf door de Stakeholder corrigeerbaar. Een
transcript wijzigt nooit stilzwijgend overige productdata.

## Invarianten

- Er bestaat één globale Stakeholder voor alle producten.
- Iedere entiteit heeft binnen deze module één repository en één schrijver.
- Broninhoud van een `UserSignal` en vastgelegde meetingberichten worden niet overschreven.
- Een tijdelijke Stakeholdervraag staat niet in permanent rolgeheugen en heeft precies één
  vertrouwd vastgelegde vragende rol.
- Algemene AI-instellingen horen bij AI-uitvoering en niet bij deze module.
- De frontend gebruikt exact dezelfde commands en queries als andere aanroepers.
- Per product en `ScheduledProcess` bestaat precies één geversioneerde scheduleconfiguratie.
- Een schedule start uitsluitend de bestaande publieke runfunctie en bevat geen product- of
  agentlogica.
- Overlegagents schrijven nooit rechtstreeks in een andere module; de notulenagent gebruikt voor
  productbrede rolgeheugenwijzigingen uitsluitend de speciale gevalideerde Agentgeheugen-batch.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Frontend](frontend.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Besluitenregister](../gedeelde-modules/besluitenregister.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
