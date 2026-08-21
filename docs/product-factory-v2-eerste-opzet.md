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

De Stakeholder is niet hetzelfde als de PO uit proces 3. De PO maakt binnen de afgesproken ruimte de
dagelijkse keuze voor het volgende werkitem. De Stakeholder hoeft die keuzes niet allemaal vooraf
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

### Signaal

Een signaal is iets dat misschien aandacht verdient. Bijvoorbeeld:

- een bugmelding;
- feedback van een gebruiker;
- een testresultaat;
- een observatie;
- een idee;
- een nieuwe kans.

Een signaal is nog geen besluit en staat nog niet op de roadmap.

### Epic

Een epic is een samenhangende verandering met één duidelijk gewenst resultaat. Een epic kan een
nieuwe mogelijkheid zijn, maar ook een grotere UX-, betrouwbaarheids-, performance- of
toegankelijkheidsverbetering.

Een epic beschrijft:

- welk probleem of welke kans we aanpakken;
- voor wie we dit doen;
- welke uitkomst we willen bereiken;
- hoe dit bij het productdoel en droombeeld past;
- welk bewijs er is;
- wat we nog niet zeker weten;
- hoe we straks zien of de epic geslaagd is.

Een epic is geen grote map met willekeurige stories. Hij mag klein zijn, zolang de stories samen
hetzelfde resultaat nastreven.

### Story

Een story is een klein stuk zichtbaar gedrag dat Software Factory kan bouwen en testen. Iedere story
hoort bij precies één epic. Stories worden pas gemaakt wanneer een epic bijna aan de beurt is of al
actief is.

Niet alle stories van een epic worden vooraf uitgeschreven. De eerste bruikbare stap moet duidelijk
zijn; de rest ontstaat pas wanneer we hebben geleerd van eerder werk.

### Bug

Een bug betekent dat bestaand gedrag aantoonbaar niet werkt zoals het hoort. Een duidelijke bug kan
rechtstreeks als bugfix worden uitgevoerd en hoeft niet kunstmatig een epic te worden.

Als meerdere bugs samen één groter probleem laten zien, kan daar wel een epic uit ontstaan. De losse
symptomen worden dan niet eindeloos één voor één bestreden.

### Kleine verbetering

Een kleine verbetering is een beperkte, duidelijke verandering, zoals een begrijpelijker foutmelding
of een beter zichtbaar gemaakte knop. Zij kan rechtstreeks worden uitgevoerd als het probleem, de
oplossing en de controle duidelijk zijn.

Heeft een verbetering onderzoek, een nieuwe gebruikersroute of meerdere samenhangende veranderingen
nodig, dan wordt zij een epic en komen er stories uit.

### Werkitem

Een werkitem is precies één concrete opdracht die de PO als volgende aan Software Factory kan geven.
Er zijn drie soorten werkitems:

- een story die bij een epic hoort;
- een bugfix;
- een kleine verbetering.

De PO kiest steeds één volgend werkitem. Zo hoeven we een bugfix geen story te noemen en blijft de
regel eenvoudig: iedere echte story hoort bij een epic.

### Leerresultaat

Na de oplevering leggen we kort vast:

- wat is gebouwd;
- of het werkt zoals bedoeld;
- wat gebruikers of tests laten zien;
- wat we nu anders weten;
- wat de logische volgende keuze is.

Dit leerresultaat wordt gebruikt bij de volgende keuze.

### Agent

Een agent is een herkenbare, langlevende AI-rol met een eigen verantwoordelijkheid en geheugen. Een
proces kan door één agent of door meerdere gespecialiseerde agents worden uitgevoerd. Dezelfde agent
kan over meerdere uitvoeringsruns heen actief blijven, maar zijn geheugen is nooit de enige plek
waar een productfeit, besluit of status bestaat.

### Overleg

Een overleg is een gesprek tussen de Stakeholder en één of meer agents over richting, een open vraag,
een beslissing of bijsturing. Zowel de Stakeholder als een agent of proces kan een overleg aanvragen.

Een overleg is geen vijfde proces. Het is een gedeeld coördinatiemiddel dat ieder van de vier
processen kan gebruiken. De uitkomst wordt als notulen, besluiten, acties en expliciete
geheugenwijzigingen opgeslagen.

### Geheugen

Geheugen is duurzame kennis die een volgende uitvoering kan gebruiken. Versie 2 kent drie lagen:

- agentgeheugen voor wat één agent vanuit zijn eigen rol heeft geleerd;
- procesgeheugen voor kennis die alle uitvoerders van hetzelfde proces nodig hebben;
- productgeheugen voor gedeelde productfeiten, richting en besluiten.

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

Alle losse informatie komt eerst in één inbox. Product Factory doet niet alsof ieder idee meteen goed
of belangrijk is.

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

## De vier vaste processen

Product Factory v2 bestaat uit vier processen die tegelijk kunnen lopen. Ze hebben ieder een andere
vraag en een ander resultaat.

