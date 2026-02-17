package com.kiosk.kioskmode

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 500L // Back to 500ms for stability
    private var lastCheckedTime = System.currentTimeMillis()
    
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var usageStatsManager: UsageStatsManager

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        whitelistManager = WhitelistManager(this)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        startForeground(1, createNotification())
        Log.d(TAG, "AppMonitorService created and started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(monitorRunnable)
        Log.d(TAG, "AppMonitorService started monitoring")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitorRunnable)
        Log.d(TAG, "AppMonitorService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForegroundApp() {
        try {
            val currentTime = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(lastCheckedTime - 1000, currentTime)
            val event = UsageEvents.Event()

            var foregroundPackage: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foregroundPackage = event.packageName
                }
            }

            lastCheckedTime = currentTime

            foregroundPackage?.let { packageName ->
                Log.d(TAG, "Foreground app: $packageName")
                if (!isPackageAllowed(packageName)) {
                    Log.w(TAG, "Blocking: $packageName")
                    blockApp(packageName)
                } else {
                    Log.d(TAG, "Allowed: $packageName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        // Check whitelist first (includes system packages from WhitelistManager)
        val isWhitelisted = whitelistManager.isWhitelisted(packageName)
        
        // Also check MIUI launchers explicitly
        val isMIUILauncher = packageName == "com.miui.home" || 
                            packageName == "com.mi.android.globallauncher" ||
                            packageName == "com.android.launcher3" ||
                            packageName == "com.google.android.apps.nexuslauncher" ||
                            packageName == "com.android.systemui"
        
        return isWhitelisted || isMIUILauncher
    }

    private fun blockApp(packageName: String) {
        val intent = Intent(this, BlockerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra("blocked_package", packageName)
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val channelId = "app_blocker_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring apps in background"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BlockIt Active")
            .setContentText("Monitoring apps")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "AppMonitorService"
    }
}
