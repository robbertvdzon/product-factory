# Plan: hogere storyopbrengst uit productcycli

## Aanleiding

Een productcyclus gebruikt meerdere kostbare agentrollen maar kan na maximaal drie schrijf- en
kritiekrondes eindigen zonder leverbare story. Dat is soms terecht, bijvoorbeeld bij een werkelijk
onveilig voorstel of wanneer een noodzakelijke eigenaarbeslissing ontbreekt. In andere gevallen
gaat bruikbaar werk verloren door herstelbare modelslordigheden, een te brede criticusbeoordeling,
een fout-positieve autonomiegate, volledige herschrijving per revisie of één slechte kandidaat die
de gehele batch blokkeert.

Dit plan verhoogt de kans op ten minste één kleine, veilige, bruikbare story zonder de privacy-,
security- en autonomiebescherming af te zwakken. “Altijd een story” is nadrukkelijk niet het doel.

## Doel

- Herstelbare uitvoerfouten verbruiken geen inhoudelijke criticusronde.
- De criticus blokkeert alleen materiële problemen.
- Revisies lossen gericht bestaande blockers op en introduceren zo min mogelijk nieuwe tekst.
- Een goede kandidaat kan doorgaan wanneer een onafhankelijke andere kandidaat revisie nodig
  heeft.
- Een bruikbaar concept blijft hervatbaar in plaats van verloren te gaan.
- Het dashboard legt begrijpelijk uit waarom een cyclus wel of geen story leverde.

## Uitgangspunten

1. Veiligheid en herleidbaarheid blijven harde grenzen.
2. Een kleine veilige story heeft voorkeur boven een grote perfecte story die niet leverbaar wordt.
3. Waarschuwingen zijn zichtbaar maar blokkeren niet.
4. Alleen materiële, aantoonbare problemen krijgen `BLOCKING`.
5. Een modelaanname wordt niet vanzelf productbeleid.
6. Duplicatie is meestal een signaal voor vergelijking of samenvoeging, geen automatische
   afwijzingsgrond.
7. Iedere automatische beslissing moet achteraf verklaarbaar zijn.

## Nieuwe uitkomstcategorieën

Vervang het te grove onderscheid `REVISE`/`REJECT` intern door preciezere oorzaken. De publieke
statussen kunnen voorlopig compatibel blijven.

- `OUTPUT_REPAIR`: beschadigde, afgebroken of redactionele modeloutput;
- `CANDIDATE_REVISE`: inhoudelijk oplosbaar probleem in één kandidaat;
- `DIRECTION_REVISE`: gekozen productrichting is intern tegenstrijdig of onbewezen;
- `RESEARCH_GAP`: concreet noodzakelijk bewijs ontbreekt;
- `OWNER_DECISION_REQUIRED`: alleen een eigenaar kan een echte beleidskeuze maken;
- `DUPLICATE_REVIEW`: mogelijke overlap die vergelijking vraagt;
- `REJECT`: fundamenteel onveilig, onwenselijk of niet passend;
- `ACCEPT_WITH_WARNINGS`: leverbaar met niet-blokkerende aandachtspunten;
- `ACCEPT`: leverbaar zonder resterende waarschuwingen.

Deze categorieën bepalen naar welke rol de cyclus teruggaat en voorkomen dat ieder probleem bij de
storywriter belandt.

## Werkstroom na verbetering

1. RESEARCHER, PRODUCT_OWNER en UX_DESIGNER leveren hun bestaande artefacten.
2. Een deterministische preflight controleert samenhang en onbewezen harde beleidsclaims.
3. STORY_WRITER maakt kandidaten.
4. Een deterministische outputvalidator herstelt of laat kapotte uitvoer opnieuw genereren.
5. CRITIC beoordeelt kandidaten afzonderlijk volgens een materialiteitscontract.
6. Geaccepteerde onafhankelijke kandidaten worden bewaard; alleen kandidaten met blockers gaan
   gericht terug.
7. De orchestrator kiest per probleem het juiste revisieniveau.
8. Bij uitputting van normale pogingen volgt eventueel één begrensde laatste reparatie.
9. De cyclus publiceert iedere leverbare kandidaat en bewaart overige kandidaten als hervatbaar
   concept, definitieve afwijzing of informatieve overlap.

