plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lucaskivi.cream.remote"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 30
        buildConfigField("boolean", "USE_FUNCTIONS_EMULATOR", "false")
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FUNCTIONS_EMULATOR", "true")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    api(project(":core"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.kotlinx.coroutines.play.services)
}
