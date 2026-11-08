package il.yad2.webapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONObject

/**
 * אחסון הדיסלייקים בצד האנדרואיד.
 *
 * למה לא להסתפק ב-localStorage של ה-WebView: האתר, המשתמש או המערכת יכולים
 * לנקות את אחסון הדפדפן (ניקוי נתוני אפליקציה חלקי, מחיקת עוגיות, החלפת
 * דומיין מ-www ל-m). SharedPreferences שייך לאפליקציה, נכלל בגיבוי הענן
 * (ראו res/xml/backup_rules.xml), ולכן הסימונים באמת "נשארים תמיד".
 *
 * מבנה: קובץ אחד שבו כל מודעה היא רשומה נפרדת — key = מזהה המודעה ביד 2,
 * value = ה-JSON של הרשומה. כתיבה של מודעה אחת לא נוגעת באחרות.
 */
class DislikeStore(context: Context) {

    private val app = context.applicationContext
    private val data = app.getSharedPreferences(PREFS_DATA, Context.MODE_PRIVATE)
    private val settings = app.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    fun allAsJson(): String {
        val sb = StringBuilder(1024)
        sb.append('{')
        var first = true
        for ((key, value) in data.all) {
            val record = value as? String ?: continue
            if (!first) sb.append(',')
            first = false
            sb.append(JSONObject.quote(key)).append(':').append(record)
        }
        sb.append('}')
        return sb.toString()
    }

    fun put(key: String, recordJson: String) {
        // מאמתים שזה JSON תקין לפני ששומרים, כדי ש-allAsJson לא יחזיר אי-פעם
        // מחרוזת שבורה שתפיל את הפענוח בצד ה-JS.
        try {
            JSONObject(recordJson)
        } catch (e: Exception) {
            return
        }
        data.edit().putString(key, recordJson).apply()
    }

    fun remove(key: String) = data.edit().remove(key).apply()

    fun clear() = data.edit().clear().apply()

    fun count(): Int = data.all.size

    fun settingsJson(): String = settings.getString(KEY_SETTINGS, "{}") ?: "{}"

    fun putSettings(json: String) {
        try {
            JSONObject(json)
        } catch (e: Exception) {
            return
        }
        settings.edit().putString(KEY_SETTINGS, json).apply()
    }

    companion object {
        const val PREFS_DATA = "yad2_dislikes"
        const val PREFS_SETTINGS = "yad2_dislike_prefs"
        private const val KEY_SETTINGS = "settings"
    }
}

/**
 * הגשר שנחשף ל-JavaScript.
 *
 * אבטחה: אנדרואיד מזריק interface שנוסף ב-addJavascriptInterface לכל ה-frames
 * בעמוד — כולל iframes של צד שלישי (פרסומות, מפות). בלי הגנה, iframe כזה היה
 * יכול לקרוא את ההערות הפרטיות של המשתמש. לכן כל קריאה חייבת להציג nonce
 * אקראי שנוצר בהפעלת האפליקציה ומוזרק *רק* ל-frame הראשי (evaluateJavascript
 * רץ רק שם) ונשמר שם ב-closure. iframe ממקור אחר לא יכול לקרוא אותו, ולכן כל
 * קריאה שלו נדחית בשקט.
 */
class Yad2Bridge(
    context: Context,
    private val store: DislikeStore,
    private val nonce: String
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private fun ok(n: String?): Boolean = n != null && n == nonce

    @JavascriptInterface
    fun getAll(n: String?): String = if (ok(n)) store.allAsJson() else "{}"

    @JavascriptInterface
    fun getSettings(n: String?): String = if (ok(n)) store.settingsJson() else "{}"

    @JavascriptInterface
    fun put(n: String?, key: String?, recordJson: String?) {
        if (!ok(n) || key.isNullOrEmpty() || recordJson == null) return
        store.put(key, recordJson)
    }

    @JavascriptInterface
    fun remove(n: String?, key: String?) {
        if (!ok(n) || key.isNullOrEmpty()) return
        store.remove(key)
    }

    @JavascriptInterface
    fun clear(n: String?) {
        if (ok(n)) store.clear()
    }

    @JavascriptInterface
    fun putSettings(n: String?, json: String?) {
        if (!ok(n) || json == null) return
        store.putSettings(json)
    }

    @JavascriptInterface
    fun toast(n: String?, message: String?) {
        if (!ok(n) || message.isNullOrEmpty()) return
        val text = message.take(120)
        main.post { Toast.makeText(app, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        /** השם שבו ה-interface נחשף ל-JS. ה-wrapper שעוטף אותו נקרא Yad2Store. */
        const val NAME = "Yad2Bridge"
    }
}
