# Product Factory v2 — eerste opzet

Status: eerste denkversie. Dit document beschrijft nog geen definitief ontwerp.

## Het idee in één zin

De Stakeholder geeft een brede opdracht aan een product. Product Factory onderzoekt daarna op eigen
initiatief hoe dat product de best mogelijke software voor die opdracht kan worden.

Zij beantwoordt steeds drie vragen:

1. Wat moeten we aan het huidige product verbeteren?
2. Welke nieuwe stap brengt het product dichter bij het droombeeld?
3. Hoe zou een uitzonderlijk goede toekomstversie eruitzien, ook als die nu nog onhaalbaar lijkt?

Daarvoor kijkt zij niet alleen naar het huidige project. Zij onderzoekt ook soortgelijke producten,
problemen van gebruikers, nieuwe mogelijkheden en oplossingen uit andere vakgebieden. Daarna maakt
zij het gekozen werk klein en duidelijk, laat het bouwen en leert van het resultaat.

## Waarom een tweede versie?

De eerste versie kan veel. Er zijn productcycli, roadmaps, ideeën, UX-ontwerpen, epics, bugs,
testsessies, overleggen en verschillende soorten geheugen. Dat klinkt krachtig, maar het maakt een
simpele vraag moeilijk:

> Wat is nu het belangrijkste om te doen, en waarom?

Ook zijn er nu meerdere manieren om van een idee naar werk te gaan. Daardoor is soms niet duidelijk:

- welk idee nog maar een losse gedachte is;
- welke keuze echt gemaakt is;
- welk UX-ontwerp het actuele ontwerp is;
- wat als volgende gebouwd moet worden;
- wat we van eerder werk hebben geleerd.

Versie 2 moet daarom minder begrippen, minder schermen en één duidelijke werkwijze krijgen.

## Wat Product Factory wel en niet doet

Product Factory **denkt mee over het product**. Zij helpt bij kiezen, uitwerken, laten bouwen en leren.

Zij wacht niet alleen op opdrachten. Binnen het afgesproken productdoel zoekt zij zelf naar problemen,
kansen en inspiratie. Zij houdt ook een ambitieus droombeeld bij van wat het product uiteindelijk zou
kunnen zijn.

Product Factory bouwt de productcode niet zelf. Daarvoor geeft zij kleine, duidelijke opdrachten aan
Software Factory. Software Factory bouwt en test die opdrachten.

De verdeling is dus:

- **Product Factory:** wat en waarom;
- **Software Factory:** hoe bouwen en opleveren.

## Wat "de best mogelijke software" betekent

De best mogelijke software is niet automatisch het product met de meeste functies of de nieuwste
techniek. Het is het product dat zijn brede opdracht zo goed mogelijk vervult.

Product Factory kijkt daarom steeds naar:

- bereikt de gebruiker het gewenste resultaat;
- is dat eenvoudig en prettig;
- is het betrouwbaar, veilig, toegankelijk en zorgvuldig met gegevens;
- past het bij de mensen voor wie het bedoeld is;
- kan het product blijven leren en verbeteren;
- levert een nieuwe mogelijkheid meer waarde op dan extra drukte of onderhoud.

Wat "best" is, kan veranderen door nieuwe kennis, techniek of gebruikersbehoeften. Product Factory
blijft dit daarom onderzoeken. Zij legt ook uit op welk bewijs haar beeld van "best" is gebaseerd.

## De twee soorten werk

Ieder product heeft steeds twee soorten werk.

### 1. Het huidige product verbeteren

Dit werk zorgt dat wat er al is goed blijft werken.

Voorbeelden:

- een fout oplossen;
- een onduidelijke knop verbeteren;
- een scherm rustiger maken;
- het product sneller maken;
- toegankelijkheid verbeteren;
- onnodig ingewikkelde code opruimen;
- een kleine wens van bestaande gebruikers uitvoeren.

We noemen dit in dit document **Verbeteren**.

De simpele vraag is:

> Wat zit gebruikers vandaag in de weg?

### 2. Nieuwe mogelijkheden bouwen

Dit werk brengt het product stap voor stap dichter bij het droombeeld.

Voorbeelden:

- een nieuwe taak mogelijk maken;
- een nieuwe groep gebruikers helpen;
- een handmatige stap automatiseren;
- een ontbrekend onderdeel van het droombeeld bouwen.

We noemen dit in dit document **Vernieuwen**.

De simpele vraag is:

> Welke nieuwe mogelijkheid brengt ons nu het meeste dichter bij het droombeeld?

### Beide zijn altijd zichtbaar

Verbeteren en Vernieuwen staan naast elkaar. Het ene mag het andere niet ongemerkt verdringen.

Kritieke fouten gaan altijd voor. Als het product goed genoeg werkt, kiezen we bewust hoeveel ruimte
naar Verbeteren en hoeveel ruimte naar Vernieuwen gaat. Bijvoorbeeld één verbetering en daarna twee
nieuwe stappen. Dit is een keuze, geen vaste regel in de software.

Er is wel één gezamenlijke grens voor werk dat tegelijk bezig is. Zo begint de factory niet steeds
nieuw werk terwijl eerder werk nog niet klaar is.

## De eenvoudige begrippen van versie 2

Versie 2 gebruikt zo weinig mogelijk eigen woorden.

### Stakeholder

De **Stakeholder** is de mens voor wie Product Factory werkt en die belang heeft bij de uitkomst van
het product. In de eerste toepassing is dat de eigenaar van het product. De Stakeholder geeft het
productdoel en de harde grenzen, neemt deel aan overleggen en kan de richting corrigeren of werk
stopzetten.

De productmodule bewaart per product een Stakeholderprofiel met identiteit, rol, contactwijze en het
afgesproken beslissingsmandaat. De Stakeholder levert verplicht de startopdracht en antwoorden op
beslissingen die buiten het mandaat vallen. Daarnaast kan de Stakeholder richting, gebruikerssignalen,
risico's en correcties leveren. De Stakeholder maakt geen epics, stories, bugs of backlogposities.

De Stakeholder is niet hetzelfde als de plannerrol binnen Productplanning. De planner onderhoudt
binnen de afgesproken ruimte de dagelijkse backlogvolgorde. De Stakeholder hoeft die keuzes niet
allemaal vooraf
goed te keuren, maar kan altijd uitleg vragen, bijsturen en de grenzen veranderen.

### Productdoel

Het productdoel is de brede opdracht van de Stakeholder. Het vertelt voor wie het product is en welk
resultaat het product zo goed mogelijk moet bereiken.

Een productdoel is bewust algemeen. Bijvoorbeeld:

> Help een klein softwareteam om met zo min mogelijk gedoe goede software te maken.

Het productdoel schrijft nog geen schermen, functies of technische oplossing voor. Het verandert
niet iedere week. Alleen de Stakeholder kan het doel of de harde grenzen wezenlijk veranderen.

### Droombeeld

Het droombeeld beschrijft hoe het product de brede opdracht ooit uitzonderlijk goed zou kunnen
vervullen. Product Factory maakt en onderhoudt dit beeld zelf.

Het droombeeld mag verder gaan dan wat vandaag technisch, financieel of praktisch haalbaar lijkt.
Het kan bijvoorbeeld beschrijven dat een gebruiker een gewenst resultaat krijgt zonder de tientallen
handmatige stappen die daar nu voor nodig zijn.

Het droombeeld is geen belofte en geen lijst functies die allemaal gebouwd moeten worden. Het is een
richtingaanwijzer. We gebruiken het om te herkennen welke kleine stap van vandaag bijdraagt aan iets
dat op lange termijn echt bijzonder kan worden.

Product Factory past het droombeeld aan wanneer onderzoek of echte resultaten daar een goede reden
voor geven. Oude versies en de reden van verandering blijven terug te vinden.

### Onderzoeksinzicht

Een onderzoeksinzicht is iets bruikbaars dat Product Factory buiten het eigen project heeft geleerd.
Het bevat:

- wat er is gevonden;
- waar en wanneer het is gevonden;
- waarom het voor dit product belangrijk kan zijn;
- wat een feit is en wat een gedachte van Product Factory is;
- welke kans, waarschuwing of vraag eruit volgt.

Een onderzoeksinzicht is nog geen besluit om iets te bouwen.

### Gebruikerssignaal

Een gebruikerssignaal is een nog niet beoordeelde aanwijzing dat een gebruiker ergens last van
heeft, iets mist of juist veel waarde uit haalt. Het kan afkomstig zijn uit directe feedback,
support, een observatie, onderzoek, analytics of een Stakeholder die feedback namens gebruikers
invoert.

De oorspronkelijke melding, bron, gebruikscontext en eventueel bewijs blijven ongewijzigd bewaard.
Een gebruikerssignaal is nog geen besluit, bug, epic of story. Kwaliteitsbewaking kan na verificatie
een bug publiceren; Productontwerp kan er een epic of een geregistreerd ontwerpbesluit aan koppelen.

Status en afhandeling staan op hetzelfde `UserSignal`. De oorspronkelijke melding blijft
onveranderlijk, maar de productmodule kan via betekenisvolle commands status, uitkomst en links naar
een verificatie, bug, besluit of epic bijwerken. Kwaliteitsbewaking bewaart haar onderzoek en bewijs
apart als `Verification` en roept daarna `recordSignalInvestigation(...)` op de productmodule aan.
Zo kan de tester zichtbaar maken wat er met een melding is gebeurd zonder de oorspronkelijke woorden
van de melder te herschrijven of rechtstreeks in de signaaltabel te schrijven.

### Epic

Een epic is een samenhangende verandering met één duidelijk gewenst resultaat. Een epic kan een
nieuwe mogelijkheid zijn, maar ook een grotere UX-, betrouwbaarheids-, performance- of
toegankelijkheidsverbetering.

Een epic beschrijft:

- welk probleem of welke kans we aanpakken;
- voor wie we dit doen;
- welke uitkomst we willen bereiken;
- wat wel en niet binnen de scope valt;
- hoe dit bij het productdoel en droombeeld past;
- welk bewijs er is;
- wat we nog niet zeker weten;
- het actuele UX-ontwerp, inclusief de hoofdroute en belangrijke schermtoestanden;
- relevante toegankelijkheids-, privacy- en kwaliteitsgrenzen;
- bekende risico's en afhankelijkheden;
- hoe we straks zien of de epic geslaagd is.

Productontwerp maakt de epic zo duidelijk en behapbaar dat Productplanning hem zonder intern
onderzoeksdossier in kleine stories kan verdelen. Productontwerp maakt die stories niet zelf.

Iedere gepubliceerde epicversie is inhoudelijk onveranderlijk. Zolang Productplanning een epic nog niet heeft gekozen, mag
Productontwerp een nieuwe versie publiceren en de vorige vervangen. Zodra Productplanning een exact
epic-ID en versienummer kiest, wordt die versie bevroren. Nieuwe inzichten veranderen de gekozen
versie niet, maar leiden tot een vervolgepic of een expliciet voorstel om de uitvoering te stoppen en
een andere epic te kiezen.

### Epicstatus

