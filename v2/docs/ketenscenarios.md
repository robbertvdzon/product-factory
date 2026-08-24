# Belangrijkste functionele ketenscenario's

Dit document beschrijft de belangrijkste routes door Product Factory in gewone taal. Het doel is
tweeledig:

- vóór de implementatie controleren of de modules en contracten samen een complete route vormen;
- na de implementatie dezelfde scenario's gebruiken als basis voor integratie- en
  acceptatietesten.

De happy flow beschrijft bewust de hele keten. De andere scenario's beginnen pas waar zij van die
route afwijken en herhalen de normale stappen niet. Ieder scenario heeft alleen een beginsituatie,
het scenario zelf en het verwachte eindresultaat.

`DONE` betekent bij een story alleen dat Software Factory haar heeft opgeleverd. De actuele
verificatie wordt apart vastgelegd. Een story heeft nooit de leveringsstatus **mislukt**. Normaal kan
een epic pas naar `VERIFYING` wanneer alle niet-geannuleerde stories en bugfixstories `DONE` zijn,
actueel geslaagd zijn geverifieerd en geen open herstelwerk resteert. Na een door Software Factory
geannuleerde story bestaat één bewuste uitzondering: zodra al het overige werk klaar is, wordt de
feitelijke complete epic opnieuw beoordeeld.

## 1. Happy flow: van product zonder werk naar een afgeronde epic

### Beginsituatie

Er is een actief product met een productopdracht, een publieke Git-URL en een werkende
acceptatieomgeving. Er zijn nog geen epics en geen stories. De backlog is dus leeg.

### Scenario

1. Productontwerp wordt volgens schedule of handmatig gestart.
2. Productontwerp leest onder andere de productopdracht, geldige besluiten, gebruikerssignalen,
   huidige code, documentatie en de werkende applicatie.
3. Productontwerp publiceert één complete epic met status `AVAILABLE`. De epic bevat een duidelijke
   gebruikersverbetering, eenduidige scope, succescriteria en het volledige benodigde UX-ontwerp.
4. Een latere Productplanning-sessie vindt de epic zelf. Productontwerp hoeft planning niet te
   starten en stuurt geen planningsrequest.
5. Productplanning claimt de exacte epicversie. De epic gaat van `AVAILABLE` naar `IN_PLANNING` en
   de inhoud en UX van die versie worden bevroren.
6. Productplanning deelt de hele epic op in zelfstandig uitvoerbare stories. Iedere story krijgt
   status `TODO` en een productbreed `sequenceNumber`.
7. Na de atomaire publicatie van de stories gaat de epic naar `ACTIVE`.
8. De dispatcher reserveert bij Productplanning atomair de eerste uitvoerbare `TODO`-story met het
   laagste `sequenceNumber`, stuurt haar naar Software Factory en laat Productplanning de
   reservering bevestigen en story op `IN_PROGRESS` zetten.
9. Software Factory bouwt en levert de story op en meldt de exacte `deliveredCommitSha`.
10. Een volgende dispatchersessie ziet de oplevering en meldt deze bij Productplanning. De story
    gaat naar `DONE` en Productplanning vraagt bij Kwaliteitsbewaking een storyverificatie tegen die
    commit aan.
11. Een latere Kwaliteitsbewaking-sessie controleert via het revisionendpoint dat de bedoelde
    omgeving de oplevercommit bevat en test daarna de opgeleverde story. De verificatie slaagt en
    Kwaliteitsbewaking meldt het resultaat bij Productplanning.
12. Zolang er nog stories over zijn, herhalen stappen 8 tot en met 11 zich. Per product staat
    normaal maximaal één Software Factory-story tegelijk open. De dispatcher mag de volgende story
    al versturen terwijl een eerdere `DONE`-story nog wordt geverifieerd; alleen de overgang naar
    epicverificatie wacht op alle geslaagde storycontroles.
13. Na de laatste geslaagde actuele storyverificatie controleert Productplanning zonder agent dat
    alle stories en bugfixstories `DONE` en geslaagd geverifieerd zijn en dat geen open bug of
    herstelopdracht resteert.