```text
1. Onderzoek en richting
   vindt kansen en voedt het droombeeld
                 ↓
2. Epics maken en ordenen
   maakt epics klaar en kiest hun volgorde
                 ↓
              actieve epic ──→ stories ──┐
                                          │
4. Tester bewaakt kwaliteit ──→ bugs ─────┼──→ 3. PO kiest volgend werkitem
                                          │              ↓
                      kleine verbetering ─┘      Software Factory
```

De processen geven werk aan elkaar door, maar worden niet één grote cyclus. Onderzoek hoeft
bijvoorbeeld niet stil te staan terwijl Software Factory een story bouwt.

## Input, status en overdracht tussen de processen

Ieder proces heeft een eigen verantwoordelijkheid, maar geen eigen afgesloten administratie. De
output van het ene proces wordt als duurzaam productobject opgeslagen en kan daarna input zijn voor
een ander proces. Een agentrun is alleen de uitvoering van een stap; hij is nooit de enige plek waar
de actuele status bestaat.

De hoofdregel is:

> Input wordt gelezen uit benoemde productobjecten, voortgang wordt bijgehouden op het object dat
> verandert en output wordt pas overgedragen nadat zij duurzaam is opgeslagen en naar haar bron
> verwijst.

Daardoor kan een proces stoppen en later verdergaan, kan een andere agent het overnemen en blijft
zichtbaar waarom iets is ontstaan.

### Het totaaloverzicht

| Proces | Belangrijkste input | Waar de status wordt bijgehouden | Duurzame output | Wordt daarna input voor |
|---|---|---|---|---|
| 1. Onderzoek en richting | productdoel, harde grenzen, Stakeholderrichting, droombeeld, signalen, gebruiksgegevens, externe bronnen, overleguitkomsten, leerresultaten en structurele bugpatronen | onderzoeksdossiers, bronnen, onderzoeksinzichten, signalen en versies van het droombeeld | onderbouwde inzichten, nieuwe of samengevoegde signalen, aangepast droombeeld en epic-kandidaten | proces 2; gerichte onderzoeksvragen kunnen opnieuw naar proces 1 |
| 2. Epics maken en ordenen | epic-kandidaten, onderzoeksinzichten, droombeeld, productdoel, Stakeholderrichting, UX-verkenning, technische haalbaarheid en productgezondheid | het epicdossier met epicstatus, bewijs, open vragen, actuele UX-richting, technische risico's, klaar-beoordeling en positie in de roadmap | klaarbeoordeelde en geordende epics, maximaal één actieve epic en de eerstvolgende stories van die epic | proces 3; ontbrekend bewijs gaat als onderzoeksvraag naar proces 1 |
| 3. De PO kiest het volgende werkitem | stories van de actieve epic, bugs, kleine verbeteringen, Stakeholderbijsturing, afhankelijkheden, urgentie, beschikbare uitvoeringsruimte en resultaten van eerder uitgevoerd werk | het PO-besluitlogboek, de geordende werkitemwachtrij en de uitvoeringsstatus van ieder werkitem | één gekozen werkitem voor Software Factory en een vastgelegde prioriteitsreden; na oplevering ook een controleverzoek en een gekoppeld leerresultaat | Software Factory; na oplevering proces 4 voor controle en processen 1 en 2 voor leren en bijsturen |
| 4. De tester bewaakt de kwaliteit | werkende applicatie, belangrijkste gebruikersroutes, recente wijzigingen en opleveringen, eerdere bugs, Stakeholdersignalen, risico's en testhistorie | teststrategie, testrotatie, testsessies, bevindingen en de levenscyclus van iedere bug | reproduceerbare bugs, hertestresultaten, kwaliteitsbeeld, epic-controle en signalen over structurele problemen | proces 3 voor prioritering; proces 1 voor structurele signalen; proces 2 voor de beoordeling van een actieve epic |

### De overdrachtskaart

```text
productdoel + droombeeld + bronnen + signalen + leerresultaten
                              │
                              ▼
                  1. Onderzoek en richting
                              │
          inzichten + droombeeldwijziging + epic-kandidaten
                              │
                              ▼
                  2. Epics maken en ordenen
                              │
                 actieve epic + volgende stories
                              │
                              ▼
                 3. PO kiest één werkitem ◀──── bugs en hertestadvies
                              │                              ▲
                  gekozen werkitem + reden                  │
                              │                              │
                              ▼                              │
                     Software Factory                       │
                              │                              │
                  oplevering + uitvoeringsstatus            │
                              │                              │
                              ▼                              │
                 4. Tester controleert resultaat ───────────┘
                              │
              leerresultaat + kwaliteitsbeeld + bugpatronen
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
  1. Onderzoek en richting         2. Epic beoordelen/bijsturen
```

Proces 2 kan bovendien een gerichte onderzoeksvraag teruggeven aan proces 1. Proces 3 kan een
geblokkeerd werkitem teruggeven aan de bijbehorende epic. Zo is iedere terugkoppeling benoemd en
verdwijnt zij niet in een algemene agentconversatie.

### Twee soorten status

Er zijn twee soorten status die niet met elkaar verward mogen worden.