Inhoud en voortgang staan op één `Epic`, waarvan Productontwerp eigenaar is. Productplanning kan een
beschikbare versie via `claimEpicForPlanning(...)` atomair claimen en later via expliciete commands
**Actief** of via `markEpicReadyForVerification(...)` **Controleren** laten worden. Dat laatste
start geen tester of queue in Productontwerp. Kwaliteitsbewaking kan met
`recordEpicVerification(...)` een onveranderlijk verificatieresultaat laten verwerken. Alleen
Productontwerp schrijft de epic en geen enkele publieke functie kan scope of UX van een geclaimde
versie veranderen.

### Story

Een story is één concrete opdracht die Software Factory kan bouwen en testen. Er zijn twee typen:

- `PRODUCT_STORY` — een klein stuk zichtbaar gedrag binnen precies één bevroren epicversie;
- `BUGFIX` — herstel van precies één door Kwaliteitsbewaking gepubliceerde bugversie.

Iedere story bevat `sequenceNumber`, status `TODO`, `IN_PROGRESS` of `DONE` en alle inhoud die nodig
is voor uitvoering. Een productstory bevat een zelfstandige kopie van de relevante UX-informatie en
ontwerpassets uit de epic. Een bugfixstory bevat de bug, bewijs en zo nodig dezelfde relevante UX.
Software Factory hoeft de epic, bug of Product Factory daarna niet te raadplegen. Wanneer een epic
wordt gepland, verdeelt Productplanning de volledige bevroren scope in zo veel stories als nodig.
Dat kunnen er twee of dertig zijn; er geldt geen kunstmatig minimum of maximum.

### Bug

Een bug betekent dat bestaand gedrag aantoonbaar niet werkt zoals het hoort. Een duidelijke bug kan
rechtstreeks als bugfix worden uitgevoerd en hoeft niet kunstmatig een epic te worden.

Als meerdere bugs samen één groter probleem laten zien, kan daar wel een epic uit ontstaan. De losse
symptomen worden dan niet eindeloos één voor één bestreden.

### Ontbrekende epicdekking

Wanneer gedrag duidelijk binnen de bevroren scope of UX valt maar nooit in een story is opgenomen,
legt Kwaliteitsbewaking dat als dekkingsgat in de epicverificatie vast. Via
`requestEpicGapPlanning(...)` vraagt zij Productplanning om aanvullende stories. Dat command zet
alleen een opdracht in de planningsqueue en start geen agents. Het gat is geen
aparte entiteit met een eigen lifecycle.

Ontbrekend gedrag is wel een bug wanneer het in een uitgevoerde story was afgesproken maar niet goed
is gebouwd. Een nieuwe wens buiten de bevroren scope is geen dekkingsgat en geen bug; Productontwerp kan
daar een vervolgepic van maken.

### Backlog

De backlog is geen aparte entiteit. Zij is de lijst van alle stories van een product die nog niet
`DONE` zijn, geordend op `sequenceNumber`. De lijst kan rechtstreeks uit de database worden gelezen.
Een lege backlog is een geldige toestand en start geen proces. Er is geen lage grens, streefpeil of
maximum: nieuwe stories ontstaan wanneer een concrete epic, bug of dekkingsgat wordt gepland.

Een kleine verbetering die geen bug is, wordt als productstory binnen een passende, zo klein
mogelijke epic uitgewerkt. Daardoor hoeft de interface tussen de processen geen derde soort
uitvoerbaar werk te kennen.

Productplanning is eigenaar van story-inhoud, volgorde en status. De dispatcher gebruikt
`markStoryAsDispatched(...)` na verzending en `markStoryAsDeveloped(...)` na oplevering; alleen de
commandhandler van Productplanning zet `TODO` naar `IN_PROGRESS` of `IN_PROGRESS` naar `DONE`. Een
gevonden afwijking heropent de oude story niet; zij leidt via een bug tot een nieuwe bugfixstory.

Wanneer de dispatcher de eerste `TODO`-story verstuurt, maakt hij mechanisch een onveranderlijk
`StoryDeliveryPackage`. Dat pakket bevat de volledige productstory of bugfixstory, bron-ID's en
versies, acceptatiecriteria, relevante UX en alle benodigde attachments. De dispatcher voegt geen
nieuwe inhoudelijke keuzes toe.

### Intern leerresultaat

Na de oplevering leggen we kort vast:

- wat is gebouwd;
- of het werkt zoals bedoeld;
- wat gebruikers of tests laten zien;
- wat we nu anders weten;
- wat de logische volgende keuze is.

Dit leerresultaat wordt gebruikt bij de volgende keuze, maar blijft intern binnen Productontwerp.
Alleen wanneer er een concrete keuze uit volgt, wordt die keuze met een korte onderbouwing in het
Besluitenregister vastgelegd.

### Besluit en Besluitenregister

Een besluit is een betekenisvolle keuze die richting, productinhoud, prioriteit, uitvoering of de
afhandeling van een onderwerp verandert. De module die bevoegd is voor het onderwerp neemt het
besluit. Het centrale Besluitenregister bewaart daarna de leesbare registratie en neemt zelf geen
besluiten.

Een besluit bevat de keuze, korte onderbouwing, alternatieven, gebruikte bronnen en entiteitsversies,
beslisser, toepassingsgebied en geldigheid. Het heeft een ingangsdatum en kan een einddatum krijgen.
Als een besluit wordt ingetrokken, krijgt het status **Ingetrokken**, een einddatum en een reden. Als
een nieuw besluit het vervangt, krijgt het oude besluit status **Vervangen** en een einddatum gelijk
aan de ingangsdatum van het nieuwe besluit. Beide records verwijzen naar elkaar en de oude inhoud
blijft leesbaar.

Het Besluitenregister is een ondersteunende module zonder agents of geplande procesfunctie. De
frontend kan actuele en historische besluiten tonen. Procesmodules blijven hun specifieke contracten
gebruiken en voeren geen vrije besluittekst uit als ongetypeerde opdracht.

### Agent

Een agent is een herkenbare, langlevende AI-rol met een eigen verantwoordelijkheid en geheugen. Een
proces kan door één agent of door meerdere gespecialiseerde agents worden uitgevoerd. Dezelfde agent
kan over meerdere uitvoeringsruns heen actief blijven, maar zijn geheugen is nooit de enige plek
waar een productfeit, besluit of status bestaat.

### Overleg

Een overleg is een gesprek tussen de Stakeholder en één of meer agents over richting, een open vraag,
een beslissing of bijsturing. Zowel de Stakeholder als een agent of proces kan een overleg aanvragen.

Een overleg is geen vierde proces. Het is een gedeeld coördinatiemiddel dat ieder van de drie
processen kan gebruiken. De uitkomst wordt als notulen, besluiten, acties en expliciete
geheugenwijzigingen opgeslagen.

### Geheugen

Geheugen is duurzame kennis die een volgende uitvoering kan gebruiken. Versie 2 kent drie lagen:

- agentgeheugen voor wat één agent vanuit zijn eigen rol heeft geleerd;
- procesgeheugen voor kennis die alle uitvoerders van hetzelfde proces nodig hebben;
- productgeheugen voor gedeelde productfeiten en richting; betekenisvolle keuzes staan in het
  afzonderlijke Besluitenregister.

Welke informatie in welke laag hoort en hoeveel gezag zij heeft, wordt verderop expliciet gemaakt.

## Hoe een product begint

Een nieuw product start met een kort overleg met de Stakeholder. We leggen alleen vast wat nodig is
om goede keuzes te kunnen maken:

1. Voor wie is het product?
2. Welk probleem lost het op?
3. Wat kan het product nu al?
4. Wat is de brede opdracht van het product?
5. Welke grenzen mogen we niet overschrijden?
6. Waar staat de productcode?

De Stakeholder bekijkt en bevestigt het productdoel en de harde grenzen. Daarna doet Product Factory
zelf een eerste onderzoek. Zij maakt op basis daarvan een eerste droombeeld, kansen en mogelijke
eerste stappen. De Stakeholder mag bijsturen, maar hoeft niet vooraf te bedenken hoe de uiteindelijke
oplossing eruit moet zien.

## Hoe signalen binnenkomen

Alle losse informatie wordt door de productmodule als gebruikerssignaal geregistreerd. In de
frontend staat dit register op het scherm **Inbox**. Product Factory doet niet alsof ieder idee
meteen goed of belangrijk is; **Inbox** is dus een schermnaam en geen aparte technische module.

Een signaal kan komen van:

- de Stakeholder;
- gebruikersfeedback;
- een test;
- een foutmelding;
- eerder geleverd werk;
- onderzoek door een agent;
- Software Factory.

Product Factory probeert dubbele signalen bij elkaar te zetten. Zij mag een signaal samenvatten en
aanvullen, maar niet stilletjes promoveren tot gepland werk.

## Hoe Product Factory zelf op onderzoek uitgaat

Product Factory wacht niet tot de Stakeholder een idee invoert. Zij gaat regelmatig en bij belangrijke
nieuwe vragen zelf op onderzoek uit.

Zij kijkt onder andere naar:

- producten die hetzelfde probleem proberen op te lossen;
- wat gebruikers goed en slecht vinden aan die producten;
- veelgevraagde functies en vaak gehoorde klachten;
- oplossingen uit andere markten die hier misschien ook werken;
- nieuwe technieken die iets mogelijk maken wat eerder niet kon;
- ontwikkelingen die het huidige product minder nuttig of juist belangrijker kunnen maken;
- voorbeelden van bijzonder goede en bijzonder slechte gebruikerservaringen.

Product Factory zoekt dus niet alleen naar functies om na te bouwen. Zij probeert te begrijpen waarom
een oplossing werkt, welk probleem eronder zit en of dat ook voor dit product geldt.

Bij onderzoek gelden een paar simpele regels:

1. Een belangrijke bewering krijgt een vindbare bron en datum.
2. Feiten, meningen van gebruikers en ideeën van de agent worden niet door elkaar gehaald.
3. Eén voorbeeld is geen bewijs dat iets voor alle gebruikers werkt.
4. We nemen geen teksten, ontwerpen of beschermde onderdelen klakkeloos over.
5. Privacy, toegankelijkheid, kosten en afhankelijkheid van leveranciers worden meegenomen.
6. Onderzoek moet eindigen in bruikbare inzichten, niet in een lange verzameling links.

Nieuwe onderzoeksinzichten kunnen leiden tot:

- een nieuw signaal;
- een aanpassing van een bestaand idee;
- een nieuw voorstel voor Verbeteren of Vernieuwen;
- een verandering in het droombeeld;
- de conclusie dat een populaire oplossing juist niet bij dit product past.

Product Factory bewaart de herkomst van inzichten, maar toont in het gewone productscherm vooral de
conclusie en waarom die van belang is.

## Hoe Product Factory droomt

Product Factory kijkt bewust verder vooruit dan de huidige roadmap. Zij stelt zichzelf bijvoorbeeld
deze vragen:

- Hoe zou dit product werken als de gebruiker bijna geen uitleg of handwerk meer nodig had?
- Welk resultaat wil de gebruiker eigenlijk, los van de manier waarop dat nu wordt bereikt?
- Wat zou dit product tien keer nuttiger, eenvoudiger of betrouwbaarder maken?
- Wat kan nu nog niet, maar wordt misschien mogelijk door nieuwe techniek of nieuwe gegevens?
- Hoe zou de ideale ervaring eruitzien als de huidige code en schermen nog niet bestonden?

Bij het dromen hoeft de bestaande architectuur niet leidend te zijn. Het productdoel en de harde
grenzen blijven wel gelden. Een droom mag dus technisch moeilijk zijn, maar niet strijdig zijn met
bijvoorbeeld privacy, veiligheid of de afgesproken doelgroep.

Een droombeeld bestaat vooral uit:

- een kort verhaal over wat een gebruiker later kan bereiken;
- de belangrijkste ervaringen die dit bijzonder maken;
- grote problemen die daarvoor nog opgelost moeten worden;
- aannames die misschien niet waar blijken te zijn;
- mogelijke tussenstappen, zonder te doen alsof die al vaststaan.

Product Factory mag meerdere wilde toekomstmogelijkheden verkennen. Zij maakt daaruit één actueel
droombeeld en legt uit waarom dit het meest veelbelovend is. Niet-gekozen mogelijkheden blijven als
inspiratie terug te vinden.

Een droomidee gaat nooit rechtstreeks naar Software Factory. Eerst moet duidelijk worden welk echt
probleem het oplost en welke kleine epic of eerste epic-slice nu al nuttig of leerzaam is.

## Drie afstanden tegelijk

Product Factory kijkt steeds op drie afstanden:

```text
Vandaag                 Komende stappen                 Verre toekomst
Verbeteren              Vernieuwen                      Dromen
Wat zit nu in de weg?   Wat bouwen we als volgende?     Wat zou het product geweldig maken?
```

De verre toekomst geeft richting aan de komende stappen. Resultaten van vandaag kunnen op hun beurt
het droombeeld veranderen. Zo wordt de droom geen los document en wordt de roadmap geen verzameling
toevallige functies.

## De drie vaste processen

Product Factory v2 bestaat uit drie zelfstandige Spring Modulith-modules met namen die hun
verantwoordelijkheid beschrijven:

- **Productontwerp** onderzoekt, droomt en publiceert complete epics met UX;
- **Productplanning** kiest een exacte epicversie, maakt stories en onderhoudt de backlog;
- **Kwaliteitsbewaking** test opleveringen en complete epics en publiceert bugs en bewijs.

Iedere module is voor de andere modules een black box: zij kennen alleen de gepubliceerde
data-interface en weten niets van agents, prompts, stappen, scores of interne tabellen van een
andere module.

Iedere procesmodule heeft precies één agentgestuurde ingang:

```java
void runProcessSession();
```

Een scheduler of bevoegde handmatige UI-/REST-actie roept deze functie aan. Per procesmodule kan
maximaal één run tegelijk actief zijn. Een botsende handmatige aanroep krijgt een fout; een
botsende scheduler-run wordt overgeslagen en geregistreerd. Is er niets te doen, dan eindigt de
aanroep als succesvolle no-op. Andere modules kunnen geen agents of interne stappen starten.

Naast de runfunctie mag een module een kleine deterministische application-API aanbieden. Die bevat
read-only queries en betekenisvolle commands zoals `claimEpicForPlanning(...)`,
`markStoryAsDeveloped(...)` en `recordSignalInvestigation(...)`. Een command start geen agents en
geeft geen vrije schrijftoegang: de eigenaar controleert bevoegdheid, versie, huidige status en
idempotentie en schrijft uitsluitend zijn eigen entiteit.

De modules delen fysiek één database, maar niet één vrij toegankelijk datamodel. Iedere entiteit
heeft precies één schrijvende module. Andere modules lezen via read-only DTO's en vragen een
toegestane wijziging alleen via de publieke Spring Modulith-interface van de eigenaar aan. Zij
schrijven nooit rechtstreeks in de tabellen van een andere module.
Interne entiteiten en repositories blijven buiten de named interface.

Stabiele owner-specifieke portinterfaces, command-DTO's en read-only query-DTO's staan in het
neutrale `processcontracts`; de eigenaar implementeert de port. Interne JPA-entiteiten, repositories
en agents staan daar nooit. Een DTO zoals `EpicDetails` is geen tweede database-entiteit. Waar geen
direct antwoord nodig is, kan een duurzaam application event dezelfde overgang aanvragen. Zo
ontstaan geen cyclische Spring Modulith-codeafhankelijkheden.

```text
Productontwerp
complete epicdefinitie + UX
            │
            ▼
Productplanning ◀──── bugs, verificaties en planverzoeken ── Kwaliteitsbewaking
            │
            │ zelfvoorzienende stories op sequenceNumber
            ▼
Software Factory-dispatcher
            │ volledig StoryDeliveryPackage inclusief UX
            ▼
     Software Factory
```

Productplanning en Kwaliteitsbewaking hebben ieder een eigen duurzame werkqueue. Andere modules
plaatsen daar met een snel, idempotent command een `PlanningWorkItem` of `QualityWorkItem` in. Pas
een latere `runProcessSession()` claimt het werk en start agents. Productontwerp heeft geen queue:
het blijft zelfstandig door scheduler of handmatig gestart worden. Deterministische
lifecycle-overgangen mogen direct via de publieke module-API lopen.

De **Software Factory-dispatcher** is geen vierde productproces. Het is een eenvoudige geplande
adapter binnen Productplanning. Hij gebruikt geen agents en neemt geen productbesluiten. Hij verwerkt
eerst de status van eerder verzonden stories. Wanneer Software Factory voor een product geen
openstaande story meer heeft, verstuurt hij precies de `TODO`-story met het laagste `sequenceNumber`,
roept `markStoryAsDispatched(...)` aan en bewaart het externe Software Factory-ID. Na oplevering
roept hij `markStoryAsDeveloped(...)` aan. Alleen Productplanning verandert de storystatus.
Software Factory krijgt alle inhoud en UX in het leveringspakket en kan daarmee zelfstandig verder.

### Scheduler, processessies en frontend

De scheduler en frontend zijn verschillende technische onderdelen:

- de **scheduler** roept op een vast ritme de drie functies `runProcessSession()` en de functie
  `runDispatchSession()` aan; hij kiest geen product, epic of story;
- een bevoegde UI-/REST-actie kan dezelfde processfunctie handmatig starten;
- iedere procesmodule handhaaft modulebreed maximaal één actieve run;
- Productontwerp kiest na zo'n aanroep zelf een opdracht; Productplanning en Kwaliteitsbewaking
  claimen hun vaste queuebatch; ieder maakt zijn eigen processessie en schrijft na afloop zijn eigen
  onveranderlijke `ProcessSession`;
- de scheduler mag sessieresultaten lezen voor monitoring en een technische retry, maar schrijft
  geen sessiepublicatie;
- de **frontend** gebruikt publieke read-only queries om actuele toestand en historie te tonen;
- een actie in de frontend wordt als command naar de application service van de eigenaarsmodule
  gestuurd en is geen rechtstreekse databasewijziging.

Productontwerp, Productplanning en Kwaliteitsbewaking gebruiken hetzelfde contracttype voor hun
sessieresultaat, maar ieder record heeft precies één van die processen als schrijver. De dispatcher
houdt zijn technische dispatchpogingen binnen Productplanning bij.

## Input, status en overdracht tussen de processen

Ieder proces heeft eigen interne administratie en een kleine gepubliceerde interface. Een agentrun is
alleen de uitvoering van een stap; hij is nooit de enige plek waar actuele productstatus bestaat.

De hoofdregel is:

> Eén module schrijft een entiteit. Andere modules lezen een read-only weergave en kunnen alleen via
> een betekenisvol command aan de eigenaar vragen om een geldige overgang uit te voeren.

Daardoor kan een proces stoppen en later verdergaan zonder dat een andere module zijn interne
toestand hoeft te begrijpen.

### Het totaaloverzicht

| Proces | Gepubliceerde input | Eigen duurzame output | Betekenis voor andere modules |
|---|---|---|---|
| Productontwerp | productopdracht, Stakeholderrichting, verificaties en gebruikerssignalen | droombeeld en complete geversioneerde `Epic`-entiteiten met UX en status | welke gebruikersverbeteringen beschikbaar of actief zijn; een nieuwe epic levert een planningrequest op |
| Productplanning | `PlanningWorkItem`s voor epics, bugs, dekkingsgaten of prioriteit, plus productgrenzen | bijgewerkte queue-items en zelfvoorzienende `Story`-entiteiten met `sequenceNumber`, drie statussen en leveringsvelden | welk planwerk wacht of klaar is en welke niet-afgeronde stories in welke volgorde staan |
| Kwaliteitsbewaking | `QualityWorkItem`s, testconfiguratie, bevroren epics, stories en eerdere bugs | bijgewerkte queue-items, `Bug` en onveranderlijke `Verification` voor story, epic of gebruikerssignaal | welk testwerk wacht of klaar is en wat aantoonbaar werkt, ontbreekt of verkeerd is gebouwd |

### De overdrachtskaart

```text
productopdracht + signalen + leren
                 │
                 ▼
          Productontwerp
                 │
       Epic + requestEpicPlanning
                 │
                 ▼
         PlanningWorkItem-queue ◀──── bugfix- en epicgatrequests
                 │
                 ▼
         Productplanning ──→ geordende Story-lijst
                 │                         │
                 │                         ▼
                 │             Software Factory-dispatcher
                 │                         │ StoryDeliveryPackage
                 │                         ▼
                 │                  Software Factory
                 │                         │ oplevering
                 │                         ▼
                 └── request verification → QualityWorkItem-queue
                                           │
                                           ▼
                                  Kwaliteitsbewaking
                                           │ recordEpicVerification
                                           └────────────────────────→ Productontwerp
```

Onderzoeksvragen, antwoorden en kansvoorstellen blijven intern binnen Productontwerp. Directe
commands schrijven alleen een geldige statusovergang of duurzaam queue-item. Agentwerk start alleen
in `runProcessSession()`; een queue-item wacht op een volgende geplande of handmatige run.

### Twee soorten status

Er zijn twee soorten status die niet met elkaar verward mogen worden.

**Inhoudelijke productstatus** hoort bij een duurzaam productobject en wordt alleen door de eigenaar
geschreven. Voorbeelden zijn de epicstatus van Productontwerp, de storystatus van Productplanning
en de bugstatus van Kwaliteitsbewaking. Deze status blijft
bestaan wanneer geen enkel proces draait.

**Operationele processtatus** vertelt alleen wat de automatisering op dit moment doet. Per proces
wordt apart bijgehouden:

- of het wacht, gepland staat, draait, geblokkeerd is of technisch is mislukt;
- welke concrete opdracht of welk object het verwerkt;
- wanneer de laatste geslaagde uitvoering was;
- wanneer het opnieuw moet starten;
- welke fout of menselijke beslissing voortgang blokkeert.

