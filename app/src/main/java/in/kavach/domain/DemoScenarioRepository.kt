package `in`.kavach.domain

import android.content.Context
import kotlinx.serialization.json.*

data class DemoScenario(val id: String, val title: String, val language: String, val text: String, val response: String)
class DemoScenarioRepository(context: Context) {
    val scenarios: List<DemoScenario> = context.assets.open("demos.json").bufferedReader().use { reader ->
        Json.parseToJsonElement(reader.readText()).jsonArray.map {
            val o = it.jsonObject
            DemoScenario(o.string("id", 32), o.string("title", 80), o.string("language", 16), o.string("text", 8000), o["response"].toString())
        }
    }
    fun result(id: String): Result {
        val demo = scenarios.single { it.id == id }
        return Result(AssessmentParser().parse(demo.response, demo.text), "Scripted demo — no AI assessment", demo.id, "synthetic-v1", 0, demo.text)
    }
}
