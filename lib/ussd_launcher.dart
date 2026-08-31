import 'package:flutter/services.dart';

/// A Flutter plugin to launch USSD requests and manage multi-step USSD sessions on Android.
class UssdLauncher {
  static const MethodChannel _channel = MethodChannel('ussd_launcher');

  /// Launches a single-session USSD request.
  ///
  /// [ussdCode] - The USSD code to dial (e.g., "*123#")
  /// [subscriptionId] - The SIM subscription ID (-1 for default SIM)
  ///
  /// Returns the USSD response as a String, or null if no response.
  /// On Android 5-7, returns "USSD_INITIATED_LEGACY" as the response
  /// comes through the message listener.
  ///
  /// Throws [PlatformException] if:
  /// - ACCESSIBILITY_NOT_ENABLED: Accessibility service is not enabled
  /// - PERMISSION_DENIED: Required permissions not granted
  /// - USSD_FAILED: USSD request failed
  static Future<String?> sendUssdRequest({
    required String ussdCode,
    required int subscriptionId,
  }) async {
    try {
      final String? response = await _channel.invokeMethod('sendUssdRequest', {
        'ussdCode': ussdCode,
        'subscriptionId': subscriptionId,
      });
      return response;
    } on PlatformException catch (e) {
      print("UssdLauncher: Error sending USSD request: ${e.message}");
      rethrow;
    }
  }

  /// Launches a multi-step USSD session with automatic menu navigation.
  ///
  /// [code] - The initial USSD code to dial
  /// [slotIndex] - The SIM slot index (0 for first SIM, 1 for second)
  /// [options] - List of menu options to select automatically
  /// [initialDelayMs] - Delay before sending first option (default: 3500ms)
  /// [optionDelayMs] - Delay between options (default: 2000ms)
  /// [overlayMessage] - Custom message to display on the overlay during the session
  ///
  /// Use [setUssdMessageListener] to receive USSD responses during the session.
  static Future<void> multisessionUssd({
    required String code,
    required int slotIndex,
    List<String> options = const [],
    int? initialDelayMs,
    int? optionDelayMs,
    String? overlayMessage,
  }) async {
    try {
      await _channel.invokeMethod('multisessionUssd', {
        'ussdCode': code,
        'slotIndex': slotIndex,
        'options': options,
        if (initialDelayMs != null) 'initialDelayMs': initialDelayMs,
        if (optionDelayMs != null) 'optionDelayMs': optionDelayMs,
        if (overlayMessage != null) 'overlayMessage': overlayMessage,
      });
    } on PlatformException catch (e) {
      print("UssdLauncher: Error in multi-session USSD: ${e.message}");
      rethrow;
    }
  }

  /// Sets a listener for USSD messages received during a session.
  ///
  /// [listener] - Callback function that receives USSD message strings
  ///
  /// This is essential for multi-session USSD to receive intermediate responses.
  static void setUssdMessageListener(void Function(String message) listener) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onUssdMessageReceived') {
        final String ussdMessage = call.arguments as String;
        listener(ussdMessage);
      }
    });
  }

  /// Removes the USSD message listener.
  static void removeUssdMessageListener() {
    _channel.setMethodCallHandler(null);
  }

  /// Gets information about available SIM cards.
  ///
  /// Returns a list of maps with SIM card information:
  /// - subscriptionId: Unique ID for the subscription
  /// - displayName: User-visible name of the SIM
  /// - carrierName: Name of the carrier
  /// - number: Phone number (may be empty)
  /// - slotIndex: Physical slot index (0 or 1)
  /// - countryIso: Country code
  /// - carrierId: Carrier ID (Android 9+)
  /// - isEmbedded: Whether it's an eSIM (Android 9+)
  /// - iccId: SIM card ICCID (may require additional permissions)
  static Future<List<Map<String, dynamic>>> getSimCards() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod('getSimCards');
      return result.map((item) => Map<String, dynamic>.from(item)).toList();
    } on PlatformException catch (e) {
      print("UssdLauncher: Error getting SIM cards: ${e.message}");
      return [];
    }
  }

  /// Checks if the accessibility service is enabled.
  ///
  /// Returns true if the USSD Launcher accessibility service is enabled.
  static Future<bool> isAccessibilityEnabled() async {
    try {
      final bool isEnabled =
          await _channel.invokeMethod('isAccessibilityEnabled');
      return isEnabled;
    } on PlatformException catch (e) {
      print("UssdLauncher: Error checking accessibility: ${e.message}");
      return false;
    }
  }

  /// Opens the system accessibility settings.
  ///
  /// Use this to guide users to enable the USSD Launcher accessibility service.
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      print("UssdLauncher: Error opening accessibility settings: ${e.message}");
    }
  }

  /// Cancels the current USSD session.
  ///
  /// Only effective if a multi-session USSD is currently active.
  static Future<void> cancelSession() async {
    try {
      await _channel.invokeMethod('cancelSession');
    } on PlatformException catch (e) {
      print("UssdLauncher: Error cancelling session: ${e.message}");
    }
  }

  /// Checks if the overlay permission (SYSTEM_ALERT_WINDOW) is granted.
  ///
  /// This permission is required to show the overlay during USSD sessions.
  static Future<bool> isOverlayPermissionGranted() async {
    try {
      final bool isGranted =
          await _channel.invokeMethod('isOverlayPermissionGranted');
      return isGranted;
    } on PlatformException catch (e) {
      print("UssdLauncher: Error checking overlay permission: ${e.message}");
      return false;
    }
  }

  /// Opens the system overlay permission settings.
  ///
  /// Use this to guide users to enable the "Draw over other apps" permission.
  static Future<void> openOverlaySettings() async {
    try {
      await _channel.invokeMethod('openOverlaySettings');
    } on PlatformException catch (e) {
      print("UssdLauncher: Error opening overlay settings: ${e.message}");
    }
  }
}