14. Productplanning laat de epic naar `VERIFYING` gaan en vraagt bij Kwaliteitsbewaking een
    volledige epicverificatie aan.
15. Een latere Kwaliteitsbewaking-sessie controleert de complete gebruikersroute en toont aan dat de
    bedoelde gebruikersverbetering is bereikt.
16. Kwaliteitsbewaking bewaart de verificatie en een nieuw kwaliteitsbeeld en meldt de geslaagde
    epicuitkomst bij Productontwerp.
17. Productontwerp zet de epic op `COMPLETED`.

### Eindresultaat

Alle stories hebben status `DONE` en een geslaagde actuele verificatie. De epic heeft status
`COMPLETED`. De backlog bevat geen open story van deze epic en de verificaties en het
kwaliteitshistoriebeeld laten zien waarom de epic is afgerond.

## 2. Een geprioriteerde backlog maken

Dit is een zelfstandig controleerbaar deel van de happy flow.

### Beginsituatie

Er is minimaal één complete epic met status `AVAILABLE`. Er kunnen daarnaast al `TODO`-stories van
andere actieve epics bestaan.

### Scenario

1. Productplanning claimt een beschikbare epic en maakt alle stories die nodig zijn om de epic af
   te dekken.
2. Iedere story is zelfstandig uitvoerbaar en bevat haar eigen acceptatiecriteria, relevante UX en
   benodigde assets. Software Factory hoeft de epic of Product Factory niet te raadplegen.
3. Productplanning ordent de nieuwe stories samen met alle bestaande `TODO`-stories.
4. Iedere `TODO`- en `IN_PROGRESS`-story heeft samen een uniek productbreed `sequenceNumber`.
   Productplanning herordent alleen `TODO`-stories. Afhankelijkheden worden zo vastgelegd dat een
   afhankelijke story nog niet uitvoerbaar is.
5. De backlogquery geeft alle `TODO`- en `IN_PROGRESS`-stories terug, oplopend op
   `sequenceNumber`.

### Eindresultaat

Er is één eenduidig geordende backlog voor het product. De backlog is geen apart databaseobject,
maar een query op de open stories. De dispatcher kan zonder planningskennis bepalen welke story als
eerste uitvoerbaar is.

## 3. De Stakeholder geeft ander werk voorrang

### Beginsituatie

Er staan `TODO`-stories van een of meer epics in de backlog. Eventueel staat één story al op
`IN_PROGRESS` bij Software Factory.

### Scenario

1. De Stakeholder geeft via de UI aan dat een andere epic voorrang moet krijgen.
2. De UI/REST-ingang roept rechtstreeks `requestEpicReprioritization(...)` op Productplanning aan.
3. Een latere Productplanning-sessie verwerkt het verzoek en ordent de `TODO`-stories opnieuw.
4. Een story die al `IN_PROGRESS` is, wordt niet onderbroken en loopt normaal af.
5. De volgende dispatchersessie kiest volgens de nieuwe volgorde de eerste uitvoerbare
   `TODO`-story.

### Eindresultaat

De backlog weerspiegelt de nieuwe prioriteit. Alleen nog niet verstuurde stories zijn verplaatst;
reeds gestart werk is niet stilzwijgend geannuleerd.

## 4. Een opgeleverde story wordt afgekeurd

### Beginsituatie

Een productstory is door Software Factory opgeleverd, heeft status `DONE` en staat klaar voor
storyverificatie. De epic is `ACTIVE`.

### Scenario

1. Kwaliteitsbewaking toont aan dat afgesproken storygedrag verkeerd is gebouwd.
2. Kwaliteitsbewaking bewaart een afgekeurde `Verification` en een reproduceerbare `Bug` en meldt
   de storyuitkomst bij Productplanning.
3. Kwaliteitsbewaking vraagt met `requestBugfix(...)` gericht herstelwerk aan. Het command maakt
   alleen een `PlanningWorkItem` en start geen planner-agent.
