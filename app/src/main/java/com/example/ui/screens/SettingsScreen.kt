package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ThemeMode
import com.example.ui.AuthenticatorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  uiState: AuthenticatorUiState,
  onBack: () -> Unit,
  onSelectTheme: (ThemeMode) -> Unit,
  onSetTimeOffsetSeconds: (Int) -> Unit,
  onSetPin: (String) -> Unit,
  onDisableAppLock: () -> Unit,
  onToggleBiometric: (Boolean) -> Unit,
  onOpenExportQr: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val biometricHardwareAvailable = remember(context) {
    val bm = BiometricManager.from(context)
    val res = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    res == BiometricManager.BIOMETRIC_SUCCESS
  }

  var showThemeDialog by remember { mutableStateOf(false) }
  var showTimeSyncDialog by remember { mutableStateOf(false) }
  var showSetPinDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
          ),
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .navigationBarsPadding()
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        ListItem(
          headlineContent = { Text("Dark mode") },
          supportingContent = {
            Text(
              when (uiState.themeMode) {
                ThemeMode.SYSTEM -> "System default"
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
              }
            )
          },
          leadingContent = {
            Icon(Icons.Outlined.DarkMode, contentDescription = null)
          },
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
          modifier = Modifier.clickable { showThemeDialog = true }.testTag("setting_dark_mode"),
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        ListItem(
          headlineContent = { Text("Time correction for codes") },
          supportingContent = {
            val offset = uiState.timeOffsetSeconds
            Text(
              if (offset == 0) "No offset (0s)"
              else if (offset > 0) "+${offset}s offset"
              else "${offset}s offset"
            )
          },
          leadingContent = {
            Icon(Icons.Outlined.AccessTime, contentDescription = null)
          },
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
          modifier = Modifier.clickable { showTimeSyncDialog = true }.testTag("setting_time_sync"),
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        ListItem(
          headlineContent = { Text("Export accounts as QR") },
          supportingContent = { Text("Transfer accounts offline to another device") },
          leadingContent = {
            Icon(Icons.Outlined.QrCode2, contentDescription = null)
          },
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
          modifier = Modifier.clickable(onClick = onOpenExportQr).testTag("setting_export_qr"),
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        ListItem(
          headlineContent = { Text("App lock (PIN)") },
          supportingContent = {
            Text(
              if (uiState.isAppLockEnabled) "Require 4-digit PIN when opening app"
              else "Off"
            )
          },
          leadingContent = {
            Icon(Icons.Outlined.Lock, contentDescription = null)
          },
          trailingContent = {
            Switch(
              checked = uiState.isAppLockEnabled,
              onCheckedChange = { checked ->
                if (checked) {
                  showSetPinDialog = true
                } else {
                  onDisableAppLock()
                }
              },
              modifier = Modifier.testTag("setting_app_lock_switch"),
            )
          },
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
          modifier =
            Modifier.clickable {
              if (uiState.isAppLockEnabled) onDisableAppLock() else showSetPinDialog = true
            },
        )

        if (uiState.isAppLockEnabled) {
          ListItem(
            headlineContent = { Text("Change PIN") },
            leadingContent = {
              Icon(Icons.Outlined.Pin, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable { showSetPinDialog = true }.testTag("setting_change_pin"),
          )

          ListItem(
            headlineContent = { Text("Unlock with fingerprint") },
            supportingContent = {
              Text(
                if (biometricHardwareAvailable) "Use fingerprint sensor on open"
                else "No fingerprint or biometric enrolled on device"
              )
            },
            leadingContent = {
              Icon(Icons.Outlined.Fingerprint, contentDescription = null)
            },
            trailingContent = {
              Switch(
                checked = uiState.isBiometricEnabled && biometricHardwareAvailable,
                enabled = biometricHardwareAvailable,
                onCheckedChange = onToggleBiometric,
                modifier = Modifier.testTag("setting_biometric_switch"),
              )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
          )
        }
      }
    }
  }

  if (showThemeDialog) {
    AlertDialog(
      onDismissRequest = { showThemeDialog = false },
      title = { Text("Choose theme") },
      text = {
        Column {
          listOf(
            ThemeMode.SYSTEM to "System default",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark",
          ).forEach { (mode, label) ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    onSelectTheme(mode)
                    showThemeDialog = false
                  }
                  .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = uiState.themeMode == mode,
                onClick = {
                  onSelectTheme(mode)
                  showThemeDialog = false
                },
              )
              Text(text = label, style = MaterialTheme.typography.bodyLarge)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
      },
    )
  }

  if (showTimeSyncDialog) {
    var draftOffset by remember(uiState.timeOffsetSeconds) {
      mutableIntStateOf(uiState.timeOffsetSeconds)
    }
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val previewTime = formatter.format(Date(System.currentTimeMillis() + draftOffset * 1000L))

    AlertDialog(
      onDismissRequest = { showTimeSyncDialog = false },
      title = { Text("Time correction") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Adjust clock offset offline if your device time drifts from standard UTC.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Offset: ${if (draftOffset > 0) "+$draftOffset" else draftOffset}s  •  Adjusted: $previewTime",
            style = MaterialTheme.typography.titleMedium,
          )
          Slider(
            value = draftOffset.toFloat(),
            onValueChange = { draftOffset = it.roundToInt() },
            valueRange = -60f..60f,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            OutlinedButton(
              onClick = { draftOffset = (draftOffset - 1).coerceAtLeast(-300) },
              modifier = Modifier.height(48.dp),
            ) {
              Text("-1s")
            }
            TextButton(
              onClick = { draftOffset = 0 },
              modifier = Modifier.height(48.dp),
            ) {
              Text("Reset")
            }
            OutlinedButton(
              onClick = { draftOffset = (draftOffset + 1).coerceAtMost(300) },
              modifier = Modifier.height(48.dp),
            ) {
              Text("+1s")
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            onSetTimeOffsetSeconds(draftOffset)
            showTimeSyncDialog = false
          },
        ) {
          Text("Apply")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTimeSyncDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showSetPinDialog) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showSetPinDialog = false },
      title = { Text("Set 4-digit PIN") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = pin,
            onValueChange = { value ->
              if (value.length <= PIN_LENGTH && value.all { it.isDigit() }) {
                pin = value
                error = null
              }
            },
            label = { Text("4-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("set_pin_input"),
          )
          OutlinedTextField(
            value = confirmPin,
            onValueChange = { value ->
              if (value.length <= PIN_LENGTH && value.all { it.isDigit() }) {
                confirmPin = value
                error = null
              }
            },
            label = { Text("Confirm PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            supportingText = error?.let { msg -> { Text(msg) } },
            modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input"),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            when {
              pin.length != PIN_LENGTH -> error = "Enter a 4-digit PIN"
              pin != confirmPin -> error = "PINs do not match"
              else -> {
                onSetPin(pin)
                showSetPinDialog = false
              }
            }
          },
          modifier = Modifier.testTag("save_pin_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel") }
      },
    )
  }
}
