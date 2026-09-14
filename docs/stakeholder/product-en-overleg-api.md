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

Naast de bestaande product- en overlegcommands biedt Product Advisor geauthenticeerde REST-routes
voor productgesprekken, append-only berichten, requestdetails en versiegebonden goedkeuring. Alle
productroutes gebruiken één centrale autorisatiecontrole: een actieve product owner ziet alleen
toegewezen producten; een factory owner ziet alles. Gebruikers- en lidmaatschapsbeheer staat alleen
open voor de factory owner en bewaart een auditbare grant/revokehistorie.

De persoonlijke queries `/api/my/actions` en `/api/my/notifications` leiden de gebruiker altijd uit
de backend-sessie af. Een client kan geen ander user-ID meegeven. Iedere publieke mutatie gebruikt
een verwachte versie en idempotentiesleutel; intrekken van toegang vereist daarnaast reden en
expliciete bevestiging.

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
uitleesbaar. Automatische verwerking controleert ieder actief, niet-gepauzeerd product iedere tien seconden.
Het bestaande veld `dispatchingEnabled` is voortaan de enige productbrede aan/uit-vlag; er is geen
losse dispatcherinstelling. De dispatcher valideert de vlag opnieuw voordat hij werk verstuurt.

`ProductAssignment` bevat minimaal doelgroep, productdoel en de publieke Git-URL.
`TestableProductConfiguration` bevat de acceptatieomgeving en eventueel veilige
productie-informatie, toegestane routes en data- en toegangsgrenzen. Iedere testbare omgeving heeft
daarnaast een revisionendpoint en een vaste regel
om daaruit de werkelijk gedeployde Git-commit of release te lezen. Story- en bugfixverificatie kan
daardoor aantonen of de `deliveredCommitSha` al op de doelomgeving staat.

De Kotlin-DTO `TestEnvironmentConfiguration` bevat geen `credentialReferences` meer.
Projectcredentialnamen worden dynamisch via Agent Runtime ontdekt en door de
AI-uitvoeringscapability afzonderlijk per product en agentrol beheerd. De productmodule bewaart geen
credentialnamen of -waarden in de testomgevingconfiguratie.

De globale Stakeholder mag ieder product en de bijbehorende opdracht en testconfiguratie beheren.
Een proces leest steeds een exacte versie en legt die bronversie op zijn processessie vast.

`DELETE /api/products/{productId}` is alleen beschikbaar voor de factory owner en vereist dat de
product-ID exact in de requestbody wordt bevestigd. De transactie verwijdert alle productgebonden
Product Factory-data. Eventueel eerder verstuurd werk in Software Factory valt buiten die transactie.

## Automatische verwerking

Ieder actief product wordt standaard iedere tien seconden gecontroleerd voor Productontwerp,
Productplanning, Kwaliteitsbewaking en de Software Factory-dispatcher. Een factory owner kan met
`PATCH /api/products/{productId}/automation` en `{paused, expectedVersion, idempotencyKey}` de
verwerking voor het hele product pauzeren of hervatten. Lopende AI-taken en extern werk mogen
afronden. Ook de directe trigger na epicgoedkeuring wacht tijdens een projectpauze.

De controle start alleen de bestaande publieke procesfuncties als er nieuw, uitvoerbaar werk is.
Bij een menselijke PO vereist ontwerp expliciete input; een AI-PO kan ook reageren op gewijzigde
productdoelen en afgeronde stories. Goedkeuringen, afhankelijkheden en maximaal één externe story
per product blijven gelden. De dispatcher gebruikt geen AI.

Lege controles en ongewijzigd extern werk maken geen processessie of schedulerrun. Eén vaste
controlerij en maximaal vier processtatusrijen per product bewaren het controlemoment, de laatst
verwerkte input en eventuele fout. Een databaselease voorkomt gelijktijdige automatische controles
voor hetzelfde product. Na herstart verloopt een achtergebleven lease binnen vijf minuten.
Technische fouten krijgen oplopende wachttijd (20 seconden tot tien minuten); een inhoudelijke
blokkade vereist nieuwe input. Identieke fouten worden niet iedere controle gelogd.

De UI toont één projectpauze, de laatste controle en betekenisvolle voortgang of fout. Oude
scheduleconfiguraties en historie blijven leesbaar voor compatibiliteit; de oude scheduler is niet
meer actief. `PUT /api/products/{productId}/schedules/{process}` retourneert HTTP 410. Er zijn geen
instelbare intervallen of schakelaars per proces meer. `PF_SCHEDULES_ENABLED` blijft uitsluitend
een technische omgevingsgrens: productie aan, acceptatie met gecontroleerde fixtures uit.

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
- Per actief product geldt één pauzestand en een vaste controle iedere tien seconden.
- Automatische verwerking start uitsluitend bestaande publieke procesfuncties; goedkeuringsregels blijven van kracht.
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