4. Een latere Productplanning-sessie maakt een complete bugfixstory, koppelt haar idempotent met
   `linkBugfixStory(bugId, storyId)` aan de bug en maakt haar daarna uitvoerbaar op de
   geprioriteerde backlog.
5. De dispatcher en Software Factory leveren de bugfixstory via de normale route.
6. Productplanning vraagt na oplevering een bugfixhertest aan.
7. Kwaliteitsbewaking hertest de oorspronkelijke reproduceerstappen. De hertest slaagt, waarna de
   bug op `RESOLVED` komt.
8. Kwaliteitsbewaking zet een nieuwe storyverificatie klaar voor de oorspronkelijke story tegen de
   nieuwe productversie.
9. Ook die verificatie slaagt. Daarna sluit de route weer aan op de happy flow.

### Eindresultaat

De oorspronkelijke story en de bugfixstory zijn beide `DONE` en actueel geslaagd geverifieerd. De
bug is `RESOLVED`. De epic blijft `ACTIVE` totdat ook al het overige werk is geleverd en
geverifieerd; daarna kan de normale epicverificatie beginnen.

## 5. Een opgeleverde bugfix lost het probleem niet op

### Beginsituatie

Een `OPEN` bug heeft een gekoppelde bugfixstory. Die story is `DONE` en Kwaliteitsbewaking voert de
bugfixhertest uit.

### Scenario

1. Kwaliteitsbewaking stelt vast dat de concrete afwijking na de fix nog bestaat.
2. Zij publiceert een afgekeurde verificatie voor de bugfixstory.
3. De bug blijft `OPEN`. De opgeleverde bugfixstory blijft `DONE`; er ontstaat geen aparte
   mislukstatus.
4. Kwaliteitsbewaking voegt het nieuwe bewijs als een nieuwe versie aan dezelfde bug toe en vraagt
   voor die bronversie opnieuw idempotent `requestBugfix(...)` aan.
5. Een latere planningsrun maakt een volgende gewone bugfixstory en koppelt die met
   `linkBugfixStory(bugId, newStoryId)`. Dat mag omdat de eerdere gekoppelde story al `DONE` is.
6. De nieuwe bugfix doorloopt de gewone leverings- en hertestroute. Na een geslaagde hertest wordt
   de bug `RESOLVED` en wordt de oorspronkelijke productstory opnieuw geverifieerd.

### Eindresultaat

Dezelfde bug blijft de actuele afwijking totdat bewijs haar oplost of ongeldig maakt. Alle
gekoppelde bugfixstories en verificaties blijven als historie zichtbaar. Per bug is maximaal één
bugfixstory tegelijk `TODO` of `IN_PROGRESS`.

## 6. Software Factory annuleert een story

### Beginsituatie

Een story van een `ACTIVE` epic staat `IN_PROGRESS` bij Software Factory. Software Factory
verwijdert de story of markeert haar bewust als niet uit te voeren, bijvoorbeeld omdat dezelfde
wijziging handmatig is gedaan.

### Scenario

1. Een dispatchersessie leest de externe status `CANCELLED`.
2. De dispatcher roept idempotent `markStoryAsCancelled(...)` aan. Productplanning zet de story op
   `CANCELLED` en bewaart de externe bron, reden en het tijdstip.
3. Er ontstaat geen storyverificatie of bugfixhertest voor de geannuleerde story, want er is geen
   oplevering om te testen. De dispatcher mag daarna de volgende backlogstory voor dit product
   versturen.
4. Zodra alle overige niet-geannuleerde stories van de epic `DONE` en actueel geslaagd zijn,
   brengt Productplanning de epic naar `VERIFYING` en vraagt zij een complete epicverificatie aan.
5. Kwaliteitsbewaking test de feitelijke gebruikersroute in de applicatie.
6. Is de verbetering ondanks de annulering aanwezig, dan kan de epic `COMPLETED` worden en wordt
   een eventueel niet meer reproduceerbare bug `RESOLVED`.
