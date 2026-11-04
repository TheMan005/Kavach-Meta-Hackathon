package `in`.kavach

import `in`.kavach.domain.*
import `in`.kavach.inference.InferenceEngine
import `in`.kavach.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class FakeEngine : InferenceEngine {
        var calls = 0
        var stops = 0
        var fail = false
        override fun stop() { stops++ }
        override suspend fun assess(text: String, sender: Sender, onLoaded: () -> Unit): EngineOutput {
            calls++; onLoaded()
            withContext(NonCancellable) { delay(100) } // Simulate native work that outlives cancellation.
            if (fail) error("Model missing")
            return EngineOutput("""{"schema_version":1,"status":"assessed","risk":"no_clear_indicators","suspected_pattern":"unclear","evidence":[],"limitations":[]}""", "fake-test-only", "test", 1)
        }
    }
    @Test fun textAndReviewNeverRunInference() = runTest(dispatcher) {
        val engine = FakeEngine(); val vm = AssessmentViewModel(engine)
        vm.edit("hello"); vm.review(); advanceUntilIdle()
        assertEquals(0, engine.calls)
        assertEquals(Phase.ConsentRequired, vm.state.value.phase)
    }
    @Test fun consentIsRequiredEveryTime() = runTest(dispatcher) {
        val engine = FakeEngine(); val vm = AssessmentViewModel(engine)
        vm.edit("hello"); vm.consent(); advanceUntilIdle(); assertEquals(0, engine.calls)
        vm.review(); vm.consent(); advanceUntilIdle(); assertEquals(1, engine.calls)
        vm.consent(); advanceUntilIdle(); assertEquals(1, engine.calls)
    }
    @Test fun disabledChecksPreventWork() = runTest(dispatcher) {
        val engine = FakeEngine(); val vm = AssessmentViewModel(engine)
        vm.edit("hello"); vm.review(); vm.enable(false); vm.consent(); advanceUntilIdle()
        assertEquals(0, engine.calls)
    }
    @Test fun cancellationSuppressesLateResult() = runTest(dispatcher) {
        val engine = FakeEngine(); val vm = AssessmentViewModel(engine)
        vm.edit("old text"); vm.review(); vm.consent(); runCurrent(); vm.cancel(); advanceUntilIdle()
        assertNull(vm.state.value.result); assertEquals(Phase.Cancelled, vm.state.value.phase)
    }
    @Test fun newShareSupersedesNativeWork() = runTest(dispatcher) {
        val engine = FakeEngine(); val vm = AssessmentViewModel(engine)
        vm.edit("old text"); vm.review(); vm.consent(); runCurrent(); vm.edit("new text"); advanceUntilIdle()
        assertEquals("new text", vm.state.value.text); assertNull(vm.state.value.result)
        assertEquals(Phase.Input, vm.state.value.phase)
    }
    @Test fun engineFailureProducesNoAssessment() = runTest(dispatcher) {
        val engine = FakeEngine().apply { fail = true }; val vm = AssessmentViewModel(engine)
        vm.edit("hello"); vm.review(); vm.consent(); advanceUntilIdle()
        assertEquals(Phase.Error, vm.state.value.phase); assertNull(vm.state.value.result)
    }
    @Test fun discardClearsTextAndResult() = runTest(dispatcher) {
        val vm = AssessmentViewModel(FakeEngine())
        vm.edit("hello"); vm.review(); vm.consent(); advanceUntilIdle(); vm.discard()
        assertEquals("", vm.state.value.text); assertNull(vm.state.value.result)
    }
}
