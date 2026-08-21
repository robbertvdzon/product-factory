# Product Factory v2 — eerste opzet

Status: eerste denkversie. Dit document beschrijft nog geen definitief ontwerp.

## Het idee in één zin

De eigenaar geeft een brede opdracht aan een product. Product Factory onderzoekt daarna op eigen
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

### Productdoel

Het productdoel is de brede opdracht van de eigenaar. Het vertelt voor wie het product is en welk
resultaat het product zo goed mogelijk moet bereiken.

Een productdoel is bewust algemeen. Bijvoorbeeld:

> Help een klein softwareteam om met zo min mogelijk gedoe goede software te maken.

Het productdoel schrijft nog geen schermen, functies of technische oplossing voor. Het verandert
niet iedere week. Alleen de eigenaar kan het doel of de harde grenzen wezenlijk veranderen.

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

### Verbeterpunt

Een verbeterpunt is een gekozen probleem in het huidige product. Het beschrijft:

- wat er misgaat of lastig is;
- voor wie dat een probleem is;
- hoe belangrijk het is;
- waaraan we straks zien dat het beter is.

### Productstap

Een productstap is een gekozen nieuwe mogelijkheid die bij het productdoel past en het product dichter
bij het droombeeld brengt. Het beschrijft:

- welk probleem of welke behoefte we aanpakken;
- voor wie we dit doen;
- welke uitkomst we willen bereiken;
- wat we nog niet zeker weten;
- welke kleine eerste versie we kunnen proberen.

In vaktaal wordt dit soms een *product bet* genoemd: we denken dat deze stap waarde heeft, maar we
willen dat nog bewijzen.

### Werkopdracht

Een werkopdracht is een klein, duidelijk stuk werk dat Software Factory kan bouwen en testen. Eén
verbeterpunt of productstap kan uit meerdere werkopdrachten bestaan, maar die gaan niet allemaal
tegelijk van start.

### Leerresultaat

Na de oplevering leggen we kort vast:

- wat is gebouwd;
- of het werkt zoals bedoeld;
- wat gebruikers of tests laten zien;
- wat we nu anders weten;
- wat de logische volgende keuze is.

Dit leerresultaat wordt gebruikt bij de volgende keuze.

## Hoe een product begint

Een nieuw product start met een kort gesprek. We leggen alleen vast wat nodig is om goede keuzes te
kunnen maken:

1. Voor wie is het product?
2. Welk probleem lost het op?
3. Wat kan het product nu al?
4. Wat is de brede opdracht van het product?
5. Welke grenzen mogen we niet overschrijden?
6. Waar staat de productcode?

De eigenaar bekijkt en bevestigt het productdoel en de harde grenzen. Daarna doet Product Factory
zelf een eerste onderzoek. Zij maakt op basis daarvan een eerste droombeeld, kansen en mogelijke
eerste stappen. De eigenaar mag bijsturen, maar hoeft niet vooraf te bedenken hoe de uiteindelijke oplossing
eruit moet zien.

## Hoe signalen binnenkomen

Alle losse informatie komt eerst in één inbox. Product Factory doet niet alsof ieder idee meteen goed
of belangrijk is.

Een signaal kan komen van:

- de producteigenaar;
- gebruikersfeedback;
- een test;
- een foutmelding;
- eerder geleverd werk;
- onderzoek door een agent;
- Software Factory.

Product Factory probeert dubbele signalen bij elkaar te zetten. Zij mag een signaal samenvatten en
aanvullen, maar niet stilletjes promoveren tot gepland werk.

## Hoe Product Factory zelf op onderzoek uitgaat

Product Factory wacht niet tot de eigenaar een idee invoert. Zij gaat regelmatig en bij belangrijke
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
probleem het oplost en welke kleine productstap nu al nuttig of leerzaam is.

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

## Hoe we werk kiezen

Op een vast keuzemoment bekijkt Product Factory vier dingen:

1. het productdoel en het actuele droombeeld;
2. de huidige staat van het product;
3. nieuwe signalen en onderzoeksinzichten;
4. wat we van eerder werk hebben geleerd.

Daarna maakt zij twee korte voorstellen:

- het belangrijkste verbeterpunt;
- de beste volgende productstap.

Bij ieder voorstel staat in gewone taal:

- waarom dit nu belangrijk is;
- welk bewijs ervoor is;
- wat het ongeveer oplevert;
- wat het grootste risico is;
- waarom andere kandidaten nu niet gekozen zijn.

