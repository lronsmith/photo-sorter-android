import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing applies only when all four variables are set (CI); otherwise release is unsigned.
val signingEnv = listOf(
    "SIGNING_KEYSTORE_PATH",
    "SIGNING_KEYSTORE_PASSWORD",
    "SIGNING_KEY_ALIAS",
    "SIGNING_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val releaseSigningAvailable = signingEnv.values.all { !it.isNullOrBlank() }

android {
    namespace = "io.github.lronsmith.photosorter"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.lronsmith.photosorter"
        minSdk = 30
        targetSdk = 37
        versionCode = providers.environmentVariable("BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("appVersionName").get()
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(signingEnv.getValue("SIGNING_KEYSTORE_PATH")!!)
                storePassword = signingEnv.getValue("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = signingEnv.getValue("SIGNING_KEY_ALIAS")
                keyPassword = signingEnv.getValue("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric needs access to JDK internals on JDK 17+ (flags from robolectric.org).
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test-junit4 brings Espresso 3.5, which calls InputManager.getInstance() (gone on
    // SDK 37) and fails every Robolectric Compose test; 3.7 fixes it.
    testImplementation(libs.androidx.test.espresso.core)
}
