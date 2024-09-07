import 'package:flutter/material.dart';
import 'package:ussd_launcher/ussd_launcher.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: UssdLauncherDemo(),
    );
  }
}

class UssdLauncherDemo extends StatefulWidget {
  const UssdLauncherDemo({super.key});

  @override
  _UssdLauncherDemoState createState() => _UssdLauncherDemoState();
}

class _UssdLauncherDemoState extends State<UssdLauncherDemo> {
  final TextEditingController _controller = TextEditingController();
  String _dialogText = '';

  @override
  void initState() {
    super.initState();
    UssdLauncher.setUssdMessageListener((message) {
      setState(() {
        _dialogText = message;
      });
    });
  }

  void _launchUssd() async {
    bool isAccessibilityEnabled = await UssdLauncher.isAccessibilityPermissionEnabled();
    if (!isAccessibilityEnabled) {
      _showAccessibilityDialog();
      return;
    }

    try {
      await UssdLauncher.launchUssd(_controller.text);
    } catch (e) {
      setState(() {
        _dialogText = 'Error: ${e.toString()}';
      });
    }
  }

  void _showAccessibilityDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text('Accessibility Service Required'),
          content: Text('The USSD Launcher requires the Accessibility Service to be enabled. Would you like to enable it now?'),
          actions: <Widget>[
            TextButton(
              child: Text('Cancel'),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
            TextButton(
              child: Text('Open Settings'),
              onPressed: () {
                Navigator.of(context).pop();
                UssdLauncher.openAccessibilitySettings();
              },
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('USSD Launcher Demo')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            TextField(
              controller: _controller,
              decoration: const InputDecoration(labelText: 'Enter USSD Code'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _launchUssd,
              child: const Text('Launch USSD'),
            ),
            const SizedBox(height: 16),
            const Text('USSD Response:'),
            Text(_dialogText),
          ],
        ),
      ),
    );
  }
}