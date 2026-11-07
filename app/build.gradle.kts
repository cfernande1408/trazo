import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Salida de un comando de git, o null si falla (sin git, sin historial…).
 * Sirve para numerar las compilaciones locales.
 */
fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

// En local, la versión sale de git: la última estable y los commits desde ella
// (0.3.0.12), así cada cambio sube un número. En GitHub Actions la ponen los workflows.
val lastTag = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
val sinceTag = lastTag?.let { git("rev-list", "--count", "$it..HEAD") } ?: "0"
val commit = System.getenv("GITHUB_SHA")?.take(7) ?: git("rev-parse", "--short=7", "HEAD") ?: "?"

android {
    namespace = "app.trazo"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.trazo"
        minSdk = 26
        targetSdk = 35
        // Estable: la etiqueta (0.3.0). Beta: la estable y el nº de ejecución (0.3.0.58).
        // Local: la estable y los commits desde ella (0.3.0.12). Debug y beta llevan su sufijo.
        versionCode = System.getenv("TRAZO_VERSION_CODE")?.toIntOrNull()
            ?: git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionName = System.getenv("TRAZO_VERSION_NAME")?.removePrefix("v")
            ?: "${lastTag?.removePrefix("v") ?: "0.0.0"}.$sinceTag"
        buildConfigField("String", "COMMIT", "\"$commit\"")

        // Comunidad (Supabase): de las variables de entorno (GitHub Actions) o de local.properties.
        // La clave "anon" es pública por diseño: la seguridad la ponen las políticas RLS de la base.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun setting(env: String, prop: String) = System.getenv(env) ?: local.getProperty(prop) ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"${setting("TRAZO_SUPABASE_URL", "trazo.supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${setting("TRAZO_SUPABASE_ANON_KEY", "trazo.supabase.anonKey")}\"")
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
        // La de debug lleva otro id para convivir con la de Releases (firmadas con claves distintas)
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
        // Canal beta: lo último de main, firmado con la misma clave que la estable pero como otra
        // app ("Trazo beta"), para probar lo nuevo sin tocar la que usas para salir
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            matchingFallbacks += listOf("release")
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
        buildConfig = true
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
