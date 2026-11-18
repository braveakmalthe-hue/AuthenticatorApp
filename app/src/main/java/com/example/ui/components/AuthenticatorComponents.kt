package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Account
import com.example.totp.TOTPUtil
import com.example.ui.theme.TotpCodeTextStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.absoluteValue

private val AvatarPalette =
  listOf(
    Color(0xFF1A73E8),
    Color(0xFFE91E63),
    Color(0xFF0F9D58),
    Color(0xFFF4511E),
    Color(0xFF8E24AA),
    Color(0xFF00897B),
    Color(0xFF3949AB),
    Color(0xFFD81B60),
  )

fun serviceAvatarColor(serviceName: String): Color {
  val lower = serviceName.trim().lowercase()
  return when {
    lower.contains("google") -> Color(0xFF4285F4)
    lower.contains("instagram") -> Color(0xFFE1306C)
    lower.contains("facebook") -> Color(0xFF1877F2)
    lower.contains("github") -> Color(0xFF24292E)
    lower.contains("microsoft") -> Color(0xFF0078D4)
    lower.contains("discord") -> Color(0xFF5865F2)
    lower.contains("amazon") -> Color(0xFFEF6C00)
    lower.contains("apple") -> Color(0xFF424242)
    lower.contains("x") || lower.contains("twitter") -> Color(0xFF1DA1F2)
    else -> AvatarPalette[(lower.hashCode().absoluteValue) % AvatarPalette.size]
  }
}

@Composable
fun ServiceAvatar(
  serviceName: String,
  initial: String,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
) {
  val bgColor = remember(serviceName) { serviceAvatarColor(serviceName) }
  Box(
    modifier = modifier.size(size).clip(CircleShape).background(bgColor),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initial,
      color = Color.White,
      fontSize = (size.value * 0.44f).sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountRow(
  account: Account,
  snapshot: TOTPUtil.CodeSnapshot,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  showDivider: Boolean,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
  val codeColor =
    if (snapshot.isExpiringSoon) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .combinedClickable(
            role = Role.Button,
            onClickLabel = "Copy code for ${account.serviceName}",
            onLongClickLabel = "Options for ${account.serviceName}",
            onClick = onClick,
            onLongClick = {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              onLongClick()
            },
          )
          .padding(horizontal = 16.dp, vertical = 14.dp)
          .testTag("account_row_${account.id}"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ServiceAvatar(
        serviceName = account.serviceName,
        initial = account.initialLetter,
        size = 42.dp,
      )

      Spacer(modifier = Modifier.width(14.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = account.serviceName,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (account.username.isNotBlank()) {
          Text(
            text = account.username,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = snapshot.formattedCode,
          style = TotpCodeTextStyle,
          color = codeColor,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.testTag("totp_code_${account.id}"),
        )
        Spacer(modifier = Modifier.width(10.dp))
        TotpCountdownPie(
          progress = snapshot.progress,
          remainingSeconds = snapshot.remainingSeconds,
          color = codeColor,
          trackColor = codeColor.copy(alpha = 0.16f),
          size = 20.dp,
        )
      }
    }

    if (showDivider) {
      HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
      )
    }
  }
}

@Composable
fun TotpCountdownPie(
  progress: Float,
  remainingSeconds: Int,
  color: Color,
  trackColor: Color,
  size: Dp = 20.dp,
  modifier: Modifier = Modifier,
) {
  Canvas(
    modifier =
      modifier
        .size(size)
        .semantics { contentDescription = "$remainingSeconds seconds remaining" },
  ) {
    drawCircle(color = trackColor)
    drawArc(
      color = color,
      startAngle = -90f,
      sweepAngle = -360f * progress.coerceIn(0f, 1f),
      useCenter = true,
    )
  }
}

@Composable
fun QrCodeImage(
  content: String,
  sizeDp: Dp = 240.dp,
  contentDescription: String = "QR Code",
  modifier: Modifier = Modifier,
) {
  val bitmap: ImageBitmap? =
    remember(content) {
      runCatching {
          val writer = QRCodeWriter()
          val hints =
            mapOf(
              EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
              EncodeHintType.MARGIN to 2,
            )
          val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
          val width = matrix.width
          val height = matrix.height
          val pixels = IntArray(width * height)
          for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
              pixels[offset + x] =
                if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
          }
          Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
          }.asImageBitmap()
        }
        .getOrNull()
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = Color.White,
    tonalElevation = 0.dp,
    shadowElevation = 2.dp,
  ) {
    Box(
      modifier = Modifier.padding(16.dp).size(sizeDp),
      contentAlignment = Alignment.Center,
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = contentDescription,
          modifier = Modifier.size(sizeDp),
        )
      } else {
        Text(
          text = "Unable to generate QR code",
          color = Color.Black,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}
