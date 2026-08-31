package com.kavina.ussd_launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Path
import java.util.Locale

class UssdAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: UssdAccessibilityService? = null
        private var pendingMessages: ArrayDeque<String> = ArrayDeque()
        var hideDialogs = false
        private var lastUssdMessage: String? = null
        private var currentStepIndex = 0
        private var retryCount = 0
        private var isDialogReady = false
        private var lastDialogDetectedTime = 0L
        
        // Optimized retries with faster response when dialog is detected
        private const val MAX_RETRIES = 10
        private const val INITIAL_RETRY_DELAY_MS = 250L
        private const val MAX_RETRY_DELAY_MS = 1000L
        private const val DIALOG_STABILITY_DELAY_MS = 400L  // Wait for dialog to stabilize
        private const val POST_CLICK_DELAY_MS = 250L  // Delay after clicking confirm
        
        // Packages that can display USSD dialogs
        private val USSD_PACKAGES = setOf(
            "com.android.phone",
            "com.samsung.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.sec.android.app.telephonyui",
            "com.huawei.systemmanager",
            "com.miui.securitycenter",
            "com.coloros.phonemanager",
            "com.oppo.usercenter",
            "com.asus.ussd",
            "com.lge.phoneui",
            "com.sonymobile.android.phone"
        )
        
        // List of common confirm button texts in multiple languages
        private val CONFIRM_BUTTON_TEXTS = listOf(
            "send", "ok", "submit", "yes", "confirm", "continue", "reply",
            "envoyer", "confirmer", "oui", "valider", "continuer", "répondre",
            "enviar", "aceptar", "sí", "confirmar",
            "senden", "ja", "bestätigen", "antworten"
        )
        
        // Cancel button texts to avoid
        private val CANCEL_BUTTON_TEXTS = listOf(
            "cancel", "annuler", "cancelar", "abbrechen", "non", "no", "dismiss", "fermer", "close"
        )

        fun sendReply(messages: List<String>) {
            println("UssdAccessibilityService: ══════════════════════════════════════")
            println("UssdAccessibilityService: Setting pending messages: $messages")
            println("UssdAccessibilityService: ══════════════════════════════════════")
            pendingMessages.clear()
            pendingMessages.addAll(messages)
            retryCount = 0
            isDialogReady = false
            // Schedule reply attempt - will wait for dialog to be ready
            instance?.scheduleReplyAttempt()
        }

        fun cancelSession() {
            instance?.let { service ->
                try {
                    val rootInActiveWindow = service.rootInActiveWindow
                    rootInActiveWindow?.let { root ->
                        val cancelButton = root.findAccessibilityNodeInfosByViewId("android:id/button2")
                        val clicked = cancelButton?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                        
                        if (!clicked) {
                            service.performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        
                        cancelButton?.forEach { it.recycle() }
                        root.recycle()
                    }
                    println("UssdAccessibilityService: USSD session cancelled")
                    resetState()
                } catch (e: Exception) {
                    println("UssdAccessibilityService: Error cancelling session: ${e.message}")
                }
            }
        }
        
        fun isServiceRunning(): Boolean = instance != null
        
        fun resetLastMessage() {
            resetState()
        }
        
        private fun resetState() {
            lastUssdMessage = null
            currentStepIndex = 0
            retryCount = 0
            isDialogReady = false
            lastDialogDetectedTime = 0L
            pendingMessages.clear()
        }
        
        fun notifyDialogReady() {
            isDialogReady = true
            lastDialogDetectedTime = System.currentTimeMillis()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Calculate progressive delay based on retry count
     */
    private fun getRetryDelay(): Long {
        val delay = INITIAL_RETRY_DELAY_MS + (retryCount * 200L)
        return minOf(delay, MAX_RETRY_DELAY_MS)
    }
    
    /**
     * Schedule a reply attempt with progressive retry logic
     */
    private fun scheduleReplyAttempt() {
        val delay = getRetryDelay()
        println("UssdAccessibilityService: Scheduling reply attempt in ${delay}ms")
        handler.postDelayed({
            tryPerformReply()
        }, delay)
    }

    /**
     * Try to perform reply with robust detection and retry mechanism
     */
    private fun tryPerformReply() {
        if (pendingMessages.isEmpty()) {
            retryCount = 0
            println("UssdAccessibilityService: No pending messages to send")
            return
        }

        val message = pendingMessages.firstOrNull() ?: return
        println("UssdAccessibilityService: ────────────────────────────────────────")
        println("UssdAccessibilityService: Attempt ${retryCount + 1}/$MAX_RETRIES")
        println("UssdAccessibilityService: Message to send: '$message'")
        println("UssdAccessibilityService: Dialog ready: $isDialogReady")
        println("UssdAccessibilityService: ────────────────────────────────────────")

        val rootInActiveWindow = this.rootInActiveWindow
        if (rootInActiveWindow == null) {
            println("UssdAccessibilityService: ⚠️ No active window available")
            retryIfNeeded()
            return
        }
        
        try {
            // Step 1: Verify this is a USSD dialog
            if (!isUssdDialogPresent(rootInActiveWindow)) {
                println("UssdAccessibilityService: ⚠️ USSD dialog not detected, waiting...")
                retryIfNeeded()
                return
            }
            
            // Step 2: Find and validate the input field
            val editText = findInputField(rootInActiveWindow)
            if (editText == null) {
                println("UssdAccessibilityService: ⚠️ Input field not found, waiting...")
                retryIfNeeded()
                return
            }
            
            // Step 3: Verify input field is ready (visible and enabled)
            if (!isInputFieldReady(editText)) {
                println("UssdAccessibilityService: ⚠️ Input field not ready (not visible/enabled)")
                editText.recycle()
                retryIfNeeded()
                return
            }
            
            // Step 4: Find confirm button BEFORE setting text
            val confirmButton = findConfirmButton(rootInActiveWindow)
            if (confirmButton == null) {
                println("UssdAccessibilityService: ⚠️ Confirm button not found, waiting...")
                editText.recycle()
                retryIfNeeded()
                return
            }
            
            println("UssdAccessibilityService: ✓ Dialog validated - proceeding with input")
            
            // Step 5: Focus the input field first
            editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // Small delay to ensure focus is set
            Thread.sleep(50)
            
            // Step 6: Clear existing text and set new text
            val clearBundle = Bundle()
            clearBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearBundle)
            
            Thread.sleep(30)
            
            val bundle = Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            val setTextSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            
            println("UssdAccessibilityService: [Step ${currentStepIndex + 1}] Set text '$message': $setTextSuccess")
            
            // Step 7: Verify text was actually set
            if (setTextSuccess) {
                Thread.sleep(50)
                val verifiedText = verifyTextWasSet(message)
                if (!verifiedText) {
                    println("UssdAccessibilityService: ⚠️ Text verification failed, retrying...")
                    editText.recycle()
                    confirmButton.recycle()
                    retryIfNeeded()
                    return
                }
            } else {
                println("UssdAccessibilityService: ⚠️ Failed to set text, retrying...")
                editText.recycle()
                confirmButton.recycle()
                retryIfNeeded()
                return
            }
            
            editText.recycle()
            
            // Step 8: Successfully set text - now click confirm
            pendingMessages.removeFirstOrNull()
            currentStepIndex++
            retryCount = 0
            isDialogReady = false  // Reset for next dialog
            
            println("UssdAccessibilityService: ✓ Text set successfully, clicking confirm button...")
            
            // Click confirm button with verification
            handler.postDelayed({
                clickConfirmButtonWithRetry(confirmButton)
            }, POST_CLICK_DELAY_MS)
            
        } catch (e: Exception) {
            println("UssdAccessibilityService: ❌ Error in tryPerformReply: ${e.message}")
            e.printStackTrace()
            retryIfNeeded()
        } finally {
            try {
                rootInActiveWindow.recycle()
            } catch (e: Exception) {
                // Already recycled
            }
        }
    }
    
    /**
     * Check if a USSD dialog is currently present
     */
    private fun isUssdDialogPresent(root: AccessibilityNodeInfo): Boolean {
        // Look for typical USSD dialog elements
        val hasEditText = findInputField(root) != null
        val hasButtons = findNodesByClassName(root, "android.widget.Button").isNotEmpty()
        val hasTextView = findNodesByClassName(root, "android.widget.TextView").isNotEmpty()
        
        val isPresent = hasEditText && hasButtons && hasTextView
        println("UssdAccessibilityService: Dialog check - EditText: $hasEditText, Buttons: $hasButtons, TextView: $hasTextView")
        return isPresent
    }
    
    /**
     * Check if input field is ready for interaction
     */
    private fun isInputFieldReady(editText: AccessibilityNodeInfo): Boolean {
        val isVisible = editText.isVisibleToUser
        val isEnabled = editText.isEnabled
        val isFocusable = editText.isFocusable
        
        println("UssdAccessibilityService: Input field - Visible: $isVisible, Enabled: $isEnabled, Focusable: $isFocusable")
        return isVisible && isEnabled
    }
    
    /**
     * Verify that the text was actually set in the input field
     */
    private fun verifyTextWasSet(expectedText: String): Boolean {
        val root = this.rootInActiveWindow ?: return false
        try {
            val editText = findInputField(root)
            if (editText != null) {
                val actualText = editText.text?.toString() ?: ""
                val matches = actualText == expectedText
                println("UssdAccessibilityService: Text verification - Expected: '$expectedText', Actual: '$actualText', Match: $matches")
                editText.recycle()
                return matches
            }
        } finally {
            root.recycle()
        }
        return false
    }
    
    /**
     * Retry if max retries not reached with progressive delay
     */
    private fun retryIfNeeded() {
        retryCount++
        if (retryCount < MAX_RETRIES) {
            val delay = getRetryDelay()
            println("UssdAccessibilityService: 🔄 Scheduling retry ${retryCount + 1}/$MAX_RETRIES in ${delay}ms")
            scheduleReplyAttempt()
        } else {
            println("UssdAccessibilityService: ❌ Max retries ($MAX_RETRIES) reached, giving up on current message")
            // Move to next message
            val skippedMessage = pendingMessages.removeFirstOrNull()
            println("UssdAccessibilityService: Skipped message: '$skippedMessage'")
            retryCount = 0
            isDialogReady = false
            if (pendingMessages.isNotEmpty()) {
                println("UssdAccessibilityService: Moving to next message...")
                scheduleReplyAttempt()
            }
        }
    }

    /**
     * Click confirm button with retry mechanism
     */
    private fun clickConfirmButtonWithRetry(button: AccessibilityNodeInfo, attempt: Int = 1) {
        val maxClickAttempts = 3
        
        try {
            val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            println("UssdAccessibilityService: [Step $currentStepIndex] Click confirm (attempt $attempt): $clickSuccess")
            
            if (!clickSuccess && attempt < maxClickAttempts) {
                // Try alternative click methods
                handler.postDelayed({
                    tryAlternativeClick(button, attempt + 1)
                }, 300)
            } else {
                button.recycle()
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService: Error clicking button: ${e.message}")
            button.recycle()
        }
    }
    
    /**
     * Try alternative click methods
     */
    private fun tryAlternativeClick(button: AccessibilityNodeInfo, attempt: Int) {
        val root = this.rootInActiveWindow
        if (root != null) {
            try {
                val freshButton = findConfirmButton(root)
                if (freshButton != null) {
                    clickConfirmButtonWithRetry(freshButton, attempt)
                } else {
                    // Try clicking any non-cancel button
                    tryAlternativeConfirmMethods(root)
                }
            } finally {
                root.recycle()
            }
        }
        try {
            button.recycle()
        } catch (e: Exception) {
            // Already recycled
        }
    }

    /**
     * Find and click the confirm button (legacy method for compatibility)
     */
    private fun clickConfirmButton() {
        val rootInActiveWindow = this.rootInActiveWindow ?: return
        
        try {
            val button = findConfirmButton(rootInActiveWindow)
            if (button != null) {
                clickConfirmButtonWithRetry(button)
            } else {
                println("UssdAccessibilityService: Confirm button not found, trying alternatives")
                tryAlternativeConfirmMethods(rootInActiveWindow)
            }
        } finally {
            rootInActiveWindow.recycle()
        }
    }

    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editTexts = findNodesByClassName(root, "android.widget.EditText")
        val result = editTexts.firstOrNull()
        editTexts.filter { it != result }.forEach { it.recycle() }
        return result
    }

    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = findNodesByClassName(root, "android.widget.Button")
        val confirmButton = buttons.firstOrNull { button ->
            val buttonText = button.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            CONFIRM_BUTTON_TEXTS.any { confirmText -> buttonText.contains(confirmText) }
        }
        buttons.filter { it != confirmButton }.forEach { it.recycle() }
        return confirmButton
    }

    private fun tryAlternativeConfirmMethods(root: AccessibilityNodeInfo) {
        val allButtons = findNodesByClassName(root, "android.widget.Button")
        
        // Sort buttons - prefer non-cancel buttons
        val sortedButtons = allButtons.sortedByDescending { button ->
            val buttonText = button.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            when {
                CONFIRM_BUTTON_TEXTS.any { buttonText.contains(it) } -> 2
                CANCEL_BUTTON_TEXTS.any { buttonText.contains(it) } -> 0
                else -> 1
            }
        }
        
        for (button in sortedButtons) {
            val buttonText = button.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            
            // Skip cancel buttons
            if (CANCEL_BUTTON_TEXTS.any { buttonText.contains(it) }) {
                println("UssdAccessibilityService: Skipping cancel button: ${button.text}")
                continue
            }
            
            println("UssdAccessibilityService: Trying button: ${button.text}")
            val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("UssdAccessibilityService: ✓ Successfully clicked: ${button.text}")
                allButtons.forEach { it.recycle() }
                return
            }
        }
        allButtons.forEach { it.recycle() }

        // Try clicking by view ID as fallback
        tryClickByViewId(root)
    }
    
    /**
     * Try to click confirm button by common view IDs
     */
    private fun tryClickByViewId(root: AccessibilityNodeInfo) {
        val buttonIds = listOf(
            "android:id/button1",  // Positive button
            "android:id/button3",  // Neutral button
            "com.android.phone:id/send_button",
            "com.android.phone:id/button_send"
        )
        
        for (buttonId in buttonIds) {
            val buttons = root.findAccessibilityNodeInfosByViewId(buttonId)
            val button = buttons?.firstOrNull()
            if (button != null) {
                println("UssdAccessibilityService: Found button by ID: $buttonId")
                val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clickSuccess) {
                    println("UssdAccessibilityService: ✓ Clicked button by ID: $buttonId")
                    buttons.forEach { it.recycle() }
                    return
                }
                buttons.forEach { it.recycle() }
            }
        }
        
        println("UssdAccessibilityService: ⚠️ No confirm button found by any method")
    }

    private fun findNodesByClassName(root: AccessibilityNodeInfo?, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className?.toString() == className) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    private fun isUssdPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        return USSD_PACKAGES.any { ussdPkg -> 
            packageName.lowercase(Locale.ROOT).contains(ussdPkg.lowercase(Locale.ROOT)) 
        } || packageName.lowercase(Locale.ROOT).contains("phone") 
          || packageName.lowercase(Locale.ROOT).contains("dialer")
          || packageName.lowercase(Locale.ROOT).contains("telecom")
    }

    private fun isValidUssdMessage(message: String): Boolean {
        val lowerMessage = message.lowercase(Locale.ROOT)
        
        if (message.length < 3) return false
        
        val invalidPatterns = listOf(
            "play store", "google play", "raccourci", "shortcut",
            "services téléchargés", "downloaded services", 
            "volume", "settings", "paramètres",
            "notification", "battery", "batterie",
            "wifi", "bluetooth", "airplane", "avion"
        )
        
        if (invalidPatterns.any { lowerMessage.contains(it) }) {
            return false
        }
        
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString()
        
        // Only process events from USSD-related packages
        if (!isUssdPackage(packageName)) {
            return
        }
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && hideDialogs) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // New window appeared - this is likely a new USSD dialog
                    println("UssdAccessibilityService: 🪟 Window state changed - package: $packageName")
                    handleDialogAppeared(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Content changed - dialog might be ready now
                    handleDialogContentChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    // A view got focus - might be the input field
                    println("UssdAccessibilityService: 🎯 View focused in USSD dialog")
                    checkAndTriggerReply()
                }
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService: Error in onAccessibilityEvent: ${e.message}")
        }
    }
    
    /**
     * Handle when a new dialog window appears
     */
    private fun handleDialogAppeared(event: AccessibilityEvent) {
        val nodeInfo = event.source ?: return
        
        try {
            // Check if this is a valid USSD dialog
            val root = this.rootInActiveWindow
            if (root != null) {
                val hasInputField = findInputField(root) != null
                val hasButtons = findNodesByClassName(root, "android.widget.Button").isNotEmpty()
                val hasTextView = findNodesByClassName(root, "android.widget.TextView").isNotEmpty()
                
                // Any USSD dialog with buttons and text - always capture content first
                if (hasButtons && hasTextView) {
                    println("UssdAccessibilityService: ✓ USSD dialog detected (hasInput: $hasInputField)")
                    
                    // ALWAYS extract and send dialog content first
                    extractAndSendDialogContent(root)
                    
                    // If it's an interactive dialog with input field AND we have pending messages
                    if (hasInputField && pendingMessages.isNotEmpty()) {
                        notifyDialogReady()
                        // Wait for dialog to stabilize before attempting reply
                        handler.removeCallbacksAndMessages(null)  // Cancel any pending attempts
                        handler.postDelayed({
                            println("UssdAccessibilityService: Dialog stabilized, attempting reply...")
                            retryCount = 0  // Reset retry count for new dialog
                            tryPerformReply()
                        }, DIALOG_STABILITY_DELAY_MS)
                    } else if (hasInputField) {
                        // Interactive dialog but no pending messages - this is the final response
                        println("UssdAccessibilityService: ✓ Final interactive dialog (no more options to send)")
                    } else {
                        // Dialog without input field - final message
                        println("UssdAccessibilityService: ✓ Final USSD dialog (no input field)")
                    }
                }
                root.recycle()
            }
        } finally {
            nodeInfo.recycle()
        }
    }
    
    /**
     * Handle when dialog content changes
     */
    private fun handleDialogContentChanged(event: AccessibilityEvent) {
        val nodeInfo = event.source ?: return
        
        try {
            val root = this.rootInActiveWindow
            if (root != null) {
                // Check if dialog is now ready (has all required elements)
                val hasInputField = findInputField(root) != null
                val hasButtons = findNodesByClassName(root, "android.widget.Button").isNotEmpty()
                val hasTextView = findNodesByClassName(root, "android.widget.TextView").isNotEmpty()
                
                // Any USSD dialog with buttons and text
                if (hasButtons && hasTextView) {
                    // ALWAYS extract and send dialog content
                    extractAndSendDialogContent(root)
                    
                    // If interactive dialog with pending messages, trigger reply
                    if (hasInputField && pendingMessages.isNotEmpty() && !isDialogReady) {
                        println("UssdAccessibilityService: ✓ Dialog content ready, will send reply")
                        notifyDialogReady()
                        checkAndTriggerReply()
                    }
                }
                root.recycle()
            }
        } finally {
            nodeInfo.recycle()
        }
    }
    
    /**
     * Extract dialog content and send to Flutter
     */
    private fun extractAndSendDialogContent(root: AccessibilityNodeInfo) {
        val dialogContent = getCompleteDialogContent(root)
        
        if (dialogContent != null && dialogContent.isNotBlank()) {
            // Log the complete dialog content
            println("")
            println("╔══════════════════════════════════════════════════════════════")
            println("║ 📱 USSD DIALOG - Step ${currentStepIndex + 1}")
            println("╠══════════════════════════════════════════════════════════════")
            dialogContent.lines().forEach { line ->
                if (line.isNotBlank()) {
                    println("║ $line")
                }
            }
            println("╚══════════════════════════════════════════════════════════════")
            println("")
            
            // Send to Flutter if different from last message
            if (isValidUssdMessage(dialogContent) && dialogContent != lastUssdMessage) {
                lastUssdMessage = dialogContent
                println(">>> SENDING TO FLUTTER: ${dialogContent.take(50)}...")
                UssdLauncherPlugin.onUssdResult(dialogContent)
            }
        }
    }
    
    /**
     * Check conditions and trigger reply if appropriate
     */
    private fun checkAndTriggerReply() {
        if (pendingMessages.isEmpty()) {
            return
        }
        
        // Only trigger if we're not already in a retry cycle
        if (retryCount == 0 && isDialogReady) {
            val timeSinceDialogDetected = System.currentTimeMillis() - lastDialogDetectedTime
            
            // Ensure dialog has been stable for a minimum time
            if (timeSinceDialogDetected >= DIALOG_STABILITY_DELAY_MS) {
                println("UssdAccessibilityService: Conditions met, triggering reply...")
                handler.removeCallbacksAndMessages(null)
                handler.post {
                    tryPerformReply()
                }
            } else {
                val remainingDelay = DIALOG_STABILITY_DELAY_MS - timeSinceDialogDetected
                println("UssdAccessibilityService: Waiting ${remainingDelay}ms for dialog stability...")
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    tryPerformReply()
                }, remainingDelay)
            }
        }
    }

    /**
     * Get the complete content of the USSD dialog
     */
    private fun getCompleteDialogContent(node: AccessibilityNodeInfo): String? {
        val allTexts = mutableListOf<String>()
        collectAllTextViewContent(node, allTexts)
        
        if (allTexts.isEmpty()) return null
        
        // Filter out button texts and system labels
        val meaningfulTexts = allTexts.filter { text ->
            val lower = text.lowercase(Locale.ROOT)
            !CONFIRM_BUTTON_TEXTS.contains(lower) &&
            lower != "annuler" && lower != "cancel" &&
            lower != "message ussd" && lower != "ussd code" &&
            text.length > 2
        }
        
        return if (meaningfulTexts.isNotEmpty()) {
            meaningfulTexts.joinToString("\n").trim()
        } else {
            null
        }
    }
    
    /**
     * Recursively collect all TextView content from the node tree
     */
    private fun collectAllTextViewContent(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        if (node.className?.toString() == "android.widget.EditText") {
            return
        }

        if (node.className?.toString() == "android.widget.TextView" && node.text != null) {
            val text = node.text.toString().trim()
            if (text.isNotBlank() && text.length > 1) {
                texts.add(text)
            }
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            try {
                collectAllTextViewContent(childNode, texts)
            } finally {
                childNode.recycle()
            }
        }
    }

    override fun onInterrupt() {
        println("UssdAccessibilityService: Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        println("UssdAccessibilityService: Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        pendingMessages.clear()
        lastUssdMessage = null
        currentStepIndex = 0
        retryCount = 0
        handler.removeCallbacksAndMessages(null)
        println("UssdAccessibilityService: Service destroyed")
    }
}