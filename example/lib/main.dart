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

  Future<void> _sendUssdRequest() async {
    try {
      final response = await UssdLauncher.launchUssd(_controller.text);
      setState(() {
        _ussdResponse = response;
      });
    } catch (e) {
      setState(() {
        _ussdResponse = 'Error: ${e.toString()}';
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
            decoration: const InputDecoration(
              labelText: 'Enter USSD Code',
              hintText: 'e.g. *880#',
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _sendUssdRequest,
            child: const Text('Launch Single Session USSD'),
          ),
          const SizedBox(height: 16),
          const Text('USSD Response:'),
          Text(_ussdResponse),
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
  bool _isLoading = false;

  void _launchMultiSessionUssd() async {
    setState(() {
      _isLoading = true;
      _dialogText = '';
    });

    try {
      String? res1 =
          await UssdLauncher.multisessionUssd(code: _ussdController.text);
      setState(() {
        _dialogText = 'Initial Response: \n $res1';
      });

      // Attendre un peu avant d'envoyer la réponse
      await Future.delayed(const Duration(seconds: 1));

      // Parcourir toutes les options dynamiquement
      for (int index = 0; index < _optionControllers.length; index++) {
        var controller = _optionControllers[index];
        String? res = await UssdLauncher.sendMessage(controller.text);

        setState(() {
          _dialogText +=
              ' \n Response after sending "1": \n ${controller.text}';
        });

        // Attendre un peu avant d'envoyer la réponse
        await Future.delayed(const Duration(seconds: 1));

        _updateDialogText(
            '\nResponse after sending "${controller.text}": \n $res');

        // Attendre 1 seconde entre chaque option
        await Future.delayed(const Duration(seconds: 1));
      }

      await UssdLauncher.cancelSession();
      _updateDialogText('\nSession cancelled');

      setState(() {
        _dialogText += 'Session cancelled';
      });
    } catch (e) {
      _updateDialogText('\nError: ${e.toString()}');

      setState(() {
        _dialogText = 'Error: ${e.toString()}';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _updateDialogText(String newText) {
    setState(() {
      _dialogText += newText;
    });
  }

  void _addOptionField() {
    setState(() {
      _optionControllers.add(TextEditingController());
    });
  }

  // Supprime le dernier champ d'option
  void _removeOptionField() {
    if (_optionControllers.isNotEmpty) {
      setState(() {
        _optionControllers.last
            .dispose(); // Libère les ressources du contrôleur
        _optionControllers.removeLast();
      });
    }
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
                    onChanged: (value) {},
                  ),
                )),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton(
                  onPressed: _addOptionField,
                  child: const Text('Add Option'),
                ),
                ElevatedButton(
                  onPressed:
                      _optionControllers.isNotEmpty ? _removeOptionField : null,
                  child: const Text('Remove Option'),
                ),
              ],
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
    for (var controller in _optionControllers) {
      controller.dispose();
    }
    super.dispose();
  }
}