**Inhoudelijke productstatus** hoort bij een duurzaam productobject. Voorbeelden zijn de status van
een epic, de herstelstatus van een bug, de uitvoeringsstatus van een werkitem en de conclusie van een
onderzoeksdossier. Deze status is de productwaarheid en blijft bestaan wanneer geen enkel proces
draait.

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

De vier processen zelf zijn doorlopend en worden niet **Klaar**. Alleen hun afzonderlijke opdrachten,
productobjecten en uitvoeringsruns hebben een status.

### De duurzame productobjecten

De processen werken minimaal met de volgende gedeelde objecten:

- **Productdoel en harde grenzen** — de vaste opdracht waar alle processen hun keuzes aan toetsen;
- **Stakeholderrichting** — een expliciete aanwijzing, correctie of grens van de Stakeholder, met
  datum, reden en toepassingsgebied;
- **Droombeeld** — de actuele verre richting, met een zichtbare versiegeschiedenis;
- **Onderzoeksdossier** — één onderzoeksvraag met bronnen, voortgang, inzichten en conclusie;
- **Signaal** — een nog niet beoordeelde aanwijzing uit feedback, onderzoek, gebruik of testen;
- **Epic** — een samenhangende gewenste verandering, inclusief bewijs, UX, techniek, status en
  roadmappositie;
- **Story** — een uitvoerbaar zichtbaar onderdeel van precies één actieve epic;
- **Bug** — een reproduceerbare afwijking met bewijs, ernst en herstelstatus;
- **Kleine verbetering** — beperkt werk dat geen epic nodig heeft;
- **Werkitem** — de concrete story, bugfix of kleine verbetering die door de PO kan worden gekozen;
- **PO-besluit** — de keuze van het volgende werkitem met reden en afgevallen alternatieven;
- **Testsessie** — wat is getest, waarom, met welk resultaat en welke dekking daarna nog ontbreekt;
- **Opleverresultaat** — wat Software Factory heeft teruggegeven en waar het uitgevoerd kan worden;
- **Leerresultaat** — wat na onderzoek, bouw of controle anders bekend is dan daarvoor;
- **Overleg** — agenda, deelnemers, berichten, geraadpleegde bronnen, status en gekoppelde objecten;
- **Overleguitkomst** — notulen met besluiten, open vragen, acties en expliciete geheugenwijzigingen;
- **Agentgeheugen** — actieve, vervangen en ingetrokken lessen van één herkenbare agent;
- **Procesgeheugen** — gedeelde werkwijze en ervaring van één van de vier processen;
- **Productgeheugen** — gedeelde actuele kennis, richting en besluiten voor alle processen.

Deze objecten worden niet voor iedere overdracht gekopieerd. Processen verwijzen naar hetzelfde
object en voegen hun eigen resultaat eraan toe. Een bug die door de tester is gemaakt, blijft dus
dezelfde bug wanneer de PO hem prioriteert en Software Factory hem oplost.

### Regels voor iedere overdracht

Een overdracht tussen processen is pas compleet wanneer:

1. de output duurzaam is opgeslagen;
2. de bron en aanleiding zichtbaar zijn;
3. duidelijk is welk proces of welke rol nu eigenaar is van de volgende stap;
4. de ontvangende wachtrij of het ontvangende object is bijgewerkt;
5. het producerende proces niet hoeft te blijven draaien om de informatie te behouden.

Een overdracht kan ook teruggaan. Als proces 2 een epic niet klaar kan verklaren door ontbrekend
bewijs, maakt het een gerichte onderzoeksvraag voor proces 1. Als proces 4 meerdere verwante bugs
ziet, maakt het naast de losse bugs een structureel signaal voor proces 1. Zo ontstaat terugkoppeling
zonder dat verantwoordelijkheden door elkaar gaan lopen.

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

- de actuele onderzoeksaanpak, bronspreiding en nog onderbelichte onderwerpen van proces 1;
- terugkerende klaarheidsproblemen en bruikbare vergelijkingsprincipes van proces 2;
- prioriteringspatronen, capaciteitsafspraken en terugkerende blokkades van proces 3;
- teststrategie, testrotatie, risicogebieden en dekkingsgaten van proces 4.

Procesgeheugen bewaart ervaring over **hoe** het proces goed wordt uitgevoerd. De actuele status van
een epic, werkitem, bug of testsessie blijft op dat productobject staan en wordt niet naar het
procesgeheugen gekopieerd.

### 3. Productgeheugen

Productgeheugen bevat kennis die voor meerdere processen of agents als gedeelde context geldt:

- Stakeholderrichting en correcties;
- actuele productregels en besluiten;
- geldige leerresultaten;
- relevante feiten en technische grenzen;
- samenvattingen van afgesloten overleggen;
- de herkomst, geldigheid en reikwijdte van ieder kennisitem.

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
bijvoorbeeld de onderzoeker, epicverantwoordelijke, PO en tester samenbrengen. De Stakeholder hoeft
niet zelf te weten welke technische agentrun op de achtergrond actief is; wel is zichtbaar vanuit
welke rol een advies of vraag komt.

### Input en status van een overleg

Een overleg begint met een korte agenda en verwijzingen naar de betrokken productobjecten. De input
kan bestaan uit:

