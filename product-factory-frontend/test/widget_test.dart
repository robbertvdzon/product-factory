import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/authentication.dart';
import 'package:product_factory_frontend/main.dart';

void main() {
  testWidgets('wacht op sessiestatus voordat inhoud zichtbaar wordt', (
    tester,
  ) async {
    final session = Completer<AuthenticationStatus>();
    await tester.pumpWidget(
      ProductFactoryApp(
        authenticationGateway: FakeAuthenticationGateway(
          sessionResult: session.future,
        ),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Technische fundering'), findsNothing);

    session.complete(
      const AuthenticationStatus(authenticated: true, authRequired: false),
    );
    await tester.pumpAndSettle();
    expect(find.text('Technische fundering'), findsOneWidget);
  });

  testWidgets('wisselt Google-login om en kan de eigen sessie uitloggen', (
    tester,
  ) async {
    final gateway = FakeAuthenticationGateway(
      sessionResult: Future.value(
        const AuthenticationStatus(authenticated: false, authRequired: true),
      ),
    );
    var federatedSignOutCalled = false;
    await tester.pumpWidget(
      ProductFactoryApp(
        authenticationGateway: gateway,
        federatedSignOut: () async => federatedSignOutCalled = true,
        googleLoginButtonBuilder: (onToken) => FilledButton(
          onPressed: () => onToken('short-lived-google-token'),
          child: const Text('Test Google-login'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Log in met het toegestane Google-account om verder te gaan.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Test Google-login'));
    await tester.pumpAndSettle();
    expect(gateway.lastGoogleToken, 'short-lived-google-token');
    expect(find.text('Technische fundering'), findsOneWidget);

    await tester.tap(find.byTooltip('Uitloggen'));
    await tester.pumpAndSettle();
    expect(gateway.logoutCsrf, 'csrf-from-backend');
    expect(federatedSignOutCalled, isTrue);
    expect(find.text('Test Google-login'), findsOneWidget);
  });

  testWidgets('acceptatiebanner staat boven iedere funderingspagina', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 720));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(home: FoundationPage(showAcceptanceBanner: true)),
    );

    expect(
      find.text(
        'Acceptatie — synthetische tijdelijke data — authenticatie uit',
      ),
      findsOneWidget,
    );
    expect(
      find.text('Nog geen functionele procesmodule actief'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });
}

class FakeAuthenticationGateway implements AuthenticationGateway {
  FakeAuthenticationGateway({required this.sessionResult});

  final Future<AuthenticationStatus> sessionResult;
  String? lastGoogleToken;
  String? logoutCsrf;

  @override
  Future<AuthenticationStatus> googleLogin(String idToken) async {
    lastGoogleToken = idToken;
    return const AuthenticationStatus(
      authenticated: true,
      authRequired: true,
      stakeholderEmail: 'stakeholder@example.com',
      csrfToken: 'csrf-from-backend',
    );
  }

  @override
  Future<void> logout(String? csrfToken) async {
    logoutCsrf = csrfToken;
  }

  @override
  Future<AuthenticationStatus> session() => sessionResult;
}