Een procesrun krijgt daarom een eigen uitvoeringsregistratie, maar die registratie is geen
productwaarheid. Als een agent crasht tijdens het onderzoeken van een epic, blijft de epic
bijvoorbeeld **Onderzoeken** en vermeldt de operationele status dat de laatste run is mislukt. De
epic wordt niet automatisch **Geblokkeerd** of **Gestopt**.

De drie processen zelf zijn doorlopend en worden niet **Klaar**. Alleen hun afzonderlijke opdrachten,
productobjecten en uitvoeringsruns hebben een status.

### De duurzame productobjecten

De processen publiceren minimaal de volgende objecten:

- **Stakeholderprofiel** — identiteit, rol, contactwijze en het afgesproken beslissingsmandaat;
- **Productopdracht** — productdoel, harde grenzen en publieke Git-URL waar de processen hun keuzes
  aan toetsen en de huidige code en documentatie read-only kunnen bekijken;
- **Stakeholderrichting** — een expliciete aanwijzing, correctie of grens van de Stakeholder, met
  datum, reden en toepassingsgebied;
- **Gebruikerssignaal** — oorspronkelijke feedback met bron en bewijs, plus actuele status,
  verwerkingsuitkomst en links naar verificatie, bug, besluit of epic; de broninhoud blijft ongewijzigd;
- **Droombeeld** — de actuele verre richting van Productontwerp, zichtbaar voor de
  Stakeholder maar geen overdracht naar een ander proces;
- **Epic** — een geversioneerde gewenste gebruikersverbetering met scope, bewijs, UX, succescriteria
  en status, uitsluitend geschreven door Productontwerp;
- **Story** — een zelfvoorzienende productstory of bugfixstory met type, `sequenceNumber`, status
  `TODO`, `IN_PROGRESS` of `DONE`, acceptatiecriteria en alle relevante UX en assets;
- **Planningsopdracht** — een duurzaam `PlanningWorkItem` met bron, type, status en
  idempotentiesleutel; alleen Productplanning schrijft het;
- **Testopdracht** — een duurzaam `QualityWorkItem` met doelversie, type, status en
  idempotentiesleutel; alleen Kwaliteitsbewaking schrijft het;
- **Bug** — een reproduceerbare afwijking met bewijs, ernst en herstelstatus;
- **Besluit** — een betekenisvolle keuze met onderbouwing, alternatieven, bronversies,
  toepassingsgebied, ingangsdatum en optionele einddatum of vervangingsrelatie;
- **Afleverpoging** — onveranderlijke technische historie van verzending, response, fout en retry;
- **Storyleveringspakket** — de onveranderlijke, volledige JSON-overdracht van één story of bugfix
  naar Software Factory, inclusief UX en attachments;
- **Verificatie** — onveranderlijk bewijs over een story, epic of gebruikerssignaal; een epiccontrole
  kan daarin ontbrekende dekking vastleggen;
- **Kwaliteitspatroon** — een `UserSignal` met categorie `QUALITY_PATTERN` dat Productontwerp kan onderzoeken;
- **Overleg** — agenda, deelnemers, berichten, geraadpleegde bronnen, status en gekoppelde objecten;
- **Overleguitkomst** — notulen met besluiten, open vragen, acties en expliciete geheugenwijzigingen;
- **Productgeheugen** — gedeelde actuele feiten en richting voor de processen; besluiten hebben een
  eigen register.

Onderzoeksdossiers, leerresultaten, bronnen, testsessies, agents, prompts, afwegingen, procesgeheugen
en agentgeheugen blijven intern bij hun eigenaar. Een andere module kan wel een concrete publieke
uitkomst of geregistreerd besluit lezen, maar niet het interne object wijzigen.

### Regels voor iedere overdracht

Een overdracht tussen processen is pas compleet wanneer:

1. de output duurzaam is opgeslagen;
2. de bron en aanleiding zichtbaar zijn;
3. precies één module eigenaar en schrijver van het object is;
4. de publieke versie en herkomst expliciet zijn;
5. een volgende module de informatie read-only kan ophalen en alleen via een command een geldige
   statusovergang aan de eigenaar kan vragen;
6. het producerende proces niet hoeft te blijven draaien om de informatie te behouden.

Een overdracht kan ook teruggaan. Als Kwaliteitsbewaking meerdere verwante bugs ziet, registreert zij
via de productmodule een `UserSignal` met categorie `QUALITY_PATTERN`. Zo ontstaat terugkoppeling
zonder gedeeld schrijverschap.

## Database en frontend

De database is de volledige productwaarheid. De frontend haalt actuele én historische versies via
read-only application-API's uit de database en maakt droombeelden, epics, UX, stories, backlog,
bugs, verificaties, signalen, besluiten, sessies en leveringen voor mensen leesbaar. Waar dat waarde
heeft kan de frontend versies naast elkaar zetten en verschillen tonen.

De frontend is daarmee de human-readable weergave van de volledige productwaarheid. Correcties lopen
via de application service van de module die eigenaar is; de frontend schrijft nooit rechtstreeks in
procestabellen.

## Read-only productrepository

De productdatabase is de waarheid over richting, epics, stories, signalen, bugs, verificaties en
besluiten. De bestaande productrepository is de waarheid over de huidige code, tests en
productdocumentatie. `ProductAssignment` bewaart daarvoor alleen de publieke Git-URL.

Productontwerp, Productplanning en Kwaliteitsbewaking mogen die URL tijdens een processessie gewoon
uitchecken en de repository read-only onderzoeken:

- Productontwerp gebruikt code en documentatie om het huidige product te begrijpen en geen bestaand
  gedrag opnieuw te ontwerpen;
- Productplanning gebruikt ze om stories realistisch te snijden en afhankelijkheden te herkennen;
- Kwaliteitsbewaking gebruikt ze om regressierisico's en relevante tests te vinden, maar beschouwt
  code nooit als bewijs dat het gedeployde gedrag werkt.

Hiervoor komt geen `product-factory-workspace`, Git-module, schrijfrepository of synchronisatiedatabase.
De processen committen en pushen nooit. De bij checkout gevonden commit-SHA mag als eenvoudige
bronverwijzing op `ProcessSession`, `Epic`, `Story` of `Verification` worden bewaard. Software Factory
krijgt nog steeds een zelfstandige story met alle product- en UX-inhoud.

## Geheugen op drie niveaus

Geheugen zorgt voor continuïteit, maar mag geen verborgen tweede productwaarheid worden. Daarom heeft
iedere geheugenlaag een eigen doel en gezag.

### 1. Agentgeheugen

Iedere langlevende agent heeft een eigen geheugen. Daarin bewaart hij kennis die bij zijn identiteit
en rol hoort, bijvoorbeeld:

- terugkerende observaties en patronen die hij zelf heeft gezien;
- werkwijzen die voor zijn taak goed of juist slecht bleken te werken;
- persoonlijke aandachtspunten en hypotheses voor een volgende uitvoering;
- lessen uit feedback op zijn eerdere bijdragen;
- onderwerpen die hij later opnieuw wil controleren.

Agentgeheugen is persoonlijk en adviserend. Een agent mag niet in zijn eigen geheugen vastleggen dat
een productbesluit is genomen of dat een epic van status is veranderd. Daarvoor moet hij het gedeelde
productobject bijwerken. Een andere agent hoeft persoonlijk geheugen niet automatisch te vertrouwen
of te ontvangen.

### 2. Procesgeheugen

Procesgeheugen is gedeeld door alle agents die hetzelfde proces uitvoeren. Het bevat kennis die nodig
is om het proces consequent voort te zetten, bijvoorbeeld:

- de actuele onderzoeksaanpak, bronspreiding, epic-klaarheidsproblemen en bruikbare
  vergelijkingsprincipes van Productontwerp;
- storyvorming, prioriteringspatronen, capaciteitsafspraken en terugkerende blokkades van
  Productplanning;
- teststrategie, testrotatie, risicogebieden en dekkingsgaten van Kwaliteitsbewaking.

Procesgeheugen bewaart ervaring over **hoe** het proces goed wordt uitgevoerd. De actuele status van
een epic, story, bug of testsessie blijft op dat productobject staan en wordt niet naar het
procesgeheugen gekopieerd.

### 3. Productgeheugen

Productgeheugen bevat feiten en richting die voor meerdere processen of agents als gedeelde context
gelden:

- Stakeholderrichting en correcties;
- actuele productregels;
- relevante feiten en technische grenzen;
- samenvattingen van afgesloten overleggen;
- de herkomst, geldigheid en reikwijdte van ieder kennisitem.

Betekenisvolle keuzes staan niet als vrije geheugenregel in Productgeheugen, maar als
`DecisionRecord` in het Besluitenregister. Een intern leerresultaat wordt pas gedeelde
productwaarheid wanneer het leidt tot een concreet publiek productobject, een productregel of een
geregistreerd besluit.

Binnen zijn toepassingsgebied heeft een expliciete, actuele richting van de Stakeholder meer gezag
dan een interpretatie in proces- of agentgeheugen. Een onderzoeksinzicht wordt niet automatisch een
productregel en een overleguitspraak wordt pas bindend wanneer zij als expliciet besluit of
Stakeholderrichting is vastgelegd.

### Geheugen blijft corrigeerbaar

Een geheugenitem kan **Concept**, **Actief**, **Vervangen**, **Ingetrokken** of **Historisch** zijn.
Normale agenttaken krijgen alleen actieve, relevante items. Vervangen of ingetrokken kennis blijft
beschikbaar voor herkomst en reconstructie, maar wordt niet stilzwijgend als actuele instructie
gebruikt.

Iedere geheugenwijziging vermeldt:

- wie of welk proces de wijziging voorstelde;
- op welke bron of ervaring zij is gebaseerd;
- voor welke agent, welk proces of welk product zij geldt;
- waarom zij is toegevoegd, vervangen of ingetrokken;
- of bevestiging van de Stakeholder nodig was.

## Overleggen met de Stakeholder

Een overleg maakt de Stakeholder onderdeel van het systeem zonder hem iedere dagelijkse keuze te
laten goedkeuren. De Stakeholder kan op ieder moment een overleg starten. Een agent of proces kan een
overleg aanvragen wanneer menselijke richting of een moeilijk omkeerbare beslissing nodig is.

### Wie neemt deel

Ieder overleg heeft:

- de Stakeholder;
- één agent die verantwoordelijk is voor het gesprek en de afronding;
- de agent of het proces dat het overleg heeft aangevraagd;
- alleen de overige agents die inhoudelijk nodig zijn.

Een overleg kan bij één proces horen of meerdere processen verbinden. Een epic-overleg kan
bijvoorbeeld de onderzoeker, epicverantwoordelijke, planner en tester samenbrengen. De Stakeholder hoeft
niet zelf te weten welke technische agentrun op de achtergrond actief is; wel is zichtbaar vanuit
welke rol een advies of vraag komt.

### Input en status van een overleg

Een overleg begint met een korte agenda en verwijzingen naar de betrokken productobjecten. De input
kan bestaan uit:

