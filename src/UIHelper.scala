package org.aprsdroid.app
// this class is a hack containing all the common UI code for different Activity subclasses

import _root_.android.app.{Activity, ListActivity}
import _root_.android.app.AlertDialog
import _root_.android.content.{BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter}
import _root_.android.content.res.Configuration
import _root_.android.net.Uri
import _root_.android.os.{Build, Environment}
import _root_.android.util.Log
import _root_.android.view.{ContextMenu, Menu, MenuItem, View, WindowManager}
import _root_.android.widget.AdapterView.AdapterContextMenuInfo
import _root_.android.widget.{EditText, Toast}
import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.Date

import android.content.pm.PackageManager
import android.provider.Settings

import androidx.core.content.FileProvider
import android.widget.PopupMenu
import com.google.android.material.bottomnavigation.BottomNavigationView

object UIHelper
{
	def getExportDirectory(ctx : Context) : File = {
		val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
		} else {
			Environment.getExternalStorageDirectory()
		}
		return new File(base, "APRSdroid")
	}

	def shareFile(ctx : Context, file : File, filename : String) {
		ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
			.setType("text/plain")
			.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, "org.aprsdroid.fileprovider", file))
			.putExtra(Intent.EXTRA_SUBJECT, filename),
		file.toString()))
	}

	/**
	 * Register a BroadcastReceiver with the appropriate flags for Android 14+
	 * (API 34+). On older Android, the flags are ignored.
	 * Uses RECEIVER_NOT_EXPORTED since all our receivers are for internal broadcasts.
	 */
	def safeRegisterReceiver(ctx : Context, receiver : BroadcastReceiver, filter : IntentFilter) {
		if (Build.VERSION.SDK_INT >= 34) {  // Android 14 (UPSIDE_DOWN_CAKE)
			ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
		} else {
			ctx.registerReceiver(receiver, filter)
		}
	}

	/**
	 * Opt out of edge-to-edge on Android 15+ (targetSdk 35). Call from
	 * any Activity's onCreate or onResume.
	 *
	 * Strategy: apply ONLY the top (status bar) inset to the root content
	 * view, and apply the bottom (nav bar) inset to the BottomNavigationView
	 * if present. This way the bottom nav's background extends to the bottom
	 * edge of the screen (no blank gap), while its icons stay above the
	 * system navigation bar.
	 *
	 * Uses the native View.OnApplyWindowInsetsListener (API 20+) so no
	 * AndroidX dependency upgrade is needed.
	 */
	def applySystemBarInsets(act : android.app.Activity) {
		// Approach 1: opt out of edge-to-edge (may be ignored by MIUI/HyperOS)
		if (Build.VERSION.SDK_INT >= 30) {
			act.getWindow().setDecorFitsSystemWindows(true)
		} else {
			androidx.core.view.WindowCompat.setDecorFitsSystemWindows(
				act.getWindow(), true)
		}

		// Approach 2: native inset listener on the root content view.
		// Apply ONLY top padding (status bar) to the root. The bottom nav
		// bar inset is applied to the BottomNavigationView so its background
		// extends edge-to-edge while icons stay above the system nav bar.
		val root = act.getWindow().getDecorView().findViewById(
			android.R.id.content).asInstanceOf[View]
		if (root != null) {
			root.setOnApplyWindowInsetsListener(
				new View.OnApplyWindowInsetsListener() {
					override def onApplyWindowInsets(v : View,
							insets : android.view.WindowInsets
							) : android.view.WindowInsets = {
						var topPad = 0
						var bottomPad = 0
						var leftPad = 0
						var rightPad = 0
						if (Build.VERSION.SDK_INT >= 30) {
							val status = insets.getInsets(
								android.view.WindowInsets.Type.statusBars())
							val nav = insets.getInsets(
								android.view.WindowInsets.Type.navigationBars())
							topPad = status.top
							leftPad = status.left
							rightPad = status.right
							bottomPad = nav.bottom
						} else {
							// API 20-29: use getSystemWindowInset*
							topPad = insets.getSystemWindowInsetTop()
							leftPad = insets.getSystemWindowInsetLeft()
							rightPad = insets.getSystemWindowInsetRight()
							bottomPad = insets.getSystemWindowInsetBottom()
						}
						// Apply ONLY top/left/right padding to root.
						// Bottom padding is applied to the BottomNavigationView
						// below so its background extends to the screen edge.
						v.setPadding(leftPad, topPad, rightPad, 0)

						// Find the BottomNavigationView and apply bottom
						// padding so its icons stay above the system nav bar
						// while its background fills the full area.
						val bn = act.findViewById(R.id.bottom_nav).asInstanceOf[View]
						if (bn != null) {
							bn.setPadding(bn.getPaddingLeft(),
								bn.getPaddingTop(),
								bn.getPaddingRight(),
								bottomPad)
						}

						// Find the bottom button bar (StationActivity:
						// MAP / APRS.FI / QRZ.COM buttons) and apply bottom
						// padding so the buttons stay above the system nav bar.
						val bbb = act.findViewById(R.id.bottom_button_bar).asInstanceOf[View]
						if (bbb != null) {
							bbb.setPadding(bbb.getPaddingLeft(),
								bbb.getPaddingTop(),
								bbb.getPaddingRight(),
								bottomPad)
						}

						// Find the message input bar (MessageActivity:
						// EditText + Send button) and apply bottom padding
						// so it stays above the system nav bar.
						val bl = act.findViewById(R.id.buttonlayout).asInstanceOf[View]
						if (bl != null && bn == null) {
							// Only apply if there's no BottomNavigationView
							// below it (main.xml has buttonlayout above the
							// BottomNavigationView, so it doesn't need this).
							bl.setPadding(bl.getPaddingLeft(),
								bl.getPaddingTop(),
								bl.getPaddingRight(),
								bottomPad)
						}
						insets
					}
				})

		// Fallback for PreferenceActivity: the inset listener above may
		// not be triggered reliably on Android 16 (edge-to-edge enforced).
		// Directly read the status bar height from the system resource and
		// apply it as top padding to BOTH the content view AND the
		// PreferenceActivity's built-in ListView (android.R.id.list).
		// The ListView padding is needed because when the user navigates
		// into a PreferenceScreen sub-screen (e.g. Notifications), the
		// ListView content is swapped but the root padding alone doesn't
		// push the list items down — the ListView itself needs padding.
		val prefList = act.findViewById(android.R.id.list).asInstanceOf[View]
		if (prefList != null) {
			val res = act.getResources()
			val resId = res.getIdentifier("status_bar_height", "dimen", "android")
			if (resId > 0) {
				val statusBarHeight = res.getDimensionPixelSize(resId)
				if (statusBarHeight > 0) {
					root.setPadding(root.getPaddingLeft(), statusBarHeight,
						root.getPaddingRight(), root.getPaddingBottom())
					prefList.setPadding(prefList.getPaddingLeft(), statusBarHeight,
						prefList.getPaddingRight(), prefList.getPaddingBottom())
				}
			}
		}
		} // close if (root != null)
	}

}

