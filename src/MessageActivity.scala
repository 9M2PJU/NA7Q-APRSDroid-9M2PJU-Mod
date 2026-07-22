package org.aprsdroid.app

import _root_.android.app.{AlertDialog, ListActivity}
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.net.Uri
import _root_.android.os.{Bundle, Handler}
import _root_.android.text.{ClipboardManager, Editable, TextWatcher}
import _root_.android.util.Log
import _root_.android.view.{ContextMenu, LayoutInflater, KeyEvent, Menu, MenuItem, View, Window}
import _root_.android.view.View.{OnClickListener, OnKeyListener}
import _root_.android.widget.{Button, EditText, LinearLayout, ListView, TextView, Toast}
import _root_.android.widget.AdapterView.AdapterContextMenuInfo

class MessageActivity extends StationHelper(R.string.app_messages)
		with OnClickListener with OnKeyListener with TextWatcher {
	val TAG = "APRSdroid.Message"

	lazy val storage = StorageDatabase.open(this)

	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new MessageListAdapter(this, prefs, mycall, targetcall)

	lazy val msginput = findView[EditText](R.id.msginput)
	lazy val msgsend = findView[Button](R.id.msgsend)

	// Winlink UI elements (only inflated when talking to WLNK-1)
	var winlinkButtons : View = null
	var winlinkStatusView : TextView = null
	var winlinkBtnLogin : Button = null
	var winlinkBtnList : Button = null
	var winlinkBtnCompose : Button = null
	var winlinkBtnLogout : Button = null

	def isWinlinkConversation = targetcall != null &&
		(targetcall.equalsIgnoreCase("WLNK-1") || targetcall.equalsIgnoreCase("WLNK"))

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.message_act)

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)

		msginput.addTextChangedListener(this)
		msginput.setOnKeyListener(this)
		msgsend.setOnClickListener(this)

		// Inflate Winlink buttons if this is a WLNK-1 conversation
		if (isWinlinkConversation) {
			setupWinlinkUI()
		}

		val message = getIntent().getStringExtra("message")
		if (message != null) {
			Log.d(TAG, "sending message to %s: %s".format(targetcall, message))
			sendMessage(message)
		}
	}

	def setupWinlinkUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		winlinkButtons = inflater.inflate(R.layout.winlink_buttons, null, false)
		winlinkStatusView = winlinkButtons.findViewById(R.id.winlink_status).asInstanceOf[TextView]
		winlinkBtnLogin = winlinkButtons.findViewById(R.id.winlink_btn_login).asInstanceOf[Button]
		winlinkBtnList = winlinkButtons.findViewById(R.id.winlink_btn_list).asInstanceOf[Button]
		winlinkBtnCompose = winlinkButtons.findViewById(R.id.winlink_btn_compose).asInstanceOf[Button]
		winlinkBtnLogout = winlinkButtons.findViewById(R.id.winlink_btn_logout).asInstanceOf[Button]

		winlinkBtnLogin.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWinlinkLogin()
		})
		winlinkBtnList.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWinlinkList()
		})
		winlinkBtnCompose.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWinlinkCompose()
		})
		winlinkBtnLogout.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWinlinkLogout()
		})

		// Insert the Winlink buttons at the top of the message activity
		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(winlinkButtons, 0)

		updateWinlinkStatus()
	}

	def updateWinlinkStatus() {
		if (winlinkStatusView == null) return
		val state = if (AprsService.running) {
			AprsService.serviceInstance match {
				case s : AprsService => s.winlinkService.getState
				case _ => WinlinkService.STATE_LOGGED_OUT
			}
		} else WinlinkService.STATE_LOGGED_OUT
		val statusText = state match {
		case WinlinkService.STATE_LOGGED_OUT => getString(R.string.winlink_status_logged_out)
		case WinlinkService.STATE_LOGIN_STARTED => getString(R.string.winlink_status_logging_in)
		case WinlinkService.STATE_CHALLENGE => getString(R.string.winlink_status_logging_in)
		case WinlinkService.STATE_LOGGED_IN => getString(R.string.winlink_status_logged_in)
		case WinlinkService.STATE_ERROR => getString(R.string.winlink_status_error)
		case _ => getString(R.string.winlink_status_logged_out)
		}
		winlinkStatusView.setText(statusText)
		// Enable/disable buttons based on state
		winlinkBtnLogin.setEnabled(state == WinlinkService.STATE_LOGGED_OUT || state == WinlinkService.STATE_ERROR)
		winlinkBtnList.setEnabled(state == WinlinkService.STATE_LOGGED_IN)
		winlinkBtnCompose.setEnabled(state == WinlinkService.STATE_LOGGED_IN)
		winlinkBtnLogout.setEnabled(state == WinlinkService.STATE_LOGGED_IN)
	}

	def getWinlinkService : Option[WinlinkService] = {
		if (AprsService.running) {
			AprsService.serviceInstance match {
				case s : AprsService => Some(s.winlinkService)
				case _ => None
			}
		} else None
	}

	def onWinlinkLogin() {
		if (prefs.getCallsign().isEmpty) {
			Toast.makeText(this, R.string.winlink_no_callsign, Toast.LENGTH_LONG).show()
			return
		}
		if (prefs.getWinlinkPassword().isEmpty) {
			Toast.makeText(this, R.string.winlink_no_password, Toast.LENGTH_LONG).show()
			return
		}
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		getWinlinkService match {
			case Some(ws) =>
				ws.login()
				Toast.makeText(this, R.string.winlink_login_started, Toast.LENGTH_SHORT).show()
			case None =>
				Toast.makeText(this, R.string.winlink_not_logged_in, Toast.LENGTH_LONG).show()
		}
		updateWinlinkStatus()
	}

	def onWinlinkLogout() {
		getWinlinkService match {
			case Some(ws) =>
				ws.logout()
				Toast.makeText(this, R.string.winlink_logged_out, Toast.LENGTH_SHORT).show()
		 case None =>
		}
		updateWinlinkStatus()
	}

	def onWinlinkList() {
		getWinlinkService match {
			case Some(ws) => ws.listMessages()
			case None => Toast.makeText(this, R.string.winlink_not_logged_in, Toast.LENGTH_LONG).show()
		}
	}

	def onWinlinkCompose() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.winlink_compose, null, false)
		val toField = view.findViewById(R.id.winlink_to).asInstanceOf[EditText]
		val subjField = view.findViewById(R.id.winlink_subject).asInstanceOf[EditText]
		val bodyField = view.findViewById(R.id.winlink_body).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.winlink_compose)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val to = toField.getText().toString.trim
					val subject = subjField.getText().toString.trim
					val body = bodyField.getText().toString
					if (to.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.winlink_email_failed, Toast.LENGTH_SHORT).show()
						return
					}
					getWinlinkService match {
						case Some(ws) =>
							ws.sendEmail(to, subject, body)
							Toast.makeText(MessageActivity.this, R.string.winlink_sending, Toast.LENGTH_SHORT).show()
						case None =>
							Toast.makeText(MessageActivity.this, R.string.winlink_not_logged_in, Toast.LENGTH_LONG).show()
					}
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	override def onResume() {
		super.onResume()
		ServiceNotifier.instance.cancelMessage(this, targetcall)
		if (isWinlinkConversation) updateWinlinkStatus()
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}

	override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		menu.findItem(R.id.message).setVisible(false)
		true
	}

	// return message cursor for a given context menu
	def menuMessageCursor(menuInfo : ContextMenu.ContextMenuInfo) = {
		val i = menuInfo.asInstanceOf[AdapterContextMenuInfo]
		// a listview with a database backend gives out a cursor :D
		getListView().getItemAtPosition(i.position)
				.asInstanceOf[android.database.Cursor]
	}

	def messageAction(id : Int, c : Cursor) : Boolean = {
		import StorageDatabase.Message._
		val msg_id = c.getLong(/* COLUMN_ID */ 0)
		val msg_type = c.getInt(COLUMN_TYPE)
		id match {
		case R.id.copy =>
			getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
				.setText(c.getString(COLUMN_TEXT))
			true
		case R.id.abort =>
			if (msg_type == TYPE_OUT_NEW) {
				storage.updateMessageType(msg_id, TYPE_OUT_ABORTED)
				sendBroadcast(AprsService.MSG_PRIV_INTENT)
			}
			true
		case R.id.resend =>
			if (msg_type != TYPE_INCOMING) {
				val cv = new ContentValues()
				cv.put(TYPE, TYPE_OUT_NEW.asInstanceOf[java.lang.Integer])
				cv.put(RETRYCNT, 0.asInstanceOf[java.lang.Integer])
				cv.put(TS, System.currentTimeMillis.asInstanceOf[java.lang.Long])
				storage.updateMessage(msg_id, cv)
				sendBroadcast(AprsService.MSG_TX_PRIV_INTENT)
			}
			true
		case _ => false
		}
	}
	override def onCreateContextMenu(menu : ContextMenu, v : View,
			menuInfo : ContextMenu.ContextMenuInfo) {
		import StorageDatabase.Message._
		//super.onCreateContextMenu(menu, v, menuInfo)
		val c = menuMessageCursor(menuInfo)
		val msg_type = c.getInt(COLUMN_TYPE)
		val title_id = if (msg_type == TYPE_INCOMING) R.string.msg_from else R.string.msg_to
		getMenuInflater().inflate(R.menu.context_msg, menu)
		menu.setGroupVisible(R.id.msg_menu_out, msg_type != TYPE_INCOMING)
		menu.setHeaderTitle(getString(title_id, c.getString(COLUMN_CALL)))
	}

	override def onContextItemSelected(item : MenuItem) : Boolean = {
		Log.d(TAG, "menu for " + menuMessageCursor(item.getMenuInfo).getLong(0))
		messageAction(item.getItemId, menuMessageCursor(item.getMenuInfo))
	}


	// TextWatcher for msginput
	override def afterTextChanged(s : Editable) {
		msgsend.setEnabled(msginput.getText().length() > 0)
	}
	override def beforeTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}
	override def onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}

	// react on "Return" key
	def onKey(v : View, kc : Int, ev : KeyEvent) = {
		if (ev.getAction() == KeyEvent.ACTION_DOWN && kc == KeyEvent.KEYCODE_ENTER) {
			sendMessage()
			true
		} else false
	}

	def sendMessage() {
		sendMessage(msginput.getText().toString())
	}
	def sendMessage(msg : String) {
		import StorageDatabase.Message._

		if (msg.length() == 0)
			return
		Log.d("MessageActivity", "sending " + msg)
		msginput.setText(null)

		val cv = new ContentValues()
		cv.put(TS, System.currentTimeMillis().asInstanceOf[java.lang.Long])
		cv.put(RETRYCNT, 0.asInstanceOf[java.lang.Integer])
		cv.put(CALL, targetcall)
		cv.put(MSGID, storage.createMsgId(targetcall).asInstanceOf[java.lang.Integer])
		cv.put(TYPE, TYPE_OUT_NEW.asInstanceOf[java.lang.Integer])
		cv.put(TEXT, msg)
		storage.addMessage(cv)
		// notify backend
		sendMessageBroadcast(targetcall, msg)
		// notify UI about new message
		sendBroadcast(AprsService.MSG_PRIV_INTENT)
		// if not connected, ask the user to start tracking
		if (!AprsService.running)
			showStartTrackingDialog()
	}

	def showStartTrackingDialog() {
		new AlertDialog.Builder(this)
			.setTitle(R.string.msg_not_tracking_title)
			.setMessage(R.string.msg_not_tracking_body)
			.setPositiveButton(R.string.startlog, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					startAprsService(START_SERVICE)
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// button actions
	override def onClick(view : View) {
		Log.d(TAG, "onClick: " + view.getId)
		view.getId match {
		case R.id.msgsend =>
			sendMessage()
		}
	}

}
