package com.abel.theftguard

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the secret PIN (salted SHA-256, never plaintext), a backup number, and wrong-attempt counters. */
object Prefs {
    private const val MAX_FAILURES = 5
    private const val WINDOW_MS = 10 * 60_000L

    private fun sp(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun hasPin(c: Context) = sp(c).contains("hash")

    fun setPin(c: Context, pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        sp(c).edit().putString("salt", salt).putString("hash", hash(salt, pin)).apply()
        clearFailures(c)
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("salt", null) ?: return false
        val expected = sp(c).getString("hash", null) ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), hash(salt, pin).toByteArray())
    }

    fun backupNumber(c: Context): String? = sp(c).getString("backup", null)?.takeIf { it.isNotBlank() }

    fun setBackupNumber(c: Context, number: String) {
        sp(c).edit().putString("backup", number.trim()).apply()
    }

    fun controllerNumber(c: Context): String? =
        sp(c).getString("controller", null)?.takeIf { it.isNotBlank() }

    fun setControllerNumber(c: Context, number: String) {
        sp(c).edit().putString("controller", number.trim()).apply()
    }

    /** If no controller number is saved, ANY phone may send commands (the PIN is the only gate). */
    fun isAuthorizedSender(c: Context, sender: String?): Boolean {
        val senderDigits = sender?.filter { it.isDigit() } ?: return false
        if (senderDigits.length < 5) return false
        val controllerDigits = controllerNumber(c)?.filter { it.isDigit() } ?: return true
        return controllerDigits.length in 8..15 && controllerDigits == senderDigits
    }

    fun clearControllerNumber(c: Context) {
        sp(c).edit().remove("controller").apply()
    }

    private const val GLOBAL_MAX_FAILURES = 40
    private const val GLOBAL_WINDOW_MS = 60 * 60_000L

    private fun key(sender: String?) = (sender?.filter { it.isDigit() } ?: "x")

    /** Per-sender lockout (5 wrong PINs / 10 min) so a stranger can't lock the owner out,
     *  plus a generous global ceiling (40 wrong PINs / hour) against many-number guessing. */
    fun isLockedOut(c: Context, sender: String?): Boolean {
        val s = sp(c)
        val now = System.currentTimeMillis()
        val k = key(sender)
        val start = s.getLong("fs_$k", 0L)
        if (now - start <= WINDOW_MS && s.getInt("fc_$k", 0) >= MAX_FAILURES) return true
        val gs = s.getLong("g_start", 0L)
        return now - gs <= GLOBAL_WINDOW_MS && s.getInt("g_count", 0) >= GLOBAL_MAX_FAILURES
    }

    fun recordFailure(c: Context, sender: String?) {
        val s = sp(c)
        val now = System.currentTimeMillis()
        val k = key(sender)
        val e = s.edit()
        if (now - s.getLong("fs_$k", 0L) > WINDOW_MS) e.putLong("fs_$k", now).putInt("fc_$k", 1)
        else e.putInt("fc_$k", s.getInt("fc_$k", 0) + 1)
        if (now - s.getLong("g_start", 0L) > GLOBAL_WINDOW_MS) e.putLong("g_start", now).putInt("g_count", 1)
        else e.putInt("g_count", s.getInt("g_count", 0) + 1)
        e.apply()
    }

    fun clearFailures(c: Context) {
        val s = sp(c)
        val e = s.edit()
        s.all.keys.filter { it.startsWith("fs_") || it.startsWith("fc_") || it.startsWith("g_") || it == "fail_start" || it == "fail_count" }
            .forEach { e.remove(it) }
        e.apply()
    }
}
