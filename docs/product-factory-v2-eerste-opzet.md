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

De Stakeholder is niet hetzelfde als de PO-rol uit proces 2. De PO onderhoudt binnen de afgesproken
ruimte de dagelijkse backlogvolgorde. De Stakeholder hoeft die keuzes niet allemaal vooraf
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

Een productstory is een klein stuk zichtbaar gedrag dat Software Factory kan bouwen en testen.
Iedere productstory hoort bij precies één epic. Stories worden pas gemaakt wanneer een epic bijna
aan de beurt is of al actief is.

Niet alle stories van een epic worden vooraf uitgeschreven. De eerste bruikbare stap moet duidelijk
zijn; de rest ontstaat pas wanneer we hebben geleerd van eerder werk.

### Bug

Een bug betekent dat bestaand gedrag aantoonbaar niet werkt zoals het hoort. Een duidelijke bug kan
rechtstreeks als bugfix worden uitgevoerd en hoeft niet kunstmatig een epic te worden.

Als meerdere bugs samen één groter probleem laten zien, kan daar wel een epic uit ontstaan. De losse
symptomen worden dan niet eindeloos één voor één bestreden.

### Backlogitem

Een backlogitem is precies één concrete opdracht die Software Factory kan bouwen en testen. Er zijn
twee soorten backlogitems:

- een **productstory** uit proces 1 die bij precies één epic hoort;
- een **bugfix** voor een bug uit proces 3.

Een kleine verbetering die geen bug is, wordt als productstory binnen een passende, zo klein
mogelijke epic uitgewerkt. Daardoor hoeft de interface tussen de processen geen derde soort
uitvoerbaar werk te kennen.

Proces 2 is de enige eigenaar van de geprioriteerde backlog. Een backlogitem verwijst naar de
oorspronkelijke story of bug, maar kopieert die niet en verandert de bron niet.

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

Een overleg is geen vierde proces. Het is een gedeeld coördinatiemiddel dat ieder van de drie
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

## De drie vaste processen

Product Factory v2 bestaat uit drie zelfstandige Spring Modulith-modules. Onderzoek en richting en
het maken van epics en stories vormen samen de module **Productontwikkeling**. Zij gebruiken
voortdurend dezelfde bewijs-, richting-, UX- en epiccontext; een aparte modulegrens zou vooral een
kunstmatige, cyclische interface opleveren.

Iedere module is voor de andere modules een black box: zij kennen alleen de gepubliceerde
data-interface en weten niets van agents, prompts, stappen, scores of interne tabellen van een
andere module.

Iedere procesmodule heeft precies één uitvoerende ingang:

```java
void runProcessSession();
```

Een scheduler roept deze functie aan. Eén aanroep claimt atomair hooguit één product waarvoor werk
nodig is en voert daarvoor één begrensde processessie uit. Is er niets te doen, dan eindigt de
aanroep als succesvolle no-op. Andere modules kunnen een proces niet starten en kunnen geen interne
stappen aanroepen.

De modules delen fysiek één database, maar niet één vrij toegankelijk datamodel. Iedere entiteit
heeft precies één schrijvende module. Andere modules lezen alleen de gepubliceerde velden via een
Spring Modulith-interface. Zij schrijven nooit rechtstreeks in de tabellen van een andere module.
Interne entiteiten en repositories blijven buiten de named interface.

Omdat de informatiestroom terugkoppelingen bevat, zouden directe Java-afhankelijkheden tussen de
drie procesmodules cycli veroorzaken. Daarom staan alleen de stabiele DTO's en read-only queryports
in een neutrale technische module `processcontracts`. Iedere eigenaar publiceert daarin een
geversioneerde databaseprojectie van zijn output. Alle procesmodules mogen `processcontracts`
gebruiken, maar mogen elkaar niet importeren. `processcontracts` bevat geen productlogica en is geen
procesmodule.

