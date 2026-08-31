package com.kavina.ussd_launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

class UssdOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val CHANNEL_ID = "UssdOverlayServiceChannel"

    companion object {
        private var instance: UssdOverlayService? = null
        private var overlayMessage = "Opération USSD en cours..."
        
        fun isRunning(): Boolean = instance != null
        
        fun updateMessage(message: String) {
            overlayMessage = message
            instance?.updateOverlayText(message)
        }
        
        fun canDrawOverlay(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        fun openOverlaySettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Start foreground immediately to prevent crash
        startForegroundService()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "USSD Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USSD Launcher")
            .setContentText("Opération USSD en cours...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    private fun createOverlayView() {
        // Create the overlay layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            
            // Background with rounded corners
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = 24f
            }
        }
        
        // Progress indicator
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
        }
        layout.addView(progressBar)
        
        // Title
        val titleView = TextView(this).apply {
            text = "USSD en cours"
            textSize = 20f
            setTextColor(Color.parseColor("#1976D2"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        layout.addView(titleView)
        
        // Message
        val messageView = TextView(this).apply {
            tag = "overlay_message"
            text = overlayMessage
            textSize = 16f
            setTextColor(Color.parseColor("#424242"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(messageView)
        
        // Subtitle
        val subtitleView = TextView(this).apply {
            text = "Veuillez patienter..."
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }
        layout.addView(subtitleView)

        overlayView = layout

        // Window params - cover most of the screen
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(overlayView, params)
            println("UssdOverlayService: Overlay displayed")
        } catch (e: Exception) {
            println("UssdOverlayService: Error displaying overlay: ${e.message}")
        }
    }

    private fun updateOverlayText(message: String) {
        overlayView?.findViewWithTag<TextView>("overlay_message")?.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            println("UssdOverlayService: Error removing overlay: ${e.message}")
        }
        overlayView = null
        instance = null
        println("UssdOverlayService: Overlay removed")
    }
}
