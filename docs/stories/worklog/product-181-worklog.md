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