```text
1. Productontwikkeling
   onderzoek + richting + epics + productstories
                 │
                 ├───────────────────┐
                 ▼                   │
2. Backlog en prioritering ◀── 3. Testen en bugs
   onderhoudt circa 10 items    publiceert uitvoerbare bugs
                 │
                 ▼
       SoftwareFactoryDispatcher
                 │
                 ▼
         Software Factory
```

De processen communiceren dus niet met elkaars gedrag. Zij reageren tijdens hun volgende geplande
sessie op duurzaam opgeslagen gegevens die een andere module heeft gepubliceerd.

De `SoftwareFactoryDispatcher` is geen vierde productproces. Het is een eenvoudige geplande adapter
binnen de proces-2-module. Hij gebruikt geen agents en neemt geen productbesluiten. Hij verwerkt
eerst de status van eerder verzonden items. Wanneer Software Factory voor een product geen
openstaand item meer heeft, verstuurt hij precies het bovenste verzendbare backlogitem en bewaart hij
het externe Software Factory-ID.

## Input, status en overdracht tussen de processen

Ieder proces heeft eigen interne administratie en een kleine gepubliceerde interface. Een agentrun is
alleen de uitvoering van een stap; hij is nooit de enige plek waar actuele productstatus bestaat.

De hoofdregel is:

> Eén module schrijft een entiteit. Andere modules lezen een gepubliceerde, geversioneerde weergave
> en bewaren alleen een verwijzing naar de bron.

Daardoor kan een proces stoppen en later verdergaan zonder dat een andere module zijn interne
toestand hoeft te begrijpen.

### Het totaaloverzicht

| Proces | Gepubliceerde input | Eigen duurzame output | Betekenis voor andere modules |
|---|---|---|---|
| 1. Productontwikkeling | productopdracht, Stakeholderrichting, backlogvoorraad, leverings- en verificatieresultaten en kwaliteits- en gebruikerssignalen | droombeeld, epics, epicvolgorde, uitvoerbare productstories en leerresultaten | welke productverandering gekozen en klaar voor uitvoering is |
| 2. Backlog en prioritering | uitvoerbare productstories, uitvoerbare bugs, productgrenzen en leveringsstatus uit Software Factory | geprioriteerde backlog, prioriteitsbesluiten en backlogvoorraadstatus | welke circa tien opdrachten in welke volgorde klaarstaan |
| 3. Testen en bugs | testbare productconfiguratie, opleveringen, open en opgeloste bugs, epicdoelen en risicosignalen | bugs, verificatieresultaten, kwaliteitsbeeld en structurele kwaliteitssignalen | wat aantoonbaar niet werkt, wat geverifieerd is en welke patronen aandacht vragen |

### De overdrachtskaart

```text
productopdracht + signalen + leerresultaten + backlogvoorraad
                              │
                              ▼
                   1. Productontwikkeling
                              │
                 uitvoerbare productstories
                              │
                              ▼
                2. Backlog en prioritering ◀──── uitvoerbare bugs
                              │                              ▲
                  geprioriteerde backlog                    │
                              │                              │
                              ▼                              │
                SoftwareFactoryDispatcher                   │
                              │                              │
                              ▼                              │
                     Software Factory                       │
                              │                              │
                   oplevering en status                     │
                              │                              │
                              ▼                              │
                    3. Testen en bugs ──────────────────────┘
                              │
              verificatie + kwaliteitssignalen
                              │
                              ▼
                   1. Productontwikkeling
```

Onderzoeksvragen, antwoorden en kansvoorstellen blijven interne overdrachten binnen
Productontwikkeling. Een lage backlogvoorraad maakt alle drie de processen opnieuw planbaar; er is
geen rechtstreekse oproep van proces 2 naar de andere processen.

### Twee soorten status

Er zijn twee soorten status die niet met elkaar verward mogen worden.