- vragen of onderwerpen van de Stakeholder;
- een overlegverzoek van een agent of proces;
- relevante epics, werkitems, bugs, testsessies of onderzoeksdossiers;
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
doorwerking is een expliciete, controleerbare wijziging. Een overleg kan zo tegelijk output leveren
aan proces 1, een epic in proces 2 bijsturen, een prioriteitsgrens voor proces 3 vastleggen en een
testopdracht voor proces 4 opleveren.

## Proces 1 — Onderzoek en richting

Dit proces kijkt breed vooruit en terug. Het gebruikt:

- het productdoel en de harde grenzen;
- het actuele droombeeld;
- onderzoek naar soortgelijke en aangrenzende producten;
- gebruikerssignalen en gebruiksgegevens;
- nieuwe technieken en marktontwikkelingen;
- bugs en patronen die de tester vindt;
- leerresultaten van opgeleverd werk.

Het proces probeert te begrijpen welke problemen, kansen en toekomstmogelijkheden belangrijk zijn.
Het levert:

- onderzoeksinzichten;
- nieuwe signalen;
- veranderingen in het droombeeld;
- mogelijke epic-kandidaten.

Het levert nog geen stories op. Een goed idee of opvallend voorbeeld wordt eerst een epic-kandidaat,
niet meteen gepland werk.

## Proces 2 — Epics maken en ordenen

Dit proces maakt van kansrijke ideeën duidelijke epics en bepaalt welke epic als eerste aan de beurt
zou moeten komen. Hier zit het meeste langetermijndenken van Product Factory.

### De stappen van het epicproces

1. **Verzamelen** — neem epic-kandidaten uit onderzoek, het droombeeld, gebruikerssignalen en
   structurele kwaliteitsproblemen.
2. **Samenvoegen** — voorkom dubbele of bijna gelijke epics.
3. **Kiezen voor onderzoek** — werk niet iedere kandidaat volledig uit.
4. **Onderzoeken** — controleer probleem, doelgroep, bewijs, mogelijke waarde en alternatieven.
5. **UX verkennen** — maak de belangrijkste gebruikersroute en onzekerheden duidelijk.
6. **Techniek verkennen** — laat Software Factory vroeg naar haalbaarheid, risico's en mogelijkheden
   kijken.
7. **Kleinste eerste versie bepalen** — kies de kleinste bruikbare of leerzame slice.
8. **Klaar beoordelen** — controleer of de epic veilig kan starten.
9. **Ordenen** — vergelijk klaarliggende epics en leg uit welke eerst komt.
10. **Activeren** — maak alleen de beste epic actief wanneer daar ruimte voor is.

### De statussen van een epic

- **Kandidaat** — mogelijk interessant, nog nauwelijks onderzocht;
- **Onderzoeken** — probleem, bewijs en mogelijkheden worden bekeken;
- **Uitwerken** — UX, techniek en eerste slice worden duidelijk gemaakt;
- **Klaar** — kan worden gestart, maar is nog niet actief;
- **Actief** — levert stap voor stap stories op;
- **Controleren** — het werk is geleverd en de gewenste uitkomst wordt beoordeeld;
- **Geslaagd** — de gewenste uitkomst is voldoende bereikt;
- **Gestopt** — bewust niet verder, met een zichtbare reden.

### Wanneer een epic klaar is

Een epic is pas **Klaar** wanneer in gewone taal duidelijk is:

- welk probleem of welke kans wordt aangepakt;
- voor welke gebruikers;
- welke gewenste uitkomst er is;
- hoe de epic bij het productdoel en droombeeld past;
- welk bewijs er is;
- wat de actuele UX-richting is;
- wat technisch lastig of riskant is;
- hoe we het resultaat gaan controleren;
- wat de kleinste eerste slice is;
- welke eerste story uitgevoerd kan worden;
- welke open vragen nog bestaan en waarom die de start niet blokkeren.

Niet alle stories hoeven dan al geschreven te zijn. Alleen de eerste slice en de eerste uitvoerbare
story moeten duidelijk zijn. De rest wordt pas uitgewerkt wanneer eerdere stories nieuwe informatie
hebben opgeleverd.

De klaar-beoordeling kijkt met meerdere blikken naar dezelfde epic:

- onderzoek: is het probleem echt en is de bron betrouwbaar;
- product: draagt dit genoeg bij aan het productdoel;
- visie: brengt dit ons in de richting van het droombeeld;
- UX: begrijpen we de belangrijkste gebruikerservaring;
- techniek: bestaat er een aannemelijke en veilige bouwroute;
- kritiek: zijn risico's, onzekerheden en afgevallen alternatieven eerlijk beschreven.

De gebruiker hoeft de interne agents niet te zien. Het zichtbare resultaat is eenvoudig:

> Deze epic is klaar, omdat …

of:

> Deze epic is nog niet klaar, omdat …

### Welke epic gaat eerst

Product Factory vergelijkt klaarliggende epics op:

