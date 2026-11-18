package com.example.model

import androidx.compose.runtime.Immutable

enum class HashAlgorithm(val macName: String, val uriValue: String) {
  SHA1("HmacSHA1", "SHA1"),
  SHA256("HmacSHA256", "SHA256"),
  SHA512("HmacSHA512", "SHA512");

  companion object {
    fun fromString(value: String?): HashAlgorithm =
      when (value?.trim()?.uppercase()) {
        "SHA256" -> SHA256
        "SHA512" -> SHA512
        else -> SHA1
      }
  }
}

@Immutable
data class Account(
  val id: String,
  val serviceName: String,
  val username: String,
  val secret: String,
  val digits: Int = 6,
  val period: Int = 30,
  val algorithm: HashAlgorithm = HashAlgorithm.SHA1,
  val createdAtMillis: Long = System.currentTimeMillis(),
) {
  val initialLetter: String
    get() =
      serviceName
        .trim()
        .firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.toString()
        ?: "?"
}

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK;

  companion object {
    fun fromString(value: String?): ThemeMode =
      entries.firstOrNull { it.name == value } ?: SYSTEM
  }
}
