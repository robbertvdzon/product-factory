# product-factory-30 - Toon gekoppelde kandidaten en leveringen in een uitklapbare cycluskaart

## Story

Toon gekoppelde kandidaten en leveringen in een uitklapbare cycluskaart

<!-- refined-by-factory -->

## Scope

- Vervang iedere bestaande cyclusregel op het hoofdscherm door een compacte, zelfstandig uitklapbare cycluskaart.
- De gesloten kaart behoudt minimaal:
  - product en cyclusnummer;
  - de bestaande lokaal geformatteerde startdatum, met `createdAt` als bestaande fallback wanneer `startedAt` ontbreekt;
  - de tekstuele cyclusstatus;
  - de bestaande toepasselijke kernreden;
  - de bestaande beslisbron en bijbehorende detailbediening;
  - afzonderlijke aantallen voor interne kandidaten en Software Factory-leveringen, expliciet aangeduid als resultaten uit de geladen gegevens.
- Bestaande aanvullende cyclusinformatie en interacties, waaronder voortgang, classificatie, detailweergave en annuleren, blijven functioneel behouden. Interactieve bedieningen worden niet in elkaar genest.
- De uitklapbediening opent uitsluitend de opbrengst binnen dezelfde kaart en opent geen dialoog. De bestaande beslisbronbediening blijft verantwoordelijk voor het openen van het cyclusdetail.
- De geopende kaart toont twee afzonderlijke groepen met exact de koppen:
  - `Interne kandidaten`;
  - `Software Factory-leveringen`.
- Iedere gekoppelde kandidaat toont zijn titel en kandidaatstatus als tekst. Iedere gekoppelde levering toont zijn titel en leveringsstatus als tekst. Ook de titellijsten worden zichtbaar benoemd als resultaten uit de geladen gegevens.
- Groepeer uitsluitend client-side op basis van de reeds opgehaalde gegevens:
  - een kandidaat koppelt alleen wanneer de combinatie `productSlug` en `iterationSequenceNumber` exact overeenkomt met precies één geladen cyclus met dezelfde `productSlug` en `sequenceNumber`;
  - een levering koppelt alleen wanneer de combinatie `productSlug` en `iterationId` exact overeenkomt met precies één geladen cyclus met dezelfde `productSlug` en `id`.
- Ontbrekende, ongeldige, kruisproduct-, tegenstrijdige of meervoudig matchende verbanden gelden als niet koppelbaar. Titel, kandidaat-id, lijstpositie, volgorde en waarschijnlijkheid zijn geen alternatieve koppelstrategie.
- Verwerk alle geladen cycli bij het groeperen, ook cycli die door de bestaande lijstbeperking nog niet zichtbaar zijn.
- Tel iedere geladen kandidaat en levering precies eenmaal: bij maximaal één cyclus of in de niet-koppelbare categorie, nooit in beide.
- Toon bij één of meer niet-koppelbare records precies één melding buiten alle cycluskaarten: `Niet aan een cyclus te koppelen in geladen gegevens: <aantal>`. Het aantal is de som van de niet-koppelbare kandidaat- en leveringsrecords.
- Houd de laadstatus van de relevante cyclus-, kandidaat- en leveringsgegevens voldoende afzonderlijk bij om gedeeltelijk geladen en mislukte bronnen eerlijk weer te geven. Een nog niet geladen of mislukte bron wordt niet als nul of volledig gepresenteerd. Een compleet globaal aantal niet-koppelbare records verschijnt pas wanneer beide opbrengstbronnen succesvol zijn geladen.
- Laat de prominente startactie en de bestaande globale kandidaat- en leveringssecties inhoudelijk en functioneel ongewijzigd. Dezelfde geladen records mogen daarnaast als gekoppelde opbrengst in een cycluskaart verschijnen.
- Er komen geen wijzigingen aan backend, API-contracten, database, schema, opslag, levering of infrastructuur.

## Acceptance criteria