- waarde voor gebruikers;
- bijdrage aan het productdoel en droombeeld;
- sterkte van het bewijs;
- urgentie;
- risico en omkeerbaarheid;
- verwachte hoeveelheid werk;
- afhankelijkheden;
- hoeveel we ervan kunnen leren;
- welk later werk hierdoor mogelijk wordt;
- de gezondheid van het huidige product.

Een score mag helpen ordenen, maar beslist niet zelfstandig. Product Factory geeft een korte
vergelijking, bijvoorbeeld:

> Epic A gaat vóór epic B. A lost een bewezen dagelijks probleem op, maakt twee latere epics mogelijk
> en kan klein worden gestart. B kan uiteindelijk meer waarde hebben, maar de belangrijkste aanname
> is nog niet onderzocht.

Normaal heeft een product:

- maximaal één actieve epic;
- maximaal één epic die uitgebreid wordt onderzocht of uitgewerkt;
- een kleine geordende lijst met klaarliggende epics;
- alle overige ideeën als kandidaten.

Zo kan het epicproces de volgende richting voorbereiden terwijl de actieve epic wordt gebouwd, zonder
veel toekomstig werk onnodig volledig uit te schrijven.

## Proces 3 — De PO kiest het volgende werkitem

De Product Owner, afgekort PO, kan een persoon of een begrensd AI-proces zijn. De PO bepaalt wat
Software Factory als volgende oppakt zodra daar ruimte voor is.

De PO kiest uit drie soorten werkitems:

- een story uit de actieve epic;
- een bugfix uit de bugwachtrij;
- een kleine algemene verbetering.

De PO bekijkt daarbij:

1. Is er een kritieke bug die alles moet onderbreken?
2. Is er een belangrijke bug die nu zwaarder weegt dan doorgaan met de epic?
3. Wat is de beste niet-geblokkeerde story uit de actieve epic?
4. Is er een kleine verbetering die nu veel waarde geeft of een blokkade wegneemt?
5. Welk werkitem past binnen de afgesproken grens voor werk dat tegelijk bezig is?

De PO geeft steeds precies één volgend werkitem aan Software Factory en legt kort uit waarom dit item
nu voorrang krijgt. De PO bepaalt niet in zijn eentje de verre productrichting; daarvoor gebruikt hij
de geordende epics uit proces 2.

Een simpele standaard voor bugs is:

- **P0** — ernstig veiligheidsprobleem, dataverlies of het product is onbruikbaar: onderbreekt alles;
- **P1** — een belangrijke gebruikersroute werkt niet: gaat meestal voor de volgende epic-story;
- **P2** — duidelijke fout met een omweg: wordt tegen de actieve epic afgewogen;
- **P3** — klein of beperkt ongemak: wordt gepland zonder de epic voortdurend te verstoren.

De PO bewaakt ook dat bugs niet alle vernieuwing verdringen en dat de actieve epic niet alle
productgezondheid verdringt.

Binnen het productdoel en de harde grenzen mag een AI-PO gewone, omkeerbare keuzes zelf maken. Hij
legt de keuze en afgevallen alternatieven vast. Bij een dure, gevoelige, moeilijk terug te draaien of
doelveranderende keuze vraagt hij eerst hulp.

## Proces 4 — De tester bewaakt de kwaliteit

De tester kan ook een persoon of een AI-proces zijn. Hij test de applicatie voortdurend, met
bijvoorbeeld iedere dag een nieuwe testsessie en extra controles na een oplevering.

Niet iedere testsessie doet exact hetzelfde. De tester onderhoudt een overzicht van wat recent is
getest en wisselt onder andere tussen:

- de belangrijkste gebruikersroutes;
- nieuw of recent veranderd gedrag;
- eerder opgeloste bugs;
- verschillende schermgroottes en mobiel gebruik;
- toegankelijkheid;
- lege, langzame en foutsituaties;
- performance;
- beveiliging en privacy;
- vrij onderzoekend testen op onverwacht gedrag.

Een gevonden bug bevat minimaal:

- wat er misgaat;
- wat er eigenlijk had moeten gebeuren;
- hoe de fout opnieuw kan worden veroorzaakt;
- waar en wanneer de fout is gevonden;
- screenshot, log of ander bewijs;
- welke gebruikers geraakt worden;
- een voorgestelde ernst van P0 tot en met P3.

De tester bepaalt niet welk werk als volgende wordt uitgevoerd. Hij levert betrouwbare bugs aan de
PO. De PO weegt die af tegen de stories van de actieve epic.

Wanneer de tester meerdere bugs vindt die op hetzelfde grotere probleem wijzen, stuurt hij ook een
signaal naar proces 1. Daar kan vervolgens een UX-, betrouwbaarheids- of technische epic uit ontstaan.

## Hoe de vier processen elkaar in beweging houden

De processen vormen samen geen vaste trein die altijd helemaal van links naar rechts moet. Ze houden
elkaar voortdurend op de hoogte:

- onderzoek kan een nieuwe epic-kandidaat opleveren;
- het epicproces kan om gericht nieuw onderzoek vragen;
- de PO kan melden dat stories steeds geblokkeerd raken;
- de tester kan een losse bug of een structureel productprobleem vinden;
- opgeleverde stories kunnen het droombeeld of de epicvolgorde veranderen;
- een afgeronde epic levert altijd een leerresultaat op.

