package org.aprsdroid.app

import _root_.android.os.Bundle
import _root_.android.preference.PreferenceActivity
import androidx.core.view.WindowCompat

class WinlinkPrefs extends PreferenceActivity {

	override def onCreate(savedInstanceState: Bundle) : Unit = {
		super.onCreate(savedInstanceState)
		UIHelper.applySystemBarInsets(this)
		addPreferencesFromResource(R.xml.winlink)
	}
}
