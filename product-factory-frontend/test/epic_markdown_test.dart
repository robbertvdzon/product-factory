import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:product_factory_frontend/epic_markdown.dart';

void main() {
  test('bestaande opsommingen worden leesbaar zonder opgeslagen inhoud te herschrijven', () {
    expect(readableEpicMarkdown('Stappen: (1) Open het dossier. (2) Deel het dossier.'), 'Stappen:\n\n1. Open het dossier.\n\n2. Deel het dossier.');
    const markdown = '## Werking\n\n**Belangrijk**\n\n- Open `DossierPage`';
    expect(readableEpicMarkdown(markdown), markdown);
  });
  test('technische bijlage blijft apart en functionele scope erna blijft zichtbaar', () {
    const source = '## Wat je kunt doen\nOpen het archief.\n\n## Technische route\nGET /api/meetings\n\n### Bewijs\nDe bestaande tabel.\n\n## Buiten scope\nGeen nieuwe analyses.';
    final parts = splitEpicSolution(source);
    expect(parts.functional, contains('Open het archief.'));
    expect(parts.functional, contains('Geen nieuwe analyses.'));
    expect(parts.functional, isNot(contains('GET /api/meetings')));
    expect(parts.technical, contains('GET /api/meetings'));
    expect(parts.technical, contains('### Bewijs'));
    expect(parts.technical, isNot(contains('Buiten scope')));
  });
  test('koppen binnen code worden niet als sectiegrens gezien', () {
    const source = 'Werking\n\n```markdown\n## Technische uitwerking\nDit is een voorbeeld.\n```\n\n## Technische uitwerking\n~~~sql\n## Geen echte kop\nSELECT 1;\n~~~\n### Controle\nBehoud alle details.';
    final parts = splitEpicSolution(source);
    expect(parts.functional, contains('Dit is een voorbeeld.'));
    expect(parts.technical, contains('## Geen echte kop'));
    expect(parts.technical, contains('SELECT 1;'));
    expect(parts.technical, contains('Behoud alle details.'));
    expect(splitEpicSolution('Gewone uitleg.').technical, isEmpty);
  });
  testWidgets('technische uitwerking opent pas op verzoek en blijft leesbaar op mobiel', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: SingleChildScrollView(
      child: EpicTechnicalDetails('### Technisch bewijs\n\nGET /api/meetings\n\nDe planner behoudt deze informatie.'),
    ))));
    await tester.pumpAndSettle();
    expect(find.text('Technische uitwerking'), findsOneWidget);
    expect(find.text('GET /api/meetings', findRichText: true), findsNothing);
    await tester.tap(find.text('Technische uitwerking'));
    await tester.pumpAndSettle();
    expect(find.textContaining('GET /api/meetings', findRichText: true), findsWidgets);
    expect(tester.takeException(), isNull);
  });
  testWidgets('Markdown blijft selecteerbaar op smal scherm met grote tekst', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320,850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(MaterialApp(home: MediaQuery(data:const MediaQueryData(textScaler:TextScaler.linear(2)), child: const Scaffold(body: SingleChildScrollView(child: Padding(padding:EdgeInsets.all(16),child:EpicMarkdown('## Werking\n\n**Duidelijke nadruk** en `DossierPage`.\n\n- Open een dossier\n- Deel een dossier\n\n```dart\nfinal eenErgLangeVariabeleVoorEenDossier = DossierPage();\n```')))))));
    await tester.pumpAndSettle();
    expect(tester.widget<MarkdownBody>(find.byType(MarkdownBody)).selectable,isTrue);
    expect(find.text('Werking',findRichText:true),findsWidgets);
    expect(tester.takeException(),isNull);
  });
}