Zo blijft Product Factory tegelijk dromen, vooruitkijken, dagelijks sturen en de bestaande software
bewaken.

## Hoe de roadmap werkt

De roadmap is alleen voor epics. Losse ideeën, bugs en kleine verbeteringen staan er niet tussen.

De roadmap heeft vier eenvoudige vakken:

- **Nu** — de ene actieve epic;
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

Product Factory legt de afweging uit. Een ondoorzichtige score beslist niet zelfstandig wat er moet
gebeuren.

Bugs en kleine verbeteringen staan naast de roadmap in **Productgezondheid**. Daardoor blijft
duidelijk welk werk het huidige product gezond houdt en welke epics het product verder brengen.

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
- uitwerken tot een epic-kandidaat.

Een agent mag ideeën onderzoeken en vergelijken. De agent mag niet doen alsof een mooi geschreven
idee daarom automatisch een goed productbesluit is.

## Hoe UX-ontwerpen worden behandeld

UX is geen losse verzameling documenten. Een UX-uitwerking hoort altijd bij één epic, één kleine
verbetering of één duidelijk benoemd onderdeel van het droombeeld. Een bug verwijst normaal naar het
bestaande gedrag dat had moeten werken.

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

De epic bezit de actuele UX-richting. Stories verwijzen naar het deel dat zij uitvoeren en maken geen
eigen concurrerende ontwerpen.

Een UX-uitwerking hoeft niet altijd mooi te zijn. Eerst moet zij duidelijk zijn. Voor een belangrijke
of onzekere gebruikersstroom kan Product Factory een klikbaar ontwerp of screenshots laten maken.
De Stakeholder kan daarop reageren voordat er wordt gebouwd.

## Eén gezamenlijke werkstroom

Epics, dagelijks productbeheer en testen komen samen bij de PO:

```text
Actieve epic ──→ story ─────────┐
                                │
Tester ────────→ bugfix ────────┼──→ PO kiest één werkitem
                                │              ↓
Productgezondheid → verbetering ┘      Software Factory
```

Er gaat normaal maar één nieuw werkitem tegelijk naar Software Factory. Pas wanneer daar weer ruimte
is, kiest de PO het volgende. Ieder resultaat gaat terug naar de epic, de bugwachtrij, het onderzoek
en het productgeheugen waar het bij hoort.

## Wat de Stakeholder doet en wat agents doen

Agents helpen met:

- informatie samenvatten;
- dubbele signalen vinden;
- op eigen initiatief onderzoek doen naar soortgelijke producten en aangrenzende oplossingen;
- een ambitieus droombeeld maken en met nieuwe kennis bijwerken;
- epic-kandidaten maken, onderzoeken en vergelijken;
- beoordelen of een epic klaar is;
- een gebruikersroute en eerste epic-slice uitwerken;
- stories voor de actieve epic schrijven;
- als PO het volgende werkitem kiezen;
- als tester dagelijks en na opleveringen de applicatie onderzoeken;
- een opgeleverd resultaat beoordelen;
- ontbrekende informatie aanwijzen.

De Stakeholder blijft verantwoordelijk voor:

- het brede productdoel en de harde grenzen;
- het corrigeren van de richting wanneer Product Factory het doel verkeerd begrijpt;
- onomkeerbare of kostbare beslissingen;
- gevoelige gegevens en externe toegang.

Product Factory is binnen die opdracht verantwoordelijk voor het actuele droombeeld, onderzoek, het
epicportfolio, de epicvolgorde, de PO-keuzes, het testproces en de balans tussen Verbeteren en
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
- welk werkitem de PO als volgende heeft gekozen;
- wat bij Software Factory bezig is;
- welke belangrijke bugs openstaan;
- wanneer en wat de tester voor het laatst heeft getest;
- wat we recent hebben geleerd;
- wat recent onderzoek heeft veranderd;
- of ergens hulp nodig is;
- welke overleggen zijn aangevraagd of openstaan;
- welke recente Stakeholderrichting de vier processen beïnvloedt.

### 2. Inbox

Hier staan nieuwe feedback, observaties, ideeën en onderzoeksinzichten. Je kunt ze bekijken,
samenvoegen, afwijzen of laten uitwerken tot een epic-kandidaat. Bugs komen vanuit het testproces in
de aparte productgezondheidslijst.

### 3. Plan

Hier staan drie onderdelen bij elkaar:

- het droombeeld als verre richting;
- de epic-roadmap met Nu, Hierna en Later;
- Productgezondheid met bugs en kleine verbeteringen.

Ook is zichtbaar welke epic wordt onderzocht, waarom epics wel of niet klaar zijn en hoe Product
Factory de klaarliggende epics heeft geordend.

### 4. Detail

Een epic, story, bug of kleine verbetering heeft één rustige detailpagina met:

- probleem en gewenste uitkomst;
- bewijs en open vragen;
- actuele UX-uitwerking, als die nodig is;
- eventuele bovenliggende epic en relevante storyrelaties;
- reden van de PO-prioriteit;
- voortgang en resultaat;
- beslissingen en leerresultaten.