- vragen of onderwerpen van de Stakeholder;
- een overlegverzoek van een agent of proces;
- relevante epics, stories, bugs, testsessies of onderzoeksdossiers;
- eerdere besluiten en actieve geheugenitems;
- concrete keuzemogelijkheden met hun gevolgen.

Een overleg heeft de status **Aangevraagd**, **Open** of **Afgesloten**. Berichten en geraadpleegde
bronnen blijven bij het overleg bewaard. Een open overleg blokkeert alleen het betrokken werk wanneer
expliciet is vastgelegd dat menselijke input noodzakelijk is.

### Output en doorwerking

Bij het afsluiten ontstaan leesbare notulen met:

- de besproken onderwerpen;
- richting of correcties van de Stakeholder;
- genomen besluiten en afgewezen alternatieven;
- open vragen;
- acties met een eigenaar;
- de productobjecten en geheugenitems die zijn toegevoegd of aangepast.

Een transcript of samenvatting verandert niet vanzelf de roadmap of het productgeheugen. Iedere
doorwerking is een expliciete, controleerbare wijziging. Een overleg kan zo tegelijk een nog niet
gekozen epic in Productontwerp bijsturen, een prioriteitsgrens voor Productplanning vastleggen en een
gebruikerssignaal voor Kwaliteitsbewaking opleveren.

Voor Kwaliteitsbewaking kan een overleg een `UserSignal` opleveren wanneer de Stakeholder meldt
dat iets mogelijk niet goed werkt of nadrukkelijk onderzocht moet worden. Zo'n melding kan worden
gecategoriseerd als `QUALITY_CONCERN` en bevat de oorspronkelijke observatie, het betrokken
productgebied, gewenste aandacht, urgentie, context, eventueel bewijs en het bronoverleg-ID. Het is
nog geen bewezen bug en schrijft het testresultaat niet voor. Na registratie roept de
product-/overlegmodule `requestSignalInvestigation(...)` aan; dat zet alleen een gericht workitem in
de kwaliteitsqueue.

`StakeholderDirection` blijft gereserveerd voor echte productrichting: een bindende grens,
correctie, stopbesluit of wijziging van de opdracht binnen het mandaat. Extra aandacht vragen voor
een mogelijk kwaliteitsprobleem is een signaal en geen aparte richtingsoort.

## Productontwerp als black box

**Doel:** zelfstandig onderzoeken hoe het product beter kan worden, de verre richting onderhouden en
complete, behapbare epicdefinities met UX publiceren. Productontwerp maakt geen stories.

Onderzoek, bewijs, kansvoorstellen, UX-verkenning, technische verkenning en epicvorming zijn interne
onderdelen van dezelfde module. Andere modules zien alleen het gekozen epicresultaat.

**Uitvoering:** de scheduler of een bevoegde handmatige UI-/REST-actie roept de agentgestuurde
`runProcessSession()` aan. De module kiest zelf het product en de interne onderzoeks- of epictaak.
Een tweede gelijktijdige run wordt geweigerd. Publieke commands veranderen uitsluitend een geldige
epicstatus; ze starten geen ontwerpwerk. Productontwerp heeft geen werkqueue.

### Inputinterface

| Gegeven | Eigenaar en herkomst | Betekenis voor Productontwerp |
|---|---|---|
| Stakeholderprofiel | product-/overlegmodule | wie richting mag geven, hoe overleg plaatsvindt en waar het beslissingsmandaat eindigt |
| Productopdracht | productmodule; bevestigd door de Stakeholder | doelgroep, productdoel, harde grenzen en publieke Git-URL van het product |
| Stakeholderrichting | overleg/productmodule | actuele correcties en expliciete beslissingen |
| Stories en verificaties | Productplanning en Kwaliteitsbewaking | wat eerdere epics werkelijk hebben opgeleverd |
| Berekend kwaliteitsbeeld | Kwaliteitsbewaking-query | structurele problemen die een nieuwe epic kunnen rechtvaardigen |
| Gebruikerssignaal | productmodule | oorspronkelijke feedback plus actuele status en resultaatkoppelingen |

Externe bronnen worden tijdens een sessie opgehaald en intern als bronregistratie opgeslagen. Ruwe
bronnen, onderzoeksdossiers, hypotheses en kansvoorstellen steken de modulegrens niet over.

### Outputinterface

| Gegeven | Betekenis |
|---|---|
| Droombeeld | geversioneerd beeld van hoe het product zijn opdracht op lange termijn uitzonderlijk goed kan vervullen; zichtbaar voor de Stakeholder |
| Epic | geversioneerde, behapbare gebruikersverbetering met status, eenduidige scope, bewijs, compleet UX-ontwerp, risico's en succescriteria |
| Planningrequest | `requestEpicPlanning(...)` zet na publicatie alleen een `PLAN_EPIC`-werkitem bij Productplanning klaar; het start geen planner |
| Besluitregistratie | betekenisvolle ontwerpkeuze met korte onderbouwing, alternatieven, bronversies en geldigheid voor het centrale Besluitenregister |

De enige inhoudelijke overdracht van Productontwerp naar Productplanning is de epic. Het
droombeeld is zichtbare productrichting, geen uitvoerbaar werk. Leerresultaten en onderzoeksdossiers
blijven intern bij Productontwerp; alleen hun concrete publieke gevolg en eventuele besluitregistratie
gaan over de modulegrens.

Productontwerp mag een epic herzien zolang Productplanning hem niet heeft gekozen. Een gekozen
epicversie is bevroren en wordt nooit stilletjes aangepast. De interne werking staat in
[Productontwerp](productontwerp.md).

## Productplanning als black box

**Doel:** gequeue'de epics, bugs, epicgaten en prioriteitswijzigingen verwerken, exacte epicversies
bevriezen, volledige epics in productstories verdelen en alle niet-afgeronde stories productbreed
ordenen. Een story is een productstory of bugfixstory.

**Uitvoering:** scheduler of bevoegde bediening roept `runProcessSession()` aan. Maximaal één run
claimt een vaste batch `PlanningWorkItem`s en mag agents starten. Een lege queue is een normale
no-op. De sessie maakt en ordent stories, maar verstuurt niets naar Software Factory. De dispatcher
gebruikt deterministische storycommands voor verzending en oplevering.

### Inputinterface

| Gegeven | Eigenaar en herkomst | Betekenis voor Productplanning |
|---|---|---|
| Stakeholderprofiel | product-/overlegmodule | geldig beslissingsmandaat en contactcontext achter Stakeholderrichting |
| Planningsopdracht | Productplanning, aangevraagd door Productontwerp, Kwaliteitsbewaking, product-/overlegmodule of bediening | duurzaam werk van type `PLAN_EPIC`, `PLAN_BUGFIX`, `PLAN_EPIC_GAP`, `REPRIORITIZE_EPIC` of `MANUAL_REPLAN` |
| Beschikbare epic | Productontwerp | exacte bron voor `PLAN_EPIC` en later via `claimEpicForPlanning(...)` bevroren |
| Uitvoerbare bug of epicverificatie | Kwaliteitsbewaking | bewijsbron voor een bugfix of ontbrekende epicdekking |
| Productopdracht en Stakeholderrichting | productmodule | grenzen en expliciete prioriteitsaanwijzingen |
| Story met leveringsvelden | Productplanning zelf, bijgewerkt via dispatchercommands | of een eerder verzonden item nog open, opgeleverd of geblokkeerd is |
| Verificatie | Kwaliteitsbewaking | of werk goed is en of de hele epic geslaagd kan worden afgesloten |

### Outputinterface

| Gegeven | Betekenis |
|---|---|
| Story | complete productstory of bugfixstory met `sequenceNumber`, status `TODO`, `IN_PROGRESS` of `DONE`, acceptatiecriteria en relevante UX en assets |
| Backlog | berekende lijst van alle stories die niet `DONE` zijn, geordend op `sequenceNumber`; geen aparte entiteit |
| Planningsopdrachtstatus | read-only overzicht van type, bron, status, claim, resultaat of fout van ieder `PlanningWorkItem` |
| Besluitregistratie | betekenisvolle epic-, prioriteits- of afsluitkeuze voor het centrale Besluitenregister |

Er geldt geen backloglimiet of streefgetal. Een epic wordt in zo veel stories verdeeld als haar
scope vraagt. Meerdere epics mogen tegelijk actief zijn en hun `TODO`-stories mogen in de globale
volgorde door elkaar staan. Een Stakeholder kan via een vastgelegde richting een urgente epic
voorrang geven; een `IN_PROGRESS`-story loopt normaal door.

Productplanning is de enige schrijver van `Story`, story-inhoud en `sequenceNumber`. Zij vraagt
epicstatusovergangen via commands aan Productontwerp. De dispatcher beheert geen story rechtstreeks,
maar gebruikt `markStoryAsDispatched(...)`, `markStoryAsDeveloped(...)` en
`recordDispatchFailure(...)`.
De interne werking en de dispatcher staan in
[Productplanning](productplanning.md).

## Kwaliteitsbewaking als black box

**Doel:** de werkende applicatie voortdurend onderzoeken, losse opleveringen verifiëren en na de
laatste story vaststellen of de complete bevroren epic de bedoelde gebruikersverbetering bereikt.

**Uitvoering:** scheduler of bevoegde bediening roept `runProcessSession()` aan. Maximaal één run
claimt een vaste batch `QualityWorkItem`s en mag testagents starten. Queuecommands zoals
`requestEpicVerification(...)` slaan alleen een opdracht op en starten geen test.

### Inputinterface

| Gegeven | Eigenaar en herkomst | Betekenis voor Kwaliteitsbewaking |
|---|---|---|
| Stakeholderprofiel | product-/overlegmodule | wie kwaliteitsgrenzen en gemelde risico's bevoegd mag verduidelijken |
| Testopdracht | Kwaliteitsbewaking, aangevraagd door Productplanning of product-/overlegmodule | gericht werk van type `VERIFY_STORY`, `VERIFY_EPIC`, `RETEST_BUGFIX` of `INVESTIGATE_USER_SIGNAL` |
| Productopdracht | productmodule | productgrenzen en publieke Git-URL voor read-only code, tests en documentatie |
| Stakeholderrichting | overleg/productmodule | bindende productgrens, correctie, stopbesluit of opdrachtwijziging; een kwaliteitszorg is een gebruikerssignaal |
| Testbare productconfiguratie | productmodule | URL's, toegestane accounts, routes en testgrenzen |
| Story met oplevering en externe referentie | Productplanning | wat nieuw of gewijzigd is en waar het getest kan worden |
| Bevroren epic en UX | Productontwerp | scope, complete gebruikersroute, succescriteria en wanneer epiccontrole nodig is |
| Stories | Productplanning | verwacht gedrag en storytype |
| Bestaande bugs | Kwaliteitsbewaking zelf | wat moet worden hergetest en welke patronen al bekend zijn |
| Gebruikerssignaal | productmodule | oorspronkelijke melding, context, bewijs, status en resultaatkoppelingen |

### Outputinterface

