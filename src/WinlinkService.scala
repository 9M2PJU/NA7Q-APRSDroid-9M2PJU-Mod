package org.aprsdroid.app

import _root_.android.content.{Context, ContentValues, Intent}
import _root_.android.util.Log
import _root_.android.widget.Toast

import _root_.net.ab0oo.aprs.parser.MessagePacket
import scala.collection.mutable

/**
 * WinlinkService — manages the APRSLink (WLNK-1) session state machine.
 *
 * APRSLink lets APRS users send/receive Winlink email by sending APRS
 * messages addressed to WLNK-1. The protocol is documented at
 * https://winlink.org/APRSLink
 *
 * Session flow:
 *   1. User taps "Login" → we send "Start" to WLNK-1
 *   2. WLNK-1 responds with "Login [XXX]:" (3 digits = password positions)
 *   3. We compute the 6-char response (3 password chars + 3 random, shuffled)
 *      and send it
 *   4. WLNK-1 responds "Hello CALLSIGN" → logged in
 *   5. User can then: L (list), R# (read), SP (send), K# (kill), B (logout)
 *
 * Each APRS message is max 67 chars. Email bodies are sent as multiple
 * lines, terminated by /EX.
 *
 * This service is a singleton tied to AprsService lifecycle. It hooks into
 * MessageService.handleMessage() — when a message arrives from WLNK-1,
 * it's routed here for protocol parsing instead of normal APRS ACK handling.
 */
object WinlinkService {
	private val TAG = "APRSdroid.Winlink"

	// Winlink destination callsign
	val WLNK_CALL = "WLNK-1"

	// Session states
	val STATE_LOGGED_OUT    = 0
	val STATE_LOGIN_STARTED = 1  // sent "Start", waiting for challenge
	val STATE_CHALLENGE     = 2  // received challenge, sent response, waiting for Hello
	val STATE_LOGGED_IN     = 3
	val STATE_ERROR         = 4

	// Email composition state (for multi-line SP command)
	val COMPOSE_IDLE        = 0
	val COMPOSE_SP_SENT     = 1  // SP line sent, sending body lines
	val COMPOSE_DONE        = 2  // /EX sent, waiting for confirmation

	// Session timeout: 2 hours (per Winlink spec)
	val SESSION_TIMEOUT_MS = 2L * 60 * 60 * 1000
}

class WinlinkService(s : AprsService) {
	import WinlinkService._

	val TAG = WinlinkService.TAG

	private var state : Int = STATE_LOGGED_OUT
	private var composeState : Int = COMPOSE_IDLE
	private var loginTime : Long = 0
	private var lastChallengeTime : Long = 0
	private var challengeAnswer : String = ""

	// Pending email body lines to send (for multi-line SP command)
	private val pendingBodyLines = mutable.Queue.empty[String]
	private var composeSubject : String = ""
	private var composeTo : String = ""

	// Parsed message list from "L" command: Map(number -> "From: ... Subj: ...")
	private val messageList = mutable.Map.empty[Int, String]

	// Buffer for multi-line message content being received
	private val readBuffer = new StringBuilder()
	private var readMessageNum : Int = -1

	// Callback to update UI (set by MessageActivity when it's active)
	@volatile var statusCallback : (Int => Unit) = null

	def getState = state
	def isLoggedIn = state == STATE_LOGGED_IN
	def isComposing = composeState != COMPOSE_IDLE

	private def setState(newState : Int) {
		Log.d(TAG, "state %d -> %d".format(state, newState))
		state = newState
		if (statusCallback != null) {
			try { statusCallback(newState) } catch { case _ : Throwable => }
		}
	}

	// Send a message to WLNK-1 via the APRS pipeline.
	// Stores it in the DB as TYPE_WINLINK_OUT and triggers TX.
	private def sendToWlnk(text : String) {
		Log.d(TAG, "TX to WLNK-1: " + text)
		s.db.addWinlinkMessage(WLNK_CALL, text,
			StorageDatabase.Message.TYPE_WINLINK_OUT, null)
		val msg = s.newPacket(new MessagePacket(WLNK_CALL, text, ""))
		s.sendPacket(msg)
		// Notify UI
		s.sendBroadcast(AprsService.MSG_PRIV_INTENT)
	}