7. Ontbreekt het gedrag nog, dan gaat de epic via `NEEDS_WORK` terug naar `ACTIVE`. Een bestaande
   bug blijft `OPEN`, of Kwaliteitsbewaking publiceert een nieuwe concrete bug of een dekkingsgat,
   en vraagt daarna gewoon nieuw planwerk aan.

### Eindresultaat

De annulering wordt als `CANCELLED` geaccepteerd en nooit als **mislukt** vertaald. De epic raakt
niet permanent geblokkeerd: actueel testbewijs bepaalt of zij klaar is of opnieuw ontwikkelwerk
nodig heeft.

## 7. Een story of bugfix kan tijdelijk niet worden getest

### Beginsituatie

Een opgeleverde story of bugfixstory staat klaar voor verificatie, maar de testomgeving, toegang of
benodigde informatie is tijdelijk niet beschikbaar.

### Scenario

1. Kwaliteitsbewaking verhoogt `attemptCount`, bewaart de concrete `blockedReason`, zet het
   `QualityWorkItem` op retrybaar `BLOCKED` en berekent `retryAfter` volgens 15 minuten, 1 uur, 4
   uur en daarna maximaal 24 uur back-off.
2. De story blijft `DONE`, maar heeft geen geslaagde actuele verificatie. De epic blijft daarom
   `ACTIVE` en kan niet naar `VERIFYING`.
3. Het workitem staat in de UI-lijst met de meeste pogingen bovenaan. Vanaf vijf pogingen toont de
   UI **Aandacht nodig**; er is geen maximaal aantal domeinretries.
4. Na `retryAfter` maakt een volgende kwaliteitsrun het item automatisch weer `PENDING`. De
   Stakeholder kan ook **Retry now** kiezen: dat maakt het item direct `PENDING` en start de gewone
   kwaliteitsrun als die nog niet draait.
5. Een eventuele gelijktijdige actieve kwaliteitsrun wordt niet verdubbeld; het item wacht dan op
   de volgende vaste batch.

### Eindresultaat

De oplevering wordt niet ten onrechte goedgekeurd of als productfout afgekeurd. De blokkade is
met volledige retryhistorie zichtbaar en de epic wacht op een echte actuele testuitkomst.

## 8. De epic heeft nog ontwikkelwerk nodig door ontbrekende dekking

### Beginsituatie

Alle stories en bugfixstories van een epic zijn `DONE` en actueel geslaagd geverifieerd. De epic
staat op `VERIFYING` en de volledige epiccontrole wordt uitgevoerd.

### Scenario

1. Kwaliteitsbewaking stelt vast dat gedrag of UX binnen de bevroren epicscope ontbreekt en dat er
   nooit een story voor bestond.
2. Kwaliteitsbewaking bewaart een epicverificatie met uitkomst `NEEDS_WORK` en beschrijft het
   dekkingsgat met bewijs.
3. Kwaliteitsbewaking meldt de uitkomst bij Productontwerp. Productontwerp zet de epic terug op
   `ACTIVE`; de bevroren epicinhoud verandert niet.
4. Kwaliteitsbewaking vraagt met `requestEpicGapPlanning(...)` gericht planwerk aan.
5. Een latere Productplanning-sessie maakt aanvullende productstories binnen dezelfde epic en zet
   ze op de backlog.
6. De aanvullende stories doorlopen dispatcher, Software Factory en storyverificatie via de gewone
   route.
7. Wanneer opnieuw al het werk actueel geslaagd is, wordt een nieuwe volledige epicverificatie
   klaargezet.

### Eindresultaat

De oorspronkelijke epic blijft het bevroren contract. Het ontbrekende werk is als nieuwe stories
zichtbaar uitgevoerd en getest. Alleen na een nieuwe geslaagde epicverificatie wordt de epic
`COMPLETED`.

## 9. Tijdens de epiccontrole wordt een bug gevonden

### Beginsituatie

De epic staat op `VERIFYING`, maar tijdens de complete gebruikersroute blijkt gedrag uit een reeds
uitgevoerde story niet goed te werken.

### Scenario

1. Kwaliteitsbewaking publiceert een reproduceerbare bug en een epicverificatie met uitkomst
   `NEEDS_WORK`.
