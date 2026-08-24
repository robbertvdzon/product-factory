import 'dart:convert';

import 'package:http/http.dart' as http;

import 'build_identity.dart';
import 'http_client_factory.dart';

abstract interface class FrontendVersionSource {
  Future<BuildIdentity> latest();
}

class HttpFrontendVersionSource implements FrontendVersionSource {
  HttpFrontendVersionSource({http.Client? client})
    : _client = client ?? createHttpClient();

  final http.Client _client;

  @override
  Future<BuildIdentity> latest() async {
    try {
      final response = await _client.get(
        Uri.parse('/version.json'),
        headers: const {'Cache-Control': 'no-cache'},
      );
      if (response.statusCode != 200) throw const VersionFailure();
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is! Map<String, Object?>) throw const VersionFailure();
      return BuildIdentity.validated(
        applicationVersion: decoded['applicationVersion'] as String? ?? '',
        apiVersion: decoded['apiVersion'] as String? ?? '',
        gitRevision: decoded['gitRevision'] as String? ?? '',
        buildTime: decoded['buildTime'] as String? ?? '',
        environment: decoded['environment'] as String? ?? '',
        buildIdentity: decoded['frontendBuildIdentity'] as String? ?? '',
      );
    } on VersionFailure {
      rethrow;
    } on Object {
      throw const VersionFailure();
    }
  }
}

class VersionUpdateTracker {
  bool _notified = false;

  bool shouldNotify(BuildIdentity current, BuildIdentity latest) {
    if (_notified ||
        current.buildIdentity == BuildIdentity.unknown ||
        latest.buildIdentity == BuildIdentity.unknown) {
      return false;
    }
    if (current.buildIdentity == latest.buildIdentity) return false;
    _notified = true;
    return true;
  }
}
