plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) // reads app/google-services.json (Firebase project settings)
}

android {
    namespace = "io.github.devasenan134.isaipetti"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.devasenan134.isaipetti"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.3"

        // Address of the companion (friends) server. For testing against a local copy:
        //   ./gradlew assembleDebug -PsocialUrl=http://10.0.2.2:8095
        val socialUrl = (project.findProperty("socialUrl") as String?) ?: "https://friends.example.com"
        buildConfigField("String", "SOCIAL_URL", "\"$socialUrl\"")
        // Plain http is only allowed when a local test server is configured.
        manifestPlaceholders["cleartext"] = socialUrl.startsWith("http://").toString()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging) // push notifications
}
