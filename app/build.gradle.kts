plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.trazo"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.trazo"
        minSdk = 26
        targetSdk = 35
        // En las releases los pone GitHub Actions a partir de la etiqueta (v0.2.0) y el nº de ejecución
        versionCode = System.getenv("TRAZO_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("TRAZO_VERSION_NAME")?.removePrefix("v") ?: "0.1.0"
    }

    // Firma de release desde variables de entorno (secretos de GitHub o tu terminal).
    // Sin ellas, assembleRelease sale sin firmar y assembleDebug no cambia.
    val keystore = System.getenv("TRAZO_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("TRAZO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TRAZO_KEY_ALIAS")
                keyPassword = System.getenv("TRAZO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:gpx"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.maplibre.android)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
