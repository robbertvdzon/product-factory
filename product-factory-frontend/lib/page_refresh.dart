import 'package:flutter/foundation.dart';

class PageRefreshController extends ChangeNotifier {
  bool _userInitiated = false;

  bool get userInitiated => _userInitiated;

  void request({bool userInitiated = false}) {
    _userInitiated = userInitiated;
    notifyListeners();
  }
}
