package com.kiosk.kioskmode

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnUsageStats: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnAdmin: Button
    private lateinit var btnNotification: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        initViews()
        updateStatus()
        setupListeners()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        btnUsageStats = findViewById(R.id.btnUsageStats)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAdmin = findViewById(R.id.btnAdmin)
        btnNotification = findViewById(R.id.btnNotification)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
    }

    private fun setupListeners() {
        btnUsageStats.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable device admin to lock screen when blocking apps"
            )
            startActivity(intent)
        }

        btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        btnStartService.setOnClickListener {
            if (checkAllPermissions()) {
                startMonitoringService()
            } else {
                statusText.text = "Please grant all permissions first!"
            }
        }

        btnStopService.setOnClickListener {
            stopMonitoringService()
        }
    }

    private fun checkAllPermissions(): Boolean {
        val hasUsageStats = hasUsageStatsPermission()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        val hasAdmin = devicePolicyManager.isAdminActive(adminComponent)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasUsageStats && hasOverlay && hasAdmin && hasNotification
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updateStatus() {
        val hasUsageStats = hasUsageStatsPermission()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        val hasAdmin = devicePolicyManager.isAdminActive(adminComponent)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val serviceRunning = isServiceRunning()

        val status = buildString {
            append("Status:\n\n")
            append("Usage Stats: ${if (hasUsageStats) "✓ Granted" else "✗ Not Granted"}\n")
            append("Overlay: ${if (hasOverlay) "✓ Granted" else "✗ Not Granted"}\n")
            append("Device Admin: ${if (hasAdmin) "✓ Active" else "✗ Not Active"}\n")
            append("Notification: ${if (hasNotification) "✓ Granted" else "✗ Not Granted"}\n\n")
            append("Service: ${if (serviceRunning) "✓ Running" else "✗ Stopped"}\n\n")
            if (hasUsageStats && hasOverlay && hasAdmin && hasNotification) {
                append("All permissions granted! Start the service.")
            } else {
                append("Grant all permissions to enable blocking.")
            }
        }

        statusText.text = status

        btnUsageStats.isEnabled = !hasUsageStats
        btnOverlay.isEnabled = !hasOverlay
        btnAdmin.isEnabled = !hasAdmin
        btnNotification.isEnabled = !hasNotification
        btnStartService.isEnabled = !serviceRunning && checkAllPermissions()
        btnStopService.isEnabled = serviceRunning
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (AppMonitorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startMonitoringService() {
        val intent = Intent(this, AppMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, AppMonitorService::class.java)
        stopService(intent)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
