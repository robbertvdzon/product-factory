import 'package:flutter/foundation.dart';

class NavigationLocation {
  Uri get current => Uri(path: '/');

  void push(Uri location) {}

  void replace(Uri location) {}

  VoidCallback listen(VoidCallback callback) => () {};
}
