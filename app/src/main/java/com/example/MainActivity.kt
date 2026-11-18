package com.example

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Account
import com.example.model.ThemeMode
import com.example.totp.TOTPUtil
import com.example.ui.AuthViewModel
import com.example.ui.AuthenticatorUiState
import com.example.ui.QrImportResult
import com.example.ui.components.AccountRow
import com.example.ui.components.ServiceAvatar
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.ExportQrScreen
import com.example.ui.screens.ManualEntryScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AuthenticatorTheme

sealed interface AuthenticatorDestination {
  data object Home : AuthenticatorDestination
  data object ScanQr : AuthenticatorDestination
  data object ManualEntry : AuthenticatorDestination
  data object Settings : AuthenticatorDestination
  data class ExportQr(val singleAccount: Account? = null) : AuthenticatorDestination
}

class MainActivity : FragmentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(application))
      val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

      val isDark =
        when (uiState.themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }

      LaunchedEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle =
            if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
          navigationBarStyle =
            if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
      }

      AuthenticatorTheme(themeMode = uiState.themeMode) {
        AuthenticatorApp(
          uiState = uiState,
          viewModel = authViewModel,
          onTriggerBiometric = {
            showBiometricPrompt(
              onSuccess = { authViewModel.unlockWithBiometric() },
            )
          },
          canUseBiometrics = { canAuthenticateBiometric() },
        )
      }
    }
  }

  private fun canAuthenticateBiometric(): Boolean {
    val bm = BiometricManager.from(this)
    return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
      BiometricManager.BIOMETRIC_SUCCESS
  }

  private fun showBiometricPrompt(onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(this)
    val prompt =
      BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
          }
        },
      )
    val info =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Authenticator")
        .setSubtitle("Use your fingerprint to view verification codes")
        .setNegativeButtonText("Use PIN")
        .build()
    prompt.authenticate(info)
  }
}