- Een geautomatiseerde widgettest bewijst dat iedere gesloten cycluskaart zonder uitklappen het cyclusnummer, de datum, tekstuele status, toepasselijke kernreden, beslisbron en afzonderlijke aantallen kandidaten en leveringen toont. De aantallen zijn zichtbaar gelabeld als afkomstig uit de geladen gegevens en worden uit de gegroepeerde geladen records berekend, niet uit een backendtotaal voor de cyclus.
- De uitklapbediening is een native, toetsenbordbedienbare knop. De toegankelijke naam bevat het cyclusnummer en de programmatische semantics bevatten de actuele expanded-status.
- Activering met muis, Enter of Spatie wisselt het zichtbare label tussen `Toon opbrengst` en `Verberg opbrengst`. De focus blijft na openen en sluiten op dezelfde bediening en er is een zichtbare focusindicator.
- Na openen toont uitsluitend de geactiveerde kaart de twee groepen `Interne kandidaten` en `Software Factory-leveringen`. Titels en bijbehorende kandidaat- of leveringsstatussen zijn leesbare tekst en worden niet uitsluitend via kleur gecommuniceerd.
- Lege gekoppelde groepen blijven herkenbaar als een resultaat van de geladen gegevens en suggereren geen ontbrekende inhoud.
- Pure, geautomatiseerde tests van de koppellogica dekken volledige koppelingen, ontbrekende velden, ongeldige waarden, kruisproductverbanden en ambigue matches. Ze bewijzen dat ieder geladen record in maximaal één categorie en maximaal één telling terechtkomt en nooit op titel, kandidaat-id, lijstpositie, volgorde of waarschijnlijkheid wordt gekoppeld.
- Bij niet-koppelbare records staat exact één globale melding buiten de kaarten met `Niet aan een cyclus te koppelen in geladen gegevens: <aantal>`. De telling bevat ieder niet-koppelbaar record eenmaal; deze records verschijnen niet in een kaarttelling of opbrengstgroep.
- Bij nul niet-koppelbare records wordt de globale melding niet getoond.
- Inklappen verwijdert de opbrengstinhoud uit de widget- en semantics-tree, terwijl de kernvelden zichtbaar blijven en de focus op de uitklapbediening blijft.
- Meerdere kaarten kunnen onafhankelijk worden geopend en gesloten. Hun toestand blijft tijdens de normale automatische verversing behouden zolang de betreffende cycli geladen blijven.
- Bij een gedeeltelijk geladen toestand wordt uitsluitend informatie uit succesvol geladen bronnen als geladen resultaat getoond. Een ladende of mislukte bron toont geen misleidende nul- of totaaltelling.
- Bij een laadfout is zichtbaar welke opbrengst niet beschikbaar of onvolledig is. Er worden geen koppelingen, aantallen of globale niet-koppelbare totalen als volledig gepresenteerd zolang de benodigde bronnen niet allemaal succesvol zijn geladen.
- Widget-, semantics-, overflow- en golden-tests dekken gesloten, geopend, leeg, gedeeltelijk geladen, laadfout, volledig koppelbaar, ontbrekend verband, ambigu verband en lange tekst bij 320 CSS-pixels en 200% tekstvergroting. Essentiële informatie wordt niet afgekapt en er ontstaat geen horizontale overflow.
- Een geautomatiseerde contrasttest controleert alle door deze story geïntroduceerde tekst-, knop-, status- en zichtbare focuskleuren tegen hun daadwerkelijk gerenderde achtergrond in gesloten, geopende, fout- en focustoestanden. De test faalt bij een contrast onder de toepasselijke WCAG AA-grens of bij het ontbreken van een zichtbare focusindicator.
- Regressietests bewijzen dat de prominente startactie, het cyclusdetail en de bestaande globale kandidaat- en leveringssecties hun huidige gedrag behouden.

## Aannames

- “Geladen gegevens” betekent uitsluitend records die de bestaande leesverzoeken in de actuele dashboardverversing succesvol hebben opgeleverd; niet-teruggestuurde backendrecords worden niet meegeteld.
- Een kandidaat heeft in de huidige leesgegevens geen cyclus-id maar wel een expliciet cyclusnummer. Daarom is alleen de combinatie van product en cyclusnummer geldig voor kandidaatkoppeling.
- Een levering heeft een expliciete cyclus-id. Daarom is alleen de combinatie van product en cyclus-id geldig voor leveringskoppeling; de eventuele relatie met een kandidaat is hiervoor niet nodig.
- Vergelijkingen zijn exact en hoofdlettergevoelig. Ontbrekende of anders getypeerde koppelwaarden worden niet genormaliseerd of geschat.
- De bestaande database voorkomt normaal dubbele cyclusnummers binnen één product en dubbele cyclus-id’s. De frontend blijft defensief: levert een fixture of onverwachte respons toch meerdere matches op, dan wordt het record niet gekoppeld.
- De globale niet-koppelbare melding wordt alleen getoond bij een positief, volledig berekend aantal. Bij onvolledige bronnen komt daarvoor een zichtbare onvolledigheids- of foutmelding in de plaats.
- “Globale secties blijven ongewijzigd” betreft hun inhoud, acties en gedrag bij succesvol geladen gegevens; een noodzakelijke interne herindeling van laadstatussen voor eerlijke gedeeltelijke en foutweergave valt binnen deze story.

## Eindsamenvatting

De dashboardweergave toont productcycli nu als zelfstandig uitklapbare kaarten. Elke kaart behoudt de bestaande kerninformatie en toont afzonderlijke aantallen voor gekoppelde interne kandidaten en Software Factory-leveringen. Na uitklappen verschijnen titels en statussen in twee herkenbare groepen.

Koppeling gebeurt uitsluitend via de afgesproken product- en cyclusgegevens. Ontbrekende, ongeldige, kruisproduct- en dubbelzinnige verbanden worden eenmaal als niet-koppelbaar geteld. Laad- en foutstatussen blijven per gegevensbron zichtbaar, zodat ontbrekende gegevens niet als nul worden gepresenteerd. De uitklapstatus blijft bij automatisch verversen behouden en meerdere kaarten werken onafhankelijk.

Tijdens review zijn dubbele kaartidentiteiten defensief opgelost en is de contrastcontrole aangescherpt naar werkelijk weergegeven toestanden. Het volledige ontwikkelvangnet slaagde: zes Maven-modules, statische Flutter-controle en 244 Flutter-tests. De tester draaide daarnaast 28 gerichte regressietests en controleerde de preview, inclusief muis- en toetsenbordbediening, focusbehoud en gekoppelde opbrengst. Backend, API-contracten, database, infrastructuur en de bestaande globale kandidaat- en leveringssecties zijn bewust niet gewijzigd.

<!-- deploy-summary:start -->
Productcycli zijn nu overzichtelijke kaarten die laten zien hoeveel kandidaten en leveringen erbij horen. Je kunt elke kaart afzonderlijk openen om de bijbehorende titels en statussen te bekijken, terwijl ontbrekende of niet-koppelbare gegevens duidelijk worden gemeld.
<!-- deploy-summary:end -->
