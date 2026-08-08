import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/main.dart';

/// Vervangt de echte HTTP-oproep van [DashboardApi.shadowIterationSession] door vaste data, zodat
/// het detaildialoog getest kan worden zonder netwerkverkeer (conform de projectconventie dat
/// widgettests géén echte HTTP-calls doen).
class _FakeApi extends DashboardApi {
  _FakeApi(this.session) : super('http://example.invalid', null);

  final Map<String, dynamic> session;

  @override
  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async => session;
}

void main() {
  testWidgets(
    'de workspace-publicatie-weergave in het iteratiedetaildialoog blijft ongewijzigd (baseline)',
    (tester) async {
      // Standaard testvenster (800x600) laat de workspace-publicatiesectie onder de sliver-cache-extent
      // vallen zodat hij nooit gemount wordt; vergroot het venster zoals bij andere lange-lijst-tests.
      tester.view.physicalSize = const Size(1200, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final iterationData = <String, dynamic>{
        'iteration': {
          'id': 'iter-1',
          'productSlug': 'demo',
          'sequenceNumber': 1,
          'focus': 'Onderzoek reizigersvoorkeuren',
          'mode': 'autonomous',
          'status': 'ACCEPTED',
          'currentRole': null,
          'criticVerdict': 'ACCEPT',
          'candidateCount': 2,
          'workspaceRunId': 'run-1',
          'workspacePullRequestUrl': 'https://github.com/example/repo/pull/42',
          'workspaceCommitSha': 'abc1234',
          'errorMessage': null,
          'summary': null,
          'createdAt': DateTime(2026, 1, 1).toIso8601String(),
          'startedAt': DateTime(2026, 1, 1).toIso8601String(),
          'completedAt': DateTime(2026, 1, 1).toIso8601String(),
        },
        'steps': <dynamic>[],
        'artifacts': <dynamic>[],
        'dossier': null,
      };

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () => showDialog<void>(
                  context: context,
                  builder: (_) => IterationSessionDialog(
                    api: _FakeApi(iterationData),
                    productSlug: 'demo',
                    iterationId: 'iter-1',
                  ),
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      // Baseline-inhoud: exact deze teksten, op exact deze plek (na 'Voortgang'), moet blijven
      // staan zoals main.dart ze vandaag rendert.
      expect(find.text('Workspace-publicatie'), findsOneWidget);
      expect(
        find.text('https://github.com/example/repo/pull/42'),
        findsOneWidget,
      );
      expect(find.text('Commit: abc1234'), findsOneWidget);

      final progressTop = tester.getTopLeft(find.text('Voortgang')).dy;
      final workspaceHeadingTop = tester
          .getTopLeft(find.text('Workspace-publicatie'))
          .dy;
      expect(workspaceHeadingTop, greaterThan(progressTop));

      // Dialoog sluiten zodat de periodieke refresh-timer van het dialoog netjes wordt opgeruimd.
      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );
}