**Inhoudelijke productstatus** hoort bij een duurzaam productobject en wordt alleen door de eigenaar
geschreven. Voorbeelden zijn de epicstatus van proces 1, de backlogstatus van proces 2 en de
bugstatus van proces 3. Deze status blijft bestaan wanneer geen enkel proces draait.

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

- **Productdoel en harde grenzen** — de vaste opdracht waar alle processen hun keuzes aan toetsen;
- **Stakeholderrichting** — een expliciete aanwijzing, correctie of grens van de Stakeholder, met
  datum, reden en toepassingsgebied;
- **Droombeeld** — de actuele verre richting van Productontwikkeling, zichtbaar voor de
  Stakeholder maar geen overdracht naar een ander proces;
- **Epic** — een samenhangende gewenste verandering, inclusief bewijs, UX, techniek, status en
  roadmappositie;
- **Productstory** — een uitvoerbaar zichtbaar onderdeel van precies één epic;
- **Bug** — een reproduceerbare afwijking met bewijs, ernst en herstelstatus;
- **Backlogitem** — de verwijzing van proces 2 naar een productstory of bugfix, met prioriteit en
  uitvoeringsstatus;
- **Backlogvoorraad** — het aantal verzendbare items en of aanvulling nodig is;
- **Prioriteitsbesluit** — de geordende backlog met reden en afgevallen alternatieven;
- **Opleverresultaat** — wat Software Factory heeft teruggegeven en waar het is uitgevoerd;
- **Verificatieresultaat** — het bewijs van proces 3 dat een oplevering wel of niet werkt;
- **Kwaliteitssignaal** — een structureel patroon dat proces 1 kan onderzoeken;
- **Leerresultaat** — door proces 1 gevalideerde productkennis over wat na onderzoek, bouw of
  controle anders bekend is dan daarvoor;
- **Overleg** — agenda, deelnemers, berichten, geraadpleegde bronnen, status en gekoppelde objecten;
- **Overleguitkomst** — notulen met besluiten, open vragen, acties en expliciete geheugenwijzigingen;
- **Productgeheugen** — gedeelde actuele kennis, richting en besluiten voor alle processen.

Onderzoeksdossiers, bronnen, testsessies, agents, prompts, afwegingen, procesgeheugen en agentgeheugen
blijven intern bij hun eigenaar. Een andere module kan wel een gepubliceerde samenvatting lezen, maar
niet het interne object wijzigen.

### Regels voor iedere overdracht

Een overdracht tussen processen is pas compleet wanneer:

1. de output duurzaam is opgeslagen;
2. de bron en aanleiding zichtbaar zijn;
3. precies één module eigenaar en schrijver van het object is;
4. de publieke versie en herkomst expliciet zijn;
5. een volgende module de informatie via een read-only interface kan ophalen;
6. het producerende proces niet hoeft te blijven draaien om de informatie te behouden.

Een overdracht kan ook teruggaan. Als proces 3 meerdere verwante bugs ziet, publiceert het naast de
losse bugs een structureel kwaliteitssignaal voor proces 1. Zo ontstaat terugkoppeling zonder dat
verantwoordelijkheden door elkaar gaan lopen.

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
  vergelijkingsprincipes van proces 1;
- prioriteringspatronen, capaciteitsafspraken en terugkerende blokkades van proces 2;
- teststrategie, testrotatie, risicogebieden en dekkingsgaten van proces 3.

Procesgeheugen bewaart ervaring over **hoe** het proces goed wordt uitgevoerd. De actuele status van
een epic, backlogitem, bug of testsessie blijft op dat productobject staan en wordt niet naar het
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
- relevante epics, backlogitems, bugs, testsessies of onderzoeksdossiers;
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
doorwerking is een expliciete, controleerbare wijziging. Een overleg kan zo tegelijk een epic in
proces 1 bijsturen, een prioriteitsgrens voor proces 2 vastleggen en een testopdracht voor proces 3
opleveren.

## Proces 1 — Productontwikkeling als black box

