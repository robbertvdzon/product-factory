import 'package:flutter/material.dart';
import 'package:product_factory_dashboard/bugs.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:product_factory_dashboard/manual_cycle_start.dart';

void main() => runApp(const _ManualCycleStartHarness());

class _ManualCycleStartHarness extends StatefulWidget {
  const _ManualCycleStartHarness();

  @override
  State<_ManualCycleStartHarness> createState() =>
      _ManualCycleStartHarnessState();
}

class _ManualCycleStartHarnessState extends State<_ManualCycleStartHarness> {
  DashboardSection section = DashboardSection.overview;

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Builder(
          builder: (context) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              StartCycleButton(
                onPressed: () => showDialog<bool>(
                  context: context,
                  traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
                  builder: (_) => ManualCycleStartDialog(
                    productSlug: 'actief-product',
                    onStart: (ManualCycleStartSubmission _) async {},
                  ),
                ),
              ),
              const SizedBox(height: 16),
              MobileDashboardSectionNavigation(
                value: section,
                onChanged: (value) => setState(() => section = value),
              ),
              const SizedBox(height: 16),
              const OperationalSummary(
                children: [
                  MetricCard(label: 'Producten', value: '1', icon: Icons.apps),
                ],
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
