package com.kiosk.kioskmode

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    var isWhitelisted: Boolean = false
)