| Gegeven | Betekenis |
|---|---|
| Bug | reproduceerbare afwijking met verwacht en werkelijk gedrag, bewijs, impact, ernst en herstelstatus |
| Verificatie | onveranderlijk oordeel met bewijs over een story, epic of gebruikerssignaal; kan ontbrekende epicdekking bevatten |
| Kwaliteitsbeeld | berekende samenvatting van dekking, belangrijke risico's en recent onderzochte gebieden |
| Testopdrachtstatus | read-only overzicht van type, doelversie, status, claim, resultaat of fout van ieder `QualityWorkItem` |

Kwaliteitsbewaking maakt geen stories en bepaalt geen backlogpositie. Zij vraagt bugfixes en
aanvullend werk via commands aan Productplanning en geeft epic- en signaaluitkomsten via commands aan
hun eigenaar door. De interne werking staat in
[Kwaliteitsbewaking](kwaliteitsbewaking.md).

## Hoe de drie processen elkaar in beweging houden

Agentgestuurde processessies vormen geen synchrone keten. De overdracht werkt zo:

- Productontwerp publiceert een complete epic en queue't met `requestEpicPlanning(...)` later
  planwerk;
- Productplanning verwerkt `PlanningWorkItem`s en publiceert alle benodigde stories;
- de dispatcher meldt een oplevering met `markStoryAsDeveloped(...)`;
- Productplanning zet de story direct `DONE`, queue't storyverificatie of een bugfix-hertest en
  controleert zonder agent of alle stories van die epic klaar zijn;
- zo ja, zet zij de epic op **Controleren** en queue't via `requestEpicVerification(...)` een
  kwaliteitsopdracht;
- Kwaliteitsbewaking verwerkt die opdracht in een latere run;
- alleen bij een bouwfout of ontbrekende epicdekking queue't zij via `requestBugfix(...)` of
  `requestEpicGapPlanning(...)` nieuw planwerk.

De backlog mag leeg zijn. Dat start niets en is geen fout. Zodra Productontwerp later een nieuwe
epic publiceert, ontstaat vanzelf weer een planningsopdracht. Iedere module kan zonder wijziging
eindigen wanneer er geen nuttig werk is.

## Hoe de roadmap werkt

De roadmap is alleen voor epics. Losse ideeën, bugs en productstories staan er niet tussen.

De roadmap heeft vier eenvoudige vakken:

- **Nu** — de actieve epics, met één normale hoofdfocus en eventuele handmatig urgente epic;
- **Hierna** — een kleine geordende lijst met klaarliggende epics;
- **Later** — interessante epic-kandidaten die nog niet klaar zijn;
- **Niet gekozen** — epics die bewust zijn gestopt of uitgesteld, met de reden erbij.

Er zijn geen schijnzekere datums voor werk dat nog niet goed begrepen is. Alleen werk dat echt is
ingepland krijgt een verwachte periode.

De volgorde komt vooral uit:

- waarde voor de gebruiker;
- bijdrage aan het productdoel en droombeeld;
- beschikbaar bewijs;
- risico en onzekerheid;
- afhankelijkheden;
- hoeveelheid werk.

Productplanning legt de afweging uit en kiest een exact epic-ID en versienummer. Vanaf dat moment is
die epicversie bevroren. Een ondoorzichtige score beslist niet zelfstandig wat er moet gebeuren.

Bugs staan naast de roadmap in **Productgezondheid**. Productplanning maakt voor uitvoerbare bugs een
bugfixstory en ordent die met productstories via `sequenceNumber`.

## Hoe ideeën worden behandeld

Een idee begint klein. Minimaal bewaren we:

- de tekst van het idee;
- waar het vandaan kwam;
- voor wie het mogelijk nuttig is;
- wanneer het is toegevoegd.

Daarna kan een idee vier kanten op:

- bewaren voor later;
- samenvoegen met een bestaand idee;
- afwijzen, met een korte reden;
- door Productontwerp uitwerken tot een kansvoorstel.

Een agent mag ideeën onderzoeken en vergelijken. De agent mag niet doen alsof een mooi geschreven
idee daarom automatisch een goed productbesluit is.

## Hoe UX-ontwerpen worden behandeld

UX is geen losse verzameling documenten. Een UX-uitwerking hoort altijd bij één epic of één duidelijk
benoemd onderdeel van het droombeeld. Een bug verwijst normaal naar het bestaande gedrag dat had
moeten werken.

Een UX-uitwerking laat minimaal zien:

- welke gebruiker iets wil bereiken;
- welke stappen die gebruiker doorloopt;
- welke hoofdschermen of toestanden nodig zijn;
- wat er gebeurt als iets leeg, langzaam of fout gaat;
- wat belangrijk is voor toegankelijkheid en privacy;
- welke vragen nog openstaan.

Per onderwerp is er precies één versie gemarkeerd als **actueel**. Oudere versies blijven terug te
vinden, maar worden niet naast de actuele versie als gelijkwaardige ontwerpen getoond.

Een UX-uitwerking voor de verre toekomst krijgt duidelijk het label **Droomconcept**. Dit concept mag
vrij en ambitieus zijn, maar niemand mag het verwarren met een gepland ontwerp. Wanneer een deel echt
gebouwd gaat worden, krijgt de bijbehorende epic een eigen, concretere UX-uitwerking.

De epicdefinitie bezit het actuele UX-ontwerp. Productplanning selecteert zonder inhoudelijke
wijziging het relevante deel en neemt daarvan een zelfstandige momentopname op in iedere story. Die
story bevat de gebruikersflow, schermen, toestanden, interacties, responsive gedrag,
toegankelijkheid en benodigde ontwerpassets. Zodra een epicversie is gekozen, wordt ook het
bijbehorende UX-ontwerp bevroren.

Tekst, Markdown, JSON en SVG worden als gewone tekstvelden overgedragen. Binaire assets zoals PNG of
JPG worden als begrensde attachments met bestandsnaam, MIME-type, grootte en SHA-256-hash verstuurd;
bij een JSON-only API mag de transportcodering Base64 zijn. Base64 is geen opslag- of
ontwerpformaat. Software Factory kopieert alle ontvangen inhoud bij acceptatie naar de eigen
storystorage en heeft daarna Product Factory niet meer nodig.

Een UX-uitwerking hoeft niet altijd mooi te zijn. Eerst moet zij duidelijk zijn. Voor een belangrijke
of onzekere gebruikersstroom kan Product Factory een klikbaar ontwerp of screenshots laten maken.
De Stakeholder kan daarop reageren voordat er wordt gebouwd.

## Eén gezamenlijke werkstroom

Productplanning maakt productstories uit een bevroren epic en bugfixstories uit bugs. Samen vormen
alle niet-afgeronde stories de berekende backlog:

```text
Productontwerp ─→ PLAN_EPIC ───────────────┐
                                           ▼
Kwaliteitsbewaking ─→ BUG / EPIC_GAP ─→ planningsqueue ─→ Productplanning
                                                               │
                                                               ▼
                                                Story[] op sequenceNumber
                                                               │
                                                               ▼
                                               Software Factory-dispatcher
                                                               │
                                                               ▼
                                                      Software Factory
                                                               │ ontwikkeld
                                                               ▼
                                      story DONE → VERIFY_EPIC in kwaliteitsqueue
```

Productontwerp zet na epicpublicatie eerst een `PLAN_EPIC`-opdracht in de planningsqueue.
Productplanning maakt tijdens een latere run de volledige storyset voor die epic. De dispatcher
synchroniseert vervolgens eerder verstuurd werk. Alleen wanneer Software Factory voor het
product geen openstaande story heeft, verstuurt hij precies de `TODO`-story met het laagste
`sequenceNumber` als volledig `StoryDeliveryPackage`. Hij kan geen story overslaan, de volgorde
wijzigen of UX aanvullen. Hij gebruikt alleen de publieke storycommands van Productplanning.
Statusovergangen worden direct verwerkt; inhoudelijk plan- en testwerk wacht in de juiste queue op
een volgende geplande of handmatige processessie.

## Wanneer een epic klaar is

Alle stories op `DONE` betekent nog niet automatisch dat de epic geslaagd is. Productplanning roept
`markEpicReadyForVerification(...)` op Productontwerp aan. Dat zet de epic alleen op
**Controleren** (`VERIFYING`). Daarna roept Productplanning
`requestEpicVerification(...)` op Kwaliteitsbewaking aan. Dit snelle command start geen agent maar
plaatst een `VERIFY_EPIC`-opdracht in de kwaliteitsqueue. Tijdens een latere
`runProcessSession()` controleert Kwaliteitsbewaking het geheel tegen de bevroren scope, het
UX-ontwerp en de succescriteria.

Kwaliteitsbewaking publiceert één van deze uitkomsten:

- **Geslaagd** — de complete gebruikersverbetering is aantoonbaar bereikt;
- **Onvolledig** — gedrag binnen de bevroren epic ontbreekt;
- **Niet aantoonbaar** — alles lijkt geleverd, maar er is nog onvoldoende bewijs;
- **Geblokkeerd** — de controle kan door een omgeving, toegang of andere externe reden niet worden
  afgerond;
- **Niet geslaagd** — alles werkt zoals ontworpen, maar het bedoelde gebruikersresultaat is niet
  bereikt.

Kwaliteitsbewaking bewaart het oordeel als `Verification`; Productontwerp is de enige schrijver van
de epicstatus en verwerkt het via `recordEpicVerification(...)`:

- bij **Geslaagd** sluit Productontwerp de epic als **Geslaagd** af;
- bij een echte bouwfout maakt Kwaliteitsbewaking een bug en vraagt zij Productplanning via
  `requestBugfix(...)` om een nieuwe bugfixstory;
- bij een dekkingsgat vraagt Kwaliteitsbewaking via `requestEpicGapPlanning(...)` aanvullende stories
  aan binnen dezelfde bevroren epic;
- in beide herstelgevallen gaat de epic terug naar **Actief** en wordt zij na oplevering opnieuw gecontroleerd;
- bij **Niet aantoonbaar** of **Geblokkeerd** blijft de epic op **Controleren** en plant
  Kwaliteitsbewaking later nieuw bewijs- of testwerk;
- bij **Niet geslaagd** registreert Productontwerp die uitkomst en verwerkt de conclusie intern;
- een wens buiten scope of onjuiste productaanname kan later via Productontwerp een nieuwe
  vervolgepic worden; de bevroren epic wordt niet herschreven.

Kwaliteitsbewaking schrijft dus geen stories of epics. Zij levert het inhoudelijke bewijs en vraagt
de eigenaar via een command om het vervolg; de eigenaar valideert en schrijft zijn eigen entiteit.

## Wat de Stakeholder doet en wat agents doen

Agents helpen met:

- informatie samenvatten;
- dubbele signalen vinden;
- op eigen initiatief onderzoek doen naar soortgelijke producten en aangrenzende oplossingen;
- een ambitieus droombeeld maken en met nieuwe kennis bijwerken;
- epic-kandidaten maken, onderzoeken en vergelijken;
- beoordelen of een epic klaar is;
- het volledige UX-ontwerp van een epic uitwerken;
- een exacte epicversie kiezen en bevriezen;
- de gekozen epic in productstories verdelen;
- als planner volledige epics in zelfstandig uitvoerbare stories verdelen en alle `TODO`-stories
  productbreed prioriteren;
