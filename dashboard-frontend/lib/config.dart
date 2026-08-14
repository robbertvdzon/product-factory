abstract final class AppConfig {
  static const apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8081',
  );
  static const googleClientId = String.fromEnvironment('GOOGLE_CLIENT_ID');
  static const authRequired = bool.fromEnvironment(
    'AUTH_REQUIRED',
    defaultValue: false,
  );
  static const acceptanceDataset = bool.fromEnvironment(
    'ACCEPTANCE_DATASET',
    defaultValue: false,
  );
}
