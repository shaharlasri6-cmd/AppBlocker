package com.shahar.appblocker

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordManager(context: Context) {
    private val prefs = context.getSharedPreferences("local_admin_password", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean =
        prefs.contains("salt") && prefs.contains("hash")

    fun setInitial(password: String) {
        require(!hasPassword()) { "Password is already configured" }
        save(password)
    }

    fun verify(password: String): Boolean {
        val saltText = prefs.getString("salt", null) ?: return false
        val hashText = prefs.getString("hash", null) ?: return false
        return try {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = derive(password, salt)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    fun change(oldPassword: String, newPassword: String): Boolean {
        if (!verify(oldPassword)) return false
        save(newPassword)
        return true
    }

    private fun save(password: String) {
        require(password.length >= 6) { "Password must contain at least 6 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 150_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
