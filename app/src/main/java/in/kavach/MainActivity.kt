package `in`.kavach

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import `in`.kavach.domain.DemoScenarioRepository
import `in`.kavach.input.ShareInputHandler
import `in`.kavach.ui.*
import `in`.kavach.live.liveFeatureVisibility

class MainActivity : ComponentActivity() {
    private val engine get() = (application as KavachApplication).engine
    private val assessment: AssessmentViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AssessmentViewModel(engine) as T
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND) receive(intent)
        intent = Intent(this, MainActivity::class.java)
        setContent { KavachApp(assessment, DemoScenarioRepository(applicationContext)) }
    }
    override fun onStart() { super.onStart(); liveFeatureVisibility(true) }
    override fun onStop() { liveFeatureVisibility(false); super.onStop() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
        setIntent(Intent(this, MainActivity::class.java))
    }
    private fun receive(incoming: Intent) {
        ShareInputHandler.read(incoming).fold(assessment::edit) { assessment.error(it.message ?: "Could not read shared text.") }
        incoming.removeExtra(Intent.EXTRA_TEXT)
        incoming.clipData = null
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.clear() // Do not serialize Compose or conversation state into Android saved state.
    }
}