	// Store an incoming message from WLNK-1 in the DB
	private def storeFromWlnk(text : String, subject : String) {
		Log.d(TAG, "RX from WLNK-1: " + text)
		s.db.addWinlinkMessage(WLNK_CALL, text,
			StorageDatabase.Message.TYPE_WINLINK_IN, subject)
		s.sendBroadcast(AprsService.MSG_PRIV_INTENT)
	}

	/**
	 * Initiate Winlink login by sending "Start" to WLNK-1.
	 * Called when user taps the Login button.
	 */
	def login() {
		val password = s.prefs.getWinlinkPassword()
		val callsign = s.prefs.getCallsign()
		if (callsign.isEmpty) {
			Log.w(TAG, "no callsign set")
			return
		}
		if (password.isEmpty) {
			Log.w(TAG, "no Winlink password set")
			return
		}
		if (!AprsService.running) {
			Log.w(TAG, "APRS service not running")
			return
		}
		Log.i(TAG, "initiating Winlink login")
		setState(STATE_LOGIN_STARTED)
		sendToWlnk("Start")
	}

	/**
	 * Logout from Winlink by sending "B" to WLNK-1.
	 */
	def logout() {
		if (state != STATE_LOGGED_OUT) {
			sendToWlnk("B")
			setState(STATE_LOGGED_OUT)
			composeState = COMPOSE_IDLE
			pendingBodyLines.clear()
		}
	}

	/**
	 * Request the list of pending messages (max 5).
	 */
	def listMessages() {
		if (state != STATE_LOGGED_IN) return
		messageList.clear()
		sendToWlnk("L")
	}

	/**
	 * Read a specific message by list number.
	 */
	def readMessage(num : Int) {
		if (state != STATE_LOGGED_IN) return
		readBuffer.clear()
		readMessageNum = num
		sendToWlnk("R%d".format(num))
	}

	/**
	 * Delete (kill) a message by list number.
	 */
	def killMessage(num : Int) {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("K%d".format(num))
	}

	/**
	 * Reply to a message by list number.
	 */
	def replyMessage(num : Int) {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("Y%d".format(num))
	}

	/**
	 * Request help (H or ? command).
	 */
	def help() {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("H")
	}

	/**
	 * Playback message lines being written (P command).
	 */
	def playback() {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("P")
	}

	/**
	 * Send a short one-line message (SMS command).
	 * Format: SMS [email/callsign/alias] [message]
	 */
	def sendSMS(to : String, message : String) {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("SMS %s %s".format(to, message))
	}

	/**
	 * Create or update an alias (A command).
	 * Format: A [alias]=[email]
	 * If email is empty, deletes the alias.
	 */
	def setAlias(alias : String, email : String) {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("A %s=%s".format(alias, email))
	}

	/**
	 * List all aliases (AL command).
	 */
	def listAliases() {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("AL")
	}

	/**
	 * Forward a message to another address (F# command).
	 * Format: F# [email/callsign]
	 */
	def forwardMessage(num : Int, to : String) {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("F%d %s".format(num, to))
	}

	/**
	 * Request nearby RMS packet gateways (G# command).
	 * count is the number of gateways to return (default 1).
	 */
	def gatewayInfo(count : Int) {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("G%d".format(count))
	}

	/**
	 * Request APRSLink information (I command).
	 */
	def info() {
		if (state != STATE_LOGGED_IN) return
		sendToWlnk("I")
	}

