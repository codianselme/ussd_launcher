import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'ussd_launcher_platform_interface.dart';

/// An implementation of [UssdLauncherPlatform] that uses method channels.
class MethodChannelUssdLauncher extends UssdLauncherPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('ussd_launcher');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
