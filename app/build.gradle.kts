plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) apply false
}

// Private settings live outside the repository, in ~/.gradle/gradle.properties (or -P on the command line):
//   ISAIPETTI_SERVER_URL        your Navidrome server, filled in on the login screen
//   ISAIPETTI_SOCIAL_URL        the companion (friends) server
//   ISAIPETTI_KEYSTORE, ISAIPETTI_KEYSTORE_PASSWORD, ISAIPETTI_KEY_ALIAS   release signing
fun privateSetting(name: String): String? = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

// Firebase (push notifications) is only built in when app/google-services.json is present.
// It's kept out of git; see the README to use your own Firebase project.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

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
        //   ./gradlew assembleDebug -PISAIPETTI_SOCIAL_URL=http://10.0.2.2:8095
        val socialUrl = privateSetting("ISAIPETTI_SOCIAL_URL") ?: privateSetting("socialUrl") ?: ""
        buildConfigField("String", "SOCIAL_URL", "\"$socialUrl\"")
        // Where the app looks for new versions (GitHub Releases) and bug reports go.
        buildConfigField("String", "GITHUB_REPO", "\"${privateSetting("ISAIPETTI_GITHUB_REPO") ?: "devasenan134/isaipetti"}\"")
        buildConfigField("String", "DEFAULT_SERVER", "\"${privateSetting("ISAIPETTI_SERVER_URL").orEmpty()}\"")
        // Plain http is only allowed when a local test server is configured.
        manifestPlaceholders["cleartext"] = socialUrl.startsWith("http://").toString()
    }

    signingConfigs {
        val keystore = privateSetting("ISAIPETTI_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = privateSetting("ISAIPETTI_KEYSTORE_PASSWORD")
                keyAlias = privateSetting("ISAIPETTI_KEY_ALIAS") ?: "isaipetti"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            // Every release must be signed with the same key, or phones refuse the update.
            signingConfig = signingConfigs.findByName("release")
        }
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
