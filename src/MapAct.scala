package org.aprsdroid.app

import _root_.android.{Manifest => AndroidManifest}
import _root_.android.app.AlertDialog
import _root_.android.content.{Context, DialogInterface, Intent, IntentFilter}
import _root_.android.content.pm.PackageManager
import _root_.android.content.res.Configuration
import _root_.android.database.Cursor
import _root_.android.graphics.drawable.{BitmapDrawable, Drawable}
import _root_.android.graphics.{Canvas, Paint, Path, Point, Rect, Typeface}
import _root_.android.location.LocationManager
import _root_.android.os.{Build, Bundle}
import _root_.android.util.Log
import _root_.android.view.{KeyEvent, Menu, MenuItem, View, WindowManager}
import _root_.android.widget.Toast
import _root_.org.mapsforge.v3.android.maps._
import _root_.org.mapsforge.v3.core.{GeoPoint, Tile}
import _root_.org.mapsforge.v3.android.maps.overlay.{ItemizedOverlay, OverlayItem}

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.java.io.File
import _root_.java.util.ArrayList
import java.lang.UnsupportedOperationException

import org.mapsforge.v3.android.maps.mapgenerator.{MapGeneratorFactory, MapGeneratorInternal}
import org.mapsforge.v3.map.reader.header.FileOpenResult

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class MapAct extends MapActivity with MapMenuHelper {
	override val TAG = "APRSdroid.Map"

	menu_id = R.id.map

	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val allicons = this.getResources().getDrawable(R.drawable.allicons)
	lazy val db = StorageDatabase.open(this)
	lazy val staoverlay = new StationOverlay(allicons, this, db)
	lazy val loading = findViewById(R.id.loading).asInstanceOf[View]
	lazy val locReceiver = new LocationReceiver2[ArrayList[OSMStation]](staoverlay.load_stations,
			staoverlay.replace_stations, staoverlay.cancel_stations)
	lazy val myLocationBtn = findViewById(R.id.my_location_btn).asInstanceOf[com.google.android.material.floatingactionbutton.FloatingActionButton]

	// Apply hardware acceleration based on user preference
	def applyHardwareAcceleration(useHardwareAcceleration : Boolean, mapview : MapView) {
		if (useHardwareAcceleration) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
				WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
			Log.d("Map", "Hardware acceleration enabled.")
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
			mapview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
			Log.d("Map", "Hardware acceleration disabled for MapView.")
		}
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)
		mapview.getOverlays().add(staoverlay)
		mapview.setTextScale(getResources().getDisplayMetrics().density)
		// Increase in-memory tile cache so more tiles stay in RAM
		// during a session, reducing re-downloads when panning.
		mapview.getInMemoryTileCache().setCapacity(50)
		// Enable persistent disk cache so downloaded tiles survive
		// across app restarts -- no re-download on next launch.
		mapview.getFileSystemTileCache().setPersistent(true)
		// Apply hardware acceleration preference
		applyHardwareAcceleration(prefs.getBoolean("hardware_acceleration", true), mapview)
		setupBottomNav()
		// "My location" FAB -- center the map on the last known GPS location
		myLocationBtn.setOnClickListener(new View.OnClickListener {
			override def onClick(v : View) : Unit = centerOnMyLocation()
		})

		startLoading()
	}

	def centerOnMyLocation() {
		try {
			val locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
			val provider = PeriodicGPS.bestProvider(locMan)
			if (provider != null) {
				val loc = locMan.getLastKnownLocation(provider)
				if (loc != null) {
					mapview.getController().setCenter(new GeoPoint(loc.getLatitude.toFloat, loc.getLongitude.toFloat))
					mapview.getController().setZoom(14)
					return
				}
			}
		} catch {
			case _ : SecurityException => /* ignore */
			case _ : Throwable => /* ignore */
		}
		Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
	}

	override def onResume() {
		super.onResume()
		applySystemBarInsets()
		highlightBottomNav()
		// only make it default if not tracking
		if (isCoordinateChooser)
			setTitle(R.string.p_source_from_map)
		else if (targetcall == "")
			setTitle(R.string.app_map)
		else
			setLongTitle(R.string.app_map, targetcall)
		setKeepScreenOn()
		setVolumeControls()
		//checkPermissions(Array(AndroidManifest.permission.READ_EXTERNAL_STORAGE, AndroidManifest.permission.WRITE_EXTERNAL_STORAGE), RELOAD_MAP)
		reloadMapAndTheme()
		mapview.requestFocus()
	}

	override def onConfigurationChanged(c : Configuration) = {
		super.onConfigurationChanged(c)
		if (targetcall != "")
			setLongTitle(R.string.app_map, targetcall)
	}

	override def onPause() {
		super.onPause()
		val pos = mapview.getMapPosition().getMapPosition()
		if (pos == null || pos.geoPoint == null)
			return
		saveMapViewPosition(pos.geoPoint.latitudeE6/1000000.0f, pos.geoPoint.longitudeE6/1000000.0f, pos.zoomLevel)
	}

	override def onDestroy() {
		super.onDestroy()
		unregisterReceiver(locReceiver)
	}

        override def loadMapViewPosition(lat : Float, lon : Float, zoom : Float) {
		mapview.getController().setCenter(new GeoPoint(lat, lon))
		mapview.getController().setZoom(zoom.asInstanceOf[Int])
        }

	def startLoading() {
		UIHelper.safeRegisterReceiver(this, locReceiver, new IntentFilter(AprsService.UPDATE))
		locReceiver.startTask(null)
	}

	val RELOAD_MAP = 1010

	override def getActionName(action : Int): Int = {
		action match {
		case RELOAD_MAP => R.string.show_map
		case _ => super.getActionName(action)
		}
	}
	override def onAllPermissionsGranted(action: Int): Unit = {
		action match {
		case RELOAD_MAP => reloadMapAndTheme()
		case _ => super.onAllPermissionsGranted(action)
		}
	}

	override def onPermissionsFailed(action : Int, permissions : Set[String]): Unit = {
		// fail to online OSM map
        }

	override def onPermissionsFailedCancel(action: Int): Unit = {
		// should never be called
        }

	def reloadMapAndTheme() {
		// Try tilepath first (NA7Q's preference key), then fall back to mapfile
		val tilepath = prefs.getString("tilepath", null)
		val mapfilePath = if (tilepath != null && tilepath.nonEmpty) tilepath
			else prefs.getString("mapfile", android.os.Environment.getExternalStorageDirectory() + "/aprsdroid.map")
		val mapfile = new File(mapfilePath)
		val isMapFileValid = mapfilePath.endsWith(".map") && mapfile.exists() && mapfile.canRead()
		// If offline mode is enabled, attempt to load the map file
		if (prefs.isOfflineMap()) {
			try {
				if (isMapFileValid) {
					val result = mapview.setMapFile(mapfile)
					if (!result.isSuccess)
						Toast.makeText(this, result.getErrorMessage, Toast.LENGTH_SHORT).show()
				} else {
					// Offline mode on but file invalid -- fall back to online
					loadOnlineMap()
				}
			} catch {
				case e : Exception =>
					Log.e("MapAct", "Unexpected error during map reload", e)
					loadOnlineMap()
			}
		} else {
			// Offline mode off -- use online map
			loadOnlineMap()
		}
		val themefile = new File(prefs.getString("themefile", android.os.Environment.getExternalStorageDirectory() + "/aprsdroid.xml"))
		if (themefile.exists())
			mapview.setRenderTheme(themefile)
		loadMapViewPosition()
	}

	// Helper: load online OSM tiles
	def loadOnlineMap() {
		try {
			// Always install the online OSM tile generator when called -- the
			// caller (reloadMapAndTheme) has already decided that online mode
			// is wanted (either offline mode is off, or the offline map file
			// is invalid/missing). Guarding on `mapview.getMapFile == null`
			// is wrong: if a map file was set in a previous session while
			// offline mode was enabled, getMapFile stays non-null and the
			// online generator is never installed, so the map keeps rendering
			// from the stale offline file even after the user disabled
			// offline mode.
			val map_gen = OsmTileDownloader.create(this)
			map_gen.setUserAgent(getString(R.string.build_version))
			mapview.setMapGenerator(map_gen)
		} catch {
			case _ : UnsupportedOperationException =>  /* ignore */
		}
	}

	override def onKeyDown(keyCode : Int, event : KeyEvent) : Boolean = {
		keyCode match {
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD |
		     KeyEvent.KEYCODE_MEDIA_NEXT =>
			changeZoom(+1)
			true
		case KeyEvent.KEYCODE_MEDIA_REWIND |
		     KeyEvent.KEYCODE_MEDIA_PREVIOUS =>
			changeZoom(-1)
			true
		case KeyEvent.KEYCODE_MEDIA_PLAY |
		     KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE =>
			if (mapview.hasFocus())
				mapview.focusSearch(View.FOCUS_FORWARD).requestFocus()
			else
				mapview.requestFocus()
			true
		case KeyEvent.KEYCODE_DPAD_CENTER |
		     KeyEvent.KEYCODE_ENTER =>
			// TODO: return coordinates
			if (isCoordinateChooser) {
				setResult(android.app.Activity.RESULT_OK, resultIntent)
				finish()
			}
			true
		case _ => super.onKeyDown(keyCode, event)
		}
	}

	def updateCoordinateInfo(): Unit = {
		if (!isCoordinateChooser)
			return
		val pos = mapview.getMapPosition().getMapPosition()
		if (pos == null || pos.geoPoint == null)
			return
		updateCoordinateInfo(pos.geoPoint.latitudeE6/1000000.0f, pos.geoPoint.longitudeE6/1000000.0f)
	}

	override def changeZoom(delta : Int) {
		mapview.getController().setZoom(mapview.getMapPosition().getZoomLevel() + delta)
	}

	def animateToCall() {
		if (targetcall != "") {
			val (found, lat, lon) = getStaPosition(db, targetcall)
			if (found)
				mapview.getController().setCenter(new GeoPoint(lat, lon))
		}
	}

	def onPostLoad() {
		mapview.invalidate()
		onStopLoading()
		animateToCall()
	}

	override def reloadMap() {
		onStartLoading()
		locReceiver.startTask(null)
	}

	override def onStartLoading() {
		loading.setVisibility(View.VISIBLE)
	}

	override def onStopLoading() {
		loading.setVisibility(View.GONE)
	}
}

