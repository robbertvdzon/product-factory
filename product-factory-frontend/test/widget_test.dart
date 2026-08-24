import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/main.dart';

void main() {
  testWidgets('toont de lege maar herkenbare fundering', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 720));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(const ProductFactoryApp());

    expect(find.text('Product Factory'), findsOneWidget);
    expect(find.text('Technische fundering'), findsOneWidget);
    expect(find.text('Nog geen functionele procesmodule actief'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
