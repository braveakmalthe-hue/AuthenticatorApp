package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.KeystoreSecureStorage
import com.example.data.SecureVaultStorage
import com.example.model.Account
import com.example.model.HashAlgorithm
import com.example.model.ThemeMode
import com.example.totp.TOTPUtil
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AuthenticatorUiState(
  val accounts: List<Account> = emptyList(),
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val timeOffsetSeconds: Int = 0,
  val isAppLockEnabled: Boolean = false,
  val isBiometricEnabled: Boolean = false,
  val isLocked: Boolean = false,
  val adjustedNowMillis: Long = System.currentTimeMillis(),
) {
  val filteredAccounts: List<Account>
    get() {
      val q = searchQuery.trim()
      if (q.isEmpty()) return accounts
      return accounts.filter { account ->
        account.serviceName.contains(q, ignoreCase = true) ||
          account.username.contains(q, ignoreCase = true)
      }
    }
}

sealed class QrImportResult {
  data class Success(val addedCount: Int, val firstServiceName: String) : QrImportResult()
  data object DuplicateOnly : QrImportResult()
  data object InvalidQr : QrImportResult()
}

class AuthViewModel(
  private val storage: SecureVaultStorage,
  private val clockMillisProvider: () -> Long = { System.currentTimeMillis() },
  startTicker: Boolean = true,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow(
      AuthenticatorUiState(
        accounts = storage.loadAccounts(),
        themeMode = storage.getThemeMode(),
        timeOffsetSeconds = storage.getTimeOffsetSeconds(),
        isAppLockEnabled = storage.isAppLockEnabled(),
        isBiometricEnabled = storage.isBiometricEnabled(),
        isLocked = storage.isAppLockEnabled(),
        adjustedNowMillis = clockMillisProvider() + storage.getTimeOffsetSeconds() * 1000L,
      )
    )
  val uiState: StateFlow<AuthenticatorUiState> = _uiState.asStateFlow()

  init {
    if (startTicker) {
      viewModelScope.launch {
        while (isActive) {
          val offsetMs = _uiState.value.timeOffsetSeconds * 1000L
          _uiState.update { it.copy(adjustedNowMillis = clockMillisProvider() + offsetMs) }
          delay(100L)
        }
      }
    }
  }

  fun setSearchActive(active: Boolean) {
    _uiState.update { state ->
      state.copy(
        isSearchActive = active,
        searchQuery = if (active) state.searchQuery else "",
      )
    }
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  fun addManualAccount(
    serviceName: String,
    username: String,
    secretKey: String,
    digits: Int = 6,
    period: Int = 30,
    algorithm: HashAlgorithm = HashAlgorithm.SHA1,
  ): Boolean {
    val cleanService = serviceName.trim()
    val normalizedSecret = TOTPUtil.normalizeBase32(secretKey)
    if (cleanService.isEmpty() || !TOTPUtil.isValidBase32(normalizedSecret)) {
      return false
    }

    val newAccount =
      Account(
        id = UUID.randomUUID().toString(),
        serviceName = cleanService,
        username = username.trim(),
        secret = normalizedSecret,
        digits = digits,
        period = period,
        algorithm = algorithm,
      )
    val updated = _uiState.value.accounts + newAccount
    storage.saveAccounts(updated)
    _uiState.update { it.copy(accounts = updated) }
    return true
  }

  fun importFromQr(rawContent: String): QrImportResult {
    val parsed = TOTPUtil.parseQrContent(rawContent)
    if (parsed.isEmpty()) return QrImportResult.InvalidQr

    val existing = _uiState.value.accounts
    val existingKeys =
      existing.map { "${it.serviceName.lowercase()}|${it.username.lowercase()}|${it.secret}" }.toSet()

    val toAdd =
      parsed.filter { candidate ->
        val key =
          "${candidate.serviceName.lowercase()}|${candidate.username.lowercase()}|${candidate.secret}"
        key !in existingKeys
      }

    if (toAdd.isEmpty()) {
      return QrImportResult.DuplicateOnly
    }

    val updated = existing + toAdd
    storage.saveAccounts(updated)
    _uiState.update { it.copy(accounts = updated) }
    return QrImportResult.Success(
      addedCount = toAdd.size,
      firstServiceName = toAdd.first().serviceName,
    )
  }

  fun updateAccount(accountId: String, serviceName: String, username: String): Boolean {
    val cleanService = serviceName.trim()
    if (cleanService.isEmpty()) return false
    val updated =
      _uiState.value.accounts.map { account ->
        if (account.id == accountId) {
          account.copy(serviceName = cleanService, username = username.trim())
        } else {
          account
        }
      }
    storage.saveAccounts(updated)
    _uiState.update { it.copy(accounts = updated) }
    return true
  }

  fun deleteAccount(accountId: String) {
    val updated = _uiState.value.accounts.filterNot { it.id == accountId }
    storage.saveAccounts(updated)
    _uiState.update { it.copy(accounts = updated) }
  }

  fun setThemeMode(mode: ThemeMode) {
    storage.setThemeMode(mode)
    _uiState.update { it.copy(themeMode = mode) }
  }

  fun setTimeOffsetSeconds(seconds: Int) {
    val clamped = seconds.coerceIn(-300, 300)
    storage.setTimeOffsetSeconds(clamped)
    _uiState.update {
      it.copy(
        timeOffsetSeconds = clamped,
        adjustedNowMillis = clockMillisProvider() + clamped * 1000L,
      )
    }
  }

  fun setPinLock(pin: String) {
    storage.setPin(pin)
    _uiState.update { it.copy(isAppLockEnabled = true) }
  }

  fun disableAppLock() {
    storage.clearAppLock()
    _uiState.update {
      it.copy(
        isAppLockEnabled = false,
        isBiometricEnabled = false,
        isLocked = false,
      )
    }
  }

  fun setBiometricEnabled(enabled: Boolean) {
    storage.setBiometricEnabled(enabled)
    _uiState.update { it.copy(isBiometricEnabled = enabled) }
  }

  fun unlockWithPin(pin: String): Boolean {
    val ok = storage.verifyPin(pin)
    if (ok) {
      _uiState.update { it.copy(isLocked = false) }
    }
    return ok
  }

  fun unlockWithBiometric() {
    _uiState.update { it.copy(isLocked = false) }
  }

  fun lockIfEnabled() {
    if (_uiState.value.isAppLockEnabled) {
      _uiState.update { it.copy(isLocked = true) }
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return AuthViewModel(KeystoreSecureStorage(application)) as T
        }
      }
  }
}
