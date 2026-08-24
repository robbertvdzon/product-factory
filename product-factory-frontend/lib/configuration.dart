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

  static bool get isAcceptance => environment == 'acceptance';
}
