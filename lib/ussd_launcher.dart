import 'package:flutter/services.dart';

class UssdLauncher {
  static const MethodChannel _channel = MethodChannel('ussd_launcher');

  static Future<void> launchUssd(String ussdCode) async {
    try {
      await _channel.invokeMethod('launchUssd', {'ussdCode': ussdCode});
    } on PlatformException catch (e) {
      print("Failed to launch USSD: '${e.message}'.");
      rethrow;
    }
  }

  static Future<String?> multisessionUssd({required String code, int subscriptionId = -1}) async {
    try {
      final String? result = await _channel.invokeMethod('multisessionUssd', {
        'ussdCode': code,
        'subscriptionId': subscriptionId,
      });
      return result;
    } on PlatformException catch (e) {
      print("Failed to launch multi-session USSD: '${e.message}'.");
      rethrow;
    }
  }

  static Future<String?> sendMessage(String message) async {
    try {
      final String? result = await _channel.invokeMethod('sendMessage', {'message': message});
      return result;
    } on PlatformException catch (e) {
      print("Failed to send USSD message: '${e.message}'.");
      rethrow;
    }
  }

  static Future<void> cancelSession() async {
    try {
      await _channel.invokeMethod('cancelSession');
    } on PlatformException catch (e) {
      print("Failed to cancel USSD session: '${e.message}'.");
      rethrow;
    }
  }

  static void setUssdMessageListener(Function(String) listener) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onUssdMessageReceived') {
        final String ussdMessage = call.arguments;
        listener(ussdMessage);
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

  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      print("Failed to open accessibility settings: '${e.message}'.");
      rethrow;
    }
  }
}