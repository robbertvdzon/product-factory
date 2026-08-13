# product-181 - Testerworklog

## Uitgevoerde verificatie

- `.task.md`, factory-documentatie, `.factory/verification.yaml`, story-diff en developerworklogs gelezen.
- Gerichte Flutter-tests uitgevoerd: `flutter test test/management_view_test.dart
  test/story_queue_blocked_label_test.dart`; 20 tests geslaagd, 0 failures en 0 errors.
- Preview en API-health gecontroleerd; beide antwoorden met HTTP 200. De preview gebruikt de bestaande
  periodieke kandidaat- en leveringsrequests en toont op het overzicht geen globale storywachtrij.
- In de preview met headless Chromium Flutter-semantiek geactiveerd. De accessibility-tree biedt `Beheer`
  en `Terug naar overzicht` beide als focusbare link met de juiste toegankelijke naam aan.
- Met muisbediening opent Beheer en toont daar eerst `Software Factory-stories`, daarna `Storywachtrij` en
  de aanwezige kandidaat. Screenshots staan in `/work/screenshots`, waaronder de beheerweergave op 1280px
  en 320px breed.

## Bevinding: toetsenbordterugkeer werkt niet in Flutter Web-preview

Reproductie op `https://product-factory-pr-67.vdzonsoftware.nl`:

1. Activeer de Flutter-web semantics-tree.
2. Druk Tab; focus staat op de link `Beheer`.
3. Druk Enter; de beheerweergave opent correct.
4. Druk Tab totdat de link `Terug naar overzicht` focus heeft.
5. Druk Enter.

Verwacht: het bestaande hoofdscherm met `Productoverzicht` opent.

Werkelijk: de beheerweergave blijft zichtbaar; `Productoverzicht` verschijnt niet. Dit is tweemaal
gereproduceerd, zowel door de link programmatisch te focussen als via de echte browser-Tabvolgorde. De
previewtest liep daarbij uit op het wachten op `Productoverzicht`. De widgettest die dezelfde Enter-actie
simuleert is groen, maar reproduceert het gedrag van de gebouwde Flutter-webapp dus niet volledig.

Besluit: afwijzen wegens schending van het acceptancecriterium dat `Terug naar overzicht` met het
toetsenbord activeerbaar is. Het volledige vangnet na deze run kan deze gedragsfout niet groen maken.

## Hertest na developerherstel

- Preview en API-health geven HTTP 200. De deployments in namespace `product-factory-pr-67` gebruiken
  image `sha-b0b40133d6f6932a9342f0e3391d339464e045a2`, gelijk aan de geteste HEAD.
- In headless Chromium is Flutter-semantiek geactiveerd. Chromium rapporteert `Beheer` en `Terug naar
  overzicht` als focusbare links; beide zijn de eerste Tab-stop in hun weergave.
- Twee echte Tab+Enter-rondreizen zijn geslaagd. In de eerste bleef `Terug naar overzicht` 6,5 seconden
  en daarmee over de automatische refresh gefocust, waarna Enter het hoofdscherm opende. De tweede
  rondreis zonder wachttijd en heen/terug met de muis zijn eveneens geslaagd.
- Op 320 logische pixels breed zijn de teruglink, beide beheersecties en de kandidaatkaart bereikbaar.
  `documentElement.scrollWidth` bleef 320, een horizontale scrollpoging hield `scrollX` op 0 en er was
  geen zichtbare overlap of overflow.
- Gerichte bestaande widgettests uitgevoerd voor volledige tabvolgorde/focus, Beheer op 320 pixels met
  200% tekst en lange records/Meer-acties, en laad-/foutmeldingen op 320 pixels met 200% tekst: 3 tests
  geslaagd, 0 failures en 0 errors.
- Screenshots van overzicht, Beheer, gefocuste teruglink na refresh en de 320px-weergave staan in
  `/work/screenshots`.

Hertestbesluit: de eerder gemelde toetsenbordbug is op de actuele revision opgelost. Het volledige
revisiongebonden vangnet wordt conform de tester-instructie na deze run door de factory-harness uitgevoerd.
