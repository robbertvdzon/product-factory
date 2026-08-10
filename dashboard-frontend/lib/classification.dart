/// Vaste uitkomstclassificatie voor een productcyclus-iteratie, afgeleid uit bestaande velden
/// (`status`, `criticVerdict`, `errorMessage`) zonder nieuwe databronnen. Bewust een eigen library
/// (zoals `formatting.dart`/`limited_list.dart`) zodat de pure mapping- en kleurlogica los van de
/// UI unit-getest kan worden.
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const String kOnderzoekOnvoldoende = 'onderzoek-onvoldoende';
const String kGuardrailConflict = 'guardrail-conflict';
const String kRichtingGekozen = 'richting-gekozen';
const String kRichtingVerworpen = 'richting-verworpen';
const String kNietClassificeerbaar = 'niet-classificeerbaar';
const String kTechnischeFout = 'technische fout';

/// De zes toegestane classificatiewaarden; een badge mag nooit iets anders tonen.
/// [kGuardrailConflict] blijft hierin staan omdat `main.dart` deze onafhankelijk hergebruikt
/// voor het "geblokkeerd"-label op storyqueue-kaarten (dependsOn-blokkade), ook al mapt geen
/// enkele `status`-waarde er in [kBekendeStatuswaardenPerCategorie] nog naartoe.
const List<String> kIterationClassifications = [
  kOnderzoekOnvoldoende,
  kGuardrailConflict,
  kRichtingGekozen,
  kRichtingVerworpen,
  kNietClassificeerbaar,
  kTechnischeFout,
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
  kTechnischeFout: ['FAILED'],
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
  kTechnischeFout: ClassificationColors(
    background: Color(0xFFE4E1F5),
    foreground: Color(0xFF33306B),
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

/// Vaste scope-disclaimerzin in het inline uitklappaneel van [ClassificationBadge]: de badge toont
/// uitsluitend de vastgestelde uitkomst ('wat'), niet de onderliggende motivatie ('waarom') — die
/// laatste blijft voorbehouden aan de bestaande `IterationSessionDialog`.
const String kBadgeScopeDisclaimer =
    'Dit toont wat de uitkomst was, niet waarom.';

/// Badge die de vaste classificatietekst toont, met AA-contrast en een programmatisch leesbaar
/// (Semantics) label — de kleur is dus nooit het enige signaal. Toetsenbord- en muis-activeerbaar:
/// klapt bij activatie een inline scope-disclaimerpaneel open direct onder zichzelf.
///
/// Bewust géén `showDialog`/`AlertDialog`, ook al is dat het bestaande herbruikbare modale patroon
/// in dit project (`IterationSessionDialog`, `AddProductDialog`, `ProductSettingsDialog`,
/// `_completeHumanAction`): een dialoog zou een focus-trap en `role="dialog"`-achtige semantiek
/// introduceren die voor dit korte scope-signaal niet nodig is, en zou het los koppelen van de
/// rij waar de badge bij hoort. Het paneel is daarom een gewone widget in de bestaande
/// widgetboom, geen aparte overlay/route.
class ClassificationBadge extends StatefulWidget {
  const ClassificationBadge({required this.classification, super.key});

  final String classification;

  @override
  State<ClassificationBadge> createState() => _ClassificationBadgeState();
}

class _ClassificationBadgeState extends State<ClassificationBadge> {
  final FocusNode _focusNode = FocusNode(debugLabel: 'classification-badge');
  bool _expanded = false;

  @override
  void initState() {
    super.initState();
    // Rebuild op focuswissel zodat de focusrand (en straks eventuele focus-afhankelijke styling)
    // synchroon blijft met de daadwerkelijke focusstatus.
    _focusNode.addListener(_onFocusChange);
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    _focusNode.dispose();
    super.dispose();
  }

  void _onFocusChange() => setState(() {});

  void _toggleExpanded() => setState(() => _expanded = !_expanded);

  void _collapseAndRestoreFocus() {
    setState(() => _expanded = false);
    _focusNode.requestFocus();
  }

  KeyEventResult _handleKeyEvent(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.numpadEnter ||
        key == LogicalKeyboardKey.space) {
      _toggleExpanded();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.escape && _expanded) {
      _collapseAndRestoreFocus();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final colors =
        kClassificationColors[widget.classification] ??
        kClassificationColors[kOnderzoekOnvoldoende]!;
    // Eén Semantics op het buitenste niveau (i.p.v. alleen op de badge-chip) zodat het label en de
    // expanded-status ook direct opvraagbaar zijn via `tester.getSemantics(find.byType(...))` op
    // deze widget zelf, ongeacht of het paneel er (nog) niet bij staat.
    return Semantics(
      label: 'classificatie: ${widget.classification}',
      expanded: _expanded,
      button: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Focus(
            focusNode: _focusNode,
            onKeyEvent: _handleKeyEvent,
            child: GestureDetector(
              // Consumeert de tap op het badge zelf, zodat de omringende ListTile.onTap (die het
              // detaildialoog opent) niet ook triggert; een tap ernaast/erbuiten raakt de
              // GestureDetector niet en bereikt gewoon de rij.
              behavior: HitTestBehavior.opaque,
              onTap: _toggleExpanded,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: colors.background,
                  borderRadius: BorderRadius.circular(12),
                  border: _focusNode.hasFocus
                      ? Border.all(color: colors.foreground, width: 2)
                      : null,
                ),
                // De zichtbare Text blijft de bron van waarheid voor de widgettest en voor
                // gebruikers die wél kleuren zien; ExcludeSemantics voorkomt een dubbel (en
                // afwijkend) accessibility-label naast het expliciete label hierboven.
                child: ExcludeSemantics(
                  child: Text(
                    widget.classification,
                    style: TextStyle(
                      color: colors.foreground,
                      fontWeight: FontWeight.w600,
                      fontSize: 12,
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (_expanded)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 260),
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: colors.background.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: colors.foreground, width: 1),
                  ),
                  child: Text(
                    kBadgeScopeDisclaimer,
                    style: const TextStyle(fontSize: 12),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
