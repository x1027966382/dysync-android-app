package com.example.dysync

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.dysync.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var targetUrl = "http://192.168.5.9:10101"
    private var lastUrl = ""
    private var proxyConfig: ProxyUtils.ProxyConfig = ProxyUtils.ProxyConfig()

    companion object {
        const val EXTRA_TARGET_URL = "extra_target_url"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从 Intent 获取自定义 URL（支持 Scheme 唤起、设置页返回）
        intent.getStringExtra(EXTRA_TARGET_URL)?.let { targetUrl = it }
        intent.data?.let { targetUrl = it.toString() }

        loadProxyConfig()
        lastUrl = targetUrl

        setupToolbar()
        setupWebView()
        setupSwipeRefresh()
        loadUrl(targetUrl)
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回时重新读取配置
        loadProxyConfig()
        // 如果目标 URL 变了，重新加载
        val newTarget = proxyConfig.targetUrl
        if (newTarget != targetUrl) {
            targetUrl = newTarget
            loadUrl(targetUrl)
        }
    }

    private fun loadProxyConfig() {
        proxyConfig = ProxyUtils.getProxyConfig(this)
        targetUrl = proxyConfig.targetUrl
        Log.d("MainActivity", "Proxy: enabled=${proxyConfig.enabled}, host=${proxyConfig.host}:${proxyConfig.port}, target=$targetUrl")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            supportZoom = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString += " DysyncApp/1.1"
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
            setAppCachePath(cacheDir.absolutePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
        }

        webView.webViewClient = object : WebViewClient() {

            @SuppressLint("TrustAllX509TrustManager")
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString() ?: return null
                // 只代理同源请求，外部资源直连
                if (!url.startsWith(targetUrl)) {
                    return null
                }

                try {
                    val requestUrl = URL(url)
                    val inputStream = ProxyUtils.fetchWithProxy(requestUrl, proxyConfig)

                    // 读取响应头
                    val conn = ProxyUtils.openConnection(requestUrl, proxyConfig)
                    val responseCode = conn.responseCode
                    val contentType = conn.contentType ?: "text/html"
                    val encoding = conn.contentEncoding ?: "UTF-8"

                    // 同步 Cookie
                    ProxyUtils.syncCookiesFromResponse(url, conn, webView)

                    // 读取 body
                    val buffer = ByteArrayOutputStream()
                    inputStream.copyTo(buffer)
                    inputStream.close()
                    conn.disconnect()

                    return WebResourceResponse(contentType, encoding, buffer.toByteArray().inputStream())
                } catch (e: Exception) {
                    Log.w("WebViewProxy", "Proxy fetch failed for $url: ${e.message}")
                    return null // 失败则让 WebView 直连重试
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!url.startsWith(targetUrl) && (url.startsWith("http://") || url.startsWith("https://"))) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                lastUrl = url
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.swipeRefresh.isRefreshing = true
                lastUrl = url ?: ""
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                lastUrl = url ?: ""
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError
            ) {
                binding.swipeRefresh.isRefreshing = false
                if (request?.isForMainFrame == true) {
                    showErrorPage(error.description.toString())
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true && errorResponse != null) {
                    if (errorResponse.statusCode == 401 || errorResponse.statusCode == 403) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "登录已过期，请重新登录", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webView.downloadListener = DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            request.addRequestHeader("User-Agent", userAgent)
            request.setTitle(contentDisposition ?: "下载文件")
            request.setDescription("来自抖小云")
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).lastPathSegment ?: "download")
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show()
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress == 100) android.view.View.GONE else android.view.View.VISIBLE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                // supportActionBar?.title = title
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
        binding.swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun loadUrl(url: String) {
        binding.webView.loadUrl(url)
    }

    private fun showErrorPage(msg: String) {
        binding.webView.loadDataWithBaseURL(
            targetUrl,
            """
                <html><body style='font-family:sans-serif;text-align:center;padding:40px;color:#666;'>
                    <h2>😕 无法连接</h2>
                    <p>$msg</p>
                    <p>请检查：</p>
                    <ul style='text-align:left;display:inline-block;'>
                        <li>NAS 是否在线</li>
                        <li>IP/端口是否正确：$targetUrl</li>
                        <li>代理是否可用：${if (proxyConfig.enabled) "${proxyConfig.host}:${proxyConfig.port}" else "未启用"}</li>
                        <li>网络是否同一局域网或已配置穿透</li>
                    </ul>
                    <button onclick='location.reload()' style='padding:10px 20px;font-size:16px;'>重试</button>
                </body></html>
            """.trimIndent(),
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateContextMenu(menu: android.view.ContextMenu?, v: android.view.View?, menuInfo: android.view.ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val hitTest = binding.webView.getHitTestResult()
        if (hitTest.type == WebView.HitTestResult.ANCHOR_TYPE || hitTest.type == WebView.HitTestResult.IMAGE_TYPE) {
            menu?.add(0, 1, 0, "复制链接").setOnMenuItemClickListener {
                android.content.ClipboardManager.from(this).setPrimaryClip(
                    android.content.ClipData.newPlainText("url", hitTest.extra)
                )
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu!!)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                binding.webView.reload()
                true
            }
            R.id.action_home -> {
                loadUrl(targetUrl)
                true
            }
            R.id.action_open_browser -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lastUrl))
                startActivity(intent)
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}