package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

const val PIN_LENGTH = 4

@Composable
fun AppLockScreen(
  biometricAvailable: Boolean,
  onVerifyPin: (String) -> Boolean,
  onRequestBiometric: () -> Unit,
) {
  var enteredPin by remember { mutableStateOf("") }
  var showError by remember { mutableStateOf(false) }

  LaunchedEffect(biometricAvailable) {
    if (biometricAvailable) {
      onRequestBiometric()
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 24.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 36.dp),
      ) {
        Box(
          modifier =
            Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
          )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = "Unlock Authenticator",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = if (showError) "Incorrect PIN. Try again." else "Enter your 4-digit PIN",
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (showError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        PinDotsRow(length = PIN_LENGTH, filledCount = enteredPin.length, isError = showError)
      }

      PinKeypad(
        showBiometricButton = biometricAvailable,
        onDigitClick = { digit ->
          if (enteredPin.length < PIN_LENGTH) {
            showError = false
            val next = enteredPin + digit
            enteredPin = next
            if (next.length == PIN_LENGTH) {
              val verified = onVerifyPin(next)
              if (!verified) {
                showError = true
                enteredPin = ""
              }
            }
          }
        },
        onBackspace = {
          if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            showError = false
          }
        },
        onBiometricClick = onRequestBiometric,
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(bottom = 16.dp),
      )
    }
  }
}

@Composable
fun PinDotsRow(
  length: Int,
  filledCount: Int,
  isError: Boolean = false,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(length) { index ->
      val filled = index < filledCount
      val dotColor =
        when {
          isError -> MaterialTheme.colorScheme.error
          filled -> MaterialTheme.colorScheme.primary
          else -> MaterialTheme.colorScheme.outline
        }
      Box(
        modifier =
          Modifier
            .size(16.dp)
            .clip(CircleShape)
            .then(
              if (filled) {
                Modifier.background(dotColor)
              } else {
                Modifier.border(1.5.dp, dotColor, CircleShape)
              }
            )
      )
    }
  }
}

@Composable
fun PinKeypad(
  showBiometricButton: Boolean,
  onDigitClick: (String) -> Unit,
  onBackspace: () -> Unit,
  onBiometricClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val rows =
    listOf(
      listOf("1", "2", "3"),
      listOf("4", "5", "6"),
      listOf("7", "8", "9"),
    )
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    rows.forEach { rowDigits ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        rowDigits.forEach { digit ->
          KeypadDigitButton(digit = digit, onClick = { onDigitClick(digit) })
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showBiometricButton) {
        IconButton(
          onClick = onBiometricClick,
          modifier = Modifier.size(68.dp).testTag("pin_biometric_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = stringResource(R.string.cd_fingerprint),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
        }
      } else {
        Spacer(modifier = Modifier.size(68.dp))
      }

      KeypadDigitButton(digit = "0", onClick = { onDigitClick("0") })

      IconButton(
        onClick = onBackspace,
        modifier = Modifier.size(68.dp).testTag("pin_backspace_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Backspace,
          contentDescription = "Delete digit",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun KeypadDigitButton(
  digit: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(68.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        .clickable(onClick = onClick)
        .testTag("pin_key_$digit"),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = digit,
      fontSize = 24.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