- als tester dagelijks en na opleveringen de applicatie onderzoeken;
- losse opleveringen en complete epics beoordelen;
- ontbrekende informatie aanwijzen.

De Stakeholder blijft verantwoordelijk voor:

- een herkenbaar Stakeholderprofiel en bereikbaar contactkanaal;
- het brede productdoel en de harde grenzen;
- het corrigeren van de richting wanneer Product Factory het doel verkeerd begrijpt;
- onomkeerbare of kostbare beslissingen;
- gevoelige gegevens en externe toegang.

De Stakeholder kan daarnaast gebruikerssignalen, risico's, vragen en richting leveren, maar hoeft
geen oplossing te ontwerpen. De Stakeholder schrijft geen epic, story, bug, verificatie of
backlogpositie en sluit een epic niet administratief af.

Product Factory is binnen die opdracht verantwoordelijk voor het actuele droombeeld, onderzoek, het
epicportfolio, de epicvolgorde, de backlogkeuzes, het testproces en de balans tussen Verbeteren en
Vernieuwen. Zij legt deze keuzes uit en maakt ze zichtbaar, zodat de Stakeholder kan ingrijpen zonder
ieder stapje te hoeven besturen.

De Stakeholder en agents ontmoeten elkaar in overleggen. Agents mogen zelf een overleg aanvragen met
een korte agenda en concrete reden. De Stakeholder kan in een overleg vragen stellen, richting geven,
een keuze corrigeren of nieuw onderzoek laten starten. De uitkomst wordt via de normale
productobjecten en geheugenlagen verwerkt; zij blijft niet alleen in het gesprek staan.

De interface toont vooral de uitkomst en de reden. Namen van agents, prompts, JSON en technische
tussenstappen horen in een technisch logboek, niet in het gewone productscherm.

## Wanneer Product Factory om hulp vraagt

Product Factory probeert gewone, veilige keuzes zelf voor te bereiden. Zij vraagt hulp als:

- twee productrichtingen allebei mogelijk zijn en de keuze veel invloed heeft;
- er geld moet worden uitgegeven;
- er een account, token of toestemming van buiten nodig is;
- een keuze moeilijk terug te draaien is;
- de beschikbare informatie elkaar tegenspreekt;
- een voorgestelde stap het productdoel of de harde grenzen zou veranderen.

De vraag wordt als overlegverzoek aan de Stakeholder aangeboden. Zij moet kort zijn en altijd
mogelijke keuzes met gevolgen tonen. Een overleg wordt niet gebruikt als veilige standaarduitweg
voor iedere onzekerheid: de agents blijven verantwoordelijk voor gewone, omkeerbare keuzes binnen
hun mandaat.

## De belangrijkste schermen

Versie 2 begint met hooguit vier hoofdschermen.

### 1. Product

Hier zie je in één oogopslag:

- het productdoel en een korte versie van het droombeeld;
- welke epic actief is en waarom;
- welke `TODO`-story het laagste `sequenceNumber` heeft en waarom;
- hoeveel `TODO`-stories nog klaarstaan;
- wat bij Software Factory bezig is;
- welke belangrijke bugs openstaan;
- wanneer en wat de tester voor het laatst heeft getest;
- wat we recent hebben geleerd;
- wat recent onderzoek heeft veranderd;
- of ergens hulp nodig is;
- welke overleggen zijn aangevraagd of openstaan;
- welke recente Stakeholderrichting de drie processen beïnvloedt.
- welke gebruikerssignalen nieuw, onderzocht, gekoppeld of afgesloten zijn.

### 2. Inbox

Hier staan nieuwe feedback, observaties, ideeën en onderzoeksinzichten. Per gebruikerssignaal blijven
de oorspronkelijke tekst, bron, context en bijlagen zichtbaar, samen met de actuele status en
koppelingen naar verificatie, besluit, epic of bug. Je kunt
signalen bekijken en een onderzoeksverzoek indienen. De productmodule registreert het signaal en
werkt status en koppelingen bij via haar commands; het Inbox-scherm schrijft
niet rechtstreeks in de database. Bugs komen vanuit Kwaliteitsbewaking in de aparte
productgezondheidslijst.

### 3. Plan

Hier staan drie onderdelen bij elkaar:

- het droombeeld als verre richting;
- de epic-roadmap met Nu, Hierna en Later;
- Productgezondheid met bugs;
- de berekende backlog met product- en bugfixstories op `sequenceNumber`.

Ook is zichtbaar welke epic wordt onderzocht, waarom epics wel of niet klaar zijn en hoe Product
Factory de klaarliggende epics heeft geordend.

### 4. Detail

Een epic, story of bug heeft één rustige detailpagina met:

- probleem en gewenste uitkomst;
- bewijs en open vragen;
- actuele UX-uitwerking, als die nodig is;
- eventuele bovenliggende epic en relevante storyrelaties;
- reden van de backlogprioriteit;
- voortgang en resultaat;
- geldende en historische besluiten met hun onderbouwing en geldigheidsperiode.

Technische details zijn beschikbaar via een aparte knop, maar staan standaard dicht.

Overleggen en geheugen hoeven geen extra hoofdscherm te worden. Vanuit Product en ieder detail kan de
Stakeholder een overleg openen of starten. Een aparte secundaire weergave toont alle overleggen en de
geschiedenis van agent-, proces- en productgeheugen, inclusief vervangen en ingetrokken items.

## Eenvoudige statussen voor epics en stories

Productontwerp beheert de epic; Productplanning beheert stories. Een `Epic` gebruikt:

- **Beschikbaar** — Productplanning mag deze complete versie kiezen;
- **In planning** — een exact epic-ID en versienummer zijn geclaimd en bevroren;
- **Actief** — één of meer stories worden uitgevoerd;
- **Controleren** — alle geplande stories zijn klaar en Kwaliteitsbewaking controleert het geheel;
- **Geslaagd** — de bedoelde gebruikersverbetering is bewezen;
- **Niet geslaagd** — alles is geleverd, maar het gebruikersresultaat is niet bereikt;
- **Gestopt** — bewust niet verder, met een zichtbare reden.

Stories gebruiken precies drie statussen:

- `TODO` — compleet, geprioriteerd en nog niet naar Software Factory gestuurd;
- `IN_PROGRESS` — naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory opgeleverd.

De backlog is de query op stories die niet `DONE` zijn. Actuele externe velden staan op `Story` en
retryhistorie in `DeliveryAttempt`; inhoudelijk testbewijs staat in `Verification`. Een bug houdt
daarnaast zijn eigen herstelstatus in Kwaliteitsbewaking.

## Regels die versie 2 eenvoudig houden

1. Eén plek bevat de actuele productwaarheid.
2. Een los idee is nog geen roadmapitem.
3. Product Factory wacht niet alleen op invoer, maar zoekt zelf naar kansen en bedreigingen.
4. Het droombeeld mag onhaalbaar lijken; de eerstvolgende epic-slice moet wel klein en toetsbaar zijn.
5. De roadmap bevat epics, geen losse stories, bugs of ideeën.
6. Iedere productstory hoort bij precies één gekozen, bevroren epicversie; iedere bugfixstory bij
   precies één bugversie en indien relevant ook bij de betrokken epicversie.
7. Productontwerp maakt geen stories; alleen Productplanning doet dat na epicselectie.
8. De backlog is geen entiteit, maar de geordende lijst product- en bugfixstories die niet `DONE` zijn.
9. Productplanning verwerkt een complete epic in zo veel stories als nodig en bepaalt productbreed
   hun `sequenceNumber`.
10. De tester levert bewijs en ernst, maar bepaalt niet de uitvoeringsvolgorde.
11. Ieder UX-ontwerp hoort bij één epic of benoemd droomconcept.
12. Een droomconcept is nooit stilletjes een gepland ontwerp.
13. Per onderwerp is maar één UX-versie actueel.
14. Iedere naar Software Factory verzonden story bevat zelfstandig alle relevante UX en assets.
15. We bouwen kleine stappen en leren na iedere oplevering.
16. Er is weinig werk tegelijk bezig; meerdere epics mogen wel actief zijn en de Stakeholder kan
    een urgente epic handmatig voorrang geven.
17. Kritieke fouten gaan voor, maar onderhoud verdringt vernieuwing niet stilletjes.
18. Agents nemen gewone omkeerbare productbesluiten en leggen die uit.
19. De Stakeholder kan richting geven en bijsturen zonder de dagelijkse planner te worden.
20. Iedere langlevende agent heeft eigen geheugen; ieder proces heeft gedeeld procesgeheugen.
21. Agent- en procesgeheugen zijn nooit de enige bron van productwaarheid.
22. Een overleguitkomst werkt alleen door via expliciete besluiten, acties of geheugenwijzigingen.
23. Een verborgen score neemt geen groot of onomkeerbaar productbesluit.
24. Het gewone scherm toont producttaal, geen interne agent- of databasetaal.
25. Nieuwe functies komen alleen in Product Factory als ze een hoofdvraag aantoonbaar eenvoudiger
    maken.
26. Iedere intelligente procesmodule heeft alleen `runProcessSession()` als agentgestuurde ingang;
    de dispatcher is een afzonderlijke technische adapter zonder productlogica.
27. Iedere duurzame entiteit heeft precies één schrijvende module.
28. Andere modules lezen via read-only queries en vragen wijzigingen alleen via betekenisvolle
    commands aan de eigenaar; algemene setters en repositorytoegang zijn verboden.
29. De dispatcher neemt geen productbesluiten en verstuurt alleen de eerste `TODO`-story op `sequenceNumber`.
30. Een lege backlog is geldig en triggert geen ontwerp- of planningsrun.
31. Productontwerp mag een niet-gekozen epic herzien, maar nooit een gekozen epicversie wijzigen.
32. Alle stories afgerond is niet hetzelfde als een geslaagde epic.
33. Kwaliteitsbewaking maakt bugs en verificaties, maar geen stories of aparte epicgaten.
34. Alleen Productontwerp verandert de epicstatus en sluit haar na inhoudelijke verificatie af.
35. Interne leerresultaten blijven bij Productontwerp; betekenisvolle keuzes staan met begin-,
    eind- en vervangingsrelaties in het Besluitenregister.
36. Alleen `runProcessSession()` mag agents starten; queuecommands slaan alleen duurzaam werk op.
37. Productplanning en Kwaliteitsbewaking hebben elk hun eigen queue en verwerken per run een vaste
    batch; Productontwerp heeft bewust geen queue.
38. Per procesmodule kan maximaal één intelligente run tegelijk actief zijn.

## Wat we uit versie 1 willen behouden

De tweede versie hoeft niet alles opnieuw uit te vinden. Waardevolle lessen en onderdelen zijn:

- een product kan als gegevens worden toegevoegd, zonder nieuwe productcode in Product Factory;
- Product Factory en Software Factory hebben ieder een duidelijke taak;
- weinig werk tegelijk geeft rust en maakt prioriteiten echt;
- een duidelijke, productbreed geordende backlog laat Software Factory zonder productbesluit het
  eerstvolgende beschikbare werk oppakken;
