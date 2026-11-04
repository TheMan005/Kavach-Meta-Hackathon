package `in`.kavach.live

import androidx.compose.runtime.Composable

@Composable fun LiveFeaturePanel(enabled: Boolean, onSetup: () -> Unit = {}) = Unit
@Composable fun LiveFeatureLifecycle(enabled: Boolean) = Unit
fun liveFeatureVisibility(visible: Boolean) = Unit

fun setLiveFeatureEnabled(context: android.content.Context, enabled: Boolean) = Unit
