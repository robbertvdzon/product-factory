import 'dart:js_interop';

import 'package:flutter/foundation.dart';
import 'package:web/web.dart' as web;

class NavigationLocation {
  Uri get current {
    final path = web.window.location.pathname;
    final search = web.window.location.search;
    return Uri.parse('$path$search');
  }

  void push(Uri location) {
    web.window.history.pushState(null, '', location.toString());
  }

  void replace(Uri location) {
    web.window.history.replaceState(null, '', location.toString());
  }

  VoidCallback listen(VoidCallback callback) {
    final listener = ((web.Event _) => callback()).toJS;
    web.window.addEventListener('popstate', listener);
    return () => web.window.removeEventListener('popstate', listener);
  }
}
