/// Vaste uitkomstclassificatie voor een productcyclus-iteratie, afgeleid uit bestaande velden
/// (`status`, `criticVerdict`, `errorMessage`) zonder nieuwe databronnen. Bewust een eigen library
/// (zoals `formatting.dart`/`limited_list.dart`) zodat de pure mapping- en kleurlogica los van de
/// UI unit-getest kan worden.
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';

const String kOnderzoekOnvoldoende = 'onderzoek-onvoldoende';
const String kGuardrailConflict = 'guardrail-conflict';
const String kRichtingGekozen = 'richting-gekozen';
const String kRichtingVerworpen = 'richting-verworpen';
const String kNietClassificeerbaar = 'niet-classificeerbaar';

/// De vijf toegestane classificatiewaarden; een badge mag nooit iets anders tonen.
const List<String> kIterationClassifications = [
  kOnderzoekOnvoldoende,
  kGuardrailConflict,
  kRichtingGekozen,
  kRichtingVerworpen,
  kNietClassificeerbaar,
];

/// Expliciete, uitbreidbare tabel van de daadwerkelijk voorkomende bekende ruwe `status`-waarden
/// per badge-categorie (`status` is een vrij `varchar(32)`, geen DB-enum). QUEUED/RUNNING zijn
/// bewust gemodelleerde, niet-afgeronde tussenstatussen die op [kOnderzoekOnvoldoende] blijven
/// mappen. Een toekomstige nieuwe status hoort hier bewust aan een categorie toegevoegd te
/// worden; alles wat hier niet in voorkomt (inclusief `null`/leeg) is per definitie onbekend en
/// valt terug op [kNietClassificeerbaar] in [classifyIterationOutcome].
const Map<String, List<String>> kBekendeStatuswaardenPerCategorie = {
  kRichtingGekozen: ['ACCEPTED'],
  kOnderzoekOnvoldoende: ['NEEDS_REVISION', 'QUEUED', 'RUNNING'],
  kRichtingVerworpen: ['REJECTED'],
  kGuardrailConflict: ['FAILED'],
};

/// Bepaalt de classificatie van een iteratie op basis van `status` (en indirect `criticVerdict`/
/// `errorMessage`, die de backend altijd in lijn met `status` zet, zie `ShadowIterationApi.kt`).
/// De vier badge-categorieën worden gevoed door de expliciete, uitbreidbare tabel
/// [kBekendeStatuswaardenPerCategorie]. Een lopende/wachtende iteratie (QUEUED/RUNNING) valt
/// bewust terug op [kOnderzoekOnvoldoende], omdat er dan nog geen afgeronde uitkomst is om te
/// classificeren. Elke andere, niet-bekende statuswaarde (inclusief `null`/leeg en elke
/// toekomstige, nu nog niet bestaande statuscode) mapt naar [kNietClassificeerbaar], zodat de
/// badge nooit ten onrechte 'onderzoek-onvoldoende' claimt voor een uitkomst die het systeem niet
/// kent.
String classifyIterationOutcome({
  required String? status,
  String? criticVerdict,
  String? errorMessage,
}) {
  if (status == null) {
    return kNietClassificeerbaar;
  }
  for (final entry in kBekendeStatuswaardenPerCategorie.entries) {
    if (entry.value.contains(status)) {
      return entry.key;
    }
  }
  return kNietClassificeerbaar;
}

/// Tekst-op-achtergrondkleurenpaar voor een badgevariant. Elk paar haalt minimaal WCAG 2.1 AA
/// (4.5:1 voor normale tekst); zie `test/classification_test.dart` voor de contrastcheck.
class ClassificationColors {
  const ClassificationColors({
    required this.background,
    required this.foreground,
  });

  final Color background;
  final Color foreground;
}

const Map<String, ClassificationColors> kClassificationColors = {
  kOnderzoekOnvoldoende: ClassificationColors(
    background: Color(0xFFFFF3CD),
    foreground: Color(0xFF6B4E00),
  ),
  kGuardrailConflict: ClassificationColors(
    background: Color(0xFFF8D7DA),
    foreground: Color(0xFF7A1220),
  ),
  kRichtingGekozen: ClassificationColors(
    background: Color(0xFFD1E7DD),
    foreground: Color(0xFF0B4228),
  ),
  kRichtingVerworpen: ClassificationColors(
    background: Color(0xFFE2E3E5),
    foreground: Color(0xFF383A3D),
  ),
  kNietClassificeerbaar: ClassificationColors(
    background: Color(0xFFCFE2FF),
    foreground: Color(0xFF073880),
  ),
};

/// Relatieve luminantie volgens WCAG 2.1, gebruikt om het contrastcijfer te berekenen.
double relativeLuminance(Color color) {
  double channel(double srgb) => srgb <= 0.03928
      ? srgb / 12.92
      : math.pow((srgb + 0.055) / 1.055, 2.4).toDouble();

  return 0.2126 * channel(color.r) +
      0.7152 * channel(color.g) +
      0.0722 * channel(color.b);
}

/// Contrastratio volgens WCAG 2.1 tussen twee kleuren; minimaal 4.5:1 vereist voor normale tekst.
double contrastRatio(Color a, Color b) {
  final lumA = relativeLuminance(a);
  final lumB = relativeLuminance(b);
  final lighter = lumA > lumB ? lumA : lumB;
  final darker = lumA > lumB ? lumB : lumA;
  return (lighter + 0.05) / (darker + 0.05);
}

/// Badge die de vaste classificatietekst toont, met AA-contrast en een programmatisch leesbaar
/// (Semantics) label — de kleur is dus nooit het enige signaal.
class ClassificationBadge extends StatelessWidget {
  const ClassificationBadge({required this.classification, super.key});

  final String classification;

  @override
  Widget build(BuildContext context) {
    final colors =
        kClassificationColors[classification] ??
        kClassificationColors[kOnderzoekOnvoldoende]!;
    return Semantics(
      label: 'classificatie: $classification',
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: colors.background,
          borderRadius: BorderRadius.circular(12),
        ),
        // De zichtbare Text blijft de bron van waarheid voor de widgettest en voor gebruikers die
        // wél kleuren zien; ExcludeSemantics voorkomt een dubbel (en afwijkend) accessibility-label
        // naast het expliciete label hierboven.
        child: ExcludeSemantics(
          child: Text(
            classification,
            style: TextStyle(
              color: colors.foreground,
              fontWeight: FontWeight.w600,
              fontSize: 12,
            ),
          ),
        ),
      ),
    );
  }
}
