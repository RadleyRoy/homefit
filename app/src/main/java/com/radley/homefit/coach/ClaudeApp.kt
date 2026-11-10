package com.radley.homefit.coach

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Hands prompts to the Claude app, so the coach runs on the user's own Claude plan (Pro, Max…)
 * rather than on API credit. Claude's subscription can't be called from another app, which is
 * the point of going through Claude's own app: the user sees and sends the message themselves,
 * and the reply comes back by Share or the clipboard ([CoachImport] reads it).
 */
object ClaudeApp {

    const val PACKAGE = "com.anthropic.claude"
    private const val WEB = "https://claude.ai/new"

    enum class Opened { CLAUDE_APP, SHARE_SHEET, NOTHING }

    /** Needs the `<queries>` entry in the manifest to see the app on Android 11+. */
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Opens a new Claude chat with [prompt] filled in. The prompt is also put on the clipboard,
     * so if the app ever opens without the text the user can paste it.
     */
    fun send(context: Context, prompt: String): Opened {
        copy(context, prompt)
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt)
        return try {
            context.startActivity(Intent(share).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Opened.CLAUDE_APP
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent.createChooser(share, "Send to Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Opened.SHARE_SHEET
            } catch (_: ActivityNotFoundException) {
                Opened.NOTHING
            }
        }
    }

    /** Claude on the web, for when the app isn't installed; the prompt is already copied. */
    fun openWeb(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEB)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No browser at all; nothing useful to do.
        }
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("HomeFit prompt", text))
    }

    fun clipboardText(context: Context): String? =
        context.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
}
