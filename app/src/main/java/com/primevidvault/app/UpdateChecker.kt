package com.primevidvault.app

import android.app.Activity
import android.content.Context
import org.json.JSONObject
import java.net.URL

/**
 * In-app updater (v3.1+).
 *
 * Checks version.json hosted on GitHub for a newer versionCode. Silent on
 * failure (offline, no releases yet, etc.) — the user is never nagged.
 * When an update is found, the app downloads the APK itself and opens the
 * system installer, so users never have to re-download manually.
 */
object UpdateChecker {
    private const val VERSION_URL =
        "https://raw.githubusercontent.com/abdulraheemsamoo79-lgtm/prime-vid-vault/main/version.json"
    private const val PREFS = "prime_vid_vault"
    private const val KEY_LAST_CHECK = "last_update_check"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String
    )

    /** Calls back on the UI thread with UpdateInfo, or null if up to date / check failed. */
    fun check(activity: Activity, currentVersionCode: Int, onResult: (UpdateInfo?) -> Unit) {
        Thread {
            var result: UpdateInfo? = null
            try {
                val json = JSONObject(URL(VERSION_URL).readText())
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    changelog = json.optString("changelog", "")
                )
                if (info.versionCode > currentVersionCode && info.apkUrl.isNotEmpty()) {
                    result = info
                }
            } catch (e: Exception) {
                // Silent: offline or no version file yet — no prompt.
            }
            val finalResult = result
            activity.runOnUiThread { onResult(finalResult) }
        }.start()
    }

    /** Auto-check at most once per day; the manual button always checks. */
    fun shouldAutoCheck(activity: Activity): Boolean {
        val last = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    fun markChecked(activity: Activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }
}
