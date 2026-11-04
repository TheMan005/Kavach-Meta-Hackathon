package `in`.kavach

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import `in`.kavach.input.ShareInputHandler
import `in`.kavach.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBoundaryTest {
    private fun share() = Intent(Intent.ACTION_SEND).setType("text/plain")
    @Test fun missingExtraRejected() { assertTrue(ShareInputHandler.read(share()).isFailure) }
    @Test fun wrongTypeRejected() { assertTrue(ShareInputHandler.read(share().putExtra(Intent.EXTRA_TEXT, 42)).isFailure) }
    @Test fun wrongMimeRejected() { assertTrue(ShareInputHandler.read(share().setType("image/png").putExtra(Intent.EXTRA_TEXT, "hello")).isFailure) }
    @Test fun oversizeRejected() { assertTrue(ShareInputHandler.read(share().putExtra(Intent.EXTRA_TEXT, "x".repeat(8001))).isFailure) }
    @Test fun hindiAndEmojiPreserved() {
        val text = "नमस्ते 🙂"
        assertEquals(text, ShareInputHandler.read(share().putExtra(Intent.EXTRA_TEXT, text)).getOrThrow())
    }
    @Test fun demosAreValidatedAndOnlyIdAddressable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val demos = DemoScenarioRepository(context)
        assertEquals(12, demos.scenarios.size)
        demos.scenarios.forEach { assertEquals("Scripted demo — no AI assessment", demos.result(it.id).source) }
        assertTrue(runCatching { demos.result("arbitrary user input") }.isFailure)
    }
}
