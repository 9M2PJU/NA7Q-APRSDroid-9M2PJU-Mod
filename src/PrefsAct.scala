package org.aprsdroid.app

import _root_.android.app.{AlertDialog, ListActivity}
import _root_.android.content.{Context, DialogInterface, Intent, SharedPreferences}
import _root_.android.net.Uri
import _root_.android.content.ContentUris
import _root_.android.os.{Build, Bundle, Environment}
import _root_.android.preference.Preference
import _root_.android.preference.Preference.OnPreferenceChangeListener
import _root_.android.preference.Preference.OnPreferenceClickListener
import _root_.android.preference.{PreferenceActivity, PreferenceManager}
import _root_.android.view.{Menu, MenuItem}
import _root_.android.widget.Toast
import java.text.SimpleDateFormat
import java.io.{File, PrintWriter}
import java.util.Date
import android.provider.{Settings, DocumentsContract, MediaStore}

import org.json.JSONObject
import androidx.core.view.WindowCompat

class PrefsAct extends PreferenceActivity {
	lazy val db = StorageDatabase.open(this)
	lazy val prefs = new PrefsWrapper(this)

	def exportPrefs() {
		val filename = "profile-%s.aprs".format(new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()))
		val directory = UIHelper.getExportDirectory(this)
		val file = new File(directory, filename)
		try {
			directory.mkdirs()
			val prefs = PreferenceManager.getDefaultSharedPreferences(this)
			val allPrefs = prefs.getAll
			allPrefs.remove("map_zoom")
			val json = new JSONObject(allPrefs)
			val fo = new PrintWriter(file)
			fo.println(json.toString(2))
			fo.close()

			UIHelper.shareFile(this, file, filename)
		} catch {
			case e : Exception => Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()
		}
	}

	def fileChooserPreference(pref_name : String, reqCode : Int, titleId : Int) {
		findPreference(pref_name).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			def onPreferenceClick(preference : Preference) = {
				val get_file = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
					.addCategory(Intent.CATEGORY_OPENABLE)
				startActivityForResult(Intent.createChooser(get_file,
					getString(titleId)), reqCode)
				true
			}
		});
	}

	// Apply top (status bar) and bottom (nav bar) padding to the
	// preference ListView. This is the resource-based fallback that
	// complements the OnApplyWindowInsetsListener in
	// UIHelper.applyPrefActivityInsets. It is re-applied after sub-screen
	// navigation (onContentChanged, onWindowFocusChanged, onPreDraw)
	// because the ListView content is swapped and padding may be reset.
	// Uses clipToPadding=false so items scroll smoothly through the
	// padding area under the transparent status bar — modern edge-to-edge.
	def applyPrefTopInset() {
		val res = getResources()
		val resId = res.getIdentifier("status_bar_height", "dimen", "android")
		val navBarResId = res.getIdentifier("navigation_bar_height", "dimen", "android")
		val statusBarHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
		val navBarHeight = if (navBarResId > 0) res.getDimensionPixelSize(navBarResId) else 0
		val lv = findViewById(android.R.id.list).asInstanceOf[android.view.View]
		if (lv != null) {
			// clipToPadding=false for smooth edge-to-edge scrolling —
			// items scroll through the padding area under the status bar.
			if (lv.isInstanceOf[android.view.ViewGroup])
				lv.asInstanceOf[android.view.ViewGroup].setClipToPadding(false)
			if (lv.getPaddingTop != statusBarHeight || lv.getPaddingBottom != navBarHeight) {
				lv.setPadding(lv.getPaddingLeft, statusBarHeight,
					lv.getPaddingRight, navBarHeight)
			}
		}
	}

	// Schedule applyPrefTopInset to run after the current event loop.
	def postApplyPrefTopInset() {
		new android.os.Handler(getMainLooper).post(new Runnable {
			override def run() : Unit = applyPrefTopInset()
		})
	}

	// Called when the preference list content changes (e.g. when navigating
	// into a PreferenceScreen sub-screen). Re-apply the top inset padding
	// and re-attach the OnPreDrawListener to the new content view.
	override def onContentChanged() {
		android.util.Log.d("PrefsAct", "onContentChanged")
		super.onContentChanged()
		applyPrefTopInset()
		postApplyPrefTopInset()
		// Re-attach the OnPreDrawListener to the new content view
		val rootView = getWindow.getDecorView.findViewById(
			android.R.id.content).asInstanceOf[android.view.View]
		android.util.Log.d("PrefsAct", "onContentChanged: rootView=" + rootView)
		if (rootView != null) {
			rootView.getViewTreeObserver.addOnPreDrawListener(
				new android.view.ViewTreeObserver.OnPreDrawListener {
					override def onPreDraw() : Boolean = {
						applyPrefTopInset()
						true
					}
				})
		}
	}

	// Called when the window focus changes. This fires after layout
	// is complete, so padding set here won't be reset by the layout pass.
	// This catches PreferenceScreen sub-screen navigation where the
	// ListView content is swapped.
	override def onWindowFocusChanged(hasFocus : Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus)
			applyPrefTopInset()
	}

	// Let the system create the PreferenceScreen dialog (edge-to-edge
	// with transparent status bar), then apply top padding to the
	// dialog's ListView so text starts below the status bar icons.
	// We access the system-created dialog via PreferenceScreen.getDialog()
	// reflection (which works — only field/method reflection on hidden
	// APIs is blocked on Android 16, but getDialog() is accessible).
	// This is much simpler than creating our own dialog: no custom
	// AlertDialog, no performClick() reflection, no old-dialog dismissal.
	override def onPreferenceTreeClick(preferenceScreen : android.preference.PreferenceScreen,
			preference : android.preference.Preference) : Boolean = {
		val result = super.onPreferenceTreeClick(preferenceScreen, preference)
		// After the system creates the dialog, apply padding to its
		// ListView so content starts below the status bar.
		if (preference.isInstanceOf[android.preference.PreferenceScreen]) {
			val screen = preference.asInstanceOf[android.preference.PreferenceScreen]
			val adapter = screen.getRootAdapter()
			// Only apply padding for real sub-screens (with nested
			// preferences). Screens with <intent> tags launch activities.
			if (adapter != null && adapter.getCount() > 0) {
				applyDialogInsetPadding(screen)
			}
		}
		result
	}

	// Apply top (status bar) and bottom (nav bar) padding to the
	// PreferenceScreen dialog's ListView. Uses getDialog() reflection
	// to access the system-created dialog, then sets an
	// OnApplyWindowInsetsListener on the dialog's decorView so padding
	// is applied during the layout pass (not overridden by the dialog's
	// internal layout logic).
	def applyDialogInsetPadding(screen : android.preference.PreferenceScreen) {
		new android.os.Handler(getMainLooper).postDelayed(new Runnable {
			override def run() : Unit = {
				try {
					val m = classOf[android.preference.PreferenceScreen]
						.getMethod("getDialog")
					val dialog = m.invoke(screen).asInstanceOf[android.app.Dialog]
					if (dialog != null && dialog.isShowing) {
						val lv = dialog.findViewById(android.R.id.list)
							.asInstanceOf[android.widget.ListView]
						if (lv != null) {
							// clipToPadding=true (default) so items are clipped
							// at the padding boundary and can't scroll under
							// the status bar. The dialog's background still
							// flows under the transparent status bar (edge-to-
							// edge), but text stays below the status bar icons.
							lv.setClipToPadding(true)
							// Set an insets listener on the dialog's decorView
							// so padding is applied during layout and survives
							// any internal re-layout by the AlertDialog.
							val decor = dialog.getWindow().getDecorView()
							decor.setOnApplyWindowInsetsListener(
								new android.view.View.OnApplyWindowInsetsListener {
									override def onApplyWindowInsets(v : android.view.View,
											insets : android.view.WindowInsets
											) : android.view.WindowInsets = {
										var topPad = 0
										var bottomPad = 0
										if (Build.VERSION.SDK_INT >= 30) {
											topPad = insets.getInsets(
												android.view.WindowInsets.Type.statusBars()).top
											bottomPad = insets.getInsets(
												android.view.WindowInsets.Type.navigationBars()).bottom
										} else {
											topPad = insets.getSystemWindowInsetTop()
											bottomPad = insets.getSystemWindowInsetBottom()
										}
										lv.setPadding(0, topPad, 0, bottomPad)
										lv.setSelection(0)
										android.util.Log.d("PrefsAct",
											"Dialog inset listener: top=%d bottom=%d"
												.format(topPad, bottomPad))
										insets
									}
								})
							// Also apply padding immediately as fallback
							val res = getResources()
							val resId = res.getIdentifier("status_bar_height", "dimen", "android")
							val navBarResId = res.getIdentifier("navigation_bar_height", "dimen", "android")
							val statusBarHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
							val navBarHeight = if (navBarResId > 0) res.getDimensionPixelSize(navBarResId) else 0
							lv.setPadding(0, statusBarHeight, 0, navBarHeight)
							lv.setSelection(0)
							// Request a new insets dispatch to trigger the listener
							decor.requestApplyInsets()
						}
					}
				} catch {
					case e : Exception =>
						android.util.Log.e("PrefsAct", "applyDialogInsetPadding failed", e)
				}
			}
		}, 50)
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		UIHelper.applySystemBarInsets(this)
		applyPrefTopInset()
		addPreferencesFromResource(R.xml.preferences)

		// Add an OnPreDrawListener to the DecorView (which persists
		// across content view swaps) to re-apply top inset padding right
		// before each draw. This catches PreferenceScreen sub-screen
		// navigation where the content view is replaced and the ListView
		// content is swapped.
		val decorView = getWindow.getDecorView.asInstanceOf[android.view.View]
		if (decorView != null) {
			decorView.getViewTreeObserver.addOnPreDrawListener(
				new android.view.ViewTreeObserver.OnPreDrawListener {
					override def onPreDraw() : Boolean = {
						applyPrefTopInset()
						true
					}
				})
		}

		// Set up "Grant Storage Permissions" button
		val allFilesAccessPref = findPreference("all_files_access")
		if (allFilesAccessPref != null) {
			allFilesAccessPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				def onPreferenceClick(preference : Preference) = {
					openAllFilesAccessSettings()
					true
				}
			})
		}
		// Warn user to restart app when offline map is toggled
		val offlineMapPref = findPreference("p.offlinemap")
		if (offlineMapPref != null) {
			offlineMapPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				def onPreferenceChange(preference : Preference, newValue : Object) : Boolean = {
					val enabled = newValue.asInstanceOf[Boolean]
					if (enabled)
						showRestartDialog()
					true
				}
			})
		}
		// File pickers
		fileChooserPreference("tilepath", 123456, R.string.p_mbtiles_file_picker_title)
	}

	def showRestartDialog() {
		new AlertDialog.Builder(this)
			.setTitle(R.string.restart_required_title)
			.setMessage(R.string.restart_required_body)
			.setPositiveButton(R.string.restart_now, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val pm = getPackageManager().getLaunchIntentForPackage(getPackageName())
					val intent = pm.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
					finishAffinity()
					startActivity(intent)
					// hard kill so the process restarts cleanly
					android.os.Process.killProcess(android.os.Process.myPid())
				}
			})
			.setNegativeButton(R.string.restart_later, null)
			.show()
	}
	override def onResume() {
		super.onResume()
		findPreference("p_connsetup").setSummary(prefs.getBackendName())
		findPreference("p_location").setSummary(prefs.getLocationSourceName())
		findPreference("p_symbol").setSummary(getString(R.string.p_symbol_summary) + ": " + prefs.getString("symbol", "/$"))
		// Show current tilepath in summary
		val tilepath = prefs.prefs.getString("tilepath", null)
		if (tilepath != null && tilepath.nonEmpty) {
			findPreference("tilepath").setSummary(tilepath)
		}
	}

	def openAllFilesAccessSettings() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			val intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
			intent.setData(Uri.parse("package:" + getPackageName()))
			startActivity(intent)
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
			intent.setData(Uri.parse("package:" + getPackageName()))
			startActivity(intent)
		} else {
			val intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
			intent.setData(Uri.parse("package:" + getPackageName()))
			startActivity(intent)
		}
	}

	def resolveContentUri(uri : Uri) = {
		val Array(storage, path) = uri.getPath().replace("/document/", "").split(":", 2)
		android.util.Log.d("PrefsAct", "resolveContentUri s=" + storage + " p=" + path)
		if (storage == "primary")
			Environment.getExternalStorageDirectory() + "/" + path
		else
			"/storage/" + storage + "/" + path
	}

	def parseFilePickerResult(data : Intent, pref_name : String, error_id : Int) {
		val file = data.getData().getScheme() match {
		case "file" =>
			data.getData().getPath()
		case "content" =>
			// fix up Uri for KitKat+; http://stackoverflow.com/a/20559175/539443
			// http://stackoverflow.com/a/27271131/539443
			if ("com.android.externalstorage.documents".equals(data.getData().getAuthority())) {
				resolveContentUri(data.getData())
			} else {
				val fixup_uri = Uri.parse(data.getDataString().replace(
					"content://com.android.providers.downloads.documents/document",
					"content://downloads/public_downloads"))
				val cursor = getContentResolver().query(fixup_uri, null, null, null, null)
				cursor.moveToFirst()
				val idx = cursor.getColumnIndex("_data")
				val result = if (idx != -1) cursor.getString(idx) else null
				cursor.close()
				result
			}
		case _ =>
			null
		}
		if (file != null) {
			PreferenceManager.getDefaultSharedPreferences(this)
				.edit().putString(pref_name, file).commit()
			Toast.makeText(this, file, Toast.LENGTH_SHORT).show()
			// reload prefs
			finish()
			startActivity(getIntent())
		} else {
			val errmsg = getString(error_id, data.getDataString())
			Toast.makeText(this, errmsg, Toast.LENGTH_SHORT).show()
			db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_ERROR,
				getString(R.string.post_error), errmsg)
		}
	}

	override def onActivityResult(reqCode : Int, resultCode : Int, data : Intent) {
		android.util.Log.d("PrefsAct", "onActResult: request=" + reqCode + " result=" + resultCode + " " + data)
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123456) {
			// tilepath picker — resolve URI to real path and save
			val takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
			getContentResolver.takePersistableUriPermission(data.getData(), takeFlags)
			val resolvedPath = data.getData().getScheme match {
				case "file" => data.getData().getPath
				case "content" => resolveContentUri(data.getData())
				case _ => null
			}
			if (resolvedPath != null) {
				PreferenceManager.getDefaultSharedPreferences(this)
					.edit().putString("tilepath", resolvedPath).commit()
				Toast.makeText(this, getString(R.string.selected_file, new File(resolvedPath).getName()), Toast.LENGTH_SHORT).show()
			} else {
				Toast.makeText(this, R.string.mapfile_error, Toast.LENGTH_SHORT).show()
			}
			finish()
			startActivity(getIntent())
		} else
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123458) {
			data.setClass(this, classOf[ProfileImportActivity])
			startActivity(data)
		} else
			super.onActivityResult(reqCode, resultCode, data)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_prefs, menu)
		true
	}
	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.profile_load =>
			val get_file = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
			// TODO: use MaterialFilePicker().withFilter() for *.aprs
			startActivityForResult(Intent.createChooser(get_file,
				getString(R.string.profile_import_activity)), 123458)
			true
		case R.id.profile_export =>
			exportPrefs()
			true
		case _ => super.onOptionsItemSelected(mi)
		}
	}
}
