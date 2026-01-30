package com.kiosk.kioskmode

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockerActivity : AppCompatActivity() {

    private lateinit var blockedAppText: TextView
    private lateinit var btnGoHome: Button
    private lateinit var btnLockScreen: Button

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocker)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        blockedAppText = findViewById(R.id.blockedAppText)
        btnGoHome = findViewById(R.id.btnGoHome)
        btnLockScreen = findViewById(R.id.btnLockScreen)

        val blockedPackage = intent.getStringExtra("blocked_package") ?: "Unknown App"
        val appName = getAppName(blockedPackage)
        blockedAppText.text = "Access Blocked!\n\n\"$appName\" is not allowed.\n\nOnly Phone Dialer is permitted."

        btnGoHome.setOnClickListener {
            val homeIntent = Intent(this, MainActivity::class.java)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(homeIntent)
            finish()
        }

        btnLockScreen.setOnClickListener {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
            }
            finish()
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        finish()
    }
}