class OSMStation(val movelog : ArrayBuffer[GeoPoint], val pt : GeoPoint,
	val call : String, val origin : String, val symbol : String)
	extends OverlayItem(pt, call, origin) {

	def inArea(bl : GeoPoint, tr : GeoPoint) = {
		val lat_ok = (bl.latitudeE6 <= pt.latitudeE6 && pt.latitudeE6 <= tr.latitudeE6)
		val lon_ok = if (bl.longitudeE6 <= tr.longitudeE6)
				     (bl.longitudeE6 <= pt.longitudeE6 && pt.longitudeE6 <= tr.longitudeE6)
			     else
				     (bl.longitudeE6 <= pt.longitudeE6 || pt.longitudeE6 <= tr.longitudeE6)
		lat_ok && lon_ok
	}
}

class StationOverlay(icons : Drawable, context : MapAct, db : StorageDatabase) extends ItemizedOverlay[OSMStation](icons) {
	val TAG = "APRSdroid.StaOverlay"

	//lazy val calls = new scala.collection.mutable.HashMap[String, Boolean]()
	var stations = new java.util.ArrayList[OSMStation]()

	// prevent android bug #11666
	populate()

	val iconbitmap = icons.asInstanceOf[BitmapDrawable].getBitmap
	val symbolSize = iconbitmap.getWidth()/16
	lazy val drawSize = (context.getResources().getDisplayMetrics().density * 24).toInt

