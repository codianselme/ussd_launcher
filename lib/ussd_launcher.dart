import 'package:flutter/services.dart';
// import 'dart:developer' as developer;

class UssdLauncher {
  static const MethodChannel _channel = MethodChannel('touch_ussd');

  /// Appelle l'invocation USSD initiale
  Future<String?> callUSSDInvoke(String ussdCode, int simSlot) async {
    try {
      final String? result = await _channel.invokeMethod('callUSSDInvoke', {
        'ussdCode': ussdCode,
        'simSlot': simSlot,
      });
      return result;
    } on PlatformException catch (e) {
      print("Failed to call USSD: '${e.message}'.");
      return null;
    }
  }

  /// Envoie une réponse USSD
  Future<String?> sendUSSD(String response, int simSlot) async {
    try {
      final String? result = await _channel.invokeMethod('sendUSSD', {
        'response': response,
        'simSlot': simSlot,
      });
      return result;
    } on PlatformException catch (e) {
      print("Failed to send USSD: '${e.message}'.");
      return null;
    }
  }

  /// Ajoute une étape USSD
  Future<bool> addUSSDStep(String response) async {
    try {
      final bool? result = await _channel.invokeMethod('addUSSDStep', {
        'response': response,
      });
      return result ?? false;
    } on PlatformException catch (e) {
      print("Failed to add USSD step: '${e.message}'.");
      return false;
    }
  }

  /// Appelle l'invocation USSD avec Overlay
  Future<String?> callUSSDOverlayInvoke(String ussdCode, int simSlot) async {
    try {
      final String? result =
      await _channel.invokeMethod('callUSSDOverlayInvoke', {
        'ussdCode': ussdCode,
        'simSlot': simSlot,
      });
      return result;
    } on PlatformException catch (e) {
      print("Failed to call USSD with Overlay: '${e.message}'.");
      return null;
    }
  }

  /// Exécute une séquence USSD dynamique
  Future<void> executeUSSDSequence(
      String ussdCode, int simSlot, List<String> steps) async {
    print("------------ USSD Code: $ussdCode");
    print("------------ SIM Slot: $simSlot");
    print("------------ Steps: $steps");

    // Appeler l'invocation initiale
    String? initialResponse = await callUSSDInvoke(ussdCode, simSlot);
    print("------------ Initial USSD Response: $initialResponse");

    // Ajouter dynamiquement les étapes
    for (var step in steps) {
      await addUSSDStep(step);
    }

    // Envoyer chaque étape séquentiellement
    for (var step in steps) {
      String? response = await sendUSSD(step, simSlot);
      print("USSD Step Response: $response");
    }
  }
}