	/**
	 * Compose and send a Winlink email.
	 * Splits the body into ≤67-char lines, sends SP line first, then body
	 * lines, then /EX.
	 *
	 * Each line is sent as a separate APRS message to WLNK-1.
	 */
	def sendEmail(to : String, subject : String, body : String) {
		if (state != STATE_LOGGED_IN) {
			Log.w(TAG, "cannot send email: not logged in")
			return
		}
		Log.i(TAG, "composing email to %s: %s".format(to, subject))
		composeTo = to
		composeSubject = subject
		composeState = COMPOSE_SP_SENT

		// Send SP line: "SP <to> <subject>" (must fit in 67 chars)
		val spLine = "SP %s %s".format(to, subject)
		sendToWlnk(spLine)

		// Queue body lines (max 67 chars each)
		pendingBodyLines.clear()
		for (line <- body.split("\n", -1)) {
			var remaining = line
			while (remaining.length > 67) {
				pendingBodyLines.enqueue(remaining.substring(0, 67))
				remaining = remaining.substring(67)
			}
			pendingBodyLines.enqueue(remaining)
		}

		// Send body lines with a small delay between each to avoid
		// overwhelming the APRS pipeline. The /EX terminator is sent
		// after all body lines.
		sendNextBodyLine()
	}

	// Send the next queued body line, or /EX if queue is empty.
	private def sendNextBodyLine() {
		if (pendingBodyLines.nonEmpty) {
			val line = pendingBodyLines.dequeue()
			sendToWlnk(line)
			// Schedule next line after 500ms
			s.handler.postDelayed(new Runnable {
				override def run() : Unit = sendNextBodyLine()
			}, 500)
		} else {
			// Send /EX to complete the message
			sendToWlnk("/EX")
			composeState = COMPOSE_DONE
		}
	}

	/**
	 * Handle an incoming APRS message from WLNK-1.
	 * Called from MessageService.handleMessage() when the source is WLNK-1.
	 *
	 * Parses the response based on current state and updates the state machine.
	 * Returns true if the message was handled (so MessageService skips normal
	 * APRS ACK processing), false otherwise.
	 */
	def handleIncoming(text : String) : Boolean = {
		Log.d(TAG, "handleIncoming state=%d text=%s".format(state, text))

		// Check for session timeout
		if (state == STATE_LOGGED_IN && loginTime > 0 &&
		    (System.currentTimeMillis - loginTime) > SESSION_TIMEOUT_MS) {
			Log.i(TAG, "session timed out")
			setState(STATE_LOGGED_OUT)
		}

		state match {
		case STATE_LOGIN_STARTED =>
			// Expecting "Login [XXX]:" challenge
			if (text.startsWith("Login [") || text.matches("(?i)login.*\\[\\d+\\].*")) {
				handleChallenge(text)
				true
			} else if (text.toLowerCase.startsWith("hello")) {
				// Some servers may skip challenge if secure login not enabled
				handleLoginSuccess(text)
				true
			} else {
				// Unexpected response, store it
				storeFromWlnk(text, null)
				true
			}

		case STATE_CHALLENGE =>
			// Expecting "Hello CALLSIGN" confirmation
			if (text.toLowerCase.startsWith("hello")) {
				handleLoginSuccess(text)
				true
			} else if (text.toLowerCase.contains("invalid") ||
				   text.toLowerCase.contains("incorrect") ||
				   text.toLowerCase.contains("failed")) {
				Log.w(TAG, "login failed: " + text)
				setState(STATE_ERROR)
				storeFromWlnk(text, null)
				true
			} else {
				// Store unexpected responses
				storeFromWlnk(text, null)
				true
			}

		case STATE_LOGGED_IN =>
			handleCommandResponse(text)
			true

		case STATE_ERROR =>
			// Store any responses, allow re-login attempt
			storeFromWlnk(text, null)
			true

		case _ =>
			// Not in a login flow — could be an unsolicited notification
			// (e.g. "you have unread mail"). Store it.
			storeFromWlnk(text, null)
			true
		}
	}

