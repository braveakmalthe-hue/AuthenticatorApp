package com.example.totp

import android.net.Uri
import android.util.Base64
import com.example.model.Account
import com.example.model.HashAlgorithm
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TOTPUtil {

  private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

  data class CodeSnapshot(
    val rawCode: String,
    val formattedCode: String,
    val remainingSeconds: Int,
    val progress: Float,
    val isExpiringSoon: Boolean,
  )

  fun normalizeBase32(input: String): String =
    input.uppercase().replace(" ", "").replace("-", "").trimEnd('=')

  fun isValidBase32(input: String): Boolean {
    val normalized = normalizeBase32(input)
    if (normalized.isEmpty()) return false
    return decodeBase32OrNull(normalized)?.isNotEmpty() == true
  }

  fun decodeBase32OrNull(input: String): ByteArray? {
    val clean = normalizeBase32(input)
    if (clean.isEmpty()) return null

    val output = ByteArrayOutputStream()
    var buffer = 0
    var bitsLeft = 0

    for (ch in clean) {
      val value = BASE32_ALPHABET.indexOf(ch)
      if (value < 0) return null
      buffer = (buffer shl 5) or value
      bitsLeft += 5
      if (bitsLeft >= 8) {
        output.write((buffer shr (bitsLeft - 8)) and 0xFF)
        bitsLeft -= 8
      }
    }
    val bytes = output.toByteArray()
    return if (bytes.isEmpty()) null else bytes
  }

  fun encodeBase32(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val sb = StringBuilder((bytes.size * 8 + 4) / 5)
    var buffer = 0
    var bitsLeft = 0
    for (b in bytes) {
      buffer = (buffer shl 8) or (b.toInt() and 0xFF)
      bitsLeft += 8
      while (bitsLeft >= 5) {
        val index = (buffer shr (bitsLeft - 5)) and 0x1F
        sb.append(BASE32_ALPHABET[index])
        bitsLeft -= 5
      }
    }
    if (bitsLeft > 0) {
      val index = (buffer shl (5 - bitsLeft)) and 0x1F
      sb.append(BASE32_ALPHABET[index])
    }
    return sb.toString()
  }

  fun generateTOTP(
    secretBase32: String,
    timeSeconds: Long,
    period: Int = 30,
    digits: Int = 6,
    algorithm: HashAlgorithm = HashAlgorithm.SHA1,
  ): String {
    val keyBytes = decodeBase32OrNull(secretBase32) ?: return "------"
    val safePeriod = if (period > 0) period else 30
    val safeDigits = digits.coerceIn(6, 8)
    val counter = Math.floorDiv(timeSeconds, safePeriod.toLong())

    val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
    val hash =
      try {
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(keyBytes, algorithm.macName))
        mac.doFinal(counterBytes)
      } catch (_: GeneralSecurityException) {
        return "------"
      }

    val offset = hash[hash.size - 1].toInt() and 0x0F
    val binary =
      ((hash[offset].toInt() and 0x7F) shl 24) or
        ((hash[offset + 1].toInt() and 0xFF) shl 16) or
        ((hash[offset + 2].toInt() and 0xFF) shl 8) or
        (hash[offset + 3].toInt() and 0xFF)

    val modulus = 10.0.pow(safeDigits.toDouble()).toInt()
    val otp = binary % modulus
    return otp.toString().padStart(safeDigits, '0')
  }

  fun formatCode(code: String): String {
    if (code.length == 6) {
      return "${code.substring(0, 3)} ${code.substring(3, 6)}"
    }
    if (code.length == 8) {
      return "${code.substring(0, 4)} ${code.substring(4, 8)}"
    }
    return code
  }

  fun snapshotForAccount(
    account: Account,
    adjustedNowMillis: Long,
  ): CodeSnapshot {
    val safePeriod = if (account.period > 0) account.period else 30
    val periodMillis = safePeriod * 1000L
    val nowSeconds = Math.floorDiv(adjustedNowMillis, 1000L)
    val elapsedMillisInPeriod = Math.floorMod(adjustedNowMillis, periodMillis)
    val remainingMillis = periodMillis - elapsedMillisInPeriod
    val remainingSeconds = ((remainingMillis + 999L) / 1000L).toInt().coerceIn(1, safePeriod)
    val progress = (remainingMillis.toFloat() / periodMillis.toFloat()).coerceIn(0f, 1f)
    val rawCode =
      generateTOTP(
        secretBase32 = account.secret,
        timeSeconds = nowSeconds,
        period = safePeriod,
        digits = account.digits,
        algorithm = account.algorithm,
      )
    return CodeSnapshot(
      rawCode = rawCode,
      formattedCode = formatCode(rawCode),
      remainingSeconds = remainingSeconds,
      progress = progress,
      isExpiringSoon = remainingSeconds <= 5,
    )
  }

  fun parseQrContent(rawContent: String): List<Account> {
    val trimmed = rawContent.trim()
    if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
      return listOfNotNull(parseOtpAuthUri(trimmed))
    }
    if (trimmed.startsWith("otpauth-migration://", ignoreCase = true)) {
      return parseMigrationUri(trimmed)
    }
    return emptyList()
  }

  fun parseOtpAuthUri(uriString: String): Account? {
    val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("otpauth", ignoreCase = true)) return null
    val type = uri.host?.lowercase() ?: return null
    if (type != "totp") return null

    val secretParam = uri.getQueryParameter("secret") ?: return null
    val normalizedSecret = normalizeBase32(secretParam)
    if (!isValidBase32(normalizedSecret)) return null

    val rawPath = uri.path?.removePrefix("/")?.let { urlDecode(it) }.orEmpty()
    val issuerParam = uri.getQueryParameter("issuer")?.let { urlDecode(it) }?.trim().orEmpty()

    val (labelIssuer, labelAccount) =
      if (rawPath.contains(":")) {
        val parts = rawPath.split(":", limit = 2)
        parts[0].trim() to parts[1].trim()
      } else {
        "" to rawPath.trim()
      }

    val serviceName =
      when {
        issuerParam.isNotEmpty() -> issuerParam
        labelIssuer.isNotEmpty() -> labelIssuer
        labelAccount.isNotEmpty() -> labelAccount
        else -> "Account"
      }

    val username =
      when {
        labelAccount.isNotEmpty() && !labelAccount.equals(serviceName, ignoreCase = true) ->
          labelAccount
        else -> ""
      }

    val digits = uri.getQueryParameter("digits")?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6
    val period = uri.getQueryParameter("period")?.toIntOrNull()?.takeIf { it in 10..120 } ?: 30
    val algorithm = HashAlgorithm.fromString(uri.getQueryParameter("algorithm"))

    return Account(
      id = UUID.randomUUID().toString(),
      serviceName = serviceName,
      username = username,
      secret = normalizedSecret,
      digits = digits,
      period = period,
      algorithm = algorithm,
    )
  }

  fun buildOtpAuthUri(account: Account): String {
    val issuerEncoded = urlEncode(account.serviceName.ifBlank { "Account" })
    val label =
      if (account.username.isNotBlank()) {
        "${urlEncode(account.serviceName)}:${urlEncode(account.username)}"
      } else {
        issuerEncoded
      }
    return buildString {
      append("otpauth://totp/")
      append(label)
      append("?secret=").append(normalizeBase32(account.secret))
      append("&issuer=").append(issuerEncoded)
      append("&algorithm=").append(account.algorithm.uriValue)
      append("&digits=").append(account.digits)
      append("&period=").append(account.period)
    }
  }

  fun buildMigrationUris(accounts: List<Account>, chunkSize: Int = 8): List<String> {
    if (accounts.isEmpty()) return emptyList()
    val chunks = accounts.chunked(chunkSize.coerceAtLeast(1))
    val batchId = (System.currentTimeMillis() and 0x7FFFFFFFL).toInt()
    return chunks.mapIndexed { batchIndex, chunk ->
      val payload = ByteArrayOutputStream()
      for (account in chunk) {
        val secretBytes = decodeBase32OrNull(account.secret) ?: continue
        val paramOut = ByteArrayOutputStream()
        writeBytesField(paramOut, 1, secretBytes)
        val displayName =
          if (account.username.isNotBlank()) {
            "${account.serviceName}:${account.username}"
          } else {
            account.serviceName
          }
        writeStringField(paramOut, 2, displayName)
        writeStringField(paramOut, 3, account.serviceName)
        val algEnum =
          when (account.algorithm) {
            HashAlgorithm.SHA1 -> 1
            HashAlgorithm.SHA256 -> 2
            HashAlgorithm.SHA512 -> 3
          }
        writeVarintField(paramOut, 4, algEnum.toLong())
        val digitsEnum = if (account.digits == 8) 2 else 1
        writeVarintField(paramOut, 5, digitsEnum.toLong())
        writeVarintField(paramOut, 6, 2L) // TOTP = 2
        writeBytesField(payload, 1, paramOut.toByteArray())
      }
      writeVarintField(payload, 2, 1L) // version = 1
      writeVarintField(payload, 3, chunks.size.toLong()) // batch_size
      writeVarintField(payload, 4, batchIndex.toLong()) // batch_index
      writeVarintField(payload, 5, batchId.toLong()) // batch_id

      val encoded = Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP)
      "otpauth-migration://offline?data=${urlEncode(encoded)}"
    }
  }

  fun parseMigrationUri(uriString: String): List<Account> {
    val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return emptyList()
    if (!uri.scheme.equals("otpauth-migration", ignoreCase = true)) return emptyList()
    val dataParam = uri.getQueryParameter("data") ?: return emptyList()
    val rawBytes =
      runCatching { Base64.decode(dataParam, Base64.DEFAULT) }.getOrNull() ?: return emptyList()

    val accounts = mutableListOf<Account>()
    val reader = ProtoReader(rawBytes)
    while (!reader.isAtEnd()) {
      val tag = reader.readVarint().toInt()
      val fieldNumber = tag ushr 3
      val wireType = tag and 0x07
      if (fieldNumber == 1 && wireType == 2) {
        val paramBytes = reader.readBytes()
        parseMigrationOtpParam(paramBytes)?.let { accounts.add(it) }
      } else {
        reader.skipField(wireType)
      }
    }
    return accounts
  }

  private fun parseMigrationOtpParam(bytes: ByteArray): Account? {
    val reader = ProtoReader(bytes)
    var secretBytes = ByteArray(0)
    var name = ""
    var issuer = ""
    var algorithm = HashAlgorithm.SHA1
    var digits = 6
    var type = 2 // default TOTP

    while (!reader.isAtEnd()) {
      val tag = reader.readVarint().toInt()
      val fieldNumber = tag ushr 3
      val wireType = tag and 0x07
      when (fieldNumber) {
        1 -> if (wireType == 2) secretBytes = reader.readBytes() else reader.skipField(wireType)
        2 -> if (wireType == 2) name = String(reader.readBytes(), Charsets.UTF_8) else reader.skipField(wireType)
        3 -> if (wireType == 2) issuer = String(reader.readBytes(), Charsets.UTF_8) else reader.skipField(wireType)
        4 ->
          if (wireType == 0) {
            algorithm =
              when (reader.readVarint().toInt()) {
                2 -> HashAlgorithm.SHA256
                3 -> HashAlgorithm.SHA512
                else -> HashAlgorithm.SHA1
              }
          } else {
            reader.skipField(wireType)
          }
        5 ->
          if (wireType == 0) {
            digits = if (reader.readVarint().toInt() == 2) 8 else 6
          } else {
            reader.skipField(wireType)
          }
        6 ->
          if (wireType == 0) {
            type = reader.readVarint().toInt()
          } else {
            reader.skipField(wireType)
          }
        else -> reader.skipField(wireType)
      }
    }

    if (secretBytes.isEmpty() || type == 1) return null // skip HOTP if explicitly HOTP

    val (parsedIssuer, parsedUser) =
      if (name.contains(":")) {
        val parts = name.split(":", limit = 2)
        parts[0].trim() to parts[1].trim()
      } else {
        "" to name.trim()
      }

    val finalService = issuer.trim().ifEmpty { parsedIssuer }.ifEmpty { parsedUser }.ifEmpty { "Account" }
    val finalUser =
      if (parsedUser.equals(finalService, ignoreCase = true)) "" else parsedUser

    return Account(
      id = UUID.randomUUID().toString(),
      serviceName = finalService,
      username = finalUser,
      secret = encodeBase32(secretBytes),
      digits = digits,
      period = 30,
      algorithm = algorithm,
    )
  }

  private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
    var v = value
    while (true) {
      if ((v and 0x7FL.inv()) == 0L) {
        out.write(v.toInt())
        return
      }
      out.write(((v.toInt() and 0x7F) or 0x80))
      v = v ushr 7
    }
  }

  private fun writeVarintField(out: ByteArrayOutputStream, fieldNumber: Int, value: Long) {
    writeVarint(out, ((fieldNumber shl 3) or 0).toLong())
    writeVarint(out, value)
  }

  private fun writeBytesField(out: ByteArrayOutputStream, fieldNumber: Int, data: ByteArray) {
    writeVarint(out, ((fieldNumber shl 3) or 2).toLong())
    writeVarint(out, data.size.toLong())
    out.write(data)
  }

  private fun writeStringField(out: ByteArrayOutputStream, fieldNumber: Int, text: String) {
    if (text.isEmpty()) return
    writeBytesField(out, fieldNumber, text.toByteArray(Charsets.UTF_8))
  }

  private class ProtoReader(private val data: ByteArray) {
    private var pos = 0

    fun isAtEnd(): Boolean = pos >= data.size

    fun readVarint(): Long {
      var result = 0L
      var shift = 0
      while (pos < data.size && shift < 64) {
        val b = data[pos++].toInt() and 0xFF
        result = result or ((b and 0x7F).toLong() shl shift)
        if ((b and 0x80) == 0) return result
        shift += 7
      }
      return result
    }

    fun readBytes(): ByteArray {
      val len = readVarint().toInt().coerceAtLeast(0)
      val end = (pos + len).coerceAtMost(data.size)
      val slice = data.copyOfRange(pos, end)
      pos = end
      return slice
    }

    fun skipField(wireType: Int) {
      when (wireType) {
        0 -> readVarint()
        1 -> pos = (pos + 8).coerceAtMost(data.size)
        2 -> {
          val len = readVarint().toInt().coerceAtLeast(0)
          pos = (pos + len).coerceAtMost(data.size)
        }
        5 -> pos = (pos + 4).coerceAtMost(data.size)
        else -> pos = data.size
      }
    }
  }

  private fun urlDecode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

  private fun urlEncode(value: String): String =
    runCatching { URLEncoder.encode(value, "UTF-8").replace("+", "%20") }.getOrDefault(value)
}
