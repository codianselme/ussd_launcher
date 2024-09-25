package com.kavina.ussd_launcher;

import java.util.HashMap;
import java.util.HashSet;

public interface USSDApi {
    void callUSSDInvoke(String ussdCode, HashMap<String, HashSet<String>> hashMap, USSDController.CallbackInvoke callbackInvoke);
    void callUSSDOverlayInvoke(String ussdCode, HashMap<String, HashSet<String>> hashMap, USSDController.CallbackInvoke callbackInvoke);
}