	/**
	 * Parse the challenge ("Login [XXX]:") and send the response.
	 * The 3 digits indicate 1-indexed positions in the password.
	 * Response = those 3 password chars + 3 random chars, all shuffled.
	 */
	private def handleChallenge(text : String) {
		// Extract digits from "Login [XXX]:" or similar
		val digits = text.filter(_.isDigit)
		if (digits.length < 3) {
			Log.w(TAG, "could not parse challenge: " + text)
			setState(STATE_ERROR)
			return
		}

		val password = s.prefs.getWinlinkPassword()
		if (password.isEmpty) {
			Log.w(TAG, "no password set for challenge")
			setState(STATE_ERROR)
			return
		}

		val challengeDigits = digits.substring(0, 3)
		Log.d(TAG, "challenge digits: " + challengeDigits)

		// Build the answer: 3 password chars at indicated positions + 3 random
		val answer = new StringBuilder
		for (c <- challengeDigits) {
			val pos = c - '0'  // 1-indexed position
			if (pos >= 1 && pos <= password.length) {
				answer.append(password.charAt(pos - 1))
			} else {
				// Position out of range — use first char as fallback
				Log.w(TAG, "challenge position %d exceeds password length %d".format(pos, password.length))
				answer.append(password.charAt(0))
			}
		}

		// Add 3 random alphanumeric characters
		val randChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
		for (_ <- 0 until 3) {
			answer.append(randChars.charAt(scala.util.Random.nextInt(randChars.length)))
		}

		// Fisher-Yates shuffle the 6-character answer
		val arr = answer.toString.toCharArray
		for (i <- (arr.length - 1) to 1 by -1) {
			val j = scala.util.Random.nextInt(i + 1)
			val tmp = arr(i)
			arr(i) = arr(j)
			arr(j) = tmp
		}

		challengeAnswer = new String(arr)
		Log.d(TAG, "sending challenge response")
		setState(STATE_CHALLENGE)
		sendToWlnk(challengeAnswer)
	}

	private def handleLoginSuccess(text : String) {
		Log.i(TAG, "login success: " + text)
		loginTime = System.currentTimeMillis()
		setState(STATE_LOGGED_IN)
		storeFromWlnk(text, null)
	}

	/**
	 * Handle a response while in logged-in state.
	 * Parses list entries, message content, confirmations, etc.
	 */
	private def handleCommandResponse(text : String) {
		// Check for logout confirmation
		if (text.toLowerCase.startsWith("bye") || text.toLowerCase.contains("logged off") ||
		    text.toLowerCase.contains("disconnected")) {
			setState(STATE_LOGGED_OUT)
			storeFromWlnk(text, null)
			return
		}

		// Check for email send confirmation
		if (composeState == COMPOSE_DONE) {
			if (text.toLowerCase.contains("sent") || text.toLowerCase.contains("accepted") ||
			    text.toLowerCase.contains("queued") || text.matches("(?i).*message.*has been.*")) {
				composeState = COMPOSE_IDLE
				storeFromWlnk(text, composeSubject)
				return
			}
		}

		// Check for message list entries: "N: From: ... Subj: ..."
		val listPattern = """^(\d+)\s*[:.]\s*(.+)""".r
		text match {
		case listPattern(num, rest) =>
			val n = num.toInt
			messageList(n) = rest
			storeFromWlnk(text, null)
			return
		case _ =>
		}

		// Check for "No messages" or similar
		if (text.toLowerCase.contains("no message") || text.toLowerCase.contains("no pending")) {
			storeFromWlnk(text, null)
			return
		}

		// Default: store as a regular response
		storeFromWlnk(text, null)
	}

	/** Get the current message list (from last "L" command). */
	def getMessageList : Map[Int, String] = messageList.toMap

	/** Reset the service state (e.g. when APRS service stops). */
	def reset() {
		setState(STATE_LOGGED_OUT)
		composeState = COMPOSE_IDLE
		pendingBodyLines.clear()
		messageList.clear()
		readBuffer.clear()
		loginTime = 0
		challengeAnswer = ""
	}
}
