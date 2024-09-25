package com.kavina.ussd_launcher;

public interface USSDInterface {
    void send(String str, USSDController.CallbackMessage callbackMessage);
    void sendData(String data);
}