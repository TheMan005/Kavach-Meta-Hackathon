package `in`.kavach.live

import `in`.kavach.inference.BackendSettings
import `in`.kavach.ui.BackendScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class BackendScreenTest {
    @get:Rule val compose = createComposeRule()
    @Before fun reset() { BackendSettings.clear() }
    @After fun clear() { BackendSettings.clear() }
    @Test fun copiedEnvLineConnectsAndSavesOnlyAfterSuccess() {
        var calls = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BackendScreen(verify = { config -> Assert.assertEquals("a".repeat(43), config.token); calls++ })
        } } }
        compose.onNodeWithText("Server token").performTextInput("KAVACH_LIVE_TOKEN=\"" + "a".repeat(43) + "\"")
        Assert.assertNull(BackendSettings.state.value)
        compose.onNodeWithText("Connect", substring = false).performScrollTo().performClick()
        compose.waitUntil(5000) { BackendSettings.state.value != null }
        compose.onNodeWithText("Connected. Open Check or Live to continue.").assertExists()
        Assert.assertEquals(1, calls)
    }
    @Test fun badAddressIsNotReportedAsBadToken() {
        var calls = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BackendScreen(verify = { calls++ }) } } }
        compose.onNodeWithText("Backend address").performTextReplacement("bad-address")
        compose.onNodeWithText("Server token").performTextInput("a".repeat(43))
        compose.onNodeWithText("Connect", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Enter an HTTPS address, or http://127.0.0.1:8765 for USB.").assertExists()
        Assert.assertEquals(0, calls); Assert.assertNull(BackendSettings.state.value)
    }
    @Test fun rejectedServerDoesNotSaveSettings() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BackendScreen(verify = { error("Token rejected by server.") }) } } }
        compose.onNodeWithText("Server token").performTextInput("a".repeat(43))
        compose.onNodeWithText("Connect", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Token rejected by server.").assertExists()
        Assert.assertNull(BackendSettings.state.value)
    }
}