- beslissingen en resultaten moeten terug te vinden zijn;
- opgeleverd werk moet nieuwe productkennis opleveren;
- tokens en andere geheimen horen niet in gewone productgegevens;
- agents moeten begrensd en controleerbaar werken;
- de Stakeholder en het product kunnen allebei een overleg starten of aanvragen;
- overlegnotulen, geraadpleegde bronnen en geheugenwijzigingen blijven terug te vinden;
- geheugen kan worden vervangen of ingetrokken zonder de geschiedenis te wissen;
- agents houden een eigen herkenbare identiteit en geheugen over meerdere uitvoeringen;
- de zelfstandige agentworker kan een nuttige technische basis blijven.

We nemen deze onderdelen pas over nadat is vastgesteld dat ze in het eenvoudige v2-proces passen.

## Wat niet automatisch meegaat naar versie 2

We nemen niet standaard mee:

- shadow-iteraties als zichtbaar productbegrip;
- meerdere concurrerende manieren om een roadmap te maken;
- periodiek nieuwe stories maken zonder duidelijke epic, bug, epicgat of productkeuze;
- het oude epicmodel met capabilities, horizons, meerdere ranks en scores tegelijk;
- agents als hoofdnavigatie;
- ruwe agentuitvoer als normaal productdocument;
- losse UX-documenten zonder eigenaar of actuele status;
- alle bestaande databasevelden en historie;
- ieder scherm of iedere functie uit het huidige dashboard.

Ieder onderdeel uit versie 1 moet opnieuw bewijzen dat het nodig is.

## De kleinste bruikbare versie

De eerste bruikbare versie moet meteen bewijzen dat de drie processen samen werken. Zij hoeft nog
niet iedere mogelijke bron of vorm van automatisering te ondersteunen.

### Minimaal voor Productontwerp

1. een product met Stakeholderprofiel, breed productdoel en harde grenzen vastleggen;
2. zelf onderzoek naar soortgelijke en aangrenzende producten starten;
3. bronnen en bruikbare onderzoeksinzichten bewaren;
4. een ambitieus droombeeld maken en met nieuw bewijs aanpassen;
5. intern kansen onderzoeken en tot behapbare epics uitwerken;
6. per epic een eenduidige scope en volledig actueel UX-ontwerp maken;
7. vroeg technische haalbaarheidsinformatie gebruiken;
8. zichtbaar beoordelen of een epic klaar is;
9. nieuwe epicversies publiceren zolang de epic niet is gekozen;
10. na publicatie idempotent `requestEpicPlanning(...)` aanroepen;
11. geen stories maken en geen gekozen epicversie wijzigen.

### Minimaal voor Productplanning

1. duurzame planningsopdrachten voor epics, bugs, dekkingsgaten en handmatige prioriteit verwerken;
2. iedere gekozen epic via `claimEpicForPlanning(...)` bevriezen en volledig in kleine, testbare en zelfstandige productstories verdelen, met relevante UX en
   ontwerpassets in iedere story;
3. productstories, bugs en dekkingsgaten uit verificaties verwerken zonder de epic te wijzigen;
4. alle niet-afgeronde stories met een uitlegbaar productbreed `sequenceNumber` onderhouden;
5. meerdere actieve epics en een handmatige urgente epic kunnen ordenen;
6. na storyoplevering zonder agent bepalen of de betreffende epic klaar is voor verificatie;
7. via een `QualityWorkItem` epicverificatie aanvragen;
8. Productontwerp een epic alleen als **Geslaagd** laten afsluiten na een geslaagde verificatie;
9. via de eenvoudige dispatcher precies één story tegelijk naar Software Factory sturen.

### Minimaal voor Kwaliteitsbewaking

1. iedere dag en na een oplevering een gerichte testsessie kunnen starten;
2. belangrijke routes en wisselende onderzoeksthema's bijhouden;
3. een reproduceerbare bug met bewijs en voorgestelde ernst maken;
4. bugs als uitvoerbare input voor Productplanning publiceren;
5. na alle stories de complete epic tegen scope, UX en succescriteria controleren;
6. ontbrekende epicdekking in de epicverificatie vastleggen en aanvullend planwerk aanvragen;
7. een epic als **Geslaagd**, **Onvolledig**, **Niet aantoonbaar**, **Geblokkeerd** of **Niet geslaagd** beoordelen;
8. patronen en onjuiste productaannames als `UserSignal` met categorie `QUALITY_PATTERN` registreren;
9. gerichte testverzoeken duurzaam queueën en alleen tijdens `runProcessSession()` agents starten.

### Minimaal voor de Software Factory-dispatcher

1. op een vast ritme de status van eerder verstuurde stories synchroniseren;
2. het externe Software Factory-ID bij de story bewaren;
3. geen nieuwe story sturen zolang Software Factory voor het product nog openstaand werk heeft;
4. anders precies de `TODO`-story met het laagste `sequenceNumber` als volledig `StoryDeliveryPackage` aanmaken;
5. tekst en SVG als tekst en begrensde binaire assets als attachments overdragen;
6. geen agents, herordening of productbesluit bevatten.

### Minimaal voor de frontend

1. alle actuele en historische productentiteiten via read-only queries leesbaar tonen;
2. per gebruikerssignaal de oorspronkelijke inhoud, actuele status en resultaatkoppelingen tonen;
3. relevante versies en herkomst zichtbaar maken en waar nuttig vergelijken;
4. wijzigingen uitsluitend via betekenisvolle commands van de eigenaarsmodule laten lopen.

### Minimaal voor overleggen en geheugen

1. de Stakeholder kan vanuit een productobject een overleg starten;
2. ieder proces en iedere bevoegde agent kan met reden en onderwerpen een overleg aanvragen;
3. een overleg bewaart deelnemers, berichten, bronnen, gekoppelde objecten, status en notulen;
4. besluiten, acties en geheugenwijzigingen uit het overleg worden expliciet doorgevoerd;
5. iedere langlevende agent heeft corrigeerbaar agentgeheugen;
6. ieder proces heeft gedeeld procesgeheugen voor aanpak, continuïteit en terugkerende lessen;
7. gedeelde productwaarheid staat in productgeheugen of op het bijbehorende productobject;
8. actieve, vervangen, ingetrokken en historische kennis zijn zichtbaar van elkaar onderscheiden.

Zelf onderzoek starten, dromen, epics vormen, gequeue'd planwerk prioriteren en continu testen horen
bij de kern. Dit zijn geen uitbreidingen voor later.

## Wanneer versie 2 geslaagd is

De Stakeholder moet zonder technische uitleg binnen één minuut antwoord kunnen geven op:

1. Wat is de brede opdracht van dit product?
2. Hoe ziet Product Factory de ideale verre toekomst van dit product?
3. Wat heeft Product Factory onlangs buiten het project geleerd?
4. Welke epics zijn actief en welke heeft nu de hoofdfocus?
5. Heeft de Stakeholder een andere epic handmatig voorrang gegeven, en waarom?
6. Waarom is een epic wel of nog niet klaar?
7. Welke exacte epicversie is gekozen en sinds wanneer is zij bevroren?
8. Welke `TODO`-story heeft het laagste `sequenceNumber` en waarom?
9. Is dat een productstory of bugfixstory?
10. Bij welke epicversie hoort een story?
11. Wat heeft Kwaliteitsbewaking recent onderzocht en gevonden?
12. Wat is het actuele UX-ontwerp en is het een droomconcept of een bevroren bouwplan?
13. Wat wordt op dit moment gebouwd?
14. Wat hebben we van de laatste oplevering geleerd?
15. Is de epic alleen technisch klaar of ook aantoonbaar geslaagd?
16. Welke beslissing heeft mijn aandacht nodig?
17. Welk overleg is aangevraagd, waarom en door welke rol?
18. Wat heeft een agent of proces onthouden en wat geldt als gedeelde productwaarheid?
19. Welke van mijn eerdere aanwijzingen zijn nog actief, vervangen of ingetrokken?
20. Hoeveel `TODO`-stories staan voor HKH klaar?
21. Welke story staat `IN_PROGRESS` in Software Factory en wat is de laatste leveringsstatus?
22. Welke gebruikerssignalen zijn binnengekomen en wat is ermee gebeurd?
23. Welke opdracht, richting, signalen of antwoorden heb ik als Stakeholder geleverd?
24. Bevat de verzonden Software Factory-story zelfstandig alle benodigde UX en ontwerpassets?

Als die antwoorden verspreid staan over meerdere schermen, documenten of agentruns, is het ontwerp
nog niet eenvoudig genoeg.

## Open keuzes voor de volgende versie van dit document

Voordat we gaan bouwen, moeten we nog samen kiezen:

- Hoe vrij mag Product Factory het algemene productdoel interpreteren?
- Welke beslissingen mag Product Factory zelfstandig nemen en welke moeten altijd langs de Stakeholder?
- Hoe vaak en naar welke bronnen moet Product Factory uit zichzelf onderzoek doen?
- Hoe voorkomen we dat onderzoek vooral bestaande producten kopieert in plaats van nieuwe kansen vindt?
- Hoe ver en hoe wild mag het droombeeld gaan?
- Wanneer is er genoeg bewijs om het droombeeld wezenlijk te veranderen?
- Wanneer mag een `IN_PROGRESS`-story voor een handmatig urgente epic worden onderbroken in plaats
  van normaal afgemaakt?
- Hoe bepaalt Productplanning de normale balans tussen productstories en bugfixes?
- Welke delen van de applicatie test de tester iedere dag en welke in een langere roulatie?
- Wanneer worden meerdere losse bugs samen een intern kansvoorstel voor Productontwerp?
- Welke gegevens uit versie 1 zijn echt waardevol genoeg om over te nemen?
- Wanneer mag een langlevende agent worden vervangen en wat gebeurt dan met zijn geheugen?
- Welke lessen horen in agentgeheugen en wanneer moeten zij naar proces- of productgeheugen worden
  gepromoveerd?
- Wie mag proces- en productgeheugen activeren, vervangen of intrekken?
- Wanneer vraagt een agent zelf om overleg en hoe voorkomen we onnodige overlegverzoeken?
- Mag een overleg meerdere processen tegelijk bijsturen of worden vervolgacties altijd per proces
  gesplitst?
- Bouwen we v2 in deze repository naast v1, of eerst in een aparte repository?

Deze keuzes moeten eerst in gewone taal beantwoord zijn. Daarna pas maken we het technische ontwerp.

## Uitwerking per procesmodule

De black-boxinterfaces hierboven zijn leidend. Het overkoepelende design beschrijft eigenaarschap en
gegevensstromen; de drie procesdocumenten beschrijven de interne agents, volgorde, parallelle
stappen, interne entiteiten en sessieregels:

- [Overkoepelend design van processen en publieke entiteiten](processen-en-entiteiten.md)
- [Besluitenregister](besluitenregister.md)
- [Productontwerp](productontwerp.md)
- [Productplanning](productplanning.md)
- [Kwaliteitsbewaking](kwaliteitsbewaking.md)