Technische details zijn beschikbaar via een aparte knop, maar staan standaard dicht.

Overleggen en geheugen hoeven geen extra hoofdscherm te worden. Vanuit Product en ieder detail kan de
Stakeholder een overleg openen of starten. Een aparte secundaire weergave toont alle overleggen en de
geschiedenis van agent-, proces- en productgeheugen, inclusief vervangen en ingetrokken items.

## Eenvoudige statussen voor werkitems

Epic-statussen staan bij proces 2. Uitvoerbare stories, bugfixes en kleine verbeteringen gebruiken een
kortere reeks:

- **Klaar** — duidelijk genoeg om uitgevoerd te worden;
- **In wachtrij** — de PO heeft het item gekozen;
- **Bezig** — Software Factory werkt eraan;
- **Controleren** — opgeleverd, maar nog niet bewezen;
- **Afgerond** — werkt zoals bedoeld;
- **Geblokkeerd** — kan niet verder, met een zichtbare reden;
- **Gestopt** — bewust niet verder, met een zichtbare reden.

Een status beschrijft de toestand van het werk. Een agentnaam of processtap is geen productstatus.

## Regels die versie 2 eenvoudig houden

1. Eén plek bevat de actuele productwaarheid.
2. Een los idee is nog geen roadmapitem.
3. Product Factory wacht niet alleen op invoer, maar zoekt zelf naar kansen en bedreigingen.
4. Het droombeeld mag onhaalbaar lijken; de eerstvolgende epic-slice moet wel klein en toetsbaar zijn.
5. De roadmap bevat epics, geen losse stories, bugs of ideeën.
6. Iedere story hoort bij precies één epic.
7. Stories ontstaan pas wanneer een epic bijna aan de beurt is of actief is.
8. Een bugfix en kleine verbetering mogen rechtstreeks een werkitem zijn.
9. De PO kiest steeds het volgende werkitem; het epicproces kiest de productrichting.
10. De tester levert bewijs en ernst, maar bepaalt niet de uitvoeringsvolgorde.
11. Ieder UX-ontwerp hoort bij één epic, kleine verbetering of benoemd droomconcept.
12. Een droomconcept is nooit stilletjes een gepland ontwerp.
13. Per onderwerp is maar één UX-versie actueel.
14. We bouwen kleine stappen en leren na iedere oplevering.
15. Er is weinig werk tegelijk bezig.
16. Kritieke fouten gaan voor, maar onderhoud verdringt vernieuwing niet stilletjes.
17. Agents nemen gewone omkeerbare productbesluiten en leggen die uit.
18. De Stakeholder kan richting geven en bijsturen zonder de dagelijkse PO te worden.
19. Iedere langlevende agent heeft eigen geheugen; ieder proces heeft gedeeld procesgeheugen.
20. Agent- en procesgeheugen zijn nooit de enige bron van productwaarheid.
21. Een overleguitkomst werkt alleen door via expliciete besluiten, acties of geheugenwijzigingen.
22. Een verborgen score neemt geen groot of onomkeerbaar productbesluit.
23. Het gewone scherm toont producttaal, geen interne agent- of databasetaal.
24. Nieuwe functies komen alleen in Product Factory als ze een hoofdvraag aantoonbaar eenvoudiger
    maken.

## Wat we uit versie 1 willen behouden

De tweede versie hoeft niet alles opnieuw uit te vinden. Waardevolle lessen en onderdelen zijn:

- een product kan als gegevens worden toegevoegd, zonder nieuwe productcode in Product Factory;
- Product Factory en Software Factory hebben ieder een duidelijke taak;
- weinig werk tegelijk geeft rust en maakt prioriteiten echt;
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
- automatisch periodiek nieuwe stories maken zonder een duidelijke keuze;
- het oude epicmodel met capabilities, horizons, meerdere ranks en scores tegelijk;
- agents als hoofdnavigatie;
- ruwe agentuitvoer als normaal productdocument;
- losse UX-documenten zonder eigenaar of actuele status;
- alle bestaande databasevelden en historie;
- ieder scherm of iedere functie uit het huidige dashboard.

Ieder onderdeel uit versie 1 moet opnieuw bewijzen dat het nodig is.

## De kleinste bruikbare versie

De eerste bruikbare versie moet meteen bewijzen dat de vier processen samen werken. Zij hoeft nog
niet iedere mogelijke bron of vorm van automatisering te ondersteunen.

### Minimaal voor proces 1 — Onderzoek en richting

1. een product met een breed productdoel en harde grenzen vastleggen;
2. zelf onderzoek naar soortgelijke en aangrenzende producten starten;
3. bronnen en bruikbare onderzoeksinzichten bewaren;
4. een ambitieus droombeeld maken en met nieuw bewijs aanpassen;
5. vanuit onderzoek, signalen en het droombeeld epic-kandidaten maken.

### Minimaal voor proces 2 — Epics maken en ordenen