## Fase 0 — meten en oorzaken vastleggen

Voeg eerst observatie toe zonder het gedrag te veranderen.

Registreer per kandidaat en poging:

- oorspronkelijke en definitieve uitkomst;
- blocker-categorie en herkomst (`RUNTIME`, `CRITIC`, `POLICY_CONFLICT`);
- welke rol het probleem introduceerde;
- of de blocker in de volgende ronde is opgelost;
- nieuwe blockers die pas tijdens revisie ontstonden;
- tekstuele overeenkomst tussen opeenvolgende kandidaten;
- reden waarom geen kandidaat is gepubliceerd;
- overlap met bestaande kandidaten en hun status.

Maak in het dashboard en via metrics onderscheid tussen technische fouten, inhoudelijke afwijzing,
herstelbare uitvoerfouten en ontbrekende eigenaarbesluiten.

**Klaar wanneer:** voor iedere cyclus zonder story exact zichtbaar is welke minimale blokkade de
publicatie verhinderde en in welke ronde die ontstond.

## Fase 1 — deterministische uitvoerkwaliteit

### Story-outputvalidator

Breid `validateStories` uit met detectie van:

- redactionele metatekst zoals “Need Dutch only”, “fix mentally”, “TODO for model” en vergelijkbare
  zelfinstructies;
- afgebroken zinnen en ongebalanceerde haakjes of aanhalingstekens;
- velden die exact op een schemalimiet eindigen;
- lege of uitsluitend uit witruimte bestaande lijstitems;
- acceptatiecriteria zonder toetsbare uitkomst;
- onbedoelde vermenging van talen wanneer het product Nederlands als voertaal gebruikt;
- buitensporig lange samengestelde acceptatiecriteria die beter gesplitst kunnen worden.

Een fout leidt tot `OUTPUT_REPAIR` en een korte correctieprompt. Deze poging telt niet als
inhoudelijke criticusrevisie. Bewaar de afgekeurde ruwe uitvoer wel voor diagnose.

### Andere rollen

Pas hetzelfde retrymechanisme dat al voor RESEARCHER, PRODUCT_OWNER en UX_DESIGNER bestaat ook toe
op inhoudelijke schema-/validatiefouten van STORY_WRITER en CRITIC. Houd technische retries apart
van inhoudelijke storyrevisies.

### Autonomiegate

Vervang de huidige eenvoudige trefwoordregex door een expliciete classificatie van vereiste
handelingen. Minimaal:

- herken ontkenningen zoals “zonder menselijke controle”;
- accepteer geautomatiseerde widget-, unit-, integratie-, semantiek- en browsertests;
- blokkeer alleen wanneer de eigenaar daadwerkelijk een actie moet uitvoeren;
- voeg een machineleesbare reden en de gematchte passage toe;
- laat twijfel niet automatisch als eigenaarafhankelijkheid gelden.

**Klaar wanneer:** de bekende redactionele fouten en de zin “zonder menselijke controle” in tests
geen inhoudelijke revisiecyclus meer verspillen.

## Fase 2 — criticuscontract en materialiteit

### Wanneer iets blokkeert

CRITIC mag `BLOCKING` alleen gebruiken voor:

- concreet privacy- of securityrisico;
- strijd met een aantoonbaar actieve, toepasselijke productregel;
- technisch niet-uitvoerbare of niet-verifieerbare kernacceptatie;
- fundamenteel verkeerde scope of richting;
- een noodzakelijke maar ontbrekende eigenaarbeslissing;
- beschadigde uitvoer die de preflight niet veilig kon repareren;
- een afhankelijkheidsconflict waardoor uitvoering onmogelijk is.

De volgende zaken zijn normaal `WARNING` of `INFO`:

- mogelijke toekomstige uitbreiding;
- aanvullende bronverrijking;
- een extra randgeval buiten de afgesproken MVP-scope;
- cosmetische voorkeur;
- onzekerheid die de story al veilig fail-closed afhandelt;
- gedeeltelijke overlap met bestaand werk;
- documentatieverbetering die niet nodig is om de story correct uit te voeren.

