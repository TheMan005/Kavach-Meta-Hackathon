package `in`.kavach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import `in`.kavach.inference.*
import `in`.kavach.domain.*
import `in`.kavach.ui.AssessmentViewModel
import `in`.kavach.ui.KavachApp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class ConsentScreenTest {
    @get:Rule val compose = createComposeRule()
    @Before fun reset() { BackendSettings.clear() }
    @After fun clear() { BackendSettings.clear() }
    private class TestEngine : InferenceEngine {
        var calls = 0
        override fun stop() = Unit
        override suspend fun assess(text: String, sender: Sender, onLoaded: () -> Unit): EngineOutput {
            calls++; onLoaded()
            return EngineOutput("""{"schema_version":1,"status":"assessed","risk":"no_clear_indicators","suspected_pattern":"unclear","evidence":[],"limitations":[]}""", "muse-spark-1.3", "test-only", 1)
        }
    }
    @Test fun cloudConsentPrecedesAnyRequest() {
        BackendSettings.save("https://example.com", "a".repeat(40))
        val engine = TestEngine(); val vm = AssessmentViewModel(engine)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent { KavachApp(vm, DemoScenarioRepository(context)) }
        compose.onNode(hasSetTextAction()).performTextInput("Synthetic text for the UI test")
        if (!BuildConfig.LIVE_FEATURE) {
            compose.onNodeWithText("Review & check").performScrollTo().assertIsNotEnabled()
            return
        }
        compose.onNodeWithText("Review & check").performScrollTo().performClick()
        compose.onNodeWithText("api.meta.ai", substring = true).assertExists()
        Assert.assertEquals(0, engine.calls)
        compose.onNodeWithText("Send to Meta & check").performClick()
        compose.waitUntil(5000) { engine.calls == 1 }
        compose.onNodeWithText("Meta Model API").assertExists()
    }
    @Test fun cancellingDisclosureDoesNotCallEngine() {
        BackendSettings.save("https://example.com", "a".repeat(40))
        val engine = TestEngine()
        compose.setContent { KavachApp(AssessmentViewModel(engine), DemoScenarioRepository(ApplicationProvider.getApplicationContext())) }
        compose.onNode(hasSetTextAction()).performTextInput("Synthetic text")
        if (!BuildConfig.LIVE_FEATURE) return
        compose.onNodeWithText("Review & check").performScrollTo().performClick()
        compose.onNodeWithText("Keep editing").performClick()
        Assert.assertEquals(0, engine.calls)
    }
    @Test fun missingBackendDisablesCheckWithoutLocalModelPrompt() {
        compose.setContent { KavachApp(AssessmentViewModel(TestEngine()), DemoScenarioRepository(ApplicationProvider.getApplicationContext())) }
        compose.onNode(hasSetTextAction()).performTextInput("Synthetic text")
        compose.onNodeWithText("Review & check").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Model not installed").assertDoesNotExist()
        compose.onNodeWithText("Settings", useUnmergedTree = true).assertExists()
    }
}
