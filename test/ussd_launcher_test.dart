import 'package:flutter_test/flutter_test.dart';
import 'package:ussd_launcher/ussd_launcher.dart';
import 'package:ussd_launcher/ussd_launcher_platform_interface.dart';
import 'package:ussd_launcher/ussd_launcher_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockUssdLauncherPlatform
    with MockPlatformInterfaceMixin
    implements UssdLauncherPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final UssdLauncherPlatform initialPlatform = UssdLauncherPlatform.instance;

  test('$MethodChannelUssdLauncher is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelUssdLauncher>());
  });

  test('getPlatformVersion', () async {
    UssdLauncher ussdLauncherPlugin = UssdLauncher();
    MockUssdLauncherPlatform fakePlatform = MockUssdLauncherPlatform();
    UssdLauncherPlatform.instance = fakePlatform;

    expect(await ussdLauncherPlugin.getPlatformVersion(), '42');
  });
}
