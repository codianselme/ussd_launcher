# ussd_launcher
A Flutter plugin to launch USSD requests and manage multi-step USSD sessions on Android directly from your application.


## Features
- Launch simple USSD requests
- Manage multi-step USSD sessions
- Check and request necessary accessibility permissions
- Compatibility with Android devices (API level 26+)
- Handle USSD responses and errors gracefully.
- Open accessibility settings if the service is not enabled.
- Get information about the SIM card


## Installation
Add `ussd_launcher` as a dependency in your `pubspec.yaml` file:

```yaml
dependencies:
  ussd_launcher: ^latest_version
```


## Configuration
### Android
You will need to add the following permissions to your Android manifest file.
```XML
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.PROCESS_OUTGOING_CALLS"/>
```


### For Android multi-session USSD
Add the USSD dialog accessibility service to your Android Manifest


```XML
<application>
    ...
    <service
        android:name="com.kavina.ussd_launcher.UssdAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="false">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>
</application>
```


## Usage
### Example
```dart
import 'package:flutter/material.dart';
import 'package:ussd_launcher/ussd_launcher.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: DefaultTabController(
        length: 2,
        child: Scaffold(
          appBar: AppBar(
            title: const Text('USSD Launcher Demo'),
            bottom: const TabBar(
              tabs: [
                Tab(text: 'Single Session'),
                Tab(text: 'Multi Session'),
              ],
            ),
          ),
          body: const TabBarView(
            children: [
              SingleSessionTab(),
              MultiSessionTab(),
            ],
          ),
        ),
      ),
    );
  }
}

class SingleSessionTab extends StatefulWidget {
  const SingleSessionTab({super.key});

  @override
  _SingleSessionTabState createState() => _SingleSessionTabState();
}

class _SingleSessionTabState extends State<SingleSessionTab> {
  final TextEditingController _controller = TextEditingController();
  String _ussdResponse = '';
  List<Map<String, dynamic>> _simCards = [];
  int? _selectedSimId;

  @override
  void initState() {
    super.initState();
    _loadSimCards();
  }

  Future<void> _loadSimCards() async {
    var status = await Permission.phone.request();
    if (status.isGranted) {
      try {
        final simCards = await UssdLauncher.getSimCards();
        // print("simCards --------------------- $simCards");
        setState(() {
          _simCards = simCards;
          if (simCards.isNotEmpty) {
            _selectedSimId = simCards[0]['subscriptionId'] as int?;
          }
        });
      } catch (e) {
        print("Erreur lors du chargement des cartes SIM: $e");
      }
    } else {
      print("Permission téléphone non accordée");
    }
  }

  Future<void> _sendUssdRequest() async {
    setState(() {
      _ussdResponse = 'Envoi de la requête USSD...';
    });

    try {
      String? response = await UssdLauncher.sendUssdRequest(
        ussdCode: _controller.text,
        subscriptionId: _selectedSimId ?? -1,
      );
      setState(() {
        _ussdResponse = response ?? 'Aucune réponse reçue';
      });
    } catch (e) {
      setState(() {
        _ussdResponse = 'Erreur: ${e.toString()}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        children: [
          DropdownButton<int>(
            value: _selectedSimId,
            hint: const Text('Sélectionner SIM'),
            items: _simCards.map((sim) {
              return DropdownMenuItem<int>(
                value: sim['subscriptionId'],
                child: Text("${sim['displayName']} (${sim['carrierName']})"),
              );
            }).toList(),
            onChanged: (value) {
              setState(() {
                _selectedSimId = value;
              });
            },
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            decoration: const InputDecoration(labelText: 'Entrer le code USSD'),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _sendUssdRequest,
            child: const Text('Envoyer USSD'),
          ),
          const SizedBox(height: 16),
          const Text('Réponse USSD :'),
          Text(
            _ussdResponse,
            style: const TextStyle(color: Colors.blue, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class MultiSessionTab extends StatefulWidget {
  const MultiSessionTab({super.key});

  @override
  _MultiSessionTabState createState() => _MultiSessionTabState();
}

class _MultiSessionTabState extends State<MultiSessionTab> {
  final TextEditingController _ussdController = TextEditingController();
  final List<TextEditingController> _optionControllers = [];
  List<String> _ussdMessages = []; // Liste pour stocker les messages USSD
  bool _isLoading = false;
  List<Map<String, dynamic>> _simCards = [];
  int? _selectedSlotIndex;

  String _sessionStatus = '';

  @override
  void initState() {
    super.initState();
    _loadSimCards();

    // Configurer le listener pour les messages USSD
    UssdLauncher.setUssdMessageListener(_onUssdMessageReceived);
  }

  /// Méthode appelée lorsque un message USSD est reçu.
  void _onUssdMessageReceived(String message) {
    print("Message USSD reçu: $message"); // Journalisation
    setState(() {
      // Ne garder que le dernier message
      _ussdMessages = [message];
      if (message.contains("completed") || message.contains("cancelled")) {
        _sessionStatus = "Session USSD terminée.";
      }
    });
  }

  Future<void> _loadSimCards() async {
    var statut = await Permission.phone.request();
    if (statut.isGranted) {
      final simCards = await UssdLauncher.getSimCards();
      setState(() {
        _simCards = simCards;
        if (simCards.isNotEmpty) {
          _selectedSlotIndex = simCards[0]['slotIndex'];
        }
      });
    } else {
      print("Permission téléphone non accordée");
    }
  }

  void _launchMultiSessionUssd() async {
    setState(() {
      _isLoading = true;
      _ussdMessages.clear(); // Réinitialiser la liste au début d'une nouvelle session
      _sessionStatus = '';
    });

    try {
      List<String> options = _optionControllers.map((controller) => controller.text).toList();

      await UssdLauncher.multisessionUssd(
        code: _ussdController.text,
        slotIndex: (_selectedSlotIndex ?? 0),
        options: options,
      );
      // Aucun besoin de gérer 'res1' ici, les messages sont reçus via le listener
    } catch (e) {
      _updateUssdMessages('\nErreur : ${e.toString()}');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _updateUssdMessages(String newText) {
    setState(() {
      _ussdMessages.add(newText);
    });
  }

  void _addOptionField() {
    setState(() {
      _optionControllers.add(TextEditingController());
    });
  }

  void _removeOptionField() {
    setState(() {
      if (_optionControllers.isNotEmpty) {
        _optionControllers.removeLast().dispose();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: SingleChildScrollView(
        child: Column(
          children: [
            // Affichage en temps réel de slotIndex
            Text(
              'Slot SIM sélectionné : ${_selectedSlotIndex ?? "Aucun"}',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            DropdownButton<int>(
              value: _selectedSlotIndex,
              hint: const Text('Sélectionner SIM'),
              items: _simCards.map((sim) {
                return DropdownMenuItem<int>(
                  value: sim['slotIndex'],
                  child: Text("${sim['displayName']} (${sim['carrierName']})"),
                );
              }).toList(),
              onChanged: (value) {
                setState(() {
                  _selectedSlotIndex = value;
                });
              },
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _ussdController,
              decoration: const InputDecoration(labelText: 'Entrer le code USSD'),
            ),
            ..._optionControllers.asMap().entries.map((entry) {
              return Padding(
                padding: const EdgeInsets.only(top: 8.0),
                child: TextField(
                  controller: entry.value,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(labelText: 'Option ${entry.key + 1}'),
                ),
              );
            }),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton(
                  onPressed: _addOptionField,
                  child: const Text('Ajouter Option'),
                ),
                ElevatedButton(
                  onPressed: _optionControllers.isNotEmpty ? _removeOptionField : null,
                  child: const Text('Retirer Option'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _isLoading ? null : _launchMultiSessionUssd,
              child: const Text('Lancer USSD Multi-Session'),
            ),
            const SizedBox(height: 16),
            const Text('Réponses USSD :'),
            Container(
              height: 200,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.blueAccent),
                borderRadius: BorderRadius.circular(8.0),
              ),
              padding: const EdgeInsets.all(8.0),
              child: ListView.builder(
                itemCount: _ussdMessages.length,
                itemBuilder: (context, index) {
                  final isLastMessage = index == _ussdMessages.length - 1;
                  return Text(
                    _ussdMessages[index],
                    style: TextStyle(
                      color: isLastMessage ? Colors.red : Colors.blue,
                      fontWeight: isLastMessage ? FontWeight.bold : FontWeight.normal,
                      fontSize: isLastMessage ? 16 : 14,
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 16),
            if (_sessionStatus.isNotEmpty)
              Text(
                _sessionStatus,
                style: const TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
              ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    // Nettoyer les contrôleurs de texte
    for (var controller in _optionControllers) {
      controller.dispose();
    }
    _ussdController.dispose();
    super.dispose();
  }
}
```

