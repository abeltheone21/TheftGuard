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

    fun isAuthorizedSender(c: Context, sender: String?): Boolean {
        val controllerDigits = controllerNumber(c)?.filter { it.isDigit() } ?: return false
        val senderDigits = sender?.filter { it.isDigit() } ?: return false
        return controllerDigits.length in 8..15 && controllerDigits == senderDigits
    }

    fun isLockedOut(c: Context): Boolean {
        val s = sp(c)
        val start = s.getLong("fail_start", 0L)
        if (System.currentTimeMillis() - start > WINDOW_MS) return false
        return s.getInt("fail_count", 0) >= MAX_FAILURES
    }

    fun recordFailure(c: Context) {
        val s = sp(c)
        val now = System.currentTimeMillis()
        val start = s.getLong("fail_start", 0L)
        if (now - start > WINDOW_MS) {
            s.edit().putLong("fail_start", now).putInt("fail_count", 1).apply()
        } else {
            s.edit().putInt("fail_count", s.getInt("fail_count", 0) + 1).apply()
        }
    }

    fun clearFailures(c: Context) {
        sp(c).edit().remove("fail_start").remove("fail_count").apply()
    }
}
