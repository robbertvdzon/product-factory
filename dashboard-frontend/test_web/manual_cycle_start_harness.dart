import 'package:flutter/material.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:product_factory_dashboard/manual_cycle_start.dart';

void main() => runApp(const _ManualCycleStartHarness());

class _ManualCycleStartHarness extends StatelessWidget {
  const _ManualCycleStartHarness();

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: Builder(
        builder: (context) => StartCycleButton(
          onPressed: () => showDialog<bool>(
            context: context,
            traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
            builder: (_) => ManualCycleStartDialog(
              productSlug: 'actief-product',
              onStart: (ManualCycleStartSubmission _) async {},
            ),
          ),
        ),
      ),
    ),
  );
}
