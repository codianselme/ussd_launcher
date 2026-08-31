## 1.5.0

### New Features
* **Custom Overlay Message**: Added `overlayMessage` parameter to `multisessionUssd()` to customize the overlay text during USSD operations.
* **Enhanced Dialog Detection**: Improved detection of USSD dialogs including final response dialogs without input fields.

### Improvements
* **Robust Input Validation**: Now validates dialog presence (EditText + Buttons + TextView) before attempting input.
* **Text Verification**: Confirms text was successfully inserted before clicking confirm button.
* **Progressive Retry**: Retry delays now increase progressively (250ms to 1000ms) for better reliability.
* **Increased Retries**: Maximum retries increased from 5 to 10 for slow networks.
* **Faster Response**: Reduced delays by 50% when dialog is properly detected.
* **Better Final Message Capture**: Improved capture of final USSD response messages.
* **Extended Device Support**: Added more manufacturer-specific USSD packages (Asus, LG, Sony).

### Bug Fixes
* Fixed issue where options were displayed on overlay (now only shows custom message).
* Improved memory management with proper recycling of AccessibilityNodeInfo objects.

## 1.4.0

### New Features
* **Invisible Overlay**: Added `UssdOverlayService` to hide system USSD dialogs during multi-session operations.
* **Overlay Permissions**: Added `isOverlayPermissionGranted()` and `openOverlaySettings()` to manage overlay permissions.
* **Robust Retry Mechanism**: Implemented smart retry logic for USSD input fields to handle slow devices/networks.
* **Foreground Service**: Overlay service now runs as a foreground service with notification to prevent system kills on Android 8+.

### Improvements
* **Better Filtering**: Enhanced USSD message filtering to ignore system texts and button labels.
* **Complete Content**: Now captures and returns the full content of USSD dialogs, not just the first line.
* **Stability**: Fixed `RemoteServiceException` crashes on Android 8+ by properly implementing foreground service requirements.
* **Documentation**: Comprehensive updates to README with overlay usage instructions.

## 1.3.0

### Major improvements
* **Android compatibility**: Full support for Android 5.0+ (API 21+)
  - Android 8+ uses TelephonyManager.sendUssdRequest()
  - Android 5-7 falls back to ACTION_CALL intent with accessibility service
* **Memory leak fixes**: Proper recycling of AccessibilityNodeInfo objects
* **Deprecated API fixes**:
  - Replaced GlobalScope with proper CoroutineScope
  - Replaced toLowerCase() with lowercase(Locale.ROOT)
* **New methods added**:
  - `isAccessibilityEnabled()`: Check if accessibility service is enabled
  - `openAccessibilitySettings()`: Open system accessibility settings
  - `cancelSession()`: Cancel active USSD session
  - `removeUssdMessageListener()`: Remove the message listener
* **Configurable delays**: Multi-session USSD now supports custom delays
  - `initialDelayMs`: Delay before first option (default: 3500ms)
  - `optionDelayMs`: Delay between options (default: 2000ms)
* **Better error handling**: All methods now return proper error codes
* **Multi-language support**: Confirm button detection in EN, FR, ES, DE
* **Updated dependencies**:
  - Kotlin 1.9.10
  - Android Gradle Plugin 8.1.0
  - Java 17 compatibility

### Bug fixes
* Fixed: Flutter code hanging when accessibility not enabled
* Fixed: Crash on Android versions below 8.0
* Fixed: Memory leaks in AccessibilityService
* Fixed: Incorrect accessibility service configuration

## 1.2.7

* Bug fixed

## 1.2.2

* Sim Cards data updated

## 1.2.1

* list and information about the sim cards present in the phone
* launch an operation from a selected sim

## 1.1.0

* Launch simple USSD requests
* Manage multi-step USSD sessions
* Updated readme

## 1.0.0

* Launch simple USSD requests

## 0.1.0

* Initial version.