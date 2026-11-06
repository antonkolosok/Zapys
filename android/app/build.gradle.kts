plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ua.zapys"
    compileSdk = 35
    // Existing instrumentation tests use the platform JUnit3 test libraries.
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    defaultConfig {
        applicationId = "ua.zapys"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
