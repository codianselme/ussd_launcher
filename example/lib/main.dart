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
          body: TabBarView(
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
  String _dialogText = '';

  void _launchUssd() async {
    print(
        '----------------Launching single session USSD with code: ${_controller.text}');
    try {
      await UssdLauncher.launchUssd(_controller.text);
      print('----------------Single session USSD launched successfully');
    } catch (e) {
      print('----------------Error launching single session USSD: $e');
      setState(() {
        _dialogText = 'Error: ${e.toString()}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
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
            child: const Text('Launch Single Session USSD'),
          ),
          const SizedBox(height: 16),
          const Text('USSD Response:'),
          Text(_dialogText),
        ],
      ),
    );
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
  String _dialogText = '';

  void _printOptionControllers() {
    print('----------------Current _optionControllers content:');
    for (int i = 0; i < _optionControllers.length; i++) {
      print('----------------Option ${i + 1}: ${_optionControllers[i].text}');
    }
  }

  void _launchMultiSessionUssd() async {
    print('Launching multi-session USSD with code: ${_ussdController.text}');
    _printOptionControllers();
    try {
      String? res1 =
          await UssdLauncher.multisessionUssd(code: _ussdController.text);
      print('Initial USSD response (res1): $res1');
      setState(() {
        _dialogText = 'Initial Response: \n $res1';
      });

      // Attendre un peu avant d'envoyer la réponse
      await Future.delayed(const Duration(seconds: 2));

      String? res2 = await UssdLauncher.sendMessage("1");
      print('USSD response after sending "1" (res2): $res2');
      setState(() {
        _dialogText += ' \n Response after sending "1": \n $res2';
      });

      print('Cancelling USSD session');
      await UssdLauncher.cancelSession();
      print('USSD session cancelled');
      setState(() {
        _dialogText += 'Session cancelled';
      });
    } catch (e) {
      print('Error in multi-session USSD: $e');
      setState(() {
        _dialogText = 'Error: ${e.toString()}';
      });
    }
  }

  void _addOptionField() {
    print('----------------Adding new option field');
    setState(() {
      _optionControllers.add(TextEditingController());
    });
    _printOptionControllers();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: SingleChildScrollView(
        child: Column(
          children: [
            TextField(
              controller: _ussdController,
              decoration: const InputDecoration(labelText: 'Enter USSD Code'),
            ),
            const SizedBox(height: 16),
            ..._optionControllers.asMap().entries.map((entry) => Padding(
                  padding: const EdgeInsets.only(bottom: 8.0),
                  child: TextField(
                    controller: entry.value,
                    decoration: InputDecoration(
                        labelText: 'Enter Option ${entry.key + 1}'),
                    onChanged: (value) {
                      print(
                          '----------------Option ${entry.key + 1} changed to: $value');
                      _printOptionControllers();
                    },
                  ),
                )),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _addOptionField,
              child: const Text('Add Option Field'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _launchMultiSessionUssd,
              child: const Text('Launch Multi-Session USSD'),
            ),
            const SizedBox(height: 16),
            const Text('USSD Response:'),
            Text(_dialogText),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    print('----------------Disposing MultiSessionTab');
    _printOptionControllers();
    for (var controller in _optionControllers) {
      controller.dispose();
    }
    super.dispose();
  }
}