	icons.setBounds(0, 0, symbolSize, symbolSize)

	// Cached Paint objects -- allocated once instead of per-frame.
	// Previously these were created fresh on every drawOverlayBitmap() call,
	// causing heavy GC pressure and slowing map rendering.
	lazy val fontSize = drawSize*7/8

	lazy val textPaint = {
		val p = new Paint()
		p.setColor(0xff000000)
		p.setTextAlign(Paint.Align.CENTER)
		p.setTextSize(fontSize)
		p.setTypeface(Typeface.MONOSPACE)
		p.setAntiAlias(true)
		p
	}

	lazy val strokePaint = {
		val p = new Paint(textPaint)
		p.setColor(0xffc8ffc8)
		p.setStyle(Paint.Style.STROKE)
		p.setStrokeWidth(drawSize.asInstanceOf[Float]/12.0f)
		p.setShadowLayer(10, 0, 0, 0x80c8ffc8)
		p
	}

	lazy val zoomTextPaint = {
		val p = new Paint(textPaint)
		p.setTextSize(fontSize * 0.7f)
		p
	}

	lazy val tracePaint = {
		val p = new Paint()
		p.setARGB(128, 100, 100, 255)
		p.setStyle(Paint.Style.STROKE)
		p.setStrokeJoin(Paint.Join.ROUND)
		p.setStrokeCap(Paint.Cap.ROUND)
		p.setStrokeWidth(drawSize/6)
		p.setAntiAlias(true)
		p
	}

