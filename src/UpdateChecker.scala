package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.app.AlertDialog
import _root_.android.app.ProgressDialog
import _root_.android.content.{Context, DialogInterface, Intent}
import _root_.android.content.pm.PackageManager
import _root_.android.net.Uri
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.widget.Toast

import androidx.core.content.FileProvider

import java.io.{BufferedReader, File, FileOutputStream, InputStreamReader}
import java.net.{HttpURLConnection, URL}

import org.json.JSONObject

/**
 * Checks GitHub Releases API for a newer version than the installed one.
 * If a newer version is found, shows a dialog. When the user taps
 * "Download", the APK is downloaded to the app's cache directory and
 * Android's package installer is triggered automatically.
 *
 * The check runs at most once per day per app instance, controlled by
 * the "last_update_check" SharedPreferences timestamp.
 */
object UpdateChecker {
    val TAG = "UpdateChecker"
    val REPO_OWNER = "9M2PJU"
    val REPO_NAME  = "APRSdroid-9M2PJU-Mod"
    val API_URL = s"https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    val RELEASES_URL = s"https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
    // Fallback: self-hosted version.json on Cloudflare CDN (different network
    // than api.github.com, works when GitHub API is blocked/throttled)
    val FALLBACK_URL = "https://aprsdroid.hamradio.my/version.json"
    val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 hours

    // Result of fetching release info from GitHub API
    case class ReleaseInfo(tag : String, apkUrl : String, apkName : String)

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