Binnen het productdoel en de harde grenzen mag Product Factory zelf de beste omkeerbare vervolgstap
kiezen. Zij legt de keuze en de afgevallen alternatieven kort uit. De eigenaar kan altijd bijsturen,
uitstellen of stoppen, maar Product Factory hoeft niet bij ieder normaal besluit op toestemming te
wachten.

Bij een dure, gevoelige, moeilijk terug te draaien of doelveranderende keuze vraagt zij wel eerst om
hulp.

## De route voor Verbeteren

Een verbeterpunt volgt een korte route:

1. **Begrijpen** — wat gaat er precies mis?
2. **Oorzaak zoeken** — waarom gebeurt dit?
3. **Kleinste oplossing kiezen** — wat is genoeg om het merkbaar beter te maken?
4. **Werkopdracht maken** — wat moet Software Factory bouwen en testen?
5. **Controleren** — is het probleem echt opgelost en is niets anders stukgegaan?
6. **Leren** — sluiten we het punt of is vervolgwerk nodig?

Niet ieder verbeterpunt heeft een nieuw UX-ontwerp nodig. Een zichtbare verandering krijgt wel een
kleine UX-uitwerking voordat er een werkopdracht wordt gemaakt.

## De route voor Vernieuwen

Een productstap volgt een andere route:

1. **Probleem kiezen** — welk gebruikersprobleem willen we oplossen?
2. **Bewijs verzamelen** — weten we genoeg om hier tijd aan te besteden?
3. **UX verkennen** — hoe zou een gebruiker dit begrijpen en gebruiken?
4. **Kleine eerste versie kiezen** — wat is de kleinste versie die echte waarde of nieuw inzicht geeft?
5. **Werkopdracht maken** — wat moet Software Factory als eerste bouwen?
6. **Resultaat bekijken** — werkt het en helpt het de gebruiker?
7. **Leren** — doorgaan, aanpassen of stoppen?

Een groot idee wordt dus niet in één keer omgezet in een stapel stories. We bouwen eerst een kleine,
bruikbare stap. Na de oplevering bepalen we met nieuwe kennis wat daarna verstandig is.

## Hoe de roadmap werkt

De roadmap is alleen voor gekozen nieuwe productstappen. Losse ideeën staan in de inbox, niet op de
roadmap.

De roadmap heeft vier eenvoudige vakken:

- **Nu** — de ene productstap waaraan we werken;
- **Hierna** — een klein aantal waarschijnlijke volgende stappen;
- **Later** — interessante stappen die nog niet dichtbij genoeg zijn;
- **Niet gekozen** — stappen die bewust zijn gestopt of uitgesteld, met de reden erbij.

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

Verbeterpunten zijn naast de roadmap zichtbaar in een eigen lijst. Daardoor blijft duidelijk welk
werk het huidige product gezond houdt en welk werk het product uitbreidt.

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
- uitwerken tot een productstap.

Een agent mag ideeën onderzoeken en vergelijken. De agent mag niet doen alsof een mooi geschreven
idee daarom automatisch een goed productbesluit is.

## Hoe UX-ontwerpen worden behandeld

UX is geen losse verzameling documenten. Een UX-uitwerking hoort altijd bij één verbeterpunt, één
productstap of één duidelijk benoemd onderdeel van het droombeeld.

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
vrij en ambitieus zijn, maar niemand mag het verwarren met een gepland ontwerp. Wanneer een klein
deel echt gebouwd gaat worden, krijgt die productstap een eigen, concretere UX-uitwerking.

Een UX-uitwerking hoeft niet altijd mooi te zijn. Eerst moet zij duidelijk zijn. Voor een belangrijke
of onzekere gebruikersstroom kan Product Factory een klikbaar ontwerp of screenshots laten maken.
De producteigenaar kan daarop reageren voordat er wordt gebouwd.

## Eén gezamenlijke werkstroom

Verbeteren en Vernieuwen komen samen zodra er een werkopdracht klaar is:

```text
Verbeterpunt ──→ werkopdracht ──┐
                                ├──→ Software Factory ──→ resultaat ──→ leren
Productstap  ──→ werkopdracht ──┘
```

Er gaat normaal maar één werkopdracht tegelijk naar Software Factory. Pas wanneer die klaar is of
bewust is gepauzeerd, start de volgende. Zo blijft duidelijk wat de hoogste prioriteit heeft.

## Wat de mens doet en wat agents doen

Agents helpen met:

- informatie samenvatten;
- dubbele signalen vinden;
- op eigen initiatief onderzoek doen naar soortgelijke producten en aangrenzende oplossingen;
- een ambitieus droombeeld maken en met nieuwe kennis bijwerken;
- voorstellen vergelijken;
- een gebruikersroute uitwerken;
- een kleine werkopdracht schrijven;
- een opgeleverd resultaat beoordelen;
- ontbrekende informatie aanwijzen.

De producteigenaar blijft verantwoordelijk voor:

- het brede productdoel en de harde grenzen;
- het corrigeren van de richting wanneer Product Factory het doel verkeerd begrijpt;
- onomkeerbare of kostbare beslissingen;
- gevoelige gegevens en externe toegang.

Product Factory is binnen die opdracht verantwoordelijk voor het actuele droombeeld, onderzoek, de
gewone productkeuzes en de balans tussen Verbeteren en Vernieuwen. Zij legt deze keuzes uit en maakt
ze zichtbaar, zodat de eigenaar kan ingrijpen zonder ieder stapje te hoeven besturen.

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

De vraag moet kort zijn en altijd mogelijke keuzes met gevolgen tonen.

## De belangrijkste schermen

Versie 2 begint met hooguit vier hoofdschermen.

### 1. Product

Hier zie je in één oogopslag:

- het productdoel en een korte versie van het droombeeld;
- wat nu wordt verbeterd;
- welke nieuwe productstap nu wordt gebouwd;
- wat bij Software Factory bezig is;
- wat we recent hebben geleerd;
- wat recent onderzoek heeft veranderd;
- of ergens hulp nodig is.

### 2. Inbox

Hier staan nieuwe bugs, feedback, observaties, ideeën en onderzoeksinzichten. Je kunt ze bekijken,
samenvoegen, afwijzen of laten uitwerken.

### 3. Plan

Hier staan twee banen naast elkaar:

- Verbeteren;
- Vernieuwen, met Nu, Hierna en Later.

Boven deze banen staat het droombeeld als richting, niet als extra lijst gepland werk.

### 4. Detail

Een verbeterpunt of productstap heeft één rustige detailpagina met:

- probleem en gewenste uitkomst;
- bewijs en open vragen;
- actuele UX-uitwerking, als die nodig is;
- gekozen kleine werkopdracht;
- voortgang en resultaat;
- beslissingen en leerresultaten.

Technische details zijn beschikbaar via een aparte knop, maar staan standaard dicht.

## Eenvoudige statussen

We gebruiken voor verbeterpunten en productstappen zoveel mogelijk dezelfde statussen:

- **Nieuw** — nog niet bekeken;
- **Onderzoeken** — we missen nog informatie;
- **Klaar om te kiezen** — er ligt een duidelijk voorstel;
- **Gepland** — bewust gekozen;
- **Bezig** — wordt uitgewerkt of gebouwd;
- **Controleren** — opgeleverd, maar het resultaat moet nog worden bekeken;
- **Klaar** — gewenste uitkomst bereikt;
- **Gestopt** — bewust niet verder, met reden.

Een status beschrijft de toestand van het werk. Een agentnaam of processtap is geen productstatus.

## Regels die versie 2 eenvoudig houden

1. Eén plek bevat de actuele productwaarheid.
2. Een los idee is nog geen roadmapitem.
3. Product Factory wacht niet alleen op invoer, maar zoekt zelf naar kansen en bedreigingen.
4. Het droombeeld mag onhaalbaar lijken; de eerstvolgende productstap moet wel klein en toetsbaar zijn.
5. Ieder UX-ontwerp hoort bij één concreet verbeterpunt, productstap of benoemd droomconcept.
6. Een droomconcept is nooit stilletjes een gepland ontwerp.
7. Per onderwerp is maar één UX-versie actueel.
8. We bouwen kleine stappen en leren na iedere oplevering.
9. Er is weinig werk tegelijk bezig.
10. Kritieke fouten gaan voor, maar onderhoud verdringt vernieuwing niet stilletjes.
11. Agents nemen gewone omkeerbare productbesluiten en leggen die uit.
12. Een verborgen score neemt geen groot of onomkeerbaar productbesluit.
13. Het gewone scherm toont producttaal, geen interne agent- of databasetaal.
14. Nieuwe functies komen alleen in Product Factory als ze een hoofdvraag aantoonbaar eenvoudiger
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
- de zelfstandige agentworker kan een nuttige technische basis blijven.