2. Productontwerp zet de epic terug op `ACTIVE`.
3. Kwaliteitsbewaking vraagt een bugfix aan bij Productplanning.
4. De bugfix, hertest en nieuwe controle van het oorspronkelijke storygedrag volgen scenario 4.
5. Daarna kan opnieuw een complete epicverificatie worden uitgevoerd.

### Eindresultaat

De bug is niet als ontbrekende epicscope behandeld. De bug is aantoonbaar opgelost en de epic
wordt pas na een nieuwe geslaagde totaalcontrole `COMPLETED`.

## 10. De epiccontrole is tijdelijk geblokkeerd

### Beginsituatie

De epic staat op `VERIFYING` en Kwaliteitsbewaking kan nog geen verantwoord eindoordeel geven.

### Scenario

1. Kwaliteitsbewaking legt uit waarom de controle nog niet verantwoord kan worden afgerond en zet
   het `VERIFY_EPIC`-workitem retrybaar op `BLOCKED` met `attemptCount` en `retryAfter`.
2. Productontwerp laat de epic op `VERIFYING` staan.
3. Er ontstaat geen bugfix- of planningswerk zolang niet is aangetoond dat ontwikkelwerk nodig is.
4. De automatische back-off of **Retry now** maakt hetzelfde workitem later opnieuw `PENDING`.
5. Een latere kwaliteitsrun voert de epiccontrole opnieuw uit nadat de blokkade is verdwenen.

### Eindresultaat

De epic wordt niet te vroeg afgesloten en gaat ook niet onnodig terug naar planning. De verificatie
maakt zichtbaar welk bewijs of welke voorwaarde nog ontbreekt.

## 11. Alles werkt zoals ontworpen, maar de epic bereikt het gebruikersdoel niet

### Beginsituatie

De epic staat op `VERIFYING`. Alle afgesproken functionaliteit werkt en een vooraf toetsbaar
succescriterium heeft een beschikbare meetbron, maar de volledige controle levert positief bewijs
dat de bedoelde gebruikersverbetering niet is bereikt.

### Scenario

1. Kwaliteitsbewaking publiceert een verificatie met uitkomst `NOT_SUCCESSFUL` en legt het bewijs
   vast.
2. Kwaliteitsbewaking meldt de uitkomst bij Productontwerp.
3. Productontwerp zet de epic op `NOT_SUCCESSFUL`.
4. Productplanning krijgt geen generiek herstelverzoek.
5. Een latere Productontwerp-sessie kan de uitkomst als nieuwe kennis gebruiken en eventueel een
   nieuwe vervolgepic maken.

### Eindresultaat

De epic blijft als historisch eindresultaat `NOT_SUCCESSFUL` en wordt niet heropend. Eventueel
vervolgwerk begint als een nieuwe epic met een nieuwe, expliciete productrichting.

## 12. De Stakeholder stopt een gekozen epic

### Beginsituatie

Een epic is al gekozen en heeft status `IN_PLANNING`, `ACTIVE` of `VERIFYING`. Er kunnen stories met
status `TODO`, `IN_PROGRESS` of `DONE` bestaan.

### Scenario

1. De Stakeholder stopt de epic via de UI.
2. Productontwerp legt een herstelbare annuleringsoperatie vast en vraagt Productplanning eerst om
   de epic voor nieuwe publicatie en dispatch te blokkeren.
3. Productplanning bewaart ook zonder bestaande stories een duurzame annuleringsmarker en zet alle
   niet-gereserveerde `TODO`-stories op `CANCELLED`.
4. Na bevestiging zet Productontwerp de epic op `CANCELLED`.
5. Een `IN_PROGRESS` story geldt als extern gestart en loopt normaal af. Een alleen lokaal
   gereserveerde story wacht op de eerstvolgende externe aanwezigheidscontrole; bestaat zij niet bij
   Software Factory, dan wordt ook deze story `CANCELLED`. Er wordt geen nieuwe complete
   epicverificatie meer gestart.
