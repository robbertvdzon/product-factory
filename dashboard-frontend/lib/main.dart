import 'dart:async';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'api.dart';
import 'config.dart';
import 'google_button_stub.dart' if (dart.library.html) 'google_button_web.dart' as google_button;
import 'session.dart';

void main() => runApp(const ProductFactoryDashboard());

class ProductFactoryDashboard extends StatelessWidget {
  const ProductFactoryDashboard({super.key});
  @override Widget build(BuildContext context) => MaterialApp(
    title: 'Product Factory', debugShowCheckedModeBanner: false,
    theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff325d4d)), useMaterial3: true),
    home: const DashboardGate(),
  );
}

class DashboardGate extends StatefulWidget {
  const DashboardGate({super.key});
  @override State<DashboardGate> createState() => _DashboardGateState();
}

class _DashboardGateState extends State<DashboardGate> {
  DashboardSession? source;
  AuthenticatedSession? session;
  StreamSubscription<AuthenticatedSession>? subscription;
  bool loading = true;
  String? error;
  @override void initState() { super.initState(); _start(); }
  Future<void> _start() async {
    if (!AppConfig.authRequired) { setState(() => loading = false); return; }
    if (AppConfig.googleClientId.isEmpty) { setState(() { loading = false; error = 'Google-login is niet geconfigureerd.'; }); return; }
    source = DashboardSession(apiBaseUrl: AppConfig.apiBaseUrl, clientId: AppConfig.googleClientId);
    subscription = source!.changes.stream.listen((value) => setState(() { session = value; loading = false; error = null; }), onError: (Object value) => setState(() { loading = false; error = '$value'; }));
    try { final value = await source!.bootstrap(); if (mounted) setState(() { session = value; loading = false; }); } catch (exception) { if (mounted) setState(() { loading = false; error = '$exception'; }); }
  }
  Future<void> _signIn() async { setState(() { loading = true; error = null; }); try { final value = await source!.signIn(); if (mounted) setState(() { session = value; loading = false; }); } catch (exception) { if (mounted) setState(() { loading = false; error = '$exception'; }); } }
  @override void dispose() { subscription?.cancel(); source?.dispose(); super.dispose(); }
  @override Widget build(BuildContext context) {
    if (loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (AppConfig.authRequired && session == null) return LoginPage(error: error, signIn: _signIn);
    return OverviewPage(session: session);
  }
}

class LoginPage extends StatelessWidget {
  const LoginPage({required this.error, required this.signIn, super.key});
  final String? error; final VoidCallback signIn;
  @override Widget build(BuildContext context) => Scaffold(body: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 420), child: Card(child: Padding(padding: const EdgeInsets.all(32), child: Column(mainAxisSize: MainAxisSize.min, children: [
    const Icon(Icons.auto_awesome, size: 56), const SizedBox(height: 16), Text('Product Factory', style: Theme.of(context).textTheme.headlineMedium), const SizedBox(height: 8), const Text('Log in met een toegestaan Google-account.'), const SizedBox(height: 24),
    if (kIsWeb) SizedBox(height: 42, child: google_button.renderGoogleButton()) else FilledButton.icon(onPressed: signIn, icon: const Icon(Icons.login), label: const Text('Inloggen met Google')),
    if (error != null) ...[const SizedBox(height: 16), Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error))],
  ]))))));
}

class OverviewPage extends StatefulWidget {
  const OverviewPage({required this.session, super.key}); final AuthenticatedSession? session;
  @override State<OverviewPage> createState() => _OverviewPageState();
}
class _OverviewPageState extends State<OverviewPage> {
  late Future<List<List<dynamic>>> data;
  late final DashboardApi api;
  @override void initState() { super.initState(); api = DashboardApi(AppConfig.apiBaseUrl, widget.session?.token); data = Future.wait([api.products(), api.stories(), api.publications()]); }
  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Product Factory'), actions: [if (widget.session != null) Padding(padding: const EdgeInsets.all(16), child: Text(widget.session!.email))]), body: FutureBuilder<List<List<dynamic>>>(future: data, builder: (context, snapshot) {
    if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
    if (snapshot.hasError) return Center(child: Text('Dashboard kon niet laden: ${snapshot.error}'));
    final products = snapshot.data![0]; final stories = snapshot.data![1]; final publications = snapshot.data![2];
    return ListView(padding: const EdgeInsets.all(24), children: [Text('Productoverzicht', style: Theme.of(context).textTheme.headlineMedium), const SizedBox(height: 16), Wrap(spacing: 16, runSpacing: 16, children: [MetricCard(label: 'Producten', value: '${products.length}', icon: Icons.apps), MetricCard(label: 'Interne storykandidaten', value: '${stories.length}', icon: Icons.lightbulb_outline), MetricCard(label: 'Workspace-publicaties', value: '${publications.length}', icon: Icons.folder_open)]), const SizedBox(height: 32), Text('Producten', style: Theme.of(context).textTheme.titleLarge), ...products.map((item) { final product = item as Map<String, dynamic>; return ListTile(title: Text('${product['name']}'), subtitle: Text('${product['mission']}'), leading: const Icon(Icons.inventory_2_outlined)); }), const SizedBox(height: 24), Text('Storykandidaten', style: Theme.of(context).textTheme.titleLarge), ...stories.map((item) { final story = item as Map<String, dynamic>; return ListTile(title: Text('${story['title']}'), subtitle: Text('${story['productSlug']} · ${story['status']}'), leading: const Icon(Icons.notes)); }), const SizedBox(height: 24), Text('Workspace', style: Theme.of(context).textTheme.titleLarge), ...publications.map((item) { final publication = item as Map<String, dynamic>; final runId = '${publication['runId']}'; return ListTile(title: Text('${publication['artifactPath']}'), subtitle: Text('$runId · ${publication['status']}'), leading: const Icon(Icons.description_outlined), trailing: const Icon(Icons.open_in_new), onTap: () async { final content = await api.artifact(runId); if (context.mounted) showDialog<void>(context: context, builder: (context) => AlertDialog(title: Text('${publication['artifactPath']}'), content: SizedBox(width: 720, child: SingleChildScrollView(child: SelectableText(content))), actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Sluiten'))])); }); })]);
  }));
}
class MetricCard extends StatelessWidget {
  const MetricCard({required this.label, required this.value, required this.icon, super.key}); final String label, value; final IconData icon;
  @override Widget build(BuildContext context) => SizedBox(width: 260, child: Card(child: Padding(padding: const EdgeInsets.all(20), child: Row(children: [Icon(icon, size: 36), const SizedBox(width: 16), Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(value, style: Theme.of(context).textTheme.headlineMedium), Text(label)])]))));
}