### Bron- en gezagscontrole

Een criticusbezwaar tegen beleid moet verwijzen naar een actief contextitem. Een uitspraak van
RESEARCHER, PRODUCT_OWNER of UX_DESIGNER is niet zelfstandig bindend. Bij een onbewezen upstream
aanname kiest CRITIC één van deze uitkomsten:

1. schrap de aanname en beoordeel de kleinere veilige story;
2. stuur terug naar PRODUCT_OWNER (`DIRECTION_REVISE`);
3. vraag een echte eigenaarbeslissing (`OWNER_DECISION_REQUIRED`).

De criticus mag niet eisen dat STORY_WRITER zelf nieuw juridisch of productbeleid ontwerpt.

### Kleinste veilige variant

Iedere blokkerende beoordeling bevat, wanneer mogelijk, een `minimumSafeChange`. Voorbeelden:

- toon alleen metadata;
- kopieer geen media;
- toon uitsluitend een gevalideerde externe link;
- laat een omstreden bronsoort buiten scope;
- gebruik een bestaande fail-closed status;
- splits één onafhankelijk deel af.

**Klaar wanneer:** waarschuwingen niet langer een `REVISE` veroorzaken en een onbewezen
Product Owner-aanname niet als nieuwe harde productregel bij STORY_WRITER terechtkomt.

## Fase 3 — gerichte en adaptieve revisie

### Patch in plaats van herschrijven

Laat STORY_WRITER bij revisie alleen de kandidaten en velden wijzigen waarop blockers betrekking
hebben. Voeg aan het responseschema toe:

- `resolvedIssueIds`;
- `changedFields`;
- `changeRationale`.

De runtime vergelijkt oud en nieuw en weigert onverklaarde wijzigingen buiten de gevraagde velden.
Voor een echt nieuwe richting mag een volledige herschrijving nog wel, maar alleen na
`DIRECTION_REVISE`.

### Terug naar de juiste rol

- `OUTPUT_REPAIR` -> dezelfde rol met technische correctie;
- `CANDIDATE_REVISE` -> STORY_WRITER;
- `DIRECTION_REVISE` -> PRODUCT_OWNER en daarna de afhankelijke rollen;
- `RESEARCH_GAP` -> RESEARCHER, uitsluitend voor de concrete ontbrekende vraag;
- `OWNER_DECISION_REQUIRED` -> overleg/human action;
- `REJECT` -> geen automatische herhaling van dezelfde richting.

### Voortgang en stagnatie

Bereken na iedere ronde:

- aantal opgeloste blockers;
- aantal resterende blockers;
- aantal nieuw geïntroduceerde blockers;
- inhoudelijke verandering van de kandidaat;
- herhaling van hetzelfde bezwaar.

Als de criticus na een verbetering alleen nieuwe niet-materiële punten introduceert, worden die
waarschuwingen. Als dezelfde fundamentele blocker niet verandert, stop dan vroeg en escaleer de
juiste oorzaak in plaats van blind meer tekst te genereren.

### Laatste reparatie

Sta na de normale drie inhoudelijke pogingen één extra reparatie toe wanneer:

- maximaal twee lokale, oplosbare blockers resteren;
- er aantoonbare voortgang is;
- geen privacy- of securitybeleid hoeft te worden bedacht;
- en de wijziging tot specifiek benoemde velden beperkt kan blijven.

Deze ronde is geen vierde volledige herontwerpronde.

**Klaar wanneer:** een oplossing van eerdere feedback niet meer verloren gaat door een onnodige
volledige herschrijving en simpele restfouten nog één begrensde herstelmogelijkheid krijgen.

## Fase 4 — kandidaatgerichte acceptatie en duplicaten

### Publiceer per kandidaat

Maak het criticusoordeel primair kandidaatgericht. Bij drie onafhankelijke kandidaten kan de cyclus
bijvoorbeeld eindigen met:

- kandidaat A: `ACCEPT`;
- kandidaat B: `ACCEPT_WITH_WARNINGS`;
- kandidaat C: `CANDIDATE_REVISE`.

