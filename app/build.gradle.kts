plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release signing is driven by environment variables, the same scheme as App Lock. When they
 * are absent we fall back to the debug key so `assembleRelease` still produces an APK that
 * installs on the phone instead of failing.
 */
val keystoreFile: File? = System.getenv("HOMEFIT_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let(::File)
    ?.takeIf(File::exists)

/**
 * CI passes the release tag's version ("1.2.3"); the version code is derived from it
 * (1.2.3 -> 10203) so every tagged release installs over the one before it.
 */
val appVersionName: String = System.getenv("HOMEFIT_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.1.0"
val appVersionCode: Int = appVersionName.substringBefore('-').split('.')
    .map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "com.radley.homefit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.radley.homefit"
        // 26 is Health Connect's floor; nothing else in the app needs more.
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("HOMEFIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HOMEFIT_KEY_ALIAS")
                keyPassword = System.getenv("HOMEFIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("HOMEFIT_KEYSTORE_PATH not set - signing release with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "ObsoleteLintCustomCheck")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Exported schemas are what migration tests compare against; they are committed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
