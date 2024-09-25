package com.kavina.ussd_launcher;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.List;

// Autres imports...

public class USSDController implements USSDInterface, USSDApi {
    protected static final String KEY_ERROR = "KEY_ERROR";
    protected static final String KEY_LOGIN = "KEY_LOGIN";
    protected static USSDController instance;
    protected CallbackInvoke callbackInvoke;
    protected CallbackMessage callbackMessage;
    protected Context context;
    protected HashMap<String, HashSet<String>> map;
    protected Boolean isRunning = false;
    private USSDInterface ussdInterface = this;
    private USSDService ussdService;

    private static final String TAG = "USSDController";

    // Queue pour gérer les étapes USSD
    private Queue<String> ussdSteps = new LinkedList<>();

    public interface CallbackInvoke {
        void over(String str);
        void responseInvoke(String str);
    }

    public interface CallbackMessage {
        void responseMessage(String str);
    }

    public static USSDController getInstance(Context context, USSDService ussdService) {
        if (instance == null) {
            instance = new USSDController(context, ussdService);
        }
        return instance;
    }

    private USSDController(Context context, USSDService ussdService) {
        this.context = context;
        this.ussdService = ussdService;
    }

    // Méthode pour ajouter des étapes
    public void addUSSDStep(String response) {
        ussdSteps.add(response);
        Log.d(TAG, "USSD Step Added: " + response);
    }

    // Méthode pour traiter la prochaine étape
    private void processNextStep() {
        String step = ussdSteps.poll();
        if (step != null && ussdService != null) {
            Log.d(TAG, "Processing next USSD step: " + step);
            ussdService.send(step);
        } else {
            Log.d(TAG, "No more USSD steps to process.");
            if (callbackInvoke != null) {
                callbackInvoke.responseInvoke("USSD sequence completed.");
            }
        }
    }

    // Méthode pour gérer la réponse USSD
    public void handleUSSDResponse(String response) {
        Log.d(TAG, "USSD Response Received: " + response);
        if (callbackMessage != null) {
            callbackMessage.responseMessage(response);
        }
        processNextStep();
    }

    @Override
    public void send(String str, CallbackMessage callbackMessage) {
        this.callbackMessage = callbackMessage;

        if (ussdService != null) {
            ussdService.send(str);
        } else {
            Log.e(TAG, "USSDService instance is null");
            if (callbackMessage != null) {
                callbackMessage.responseMessage("USSDService instance is null");
            }
        }
    }

    public void callUSSDInvoke(String str, HashMap<String, HashSet<String>> hashMap, CallbackInvoke callbackInvoke) {
        this.callbackInvoke = callbackInvoke;
        this.map = hashMap;
        if (verifyAccessibilityAccess(this.context)) {
            ussdService.send(str);
            isRunning = true;
        } else {
            this.callbackInvoke.over("Check your accessibility settings.");
        }
    }

    @Override
    public void sendData(String data) {
        Log.d(TAG, "Sending data: " + data);
        send(data, callbackMessage);
    }

    private boolean verifyAccessibilityAccess(Context context) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager == null) return false;
        return accessibilityManager.isEnabled();
    }

    // **Implémentation de la méthode callUSSDOverlayInvoke(String, HashMap<String, HashSet<String>>, CallbackInvoke)**
    @Override
    public void callUSSDOverlayInvoke(String ussdCode, HashMap<String, HashSet<String>> hashMap, CallbackInvoke callbackInvoke) {
        this.callbackInvoke = callbackInvoke;
        this.map = hashMap;
        if (verifyAccessibilityAccess(this.context)) {
            ussdService.sendOverlay(ussdCode); // Assurez-vous que USSDService a une méthode sendOverlay
            isRunning = true;
        } else {
            this.callbackInvoke.over("Check your accessibility settings.");
        }
    }

    // Autres méthodes existantes...
}