1. één epic-kandidaat onderzoeken en uitwerken;
2. één actuele UX-richting en kleinste eerste slice maken;
3. vroeg technische haalbaarheidsinformatie van Software Factory gebruiken;
4. zichtbaar beoordelen of de epic klaar is;
5. twee klaarliggende epics begrijpelijk met elkaar vergelijken;
6. maximaal één epic actief maken;
7. voor de actieve epic alleen de eerstvolgende nodige stories maken.

### Minimaal voor proces 3 — De PO kiest het volgende werkitem

1. kiezen uit een epic-story, bugfix of kleine verbetering;
2. ernst, waarde, blokkades en werk-in-uitvoering meewegen;
3. precies één volgend werkitem met een korte reden aanwijzen;
4. dit werkitem naar Software Factory sturen;
5. het resultaat ophalen en terugkoppelen naar de juiste epic, bug of verbetering.

### Minimaal voor proces 4 — De tester bewaakt de kwaliteit

1. iedere dag en na een oplevering een gerichte testsessie kunnen starten;
2. belangrijke routes en wisselende onderzoeksthema's bijhouden;
3. een reproduceerbare bug met bewijs en voorgestelde ernst maken;
4. de bug aan de PO aanbieden;
5. patronen van meerdere bugs als signaal naar onderzoek en het epicproces sturen.

### Minimaal voor overleggen en geheugen

1. de Stakeholder kan vanuit een productobject een overleg starten;
2. ieder proces en iedere bevoegde agent kan met reden en onderwerpen een overleg aanvragen;
3. een overleg bewaart deelnemers, berichten, bronnen, gekoppelde objecten, status en notulen;
4. besluiten, acties en geheugenwijzigingen uit het overleg worden expliciet doorgevoerd;
5. iedere langlevende agent heeft corrigeerbaar agentgeheugen;
6. ieder proces heeft gedeeld procesgeheugen voor aanpak, continuïteit en terugkerende lessen;
7. gedeelde productwaarheid staat in productgeheugen of op het bijbehorende productobject;
8. actieve, vervangen, ingetrokken en historische kennis zijn zichtbaar van elkaar onderscheiden.

Zelf onderzoek starten, dromen, epics vormen, dagelijks prioriteren en continu testen horen bij de
kern. Dit zijn geen uitbreidingen voor later.

## Wanneer versie 2 geslaagd is

De Stakeholder moet zonder technische uitleg binnen één minuut antwoord kunnen geven op:

1. Wat is de brede opdracht van dit product?
2. Hoe ziet Product Factory de ideale verre toekomst van dit product?
3. Wat heeft Product Factory onlangs buiten het project geleerd?
4. Welke epic is actief en waarom juist deze?
5. Welke epic staat hierna klaar en waarom?
6. Waarom is een epic wel of nog niet klaar?
7. Welk werkitem heeft de PO als volgende gekozen?
8. Is dat een story, bugfix of kleine verbetering?
9. Bij welke epic hoort een story?
10. Wat heeft de tester recent onderzocht en gevonden?
11. Wat is het actuele UX-ontwerp en is het een droomconcept of een bouwplan?
12. Wat wordt op dit moment gebouwd?
13. Wat hebben we van de laatste oplevering geleerd?
14. Welke beslissing heeft mijn aandacht nodig?
15. Welk overleg is aangevraagd, waarom en door welke rol?
16. Wat heeft een agent of proces onthouden en wat geldt als gedeelde productwaarheid?
17. Welke van mijn eerdere aanwijzingen zijn nog actief, vervangen of ingetrokken?

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
- Welke harde voorwaarden moet iedere epic doorlopen voordat zij Klaar wordt?
- Wie of welk AI-proces mag de definitieve epicvolgorde veranderen?
- Wanneer mag een actieve epic worden onderbroken of gestopt?
- Hoe vaak kiest de PO opnieuw: na iedere oplevering, bij een kritieke bug of ook op vaste momenten?
- Hoe bepaalt de PO de normale balans tussen epic-stories, bugs en kleine verbeteringen?
- Welke delen van de applicatie test de tester iedere dag en welke in een langere roulatie?
- Wanneer worden meerdere losse bugs samen een epic-kandidaat?
- Welke UX-vorm is minimaal nodig voordat nieuwe zichtbare functionaliteit gebouwd mag worden?
- Welke gegevens uit versie 1 zijn echt waardevol genoeg om over te nemen?
- Welke langlevende agents krijgt ieder proces minimaal en wanneer mag een agent worden vervangen?
- Welke lessen horen in agentgeheugen en wanneer moeten zij naar proces- of productgeheugen worden
  gepromoveerd?
- Wie mag proces- en productgeheugen activeren, vervangen of intrekken?
- Wanneer vraagt een agent zelf om overleg en hoe voorkomen we onnodige overlegverzoeken?
- Mag een overleg meerdere processen tegelijk bijsturen of worden vervolgacties altijd per proces
  gesplitst?
- Bouwen we v2 in deze repository naast v1, of eerst in een aparte repository?

Deze keuzes moeten eerst in gewone taal beantwoord zijn. Daarna pas maken we het technische ontwerp.