@Composable
fun AuthenticatorApp(
  uiState: AuthenticatorUiState,
  viewModel: AuthViewModel,
  onTriggerBiometric: () -> Unit,
  canUseBiometrics: () -> Boolean,
) {
  val context = LocalContext.current
  var destination by remember { mutableStateOf<AuthenticatorDestination>(AuthenticatorDestination.Home) }

  if (uiState.isLocked) {
    AppLockScreen(
      biometricAvailable = uiState.isBiometricEnabled && canUseBiometrics(),
      onVerifyPin = { pin -> viewModel.unlockWithPin(pin) },
      onRequestBiometric = onTriggerBiometric,
    )
    return
  }

  when (val current = destination) {
    AuthenticatorDestination.Home -> {
      HomeScreen(
        uiState = uiState,
        onToggleSearch = { active -> viewModel.setSearchActive(active) },
        onSearchQueryChange = { q -> viewModel.updateSearchQuery(q) },
        onCopyCode = { rawCode ->
          copySensitiveCode(context, rawCode)
          Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT)
            .show()
        },
        onScanQr = { destination = AuthenticatorDestination.ScanQr },
        onEnterSetupKey = { destination = AuthenticatorDestination.ManualEntry },
        onOpenSettings = { destination = AuthenticatorDestination.Settings },
        onOpenExportAll = { destination = AuthenticatorDestination.ExportQr(singleAccount = null) },
        onTransferSingle = { account ->
          destination = AuthenticatorDestination.ExportQr(singleAccount = account)
        },
        onEditAccount = { account, newService, newUser ->
          viewModel.updateAccount(account.id, newService, newUser)
        },
        onDeleteAccount = { account ->
          viewModel.deleteAccount(account.id)
        },
        onLockNow = { viewModel.lockIfEnabled() },
      )
    }

    AuthenticatorDestination.ScanQr -> {
      QrScannerScreen(
        onBack = { destination = AuthenticatorDestination.Home },
        onEnterManually = { destination = AuthenticatorDestination.ManualEntry },
        onQrScanned = { rawQr ->
          when (val result = viewModel.importFromQr(rawQr)) {
            is QrImportResult.Success -> {
              val msg =
                if (result.addedCount == 1) "Added ${result.firstServiceName}"
                else "Added ${result.addedCount} accounts"
              Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
              destination = AuthenticatorDestination.Home
              true
            }
            QrImportResult.DuplicateOnly -> {
              Toast.makeText(context, "Account already added", Toast.LENGTH_SHORT).show()
              destination = AuthenticatorDestination.Home
              true
            }
            QrImportResult.InvalidQr -> false
          }
        },
      )
    }

    AuthenticatorDestination.ManualEntry -> {
      ManualEntryScreen(
        onBack = { destination = AuthenticatorDestination.Home },
        onSave = { service, username, secret, alg, digits, period ->
          val added =
            viewModel.addManualAccount(
              serviceName = service,
              username = username,
              secretKey = secret,
              digits = digits,
              period = period,
              algorithm = alg,
            )
          if (added) {
            Toast.makeText(context, "Account added", Toast.LENGTH_SHORT).show()
            destination = AuthenticatorDestination.Home
          }
          added
        },
      )
    }

    AuthenticatorDestination.Settings -> {
      SettingsScreen(
        uiState = uiState,
        onBack = { destination = AuthenticatorDestination.Home },
        onSelectTheme = { mode -> viewModel.setThemeMode(mode) },
        onSetTimeOffsetSeconds = { sec -> viewModel.setTimeOffsetSeconds(sec) },
        onSetPin = { pin ->
          viewModel.setPinLock(pin)
          Toast.makeText(context, "PIN lock enabled", Toast.LENGTH_SHORT).show()
        },
        onDisableAppLock = {
          viewModel.disableAppLock()
          Toast.makeText(context, "App lock disabled", Toast.LENGTH_SHORT).show()
        },
        onToggleBiometric = { enabled -> viewModel.setBiometricEnabled(enabled) },
        onOpenExportQr = { destination = AuthenticatorDestination.ExportQr(singleAccount = null) },
      )
    }

    is AuthenticatorDestination.ExportQr -> {
      ExportQrScreen(
        accounts = uiState.accounts,
        singleAccount = current.singleAccount,
        onBack = { destination = AuthenticatorDestination.Home },
        onScanImportQr = { destination = AuthenticatorDestination.ScanQr },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  uiState: AuthenticatorUiState,
  onToggleSearch: (Boolean) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onCopyCode: (String) -> Unit,
  onScanQr: () -> Unit,
  onEnterSetupKey: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenExportAll: () -> Unit,
  onTransferSingle: (Account) -> Unit,
  onEditAccount: (Account, String, String) -> Unit,
  onDeleteAccount: (Account) -> Unit,
  onLockNow: () -> Unit,
) {
  if (uiState.isSearchActive) {
    BackHandler { onToggleSearch(false) }
  }

  var showOverflowMenu by remember { mutableStateOf(false) }
  var showAddSheet by remember { mutableStateOf(false) }
  var selectedAccountForSheet by remember { mutableStateOf<Account?>(null) }
  var accountToEdit by remember { mutableStateOf<Account?>(null) }
  var accountToDelete by remember { mutableStateOf<Account?>(null) }

  val filteredAccounts = uiState.filteredAccounts

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      if (uiState.isSearchActive) {
        SearchTopBar(
          query = uiState.searchQuery,
          onQueryChange = onSearchQueryChange,
          onClose = { onToggleSearch(false) },
        )
      } else {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
            )
          },
          actions = {
            IconButton(
              onClick = { onToggleSearch(true) },
              modifier = Modifier.testTag("top_search_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.cd_search),
              )
            }
            IconButton(
              onClick = { showAddSheet = true },
              modifier = Modifier.testTag("top_add_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add_account),
              )
            }
            Box {
              IconButton(
                onClick = { showOverflowMenu = true },
                modifier = Modifier.testTag("top_more_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.MoreVert,
                  contentDescription = stringResource(R.string.cd_more_options),
                )
              }
              DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
              ) {
                DropdownMenuItem(
                  text = { Text("Transfer accounts") },
                  onClick = {
                    showOverflowMenu = false
                    onOpenExportAll()
                  },
                  modifier = Modifier.testTag("menu_transfer_accounts"),
                )
                DropdownMenuItem(
                  text = { Text("Settings") },
                  onClick = {
                    showOverflowMenu = false
                    onOpenSettings()
                  },
                  modifier = Modifier.testTag("menu_settings"),
                )
                if (uiState.isAppLockEnabled) {
                  DropdownMenuItem(
                    text = { Text("Lock app") },
                    onClick = {
                      showOverflowMenu = false
                      onLockNow()
                    },
                    modifier = Modifier.testTag("menu_lock_app"),
                  )
                }
              }
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.onBackground,
              actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      if (filteredAccounts.isEmpty()) {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .widthIn(max = 480.dp)
              .padding(32.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(52.dp),
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text =
              if (uiState.searchQuery.isNotBlank()) "No matching accounts"
              else "No accounts yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              if (uiState.searchQuery.isNotBlank()) "Try a different service or username."
              else "Tap + at the top to scan a QR code or enter a setup key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().testTag("accounts_list"),
        ) {
          itemsIndexed(
            items = filteredAccounts,
            key = { _, item -> item.id },
          ) { index, account ->
            val snapshot =
              TOTPUtil.snapshotForAccount(
                account = account,
                adjustedNowMillis = uiState.adjustedNowMillis,
              )
            AccountRow(
              account = account,
              snapshot = snapshot,
              onClick = { onCopyCode(snapshot.rawCode) },
              onLongClick = { selectedAccountForSheet = account },
              showDivider = index < filteredAccounts.lastIndex,
            )
          }
        }
      }
    }
  }

  if (showAddSheet) {
    ModalBottomSheet(
      onDismissRequest = { showAddSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
          text = "Add an account",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        ListItem(
          headlineContent = { Text("Scan a QR code") },
          supportingContent = { Text("Use camera to scan 2FA barcode") },
          leadingContent = {
            Icon(
              imageVector = Icons.Outlined.QrCodeScanner,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          modifier =
            Modifier
              .clickable {
                showAddSheet = false
                onScanQr()
              }
              .testTag("add_scan_qr_option"),
        )
        ListItem(
          headlineContent = { Text("Enter a setup key") },
          supportingContent = { Text("Type service name and Base32 secret key") },
          leadingContent = {
            Icon(
              imageVector = Icons.Outlined.Keyboard,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          modifier =
            Modifier
              .clickable {
                showAddSheet = false
                onEnterSetupKey()
              }
              .testTag("add_manual_key_option"),
        )
      }
    }
  }

  selectedAccountForSheet?.let { account ->
    ModalBottomSheet(
      onDismissRequest = { selectedAccountForSheet = null },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ServiceAvatar(serviceName = account.serviceName, initial = account.initialLetter, size = 36.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(text = account.serviceName, style = MaterialTheme.typography.titleMedium)
            if (account.username.isNotBlank()) {
              Text(
                text = account.username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ListItem(
          headlineContent = { Text("Edit") },
          leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
          modifier =
            Modifier
              .clickable {
                selectedAccountForSheet = null
                accountToEdit = account
              }
              .testTag("account_action_edit"),
        )
        ListItem(
          headlineContent = { Text("Transfer") },
          leadingContent = { Icon(Icons.Outlined.QrCode2, contentDescription = null) },
          modifier =
            Modifier
              .clickable {
                selectedAccountForSheet = null
                onTransferSingle(account)
              }
              .testTag("account_action_transfer"),
        )
        ListItem(
          headlineContent = {
            Text("Delete", color = MaterialTheme.colorScheme.error)
          },
          leadingContent = {
            Icon(
              Icons.Outlined.Delete,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
            )
          },
          modifier =
            Modifier
              .clickable {
                selectedAccountForSheet = null
                accountToDelete = account
              }
              .testTag("account_action_delete"),
        )
      }
    }
  }

  accountToEdit?.let { account ->
    var editedService by remember(account) { mutableStateOf(account.serviceName) }
    var editedUsername by remember(account) { mutableStateOf(account.username) }
    var editError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { accountToEdit = null },
      title = { Text("Edit account") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = editedService,
            onValueChange = {
              editedService = it
              editError = null
            },
            label = { Text("Service name") },
            singleLine = true,
            isError = editError != null,
            supportingText = editError?.let { msg -> { Text(msg) } },
            modifier = Modifier.fillMaxWidth().testTag("edit_service_input"),
          )
          OutlinedTextField(
            value = editedUsername,
            onValueChange = { editedUsername = it },
            label = { Text("Username or email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("edit_username_input"),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (editedService.isBlank()) {
              editError = "Service name is required"
            } else {
              onEditAccount(account, editedService, editedUsername)
              accountToEdit = null
            }
          },
          modifier = Modifier.testTag("edit_save_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { accountToEdit = null }) { Text("Cancel") }
      },
    )
  }

  accountToDelete?.let { account ->
    AlertDialog(
      onDismissRequest = { accountToDelete = null },
      title = { Text("Remove ${account.serviceName}?") },
      text = {
        Text(
          "Removing this account from Authenticator does not turn off 2-factor authentication on ${account.serviceName}. Make sure you have another way to sign in."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteAccount(account)
            accountToDelete = null
          },
          modifier = Modifier.testTag("confirm_delete_button"),
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { accountToDelete = null }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onClose: () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  TopAppBar(
    navigationIcon = {
      IconButton(
        onClick = onClose,
        modifier = Modifier.testTag("search_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.cd_back),
        )
      }
    },
    title = {
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle =
          TextStyle(
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
          ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("search_input"),
        decorationBox = { innerTextField ->
          if (query.isEmpty()) {
            Text(
              text = stringResource(R.string.search_accounts_hint),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 18.sp,
            )
          }
          innerTextField()
        },
      )
    },
    actions = {
      if (query.isNotEmpty()) {
        IconButton(
          onClick = { onQueryChange("") },
          modifier = Modifier.testTag("search_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_clear_search),
          )
        }
      }
    },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
      ),
  )
}

private fun copySensitiveCode(context: Context, rawCode: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  val clip =
    ClipData.newPlainText("Verification code", rawCode).apply {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        description.extras =
          PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
          }
      }
    }
  clipboard.setPrimaryClip(clip)
}
