import 'dart:convert';
import 'package:http/http.dart' as http;

class DashboardApi {
  const DashboardApi(this.baseUrl, this.token);
  final String baseUrl;
  final String? token;
  Map<String, String> get headers => {
    'Content-Type': 'application/json',
    if (token != null) 'Authorization': 'Bearer $token',
  };
  Future<List<dynamic>> products() => _list('/api/products');
  Future<List<dynamic>> stories() => _list('/api/story-candidates');
  Future<List<dynamic>> publications() => _list('/api/workspace/publications');
  Future<List<dynamic>> shadowIterations() => _list('/api/shadow-iterations');
  Future<List<dynamic>> deliveries() => _list('/api/autonomy/deliveries');
  Future<List<dynamic>> humanActions() => _list('/api/autonomy/human-actions');
  Future<String> artifact(String productSlug, String runId) async {
    final response = await http.get(
      Uri.parse(
        '$baseUrl/api/workspace/publications/$runId/artifact?productSlug=${Uri.encodeQueryComponent(productSlug)}',
      ),
      headers: headers,
    );
    if (response.statusCode != 200) {
      throw StateError('Artefact kon niet worden geopend.');
    }
    return response.body;
  }

  Future<void> changeProductStatus(String slug, String action) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/$action'),
      headers: headers,
    );
    if (response.statusCode != 200) {
      throw StateError(
        'Productstatus kon niet worden aangepast (${response.statusCode}).',
      );
    }
  }

  Future<void> createProduct(Map<String, dynamic> product) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products'),
      headers: headers,
      body: jsonEncode(product),
    );
    if (response.statusCode != 201) {
      throw StateError(
        'Product kon niet worden toegevoegd (${response.statusCode}).',
      );
    }
  }

  Future<void> startShadowIteration(String slug) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/shadow-iterations'),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 202) {
      throw StateError(
        'Shadow-iteratie kon niet worden gestart (${response.statusCode}).',
      );
    }
  }

  Future<void> startAutonomousCycle(String slug) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/autonomous-cycles'),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 202) {
      throw StateError(
        'Autonome cyclus kon niet worden gestart (${response.statusCode}).',
      );
    }
  }

  Future<void> completeHumanAction(int id, String result) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/autonomy/human-actions/$id/complete'),
      headers: headers,
      body: jsonEncode({'result': result}),
    );
    if (response.statusCode != 200) {
      throw StateError(
        'Menselijke actie kon niet worden afgerond (${response.statusCode}).',
      );
    }
  }

  Future<List<dynamic>> _list(String path) async {
    final response = await http.get(
      Uri.parse('$baseUrl$path'),
      headers: headers,
    );
    if (response.statusCode != 200) {
      throw StateError('Dashboard API gaf ${response.statusCode}.');
    }
    return jsonDecode(response.body) as List<dynamic>;
  }
}
