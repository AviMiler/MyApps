package il.agora.webapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * This app is a single-purpose "kiosk browser" for ONE domain only: agora.co.il
 *
 * Rules enforced:
 * 1. The WebView will ONLY ever load pages whose host is agora.co.il (or www.agora.co.il).
 * 2. ANY navigation attempt (link click, redirect, popup, form submit) to any other
 *    host is intercepted BEFORE it loads, and is handed off to the system (the user's
 *    normal default browser / the app that normally owns that link) instead of being
 *    shown inside this app. Nothing outside the allowed domain is ever rendered here.
 * 3. If this app is launched via a link that is NOT agora.co.il (which can only happen
 *    if the user manually sets it as their default link handler), it immediately
 *    forwards that link to the system for normal handling and closes itself — it never
 *    displays anything for foreign links.
 */
class MainActivity : AppCompatActivity() {

    // The only domain this app is allowed to display. Everything else is forwarded out.
    private val allowedHosts = setOf("agora.co.il", "www.agora.co.il")
    private val startUrl = "https://agora.co.il/"

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Case 1: App was opened via a link (VIEW intent), not from the launcher icon.
        val incomingUri: Uri? = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        if (incomingUri != null && !isAllowedHost(incomingUri.host)) {
            // Foreign link -> forward to system default handling, show nothing, close.
            forwardToSystem(incomingUri)
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        setupWebView()

        val loadUrl = if (incomingUri != null && isAllowedHost(incomingUri.host)) {
            incomingUri.toString()
        } else {
            startUrl
        }
        webView.loadUrl(loadUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data ?: return
        if (isAllowedHost(uri.host)) {
            webView.loadUrl(uri.toString())
        } else {
            forwardToSystem(uri)
        }
    }

    private fun isAllowedHost(host: String?): Boolean {
        if (host == null) return false
        return allowedHosts.contains(host.lowercase())
    }

    /**
     * Sends a URL out to the normal system handling (the browser/app that would have
     * opened it if this app did not exist), explicitly excluding this app itself so
     * there is no risk of a loop back into this app.
     */
    private fun forwardToSystem(uri: Uri) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri)
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val pm = packageManager
            val resolveInfos = pm.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val myPackage = packageName

            // Pick the first candidate activity that is NOT this app.
            val target = resolveInfos.firstOrNull { it.activityInfo.packageName != myPackage }

            if (target != null) {
                val explicitIntent = Intent(Intent.ACTION_VIEW, uri)
                explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                explicitIntent.setClassName(
                    target.activityInfo.packageName,
                    target.activityInfo.name
                )
                startActivity(explicitIntent)
            } else {
                // No other app can handle it (or this app is the only one registered) —
                // do nothing rather than risk opening the foreign URL inside this app.
            }
        } catch (e: Exception) {
            // Fail safe: never fall back to loading the foreign URL inside this app.
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                return if (isAllowedHost(uri.host)) {
                    // Allowed domain: let the WebView load it normally.
                    false
                } else {
                    // Any other domain: never load it here. Send it out to the system.
                    forwardToSystem(uri)
                    true
                }
            }
        }

        // Popups / new windows: never open a second WebView, and never let
        // window.open() escape the domain restriction. Foreign popups get
        // forwarded out the same way; same-domain popups just load in place.
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
