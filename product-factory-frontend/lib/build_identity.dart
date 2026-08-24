import 'dart:convert';

import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';

class BuildIdentity {
  const BuildIdentity({
    required this.applicationVersion,
    required this.apiVersion,
    required this.gitRevision,
    required this.buildTime,
    required this.environment,
    required this.buildIdentity,
  });

  factory BuildIdentity.validated({
    required String applicationVersion,
    required String apiVersion,
    required String gitRevision,
    required String buildTime,
    required String environment,
    required String buildIdentity,
  }) {
    final version = RegExp(r'^\d+\.\d+\.\d+$').hasMatch(applicationVersion)
        ? applicationVersion
        : unknown;
    final revision =
        RegExp(r'^[0-9a-f]{40}$').hasMatch(gitRevision.toLowerCase())
        ? gitRevision.toLowerCase()
        : unknown;
    final parsedTime = DateTime.tryParse(buildTime);
    final time = buildTime.endsWith('Z') && parsedTime?.isUtc == true
        ? buildTime
        : unknown;
    final runtimeEnvironment =
        const {'local', 'acceptance', 'production'}.contains(environment)
        ? environment
        : unknown;
    return BuildIdentity(
      applicationVersion: version,
      apiVersion: RegExp(r'^\d+$').hasMatch(apiVersion) ? apiVersion : unknown,
      gitRevision: revision,
      buildTime: time,
      environment: runtimeEnvironment,
      buildIdentity:
          RegExp(r'^\d+\.\d+\.\d+\+[0-9a-f]{12}$').hasMatch(buildIdentity)
          ? buildIdentity
          : unknown,
    );
  }

  factory BuildIdentity.fromBackendJson(Map<String, Object?> json) =>
      BuildIdentity.validated(
        applicationVersion: json['applicationVersion'] as String? ?? '',
        apiVersion: json['apiVersion'] as String? ?? '',
        gitRevision: json['gitRevision'] as String? ?? '',
        buildTime: json['buildTime'] as String? ?? '',
        environment: json['environment'] as String? ?? '',
        buildIdentity: json['backendBuildIdentity'] as String? ?? '',
      );

  factory BuildIdentity.frontend() {
    final revision = AppConfiguration.gitRevision.toLowerCase();
    final shortRevision = RegExp(r'^[0-9a-f]{40}$').hasMatch(revision)
        ? revision.substring(0, 12)
        : unknown;
    final version =
        RegExp(r'^\d+\.\d+\.\d+$').hasMatch(AppConfiguration.applicationVersion)
        ? AppConfiguration.applicationVersion
        : unknown;
    return BuildIdentity.validated(
      applicationVersion: version,
      apiVersion: AppConfiguration.apiVersion,
      gitRevision: revision,
      buildTime: AppConfiguration.buildTime,
      environment: AppConfiguration.environment,
      buildIdentity: version == unknown || shortRevision == unknown
          ? unknown
          : '$version+$shortRevision',
    );
  }

  final String applicationVersion;
  final String apiVersion;
  final String gitRevision;
  final String buildTime;
  final String environment;
  final String buildIdentity;

  static const unknown = 'Onbekend';
}

abstract interface class VersionGateway {
  Future<BuildIdentity> backendIdentity();
}

class HttpVersionGateway implements VersionGateway {
  HttpVersionGateway({http.Client? client, String? backendUrl})
    : _client = client ?? createHttpClient(),
      _backendUrl = (backendUrl ?? AppConfiguration.backendUrl).replaceAll(
        RegExp(r'/$'),
        '',
      );

  final http.Client _client;
  final String _backendUrl;

  @override
  Future<BuildIdentity> backendIdentity() async {
    try {
      final response = await _client.get(
        Uri.parse('$_backendUrl/api/version'),
        headers: const {'Cache-Control': 'no-cache'},
      );
      if (response.statusCode != 200) throw const VersionFailure();
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is! Map<String, Object?>) throw const VersionFailure();
      return BuildIdentity.fromBackendJson(decoded);
    } on VersionFailure {
      rethrow;
    } on Object {
      throw const VersionFailure();
    }
  }
}

class VersionFailure implements Exception {
  const VersionFailure();
}
