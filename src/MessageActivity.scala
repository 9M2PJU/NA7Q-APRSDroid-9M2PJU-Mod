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
import _root_.android.widget.{ArrayAdapter, Button, EditText, LinearLayout, ListView, Spinner, TextView, Toast}
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
	var winlinkBtnForward : Button = null
	var winlinkBtnKill : Button = null

	// WTSAPP UI elements (only inflated when talking to WTSAPP)
	var wtsappButtons : View = null
	var wtsappBtnSend : Button = null
	var wtsappBtnSetAlias : Button = null
	var wtsappBtnRemoveAlias : Button = null

	// 9M2PJU-4 APRS Bot UI elements
	var botButtons : View = null
	var botBtnHelp : Button = null
	var botBtnToday : Button = null
	var botBtnPosmsg : Button = null
	var botBtnWhereis : Button = null
	var botBtnWhereami : Button = null
	var botBtnRiseset : Button = null
	var botBtnSatpass : Button = null
	var botBtnSota : Button = null
	var botBtnPota : Button = null
	var botBtnPolice : Button = null
	var botBtnHospital : Button = null
	var botBtnFireStation : Button = null
	var botBtnProp : Button = null

	// APRSMY UI elements
	var aprsmyButtons : View = null
	var aprsmyBtnCheck : Button = null
	var aprsmyBtnHelp : Button = null
	var aprsmyBtnStatus : Button = null
	var aprsmyBtnCount : Button = null
	var aprsmyBtnLast : Button = null
	var aprsmyBtnTop : Button = null
	var aprsmyBtnMe : Button = null

	// MAILMY UI elements
	var mailmyButtons : View = null
	var mailmyBtnEmail : Button = null
	var mailmyBtnSend : Button = null
	var mailmyBtnSendloc : Button = null
	var mailmyBtnStatus : Button = null
	var mailmyBtnCancel : Button = null
	var mailmyBtnHelp : Button = null

	// CALLMY UI elements
	var callmyButtons : View = null
	var callmyBtnCallsign : Button = null
	var callmyBtnHelp : Button = null

	// BBSMY UI elements
	var bbsmyButtons : View = null
	var bbsmyBtnList : Button = null
	var bbsmyBtnMsg : Button = null
	var bbsmyBtnPost : Button = null
	var bbsmyBtnSend : Button = null
	var bbsmyBtnPosturgent : Button = null
	var bbsmyBtnHelp : Button = null

	// REPEAT UI elements
	var repeatButtons : View = null
	var repeatBtnNearest : Button = null
	var repeatBtnHelp : Button = null

	// GAMEMY UI elements
	var gamemyButtons : View = null
	var gamemyBtnStart : Button = null
	var gamemyBtnHint : Button = null
	var gamemyBtnSkip : Button = null
	var gamemyBtnScore : Button = null
	var gamemyBtnTop : Button = null
	var gamemyBtnStop : Button = null
	var gamemyBtnHelp : Button = null

	// Broadcast receiver for live Winlink status updates
	var winlinkStatusReceiver : BroadcastReceiver = null

	def isWinlinkConversation = targetcall != null &&
		(targetcall.equalsIgnoreCase("WLNK-1") || targetcall.equalsIgnoreCase("WLNK"))

	def isWtsappConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("WTSAPP")

	def isBotConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("9M2PJU-4")

	def isAprsmyConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("APRSMY")

	def isMailmyConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("MAILMY")

	def isCallmyConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("CALLMY")

	def isBbsmyConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("BBSMY")

	def isRepeatConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("REPEAT")

	def isGamemyConversation = targetcall != null &&
		targetcall.equalsIgnoreCase("GAMEMY")

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
		// Inflate APRS Bot buttons if this is a 9M2PJU-4 conversation
		if (isBotConversation) {
			setupBotUI()
		}
		// Inflate APRSMY buttons if this is an APRSMY conversation
		if (isAprsmyConversation) {
			setupAprsmyUI()
		}
		// Inflate MAILMY buttons if this is a MAILMY conversation
		if (isMailmyConversation) {
			setupMailmyUI()
		}
		// Inflate CALLMY buttons if this is a CALLMY conversation
		if (isCallmyConversation) {
			setupCallmyUI()
		}
		// Inflate BBSMY buttons if this is a BBSMY conversation
		if (isBbsmyConversation) {
			setupBbsmyUI()
		}
		// Inflate REPEAT buttons if this is a REPEAT conversation
		if (isRepeatConversation) {
			setupRepeatUI()
		}
		// Inflate GAMEMY buttons if this is a GAMEMY conversation
		if (isGamemyConversation) {
			setupGamemyUI()
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
		winlinkBtnForward = winlinkButtons.findViewById(R.id.winlink_btn_forward).asInstanceOf[Button]
		winlinkBtnKill = winlinkButtons.findViewById(R.id.winlink_btn_kill).asInstanceOf[Button]

		winlinkBtnLogin.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkLogin() })
		winlinkBtnLogout.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkLogout() })
		winlinkBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkHelp() })
		winlinkBtnList.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkList() })
		winlinkBtnRead.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkRead() })
		winlinkBtnReply.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkReply() })
		winlinkBtnCompose.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkCompose() })
		winlinkBtnForward.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkForward() })
		winlinkBtnKill.setOnClickListener(new OnClickListener { override def onClick(v : View) = onWinlinkKill() })

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
		winlinkBtnForward.setEnabled(logged_in)
		winlinkBtnKill.setEnabled(logged_in)
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
				Toast.makeText(this, R.string.winlink_login_started, Toast.LENGTH_LONG).show()
			case None =>
				Toast.makeText(this, R.string.winlink_not_logged_in, Toast.LENGTH_LONG).show()
		}
		updateWinlinkStatus()
	}

	def onWinlinkLogout() {
		requireWinlinkService { ws =>
			ws.logout()
			Toast.makeText(this, R.string.winlink_logged_out, Toast.LENGTH_LONG).show()
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
						Toast.makeText(MessageActivity.this, R.string.winlink_email_failed, Toast.LENGTH_LONG).show()
						return
					}
					requireWinlinkService { ws =>
						ws.sendEmail(to, subject, body)
						Toast.makeText(MessageActivity.this, R.string.winlink_sending, Toast.LENGTH_LONG).show()
					}
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
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
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_recipient, Toast.LENGTH_LONG).show()
						return
					}
					if (message.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_message, Toast.LENGTH_LONG).show()
						return
					}
					// Format: @<recipient> <message>
					val formatted = "@%s %s".format(recipient, message)
					sendMessage(formatted)
					Toast.makeText(MessageActivity.this, R.string.wtsapp_sent, Toast.LENGTH_LONG).show()
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
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_alias, Toast.LENGTH_LONG).show()
						return
					}
					if (phone.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_phone, Toast.LENGTH_LONG).show()
						return
					}
					// Format: #SET <alias> <phone>
					val formatted = "#SET %s %s".format(alias, phone)
					sendMessage(formatted)
					Toast.makeText(MessageActivity.this, R.string.wtsapp_alias_set, Toast.LENGTH_LONG).show()
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
						Toast.makeText(MessageActivity.this, R.string.wtsapp_no_alias, Toast.LENGTH_LONG).show()
						return
					}
					// Format: #RM <alias>
					val formatted = "#RM %s".format(alias)
					sendMessage(formatted)
					Toast.makeText(MessageActivity.this, R.string.wtsapp_alias_removed, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== 9M2PJU-4 APRS Bot =====

	def setupBotUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		botButtons = inflater.inflate(R.layout.bot_buttons, null, false)
		botBtnHelp = botButtons.findViewById(R.id.bot_btn_help).asInstanceOf[Button]
		botBtnToday = botButtons.findViewById(R.id.bot_btn_today).asInstanceOf[Button]
		botBtnPosmsg = botButtons.findViewById(R.id.bot_btn_posmsg).asInstanceOf[Button]
		botBtnWhereis = botButtons.findViewById(R.id.bot_btn_whereis).asInstanceOf[Button]
		botBtnWhereami = botButtons.findViewById(R.id.bot_btn_whereami).asInstanceOf[Button]
		botBtnRiseset = botButtons.findViewById(R.id.bot_btn_riseset).asInstanceOf[Button]
		botBtnSatpass = botButtons.findViewById(R.id.bot_btn_satpass).asInstanceOf[Button]
		botBtnSota = botButtons.findViewById(R.id.bot_btn_sota).asInstanceOf[Button]
		botBtnPota = botButtons.findViewById(R.id.bot_btn_pota).asInstanceOf[Button]
		botBtnPolice = botButtons.findViewById(R.id.bot_btn_police).asInstanceOf[Button]
		botBtnHospital = botButtons.findViewById(R.id.bot_btn_hospital).asInstanceOf[Button]
		botBtnFireStation = botButtons.findViewById(R.id.bot_btn_fire_station).asInstanceOf[Button]
		botBtnProp = botButtons.findViewById(R.id.bot_btn_prop).asInstanceOf[Button]

		botBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("help") })
		botBtnToday.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("today") })
		botBtnPosmsg.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotPosmsg() })
		botBtnWhereis.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotWhereis() })
		botBtnWhereami.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("whereami") })
		botBtnRiseset.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("riseset") })
		botBtnSatpass.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotSatpass() })
		botBtnSota.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotSotaOrPota("sota", R.string.bot_sota_or_alerts) })
		botBtnPota.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotSotaOrPota("pota", R.string.bot_pota_or_alerts) })
		botBtnPolice.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("police") })
		botBtnHospital.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("hospital") })
		botBtnFireStation.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("fire_station") })
		botBtnProp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBotCommand("prop") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(botButtons, 0)
	}

	// Send a simple no-argument command to the bot
	def onBotCommand(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.bot_sent, Toast.LENGTH_LONG).show()
	}

	// posmsg [email]
	def onBotPosmsg() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.bot_posmsg, null, false)
		val emailField = view.findViewById(R.id.bot_posmsg_email_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.bot_posmsg)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val email = emailField.getText().toString.trim
					if (email.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bot_no_email, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("posmsg %s".format(email))
					Toast.makeText(MessageActivity.this, R.string.bot_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// whereis [callsign]
	def onBotWhereis() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.bot_whereis, null, false)
		val callField = view.findViewById(R.id.bot_whereis_callsign_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.bot_whereis)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val call = callField.getText().toString.trim
					if (call.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bot_no_callsign, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("whereis %s".format(call))
					Toast.makeText(MessageActivity.this, R.string.bot_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// satpass [name]
	def onBotSatpass() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.bot_satpass, null, false)
		val nameField = view.findViewById(R.id.bot_satpass_name_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.bot_satpass)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val name = nameField.getText().toString.trim
					if (name.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bot_no_satname, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("satpass %s".format(name))
					Toast.makeText(MessageActivity.this, R.string.bot_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// sota/pota -- choose spots or alerts
	def onBotSotaOrPota(command : String, titleId : Int) {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		new AlertDialog.Builder(this)
			.setTitle(titleId)
			.setItems(Array[CharSequence](
				getString(R.string.bot_spots),
				getString(R.string.bot_alerts)
			), new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val sub = which match {
						case 0 => "spots"
						case 1 => "alerts"
						case _ => "spots"
					}
					sendMessage("%s %s".format(command, sub))
					Toast.makeText(MessageActivity.this, R.string.bot_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== APRSMY =====

	def setupAprsmyUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
				.asInstanceOf[LayoutInflater]
		aprsmyButtons = inflater.inflate(R.layout.aprsmy_buttons, null, false)
		aprsmyBtnCheck = aprsmyButtons.findViewById(R.id.aprsmy_btn_check).asInstanceOf[Button]
		aprsmyBtnHelp = aprsmyButtons.findViewById(R.id.aprsmy_btn_help).asInstanceOf[Button]
		aprsmyBtnStatus = aprsmyButtons.findViewById(R.id.aprsmy_btn_status).asInstanceOf[Button]
		aprsmyBtnCount = aprsmyButtons.findViewById(R.id.aprsmy_btn_count).asInstanceOf[Button]
		aprsmyBtnLast = aprsmyButtons.findViewById(R.id.aprsmy_btn_last).asInstanceOf[Button]
		aprsmyBtnTop = aprsmyButtons.findViewById(R.id.aprsmy_btn_top).asInstanceOf[Button]
		aprsmyBtnMe = aprsmyButtons.findViewById(R.id.aprsmy_btn_me).asInstanceOf[Button]

		aprsmyBtnCheck.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("CHECK") })
		aprsmyBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("HELP") })
		aprsmyBtnStatus.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("STATUS") })
		aprsmyBtnCount.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("COUNT") })
		aprsmyBtnLast.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("LAST") })
		aprsmyBtnTop.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("TOP") })
		aprsmyBtnMe.setOnClickListener(new OnClickListener { override def onClick(v : View) = onAprsmyCommand("ME") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(aprsmyButtons, 0)
	}

	def onAprsmyCommand(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.aprsmy_sent, Toast.LENGTH_LONG).show()
	}

	// ===== MAILMY =====

	def setupMailmyUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		mailmyButtons = inflater.inflate(R.layout.mailmy_buttons, null, false)
		mailmyBtnEmail = mailmyButtons.findViewById(R.id.mailmy_btn_email).asInstanceOf[Button]
		mailmyBtnSend = mailmyButtons.findViewById(R.id.mailmy_btn_send).asInstanceOf[Button]
		mailmyBtnSendloc = mailmyButtons.findViewById(R.id.mailmy_btn_sendloc).asInstanceOf[Button]
		mailmyBtnStatus = mailmyButtons.findViewById(R.id.mailmy_btn_status).asInstanceOf[Button]
		mailmyBtnCancel = mailmyButtons.findViewById(R.id.mailmy_btn_cancel).asInstanceOf[Button]
		mailmyBtnHelp = mailmyButtons.findViewById(R.id.mailmy_btn_help).asInstanceOf[Button]

		mailmyBtnEmail.setOnClickListener(new OnClickListener { override def onClick(v : View) = onMailmyEmail() })
		mailmyBtnSend.setOnClickListener(new OnClickListener { override def onClick(v : View) = onMailmySimple("SEND") })
		mailmyBtnSendloc.setOnClickListener(new OnClickListener { override def onClick(v : View) = onMailmySendloc() })
		mailmyBtnStatus.setOnClickListener(new OnClickListener { override def onClick(v : View) = onMailmySimple("STATUS") })
		mailmyBtnCancel.setOnClickListener(new OnClickListener { override def onClick(v : View) = onMailmySimple("CANCEL") })
		mailmyBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onMailmySimple("HELP") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(mailmyButtons, 0)
	}

	def onMailmySimple(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.mailmy_sent, Toast.LENGTH_LONG).show()
	}

	// email <addr> <msg> -- start draft
	def onMailmyEmail() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.mailmy_email, null, false)
		val addrField = view.findViewById(R.id.mailmy_email_addr_field).asInstanceOf[EditText]
		val msgField = view.findViewById(R.id.mailmy_email_msg_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.mailmy_email)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val addr = addrField.getText().toString.trim
					val msg = msgField.getText().toString.trim
					if (addr.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.mailmy_no_addr, Toast.LENGTH_LONG).show()
						return
					}
					if (msg.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.mailmy_no_msg, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("email %s %s".format(addr, msg))
					Toast.makeText(MessageActivity.this, R.string.mailmy_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// SENDLOC <addr> -- email your position
	def onMailmySendloc() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.mailmy_sendloc, null, false)
		val addrField = view.findViewById(R.id.mailmy_sendloc_addr_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.mailmy_sendloc)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val addr = addrField.getText().toString.trim
					if (addr.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.mailmy_no_addr, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("SENDLOC %s".format(addr))
					Toast.makeText(MessageActivity.this, R.string.mailmy_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== CALLMY =====

	def setupCallmyUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		callmyButtons = inflater.inflate(R.layout.callmy_buttons, null, false)
		callmyBtnCallsign = callmyButtons.findViewById(R.id.callmy_btn_callsign).asInstanceOf[Button]
		callmyBtnHelp = callmyButtons.findViewById(R.id.callmy_btn_help).asInstanceOf[Button]

		callmyBtnCallsign.setOnClickListener(new OnClickListener { override def onClick(v : View) = onCallmyCallsign() })
		callmyBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onCallmyCommand("HELP") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(callmyButtons, 0)
	}

	def onCallmyCommand(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.callmy_sent, Toast.LENGTH_LONG).show()
	}

	// callsign <CALL>
	def onCallmyCallsign() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.callmy_callsign, null, false)
		val callField = view.findViewById(R.id.callmy_callsign_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.callmy_callsign)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val call = callField.getText().toString.trim
					if (call.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.callmy_no_callsign, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("callsign %s".format(call))
					Toast.makeText(MessageActivity.this, R.string.callmy_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== BBSMY =====

	def setupBbsmyUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		bbsmyButtons = inflater.inflate(R.layout.bbsmy_buttons, null, false)
		bbsmyBtnList = bbsmyButtons.findViewById(R.id.bbsmy_btn_list).asInstanceOf[Button]
		bbsmyBtnMsg = bbsmyButtons.findViewById(R.id.bbsmy_btn_msg).asInstanceOf[Button]
		bbsmyBtnPost = bbsmyButtons.findViewById(R.id.bbsmy_btn_post).asInstanceOf[Button]
		bbsmyBtnSend = bbsmyButtons.findViewById(R.id.bbsmy_btn_send).asInstanceOf[Button]
		bbsmyBtnPosturgent = bbsmyButtons.findViewById(R.id.bbsmy_btn_posturgent).asInstanceOf[Button]
		bbsmyBtnHelp = bbsmyButtons.findViewById(R.id.bbsmy_btn_help).asInstanceOf[Button]

		bbsmyBtnList.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBbsmyCommand("L") })
		bbsmyBtnMsg.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBbsmyCommand("M") })
		bbsmyBtnPost.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBbsmyPost() })
		bbsmyBtnSend.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBbsmySend() })
		bbsmyBtnPosturgent.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBbsmyPostUrgent() })
		bbsmyBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onBbsmyCommand("H") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(bbsmyButtons, 0)
	}

	def onBbsmyCommand(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.bbsmy_sent, Toast.LENGTH_LONG).show()
	}

	// POST <text>
	def onBbsmyPost() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.bbsmy_post, null, false)
		val textField = view.findViewById(R.id.bbsmy_post_text_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.bbsmy_post)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val text = textField.getText().toString.trim
					if (text.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bbsmy_no_text, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("P %s".format(text))
					Toast.makeText(MessageActivity.this, R.string.bbsmy_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// POST urgent: PU <text>
	def onBbsmyPostUrgent() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.bbsmy_post, null, false)
		val textField = view.findViewById(R.id.bbsmy_post_text_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.bbsmy_posturgent)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val text = textField.getText().toString.trim
					if (text.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bbsmy_no_text, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("PU %s".format(text))
					Toast.makeText(MessageActivity.this, R.string.bbsmy_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// SEND <callsign> <text>
	def onBbsmySend() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.bbsmy_send, null, false)
		val callField = view.findViewById(R.id.bbsmy_send_callsign_field).asInstanceOf[EditText]
		val textField = view.findViewById(R.id.bbsmy_send_text_field).asInstanceOf[EditText]

		new AlertDialog.Builder(this)
			.setTitle(R.string.bbsmy_send)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val call = callField.getText().toString.trim
					val text = textField.getText().toString.trim
					if (call.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bbsmy_no_callsign, Toast.LENGTH_LONG).show()
						return
					}
					if (text.isEmpty) {
						Toast.makeText(MessageActivity.this, R.string.bbsmy_no_text, Toast.LENGTH_LONG).show()
						return
					}
					sendMessage("S %s %s".format(call, text))
					Toast.makeText(MessageActivity.this, R.string.bbsmy_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== REPEAT (Repeater lookup) =====

	def setupRepeatUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		repeatButtons = inflater.inflate(R.layout.repeat_buttons, null, false)
		repeatBtnNearest = repeatButtons.findViewById(R.id.repeat_btn_nearest).asInstanceOf[Button]
		repeatBtnHelp = repeatButtons.findViewById(R.id.repeat_btn_help).asInstanceOf[Button]

		repeatBtnNearest.setOnClickListener(new OnClickListener { override def onClick(v : View) = onRepeatNearest() })
		repeatBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onRepeatCommand("help") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(repeatButtons, 0)
	}

	def onRepeatCommand(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.repeat_sent, Toast.LENGTH_LONG).show()
	}

	// n [Num] [Band] [+Filter]
	def onRepeatNearest() {
		if (!AprsService.running) { showStartTrackingDialog(); return }
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		val view = inflater.inflate(R.layout.repeat_dialog, null, false)
		val numField = view.findViewById(R.id.repeat_num_field).asInstanceOf[EditText]
		val bandSpinner = view.findViewById(R.id.repeat_band_spinner).asInstanceOf[Spinner]
		val filtersField = view.findViewById(R.id.repeat_filters_field).asInstanceOf[EditText]

		// Band options -- default 2m (index 0)
		val bands = Array[CharSequence](
			getString(R.string.repeat_band_2m),
			getString(R.string.repeat_band_70cm),
			getString(R.string.repeat_band_6m),
			getString(R.string.repeat_band_10m),
			getString(R.string.repeat_band_1_25m),
			getString(R.string.repeat_band_33cm),
			getString(R.string.repeat_band_23cm)
		)
		val adapter = new ArrayAdapter[CharSequence](this,
			android.R.layout.simple_spinner_item, bands)
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		bandSpinner.setAdapter(adapter)

		new AlertDialog.Builder(this)
			.setTitle(R.string.repeat_nearest)
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					val numStr = numField.getText().toString.trim
					val band = bands(bandSpinner.getSelectedItemPosition).toString
					val filters = filtersField.getText().toString.trim

					// Build command: n [Num] [Band] [+Filter]
					val sb = new StringBuilder("n")
					if (!numStr.isEmpty) {
						try {
							val n = numStr.toInt
							if (n < 1 || n > 10) {
								Toast.makeText(MessageActivity.this, R.string.repeat_no_num,
									Toast.LENGTH_LONG).show()
								return
							}
							sb.append(" ").append(n)
						} catch {
							case e : Exception =>
								Toast.makeText(MessageActivity.this, R.string.repeat_no_num,
									Toast.LENGTH_LONG).show()
								return
						}
					}
					// Only append band if not default 2m (or if num was specified)
					if (band != "2m") {
						sb.append(" ").append(band)
					} else if (!numStr.isEmpty) {
						// num specified + default band -- still append band for clarity
						sb.append(" ").append(band)
					}
					if (!filters.isEmpty) {
						sb.append(" ").append(filters)
					}
					sendMessage(sb.toString)
					Toast.makeText(MessageActivity.this, R.string.repeat_sent, Toast.LENGTH_LONG).show()
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// ===== GAMEMY =====

	def setupGamemyUI() {
		val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
		gamemyButtons = inflater.inflate(R.layout.gamemy_buttons, null, false)
		gamemyBtnStart = gamemyButtons.findViewById(R.id.gamemy_btn_start).asInstanceOf[Button]
		gamemyBtnHint = gamemyButtons.findViewById(R.id.gamemy_btn_hint).asInstanceOf[Button]
		gamemyBtnSkip = gamemyButtons.findViewById(R.id.gamemy_btn_skip).asInstanceOf[Button]
		gamemyBtnScore = gamemyButtons.findViewById(R.id.gamemy_btn_score).asInstanceOf[Button]
		gamemyBtnTop = gamemyButtons.findViewById(R.id.gamemy_btn_top).asInstanceOf[Button]
		gamemyBtnStop = gamemyButtons.findViewById(R.id.gamemy_btn_stop).asInstanceOf[Button]
		gamemyBtnHelp = gamemyButtons.findViewById(R.id.gamemy_btn_help).asInstanceOf[Button]

		gamemyBtnStart.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("S") })
		gamemyBtnHint.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("H") })
		gamemyBtnSkip.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("K") })
		gamemyBtnScore.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("C") })
		gamemyBtnTop.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("T") })
		gamemyBtnStop.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("P") })
		gamemyBtnHelp.setOnClickListener(new OnClickListener { override def onClick(v : View) = onGamemyCommand("?") })

		val root = findViewById(R.id.message_act).asInstanceOf[LinearLayout]
		root.addView(gamemyButtons, 0)
	}

	def onGamemyCommand(command : String) {
		if (!AprsService.running) {
			showStartTrackingDialog()
			return
		}
		sendMessage(command)
		Toast.makeText(this, R.string.gamemy_sent, Toast.LENGTH_LONG).show()
	}

	override def onResume() {
		super.onResume()
		ServiceNotifier.instance.cancelMessage(this, targetcall)
		if (isWinlinkConversation) {
			updateWinlinkStatus()
			// Set up live status callback so UI updates when state changes
			getWinlinkService match {
				case Some(ws) => ws.statusCallback = (state : Int) => runOnUiThread(new Runnable {
					override def run() : Unit = updateWinlinkStatus()
				})
				case None =>
			}
			// Register receiver for MSG_PRIV broadcasts (new messages / state changes)
			winlinkStatusReceiver = new BroadcastReceiver {
				override def onReceive(context : Context, intent : Intent) {
					runOnUiThread(new Runnable {
						override def run() : Unit = updateWinlinkStatus()
					})
				}
			}
			UIHelper.safeRegisterReceiver(this, winlinkStatusReceiver, new IntentFilter(AprsService.MESSAGE))
		}
	}

	override def onPause() {
		super.onPause()
		// Clear status callback so we don't update a dead UI
		getWinlinkService match {
			case Some(ws) => ws.statusCallback = null
			case None =>
		}
		// Unregister receiver
		if (winlinkStatusReceiver != null) {
			scala.util.control.Exception.ignoring(classOf[IllegalArgumentException]) {
				unregisterReceiver(winlinkStatusReceiver)
			}
			winlinkStatusReceiver = null
		}
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
