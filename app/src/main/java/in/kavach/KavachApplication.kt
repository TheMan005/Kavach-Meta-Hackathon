package `in`.kavach

import android.app.Application
import `in`.kavach.inference.ApiTextEngine

class KavachApplication : Application() {
    val engine by lazy { ApiTextEngine() }
}
