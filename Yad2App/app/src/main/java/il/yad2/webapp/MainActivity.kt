package il.yad2.webapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.security.SecureRandom

/**
 * דפדפן-קיוסק לאתר יד 2 בלבד, עם תוסף הדיסלייק מוזרק לכל עמוד.
 *
 * כללי הניווט (זהים במהותם לאפליקציית אגורה):
 * 1. ה-WebView טוען *רק* עמודים שה-host שלהם yad2.co.il / www / m.
 * 2. כל ניווט של המסגרת הראשית ליעד אחר (קישור, הפניה, target=_blank,
 *    window.open) נחסם לפני הטעינה ומועבר החוצה למערכת — לדפדפן או
 *    לאפליקציה שהיו אמורים לטפל בו (כולל tel:, mailto:, whatsapp:, waze).
 * 3. אם האפליקציה נפתחת דרך קישור שאינו יד 2 — היא לא מציגה דבר, מעבירה
 *    את הקישור החוצה ונסגרת.
 *
 * חריג מכוון אחד לעומת אגורה: תוכן מוטמע (iframes) של צד שלישי כן נטען.
 * ביד 2 זה הכרחי — Google Maps, אימות אנטי-בוט וכניסה עם גוגל/פייסבוק כולם
 * iframes. החסימה חלה על ניווט של המסגרת הראשית, שהוא מה שהמשתמש רואה.
 */
class MainActivity : AppCompatActivity() {

    private val allowedHosts = setOf("yad2.co.il", "www.yad2.co.il", "m.yad2.co.il")
    private val startUrl = "https://www.yad2.co.il/"

    private lateinit var webView: WebView
    private lateinit var store: DislikeStore

    /** סוד חד-פעמי לכל הרצה, שמגן על הגשר מפני iframes של צד שלישי. */
    private val nonce: String by lazy {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        bytes.joinToString("") { "%02x".format(it) }
    }

    /** קוד התוסף, נטען פעם אחת מ-assets. */
    private val pluginSource: String by lazy {
        try {
            assets.open(PLUGIN_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // נפתחנו דרך קישור שאינו יד 2 — להעביר החוצה ולא להציג כלום.
        val incomingUri: Uri? = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        if (incomingUri != null && !isAllowedHost(incomingUri.host)) {
            forwardToSystem(incomingUri)
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        store = DislikeStore(this)
        webView = findViewById(R.id.webView)
        setupWebView()

        val restored = savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        if (!restored) {
            webView.loadUrl(if (incomingUri != null) incomingUri.toString() else startUrl)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data ?: return
        if (isAllowedHost(uri.host)) webView.loadUrl(uri.toString()) else forwardToSystem(uri)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    private fun isAllowedHost(host: String?): Boolean =
        host != null && allowedHosts.contains(host.lowercase())

    /**
     * מוציא כתובת אל הטיפול הרגיל של המערכת, תוך החרגה מפורשת של האפליקציה
     * הזו עצמה כדי שלא ייווצר לולאה חזרה פנימה.
     */
    private fun forwardToSystem(uri: Uri) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val candidates = packageManager
                .queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val target = candidates.firstOrNull { it.activityInfo.packageName != packageName }
            if (target != null) {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setClassName(target.activityInfo.packageName, target.activityInfo.name)
                    }
                )
            }
            // אם אין אפליקציה אחרת שיודעת לפתוח את זה — לא עושים כלום,
            // ובשום מקרה לא טוענים את הכתובת הזרה כאן.
        } catch (e: Exception) {
            // כשל בטוח: אף פעם לא נופלים אחורה לטעינה בתוך האפליקציה.
        }
    }

    /** ניווט שהגיע מחלון חדש / window.open. */
    private fun handleForeignNavigation(uri: Uri) {
        if (isAllowedHost(uri.host)) webView.loadUrl(uri.toString()) else forwardToSystem(uri)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)          // target=_blank מטופל ב-onCreateWindow
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // ה-UA של WebView מסתיים ב-"; wv" וחלק מהשירותים (בהם כניסה עם
            // גוגל) חוסמים אותו. הסרת הסימון נותנת UA של כרום נייד רגיל.
            userAgentString = userAgentString.replace("; wv", "")
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)   // נדרש לכניסה לחשבון יד 2
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.addJavascriptInterface(
            Yad2Bridge(this, store, nonce),
            Yad2Bridge.NAME
        )

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // תוכן מוטמע (מפות, אימות, כניסה) נטען כרגיל — החסימה היא על
                // מה שמחליף את העמוד שהמשתמש רואה.
                if (!request.isForMainFrame) return false
                val uri = request.url
                if (isAllowedHost(uri.host)) return false
                forwardToSystem(uri)
                return true
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                injectPlugin()   // מוקדם ככל האפשר, כדי שהסימונים יופיעו מיד
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                injectPlugin()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                injectPlugin()   // ניווט פנימי של ה-SPA
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            /**
             * יד 2 פותח מודעות ב-target=_blank. במקום לפתוח חלון שני, מוציאים
             * את הכתובת ומטפלים בה לפי אותם כללים כמו כל ניווט אחר.
             */
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val probe = WebView(view.context)
                probe.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        handleForeignNavigation(request.url)
                        v.destroy()
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = probe
                resultMsg.sendToTarget()
                return true
            }

            /** לא מבקשים הרשאת מיקום — דוחים מיד כדי שהאתר לא ייתקע בהמתנה. */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, false, false)
            }
        }
    }

    /**
     * מזריק את התוסף למסגרת הראשית. ההזרקה עוטפת את הגשר של אנדרואיד
     * ב-wrapper שמוסיף את ה-nonce, ורק אז מריצה את קוד התוסף — שמצדו
     * מוגן מפני הזרקה כפולה.
     */
    private fun injectPlugin() {
        if (pluginSource.isEmpty()) return
        val bootstrap = """
            (function () {
              var N = ${JSONObject.quote(nonce)};
              var B = window.${Yad2Bridge.NAME};
              if (B && !window.Yad2Store) {
                window.Yad2Store = {
                  getAll:      function ()      { return B.getAll(N); },
                  getSettings: function ()      { return B.getSettings(N); },
                  put:         function (k, v)  { B.put(N, k, v); },
                  remove:      function (k)     { B.remove(N, k); },
                  clear:       function ()      { B.clear(N); },
                  putSettings: function (s)     { B.putSettings(N, s); },
                  toast:       function (m)     { B.toast(N, m); }
                };
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(bootstrap + "\n" + pluginSource, null)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!::webView.isInitialized) {
            super.onBackPressed()
            return
        }
        // חלונית פתוחה של התוסף נסגרת ראשונה, ורק אז חוזרים אחורה באתר.
        webView.evaluateJavascript(
            "(function(){try{return window.__YAD2_DISLIKE__ ? " +
                "String(window.__YAD2_DISLIKE__.handleBack()) : 'false';}catch(e){return 'false';}})()"
        ) { result ->
            if (result != null && result.contains("true")) return@evaluateJavascript
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface(Yad2Bridge.NAME)
        }
        super.onDestroy()
    }

    companion object {
        private const val PLUGIN_ASSET = "yad2_dislike.js"
    }
}
