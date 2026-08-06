import 'dart:convert';
import 'package:http/http.dart' as http;

class DashboardApi {
  const DashboardApi(this.baseUrl, this.token);
  final String baseUrl;
  final String? token;
  Map<String, String> get headers => token == null ? const {} : {'Authorization': 'Bearer $token'};
  Future<List<dynamic>> products() => _list('/api/products');
  Future<List<dynamic>> stories() => _list('/api/story-candidates');
  Future<List<dynamic>> publications() => _list('/api/workspace/publications');
  Future<String> artifact(String runId) async {
    final response = await http.get(Uri.parse('$baseUrl/api/workspace/publications/$runId/artifact'), headers: headers);
    if (response.statusCode != 200) throw StateError('Artefact kon niet worden geopend.');
    return response.body;
  }
  Future<List<dynamic>> _list(String path) async {
    final response = await http.get(Uri.parse('$baseUrl$path'), headers: headers);
    if (response.statusCode != 200) throw StateError('Dashboard API gaf ${response.statusCode}.');
    return jsonDecode(response.body) as List<dynamic>;
  }
}
