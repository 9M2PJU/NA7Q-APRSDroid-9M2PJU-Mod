package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.content.pm.PackageManager
import _root_.android.net.Uri
import _root_.android.os.{Build, Bundle, Environment}
import _root_.android.preference.PreferenceManager
import _root_.android.provider.Settings
import _root_.android.view.View
import _root_.android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import android.{Manifest => AndroidManifest}
import scala.collection.mutable.ArrayBuffer
import androidx.core.view.WindowCompat

object FirstRunActivity {
    val REQUEST_ALL_PERMISSIONS = 9001
    val REQUEST_MANAGE_STORAGE   = 9002
}

class FirstRunActivity extends AppCompatActivity {
    import FirstRunActivity._

    lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    override def onCreate(savedInstanceState : Bundle) {
        super.onCreate(savedInstanceState)
		UIHelper.applySystemBarInsets(this)
        setContentView(R.layout.firstrun_permissions)

        findViewById(R.id.btn_grant_permissions).asInstanceOf[View]
            .setOnClickListener(new View.OnClickListener {
                override def onClick(v : View) : Unit = requestAllPermissions()
            })

        findViewById(R.id.btn_continue).asInstanceOf[View]
            .setOnClickListener(new View.OnClickListener {
                override def onClick(v : View) : Unit = finishFirstRun()
            })

        findViewById(R.id.btn_grant_storage).asInstanceOf[View]
            .setOnClickListener(new View.OnClickListener {
                override def onClick(v : View) : Unit = requestManageStorage()
            })
    }

    // Collect all runtime permissions the app needs, filtered by API level.
    def getAllPermissions() : Array[String] = {
        val perms = ArrayBuffer[String]()

        // Location — needed by everyone for beaconing
        perms += AndroidManifest.permission.ACCESS_FINE_LOCATION
        perms += AndroidManifest.permission.ACCESS_COARSE_LOCATION

        // Notifications — Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms += AndroidManifest.permission.POST_NOTIFICATIONS

        // Bluetooth — Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += AndroidManifest.permission.BLUETOOTH_CONNECT
            perms += AndroidManifest.permission.BLUETOOTH_SCAN
        }

        // Microphone — for AFSK audio decoding
        perms += AndroidManifest.permission.RECORD_AUDIO

        // Legacy storage — Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            perms += AndroidManifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
                perms += AndroidManifest.permission.WRITE_EXTERNAL_STORAGE
        }

        perms.toArray
    }

    def requestAllPermissions() : Unit = {
        val perms = getAllPermissions()
        val need_request = perms.filter(p =>
            checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)

        if (need_request.isEmpty) {
            Toast.makeText(this, R.string.firstrun_perms_already_granted,
                Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(this, need_request, REQUEST_ALL_PERMISSIONS)
        }
    }

    // MANAGE_EXTERNAL_STORAGE (Android 11+) can't be requested via the
    // standard permission dialog — the user must toggle it in system settings.
    def requestManageStorage() : Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.setData(Uri.parse("package:" + getPackageName))
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
                } catch {
                    case e : Exception =>
                        // Fallback: open general "all files access" settings
                        val intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
                }
            } else {
                Toast.makeText(this, R.string.firstrun_storage_already_granted,
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.firstrun_storage_not_needed,
                Toast.LENGTH_SHORT).show()
        }
    }

    override def onRequestPermissionsResult(
            requestCode : Int, permissions : Array[String],
            grantResults : Array[Int]) : Unit = {
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            val granted = grantResults.count(_ == PackageManager.PERMISSION_GRANTED)
            val total = grantResults.length
            if (granted == total) {
                Toast.makeText(this, R.string.firstrun_perms_granted,
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this,
                    getString(R.string.firstrun_perms_partial, granted: Integer, total: Integer),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    override def onActivityResult(requestCode : Int, resultCode : Int, data : Intent) : Unit = {
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Toast.makeText(this, R.string.firstrun_storage_granted,
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.firstrun_storage_denied,
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    def finishFirstRun() : Unit = {
        // Mark permissions as requested so this screen doesn't show again
        prefs.edit().putBoolean("permissions_requested", true).commit()

        // Route to the normal startup flow (APRSdroid will handle Hub/Log/Map)
        val intent = new Intent(this, classOf[APRSdroid])
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
