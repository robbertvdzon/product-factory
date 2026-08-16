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
  Future<List<dynamic>> memory(String slug, {String? asOf}) {
    final encodedSlug = Uri.encodeComponent(slug);
    final query = asOf == null || asOf.trim().isEmpty
        ? ''
        : '?asOf=${Uri.encodeQueryComponent(asOf.trim())}';
    return _list('/api/products/$encodedSlug/memory$query');
  }

  Future<List<dynamic>> memoryHistory(String slug) =>
      _list('/api/products/${Uri.encodeComponent(slug)}/memory/history');
  Future<List<dynamic>> stories() => _list('/api/story-candidates');
  Future<List<dynamic>> publications() => _list('/api/workspace/publications');
  Future<List<dynamic>> shadowIterations() => _list('/api/shadow-iterations');
  Future<List<dynamic>> deliveries() => _list('/api/autonomy/deliveries');
  Future<List<dynamic>> humanActions() => _list('/api/autonomy/human-actions');
  Future<List<dynamic>> meetings() => _list('/api/meetings');
  Future<Map<String, dynamic>> meeting(String slug, String id) =>
      _object('/api/products/$slug/meetings/$id');
  Future<List<dynamic>> meetingMessages(String slug, String id) =>
      _list('/api/products/$slug/meetings/$id/messages');
  Future<List<dynamic>> roadmapEpics() => _list('/api/roadmap/epics');
  Future<List<dynamic>> roadmapEpicVerifications(String slug, String id) =>
      _list('/api/products/$slug/roadmap/epics/$id/verifications');
  Future<List<dynamic>> roadmapSettledQuestions() =>
      _list('/api/roadmap/settled-questions');
  Future<List<dynamic>> roadmapSessions() => _list('/api/roadmap/sessions');
  Future<Map<String, dynamic>> aiCatalog() => _object('/api/ai-catalog');

  Future<void> createRoadmapEpic(
    String slug,
    String title,
    String description,
  ) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/roadmap/epics'),
      headers: headers,
      body: jsonEncode({'title': title, 'description': description}),
    );
    if (response.statusCode != 201) {
      throw StateError(_errorMessage(response, 'Epic kon niet worden gemaakt'));
    }
  }

  Future<void> updateRoadmapEpic(
    String slug,
    String id,
    Map<String, dynamic> changes,
  ) async {
    final response = await http.put(
      Uri.parse('$baseUrl/api/products/$slug/roadmap/epics/$id'),
      headers: headers,
      body: jsonEncode(changes),
    );
    if (response.statusCode != 200) {
      throw StateError(
        _errorMessage(response, 'Epic kon niet worden opgeslagen'),
      );
    }
  }

  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async {
    final query = 'productSlug=${Uri.encodeQueryComponent(productSlug)}';
    final base = '/api/shadow-iterations/${Uri.encodeComponent(iterationId)}';
    final result = await Future.wait<dynamic>([
      _object('$base?$query'),
      _list('$base/steps?$query'),
      _list('$base/artifacts?$query'),
    ]);
    final iteration = result[0] as Map<String, dynamic>;
    String? dossier;
    final workspaceRunId = iteration['workspaceRunId'];
    if (workspaceRunId != null && '$workspaceRunId'.isNotEmpty) {
      try {
        dossier = await artifact(productSlug, '$workspaceRunId');
      } catch (_) {
        // Een gemergde workspace-publicatie kan kort na de iteratie beschikbaar komen.
      }
    }
    return {
      'iteration': iteration,
      'steps': result[1] as List<dynamic>,
      'artifacts': result[2] as List<dynamic>,
      'dossier': dossier,
    };
  }

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

  Future<void> updateProductSettings(
    String slug,
    Map<String, dynamic> settings,
  ) async {
    final response = await http.put(
      Uri.parse('$baseUrl/api/products/$slug/settings'),
      headers: headers,
      body: jsonEncode(settings),
    );
    if (response.statusCode != 200) {
      throw StateError(
        'Instellingen konden niet worden opgeslagen (${response.statusCode}).',
      );
    }
  }

  Future<void> startCycle(String slug) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/cycles'),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 202) {
      throw StateError(
        'Productcyclus kon niet worden gestart (${response.statusCode}).',
      );
    }
  }

  Future<void> cancelIteration(
    String productSlug,
    String iterationId, {
    String? reason,
  }) async {
    final query = 'productSlug=${Uri.encodeQueryComponent(productSlug)}';
    final response = await http.post(
      Uri.parse(
        '$baseUrl/api/shadow-iterations/${Uri.encodeComponent(iterationId)}/cancel?$query',
      ),
      headers: headers,
      body: jsonEncode({'reason': reason}),
    );
    if (response.statusCode != 200) {
      throw StateError(
        'Cyclus kon niet worden geannuleerd (${response.statusCode}).',
      );
    }
  }

  Future<void> resumeIteration(String productSlug, String iterationId) async {
    final query = 'productSlug=${Uri.encodeQueryComponent(productSlug)}';
    final response = await http.post(
      Uri.parse(
        '$baseUrl/api/shadow-iterations/${Uri.encodeComponent(iterationId)}/resume?$query',
      ),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 202) {
      throw StateError(
        'Revisie kon niet worden hervat (${response.statusCode}).',
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

  Future<Map<String, dynamic>> startMeeting(String slug) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/meetings'),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 201) {
      throw StateError(
        'Overleg kon niet worden gestart (${response.statusCode}).',
      );
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> sendMeetingMessage(
    String slug,
    String id,
    String content,
  ) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/meetings/$id/messages'),
      headers: headers,
      body: jsonEncode({'content': content}),
    );
    if (response.statusCode != 200) {
      throw StateError(
        'Bericht kon niet worden verstuurd (${response.statusCode}).',
      );
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> startRoadmapSession(String slug) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/roadmap/sessions'),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 202) {
      throw StateError(
        'Roadmap-sessie kon niet worden gestart (${response.statusCode}).',
      );
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Future<void> closeMeeting(String slug, String id) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/products/$slug/meetings/$id/close'),
      headers: headers,
      body: '{}',
    );
    if (response.statusCode != 200) {
      throw StateError(
        'Overleg kon niet worden afgesloten (${response.statusCode}).',
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

  Future<Map<String, dynamic>> _object(String path) async {
    final response = await http.get(
      Uri.parse('$baseUrl$path'),
      headers: headers,
    );
    if (response.statusCode != 200) {
      throw StateError('Dashboard API gaf ${response.statusCode}.');
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  String _errorMessage(http.Response response, String fallback) {
    try {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      final message = body['message'] ?? body['detail'] ?? body['error'];
      if (message != null && '$message'.trim().isNotEmpty) return '$message';
    } catch (_) {
      // Niet iedere foutrespons bevat JSON.
    }
    return '$fallback (${response.statusCode}).';
  }
}