6. Een wachtende Planner kan door de marker geen stories meer publiceren. De dispatcher kan geen
   nieuwe reservering meer krijgen.

### Eindresultaat

De epic en het niet gestarte werk zijn zichtbaar geannuleerd. Alleen aantoonbaar reeds bestaand
extern werk loopt door; een lokale reservering tijdens een langdurige storing is niet genoeg. Reeds
gestart extern werk verdwijnt niet stilzwijgend en de epic kan niet per ongeluk alsnog `COMPLETED`
worden.

## 13. Software Factory is tijdelijk niet bereikbaar

### Beginsituatie

Er staat een uitvoerbare `TODO`-story bovenaan de backlog, maar de dispatcher krijgt een timeout,
netwerkfout of andere tijdelijke transportfout bij Software Factory.

### Scenario

1. De dispatcher bewaart de mislukte poging als `DeliveryAttempt`.
2. De story blijft `TODO` met dezelfde dispatchreservering wanneer niet bekend is dat extern werk
   bestaat.
3. Voor iedere retry controleert de dispatcher met dezelfde idempotentiesleutel of Software Factory
   de story misschien toch heeft aangemaakt.
4. Bestaat het externe werk al, dan herstelt de dispatcher de koppeling en zet Productplanning de
   story op `IN_PROGRESS`.
5. Bestaat het niet, dan laat de dispatcher Productplanning de reservering opnieuw tegen de actuele
   epicanulering valideren.
6. Alleen bij een nog geldige reservering probeert hij later gecontroleerd opnieuw.

### Eindresultaat

Er ontstaat maximaal één externe Software Factory-story. Er wordt geen bug of
planningsopdracht gemaakt; de technische fout blijft de verantwoordelijkheid van de dispatcher.

## 14. Een planningstaak faalt definitief binnen AI-uitvoering

### Beginsituatie

Productplanning heeft een epic geclaimd. De epic staat `IN_PLANNING`, maar de plan-AiTask eindigt na
haar technische `maxAttempts` als `FAILED` voordat stories zijn gepubliceerd.

### Scenario

1. Productplanning bewaart de processessie als `BLOCKED`, inclusief fout, `retryAfter`, epic-ID en
   exacte bevroren epicversie.
2. De epic blijft `IN_PLANNING` en wordt niet teruggezet naar `AVAILABLE`.
3. Een volgende planningsrun voor hetzelfde product hervat vóór nieuw werk deze sessie.
4. Na de back-off vraagt zij een nieuwe AI-taak aan voor dezelfde inputmomentopname en epicclaim.
5. Na een geldig resultaat publiceert Productplanning de storyset atomair en zet Productontwerp de
   epic op `ACTIVE`.

### Eindresultaat

De technische fout heeft geen verweesde epic of dubbele planning veroorzaakt. Dezelfde epicclaim
is zichtbaar hersteld en pas na volledige storypublicatie actief geworden.

## 15. De oplevercommit staat nog niet op acceptatie

### Beginsituatie

Software Factory heeft een story als `DONE` gemeld met `deliveredCommitSha`, maar het revisionendpoint
van de acceptatieomgeving meldt nog een oudere commit.

### Scenario

1. Kwaliteitsbewaking vergelijkt vóór inhoudelijk testen de vereiste en gedeployde revision.
2. Het `QualityWorkItem` wordt retrybaar `BLOCKED` met reden `DEPLOYMENT_PENDING`; er ontstaat geen
   afgekeurde verificatie en geen bug.
3. Na de gewone back-off of **Retry now** start een nieuwe kwaliteitssessie voor hetzelfde product.
4. Het revisionendpoint meldt nu een commit die de oplevercommit bevat.
5. De Tester voert de normale storyverificatie uit en publiceert de werkelijke uitkomst.

### Eindresultaat

Een trage deployment wordt nooit als productfout behandeld. De verificatie verwijst naar zowel de
vereiste oplevercommit als de werkelijk geteste deploymentrevision.

## 16. Twee producten draaien gelijktijdig

### Beginsituatie

