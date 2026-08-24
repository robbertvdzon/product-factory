import 'package:flutter/material.dart';

void main() {
  runApp(const ProductFactoryApp());
}

class ProductFactoryApp extends StatelessWidget {
  const ProductFactoryApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Product Factory',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xff155e75),
        ),
        scaffoldBackgroundColor: const Color(0xfff6f8f8),
        useMaterial3: true,
      ),
      home: const FoundationPage(),
    );
  }
}

class FoundationPage extends StatelessWidget {
  const FoundationPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Product Factory'),
        actions: [
          TextButton.icon(
            onPressed: () {},
            icon: const Icon(Icons.info_outline),
            label: const Text('Beheer'),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final horizontalPadding = constraints.maxWidth < 600 ? 20.0 : 48.0;
          return SingleChildScrollView(
            padding: EdgeInsets.symmetric(
              horizontal: horizontalPadding,
              vertical: 40,
            ),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1120),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Technische fundering',
                    style: Theme.of(context).textTheme.displaySmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Product Factory wordt opnieuw opgebouwd. De veilige technische basis is beschikbaar; functionele processen worden in volgende releases toegevoegd.',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          height: 1.45,
                          color: const Color(0xff334155),
                        ),
                  ),
                  const SizedBox(height: 32),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(
                            Icons.construction_rounded,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 16),
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Nog geen functionele procesmodule actief',
                                  style: TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                SizedBox(height: 8),
                                Text(
                                  'Deze lege productpagina is bewust herkenbaar en bruikbaar terwijl authenticatie, configuratie en operationele voorzieningen worden aangesloten.',
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