	lazy val dotPaint = {
		val p = new Paint()
		p.setARGB(128, 255, 0, 0)
		p.setStyle(Paint.Style.FILL)
		p.setAntiAlias(true)
		p
	}

	// Reusable Path and Point for drawTrace -- avoids per-call allocation.
	val tracePath = new Path()
	val tracePoint = new Point()

	// Reusable Point for drawOverlayBitmap -- avoids per-station allocation.
	val drawPoint = new Point()

	override def size() = stations.size()
	override def createItem(idx : Int) : OSMStation = stations.get(idx)

	def symbol2rect(index : Int, page : Int) : Rect = {
		// check for overflow
		if (index < 0 || index >= 6*16)
			return new Rect(0, 0, symbolSize, symbolSize)
		val alt_offset = page*symbolSize*6
		val y = (index / 16) * symbolSize + alt_offset
		val x = (index % 16) * symbolSize
		new Rect(x, y, x+symbolSize, y+symbolSize)
	}
	def symbol2rect(symbol : String) : Rect = {
		symbol2rect(symbol(1) - 33, if (symbol(0) == '/') 0 else 1)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}

	def drawTrace(c : Canvas, proj : Projection, s : OSMStation) : Unit = {
		if (s.movelog.size() < 2) {
			return
		}
		tracePath.reset()
		var first = true
		for (p <- s.movelog) {
			proj.toPixels(p, tracePoint)
			if (first) {
				tracePath.moveTo(tracePoint.x, tracePoint.y)
				first = false
			} else
				tracePath.lineTo(tracePoint.x, tracePoint.y)
			c.drawCircle(tracePoint.x, tracePoint.y, drawSize/12, dotPaint)
		}
		c.drawPath(tracePath, tracePaint)
	}

	override def drawOverlayBitmap(c : Canvas, dp : Point, proj : Projection, zoom : Byte) : Unit = {

		if (!context.mapview.getMapPosition.isValid)
			return
		val (width, height) = (c.getWidth(), c.getHeight())
		val ss = drawSize/2

		// Draw the zoom level text in the bottom-left corner
		val zoomText = s"Zoom: $zoom"
		val textWidth = zoomTextPaint.measureText(zoomText)
		val xPos = 20 + (textWidth / 2)
		val yPos = height - 20
		c.drawText(zoomText, xPos, yPos, zoomTextPaint)

		for (s <- stations) {
			proj.toPixels(s.pt, drawPoint)
			if (drawPoint.x >= 0 && drawPoint.y >= 0 && drawPoint.x < width && drawPoint.y < height) {
				val srcRect = symbol2rect(s.symbol)
				val destRect = new Rect(drawPoint.x-ss, drawPoint.y-ss, drawPoint.x+ss, drawPoint.y+ss)
				// first draw callsign and trace
				if (zoom >= 10) {
					drawTrace(c, proj, s)

					c.drawText(s.call, drawPoint.x, drawPoint.y+ss+fontSize, strokePaint)
					c.drawText(s.call, drawPoint.x, drawPoint.y+ss+fontSize, textPaint)
				}
				// then the bitmap
				c.drawBitmap(iconbitmap, srcRect, destRect, null)
				// and finally the bitmap overlay, if any
				if (symbolIsOverlayed(s.symbol)) {
					// use page 2, overlay letters
					c.drawBitmap(iconbitmap, symbol2rect(s.symbol(0)-33, 2), destRect, null)
				}
			}
		}
		import AprsService.block2runnable
		context.handler.post { context.updateCoordinateInfo() }
	}

