package com.kiosk.kioskmode

import android.content.Context
import android.content.SharedPreferences

class WhitelistManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "whitelist_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_WHITELISTED_APPS = "whitelisted_apps"
        
        // System packages that should always be allowed
        private val SYSTEM_PACKAGES = setOf(
            "com.kiosk.kioskmode", // Our own app
            "com.android.systemui" // System UI
        )
    }
    
    /**
     * Get all whitelisted package names including system packages
     */
    fun getWhitelistedPackages(): Set<String> {
        val userPackages = prefs.getStringSet(KEY_WHITELISTED_APPS, emptySet()) ?: emptySet()
        return SYSTEM_PACKAGES + userPackages
    }
    
    /**
     * Get only user-selected whitelisted packages (excluding system packages)
     */
    fun getUserWhitelistedPackages(): Set<String> {
        return prefs.getStringSet(KEY_WHITELISTED_APPS, emptySet()) ?: emptySet()
    }
    
    /**
     * Add a package to the whitelist
     */
    fun addPackage(packageName: String) {
        val current = getUserWhitelistedPackages().toMutableSet()
        current.add(packageName)
        saveWhitelist(current)
    }
    
    /**
     * Remove a package from the whitelist
     */
    fun removePackage(packageName: String) {
        val current = getUserWhitelistedPackages().toMutableSet()
        current.remove(packageName)
        saveWhitelist(current)
    }
    
    /**
     * Check if a package is whitelisted
     */
    fun isWhitelisted(packageName: String): Boolean {
        return getWhitelistedPackages().contains(packageName)
    }
    
    /**
     * Set the entire whitelist (replaces existing user whitelist)
     */
    fun setWhitelist(packages: Set<String>) {
        saveWhitelist(packages)
    }
    
    /**
     * Clear all user-selected whitelisted packages
     */
    fun clearWhitelist() {
        saveWhitelist(emptySet())
    }
    
    private fun saveWhitelist(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_WHITELISTED_APPS, packages).apply()
    }
}
