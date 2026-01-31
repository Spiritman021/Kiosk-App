package com.kiosk.kioskmode

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    // UI Elements - Status
    private lateinit var statusText: TextView
    
    // UI Elements - Permissions
    private lateinit var btnUsageStats: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnAdmin: Button
    private lateinit var btnNotification: Button
    
    // UI Elements - Service
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    
    // UI Elements - App Management
    private lateinit var searchBox: EditText
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnBack: ImageButton

    // Managers
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var adapter: AppListAdapter
    private val allApps = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        whitelistManager = WhitelistManager(this)

        initViews()
        setupRecyclerView()
        loadInstalledApps()
        setupListeners()
        updateStatus()
    }

    private fun initViews() {
        // Status
        statusText = findViewById(R.id.statusText)
        
        // Permissions
        btnUsageStats = findViewById(R.id.btnUsageStats)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAdmin = findViewById(R.id.btnAdmin)
        btnNotification = findViewById(R.id.btnNotification)
        
        // Service
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        
        // App Management
        searchBox = findViewById(R.id.searchBox)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(allApps) { app, isWhitelisted ->
            if (isWhitelisted) {
                whitelistManager.addPackage(app.packageName)
            } else {
                whitelistManager.removePackage(app.packageName)
            }
        }

        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        appsRecyclerView.adapter = adapter
    }

    private fun loadInstalledApps() {
        val packageManager = packageManager

        android.util.Log.d("SettingsActivity", "=== Starting to load installed apps ===")

        // Get all apps that have a launcher activity
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = packageManager.queryIntentActivities(mainIntent, 0)

        android.util.Log.d("SettingsActivity", "Found ${launchableApps.size} launchable apps via ACTION_MAIN")

        val whitelistedPackages = whitelistManager.getUserWhitelistedPackages()
        android.util.Log.d("SettingsActivity", "Whitelisted packages: $whitelistedPackages")

        allApps.clear()

        // Use a Set to avoid duplicates
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in launchableApps) {
            val packageName = resolveInfo.activityInfo.packageName

            // Skip our own app
            if (packageName == this.packageName) {
                android.util.Log.d("SettingsActivity", "Skipping our own app: $packageName")
                continue
            }

            // Skip duplicates
            if (seenPackages.contains(packageName)) {
                continue
            }
            seenPackages.add(packageName)

            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = appInfo.loadLabel(packageManager).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                android.util.Log.d("SettingsActivity", "Adding app: $appName ($packageName) - System: $isSystemApp")

                val app = AppInfo(
                    packageName = packageName,
                    appName = appName,
                    icon = appInfo.loadIcon(packageManager),
                    isWhitelisted = whitelistedPackages.contains(packageName)
                )
                allApps.add(app)
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Error loading app $packageName: ${e.message}")
            }
        }

        android.util.Log.d("SettingsActivity", "Total apps loaded: ${allApps.size}")

        // Sort by app name
        allApps.sortBy { it.appName.lowercase() }

        adapter.updateApps(allApps)
        android.util.Log.d("SettingsActivity", "=== Finished loading apps ===")
    }

    private fun setupListeners() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Permission buttons
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

        // Service buttons
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

        // App management buttons
        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnDeselectAll.setOnClickListener {
            adapter.deselectAll()
        }

        // Search functionality
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })
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
        val whitelistCount = whitelistManager.getUserWhitelistedPackages().size

        val status = buildString {
            append("Status:\n\n")
            append("Usage Stats: ${if (hasUsageStats) "✓ Granted" else "✗ Not Granted"}\n")
            append("Overlay: ${if (hasOverlay) "✓ Granted" else "✗ Not Granted"}\n")
            append("Device Admin: ${if (hasAdmin) "✓ Active" else "✗ Not Active"}\n")
            append("Notification: ${if (hasNotification) "✓ Granted" else "✗ Not Granted"}\n\n")
            append("Service: ${if (serviceRunning) "✓ Running" else "✗ Stopped"}\n")
            append("Whitelisted Apps: $whitelistCount\n\n")
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