    def shouldCheck(ctx : Context) : Boolean = {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val last = prefs.getLong("last_update_check", 0)
        System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    def markChecked(ctx : Context) : Unit = {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putLong("last_update_check", System.currentTimeMillis()).commit()
    }

    /**
     * Fetch the latest release info. Tries the GitHub API first, and
     * falls back to a self-hosted version.json on Cloudflare CDN if the
     * GitHub API is unreachable (blocked/throttled by some networks).
     */
    def fetchLatestRelease() : ReleaseInfo = {
        // Try GitHub API first
        val apiResult = fetchFromGitHubApi()
        if (apiResult != null) return apiResult

        // Fallback: self-hosted version.json
        Log.d(TAG, "GitHub API failed, trying fallback: " + FALLBACK_URL)
        fetchFromFallback()
    }

    /**
     * Fetch from the GitHub Releases API.
     */
    def fetchFromGitHubApi() : ReleaseInfo = {
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
            val tag = json.optString("tag_name", null)
            if (tag == null) return null

            // Look for an .apk asset in the release
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i <- 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    if (asset != null) {
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            val downloadUrl = asset.optString("browser_download_url", null)
                            if (downloadUrl != null) {
                                return ReleaseInfo(tag, downloadUrl, name)
                            }
                        }
                    }
                }
            }

            // No APK asset found -- fall back to the releases page URL
            ReleaseInfo(tag, RELEASES_URL, null)
        } catch {
            case e : Exception =>
                Log.e(TAG, "GitHub API failed: " + e.getMessage)
                null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Fetch from the self-hosted fallback (version.json on Cloudflare CDN).
     * Format: {"version":"v2.0.2","apkUrl":"...","apkName":"...","date":"..."}
     */
    def fetchFromFallback() : ReleaseInfo = {
        val url = new URL(FALLBACK_URL)
        val conn = url.openConnection().asInstanceOf[HttpURLConnection]
        try {
            conn.setRequestMethod("GET")
            conn.setRequestProperty("User-Agent", "APRSdroid-9M2PJU-Mod")
            conn.setConnectTimeout(10000)
            conn.setReadTimeout(10000)
            if (conn.getResponseCode != 200) {
                Log.e(TAG, "Fallback returned " + conn.getResponseCode)
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
            val tag = json.optString("version", null)
            if (tag == null) return null
            val apkUrl = json.optString("apkUrl", RELEASES_URL)
            val apkName = json.optString("apkName", null)
            Log.d(TAG, "Fallback succeeded: " + tag)
            ReleaseInfo(tag, apkUrl, apkName)
        } catch {
            case e : Exception =>
                Log.e(TAG, "Fallback failed: " + e.getMessage)
                null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Show the "update available" dialog.
     */
    def showUpdateDialog(act : Activity, localVersion : String, info : ReleaseInfo) : Unit = {
        val message = act.getString(R.string.update_available_text,
            localVersion, info.tag)
        new AlertDialog.Builder(act)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(R.string.update_download, new DialogInterface.OnClickListener {
                override def onClick(d : DialogInterface, which : Int) : Unit = {
                    if (info.apkName != null) {
                        // We have a direct APK download URL -- download and install
                        downloadAndInstall(act, info)
                    } else {
                        // No APK asset -- fall back to opening browser
                        UrlOpener.open(act, RELEASES_URL)
                    }
                }
            })
            .setNeutralButton(R.string.update_later, null)
            .setNegativeButton(R.string.update_skip, new DialogInterface.OnClickListener {
                override def onClick(d : DialogInterface, which : Int) : Unit = {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(act)
                    prefs.edit().putString("skipped_version", info.tag).commit()
                }
            })
            .create().show()
    }

    /**
     * Download the APK to the cache directory and trigger the installer.
     * Shows a progress dialog during download.
     */
    def downloadAndInstall(act : Activity, info : ReleaseInfo) : Unit = {
        val progress = new ProgressDialog(act)
        progress.setTitle(act.getString(R.string.update_downloading_title))
        progress.setMessage(act.getString(R.string.update_downloading_message, info.tag))
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progress.setCancelable(true)
        progress.setMax(100)
        progress.setProgress(0)
        progress.show()

        new MyAsyncTask[Void, File]() {
            override def doInBackground1(params : Array[String]) : File = {
                try {
                    val url = new URL(info.apkUrl)
                    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
                    conn.setRequestMethod("GET")
                    conn.setRequestProperty("User-Agent", "APRSdroid-9M2PJU-Mod")
                    conn.setConnectTimeout(30000)
                    conn.setReadTimeout(60000)
                    conn.connect()

                    if (conn.getResponseCode != 200) {
                        Log.e(TAG, "Download failed: HTTP " + conn.getResponseCode)
                        return null
                    }

                    val totalSize = conn.getContentLength
                    val updatesDir = new File(act.getCacheDir, "updates")
                    updatesDir.mkdirs()
                    val apkFile = new File(updatesDir, info.apkName)

                    val input = conn.getInputStream
                    val output = new FileOutputStream(apkFile)
                    val buffer = new Array[Byte](8192)
                    var bytesRead = 0
                    var totalRead = 0
                    var lastPct = -1

                    while ({ bytesRead = input.read(buffer); bytesRead != -1 }) {
                        if (isCancelled) {
                            input.close()
                            output.close()
                            conn.disconnect()
                            apkFile.delete()
                            return null
                        }
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalSize > 0) {
                            val pct = (totalRead * 100 / totalSize).toInt
                            if (pct != lastPct) {
                                lastPct = pct
                                act.runOnUiThread(new Runnable {
                                    override def run() : Unit = {
                                        if (progress.isShowing)
                                            progress.setProgress(pct)
                                    }
                                })
                            }
                        }
                    }
                    output.close()
                    input.close()
                    conn.disconnect()
                    Log.i(TAG, "Downloaded APK: " + apkFile.getAbsolutePath +
                        " (" + apkFile.length + " bytes)")
                    apkFile
                } catch {
                    case e : Exception =>
                        Log.e(TAG, "Download failed: " + e.getMessage)
                        null
                }
            }

            override def onPostExecute(apkFile : File) : Unit = {
                progress.dismiss()
                if (apkFile == null || !apkFile.exists()) {
                    Toast.makeText(act, R.string.update_download_failed,
                        Toast.LENGTH_LONG).show()
                    // Fall back to opening the browser
                    UrlOpener.open(act, RELEASES_URL)
                    return
                }
                promptInstall(act, apkFile)
            }

            override def onCancelled() : Unit = {
                progress.dismiss()
                Toast.makeText(act, R.string.update_download_cancelled,
                    Toast.LENGTH_SHORT).show()
            }
        }.execute()
    }

    /**
     * Trigger Android's package installer with the downloaded APK.
     */
    def promptInstall(act : Activity, apkFile : File) : Unit = {
        val uri = FileProvider.getUriForFile(act, "org.aprsdroid.fileprovider", apkFile)
        val intent = new Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            act.startActivity(intent)
        } catch {
            case e : Exception =>
                Log.e(TAG, "Failed to launch installer: " + e.getMessage)
                Toast.makeText(act, R.string.update_install_failed,
                    Toast.LENGTH_LONG).show()
                // Fall back to opening the browser
                UrlOpener.open(act, RELEASES_URL)
        }
    }

    /**
     * Run the update check if needed. Called from MainListActivity.onResume.
     */
    def checkForUpdates(act : Activity) : Unit = {
        val should = shouldCheck(act)
        Log.i(TAG, s"checkForUpdates: shouldCheck=$should, installed=${getInstalledVersionName(act)}")
        if (!should) return
        doCheck(act, force = false)
    }

    /**
     * Force an update check, bypassing the 24h cooldown.
     * Called from the "Check for updates" menu item.
     */
    def forceCheckForUpdates(act : Activity) : Unit = {
        Log.i(TAG, "forceCheckForUpdates called")
        doCheck(act, force = true)
    }

    private def doCheck(act : Activity, force : Boolean) : Unit = {
        val localVersion = getInstalledVersionName(act)
        Log.i(TAG, s"doCheck: force=$force, localVersion=$localVersion")
        if (localVersion.isEmpty) {
            Log.w(TAG, "doCheck: local version is empty, aborting")
            return
        }

        markChecked(act)  // record check time regardless of result

        new MyAsyncTask[Void, ReleaseInfo]() {
            override def doInBackground1(params : Array[String]) : ReleaseInfo = {
                Log.i(TAG, "doInBackground: fetching latest release...")
                val info = fetchLatestRelease()
                Log.i(TAG, "doInBackground: result=" + (if (info != null) info.tag else "null"))
                info
            }
            override def onPostExecute(info : ReleaseInfo) : Unit = {
                if (info == null) {
                    Log.w(TAG, "onPostExecute: info is null, fetch failed")
                    if (force)
                        Toast.makeText(act, R.string.update_check_failed, Toast.LENGTH_LONG).show()
                    return
                }

                // User skipped this version previously?
                val prefs = PreferenceManager.getDefaultSharedPreferences(act)
                val skipped = prefs.getString("skipped_version", null)
                if (skipped != null && skipped == info.tag) {
                    Log.i(TAG, s"onPostExecute: version ${info.tag} was skipped by user")
                    return
                }

                val newer = isNewerVersion(localVersion, info.tag)
                Log.i(TAG, s"onPostExecute: local=$localVersion, remote=${info.tag}, isNewer=$newer")
                if (newer) {
                    Log.i(TAG, s"Update available: $localVersion -> ${info.tag}")
                    showUpdateDialog(act, localVersion, info)
                } else {
                    Log.d(TAG, s"Up to date: $localVersion (latest: ${info.tag})")
                    if (force)
                        Toast.makeText(act, act.getString(R.string.up_to_date, info.tag), Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }
}
