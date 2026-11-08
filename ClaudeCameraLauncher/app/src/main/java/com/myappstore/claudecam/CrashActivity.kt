package com.myappstore.claudecam

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Plain, dependency-free error screen so it can render even if the rest of the app is broken. */
class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "Unknown error"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(32, 64, 32, 32)
        }

        val title = TextView(this).apply {
            text = "האפליקציה נתקלה בשגיאה"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
        }

        val subtitle = TextView(this).apply {
            text = "אפשר להעתיק/לצלם את הטקסט הבא ולשלוח אותו כדי לתקן:"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 12, 0, 24)
        }

        val traceView = TextView(this).apply {
            text = trace
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(traceView)
        setContentView(root)
    }
}
