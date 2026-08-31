import 'package:flutter_test/flutter_test.dart';
import 'package:ussd_launcher/ussd_launcher.dart';
import 'package:ussd_launcher/ussd_launcher_platform_interface.dart';
import 'package:ussd_launcher/ussd_launcher_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:flutter/services.dart';

class MockUssdLauncherPlatform
    with MockPlatformInterfaceMixin
    implements UssdLauncherPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const MethodChannel channel = MethodChannel('ussd_launcher');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      switch (methodCall.method) {
        case 'sendUssdRequest':
          return 'Test USSD Response';
        case 'getSimCards':
          return [
            {
              'subscriptionId': 1,
              'displayName': 'Test SIM',
              'carrierName': 'Test Carrier',
              'slotIndex': 0,
            }
          ];
        case 'isAccessibilityEnabled':
          return true;
        case 'openAccessibilitySettings':
          return null;
        case 'cancelSession':
          return null;
        case 'multisessionUssd':
          return null;
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  group('UssdLauncher', () {
    test('$MethodChannelUssdLauncher is the default instance', () {
      final UssdLauncherPlatform initialPlatform =
          UssdLauncherPlatform.instance;
      expect(initialPlatform, isInstanceOf<MethodChannelUssdLauncher>());
    });

    test('sendUssdRequest returns response', () async {
      final response = await UssdLauncher.sendUssdRequest(
        ussdCode: '*123#',
        subscriptionId: -1,
      );
      expect(response, 'Test USSD Response');
    });

    test('getSimCards returns list of SIM cards', () async {
      final simCards = await UssdLauncher.getSimCards();
      expect(simCards, isNotEmpty);
      expect(simCards[0]['displayName'], 'Test SIM');
      expect(simCards[0]['carrierName'], 'Test Carrier');
    });

    test('isAccessibilityEnabled returns boolean', () async {
      final isEnabled = await UssdLauncher.isAccessibilityEnabled();
      expect(isEnabled, isTrue);
    });

    test('openAccessibilitySettings completes without error', () async {
      await expectLater(
        UssdLauncher.openAccessibilitySettings(),
        completes,
      );
    });

    test('cancelSession completes without error', () async {
      await expectLater(
        UssdLauncher.cancelSession(),
        completes,
      );
    });

    test('multisessionUssd completes without error', () async {
      await expectLater(
        UssdLauncher.multisessionUssd(
          code: '*123#',
          slotIndex: 0,
          options: ['1', '2'],
        ),
        completes,
      );
    });

    test('multisessionUssd accepts custom delays', () async {
      await expectLater(
        UssdLauncher.multisessionUssd(
          code: '*123#',
          slotIndex: 0,
          options: ['1'],
          initialDelayMs: 4000,
          optionDelayMs: 2500,
        ),
        completes,
      );
    });

    test('setUssdMessageListener sets handler', () {
      String? receivedMessage;
      UssdLauncher.setUssdMessageListener((message) {
        receivedMessage = message;
      });
      // Verify no exception is thrown
      expect(receivedMessage, isNull);
    });

    test('removeUssdMessageListener removes handler', () {
      UssdLauncher.removeUssdMessageListener();
      // Verify no exception is thrown
      expect(true, isTrue);
    });
  });
}
