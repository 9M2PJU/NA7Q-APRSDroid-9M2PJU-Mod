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
	var winlinkBtnLogout : Button = null
	var winlinkBtnHelp : Button = null
	var winlinkBtnList : Button = null
	var winlinkBtnRead : Button = null
	var winlinkBtnReply : Button = null
	var winlinkBtnCompose : Button = null
	var winlinkBtnPlayback : Button = null
	var winlinkBtnSms : Button = null
	var winlinkBtnSetAlias : Button = null
	var winlinkBtnListAliases : Button = null
	var winlinkBtnForward : Button = null
	var winlinkBtnKill : Button = null
	var winlinkBtnGateway : Button = null
	var winlinkBtnInfo : Button = null

	// WTSAPP UI elements (only inflated when talking to WTSAPP)
	var wtsappButtons : View = null
	var wtsappBtnSend : Button = null
	var wtsappBtnSetAlias : Button = null
	var wtsappBtnRemoveAlias : Button = null

	def isWinlinkConversation = targetcall != null &&
		(targetcall.equalsIgnoreCase("WLNK-1") || targetcall.equalsIgnoreCase("WLNK"))

	def isWtsappConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("WTSAPP")

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
		// Inflate WTSAPP buttons if this is a WTSAPP conversation
		if (isWtsappConversation) {
			setupWtsappUI()
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
		winlinkBtnLogout = winlinkButtons.findViewById(R.id.winlink_btn_logout).asInstanceOf[Button]
		winlinkBtnHelp = winlinkButtons.findViewById(R.id.winlink_btn_help).asInstanceOf[Button]
		winlinkBtnList = winlinkButtons.findViewById(R.id.winlink_btn_list).asInstanceOf[Button]
		winlinkBtnRead = winlinkButtons.findViewById(R.id.winlink_btn_read).asInstanceOf[Button]
		winlinkBtnReply = winlinkButtons.findViewById(R.id.winlink_btn_reply).asInstanceOf[Button]
		winlinkBtnCompose = winlinkButtons.findViewById(R.id.winlink_btn_compose).asInstanceOf[Button]
		winlinkBtnPlayback = winlinkButtons.findViewById(R.id.winlink_btn_playback).asInstanceOf[Button]
		winlinkBtnSms = winlinkButtons.findViewById(R.id.winlink_btn_sms).asInstanceOf[Button]
		winlinkBtnSetAlias = winlinkButtons.findViewById(R.id.winlink_btn_set_alias).asInstanceOf[Button]
		winlinkBtnListAliases = winlinkButtons.findViewById(R.id.winlink_btn_list_aliases).asInstanceOf[Button]
		winlinkBtnForward = winlinkButtons.findViewById(R.id.winlink_btn_forward).asInstanceOf[Button]
		winlinkBtnKill = winlinkButtons.findViewById(R.id.winlink_btn_kill).asInstanceOf[Button]
		winlinkBtnGateway = winlinkButtons.findViewById(R.id.winlink_btn_gateway).asInstanceOf[Button]
		winlinkBtnInfo = winlinkButtons.findViewById(R.id.winlink_btn_info).asInstanceOf[Button]

		winlinkBtnLogin.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkLogin() })
		winlinkBtnLogout.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkLogout() })
		winlinkBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkHelp() })
		winlinkBtnList.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkList() })
		winlinkBtnRead.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkRead() })
		winlinkBtnReply.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkReply() })
		winlinkBtnCompose.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkCompose() })
		winlinkBtnPlayback.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkPlayback() })
		winlinkBtnSms.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkSms() })
		winlinkBtnSetAlias.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkSetAlias() })
		winlinkBtnListAliases.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkListAliases() })
		winlinkBtnForward.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkForward() })
		winlinkBtnKill.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkKill() })
		winlinkBtnGateway.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkGateway() })
		winlinkBtnInfo.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkInfo() })

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
		val logged_in = state == WinlinkService.STATE_LOGGED_IN
		// Enable/disable buttons based on state
		winlinkBtnLogin.setEnabled(state == WinlinkService.STATE_LOGGED_OUT || state == WinlinkService.STATE_ERROR)
		winlinkBtnLogout.setEnabled(logged_in)
		winlinkBtnHelp.setEnabled(logged_in)
		winlinkBtnList.setEnabled(logged_in)
		winlinkBtnRead.setEnabled(logged_in)
		winlinkBtnReply.setEnabled(logged_in)
		winlinkBtnCompose.setEnabled(logged_in)
		winlinkBtnPlayback.setEnabled(logged_in)
		winlinkBtnSms.setEnabled(logged_in)
		winlinkBtnSetAlias.setEnabled(logged_in)
		winlinkBtnListAliases.setEnabled(logged_in)
		winlinkBtnForward.setEnabled(logged_in)
		winlinkBtnKill.setEnabled(logged_in)
		winlinkBtnGateway.setEnabled(logged_in)
		winlinkBtnInfo.setEnabled(logged_in)
	}

	def getWinlinkService : Option[WinlinkService] = {
		if (AprsService.running) {
			AprsService.serviceInstance match {
				case s : AprsService => Some(s.winlinkService)
				case _ => None
			}
		} else None
	}

	def requireWinlinkService(f : WinlinkService => Unit) {
		getWinlinkService match {
			case Some(ws) => f(ws)
			case None => Toast.makeText(this, R.string.winlink_not_logged_in, Toast.LENGTH_LONG).show()
		}
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
		requireWinlinkService { ws =>
			ws.logout()
			Toast.makeText(this, R.string.winlink_logged_out, Toast.LENGTH_SHORT).show()
		}
		updateWinlinkStatus()
	}

	def onWinlinkHelp() {
		requireWinlinkService { ws => ws.help() }
	}

	def onWinlinkList() {
		requireWinlinkService { ws => ws.listMessages() }
	}

	def onWinlinkRead() {
		showMsgNumberDialog(R.string.winlink_read, (num) => {
			requireWinlinkService { ws => ws.readMessage(num) }
		})
	}

	def onWinlinkReply() {
		showMsgNumberDialog(R.string.winlink_reply, (num) => {
			requireWinlinkService { ws => ws.replyMessage(num) }
		})
	}

	def onWinlinkPlayback() {
		requireWinlinkService { ws => ws.playback() }
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
					requireWinlinkService { ws =>
						ws.sendEmail(to, subject, body)
						Toast.makeText(MessageActivity.this, R.string.winlink_sending, Toast.LENGTH_SHORT).show()
					}
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	def onWinlinkSms() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.winlink_sms, null, false)
		val toField = view.findViewById(R.id.winlink_sms_to_field).asInstanceOf[EditText]
		val msgField = view.findViewById(R.id.winlink_sms_message_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.winlink_sms)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val to = toField.getText().toString.trim
					val msg = msgField.getText().toString.trim
					if (to.isEmpty || msg.isEmpty) return
					requireWinlinkService { ws => ws.sendSMS(to, msg) }
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	def onWinlinkSetAlias() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.winlink_alias, null, false)
		val nameField = view.findViewById(R.id.winlink_alias_name_field).asInstanceOf[EditText]
		val emailField = view.findViewById(R.id.winlink_alias_email_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.winlink_set_alias)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val alias = nameField.getText().toString.trim
					if (alias.isEmpty) return
					val email = emailField.getText().toString.trim
					requireWinlinkService { ws => ws.setAlias(alias, email) }
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	def onWinlinkListAliases() {
		requireWinlinkService { ws => ws.listAliases() }
	}

	def onWinlinkForward() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.winlink_forward, null, false)
		val numField = view.findViewById(R.id.winlink_forward_number_field).asInstanceOf[EditText]
		val toField = view.findViewById(R.id.winlink_forward_to_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.winlink_forward)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val numStr = numField.getText().toString.trim
					val to = toField.getText().toString.trim
					if (numStr.isEmpty || to.isEmpty) return
					try {
						val num = numStr.toInt
						requireWinlinkService { ws => ws.forwardMessage(num, to) }
					} catch { case _ : Throwable => }
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	def onWinlinkKill() {
		showMsgNumberDialog(R.string.winlink_kill, (num) => {
			requireWinlinkService { ws => ws.killMessage(num) }
		})
	}

	def onWinlinkGateway() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.winlink_gateway, null, false)
		val countField = view.findViewById(R.id.winlink_gateway_count_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.winlink_gateway)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val countStr = countField.getText().toString.trim
					val count = if (countStr.isEmpty) 1 else try { countStr.toInt } catch { case _ : Throwable => 1 }
					requireWinlinkService { ws => ws.gatewayInfo(count) }
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	def onWinlinkInfo() {
		requireWinlinkService { ws => ws.info() }
	}

	// Reusable dialog for commands that take a message number (R#, Y#, K#)
	def showMsgNumberDialog(titleId : Int, callback : Int => Unit) {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.winlink_msg_number, null, false)
		val numField = view.findViewById(R.id.winlink_msg_number_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(titleId)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val numStr = numField.getText().toString.trim
					if (numStr.isEmpty) return
					try {
						val num = numStr.toInt
						if (num >= 1 && num <= 5) callback(num)
					} catch { case _ : Throwable => }
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== WTSAPP (APRS to WhatsApp Gateway) =====

	def setupWtsappUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		wtsappButtons = inflater.inflate(R.layout.wtsapp_buttons, null, false)
		wtsappBtnSend = wtsappButtons.findViewById(R.id.wtsapp_btn_send).asInstanceOf[Button]
		wtsappBtnSetAlias = wtsappButtons.findViewById(R.id.wtsapp_btn_set_alias).asInstanceOf[Button]
		wtsappBtnRemoveAlias = wtsappButtons.findViewById(R.id.wtsapp_btn_remove_alias).asInstanceOf[Button]

		wtsappBtnSend.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWtsappSend()
		})
		wtsappBtnSetAlias.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWtsappSetAlias()
		})
		wtsappBtnRemoveAlias.setOnClickListener(new OnClickListener {
			override def onClick(v : View) : Unit = onWtsappRemoveAlias()
		})

		// Insert the WTSAPP buttons at the top of the message activity
		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(wtsappButtons, 0)
	}

	// Send a WhatsApp message: formats as "@<recipient> <message>"
	def onWtsappSend() {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.wtsapp_compose, null, false)
		val recipientField = view.findViewById(R.id.wtsapp_recipient_field).asInstanceOf[EditText]
		val messageField = view.findViewById(R.id.wtsapp_message_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.wtsapp_send)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val recipient = recipientField.getText().toString.trim
					val message = messageField.getText().toString.trim
					if (recipient.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_recipient, Toast.LENGTH_SHORT).show()
						return
					}
					if (message.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_message, Toast.LENGTH_SHORT).show()
						return
					}
					// Format: @<recipient> <message>
					val formatted = "@%s %s".format(recipient, message)
					sendMessage(formatted)
					Toast.makeText(MessageActivity.this, R.string.wtsapp_sent, Toast.LENGTH_SHORT).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// Set an alias: formats as "#SET <alias> <phone>"
	def onWtsappSetAlias() {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.wtsapp_alias, null, false)
		val aliasField = view.findViewById(R.id.wtsapp_alias_field).asInstanceOf[EditText]
		val phoneField = view.findViewById(R.id.wtsapp_phone_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.wtsapp_set_alias)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val alias = aliasField.getText().toString.trim
					val phone = phoneField.getText().toString.trim
					if (alias.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_alias, Toast.LENGTH_SHORT).show()
						return
					}
					if (phone.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_phone, Toast.LENGTH_SHORT).show()
						return
					}
					// Format: #SET <alias> <phone>
					val formatted = "#SET %s %s".format(alias, phone)
					sendMessage(formatted)
					Toast.makeText(MessageActivity.this, R.string.wtsapp_alias_set, Toast.LENGTH_SHORT).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// Remove an alias: formats as "#RM <alias>"
	def onWtsappRemoveAlias() {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.wtsapp_remove, null, false)
		val aliasField = view.findViewById(R.id.wtsapp_remove_alias_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.wtsapp_remove_alias)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val alias = aliasField.getText().toString.trim
					if (alias.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_alias, Toast.LENGTH_SHORT).show()
						return
					}
					// Format: #RM <alias>
					val formatted = "#RM %s".format(alias)
					sendMessage(formatted)
					Toast.makeText(MessageActivity.this, R.string.wtsapp_alias_removed, Toast.LENGTH_SHORT).show()
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
