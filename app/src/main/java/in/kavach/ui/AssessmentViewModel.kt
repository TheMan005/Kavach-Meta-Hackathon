package `in`.kavach.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.kavach.domain.*
import `in`.kavach.inference.InferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { Input, ConsentRequired, LoadingModel, Analyzing, Result, Error, Cancelled }
data class CheckState(
    val text: String = "", val sender: Sender = Sender.unspecified,
    val enabled: Boolean = true, val phase: Phase = Phase.Input,
    val result: Result? = null, val notice: String? = null
) { val busy get() = phase == Phase.LoadingModel || phase == Phase.Analyzing }

class AssessmentViewModel(private val engine: InferenceEngine) : ViewModel() {
    private val mutable = MutableStateFlow(CheckState())
    val state = mutable.asStateFlow()
    private var request = 0L
    private var job: Job? = null
    fun edit(text: String) {
        cancelWork()
        val error = runCatching { InputPolicy.validate(text) }.exceptionOrNull()?.message
        if (text.length > InputPolicy.MAX_UNITS) { mutable.update { it.copy(phase = Phase.Input, result = null, notice = error) }; return }
        mutable.update { it.copy(text = text, phase = Phase.Input, result = null, notice = if (text.isEmpty()) null else error) }
    }
    fun sender(sender: Sender) { cancelWork(); mutable.update { it.copy(sender = sender, phase = Phase.Input, result = null) } }
    fun enable(value: Boolean) {
        cancelWork()
        mutable.update { it.copy(enabled = value, phase = Phase.Input, result = null, notice = if (!value) "Checks are paused." else null) }
    }
    fun error(message: String) { cancelWork(); mutable.update { it.copy(phase = Phase.Error, result = null, notice = message) } }
    fun review() {
        if (!mutable.value.enabled || mutable.value.busy) return
        val error = runCatching { InputPolicy.validate(mutable.value.text) }.exceptionOrNull()
        mutable.update { it.copy(phase = if (error == null) Phase.ConsentRequired else Phase.Error, notice = error?.message, result = null) }
    }
    fun dismissConsent() { mutable.update { it.copy(phase = Phase.Input) } }
    fun consent() {
        val input = mutable.value
        if (!input.enabled || input.phase != Phase.ConsentRequired) return
        cancelWork()
        val id = request
        mutable.update { it.copy(phase = Phase.LoadingModel, notice = null, result = null) }
        job = viewModelScope.launch {
            try {
                val output = engine.assess(input.text, input.sender) {
                    viewModelScope.launch { if (id == request && mutable.value.phase == Phase.LoadingModel) mutable.update { it.copy(phase = Phase.Analyzing) } }
                }
                val parsed = AssessmentParser().parse(output.raw, input.text)
                if (id == request) mutable.update { it.copy(phase = Phase.Result, result = Result(parsed, "Meta Model API", output.modelId, output.version, output.elapsedMs, input.text)) }
            } catch (e: CancellationException) { throw e }
            catch (e: InferenceUnavailable) { if (id == request) fail(e.message ?: "Model unavailable.") }
            catch (e: OutOfMemoryError) { if (id == request) fail("Not enough memory. Try a shorter excerpt.") }
            catch (e: LinkageError) { if (id == request) fail("The app could not complete this check.") }
            catch (e: Exception) { if (id == request) fail("Unable to assess. Check the backend connection and API setup, or try a shorter excerpt. No risk conclusion was produced.") }
        }
    }
    private fun fail(message: String) { mutable.update { it.copy(phase = Phase.Error, result = null, notice = message) } }
    fun cancel() { cancelWork(); mutable.update { it.copy(phase = Phase.Cancelled, result = null, notice = "Cancelled. No assessment was retained.") } }
    fun discard() { cancelWork(); mutable.update { CheckState(enabled = it.enabled) } }
    private fun cancelWork() { request++; engine.stop(); job?.cancel(); job = null }
    override fun onCleared() { cancelWork(); mutable.value = CheckState() }
}