trait UIHelper extends Activity
		with LoadingIndicator
		with PermissionHelper
{

	var menu_id : Int = -1
	lazy val prefs = new PrefsWrapper(this)
	var openedPrefs = false

	// thx to http://robots.thoughtbot.com/post/5836463058/scala-a-better-java-for-android
	def findView[WidgetType] (id : Int) : WidgetType = {
		findViewById(id).asInstanceOf[WidgetType]
	}

	def openDetails(call : String) {
		startActivity(new Intent(this, classOf[StationActivity]).setData(Uri.parse(call)))
	}

	def openMessaging(call : String) {
		startActivity(new Intent(this, classOf[MessageActivity]).setData(Uri.parse(call)))
	}

	def clearMessages(call : String) {
		new MessageCleaner(StorageDatabase.open(this), call).execute()
	}

	def clearAllMessages(call : String) {
		new AllMessageCleaner(StorageDatabase.open(this)).execute()
	}

	def openMessageSend(call : String, message : String) {
		startActivity(new Intent(this, classOf[MessageActivity]).setData(Uri.parse(call)).putExtra("message", message))
	}

	def trackOnMap(call : String) {
		val text = getString(R.string.map_track_call, call)
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
		MapModes.startMap(this, prefs, call)
	}

	def openPrefs(toastId : Int, act : Class[_]) {
		if (openedPrefs) {
			// only open prefs once, exit app afterwards
			finish()
		} else {
			startActivity(new Intent(this, act));
			Toast.makeText(this, toastId, Toast.LENGTH_SHORT).show()
			openedPrefs = true
		}
	}
	def currentListOfPermissions() : Array[String] = {
		val bi_perms = AprsBackend.defaultBackendPermissions(prefs)
		val ls_perms = LocationSource.getPermissions(prefs)
		(bi_perms ++ ls_perms).toArray
	}

	val START_SERVICE = 1001
	val START_SERVICE_ONCE = 1002

	override def getActionName(action : Int): Int = {
		action match {
		case START_SERVICE => R.string.startlog
		case START_SERVICE_ONCE => R.string.singlelog
		}
	}
	override def onAllPermissionsGranted(action : Int): Unit = {
		action match {
		case START_SERVICE => startService(AprsService.intent(this, AprsService.SERVICE))
		case START_SERVICE_ONCE => startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
		}
	}
	override def onPermissionsFailedCancel(action: Int): Unit = {
		// nop
	}
	def startAprsService(action : Int): Unit = {
		checkPermissions(currentListOfPermissions(), action)
	}

	// manual stop: remember shutdown for next reboot
	def stopAprsService() {
		// explicitly disabled, remember this
		prefs.setBoolean("service_running", false)
		stopService(AprsService.intent(this, AprsService.SERVICE))
	}

	def passcodeConfigRequired(call : String, pass : String) : Boolean = {
		import AprsBackend._
		// a valid passcode must be entered for "required",
		// "" and "-1" are accepted as well for "optional"
		defaultBackendInfo(prefs).need_passcode match {
		case PASSCODE_NONE => false
		case PASSCODE_OPTIONAL =>
			!AprsPacket.passcodeAllowed(call, pass, true)
		case PASSCODE_REQUIRED =>
			!AprsPacket.passcodeAllowed(call, pass, false)
		}
	}

	lazy val passcodeDialog = new PasscodeDialog(this, true)
	def firstRunDialog() = {
		passcodeDialog.show()
	}

	def keyboardNavDialog(force : Boolean = false) {
		if (getPackageManager().hasSystemFeature("android.hardware.touchscreen"))
			return
		if (!force && prefs.getBoolean("kbdnav_shown", false))
			return
		
		val keys = Array("←→↑↓", "⏪⏩", "⏯️", "⏎🆗")
		val titles = getResources().getStringArray(R.array.kbdnav_lines)
		val text = keys zip titles map { case (k, v) => "%s\t%s".format(k, v) } mkString("\n\n")
		new AlertDialog.Builder(this).setTitle(R.string.kbdnav_title)
			.setMessage(text)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener {
				override def onClick(dialog: DialogInterface, which: Int) = {
					prefs.prefs.edit().putBoolean("kbdnav_shown", true).commit()
				}})
			.create.show
	}

	def setTitleStatus() {
		if (AprsService.link_error != 0) {
			setTitle(getString(R.string.status_linkoff, getString(AprsService.link_error)))
		} else {
			val title = getPackageManager().getActivityInfo(getComponentName(), 0).labelRes
			setTitle(title)
		}
	}

	def setLongTitle(title_id : Int, targetcall : String) {
		// use two-line display on holo in portrait mode
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
				new HoneycombTitleSetter(getString(title_id), targetcall)
			else
				new HoneycombTitleSetter(getString(title_id) + ": " + targetcall, null)
		} else // pre-holo setTitle
			setTitle(getString(title_id) + ": " + targetcall)
	}
	class HoneycombTitleSetter(t : String, st : String) {
		UIHelper.this.setTitle(t)
		val ab = UIHelper.this.getActionBar()
		if (ab != null && st != null)
			ab.setSubtitle(st)
	}

	// store the activity name for next APRSdroid launch
	def makeLaunchActivity(actname : String) {
		prefs.prefs.edit().putString("activity", actname).commit()
	}

	// Set up the bottom navigation bar. Call from onContentViewLoaded()
	// or onResume(). The current activity is highlighted based on menu_id.
	def setupBottomNav() {
		val nav = findViewById(R.id.bottom_nav).asInstanceOf[View]
		if (nav == null) return
		val bn = nav.asInstanceOf[BottomNavigationView]
		// Highlight the current tab
		val currentNavId = menu_id match {
			case R.id.hub => R.id.nav_hub
			case R.id.log => R.id.nav_log
			case R.id.map => R.id.nav_map
			case R.id.conversations => R.id.nav_messages
			case _ => R.id.nav_log
		}
		bn.setSelectedItemId(currentNavId)
		bn.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener {
			override def onNavigationItemSelected(item : MenuItem) : Boolean = {
				item.getItemId match {
					case R.id.nav_hub =>
						startActivity(new Intent(UIHelper.this, classOf[HubActivity])
							.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
								Intent.FLAG_ACTIVITY_NO_ANIMATION))
						overridePendingTransition(0, 0)
						true
					case R.id.nav_log =>
						startActivity(new Intent(UIHelper.this, classOf[LogActivity])
							.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
								Intent.FLAG_ACTIVITY_NO_ANIMATION))
						overridePendingTransition(0, 0)
						true
					case R.id.nav_map =>
						MapModes.startMap(UIHelper.this, prefs, "")
						overridePendingTransition(0, 0)
						true
					case R.id.nav_messages =>
						startActivity(new Intent(UIHelper.this, classOf[ConversationsActivity])
							.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
								Intent.FLAG_ACTIVITY_NO_ANIMATION))
						overridePendingTransition(0, 0)
						true
					case R.id.nav_menu =>
						showOptionsMenuPopup(bn)
						true
					case _ => false
				}
			}
		})
	}

	// Show the old options menu as a popup anchored above the bottom nav.
	// This replaces the 3-dot action bar overflow menu which wasn't visible
	// on all screens (e.g. the map). Contains Preferences, Export, Clear,
	// Start/Stop, About, and map-specific options.
	def showOptionsMenuPopup(anchor : View) {
		// Anchor the popup to the BottomNavigationView and use Gravity.END
		// so it aligns to the right edge (above the Menu button, which is
		// the rightmost item in the bottom nav).
		val popup = new PopupMenu(this, anchor)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			popup.setGravity(android.view.Gravity.END)
		val menu = popup.getMenu()
		// Inflate the same menus the old onCreateOptionsMenu used
		getMenuInflater().inflate(R.menu.options_activities, menu)
		getMenuInflater().inflate(R.menu.options_map, menu)
		getMenuInflater().inflate(R.menu.options, menu)
		// Hide activity-switching items (handled by bottom nav) and the
		// "own" activity item
		Array(R.id.hub, R.id.map, R.id.log, R.id.conversations).map((id) => {
			menu.findItem(id).setVisible(false)
		})
		menu.findItem(R.id.age).setVisible(R.id.map == menu_id || R.id.hub == menu_id)
		menu.findItem(R.id.overlays).setVisible(R.id.map == menu_id)
		// Update start/stop label
		val ssmi = menu.findItem(R.id.startstopbtn)
		ssmi.setTitle(if (AprsService.running) R.string.stoplog else R.string.startlog)
		ssmi.setIcon(if (AprsService.running) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_compass)
		// Handle item selection via the existing onOptionsItemSelected
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener {
			override def onMenuItemClick(mi : MenuItem) : Boolean =
				onOptionsItemSelected(mi)
		})
		popup.show()
	}

	// keep screen on all the time if requested
	def setKeepScreenOn() {
		if (prefs.getBoolean("keepscreen", false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
	}

	// Opt out of edge-to-edge on Android 15+ (targetSdk 35) so content
	// doesn't go under the status / navigation bars. Delegates to the
	// static UIHelper.applySystemBarInsets(Activity) which uses both
	// setDecorFitsSystemWindows and a ViewCompat inset listener for
	// maximum compatibility (including MIUI/HyperOS).
	def applySystemBarInsets() {
		UIHelper.applySystemBarInsets(this)
	}

	// for AFSK, set the right volume controls
	def setVolumeControls() {
		if (prefs.getString("backend", AprsBackend.DEFAULT_CONNTYPE) == "afsk") {
			setVolumeControlStream(prefs.getAfskOutput())
		}
	}

	def checkConfig() : Boolean = {
		val callsign = prefs.getCallsign()
		val passcode = prefs.getPasscode()
		if (callsign == "" || prefs.getBoolean("firstrun", true)) {
			firstRunDialog()
			return false
		}
		// auto-switch to network location if device lacks GPS
		if (prefs.getString("loc_source", null) == null) {
			val has_location = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)
			val has_network = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)
			val has_gps = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
			val best = PeriodicGPS.bestProvider(this)
			Log.d("checkConfig", "hasLocation = " + has_location + " hasGPS = " + has_gps + " hasNetwork = " + has_network + " best = " + best)
			Log.d("checkConfig", "hasTouch = " + getPackageManager().hasSystemFeature("android.hardware.touchscreen"))
			if (!has_gps && best == "passive") {
				Log.d("checkConfig", "does not have any real location sources, must be a FireTV")
				prefs.prefs.edit()
					.putString("loc_source", "manual")
					.commit()
				startActivity(new Intent(this, classOf[LocationPrefs]));
				false
			} else if (!has_gps) {
				Log.d("checkConfig", "does not have GPS, switching to netloc")
				prefs.prefs.edit()
					.putString("loc_source", "periodic")
					.putBoolean("netloc", true)
					.commit()
			}
		}
		if (passcodeConfigRequired(callsign, passcode)) {
			openPrefs(R.string.wrongpasscode, classOf[BackendPrefs])
			return false
		}

		if (prefs.getStringInt("interval", 10) < 1) {
			openPrefs(R.string.mininterval, classOf[PrefsAct])
			return false
		}
		if (prefs.getString("proto", null) == null) {
			// upgrade to 1.4+, need to set "proto" and "link"/"aprsis"
			val proto_link_aprsis = AprsBackend.backend_upgrade(prefs.getString("backend", "tcp")).split("-")
			prefs.prefs.edit()
				.putString("proto", proto_link_aprsis(0))
				.putString("link", proto_link_aprsis(1))
				.putString("aprsis", proto_link_aprsis(2))
				.commit()
		}
		true
	}

	def aboutDialog() {
		val pi = getPackageManager().getPackageInfo(this.getPackageName(), 0)
		val title = getString(R.string.ad_title);
		val inflater = getLayoutInflater()
		val aboutview = inflater.inflate(R.layout.aboutview, null)
		// Show the version (from package info, not hardcoded) below the app name
		val versionView = aboutview.findViewById(R.id.about_version).asInstanceOf[android.widget.TextView]
		if (versionView != null)
			versionView.setText(pi.versionName)
		new AlertDialog.Builder(this).setTitle(title)
			.setView(aboutview)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton(android.R.string.ok, null)
			.setNeutralButton(R.string.ad_homepage, new UrlOpener(this, "https://aprsdroid.hamradio.my/"))
			.create.show
	}

	def reportBug() {
		val pi = getPackageManager().getPackageInfo(this.getPackageName(), 0)
		val version = pi.versionName
		val androidVersion = android.os.Build.VERSION.RELEASE
		val device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
		val body = getString(R.string.report_bug_body, version, androidVersion, device)
		val intent = new Intent(Intent.ACTION_SENDTO)
			.setData(android.net.Uri.parse("mailto:9m2pju@hamradio.my"))
			.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_bug_subject))
			.putExtra(Intent.EXTRA_TEXT, body)
		try {
			startActivity(Intent.createChooser(intent, getString(R.string.report_bug)))
		} catch {
			case _ : android.content.ActivityNotFoundException =>
				Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_LONG).show()
		}
	}

	def ageDialog() {
		val minutes = getResources().getStringArray(R.array.age_minutes)
		val selected = minutes.indexOf(prefs.getString("show_age", "30"))

		new AlertDialog.Builder(this).setTitle(getString(R.string.age))
			.setSingleChoiceItems(R.array.ages, selected, new DialogInterface.OnClickListener() {
					override def onClick(d : DialogInterface, which : Int) {
						Log.d("onClick", "clicked on: " + d + " " + which)
						val min = getResources().getStringArray(R.array.age_minutes)(which)
						prefs.prefs.edit().putString("show_age", min).commit()
						sendBroadcast(new Intent(AprsService.UPDATE))
						onStartLoading()
						d.dismiss()
					}})
			//.setPositiveButton(android.R.string.ok, null)
			//.setNegativeButton(android.R.string.cancel, null)
			.create.show
	}

	def sendMessageBroadcast(dest : String, body : String) {
		sendBroadcast(new Intent(AprsService.MESSAGETX)
			.putExtra(AprsService.SOURCE, prefs.getCallSsid())
			.putExtra(AprsService.DEST, dest)
			.putExtra(AprsService.BODY, body)
			)
	}

	abstract override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_activities, menu);
		getMenuInflater().inflate(R.menu.options_map, menu);
		getMenuInflater().inflate(R.menu.options, menu);
		// disable the "own" menu
		Array(R.id.hub, R.id.map, R.id.log, R.id.conversations).map((id) => {
			menu.findItem(id).setVisible(id != menu_id)
		})
		menu.findItem(R.id.age).setVisible(R.id.map == menu_id || R.id.hub == menu_id)
		menu.findItem(R.id.overlays).setVisible(R.id.map == menu_id)
		true
	}

	abstract override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		val mi = menu.findItem(R.id.startstopbtn)
		mi.setTitle(if (AprsService.running) R.string.stoplog else R.string.startlog)
		mi.setIcon(if (AprsService.running) android.R.drawable.ic_menu_close_clear_cancel  else android.R.drawable.ic_menu_compass)
		menu.findItem(R.id.objects).setChecked(prefs.getShowObjects())
		menu.findItem(R.id.satellite).setChecked(prefs.getShowSatellite())
		true
	}

	abstract override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.preferences =>
			startActivity(new Intent(this, classOf[PrefsAct]));
			true
		case R.id.export =>
			onStartLoading()
			new LogExporter(StorageDatabase.open(this), null).execute()
			true
		case R.id.clear =>
			onStartLoading()
			new StorageCleaner(StorageDatabase.open(this)).execute()
			true
		case R.id.clearallmessages =>
			onStartLoading()
			new AllMessageCleaner(StorageDatabase.open(this)).execute()
			true			
		case R.id.about =>
			aboutDialog()
			true
		case R.id.check_updates =>
			UpdateChecker.forceCheckForUpdates(this)
			true
		case R.id.report_bug =>
			reportBug()
			true
		case R.id.age =>
			ageDialog()
			true
		// switch between activities
		case R.id.hub =>
			startActivity(new Intent(this, classOf[HubActivity]).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
			true
		case R.id.map =>
			MapModes.startMap(this, prefs, "")
			true
		case R.id.log =>
			startActivity(new Intent(this, classOf[LogActivity]).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
			true
		case R.id.conversations =>
			startActivity(new Intent(this, classOf[ConversationsActivity]).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
			true
		// toggle service
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startAprsService(START_SERVICE)
			} else {
				stopAprsService()
			}
			true
		case R.id.singlebtn =>
			startAprsService(START_SERVICE_ONCE)
			true
		// quit the app
		//case R.id.quit =>
		//	// XXX deprecated!
		//	stopService(AprsService.intent(this, AprsService.SERVICE))
		//	finish();
		//	true
		case android.R.id.home =>
			if (isTaskRoot()) {
				startActivity(new Intent(this, classOf[HubActivity]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
				finish();
				true
			} else super.onOptionsItemSelected(mi)
		case _ => false
		}
	}
	
	abstract override def onCreateContextMenu(menu : ContextMenu, v : View,
			menuInfo : ContextMenu.ContextMenuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo)
		val call = menuInfoCall(menuInfo)
		if (call == null)
			return
		getMenuInflater().inflate(R.menu.context_call, menu)
		menu.setHeaderTitle(call)
	}
	def menuInfoCall(menuInfo : ContextMenu.ContextMenuInfo) : String = {
		val i = menuInfo.asInstanceOf[AdapterContextMenuInfo]
		// a listview with a database backend gives out a cursor :D
		val c = asInstanceOf[ListActivity].getListView()
			.getItemAtPosition(i.position).asInstanceOf[android.database.Cursor]
		StorageDatabase.cursor2call(c)
	}

	def getStaPosition(db : StorageDatabase, targetcall : String) = {
		val cursor = db.getStaPosition(targetcall)
		if (cursor.getCount() > 0) {
			cursor.moveToFirst()
			val lat = cursor.getInt(StorageDatabase.Station.COLUMN_LAT)
			val lon = cursor.getInt(StorageDatabase.Station.COLUMN_LON)
			cursor.close()
			Log.d("GetStaPos", "Found " + targetcall +" " + lat + " " + lon)
			(true, lat, lon)
		} else {
			Toast.makeText(this, getString(R.string.map_track_unknown, targetcall), Toast.LENGTH_SHORT).show()
			cursor.close()
			Log.d("GetStaPos", "Missed " + targetcall)
			(false, 0, 0)
		}
	}

	def callsignAction(id : Int, targetcall : String) : Boolean = {
		val basecall = targetcall.split("[- ]+")(0)
		id match {
		case R.id.details =>
			openDetails(targetcall)
			true
		case R.id.message =>
			openMessaging(targetcall)
			true
		case R.id.messagesclear =>
			clearMessages(targetcall)
			true
		case R.id.map =>
			trackOnMap(targetcall)
			true
		case R.id.extmap =>
			val (found, lat, lon) = getStaPosition(StorageDatabase.open(this), targetcall)
			if (found) {
				val url = "geo:%1.6f,%1.6f?q=%1.6f,%1.6f(%s)".formatLocal(null,
					lat/1000000.0, lon/1000000.0, lat/1000000.0, lon/1000000.0, targetcall)
				startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW,
					Uri.parse(url)), targetcall))
			}
			true
		case R.id.aprsfi =>
			val url = "https://aprs.fi/info/a/%s?utm_source=aprsdroid&utm_medium=inapp&utm_campaign=aprsfi".format(targetcall)
			UrlOpener.open(this, url)
			true
		case R.id.qrzcom =>
			val url = "https://qrz.com/db/%s".format(basecall)
			UrlOpener.open(this, url)
			true
		case R.id.sta_export =>
			new LogExporter(StorageDatabase.open(this), basecall).execute()
			true
		case _ =>
			false
		}
	}
	abstract override def onContextItemSelected(item : MenuItem) : Boolean = {
		val targetcall = menuInfoCall(item.getMenuInfo)
		callsignAction(item.getItemId(), targetcall)
	}


	class StorageCleaner(storage : StorageDatabase) extends MyAsyncTask[Unit, Unit] {
		override def doInBackground1(params : Array[String]) {
			Log.d("StorageCleaner", "trimming...")
			storage.trimPosts(Long.MaxValue)
		}
		override def onPostExecute(x : Unit) {
			Log.d("StorageCleaner", "broadcasting...")
			sendBroadcast(new Intent(AprsService.UPDATE))
		}
	}
	class MessageCleaner(storage : StorageDatabase, call : String) extends MyAsyncTask[Unit, Unit] {
		override def doInBackground1(params : Array[String]) {
			Log.d("MessageCleaner", "deleting...")
			storage.deleteMessages(call)
		}
		override def onPostExecute(x : Unit) {
			Log.d("MessageCleaner", "broadcasting...")
			sendBroadcast(AprsService.MSG_PRIV_INTENT)
		}
	}
	class AllMessageCleaner(storage : StorageDatabase) extends MyAsyncTask[Unit, Unit] {
		override def doInBackground1(params : Array[String]) {
			Log.d("MessageCleaner", "deleting all messages...")
			storage.deleteAllMessages()
		}
		override def onPostExecute(x : Unit) {
			Log.d("MessageCleaner", "broadcasting...")
			sendBroadcast(AprsService.MSG_PRIV_INTENT)
		}
	}	
	class LogExporter(storage : StorageDatabase, call : String) extends MyAsyncTask[Unit, String] {
		val filename = "aprsdroid-%s.log".format(new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()))
		val directory = UIHelper.getExportDirectory(UIHelper.this)
		val file = new File(directory, filename)

		override def doInBackground1(params : Array[String]) : String = {
			import StorageDatabase.Post._
			Log.d("LogExporter", "saving " + filename + " for callsign " + call)
			val c = storage.getExportPosts(call)
			if (c.getCount() == 0) {
				return getString(R.string.export_empty)
			}
			try {
				directory.mkdirs()
				val fo = new PrintWriter(file)
				while (c.moveToNext()) {
					val ts = c.getString(COLUMN_TSS)
					val tpe = c.getInt(COLUMN_TYPE)
					val packet = c.getString(COLUMN_MESSAGE)
					fo.print(ts)
					fo.print("\t")
					fo.print(if (tpe == TYPE_INCMG) "RX" else if (tpe == TYPE_DIGI) "DP" else if (tpe == TYPE_IG) "IG" else "TX" )
					fo.print("\t")
					fo.println(packet)
				}
				fo.close()
				return null
			} catch {
			case e : Exception => return e.getMessage()
			} finally {
				c.close()
			}
		}
		override def onPostExecute(error : String) {
			Log.d("LogExporter", "saving " + filename + " done: " + error)
			onStopLoading()
			if (error != null)
				Toast.makeText(UIHelper.this, error, Toast.LENGTH_SHORT).show()
			else 
				UIHelper.shareFile(UIHelper.this, file, filename)
		}
	}
}

object UrlOpener {
	def open(ctx: Context, url : String) {
		try {
			ctx.startActivity(new Intent(Intent.ACTION_VIEW,
				Uri.parse(url)))
		} catch {
		case e : Exception => Toast.makeText(ctx, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show()
		}
	}
}

class UrlOpener(ctx : Context, url : String) extends DialogInterface.OnClickListener {
	override def onClick(d : DialogInterface, which : Int) {
		UrlOpener.open(ctx, url)
	}
}

