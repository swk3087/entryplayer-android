plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.entryplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.entryplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Apache Commons Compress for zip/tar extraction
    implementation("org.apache.commons:commons-compress:1.26.2")
    // NanoHTTPD for local web server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}