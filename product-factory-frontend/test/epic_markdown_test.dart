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
