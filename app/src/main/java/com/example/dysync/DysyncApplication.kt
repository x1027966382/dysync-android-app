package com.example.dysync

import android.app.Application
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView

class DysyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WebView.setDataDirectorySuffix("dysync")
        }
        // 全局启用 Cookie
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true)
        }
    }
}