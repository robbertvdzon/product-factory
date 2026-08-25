# 0010 - `/doc` beschrijft uitsluitend de huidige applicatie

- Status: Accepted
- Datum: 2026-08-24

## Context

AI-agents gebruiken de productdocumentatie zowel om nieuwe code te ontwikkelen als om de werkende
applicatie te testen. Daarvoor moeten zij erop kunnen vertrouwen dat de documentatie overeenkomt
met de code waarop zij werken. Een document dat actuele, verwijderde en toekomstige functionaliteit
door elkaar beschrijft, is daarvoor ongeschikt en kan tot verkeerde implementaties en onterechte
testresultaten leiden.

Software Factory maakt bij iedere story al standaard een subtaak om de documentatie bij te werken.
Deze ADR legt vast wat die subtaak moet opleveren en wanneer de story als afgerond mag gelden.

## Decision

De directory `/doc` is de leesbare actuele waarheid over de geïmplementeerde applicatie. Zij bevat
minimaal:

- functionele documentatie die beschrijft wat een gebruiker op dit moment kan doen en welk gedrag
  de applicatie nu vertoont;
- de belangrijkste actuele gebruikersroutes en regressiescenario's, met minimaal een herkenbare
  beginsituatie, de uit te voeren route en het verwachte resultaat;
- technische documentatie die de huidige architectuur, modulegrenzen, publieke contracten,
  datamodellen, integraties, configuratie en operationele werking beschrijft;
- een korte inhoudsopgave waarmee een mens of AI-agent de relevante actuele documentatie kan
  vinden.

Voor `/doc` gelden de volgende inhoudelijke regels:

- alleen werkelijk geïmplementeerde functionaliteit wordt beschreven;
- toekomstige, voorgenomen of nog gedeeltelijk niet-geïmplementeerde functionaliteit staat niet in
  `/doc`;
- verwijderde of vervangen functionaliteit wordt ook uit `/doc` verwijderd;
- de tekst beschrijft de huidige werking rechtstreeks en gebruikt geen changelogvorm zoals
  “vroeger”, “nieuw in deze versie” of “later wordt dit”;
- Gitgeschiedenis bewaart oude versies van de actuele documentatie; ADR's bewaren blijvende
  beslissingen en mogen daarom wel historische context bevatten;
- plannen, doelontwerpen en nog uit te voeren stappen blijven buiten `/doc` in de daarvoor bedoelde
  specificaties, plannen of backlog.

Iedere Software Factory-story bevat een verplichte documentatiesubtaak. Die subtaak:

1. onderzoekt de daadwerkelijk opgeleverde code en het werkelijk beschikbare gedrag;
2. controleert zowel de functionele als de technische documentatie op impact;
3. werkt alle geraakte bestanden onder `/doc` bij;
4. werkt geraakte gebruikersroutes en regressiescenario's bij of voegt ze toe wanneer de story
   nieuw testbaar gedrag introduceert;
5. verwijdert uitspraken en scenario's die door de story niet meer waar zijn;
6. voegt geen gedrag toe dat nog niet is geïmplementeerd;
7. wordt binnen dezelfde storyoplevering afgerond als de codewijziging.

Als een story aantoonbaar geen functionele of technische documentatie-impact heeft, legt de
documentatiesubtaak dat als opleverresultaat uit. Die uitleg wordt geen blijvende tekst in `/doc`.
Een story kan pas als `DONE` aan Product Factory worden gemeld wanneer de verplichte
documentatiesubtaak is afgerond.

Kwaliteitsbewaking leest bij iedere inhoudelijke test de relevante delen en testscenario's uit
`/doc` op exact de Gitcommit die bij de geteste productversie hoort. Zij combineert die met het
bevroren story-, bug- of epiccontract en kiest alleen scenario's die relevant zijn voor het
workitem en het regressierisico; een kleine wijziging vereist niet automatisch alle scenario's.

Documentatie is testinput, nooit bewijs dat gedrag werkt. De werkelijk geteste applicatie en het
verzamelde bewijs blijven leidend voor de waarneming. Wanneer `/doc`, het bevroren contract en de
applicatie elkaar tegenspreken, legt Kwaliteitsbewaking de concrete afwijking vast als product- of
documentatiefout. Zij negeert de tegenstelling niet en een verificatie kan dan niet volledig slagen.
Ontbreekt voor gewijzigd belangrijk gedrag een relevant scenario, dan geldt dat eveneens als
documentatiegebrek; de tester leidt het noodzakelijke testwerk ondertussen af uit het contract en
de werkende applicatie en slaat de controle niet over.

## Consequences

- Ontwikkel- en testagents krijgen één eenduidige bron voor de huidige werking.
- Iedere functionele of technische wijziging omvat ook het verwijderen of aanpassen van verouderde
  documentatie.
- Toekomstplannen en historische uitleg kunnen niet als actuele productwerking worden gelezen.
- Documentatieonderhoud kost binnen iedere geraakte story expliciet tijd, maar vereist geen apart
  periodiek documentatieproces zolang de verplichte subtaak en kwaliteitscontrole worden gevolgd.
- Een ADR alleen houdt documentatie niet actueel; de verplichte Software Factory-subtaak is de
  uitvoerende borging en de kwaliteitscontrole is de onafhankelijke controle.

## Gerelateerde documenten

- [Software Factory-dispatcher](../processen/software-factory-dispatcher.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Agent Runtime-integratie en taakcontainer](../gedeelde-modules/ai-worker.md)
