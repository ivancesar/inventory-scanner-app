import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing from keystore.properties (gitignored). Missing file = unsigned release build.
val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.inventoryscanner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inventoryscanner"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystore.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // AppCompat ships ~100 locales; we ship two.
        localeFilters += listOf("en", "hr")
    }

    lint {
        error += "MissingTranslation"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Per-app language (setApplicationLocales), backported below Android 13.
    implementation(libs.appcompat)
    implementation(libs.camera.camera2)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit.vision)
    // Bundled model: works offline, no Play Services download on first scan.
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    // Real org.json on the JVM test classpath; android.jar only has stubs.
    testImplementation(libs.org.json)
}
