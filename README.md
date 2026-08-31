# ussd_launcher

A Flutter plugin to launch USSD requests and manage multi-step USSD sessions on Android directly from your application.

[![pub package](https://img.shields.io/pub/v/ussd_launcher.svg)](https://pub.dev/packages/ussd_launcher)

## Features

- ✅ Launch simple USSD requests (Android 5.0+)
- ✅ Manage multi-step USSD sessions with automatic menu navigation
- ✅ **Invisible Overlay**: Hides the system USSD dialogs during multi-session operations
- ✅ Check and request necessary accessibility permissions
- ✅ Full compatibility with Android 5.0+ (API 21+)
- ✅ Handle USSD responses and errors gracefully
- ✅ Get detailed information about available SIM cards
- ✅ Support for dual-SIM devices
- ✅ Configurable delays for multi-session USSD
- ✅ Multi-language button detection (EN, FR, ES, DE)

## Installation

Add `ussd_launcher` as a dependency in your `pubspec.yaml` file:

```yaml
dependencies:
  ussd_launcher: ^1.3.0
```

## ⚙️ Setup

### Android Manifest

The plugin automatically adds the necessary permissions and services to your merged manifest. However, ensure your `android/app/src/main/AndroidManifest.xml` includes the `xmlns:tools` attribute if you need to override anything.

Required permissions used by the plugin:
- `CALL_PHONE`: To initiate calls.
- `READ_PHONE_STATE`: To get SIM card information.
- `BIND_ACCESSIBILITY_SERVICE`: To interact with USSD dialogs.
- `SYSTEM_ALERT_WINDOW`: To show the overlay that hides USSD dialogs.
- `FOREGROUND_SERVICE`: To keep the overlay service active.

### Accessibility Service

For **Multi-Session USSD** and **Android < 8 Single Session**, the user **MUST** enable the Accessibility Service for your app.

You can check and request this from your Flutter app:

```dart
if (!await UssdLauncher.isAccessibilityEnabled()) {
  await UssdLauncher.openAccessibilitySettings();
}
```

### Overlay Permission (Optional)

To hide the system USSD dialogs during a multi-session operation, the app needs the "Draw over other apps" permission.

```dart
if (!await UssdLauncher.isOverlayPermissionGranted()) {
  await UssdLauncher.openOverlaySettings();
}
```

## 📖 Usage

### Single-session USSD request

```dart
try {
  String? response = await UssdLauncher.sendUssdRequest(
    ussdCode: '*123#',
    subscriptionId: 1, // Get this from UssdLauncher.getSimCards()
  );
  print('USSD Response: $response');
} on PlatformException catch (e) {
  if (e.code == 'ACCESSIBILITY_NOT_ENABLED') {
    // Guide user to enable accessibility
  }
  print('Error: ${e.message}');
}
```

### Multi-session USSD with automatic menu navigation

```dart
// 1. Set up listener for USSD messages
UssdLauncher.setUssdMessageListener((message) {
  print('USSD Message: $message');
  // Handle the message in your UI
});

// 2. Launch multi-session USSD
await UssdLauncher.multisessionUssd(
  code: '*123#',
  slotIndex: 0,                    // First SIM slot
  options: ['1', '2', '3'],        // Menu options to select
  initialDelayMs: 3500,            // Optional: delay before first option
  optionDelayMs: 2000,             // Optional: delay between options
  overlayMessage: 'Please wait...', // Optional: custom overlay message
);

// 3. Don't forget to remove listener when done
UssdLauncher.removeUssdMessageListener();
```

### Get available SIM cards

```dart
List<Map<String, dynamic>> simCards = await UssdLauncher.getSimCards();
for (var sim in simCards) {
  print('SIM: ${sim['displayName']} - ${sim['carrierName']}');
  print('Slot: ${sim['slotIndex']}, ID: ${sim['subscriptionId']}');
}
```

### Cancel an active session

```dart
await UssdLauncher.cancelSession();
```

## API Reference

### Methods

| Method | Description |
|--------|-------------|
| `sendUssdRequest()` | Send a single USSD request and get the response |
| `multisessionUssd()` | Start a multi-step USSD session with auto-navigation |
| `getSimCards()` | Get list of available SIM cards with details |
| `isAccessibilityEnabled()` | Check if accessibility service is enabled |
| `openAccessibilitySettings()` | Open system accessibility settings |
| `isOverlayPermissionGranted()` | Check if overlay permission is granted |
| `openOverlaySettings()` | Open system overlay permission settings |
| `cancelSession()` | Cancel the current USSD session |
| `setUssdMessageListener()` | Set listener for USSD messages |
| `removeUssdMessageListener()` | Remove the message listener |

### Error Codes

| Code | Description |
|------|-------------|
| `ACCESSIBILITY_NOT_ENABLED` | Accessibility service is not enabled |
| `PERMISSION_DENIED` | Required permissions not granted |
| `USSD_FAILED` | USSD request failed |
| `INVALID_ARGUMENT` | Invalid parameter provided |
| `NO_ACTIVE_SESSION` | No USSD session is currently active |

## Platform Support

| Platform | Support |
|----------|---------|
| Android 5.0-7.1 (API 21-25) | ✅ Via Accessibility Service |
| Android 8.0+ (API 26+) | ✅ Native TelephonyManager API |
| iOS | ❌ Not supported (Apple restrictions) |

## Important Notes

- This plugin requires the accessibility service to be enabled for multi-session USSD.
- Single-session USSD on Android 8+ works without accessibility service.
- Multi-step USSD sessions may vary depending on mobile operators and countries.
- Delays may need adjustment based on network and operator response times.

## Troubleshooting

### USSD not working?

1. Ensure `CALL_PHONE` and `READ_PHONE_STATE` permissions are granted
2. Enable the accessibility service in device settings
3. Try increasing delays for slow networks
4. Check if your carrier supports the USSD code

### Dual-SIM issues?

1. Use `getSimCards()` to get the correct `subscriptionId` or `slotIndex`
2. Use `subscriptionId` for `sendUssdRequest()`
3. Use `slotIndex` for `multisessionUssd()`

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request on our [GitHub repository](https://github.com/codianselme/ussd_launcher).

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.