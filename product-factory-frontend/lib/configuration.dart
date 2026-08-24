class AppConfiguration {
  static const environment = String.fromEnvironment(
    'PF_ENVIRONMENT',
    defaultValue: 'local',
  );
  static const backendUrl = String.fromEnvironment(
    'PF_PUBLIC_BACKEND_URL',
    defaultValue: 'http://localhost:8080',
  );
  static const googleClientId = String.fromEnvironment('PF_GOOGLE_CLIENT_ID');
  static const applicationVersion = String.fromEnvironment(
    'PF_APPLICATION_VERSION',
    defaultValue: '0.1.0',
  );
  static const gitRevision = String.fromEnvironment('PF_GIT_REVISION');
  static const buildTime = String.fromEnvironment('PF_BUILD_TIME');
  static const apiVersion = '1';

  static bool get isAcceptance => environment == 'acceptance';
}