A en B mogen worden gepubliceerd. C blijft als hervatbaar concept achter. Het overall oordeel is
een samenvatting en mag geaccepteerde onafhankelijke kandidaten niet meer automatisch blokkeren.

Controleer vóór gedeeltelijke publicatie:

- `dependsOn`-relaties;
- gedeelde noodzakelijke infrastructuur;
- tegenstrijdige wijzigingen;
- en of de geaccepteerde kandidaat zelfstandig begrijpelijk en uitvoerbaar blijft.

### Duplicaatbeleid

Een duplicaat is niet automatisch fout:

- **Exact dezelfde, reeds opgeleverde story:** niet opnieuw leveren; markeer als `ALREADY_DELIVERED`
  en beschouw dit niet als mislukte cyclus.
- **Overlap met een afgewezen of ingetrokken kandidaat:** toestaan; een betere nieuwe uitwerking kan
  juist gewenst zijn.
- **Overlap met een nog open kandidaat:** waarschuwing en voorstel tot samenvoegen, geen
  automatische afwijzing.
- **Hetzelfde doel met een andere of verbeterde aanpak:** normaal beoordelen en toestaan.
- **Herhaling omdat een eerdere oplossing niet voldoet:** toestaan en eventueel prioriteren.
- **Twee kandidaten in dezelfde batch die feitelijk hetzelfde werk vragen:** samenvoegen of één
  kiezen om dubbele gelijktijdige uitvoering te voorkomen.

Vervang de huidige binaire fingerprintbeslissing daarom door een overlapclassificatie met verwijzing
naar de bestaande kandidaat, diens status en leveringsresultaat. Alleen exact reeds geleverd werk
wordt automatisch niet opnieuw geleverd.

**Klaar wanneer:** één slechte kandidaat niet langer alle goede onafhankelijke kandidaten tegenhoudt
en overlap met oud afgewezen werk geen onterechte blokkade veroorzaakt.

## Fase 5 — hervatbare revisies

Maak `NEEDS_REVISION` een hervatbare toestand. Bewaar een revisiedossier met:

- de laatste kandidaatversie;
- definitief opgeloste issues;
- resterende blockers;
- de contextversie waarop de beoordeling is gebaseerd;
- het juiste hervatpunt in de agentketen;
- en een uiterste geldigheidsdatum of contextinvalidatieconditie.

Het dashboard krijgt “Hervat revisie”. De gebruiker kan optioneel een aanvullende focus of een
eigenaarbesluit meegeven. De runtime hergebruikt geldig onderzoek en laat alleen noodzakelijke
rollen opnieuw lopen.

Automatisch hervatten is alleen toegestaan wanneer de actieve context sinds de beoordeling niet
materieel is gewijzigd. Anders wordt eerst herbeoordeling van de richting gevraagd.

**Klaar wanneer:** een bijna goede kandidaat verder kan vanaf de resterende blocker zonder opnieuw
de volledige onderzoeksketen te betalen.

## Fase 6 — dashboard en productfeedback

Toon per cyclus:

- aantal voorgesteld, geaccepteerd, geleverd, te herzien, afgewezen en overgeslagen als reeds
  geleverd;
- de belangrijkste resterende blocker;
- of het probleem uit onderzoek, richting, story, criticus of runtime kwam;
- voortgang per revisieronde;
- kandidaatstatus en actie “Hervat revisie”;
- een aparte melding “geen nieuwe story nodig: exact werk is al geleverd”.

Maak zichtbaar dat een `NEEDS_REVISION`-cyclus wel bruikbaar onderzoek of een herstelbaar concept
kan hebben opgeleverd. Gebruik “geen story” alleen wanneer werkelijk geen kandidaat bestaat;
gebruik anders een preciezere formulering.

## Teststrategie

### Deterministische tests

- Redactionele metatekst wordt vóór CRITIC afgekeurd en gerepareerd.
- Een exact op de limiet afgebroken criterium wordt gedetecteerd.
- “Zonder menselijke controle” veroorzaakt geen autonomierevisie.
- Een echte handmatige eigenaarstest veroorzaakt die wel.
- `WARNING` en `INFO` blokkeren publicatie niet.
- Een blocker zonder actieve beleidsbron wordt teruggebracht tot upstream aanname.