**Doel:** zelfstandig onderzoeken hoe het product beter kan worden, de verre richting onderhouden,
kansen omzetten in gekozen epics en voldoende uitvoerbare productstories publiceren om de backlog
aan te vullen.

Onderzoek, bewijs, kansvoorstellen, UX-verkenning, technische verkenning en epicvorming zijn interne
onderdelen van dezelfde module. Andere modules zien alleen het gekozen resultaat.

**Uitvoering:** alleen de scheduler roept `runProcessSession()` aan. De module kiest zelf het product
en de interne onderzoeks-, epic- of storytaak die op dat moment de meeste waarde heeft.

### Inputinterface

| Gegeven | Eigenaar en herkomst | Betekenis voor proces 1 |
|---|---|---|
| Productopdracht | productmodule; bevestigd door de Stakeholder | doelgroep, productdoel, harde grenzen, repository en producttoegang |
| Stakeholderrichting | overleg/productmodule | actuele correcties en expliciete beslissingen |
| Backlogvoorraad | proces 2 | hoeveel nieuwe uitvoerbare productstories nodig zijn en met welke urgentie |
| Leverings- en verificatieresultaat | proces 2 en proces 3 | wat eerdere productstories werkelijk hebben opgeleverd |
| Kwaliteitsbeeld en kwaliteitssignaal | proces 3 | structurele problemen die een epic of bijsturing kunnen rechtvaardigen |
| Gebruikerssignaal | inbox/productmodule | feedback, observatie of gebruiksgegeven dat onderzoek verdient |

Externe bronnen worden tijdens een sessie opgehaald en intern als bronregistratie opgeslagen. Ruwe
bronnen, onderzoeksdossiers, hypotheses en kansvoorstellen steken de modulegrens niet over.

### Outputinterface

| Gegeven | Betekenis |
|---|---|
| Droombeeld | geversioneerd beeld van hoe het product zijn opdracht op lange termijn uitzonderlijk goed kan vervullen; zichtbaar voor de Stakeholder |
| Epic | gekozen verandering met gewenste uitkomst, bewijs, actuele UX-richting, succescriteria, status en positie |
| Productstory | klein, zelfstandig bouwbaar en testbaar gedrag met precies één epic, acceptatiecriteria en afhankelijkheden |
| Leerresultaat | gevalideerde productkennis uit onderzoek, oplevering of verificatie, met bron en reikwijdte |

Een productstory krijgt pas de publieke status **Uitvoerbaar** wanneer Software Factory hem zonder
interne kennis van Productontwikkeling kan oppakken. Proces 2 mag de inhoud niet herschrijven; het
bepaalt alleen de plek in de backlog. De interne werking staat in
[Proces 1 — Productontwikkeling](product-factory-v2-proces-1-productontwikkeling.md).

## Proces 2 — Backlog en prioritering als black box

**Doel:** voor ieder actief product, te beginnen met HKH, een geprioriteerde voorraad van ongeveer
tien uitvoerbare backlogitems onderhouden. Een backlogitem is een productstory of bugfix.

**Uitvoering:** alleen de scheduler roept `runProcessSession()` aan. De sessie vult en herordent de
backlog, maar verstuurt zelf niets naar Software Factory.

### Inputinterface

| Gegeven | Eigenaar en herkomst | Betekenis voor proces 2 |
|---|---|---|
| Uitvoerbare productstory | proces 1 | kandidaat voor een nieuw productbacklogitem |
| Uitvoerbare bug | proces 3 | kandidaat voor een bugfixbacklogitem, inclusief ernst en bewijs |
| Productopdracht en Stakeholderrichting | productmodule | grenzen en expliciete prioriteitsaanwijzingen |
| Epic en epicpositie | proces 1 | productwaarde, afhankelijkheden en samenhang van stories |
| Leveringsstatus | SoftwareFactoryDispatcher | of een eerder verzonden item nog open, opgeleverd of geblokkeerd is |
| Verificatieresultaat | proces 3 | of geleverd werk werkelijk afgerond kan worden |