	def addStation(sta : OSMStation) {
		//if (calls.contains(sta.getTitle()))
		//	return
		//calls.add(sta.getTitle(), true)
		stations.add(sta)
	}

	override def onTap(gp : GeoPoint, mv : MapView) : Boolean = {
		//Log.d(TAG, "user tapped " + gp)
		//Log.d(TAG, "icon bounds: " + icons.getBounds())
		// convert geopoint to pixels
		val proj = mv.getProjection()
		val p = proj.toPixels(gp, null)
		// ... to pixel area ... to geo area
		//Log.d(TAG, "coords: " + p)
		val botleft = proj.fromPixels(p.x - 50, p.y + 50)
		val topright = proj.fromPixels(p.x + 50, p.y - 50)
		Log.d(TAG, "from " + botleft + " to " + topright)
		// fetch stations in the tap
		val list = stations.filter(_.inArea(botleft, topright)).map(_.call)
		Log.d(TAG, "found " + list.size() + " stations")
		val result = if (list.size() == 0)
			false // nothing found, do not revert to superclass
		else if (list.size() == 1) {
			// found one entry
			val call = list.get(0)
			Log.d(TAG, "user clicked on " + call)
			context.openDetails(call)
			true
		} else {
			// TODO: replace simple adapter with StationListAdapter for better UI
			new AlertDialog.Builder(context).setTitle(R.string.map_select)
				.setItems(list.toArray.asInstanceOf[Array[CharSequence]], new DialogInterface.OnClickListener() {
					override def onClick(di : DialogInterface, item : Int) {
						context.openDetails(list.get(item))
					}})
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			true
		}
		result
	}

	override def onTap(index : Int) : Boolean = {
		val s = stations(index)
		val target = if (s.origin != null && s.origin != "") s.origin
			else s.call
		Log.d(TAG, "user clicked on " + s.call + "/" + target)
		context.openDetails(s.call)
		true
	}

	def fetchStaPositions(call : String, c : Cursor) : ArrayBuffer[GeoPoint] = {
		import StorageDatabase.Position._
		val m = new ArrayBuffer[GeoPoint]()
		// skip forward to the right callsign
		while (!c.isAfterLast() && c.getString(COLUMN_CALL) < call)
			c.moveToNext()
		// add every matching entry to arraybuffer
		while (!c.isAfterLast() && c.getString(COLUMN_CALL) == call) {
			val lat = c.getInt(COLUMN_LAT)
			val lon = c.getInt(COLUMN_LON)
			m.add(new GeoPoint(lat, lon))
			c.moveToNext()
		}
		m
	}

	def load_stations(i : Intent) : ArrayList[OSMStation] = {
		import StorageDatabase.Station._

		val s = new ArrayList[OSMStation]()
		val age_ts = (System.currentTimeMillis - context.prefs.getShowAge()).toString
		val filter = if (context.showObjects) "TS > ? OR CALL=?" else "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
		val c = db.getStations(filter, Array(age_ts, context.targetcall), null)
		c.moveToFirst()
		val pos_c = db.getAllStaPositions(age_ts)
		pos_c.moveToFirst()
		while (!c.isAfterLast()) {
			val call = c.getString(COLUMN_MAP_CALL)
			val lat = c.getInt(COLUMN_MAP_LAT)
			val lon = c.getInt(COLUMN_MAP_LON)
			val symbol = c.getString(COLUMN_MAP_SYMBOL)
			val origin = c.getString(COLUMN_MAP_ORIGIN)
			val p = new GeoPoint(lat, lon)
			val m = fetchStaPositions(call, pos_c)
			s.add(new OSMStation(m, p, call, origin, symbol))
			c.moveToNext()
		}
		c.close()
		pos_c.close()
		Log.d(TAG, "total %d items".format(s.size()))
		s
	}

	def replace_stations(s : ArrayList[OSMStation]) {
		stations = s
		Benchmark("populate") { populate() }
		context.onPostLoad()
	}
	def cancel_stations(s : ArrayList[OSMStation]) {
	}

}
