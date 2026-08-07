package com.myappstore.claudecam

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * This app has no UI of its own. It is a launcher shim: tapping its icon
 * immediately opens the Claude app (com.anthropic.claude) and then closes
 * itself, so it never appears in the recent-apps list as a separate screen.
 *
 * There is no publicly documented deep link / intent that jumps straight
 * to Claude's camera screen from outside the app, so this shim opens
 * Claude's normal home screen, from which the camera button (already
 * built into Claude) is one tap away. If Anthropic ever documents a
 * direct camera intent/URI, only the launch call below needs to change.
 */
class MainActivity : Activity() {

    private val claudePackage = "com.anthropic.claude"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchIntent = packageManager.getLaunchIntentForPackage(claudePackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            openInPlayStore()
        }
        finish()
    }

    private fun openInPlayStore() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$claudePackage"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$claudePackage")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