### Outputinterface

| Gegeven | Betekenis |
|---|---|
| Geprioriteerde backlog | geordende lijst van circa tien verzendbare productstories en bugfixes |
| Backlogitem | verwijzing naar precies één bronstory of bug, met prioriteit, reden en uitvoeringsstatus |
| Prioriteitsbesluit | uitlegbare vastlegging waarom een item boven alternatieven staat |
| Backlogvoorraad | aantallen per status en de vlag `aanvullingNodig` |

De standaard lage grens is vier verzendbare items en het streefpeil is tien. Zodra de voorraad op
vier of lager komt, wordt `aanvullingNodig` waar en maakt de scheduler alle drie processen opnieuw
planbaar.
De grens en het streefpeil zijn productconfiguratie; HKH start met vier en tien.

Proces 2 is de enige schrijver van backlogvolgorde en backlogstatus. De interne werking en de
dispatcher staan in
[Proces 2 — Backlog en prioritering](product-factory-v2-proces-2-backlog-en-prioritering.md).

## Proces 3 — Testen en bugs als black box

**Doel:** de werkende applicatie voortdurend onderzoeken, opleveringen verifiëren en aantoonbare
afwijkingen als uitvoerbare bugs publiceren.

**Uitvoering:** alleen de scheduler roept `runProcessSession()` aan. De module kiest zelf één product
en een begrensde testsessie op basis van opleveringen, risico en testrotatie.

### Inputinterface

| Gegeven | Eigenaar en herkomst | Betekenis voor proces 3 |
|---|---|---|
| Testbare productconfiguratie | productmodule | URL's, toegestane accounts, routes en testgrenzen |
| Oplevering en externe storyreferentie | SoftwareFactoryDispatcher | wat nieuw of gewijzigd is en waar het getest kan worden |
| Epic en productstory | proces 1 | gewenst gedrag, succescriteria en acceptatiecriteria |
| Backlog- en uitvoeringsstatus | proces 2 | welke bugfixes of stories wachten, bezig of geleverd zijn |
| Bestaande bugs | proces 3 zelf | wat moet worden hergetest en welke patronen al bekend zijn |
| Stakeholdersignaal | inbox/productmodule | gemelde problemen of risicogebieden |

### Outputinterface

| Gegeven | Betekenis |
|---|---|
| Bug | reproduceerbare afwijking met verwacht en werkelijk gedrag, bewijs, impact, ernst en herstelstatus |
| Verificatieresultaat | oordeel met bewijs over een oplevering of bugfix: geslaagd, afgekeurd of geblokkeerd |
| Kwaliteitsbeeld | actuele samenvatting van dekking, belangrijke risico's en recent onderzochte gebieden |
| Kwaliteitssignaal | structureel patroon dat proces 1 en 2 als productprobleem kunnen onderzoeken |

Proces 3 bepaalt ernst en bewijs, maar niet de backlogpositie. Proces 2 prioriteert een gepubliceerde
bug tussen de productstories. De interne werking staat in
[Proces 3 — Testen en bugs](product-factory-v2-proces-3-testen-en-bugs.md).

## Hoe de drie processen elkaar in beweging houden

De processen vormen geen synchrone keten en roepen elkaar niet aan. Iedere module leest tijdens een
geplande sessie de nieuwste gepubliceerde gegevens:

- Productontwikkeling kan intern nieuw onderzoek naar een epic of story laten doorstromen;
- proces 2 kan via de backlogvoorraad zichtbaar maken dat aanvulling nodig is;
- de tester kan een losse bug of een structureel productprobleem vinden;
- opgeleverde stories kunnen het droombeeld of de epicvolgorde veranderen;
- een afgeronde epic levert altijd een leerresultaat op.

