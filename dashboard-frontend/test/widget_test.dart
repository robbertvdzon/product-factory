import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/main.dart';

void main() {
  testWidgets('shows product factory dashboard shell', (tester) async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    await tester.pump();
    expect(find.text('Product Factory'), findsOneWidget);
  });
}
