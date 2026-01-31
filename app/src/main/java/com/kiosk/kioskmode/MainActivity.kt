package com.kiosk.kioskmode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var btnSettings: ImageButton
    private lateinit var launcherSection: LinearLayout
    private lateinit var launcherAppsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var launcherAdapter: LauncherAppAdapter
    private lateinit var whitelistManager: WhitelistManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        whitelistManager = WhitelistManager(this)

        initViews()
        setupLauncherGrid()
        setupListeners()
        loadWhitelistedApps()
    }

    private fun initViews() {
        btnSettings = findViewById(R.id.btnSettings)
        launcherSection = findViewById(R.id.launcherSection)
        launcherAppsRecyclerView = findViewById(R.id.launcherAppsRecyclerView)
        emptyState = findViewById(R.id.emptyState)
    }

    private fun setupListeners() {
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupLauncherGrid() {
        launcherAdapter = LauncherAppAdapter(emptyList()) { app ->
            launchApp(app)
        }

        launcherAppsRecyclerView.layoutManager = GridLayoutManager(this, 4)
        launcherAppsRecyclerView.adapter = launcherAdapter
    }

    private fun loadWhitelistedApps() {
        val packageManager = packageManager
        val whitelistedPackages = whitelistManager.getUserWhitelistedPackages()

        if (whitelistedPackages.isEmpty()) {
            launcherAppsRecyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            return
        }

        val whitelistedApps = mutableListOf<AppInfo>()

        for (packageName in whitelistedPackages) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val app = AppInfo(
                    packageName = packageName,
                    appName = appInfo.loadLabel(packageManager).toString(),
                    icon = appInfo.loadIcon(packageManager),
                    isWhitelisted = true
                )
                whitelistedApps.add(app)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading whitelisted app $packageName: ${e.message}")
            }
        }

        whitelistedApps.sortBy { it.appName.lowercase() }

        if (whitelistedApps.isEmpty()) {
            launcherAppsRecyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            launcherAppsRecyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            launcherAdapter.updateApps(whitelistedApps)
        }
    }

    private fun launchApp(app: AppInfo) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Cannot launch ${app.appName}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching ${app.appName}", Toast.LENGTH_SHORT).show()
            android.util.Log.e("MainActivity", "Error launching app: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        loadWhitelistedApps()
    }
}
