package com.abel.theftguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Commands include the PIN and may come from any phone (or only the saved controller number, if one is set):
 *   /find PIN [message]  -> alarm + torch flash + lost screen + location SMS (every minute, up to 30)
 *   /where PIN           -> just reply with the location, no noise
 *   /stop PIN            -> stop everything
 * Wrong PINs are ignored silently (no reply) and rate-limited.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val sender = msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }.trim()
        if (!body.startsWith("/")) return

        val parts = body.split(Regex("\\s+"), limit = 3)
        val cmd = parts[0].lowercase()
        if (cmd != GuardService.ACTION_FIND && cmd != GuardService.ACTION_WHERE && cmd != GuardService.ACTION_STOP) return
        if (!Prefs.isAuthorizedSender(context, sender)) return
        if (!Prefs.hasPin(context)) return
        if (Prefs.isLockedOut(context, sender)) return

        val pin = parts.getOrNull(1) ?: return
        if (!Prefs.checkPin(context, pin)) {
            Prefs.recordFailure(context, sender)
            return
        }
        Prefs.clearFailures(context)

        val svc = Intent(context, GuardService::class.java)
            .setAction(cmd)
            .putExtra(GuardService.EXTRA_SENDER, sender)
            .putExtra(GuardService.EXTRA_MESSAGE, parts.getOrNull(2))
        try {
            ContextCompat.startForegroundService(context, svc)
        } catch (e: Exception) {
            // Android 12+ can block starting a foreground service from the background.
            // Granting "draw over apps" + battery-optimization exemption in the app avoids this.
            Log.e(GuardService.TAG, "Could not start service", e)
        }
    }
}
