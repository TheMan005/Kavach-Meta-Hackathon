package `in`.kavach.input

import android.content.Intent
import `in`.kavach.domain.InputPolicy

object ShareInputHandler {
    fun read(intent: Intent): kotlin.Result<String> = runCatching {
        require(intent.action == Intent.ACTION_SEND && intent.type == "text/plain") { "Share plain text to check a conversation." }
        @Suppress("DEPRECATION")
        val value = intent.extras?.get(Intent.EXTRA_TEXT)
        require(value is CharSequence) { "The share did not contain plain text." }
        require(value.length <= InputPolicy.MAX_UNITS) { "Shared text is too long. Select up to 8,000 characters." }
        value.toString().also(InputPolicy::validate)
    }
}