### Available Methods
- `sendUssdRequest`: Launches a simple USSD request and returns the response as a string. Compatible with Android 8+ (SDK 26+). It is also possible to send the request in one go, that is to say: : `UssdLauncher.sendUssdRequest("*173*2#", -1);`
- `multisessionUssd`: Initiates a multi-step USSD session and returns the final response.
- `getSimCards`: Retrieve information about available SIM cards (subscriptionId, displayName, carrierName, number, slotIndex, countryIso, carrierId, isEmbedded, iccId) .


### SIM Card Selection
You can select which SIM card to use by providing the `subscriptionId` (or `slotIndex`). The value `-1` is the phone's default setting. This feature is compatible with Android 6+ and uses the default value if the SDK is lower.


## Important Notes
- This plugin requires the accessibility service to be enabled on the Android device to function properly.
- Multi-step USSD sessions may vary depending on mobile operators and countries.
- Make sure to properly handle exceptions and error cases in your application.


## Limitations
- This plugin works only on Android.
- Compatibility may vary depending on Android versions and device manufacturers.


## Contribution
Contributions are welcome! Feel free to open an issue or submit a pull request on our [GitHub repository](https://github.com/codianselme/ussd_launcher).


<!-- 
## Licence

Ce projet est sous licence MIT. Voir le fichier [LICENSE](https://github.com/codianselme/ussd_launcher/blob/main/LICENSE) pour plus de détails.
-->