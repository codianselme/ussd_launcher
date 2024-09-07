import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'ussd_launcher_method_channel.dart';

abstract class UssdLauncherPlatform extends PlatformInterface {
  /// Constructs a UssdLauncherPlatform.
  UssdLauncherPlatform() : super(token: _token);

  static final Object _token = Object();

  static UssdLauncherPlatform _instance = MethodChannelUssdLauncher();

  /// The default instance of [UssdLauncherPlatform] to use.
  ///
  /// Defaults to [MethodChannelUssdLauncher].
  static UssdLauncherPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [UssdLauncherPlatform] when
  /// they register themselves.
  static set instance(UssdLauncherPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
