package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerScreen(
  onBack: () -> Unit,
  onEnterManually: () -> Unit,
  onQrScanned: (String) -> Boolean,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var permissionDenied by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      permissionDenied = !granted
    }

  LaunchedEffect(Unit) {
    if (!hasPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  if (!hasPermission) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.align(Alignment.TopStart).testTag("scanner_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
          )
        }
        Column(
          modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
          )
          Text(
            text = "Camera permission needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )
          Text(
            text =
              if (permissionDenied) {
                "Allow camera access to scan account QR codes, or enter your setup key manually."
              } else {
                "Point your camera at a QR code to add your account."
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.height(48.dp).testTag("grant_camera_button"),
          ) {
            Text("Allow camera")
          }
          FilledTonalButton(
            onClick = onEnterManually,
            modifier = Modifier.height(48.dp).testTag("scanner_manual_fallback_button"),
          ) {
            Text("Enter a setup key")
          }
        }
      }
    }
    return
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val handled = remember { AtomicBoolean(false) }
  var cameraRef by remember { mutableStateOf<Camera?>(null) }
  var torchEnabled by remember { mutableStateOf(false) }
  var scanFeedback by remember { mutableStateOf("Align QR code within the frame") }

  DisposableEffect(Unit) {
    onDispose { analysisExecutor.shutdown() }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          scaleType = PreviewView.ScaleType.FILL_CENTER
        }
      },
      update = { previewView ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
          {
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
            val preview =
              Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
              }
            val analyzer =
              ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                  val reader = QRCodeReader()
                  val hints =
                    mapOf(
                      DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                      DecodeHintType.TRY_HARDER to true,
                    )
                  imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (handled.get()) {
                      imageProxy.close()
                      return@setAnalyzer
                    }
                    val decoded = decodeQrFromImageProxy(imageProxy, reader, hints)
                    imageProxy.close()
                    if (decoded != null && handled.compareAndSet(false, true)) {
                      ContextCompat.getMainExecutor(context).execute {
                        val accepted = onQrScanned(decoded)
                        if (!accepted) {
                          scanFeedback = "Not a valid Authenticator QR code"
                          handled.set(false)
                        }
                      }
                    }
                  }
                }

            runCatching {
              cameraProvider.unbindAll()
              cameraRef =
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  CameraSelector.DEFAULT_BACK_CAMERA,
                  preview,
                  analyzer,
                )
            }
          },
          ContextCompat.getMainExecutor(context),
        )
      },
      modifier = Modifier.fillMaxSize(),
    )

    // Scrim & reticle overlay
    Canvas(modifier = Modifier.fillMaxSize()) {
      val boxSize = size.minDimension * 0.68f
      val left = (size.width - boxSize) / 2f
      val top = (size.height - boxSize) / 2f
      val holeRect = Rect(left, top, left + boxSize, top + boxSize)
      val holePath =
        Path().apply {
          addRoundRect(RoundRect(holeRect, CornerRadius(28f, 28f)))
        }
      clipPath(holePath, clipOp = ClipOp.Difference) {
        drawRect(Color.Black.copy(alpha = 0.58f))
      }
      drawRoundRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 5f),
      )
    }

    // Top controls
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 8.dp, vertical = 8.dp)
          .align(Alignment.TopCenter),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.align(Alignment.CenterStart).testTag("scanner_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.cd_back),
          tint = Color.White,
        )
      }
      Text(
        text = "Scan QR code",
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        modifier = Modifier.align(Alignment.Center),
      )
      IconButton(
        onClick = {
          val next = !torchEnabled
          torchEnabled = next
          cameraRef?.cameraControl?.enableTorch(next)
        },
        modifier = Modifier.align(Alignment.CenterEnd).testTag("scanner_flash_button"),
      ) {
        Icon(
          imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
          contentDescription = stringResource(R.string.cd_toggle_flash),
          tint = Color.White,
        )
      }
    }

    // Bottom hint & manual fallback
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 28.dp)
          .align(Alignment.BottomCenter),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.65f),
      ) {
        Text(
          text = scanFeedback,
          color = Color.White,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      FilledTonalButton(
        onClick = onEnterManually,
        modifier = Modifier.height(48.dp).testTag("scanner_enter_key_button"),
      ) {
        Text("Enter a setup key")
      }
    }
  }
}

private fun decodeQrFromImageProxy(
  imageProxy: ImageProxy,
  reader: QRCodeReader,
  hints: Map<DecodeHintType, Any>,
): String? {
  val plane = imageProxy.planes.firstOrNull() ?: return null
  val width = imageProxy.width
  val height = imageProxy.height
  val rowStride = plane.rowStride
  val pixelStride = plane.pixelStride
  val buffer = plane.buffer
  val yBytes = ByteArray(width * height)

  if (pixelStride == 1 && rowStride == width && buffer.remaining() >= yBytes.size) {
    buffer.get(yBytes)
  } else {
    val rowBuffer = ByteArray(rowStride)
    for (row in 0 until height) {
      val toRead = minOf(rowStride, buffer.remaining())
      if (toRead <= 0) break
      buffer.get(rowBuffer, 0, toRead)
      val destOffset = row * width
      if (pixelStride == 1) {
        System.arraycopy(rowBuffer, 0, yBytes, destOffset, minOf(width, toRead))
      } else {
        for (col in 0 until width) {
          val srcIdx = col * pixelStride
          if (srcIdx < toRead) {
            yBytes[destOffset + col] = rowBuffer[srcIdx]
          }
        }
      }
    }
  }

  return runCatching {
      val source =
        PlanarYUVLuminanceSource(
          yBytes,
          width,
          height,
          0,
          0,
          width,
          height,
          false,
        )
      val bitmap = BinaryBitmap(HybridBinarizer(source))
      reader.decode(bitmap, hints).text
    }
    .also { reader.reset() }
    .getOrNull()
}
