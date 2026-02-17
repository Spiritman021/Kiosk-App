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
    private val checkInterval = 300L // Reduced from 500ms for faster detection
    private var lastCheckedTime = System.currentTimeMillis()
    private var lastBlockedPackage: String? = null
    private var lastBlockedTime = 0L
    
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var activityManager: ActivityManager

    // System packages that should always be allowed (lazy to avoid accessing packageName too early)
    private val systemPackages by lazy {
        setOf(
            "com.android.systemui",
            "com.miui.home", // MIUI launcher
            "com.mi.android.globallauncher", // MIUI global launcher
            "com.android.launcher3", // Stock Android launcher
            "com.google.android.apps.nexuslauncher", // Pixel launcher
            packageName // Our own app - safe to access here because lazy
        )
    }

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
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
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
            // Method 1: Check using UsageStats (most reliable on modern Android)
            val foregroundPackage = getForegroundPackageFromUsageStats()
            
            // Method 2: Fallback to ActivityManager for older devices or if UsageStats fails
            val fallbackPackage = if (foregroundPackage == null) {
                getForegroundPackageFromActivityManager()
            } else null

            val currentPackage = foregroundPackage ?: fallbackPackage

            currentPackage?.let { packageName ->
                Log.d(TAG, "Current foreground app: $packageName")
                
                if (!isPackageAllowed(packageName)) {
                    // Prevent blocking the same app multiple times in quick succession
                    val currentTime = System.currentTimeMillis()
                    if (packageName != lastBlockedPackage || currentTime - lastBlockedTime > 2000) {
                        Log.w(TAG, "Blocking non-whitelisted app: $packageName")
                        blockApp(packageName)
                        lastBlockedPackage = packageName
                        lastBlockedTime = currentTime
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun getForegroundPackageFromUsageStats(): String? {
        try {
            val currentTime = System.currentTimeMillis()
            // Increased time window to catch more events
            val events = usageStatsManager.queryEvents(lastCheckedTime - 2000, currentTime)
            val event = UsageEvents.Event()

            var foregroundPackage: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // Check both MOVE_TO_FOREGROUND and ACTIVITY_RESUMED
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                     event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) {
                    foregroundPackage = event.packageName
                }
            }

            lastCheckedTime = currentTime
            return foregroundPackage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground package from UsageStats", e)
            return null
        }
    }

    private fun getForegroundPackageFromActivityManager(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val tasks = activityManager.getRunningTasks(1)
                if (tasks.isNotEmpty()) {
                    tasks[0].topActivity?.packageName
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground package from ActivityManager", e)
            null
        }
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        // Always allow system packages
        if (systemPackages.contains(packageName)) {
            Log.d(TAG, "Allowing system package: $packageName")
            return true
        }
        
        // Check if package is in whitelist
        val isWhitelisted = whitelistManager.isWhitelisted(packageName)
        Log.d(TAG, "Package $packageName whitelisted: $isWhitelisted")
        return isWhitelisted
    }

    private fun blockApp(packageName: String) {
        Log.w(TAG, "Blocking app: $packageName")
        val intent = Intent(this, BlockerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
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