### Revisietests

- Alleen aangewezen velden wijzigen tijdens gerichte revisie.
- Opgeloste issue-ID's keren niet zonder nieuwe onderbouwing terug.
- Nieuwe cosmetische punten na de laatste ronde blijven waarschuwingen.
- De extra reparatie wordt alleen bij lokale oplosbare blockers uitgevoerd.
- Stagnatie leidt tot de juiste escalatie en niet tot eindeloze retries.

### Kandidaat- en duplicatietests

- Twee geaccepteerde kandidaten publiceren ondanks een derde revisiekandidaat.
- Een afhankelijk geaccepteerde kandidaat wacht wanneer zijn dependency niet is geaccepteerd.
- Exact reeds geleverd werk wordt niet opnieuw geleverd maar telt niet als falen.
- Overlap met een oude afgewezen kandidaat is toegestaan.
- Een verbeterde aanpak met hetzelfde doel wordt normaal beoordeeld.

### End-to-end regressies

Gebruik geanonimiseerde varianten van de geobserveerde situaties:

- Product Factory: redactionele metatekst plus de ontkenning “zonder menselijke controle”;
- HKH: een Product Owner introduceert een onbewezen algemene beleidsregel terwijl een kleinere
  veilige metadata-/linkstory mogelijk is;
- een batch met één goede en één slechte kandidaat;
- een hervatte cyclus die na één gerichte reparatie accepteert.

## Gefaseerde uitrol

1. Alleen meten en tonen.
2. Outputvalidator en autonomiegate activeren.
3. Nieuw criticuscontract eerst in shadow mode vergelijken met de oude uitkomst.
4. Gerichte revisie en laatste reparatie activeren.
5. Kandidaatgerichte gedeeltelijke publicatie activeren met featureflag.
6. Duplicaatbeleid omzetten van blokkade naar classificatie.
7. Hervatbare revisies toevoegen.
8. Na voldoende meetdata oude batchbrede blokkadelogica verwijderen.

Per fase worden acceptatiegraad, correctierondes, latere Software Factory-afwijzingen en
productiefouten gevolgd. Een hogere storyopbrengst is alleen een verbetering wanneer downstream
kwaliteit minstens gelijk blijft.

## Succescriteria

- Herstelbare tekst- en validatiefouten leiden aantoonbaar minder vaak tot een cyclus zonder story.
- Een waarschuwing zonder materieel risico blokkeert nooit zelfstandig levering.
- Iedere blocker heeft een categorie, eigenaar, bron en concrete herstelactie.
- Onafhankelijke geaccepteerde kandidaten gaan door ondanks een andere revisiekandidaat.
- Exact reeds geleverd werk wordt niet dubbel geleverd, maar overlap wordt niet algemeen
  afgestraft.
- Bij een ontbrekend beleid kiest de cyclus waar mogelijk een kleinere veilige story.
- De verhouding cycli met minimaal één leverbare story stijgt zonder stijging van security-,
  privacy- of downstream kwaliteitsincidenten.
- Cycli zonder story zijn uiteindelijk beperkt tot begrijpelijke, legitieme oorzaken: werkelijk
  onveilig, fundamenteel ongeschikt, eigenaarbesluit noodzakelijk, alle zinvolle arbeid reeds
  geleverd, of geen zinvolle kleine verbetering gevonden.

## Afhankelijkheid van het contextplan

Het criticuscontract en de bronhiërarchie kunnen pas volledig betrouwbaar worden afgedwongen als
Product Factory weet welke besluiten en documenten actueel zijn. Daarom kan fase 1 van dit plan
direct worden uitgevoerd, maar horen beleidsbroncontrole, upstream-correctie en de uiteindelijke
materialiteitsbeslissing aan te sluiten op
[plan-actuele-agentcontext-en-historisch-geheugen.md](plan-actuele-agentcontext-en-historisch-geheugen.md).
