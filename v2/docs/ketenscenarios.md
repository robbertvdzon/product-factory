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
verificatie wordt apart vastgelegd. Een epic kan pas naar `VERIFYING` wanneer alle bijbehorende
stories en bugfixstories `DONE` zijn, actueel geslaagd zijn geverifieerd en geen open herstelwerk
resteert.

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
8. De dispatcher kiest de eerste uitvoerbare `TODO`-story met het laagste `sequenceNumber`, stuurt
   haar naar Software Factory en laat Productplanning de story op `IN_PROGRESS` zetten.
9. Software Factory bouwt en levert de story op.
10. Een volgende dispatchersessie ziet de oplevering en meldt deze bij Productplanning. De story
    gaat naar `DONE` en Productplanning vraagt bij Kwaliteitsbewaking een storyverificatie aan.
11. Een latere Kwaliteitsbewaking-sessie test de opgeleverde story op de bedoelde omgeving. De
    verificatie slaagt en Kwaliteitsbewaking meldt het resultaat bij Productplanning.
12. Zolang er nog stories over zijn, herhalen stappen 8 tot en met 11 zich. Per product staat
    normaal maximaal één Software Factory-story tegelijk open.
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
4. Iedere open story krijgt een uniek productbreed `sequenceNumber`. Afhankelijkheden worden zo
   vastgelegd dat een afhankelijke story nog niet uitvoerbaar is.
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
2. De product-/overlegmodule vraagt met `requestEpicReprioritization(...)` een herprioritering aan.
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
4. Een latere Productplanning-sessie maakt een complete bugfixstory, koppelt haar aan de bug en zet
   haar op de geprioriteerde backlog.
5. De dispatcher en Software Factory leveren de bugfixstory via de normale route.
6. Productplanning vraagt na oplevering een bugfixhertest aan.
7. Kwaliteitsbewaking hertest de oorspronkelijke reproduceerstappen. De hertest slaagt, waarna de
   bug op **Opgelost** komt.
8. Kwaliteitsbewaking zet een nieuwe storyverificatie klaar voor de oorspronkelijke story tegen de
   nieuwe productversie.
9. Ook die verificatie slaagt. Daarna sluit de route weer aan op de happy flow.

### Eindresultaat

De oorspronkelijke story en de bugfixstory zijn beide `DONE` en actueel geslaagd geverifieerd. De
bug is **Opgelost**. De epic blijft `ACTIVE` totdat ook al het overige werk is geleverd en
geverifieerd; daarna kan de normale epicverificatie beginnen.

## 5. Een story of bugfix kan tijdelijk niet worden getest

### Beginsituatie

Een opgeleverde story of bugfixstory staat klaar voor verificatie, maar de testomgeving, toegang of
benodigde informatie is tijdelijk niet beschikbaar.

### Scenario

1. Kwaliteitsbewaking legt een geblokkeerde verificatie vast met de concrete blokkade en het reeds
   verzamelde bewijs.
2. Kwaliteitsbewaking meldt de geblokkeerde uitkomst bij Productplanning.
3. De story blijft `DONE`, maar heeft geen geslaagde actuele verificatie. De epic blijft daarom
   `ACTIVE` en kan niet naar `VERIFYING`.
4. Na het verdwijnen van de blokkade moet Kwaliteitsbewaking dezelfde doelversie opnieuw kunnen
   controleren.

### Eindresultaat

De oplevering wordt niet ten onrechte goedgekeurd of als productfout afgekeurd. De blokkade is
zichtbaar en de epic wacht op een echte actuele testuitkomst.

## 6. De epic is onvolledig

### Beginsituatie

Alle stories en bugfixstories van een epic zijn `DONE` en actueel geslaagd geverifieerd. De epic
staat op `VERIFYING` en de volledige epiccontrole wordt uitgevoerd.

### Scenario

1. Kwaliteitsbewaking stelt vast dat gedrag of UX binnen de bevroren epicscope ontbreekt en dat er
   nooit een story voor bestond.
2. Kwaliteitsbewaking bewaart een epicverificatie met uitkomst **Onvolledig** en beschrijft het
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

## 7. Tijdens de epiccontrole blijkt bestaand storygedrag verkeerd gebouwd

### Beginsituatie

De epic staat op `VERIFYING`, maar tijdens de complete gebruikersroute blijkt gedrag uit een reeds
uitgevoerde story niet goed te werken.

### Scenario

1. Kwaliteitsbewaking classificeert de bevinding als bouwfout en publiceert een reproduceerbare bug
   en verificatie.
2. Productontwerp zet de epic terug op `ACTIVE`.
3. Kwaliteitsbewaking vraagt een bugfix aan bij Productplanning.
4. De bugfix, hertest en nieuwe controle van het oorspronkelijke storygedrag volgen scenario 4.
5. Daarna kan opnieuw een complete epicverificatie worden uitgevoerd.

### Eindresultaat

De bouwfout is niet als ontbrekende epicscope behandeld. De bug is aantoonbaar opgelost en de epic
wordt pas na een nieuwe geslaagde totaalcontrole `COMPLETED`.

## 8. De epic is nog niet aantoonbaar of de controle is geblokkeerd

### Beginsituatie

De epic staat op `VERIFYING` en Kwaliteitsbewaking kan nog geen verantwoord eindoordeel geven.

### Scenario

