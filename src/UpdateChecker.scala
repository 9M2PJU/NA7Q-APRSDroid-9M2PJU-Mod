package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.app.AlertDialog
import _root_.android.content.{Context, DialogInterface, Intent}
import _root_.android.content.pm.PackageManager
import _root_.android.net.Uri
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.widget.Toast

import java.io.{BufferedReader, InputStreamReader}
import java.net.{HttpURLConnection, URL}

import org.json.JSONObject

/**
 * Checks GitHub Releases API for a newer version than the installed one.
 * If a newer version is found, shows a dialog with a "Download" button
 * that opens the browser to the release page.
 *
 * The check runs at most once per day per app instance, controlled by
 * the "last_update_check" SharedPreferences timestamp.
 */
object UpdateChecker {
    val TAG = "UpdateChecker"
    val REPO_OWNER = "9M2PJU"
    val REPO_NAME  = "NA7Q-APRSDroid-9M2PJU-Mod"
    val API_URL = s"https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    val RELEASES_URL = s"https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
    val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 hours

    def getInstalledVersionName(ctx : Context) : String = {
        try {
            ctx.getPackageManager.getPackageInfo(ctx.getPackageName, 0).versionName
        } catch {
            case e : PackageManager.NameNotFoundException => ""
        }
    }

    /**
     * Compare two version strings. Returns true if `remote` is newer than `local`.
     * Handles git describe output like "v1.4.7-9M2PJU-12-gabc123" by stripping
     * suffixes and comparing numeric components.
     */
    def isNewerVersion(local : String, remote : String) : Boolean = {
        if (local == null || local.isEmpty) return true
        if (remote == null || remote.isEmpty) return false

        // Strip leading 'v' and any suffix after '-' (e.g. "v1.4.7-12-gabc" -> "1.4.7")
        def normalize(v : String) : Array[Int] = {
            val s = v.stripPrefix("v").split("-")(0)
            try {
                s.split("\\.").map(_.toInt)
            } catch {
                case e : Exception => Array(0)
            }
        }

        val l = normalize(local)
        val r = normalize(remote)
        val len = math.max(l.length, r.length)
        for (i <- 0 until len) {
            val lv = if (i < l.length) l(i) else 0
            val rv = if (i < r.length) r(i) else 0
            if (rv > lv) return true
            if (rv < lv) return false
        }
        false
    }

    /**
     * Check if enough time has passed since the last update check.
     */
    def shouldCheck(ctx : Context) : Boolean = {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val last = prefs.getLong("last_update_check", 0)
        System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    /**
     * Record that we just performed an update check.
     */
    def markChecked(ctx : Context) : Unit = {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putLong("last_update_check", System.currentTimeMillis()).commit()
    }

    /**
     * Fetch the latest release tag from GitHub API.
     * Returns the tag name (e.g. "v1.4.7") or null on error.
     */
    def fetchLatestReleaseTag() : String = {
        val url = new URL(API_URL)
        val conn = url.openConnection().asInstanceOf[HttpURLConnection]
        try {
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "APRSdroid-9M2PJU-Mod")
            conn.setConnectTimeout(10000)
            conn.setReadTimeout(10000)
            if (conn.getResponseCode != 200) {
                Log.e(TAG, "GitHub API returned " + conn.getResponseCode)
                return null
            }
            val reader = new BufferedReader(new InputStreamReader(conn.getInputStream))
            val sb = new StringBuilder()
            var line : String = null
            while ({ line = reader.readLine; line != null }) {
                sb.append(line)
            }
            reader.close()
            val json = new JSONObject(sb.toString())
            json.optString("tag_name", null)
        } catch {
            case e : Exception =>
                Log.e(TAG, "Failed to fetch latest release: " + e.getMessage)
                null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Show the "update available" dialog.
     */
    def showUpdateDialog(act : Activity, localVersion : String, remoteTag : String) : Unit = {
        val message = act.getString(R.string.update_available_text,
            localVersion, remoteTag)
        new AlertDialog.Builder(act)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(R.string.update_download, new DialogInterface.OnClickListener {
                override def onClick(d : DialogInterface, which : Int) : Unit = {
                    UrlOpener.open(act, RELEASES_URL)
                }
            })
            .setNeutralButton(R.string.update_later, null)
            .setNegativeButton(R.string.update_skip, new DialogInterface.OnClickListener {
                override def onClick(d : DialogInterface, which : Int) : Unit = {
                    // Skip this version — record the remote tag as "seen"
                    val prefs = PreferenceManager.getDefaultSharedPreferences(act)
                    prefs.edit().putString("skipped_version", remoteTag).commit()
                }
            })
            .create().show()
    }

    /**
     * Run the update check if needed. Called from MainListActivity.onResume.
     */
    def checkForUpdates(act : Activity) : Unit = {
        if (!shouldCheck(act)) return

        val localVersion = getInstalledVersionName(act)
        if (localVersion.isEmpty) return

        markChecked(act)  // record check time regardless of result

        new MyAsyncTask[Void, String]() {
            override def doInBackground1(params : Array[String]) : String = {
                fetchLatestReleaseTag()
            }
            override def onPostExecute(remoteTag : String) : Unit = {
                if (remoteTag == null) return

                // User skipped this version previously?
                val prefs = PreferenceManager.getDefaultSharedPreferences(act)
                val skipped = prefs.getString("skipped_version", null)
                if (skipped != null && skipped == remoteTag) return

                if (isNewerVersion(localVersion, remoteTag)) {
                    Log.i(TAG, s"Update available: $localVersion -> $remoteTag")
                    showUpdateDialog(act, localVersion, remoteTag)
                } else {
                    Log.d(TAG, s"Up to date: $localVersion (latest: $remoteTag)")
                }
            }
        }.execute()
    }
}
