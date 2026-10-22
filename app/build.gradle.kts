import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing comes from keystore.properties (never committed) or CI environment variables.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.bentusk.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bentusk.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Google's public AdMob test IDs. Replace with real IDs in release builds via gradle properties.
        val admobAppId = (project.findProperty("bentusk.admobAppId") as String?) ?: "ca-app-pub-3940256099942544~3347511713"
        val admobBannerId = (project.findProperty("bentusk.admobBannerId") as String?) ?: "ca-app-pub-3940256099942544/9214589741"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")

        // Only ship the languages the app is translated into.
        resourceConfigurations += listOf("en", "hi")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "BENTUSK_KEYSTORE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "BENTUSK_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "BENTUSK_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "BENTUSK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            if (release.storeFile != null) signingConfig = release
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.reorderable)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.mlkit.image.labeling)

    testImplementation(libs.junit)
}