Als HKH vier of minder verzendbare backlogitems over heeft, plant de scheduler nieuwe sessies voor
alle drie de processen. Proces 1 onderzoekt en maakt voldoende stories uitvoerbaar, proces 3 vult zo
nodig bugs aan en proces 2 brengt de voorraad terug naar ongeveer tien. Een module mag ook op haar
normale ritme draaien en eindigt zonder wijziging wanneer er niets nuttigs te doen is.

## Hoe de roadmap werkt

De roadmap is alleen voor epics. Losse ideeën, bugs en productstories staan er niet tussen.

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

Bugs staan naast de roadmap in **Productgezondheid**. Proces 2 brengt productstories en bugfixes pas
samen in de geprioriteerde backlog.

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
- door proces 1 uitwerken tot een kansvoorstel.

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

De epic bezit de actuele UX-richting. Stories verwijzen naar het deel dat zij uitvoeren en maken geen
eigen concurrerende ontwerpen.

Een UX-uitwerking hoeft niet altijd mooi te zijn. Eerst moet zij duidelijk zijn. Voor een belangrijke
of onzekere gebruikersstroom kan Product Factory een klikbaar ontwerp of screenshots laten maken.
De Stakeholder kan daarop reageren voordat er wordt gebouwd.

## Eén gezamenlijke werkstroom

Productstories en bugs komen samen in één duurzame backlog:

```text
Proces 1 ──→ productstory ──┐
                            ├──→ Proces 2 ──→ geprioriteerde backlog (circa 10)
Proces 3 ──→ bug ───────────┘                         │
                                                      ▼
                                      SoftwareFactoryDispatcher
                                                      │
                                                      ▼
                                             Software Factory
```

De dispatcher synchroniseert eerst eerder verstuurd werk. Alleen wanneer Software Factory voor het
product geen openstaand item heeft, verstuurt hij precies het bovenste verzendbare backlogitem. Hij
kan geen item overslaan of de prioriteit wijzigen. De volgende geplande processessie verwerkt de
duurzaam opgeslagen leverings- en verificatieresultaten.

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
- als PO een gevulde, geprioriteerde backlog onderhouden;
- als tester dagelijks en na opleveringen de applicatie onderzoeken;
- een opgeleverd resultaat beoordelen;
- ontbrekende informatie aanwijzen.

De Stakeholder blijft verantwoordelijk voor:

- het brede productdoel en de harde grenzen;
- het corrigeren van de richting wanneer Product Factory het doel verkeerd begrijpt;
- onomkeerbare of kostbare beslissingen;
- gevoelige gegevens en externe toegang.

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
- welk backlogitem bovenaan staat en waarom;
- hoeveel verzendbare backlogitems nog klaarstaan;
- wat bij Software Factory bezig is;
- welke belangrijke bugs openstaan;
- wanneer en wat de tester voor het laatst heeft getest;
- wat we recent hebben geleerd;
- wat recent onderzoek heeft veranderd;
- of ergens hulp nodig is;
- welke overleggen zijn aangevraagd of openstaan;
- welke recente Stakeholderrichting de drie processen beïnvloedt.

### 2. Inbox

Hier staan nieuwe feedback, observaties, ideeën en onderzoeksinzichten. Je kunt ze bekijken,
samenvoegen, afwijzen of door Productontwikkeling laten onderzoeken. Bugs komen vanuit het testproces in
de aparte productgezondheidslijst.

### 3. Plan

Hier staan drie onderdelen bij elkaar:

- het droombeeld als verre richting;
- de epic-roadmap met Nu, Hierna en Later;
- Productgezondheid met bugs;
- de geprioriteerde backlog met productstories en bugfixes.

Ook is zichtbaar welke epic wordt onderzocht, waarom epics wel of niet klaar zijn en hoe Product
Factory de klaarliggende epics heeft geordend.

### 4. Detail

Een epic, productstory, bug of backlogitem heeft één rustige detailpagina met:

