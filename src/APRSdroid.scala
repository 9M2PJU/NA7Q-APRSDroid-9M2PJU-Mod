package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity

class APRSdroid extends AppCompatActivity {
	def replaceAct(act : Class[_]) {
		val i = new Intent(this, act)
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
		startActivity(i)
		finish()
	}

	override def onCreate(savedInstanceState : Bundle) {
		super.onCreate(savedInstanceState)
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)

		// if this is a USB device, auto-launch the service
		if (UsbTnc.checkDeviceHandle(prefs, getIntent.getParcelableExtra("device")) && prefs.getBoolean("service_running", false))
			startService(AprsService.intent(this, AprsService.SERVICE))

		// First-run permission flow: if the user hasn't been through the
		// permission setup screen yet, route there instead of Hub/Log/Map.
		// FirstRunActivity will route back here (via APRSdroid) after the
		// user taps "Continue", at which point permissions_requested=true
		// and we proceed to the normal flow below.
		if (!prefs.getBoolean("permissions_requested", false)) {
			replaceAct(classOf[FirstRunActivity])
			return
		}

		val mapmode = MapModes.defaultMapMode(this, new PrefsWrapper(this))
		prefs.getString("activity", "log") match {
		case "hub" => replaceAct(classOf[HubActivity])
		case "map" => replaceAct(mapmode.viewClass)
		case _ => replaceAct(classOf[LogActivity])
		}
	}
}
