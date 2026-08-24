# Product- en overlegmodule — publieke API

Status: publiek contract voor productgegevens, gebruikerssignalen en overleggen.

## Doel en grens

De product-/overlegmodule bewaart de richting en invoer die de ene globale Stakeholder via de
gebruikersinterface aan Product Factory geeft. Zij is eigenaar en enige schrijver van `Product`,
`ProductAssignment`, `TestableProductConfiguration`, `UserSignal` en `Meeting`.

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

ProductDetails getProduct(ProductId productId);
List<ProductDetails> findProducts();
List<ProductDetails> findDispatchableProducts();
ProductAssignmentDetails getProductAssignment(ProductId productId);
TestableProductDetails getTestableProduct(ProductId productId);

UserSignalId submitUserSignal(SubmitUserSignalCommand command);
void markUserSignalInReview(MarkUserSignalInReviewCommand command);
void recordSignalInvestigation(RecordSignalInvestigationCommand command);
void linkSignalToEpic(LinkSignalToEpicCommand command);
UserSignalDetails getUserSignal(UserSignalId userSignalId);
List<UserSignalDetails> findOpenUserSignals(ProductId productId);

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
`dispatchingEnabled`, aanmaakmoment en actuele versie. `findDispatchableProducts()` levert een vaste
read-only lijst van alle `ACTIVE` producten waarvoor `dispatchingEnabled = true`. De scheduler
gebruikt deze query om per product `runDispatchSession(productId)` te starten. De dispatchersessie
zelf valideert nogmaals exact dat ene product.

`ProductAssignment` bevat minimaal doelgroep, productdoel, harde grenzen en de publieke Git-URL.
`TestableProductConfiguration` bevat de acceptatieomgeving en eventueel veilige
productie-informatie, toegestane routes, testaccount- of secretreferenties en data- en
toegangsgrenzen. Iedere testbare omgeving heeft daarnaast een revisionendpoint en een vaste regel
om daaruit de werkelijk gedeployde Git-commit of release te lezen. Story- en bugfixverificatie kan
daardoor aantonen of de `deliveredCommitSha` al op de doelomgeving staat. DTO's bevatten nooit
secretwaarden, alleen referenties die de worker lokaal veilig kan oplossen.

De globale Stakeholder mag ieder product en de bijbehorende opdracht en testconfiguratie beheren.
Een proces leest steeds een exacte versie en legt die bronversie op zijn processessie vast.

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
bug, epic of planningopdracht. `findOpenUserSignals(...)` levert zowel `OPEN` als `IN_REVIEW`; alleen
`PROCESSED` valt buiten de open lijst.

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
- `findDispatchableProducts()` bevat alleen actieve, expliciet voor dispatching ingeschakelde
  producten.
- Overlegagents schrijven nooit rechtstreeks in een andere module.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Frontend](frontend.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Besluitenregister](../gedeelde-modules/besluitenregister.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
