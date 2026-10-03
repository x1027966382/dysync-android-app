package com.example.dysync

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ProxyUtils {

    private const val TAG = "ProxyUtils"
    private const val PREFS_PROXY_ENABLED = "proxy_enabled"
    private const val PREFS_PROXY_HOST = "proxy_host"
    private const val PREFS_PROXY_PORT = "proxy_port"
    private const val PREFS_TARGET_URL = "target_url"

    private val TRUST_ALL_MANAGER = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }

    private val TRUST_ALL_SSL_CONTEXT: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(TRUST_ALL_MANAGER), java.security.SecureRandom())
        }
    }

    /** 读取代理配置 */
    data class ProxyConfig(
        val enabled: Boolean = false,
        val host: String = "",
        val port: Int = 7890,
        val targetUrl: String = "http://192.168.5.9:10101"
    )

    fun getProxyConfig(context: Context): ProxyConfig {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return ProxyConfig(
            enabled = prefs.getBoolean(PREFS_PROXY_ENABLED, false),
            host = prefs.getString(PREFS_PROXY_HOST, "192.168.5.9") ?: "192.168.5.9",
            port = prefs.getString(PREFS_PROXY_PORT, "7890")?.toIntOrNull() ?: 7890,
            targetUrl = prefs.getString(PREFS_TARGET_URL, "http://192.168.5.9:10101") ?: "http://192.168.5.9:10101"
        )
    }

    fun saveProxyConfig(context: Context, config: ProxyConfig) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREFS_PROXY_ENABLED, config.enabled)
            .putString(PREFS_PROXY_HOST, config.host)
            .putString(PREFS_PROXY_PORT, config.port.toString())
            .putString(PREFS_TARGET_URL, config.targetUrl)
            .apply()
    }

    /** 生成 Proxy 对象 */
    fun createProxy(config: ProxyConfig): Proxy? {
        if (!config.enabled || config.host.isBlank()) return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port))
    }

    /** 打开连接（自动应用代理、信任所有证书、设置超时） */
    @SuppressLint("TrustAllX509TrustManager")
    fun openConnection(url: URL, config: ProxyConfig): HttpURLConnection {
        val proxy = createProxy(config)
        val conn = if (proxy != null) url.openConnection(proxy) else url.openConnection()

        conn.apply {
            connectTimeout = 15000
            readTimeout = 30000
            useCaches = false
            doInput = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) DysyncApp/1.1")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }

        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = TRUST_ALL_SSL_CONTEXT.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }

        return conn as HttpURLConnection
    }

    /** 同步 Cookie 到 WebView */
    fun syncCookiesFromResponse(url: String, conn: HttpURLConnection, webView: android.webkit.WebView) {
        val cookies = conn.headerFields["Set-Cookie"]
        if (cookies != null) {
            val cookieManager = android.webkit.CookieManager.getInstance()
            for (cookie in cookies) {
                cookieManager.setCookie(url, cookie)
            }
            cookieManager.flush()
        }
    }

    /** 用代理下载输入流（供 WebView shouldInterceptRequest 使用） */
    @Throws(IOException::class)
    fun fetchWithProxy(url: URL, config: ProxyConfig): InputStream {
        val conn = openConnection(url, config)
        val responseCode = conn.responseCode
        if (responseCode >= 400) {
            throw IOException("HTTP $responseCode")
        }
        return conn.inputStream
    }
}