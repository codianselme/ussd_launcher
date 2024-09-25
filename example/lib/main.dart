import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:ussd_launcher/ussd_launcher.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: USSDHome(),
    );
  }
}

class USSDHome extends StatefulWidget {
  const USSDHome({super.key});

  @override
  _USSDHomeState createState() => _USSDHomeState();
}

class _USSDHomeState extends State<USSDHome> {
  final UssdLauncher _touchUssdPlugin = UssdLauncher();
  String _result = '';
  int _simSlot = 0; // Default SIM slot
  // final _touchUssdPlugin = TouchUssd();


  @override
  void initState() {
    super.initState();
  }

  Future<void> _sendUSSD() async {
    // Vérification des permissions
    var status = await Permission.phone.request();
    if (status.isGranted) {
      try {
        List<String> ussdSteps = [
          "4"
          // "1234567890",
          // "0987654321",
          // "50",
          // "Facture",
          // "1234",
        ];

        await _touchUssdPlugin.executeUSSDSequence("*880#", _simSlot, ussdSteps);
      } on PlatformException catch (e) {
        setState(() {
          _result = "Error: ${e.message}";
        });
      }
    } else {
      setState(() {
        _result = "Permission denied";
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('USSD Example App'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            DropdownButton<int>(
              value: _simSlot,
              items: const [
                DropdownMenuItem(value: 0, child: Text('SIM Slot 1')),
                DropdownMenuItem(value: 1, child: Text('SIM Slot 2')),
              ],
              onChanged: (value) {
                setState(() {
                  _simSlot = value!;
                });
              },
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _sendUSSD, // Call the method to send the request
              child: const Text('Send USSD Request'),
            ),
            const SizedBox(height: 20),
            Text('Result: $_result'),
          ],
        ),
      ),
    );
  }
}
