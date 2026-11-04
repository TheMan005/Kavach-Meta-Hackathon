plugins { kotlin("jvm") version "2.0.21" }
repositories { google(); mavenCentral() }
kotlin { jvmToolchain(17) }
sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include("in/kavach/domain/Assessment.kt", "in/kavach/inference/InferenceEngine.kt", "in/kavach/ui/AssessmentViewModel.kt")
    }
    test {
        kotlin.srcDir("../app/src/test/java")
        kotlin.include("in/kavach/AssessmentTest.kt", "in/kavach/ConsentTest.kt")
    }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
