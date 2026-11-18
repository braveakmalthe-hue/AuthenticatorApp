package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Account
import com.example.totp.TOTPUtil
import com.example.ui.components.QrCodeImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportQrScreen(
  accounts: List<Account>,
  singleAccount: Account? = null,
  onBack: () -> Unit,
  onScanImportQr: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val targetAccounts = remember(accounts, singleAccount) {
    if (singleAccount != null) listOf(singleAccount) else accounts
  }
  var useStandardSingleUri by remember(singleAccount) { mutableStateOf(singleAccount != null) }
  var pageIndex by remember { mutableIntStateOf(0) }

  val migrationUris = remember(targetAccounts) { TOTPUtil.buildMigrationUris(targetAccounts) }
  val activeUri =
    when {
      targetAccounts.isEmpty() -> ""
      targetAccounts.size == 1 && useStandardSingleUri ->
        TOTPUtil.buildOtpAuthUri(targetAccounts.first())
      else -> migrationUris.getOrElse(pageIndex.coerceIn(0, (migrationUris.size - 1).coerceAtLeast(0))) { "" }
    }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Text(if (singleAccount != null) "Transfer ${singleAccount.serviceName}" else "Export accounts")
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("export_back_button"),
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
      Column(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        if (targetAccounts.isEmpty()) {
          Text(
            text = "No accounts to export yet.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
          )
          OutlinedButton(
            onClick = onScanImportQr,
            modifier = Modifier.height(48.dp).testTag("import_qr_button"),
          ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text("Import from QR code")
          }
        } else {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
              )
              Text(
                text = "This QR code contains your private secret keys. Do not share or screenshot it publicly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (targetAccounts.size == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = useStandardSingleUri,
                onClick = { useStandardSingleUri = true },
                label = { Text("Standard otpauth QR") },
              )
              FilterChip(
                selected = !useStandardSingleUri,
                onClick = { useStandardSingleUri = false },
                label = { Text("Migration QR") },
              )
            }
          }

          QrCodeImage(
            content = activeUri,
            sizeDp = 240.dp,
            contentDescription = "Export QR code for ${targetAccounts.size} account(s)",
            modifier = Modifier.testTag("export_qr_image"),
          )

          Text(
            text =
              if (targetAccounts.size == 1) {
                val acc = targetAccounts.first()
                if (acc.username.isNotBlank()) "${acc.serviceName} (${acc.username})" else acc.serviceName
              } else {
                val totalBatches = migrationUris.size.coerceAtLeast(1)
                "Batch ${pageIndex + 1} of $totalBatches (${targetAccounts.size} accounts total)"
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          if (migrationUris.size > 1 && !(targetAccounts.size == 1 && useStandardSingleUri)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedButton(
                onClick = { if (pageIndex > 0) pageIndex-- },
                enabled = pageIndex > 0,
                modifier = Modifier.height(48.dp),
              ) {
                Text("Previous")
              }
              OutlinedButton(
                onClick = { if (pageIndex < migrationUris.lastIndex) pageIndex++ },
                enabled = pageIndex < migrationUris.lastIndex,
                modifier = Modifier.height(48.dp),
              ) {
                Text("Next QR")
              }
            }
          }

          if (singleAccount == null) {
            OutlinedButton(
              onClick = onScanImportQr,
              modifier = Modifier.height(48.dp).testTag("import_qr_button"),
            ) {
              Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
              Spacer(modifier = Modifier.padding(horizontal = 4.dp))
              Text("Import accounts from QR")
            }
          }
        }
      }
    }
  }
}
