package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.Account
import com.example.model.HashAlgorithm
import com.example.model.ThemeMode
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

interface SecureVaultStorage {
  fun loadAccounts(): List<Account>
  fun saveAccounts(accounts: List<Account>)
  fun getThemeMode(): ThemeMode
  fun setThemeMode(mode: ThemeMode)
  fun getTimeOffsetSeconds(): Int
  fun setTimeOffsetSeconds(seconds: Int)
  fun isAppLockEnabled(): Boolean
  fun isBiometricEnabled(): Boolean
  fun setBiometricEnabled(enabled: Boolean)
  fun setPin(pin: String)
  fun verifyPin(pin: String): Boolean
  fun clearAppLock()
}

class KeystoreSecureStorage(context: Context) : SecureVaultStorage {

  private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

  override fun loadAccounts(): List<Account> {
    val rawJson = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
    return runCatching {
      val array = JSONArray(rawJson)
      buildList(array.length()) {
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          add(
            Account(
              id = obj.getString("id"),
              serviceName = obj.getString("serviceName"),
              username = obj.optString("username", ""),
              secret = obj.getString("secret"),
              digits = obj.optInt("digits", 6),
              period = obj.optInt("period", 30),
              algorithm = HashAlgorithm.fromString(obj.optString("algorithm", "SHA1")),
              createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  override fun saveAccounts(accounts: List<Account>) {
    val array = JSONArray()
    for (account in accounts) {
      val obj =
        JSONObject().apply {
          put("id", account.id)
          put("serviceName", account.serviceName)
          put("username", account.username)
          put("secret", account.secret)
          put("digits", account.digits)
          put("period", account.period)
          put("algorithm", account.algorithm.uriValue)
          put("createdAtMillis", account.createdAtMillis)
        }
      array.put(obj)
    }
    prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
  }

  override fun getThemeMode(): ThemeMode =
    ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name))

  override fun setThemeMode(mode: ThemeMode) {
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
  }

  override fun getTimeOffsetSeconds(): Int = prefs.getInt(KEY_TIME_OFFSET_SEC, 0)

  override fun setTimeOffsetSeconds(seconds: Int) {
    prefs.edit().putInt(KEY_TIME_OFFSET_SEC, seconds.coerceIn(-300, 300)).apply()
  }

  override fun isAppLockEnabled(): Boolean =
    prefs.getBoolean(KEY_APP_LOCK_ENABLED, false) &&
      !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()

  override fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

  override fun setBiometricEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
  }

  override fun setPin(pin: String) {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val saltHex = salt.joinToString("") { "%02x".format(it) }
    val hash = hashPin(pin, saltHex)
    prefs
      .edit()
      .putString(KEY_PIN_SALT, saltHex)
      .putString(KEY_PIN_HASH, hash)
      .putBoolean(KEY_APP_LOCK_ENABLED, true)
      .apply()
  }

  override fun verifyPin(pin: String): Boolean {
    val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
    val expectedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
    val candidateHash = hashPin(pin, salt)
    return MessageDigest.isEqual(
      candidateHash.toByteArray(Charsets.UTF_8),
      expectedHash.toByteArray(Charsets.UTF_8),
    )
  }

  override fun clearAppLock() {
    prefs
      .edit()
      .remove(KEY_PIN_SALT)
      .remove(KEY_PIN_HASH)
      .putBoolean(KEY_APP_LOCK_ENABLED, false)
      .putBoolean(KEY_BIOMETRIC_ENABLED, false)
      .apply()
  }

  private fun hashPin(pin: String, saltHex: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    var current = "$saltHex:$pin".toByteArray(Charsets.UTF_8)
    repeat(1000) {
      current = digest.digest(current)
    }
    return current.joinToString("") { "%02x".format(it) }
  }

  companion object {
    private const val PREFS_FILE_NAME = "authenticator_encrypted_vault"
    private const val KEY_ACCOUNTS = "accounts_json"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TIME_OFFSET_SEC = "time_offset_seconds"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_HASH = "pin_hash"

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
      val masterKey =
        MasterKey.Builder(context)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()
      return EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    }
  }
}

class InMemoryVaultStorage : SecureVaultStorage {
  private var accounts: List<Account> = emptyList()
  private var themeMode: ThemeMode = ThemeMode.SYSTEM
  private var timeOffsetSeconds: Int = 0
  private var appLockEnabled: Boolean = false
  private var biometricEnabled: Boolean = false
  private var pinValue: String? = null

  override fun loadAccounts(): List<Account> = accounts

  override fun saveAccounts(accounts: List<Account>) {
    this.accounts = accounts
  }

  override fun getThemeMode(): ThemeMode = themeMode

  override fun setThemeMode(mode: ThemeMode) {
    themeMode = mode
  }

  override fun getTimeOffsetSeconds(): Int = timeOffsetSeconds

  override fun setTimeOffsetSeconds(seconds: Int) {
    timeOffsetSeconds = seconds.coerceIn(-300, 300)
  }

  override fun isAppLockEnabled(): Boolean = appLockEnabled && pinValue != null

  override fun isBiometricEnabled(): Boolean = biometricEnabled

  override fun setBiometricEnabled(enabled: Boolean) {
    biometricEnabled = enabled
  }

  override fun setPin(pin: String) {
    pinValue = pin
    appLockEnabled = true
  }

  override fun verifyPin(pin: String): Boolean = pinValue == pin

  override fun clearAppLock() {
    pinValue = null
    appLockEnabled = false
    biometricEnabled = false
  }
}
