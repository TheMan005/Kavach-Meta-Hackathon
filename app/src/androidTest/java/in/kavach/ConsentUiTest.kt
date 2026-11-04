package `in`.kavach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ConsentUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun typingRequiresReviewThenConsent() {
        compose.runOnIdle { `in`.kavach.inference.BackendSettings.save("https://example.com", "a".repeat(40)) }
        compose.onNode(hasSetTextAction()).performTextInput("A sample conversation")
        if (!BuildConfig.LIVE_FEATURE) return
        compose.onNodeWithText("Review & check").performScrollTo().performClick()
        compose.onNodeWithText("Check this excerpt?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("A sample conversation").assertExists()
    }
}
