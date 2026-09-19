plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    testImplementation(libs.junit)
}
