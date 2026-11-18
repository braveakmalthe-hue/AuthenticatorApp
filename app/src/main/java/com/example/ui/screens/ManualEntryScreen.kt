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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HashAlgorithm
import com.example.totp.TOTPUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
  onBack: () -> Unit,
  onSave: (serviceName: String, username: String, secret: String, algorithm: HashAlgorithm, digits: Int, period: Int) -> Boolean,
) {
  BackHandler(onBack = onBack)

  var serviceName by rememberSaveable { mutableStateOf("") }
  var username by rememberSaveable { mutableStateOf("") }
  var secretKey by rememberSaveable { mutableStateOf("") }
  var showAdvanced by rememberSaveable { mutableStateOf(false) }
  var algorithm by rememberSaveable { mutableStateOf(HashAlgorithm.SHA1) }
  var digits by rememberSaveable { mutableStateOf(6) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Enter account details") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("manual_back_button"),
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
          .imePadding()
          .navigationBarsPadding()
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = serviceName,
          onValueChange = {
            serviceName = it
            errorMessage = null
          },
          label = { Text("Service name") },
          placeholder = { Text("e.g. Instagram, Google, Facebook, GitHub") },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.Words,
              imeAction = ImeAction.Next,
            ),
          modifier = Modifier.fillMaxWidth().testTag("manual_service_input"),
        )

        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("Username or email (optional)") },
          placeholder = { Text("e.g. name@example.com") },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Next,
            ),
          modifier = Modifier.fillMaxWidth().testTag("manual_username_input"),
        )

        OutlinedTextField(
          value = secretKey,
          onValueChange = {
            secretKey = it
            errorMessage = null
          },
          label = { Text("Your key (Base32)") },
          placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
          supportingText = {
            Text(
              text = errorMessage ?: "Spaces and letter case are ignored automatically.",
              color =
                if (errorMessage != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          isError = errorMessage != null,
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.Characters,
              autoCorrectEnabled = false,
              imeAction = ImeAction.Done,
            ),
          modifier = Modifier.fillMaxWidth().testTag("manual_secret_input"),
        )

        FilterChip(
          selected = showAdvanced,
          onClick = { showAdvanced = !showAdvanced },
          label = { Text(if (showAdvanced) "Hide advanced options" else "Time-based (30s, SHA-1, 6 digits)") },
        )

        if (showAdvanced) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Algorithm",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              HashAlgorithm.entries.forEach { alg ->
                FilterChip(
                  selected = algorithm == alg,
                  onClick = { algorithm = alg },
                  label = { Text(alg.uriValue) },
                )
              }
            }
            Text(
              text = "Digits",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(6, 8).forEach { d ->
                FilterChip(
                  selected = digits == d,
                  onClick = { digits = d },
                  label = { Text("$d digits") },
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
          onClick = {
            when {
              serviceName.isBlank() -> errorMessage = "Enter a service name"
              !TOTPUtil.isValidBase32(secretKey) ->
                errorMessage = "Enter a valid Base32 setup key (A–Z, 2–7)"
              else -> {
                val ok = onSave(serviceName, username, secretKey, algorithm, digits, 30)
                if (!ok) {
                  errorMessage = "Could not save account. Check your setup key."
                }
              }
            }
          },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("manual_add_button"),
        ) {
          Text("Add")
        }
      }
    }
  }
}
