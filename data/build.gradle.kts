plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lucaskivi.cream.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":remote-datasource"))
    implementation(project(":local-datasource"))
}