- probleem en gewenste uitkomst;
- bewijs en open vragen;
- actuele UX-uitwerking, als die nodig is;
- eventuele bovenliggende epic en relevante storyrelaties;
- reden van de backlogprioriteit;
- voortgang en resultaat;
- beslissingen en leerresultaten.

Technische details zijn beschikbaar via een aparte knop, maar staan standaard dicht.

Overleggen en geheugen hoeven geen extra hoofdscherm te worden. Vanuit Product en ieder detail kan de
Stakeholder een overleg openen of starten. Een aparte secundaire weergave toont alle overleggen en de
geschiedenis van agent-, proces- en productgeheugen, inclusief vervangen en ingetrokken items.

## Eenvoudige statussen voor backlogitems

Epic-statussen worden door proces 1 beheerd. Backlogitems van proces 2 gebruiken een eigen korte
reeks:

- **Verzendbaar** — compleet en geprioriteerd in de backlog;
- **Verstuurd** — door de dispatcher in Software Factory aangemaakt;
- **Bezig** — Software Factory meldt dat eraan wordt gewerkt;
- **Opgeleverd** — Software Factory heeft het resultaat teruggegeven;
- **Controleren** — proces 3 moet het resultaat nog bewijzen;
- **Afgerond** — proces 3 heeft het resultaat goedgekeurd;
- **Geblokkeerd** — kan niet verder, met een zichtbare reden;
- **Gestopt** — bewust niet verder, met een zichtbare reden.

Alleen proces 2 schrijft deze backlogstatus. Een story houdt daarnaast zijn inhoudelijke status in
proces 1 en een bug zijn herstelstatus in proces 3.

## Regels die versie 2 eenvoudig houden

1. Eén plek bevat de actuele productwaarheid.
2. Een los idee is nog geen roadmapitem.
3. Product Factory wacht niet alleen op invoer, maar zoekt zelf naar kansen en bedreigingen.
4. Het droombeeld mag onhaalbaar lijken; de eerstvolgende epic-slice moet wel klein en toetsbaar zijn.
5. De roadmap bevat epics, geen losse stories, bugs of ideeën.
6. Iedere story hoort bij precies één epic.
7. Stories ontstaan pas wanneer een epic bijna aan de beurt is of actief is.
8. De backlog bevat alleen productstories en bugfixes.
9. Proces 2 beheert ongeveer tien geprioriteerde backlogitems; proces 1 kiest de productrichting.
10. De tester levert bewijs en ernst, maar bepaalt niet de uitvoeringsvolgorde.
11. Ieder UX-ontwerp hoort bij één epic of benoemd droomconcept.
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
25. Iedere intelligente procesmodule heeft alleen `runProcessSession()` als agentgestuurde ingang;
    de dispatcher is een afzonderlijke technische adapter zonder productlogica.
26. Iedere gepubliceerde entiteit heeft precies één schrijvende module.
27. Andere modules lezen gegevens alleen via de gepubliceerde Spring Modulith-interface.
28. De dispatcher neemt geen productbesluiten en verstuurt alleen het bovenste backlogitem.
29. Als HKH vier of minder verzendbare items heeft, worden nieuwe processessies planbaar gemaakt.

## Wat we uit versie 1 willen behouden

De tweede versie hoeft niet alles opnieuw uit te vinden. Waardevolle lessen en onderdelen zijn:

- een product kan als gegevens worden toegevoegd, zonder nieuwe productcode in Product Factory;
- Product Factory en Software Factory hebben ieder een duidelijke taak;
- weinig werk tegelijk geeft rust en maakt prioriteiten echt;
- een gevulde backlog voorkomt dat Software Factory op nieuw productwerk hoeft te wachten;
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
- periodiek nieuwe stories maken zonder backlogvraag, duidelijke epic of productkeuze;
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

### Minimaal voor proces 1 — Productontwikkeling