We nemen deze onderdelen pas over nadat is vastgesteld dat ze in het eenvoudige v2-proces passen.

## Wat niet automatisch meegaat naar versie 2

We nemen niet standaard mee:

- shadow-iteraties als zichtbaar productbegrip;
- meerdere concurrerende manieren om een roadmap te maken;
- automatisch periodiek nieuwe stories maken zonder een duidelijke keuze;
- epics, capabilities, horizons, ranks en scores tegelijk;
- agents als hoofdnavigatie;
- ruwe agentuitvoer als normaal productdocument;
- losse UX-documenten zonder eigenaar of actuele status;
- alle bestaande databasevelden en historie;
- ieder scherm of iedere functie uit het huidige dashboard.

Ieder onderdeel uit versie 1 moet opnieuw bewijzen dat het nodig is.

## De kleinste bruikbare versie

De eerste bruikbare versie moet meteen bewijzen dat Product Factory meer is dan een takenlijst. Zij
moet twee kleine lussen goed kunnen uitvoeren.

### Lus 1: zelf richting ontdekken

1. een product met een breed productdoel en harde grenzen vastleggen;
2. zelf een klein onderzoek naar soortgelijke en aangrenzende producten uitvoeren;
3. bronnen en bruikbare onderzoeksinzichten bewaren;
4. een ambitieus eerste droombeeld maken;
5. vanuit dat beeld kansen en vragen voorstellen;
6. het droombeeld met nieuw bewijs kunnen aanpassen.

### Lus 2: verbeteren, bouwen en leren

1. signalen uit het product en uit eigen onderzoek verzamelen;
2. zelf kiezen of Verbeteren of Vernieuwen nu de beste volgende stap bevat;
3. een klein en duidelijk voorstel maken;
4. zo nodig één actuele UX-uitwerking maken;
5. één kleine werkopdracht naar Software Factory sturen;
6. het resultaat ophalen en controleren;
7. een leerresultaat vastleggen;
8. de volgende keuze en zo nodig het droombeeld bijwerken.

De eerste versie hoeft nog niet iedere mogelijke bron of vorm van automatisering te ondersteunen.
Zelf onderzoek starten, dromen en een gewone vervolgstap kiezen horen echter wel bij de kern en zijn
geen uitbreidingen voor later.

## Wanneer versie 2 geslaagd is

De producteigenaar moet zonder technische uitleg binnen één minuut antwoord kunnen geven op:

1. Wat is de brede opdracht van dit product?
2. Hoe ziet Product Factory de ideale verre toekomst van dit product?
3. Wat heeft Product Factory onlangs buiten het project geleerd?
4. Wat werkt er nu niet goed genoeg?
5. Welke nieuwe stap bouwen we nu?
6. Waarom hebben we juist deze dingen gekozen?
7. Wat is het actuele UX-ontwerp en is het een droomconcept of een bouwplan?
8. Wat wordt op dit moment gebouwd?
9. Wat hebben we van de laatste oplevering geleerd?
10. Welke beslissing heeft mijn aandacht nodig?

Als die antwoorden verspreid staan over meerdere schermen, documenten of agentruns, is het ontwerp
nog niet eenvoudig genoeg.

## Open keuzes voor de volgende versie van dit document

Voordat we gaan bouwen, moeten we nog samen kiezen:

- Hoe vrij mag Product Factory het algemene productdoel interpreteren?
- Welke beslissingen mag Product Factory zelfstandig nemen en welke moeten altijd langs de eigenaar?
- Hoe vaak en naar welke bronnen moet Product Factory uit zichzelf onderzoek doen?
- Hoe voorkomen we dat onderzoek vooral bestaande producten kopieert in plaats van nieuwe kansen vindt?
- Hoe ver en hoe wild mag het droombeeld gaan?
- Wanneer is er genoeg bewijs om het droombeeld wezenlijk te veranderen?
- Hoe vaak maken we een nieuwe keuze: na iedere oplevering, op een vast moment of beide?
- Hoe bepalen we de normale balans tussen Verbeteren en Vernieuwen?
- Welke UX-vorm is minimaal nodig voordat nieuwe zichtbare functionaliteit gebouwd mag worden?
- Welke gegevens uit versie 1 zijn echt waardevol genoeg om over te nemen?
- Bouwen we v2 in deze repository naast v1, of eerst in een aparte repository?

Deze keuzes moeten eerst in gewone taal beantwoord zijn. Daarna pas maken we het technische ontwerp.
