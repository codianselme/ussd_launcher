import 'package:flutter/services.dart';

class UssdLauncher {
  static const MethodChannel _channel = MethodChannel('ussd_launcher');
  static Function(String)? onUssdMessageReceived;

  static Future<void> launchUssd(String ussdCode) async {
    try {
      await _channel.invokeMethod('launchUssd', {'ussdCode': ussdCode});
    } on PlatformException catch (e) {
      print("Failed to launch USSD: '${e.message}'.");
      rethrow;
    }
  }

  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      print("Failed to open accessibility settings: '${e.message}'.");
      rethrow;
    }
  }

  static void setUssdMessageListener(Function(String) listener) {
    onUssdMessageReceived = listener;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onUssdMessageReceived') {
        final String ussdMessage = call.arguments;
        onUssdMessageReceived?.call(ussdMessage);
      }
    });
  }

  static Future<bool> isAccessibilityPermissionEnabled() async {
    try {
      final bool isEnabled = await _channel.invokeMethod('isAccessibilityPermissionEnabled');
      return isEnabled;
    } on PlatformException catch (e) {
      print("Failed to check accessibility permission: '${e.message}'.");
      return false;
    }
  }
}