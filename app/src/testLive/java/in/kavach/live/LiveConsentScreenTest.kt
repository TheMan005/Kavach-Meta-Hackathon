package `in`.kavach.live

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class LiveConsentScreenTest {
    @get:Rule val compose = createComposeRule()
    @Before fun reset() { `in`.kavach.inference.BackendSettings.clear(); LiveSessionStore.stop(); AudioSessionStore.stop(); LiveSessionStore.enabled = true }
    @Test fun validSettingsStillNeedDisclosureAndCanBeCancelled() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { LiveFeaturePanel(true) } } }
        compose.runOnIdle { `in`.kavach.inference.BackendSettings.save("https://example.com", "a".repeat(40)) }
        compose.onNodeWithText("Review live sharing").performScrollTo().performClick()
        compose.onNodeWithText("Share screen images with Meta?").assertIsDisplayed()
        compose.onNodeWithText("api.meta.ai", substring = true).assertExists()
        Assert.assertFalse(LiveSessionStore.state.value.active)
        compose.onNodeWithText("Cancel").performClick()
        Assert.assertFalse(LiveSessionStore.state.value.active)
    }
    @Test fun microphoneNeedsSeparateDisclosureAndCanBeCancelled() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { LiveFeaturePanel(true) } } }
        compose.onNodeWithText("Speakerphone").performClick()
        compose.runOnIdle { `in`.kavach.inference.BackendSettings.save("https://example.com", "a".repeat(40)) }
        compose.onNodeWithText("Review microphone sharing").performScrollTo().performClick()
        compose.onNodeWithText("Share microphone audio with Meta?").assertIsDisplayed()
        compose.onNodeWithText("api.meta.ai", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        Assert.assertFalse(AudioSessionStore.state.value.active)
    }
    @Test fun pausedChecksPreventMicrophoneStart() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { AudioFeaturePanel(false) } } }
        compose.onNodeWithText("Review microphone sharing").performScrollTo().assertIsNotEnabled()
    }
    @Test fun disablingChecksPreventsNewSharing() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { LiveFeaturePanel(false) } } }
        compose.onNodeWithText("Review live sharing").performScrollTo().assertIsNotEnabled()
        Assert.assertFalse(LiveSessionStore.state.value.active)
    }
}
