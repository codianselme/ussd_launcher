package com.kavina.ussd_launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import android.os.Handler;
import android.os.Looper;

/* loaded from: classes2.dex */
public class USSDService extends AccessibilityService {
    private static final String TAG = "USSDService";
    private AccessibilityEvent currentEvent; // Changed from static to instance
    private USSDService ussdService;
    private Context context;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        context = this; // Initialize context here
        ussdService = this; // Initialize USSDService instance
        USSDController.getInstance(context, ussdService); // Pass USSDService instance
        Log.d(TAG, "Accessibility Service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "Received AccessibilityEvent: " + event);
        currentEvent = event; // Store the current event

        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String className = event.getClassName().toString();
            if (className.equals("com.android.phone.MMIDialogActivity") || className.equals("android.app.AlertDialog")) {
                // Existing logic...
            }
        } else {
            Log.e(TAG, "Received null AccessibilityEvent or unsupported event type");
        }
    }

    @Override
    public void onInterrupt() {
        // Implémentation requise de la méthode abstraite
        Log.d(TAG, "Accessibility Service interrupted");
    }

    // Méthode existante pour envoyer des données USSD
    public void send(String str) {
        if (currentEvent != null) {
            setTextIntoField(currentEvent, str);
            clickOnButton(currentEvent, 1);
        } else {
            Log.e(TAG, "AccessibilityEvent is null, cannot send data");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentEvent != null) {
                    setTextIntoField(currentEvent, str);
                    clickOnButton(currentEvent, 1);
                } else {
                    Log.e(TAG, "Still null after delay, cannot send data");
                    // Optionnellement, notifier le côté Flutter de l'échec
                }
            }, 1000);
        }
    }

    // **Nouvelles méthodes pour interagir avec l'interface utilisateur**

    /**
     * Définit le texte dans un champ spécifique de l'événement actuel.
     *
     * @param event L'événement d'accessibilité actuel.
     * @param text Le texte à définir.
     */
    private void setTextIntoField(AccessibilityEvent event, String text) {
        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo != null) {
            AccessibilityNodeInfoCompat node = new AccessibilityNodeInfoCompat(nodeInfo);
            List<AccessibilityNodeInfoCompat> textFields = node.findAccessibilityNodeInfosByViewId("android:id/message");
            for (AccessibilityNodeInfoCompat textField : textFields) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                textField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                Log.d(TAG, "Text set into field: " + text);
            }
        } else {
            Log.e(TAG, "NodeInfo is null, cannot set text");
        }
    }

    /**
     * Cliquez sur un bouton spécifique dans l'événement actuel.
     *
     * @param event L'événement d'accessibilité actuel.
     * @param buttonIndex L'index du bouton à cliquer.
     */
    private void clickOnButton(AccessibilityEvent event, int buttonIndex) {
        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo != null) {
            AccessibilityNodeInfoCompat node = new AccessibilityNodeInfoCompat(nodeInfo);
            List<AccessibilityNodeInfoCompat> buttons = node.findAccessibilityNodeInfosByViewId("android:id/button1");
            if (buttonIndex < buttons.size()) {
                AccessibilityNodeInfoCompat button = buttons.get(buttonIndex);
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicked on button index: " + buttonIndex);
            } else {
                Log.e(TAG, "Button index out of bounds");
            }
        } else {
            Log.e(TAG, "NodeInfo is null, cannot click on button");
        }
    }

    // **New method to send USSD with overlay**
    public void sendOverlay(String ussdCode) {
        Log.d(TAG, "Sending USSD Code with Overlay: " + ussdCode);
        // Implementation specific to the overlay
        // ...
    }
}