1. een product met een breed productdoel en harde grenzen vastleggen;
2. zelf onderzoek naar soortgelijke en aangrenzende producten starten;
3. bronnen en bruikbare onderzoeksinzichten bewaren;
4. een ambitieus droombeeld maken en met nieuw bewijs aanpassen;
5. intern kansen onderzoeken en tot epics uitwerken;
6. één actuele UX-richting en kleinste eerste slice maken;
7. vroeg technische haalbaarheidsinformatie gebruiken;
8. zichtbaar beoordelen of een epic klaar is;
9. maximaal één epic actief maken;
10. voldoende uitvoerbare productstories publiceren om de backlog aan te vullen.

### Minimaal voor proces 2 — Backlog en prioritering

1. kiezen uit uitvoerbare productstories en bugs;
2. ernst, waarde, afhankelijkheden, blokkades en actueel werk meewegen;
3. een uitlegbaar geprioriteerde backlog van ongeveer tien items onderhouden;
4. bij vier of minder verzendbare items `aanvullingNodig` publiceren;
5. leverings- en verificatiestatus verwerken zonder bronentiteiten te wijzigen;
6. via de eenvoudige dispatcher precies één item tegelijk naar Software Factory sturen.

### Minimaal voor proces 3 — Testen en bugs

1. iedere dag en na een oplevering een gerichte testsessie kunnen starten;
2. belangrijke routes en wisselende onderzoeksthema's bijhouden;
3. een reproduceerbare bug met bewijs en voorgestelde ernst maken;
4. de bug als uitvoerbare input voor proces 2 publiceren;
5. patronen van meerdere bugs als kwaliteitssignaal naar proces 1 sturen.

### Minimaal voor de SoftwareFactoryDispatcher

1. op een vast ritme de status van eerder verstuurde backlogitems synchroniseren;
2. het externe Software Factory-ID bij het backlogitem bewaren;
3. geen nieuw item sturen zolang Software Factory voor het product nog openstaand werk heeft;
4. anders precies het bovenste verzendbare item aanmaken;
5. geen agents, herordening of productbesluit bevatten.

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
7. Welk backlogitem staat bovenaan en waarom?
8. Is dat een productstory of bugfix?
9. Bij welke epic hoort een story?
10. Wat heeft de tester recent onderzocht en gevonden?
11. Wat is het actuele UX-ontwerp en is het een droomconcept of een bouwplan?
12. Wat wordt op dit moment gebouwd?
13. Wat hebben we van de laatste oplevering geleerd?
14. Welke beslissing heeft mijn aandacht nodig?
15. Welk overleg is aangevraagd, waarom en door welke rol?
16. Wat heeft een agent of proces onthouden en wat geldt als gedeelde productwaarheid?
17. Welke van mijn eerdere aanwijzingen zijn nog actief, vervangen of ingetrokken?
18. Hoeveel verzendbare backlogitems staan voor HKH klaar?
19. Welk backlogitem staat open in Software Factory en wat is de laatste leveringsstatus?

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
- Wanneer mag een actieve epic worden onderbroken of gestopt?
- Hoe vaak moet proces 2 naast de lage-voorraadtrigger opnieuw prioriteren?
- Hoe bepaalt proces 2 de normale balans tussen productstories en bugfixes?
- Welke delen van de applicatie test de tester iedere dag en welke in een langere roulatie?
- Wanneer worden meerdere losse bugs samen een intern kansvoorstel voor Productontwikkeling?
- Welke UX-vorm is minimaal nodig voordat nieuwe zichtbare functionaliteit gebouwd mag worden?
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

De black-boxinterfaces hierboven zijn leidend. De interne agents, volgorde, parallelle stappen,
interne entiteiten en sessieregels staan in drie afzonderlijke documenten:

- [Proces 1 — Productontwikkeling](product-factory-v2-proces-1-productontwikkeling.md)
- [Proces 2 — Backlog en prioritering](product-factory-v2-proces-2-backlog-en-prioritering.md)
- [Proces 3 — Testen en bugs](product-factory-v2-proces-3-testen-en-bugs.md)