Product A heeft een langlopende AI-taak in Productplanning. Product B heeft nieuw planwerk of
kwaliteitswerk klaarstaan.

### Scenario

1. De processessie van Product A staat `WAITING_FOR_AI`.
2. De scheduler of Stakeholder start dezelfde procesmodule met het product-ID van Product B.
3. Product B krijgt een eigen processessie en kan normaal werk claimen en AI-taken aanvragen.
4. Een tweede start voor Product A wordt niet als nieuwe sessie aangemaakt, maar hervat of meldt de
   bestaande sessie volgens haar status.

### Eindresultaat

Per combinatie van module en product bestaat maximaal één onafgeronde logische sessie. Een traag
product blokkeert geen ander product.

## 17. Een dependency van een open story wordt geannuleerd

### Beginsituatie

Een `TODO`-story is afhankelijk van een andere story. Software Factory of epicannulering maakt die
bronstory `CANCELLED` voordat de afhankelijke story is verstuurd.

### Scenario

1. Productplanning behandelt `CANCELLED` niet als een vervulde dependency.
2. De afhankelijke `TODO`-story is niet dispatchbaar.
3. Productplanning maakt idempotent een `REPLAN_CANCELLED_DEPENDENCY`-workitem.
4. Een latere planningsrun voor het product vervangt of annuleert de afhankelijke story op basis van
   de actuele epic en producttoestand.
5. Alleen een opnieuw zelfstandig uitvoerbare `TODO`-story kan daarna worden gereserveerd.

### Eindresultaat

De dispatcher bouwt niet blind verder op geannuleerd werk. De backlog blijft door Productplanning
uitlegbaar en herstelbaar zonder dat de Stakeholder een losse story hoeft te bewerken.

## 18. De Stakeholder stelt per proces een eigen automatisch ritme in

### Beginsituatie

HKH is actief. De Stakeholder wil Productplanning iedere dag om 07:00 en 20:00 laten draaien,
Productontwerp op maandag om 09:00 en op vrijdag om 21:00 en de dispatcher ieder uur.

### Scenario

1. De Stakeholder opent **Beheer → Instellingen → Automatisering** voor HKH.
2. Zij maakt voor Productplanning één regel met alle dagen en de tijden 07:00 en 20:00 in
   `Europe/Amsterdam`.
3. Zij maakt voor Productontwerp twee regels: maandag om 09:00 en vrijdag om 21:00.
4. Zij stelt de dispatcher in op een interval van 60 minuten en slaat ieder schema op.
5. De backend bewaart per product en proces één nieuwe configuratieversie, ontdubbelt gelijke
   combinaties en berekent het vroegste toekomstige tijdstip als `nextRunAt`.
6. Op ieder vervallen tijdstip claimt de scheduler de geplande start hooguit eenmaal en roept hij de
   gewone publieke runfunctie voor HKH aan.
7. De Stakeholder schakelt later Productontwerp tijdelijk uit en kan het desondanks met
   **Nu starten** handmatig uitvoeren.
8. Na een periode van downtime haalt de scheduler hooguit één gemiste run per schema in en plant
   daarna meteen het eerstvolgende toekomstige tijdstip.

### Eindresultaat

Ieder product en proces kan een eigen begrijpelijk automatisch ritme hebben, inclusief meerdere
tijden per dag en meerdere regels met andere dagen en tijden. Er zijn geen cronexpressies in de UI,
geen dubbele geplande starts en uitschakelen blokkeert de handmatige bediening niet.

## Brondocumenten

- [Overzicht](overzicht.md)
- [Productontwerp-API](processen/productontwerp/api.md)
- [Productplanning-API](processen/productplanning/api.md)
- [Software Factory-dispatcher](processen/software-factory-dispatcher.md)
- [Kwaliteitsbewaking-API](processen/kwaliteitsbewaking/api.md)
- [Processen en entiteiten](processen/processen-en-entiteiten.md)
- [Product- en overlegmodule-API](stakeholder/product-en-overleg-api.md)
- [Integratie- en acceptatietesten](platform/integratie-en-acceptatietesten.md)
