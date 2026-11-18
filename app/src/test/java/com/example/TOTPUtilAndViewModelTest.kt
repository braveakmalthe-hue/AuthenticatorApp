package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.InMemoryVaultStorage
import com.example.model.HashAlgorithm
import com.example.model.ThemeMode
import com.example.totp.TOTPUtil
import com.example.ui.AuthViewModel
import com.example.ui.QrImportResult
import com.example.ui.theme.AuthenticatorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TOTPUtilAndViewModelTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun rfc6238Sha1VectorsMatchExactSpecification() {
    // Secret: "12345678901234567890" -> Base32: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
    val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    assertEquals("94287082", TOTPUtil.generateTOTP(secret, 59L, 30, 8, HashAlgorithm.SHA1))
    assertEquals("287082", TOTPUtil.generateTOTP(secret, 59L, 30, 6, HashAlgorithm.SHA1))

    assertEquals("07081804", TOTPUtil.generateTOTP(secret, 1111111109L, 30, 8, HashAlgorithm.SHA1))
    assertEquals("081804", TOTPUtil.generateTOTP(secret, 1111111109L, 30, 6, HashAlgorithm.SHA1))

    assertEquals("89005924", TOTPUtil.generateTOTP(secret, 1234567890L, 30, 8, HashAlgorithm.SHA1))
    assertEquals("005924", TOTPUtil.generateTOTP(secret, 1234567890L, 30, 6, HashAlgorithm.SHA1))

    assertEquals("69279037", TOTPUtil.generateTOTP(secret, 2000000000L, 30, 8, HashAlgorithm.SHA1))
    assertEquals("279037", TOTPUtil.generateTOTP(secret, 2000000000L, 30, 6, HashAlgorithm.SHA1))
  }

  @Test
  fun parseOtpAuthUriWorksForMajorServices() {
    val githubUri =
      "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30"
    val github = TOTPUtil.parseOtpAuthUri(githubUri)
    assertNotNull(github)
    assertEquals("GitHub", github!!.serviceName)
    assertEquals("octocat", github.username)
    assertEquals("JBSWY3DPEHPK3PXP", github.secret)

    val instagramUri =
      "otpauth://totp/Instagram%3Aphoto_user?secret=NB2W45DFOIZA====&issuer=Instagram"
    val instagram = TOTPUtil.parseOtpAuthUri(instagramUri)
    assertNotNull(instagram)
    assertEquals("Instagram", instagram!!.serviceName)
    assertEquals("photo_user", instagram.username)
    assertEquals("NB2W45DFOIZA", instagram.secret)
  }

  @Test
  fun migrationProtobufRoundTripPreservesAccounts() {
    val storage = InMemoryVaultStorage()
    val vm = AuthViewModel(storage, clockMillisProvider = { 59_000L }, startTicker = false)
    assertTrue(vm.addManualAccount("Google", "user@gmail.com", "JBSWY3DPEHPK3PXP"))
    assertTrue(vm.addManualAccount("Facebook", "alex.doe", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))

    val uris = TOTPUtil.buildMigrationUris(vm.uiState.value.accounts)
    assertEquals(1, uris.size)
    assertTrue(uris.first().startsWith("otpauth-migration://offline?data="))

    val parsed = TOTPUtil.parseMigrationUri(uris.first())
    assertEquals(2, parsed.size)
    assertEquals("Google", parsed[0].serviceName)
    assertEquals("user@gmail.com", parsed[0].username)
    assertEquals("JBSWY3DPEHPK3PXP", parsed[0].secret)
    assertEquals("Facebook", parsed[1].serviceName)
    assertEquals("alex.doe", parsed[1].username)
  }

  @Test
  fun viewModelCrudSearchThemeAndLockLifecycle() {
    val storage = InMemoryVaultStorage()
    val vm = AuthViewModel(storage, clockMillisProvider = { 59_000L }, startTicker = false)

    assertFalse(vm.addManualAccount("Instagram", "me", "INVALID!KEY"))
    assertTrue(vm.addManualAccount("Instagram", "me@insta.com", "jbsw y3dp ehpk 3pxp"))
    assertTrue(vm.addManualAccount("GitHub", "dev", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))

    assertEquals(2, vm.uiState.value.accounts.size)

    vm.setSearchActive(true)
    vm.updateSearchQuery("git")
    assertEquals(1, vm.uiState.value.filteredAccounts.size)
    assertEquals("GitHub", vm.uiState.value.filteredAccounts.first().serviceName)

    val igId = vm.uiState.value.accounts.first().id
    vm.updateAccount(igId, "Instagram Work", "work@insta.com")
    assertEquals("Instagram Work", vm.uiState.value.accounts.first().serviceName)

    vm.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, storage.getThemeMode())

    vm.setTimeOffsetSeconds(15)
    assertEquals(15, storage.getTimeOffsetSeconds())

    vm.setPinLock("1357")
    vm.lockIfEnabled()
    assertTrue(vm.uiState.value.isLocked)
    assertFalse(vm.unlockWithPin("0000"))
    assertTrue(vm.uiState.value.isLocked)
    assertTrue(vm.unlockWithPin("1357"))
    assertFalse(vm.uiState.value.isLocked)

    assertEquals(QrImportResult.DuplicateOnly, vm.importFromQr("otpauth://totp/GitHub:dev?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=GitHub"))
    vm.deleteAccount(igId)
    assertEquals(1, vm.uiState.value.accounts.size)
  }

  @Test
  fun homeAndManualEntryUiCuj() {
    val storage = InMemoryVaultStorage()
    val vm = AuthViewModel(storage, clockMillisProvider = { 59_000L }, startTicker = false)

    composeTestRule.setContent {
      val state = vm.uiState.collectAsStateWithLifecycle().value
      AuthenticatorTheme(themeMode = state.themeMode) {
        AuthenticatorApp(
          uiState = state,
          viewModel = vm,
          onTriggerBiometric = {},
          canUseBiometrics = { false },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Authenticator").assertIsDisplayed()
    composeTestRule.onNodeWithText("No accounts yet").assertIsDisplayed()

    composeTestRule.onNodeWithTag("top_add_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_manual_key_option").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("manual_service_input").performTextInput("Google")
    composeTestRule.onNodeWithTag("manual_username_input").performTextInput("alex@gmail.com")
    composeTestRule.onNodeWithTag("manual_secret_input").performTextInput("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
    composeTestRule.onNodeWithTag("manual_add_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Authenticator").assertIsDisplayed()
    composeTestRule.onNodeWithText("Google").assertIsDisplayed()
    composeTestRule.onNodeWithText("alex@gmail.com").assertIsDisplayed()
    composeTestRule.onNodeWithText("287 082").assertIsDisplayed()
  }
}