1. Kwaliteitsbewaking publiceert een verificatie met uitkomst **Niet aantoonbaar** wanneer bewijs of
   meting ontbreekt, of **Geblokkeerd** wanneer de controle niet kan worden afgerond.
2. Productontwerp laat de epic op `VERIFYING` staan.
3. Er ontstaat geen bugfix- of planningswerk zolang niet is aangetoond dat ontwikkelwerk nodig is.
4. Kwaliteitsbewaking plant later nieuw bewijswerk of voert de controle opnieuw uit nadat de
   blokkade is verdwenen.

### Eindresultaat

De epic wordt niet te vroeg afgesloten en gaat ook niet onnodig terug naar planning. De verificatie
maakt zichtbaar welk bewijs of welke voorwaarde nog ontbreekt.

## 9. Alles werkt zoals ontworpen, maar de epic bereikt het gebruikersdoel niet

### Beginsituatie

De epic staat op `VERIFYING`. Alle afgesproken functionaliteit werkt, maar de volledige controle
laat zien dat de bedoelde gebruikersverbetering niet is bereikt.

### Scenario

1. Kwaliteitsbewaking publiceert een verificatie met uitkomst **Niet geslaagd** en legt het bewijs
   vast.
2. Kwaliteitsbewaking meldt de uitkomst bij Productontwerp.
3. Productontwerp zet de epic op `NOT_SUCCESSFUL`.
4. Er ontstaat geen repair story en Productplanning krijgt geen generiek herstelverzoek.
5. Een latere Productontwerp-sessie kan de uitkomst als nieuwe kennis gebruiken en eventueel een
   nieuwe vervolgepic maken.

### Eindresultaat

De epic blijft als historisch eindresultaat `NOT_SUCCESSFUL` en wordt niet heropend. Eventueel
vervolgwerk begint als een nieuwe epic met een nieuwe, expliciete productrichting.

## 10. De Stakeholder stopt een gekozen epic

### Beginsituatie

Een epic is al gekozen en heeft status `IN_PLANNING`, `ACTIVE` of `VERIFYING`. Er kunnen stories met
status `TODO`, `IN_PROGRESS` of `DONE` bestaan.

### Scenario

1. De Stakeholder stopt de epic via de UI.
2. Productontwerp voert `cancelEpic(...)` uit en zet de epic op `CANCELLED`.
3. Productontwerp vraagt Productplanning om alle nog niet verstuurde stories van deze epic te
   annuleren.
4. Productplanning zet de `TODO`-stories op `CANCELLED`.
5. Een reeds `IN_PROGRESS` story loopt normaal af, maar er wordt geen nieuwe complete
   epicverificatie meer gestart.
6. De dispatcher verstuurt geen nieuwe story van de geannuleerde epic.

### Eindresultaat

De epic en het niet gestarte werk zijn zichtbaar geannuleerd. Reeds gestart extern werk verdwijnt
niet stilzwijgend en de epic kan niet per ongeluk alsnog `COMPLETED` worden.

## 11. Software Factory is tijdelijk niet bereikbaar

### Beginsituatie

Er staat een uitvoerbare `TODO`-story bovenaan de backlog, maar de dispatcher krijgt een timeout,
netwerkfout of andere tijdelijke transportfout bij Software Factory.

### Scenario

1. De dispatcher bewaart de mislukte poging als `DeliveryAttempt`.
2. De story blijft `TODO` wanneer niet bekend is dat extern werk bestaat.
3. Voor iedere retry controleert de dispatcher met dezelfde idempotentiesleutel of Software Factory
   de story misschien toch heeft aangemaakt.
4. Bestaat het externe werk al, dan herstelt de dispatcher de koppeling en zet Productplanning de
   story op `IN_PROGRESS`.
5. Bestaat het niet, dan probeert de dispatcher later gecontroleerd opnieuw.

### Eindresultaat

Er ontstaat maximaal één externe Software Factory-story. Er wordt geen bug, repair story of
planningsopdracht gemaakt; de technische fout blijft de verantwoordelijkheid van de dispatcher.

## Nog niet volledig bepaald door de huidige specificaties

De scenario's maken twee vervolgstappen zichtbaar die nog niet eenduidig genoeg zijn beschreven:

1. Na een **geblokkeerde storyverificatie** staat wel vast dat de epic `ACTIVE` blijft, maar nog niet
   welk exact idempotent mechanisme de nieuwe controle klaarzet zodra de blokkade is verdwenen.
2. Na een **afgekeurde bugfixhertest** kan de bug volgens het contract **Heropend** worden, maar nog
   niet expliciet of Kwaliteitsbewaking automatisch een nieuw `requestBugfix(...)` doet of dat een
   andere aanleiding vereist.

Deze punten moeten vóór implementatie van de volledige keten worden besloten. Tot die tijd mogen
acceptatietests hiervoor geen zelfbedacht gedrag als norm vastleggen.

## Brondocumenten

- [Overzicht](overzicht.md)
- [Productontwerp-API](processen/productontwerp/api.md)
- [Productplanning-API](processen/productplanning/api.md)
- [Software Factory-dispatcher](processen/software-factory-dispatcher.md)
- [Kwaliteitsbewaking-API](processen/kwaliteitsbewaking/api.md)
- [Processen en entiteiten](processen/processen-en-entiteiten.md)
- [Integratie- en acceptatietesten](platform/integratie-en-acceptatietesten.md)

