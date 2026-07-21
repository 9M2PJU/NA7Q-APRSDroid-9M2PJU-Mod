package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.os.{Bundle, Handler}
import _root_.android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class APRSdroid extends AppCompatActivity {
	val SPLASH_DELAY_MS = 2000

	def replaceAct(act : Class[_]) {
		val i = new Intent(this, act)
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
		startActivity(i)
		finish()
	}

	override def onCreate(savedInstanceState : Bundle) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(getWindow(), true)
		setContentView(R.layout.splash)
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)

		// if this is a USB device, auto-launch the service
		if (UsbTnc.checkDeviceHandle(prefs, getIntent.getParcelableExtra("device")) && prefs.getBoolean("service_running", false))
			startService(AprsService.intent(this, AprsService.SERVICE))

		// Determine the destination activity, then show the splash for
		// SPLASH_DELAY_MS before navigating to it.
		// - First run (no permissions requested yet) → FirstRunActivity
		// - No background service running → always show Log
		// - Service running → show the last activity the user was on
		val target : Class[_] =
			if (!prefs.getBoolean("permissions_requested", false))
				classOf[FirstRunActivity]
			else if (!AprsService.running && !prefs.getBoolean("service_running", false))
				classOf[LogActivity]
			else {
				val mapmode = MapModes.defaultMapMode(this, new PrefsWrapper(this))
				prefs.getString("activity", "log") match {
				case "hub" => classOf[HubActivity]
				case "map" => mapmode.viewClass
				case _ => classOf[LogActivity]
				}
			}

		new Handler().postDelayed(new Runnable {
			override def run() : Unit = replaceAct(target)
		}, SPLASH_DELAY_MS)
	}
}
