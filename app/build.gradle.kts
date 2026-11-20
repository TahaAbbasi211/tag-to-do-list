import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tag.routine"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.tag.routine"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.6"

        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("en", "fa", "fr", "de", "tr")
    }

    signingConfigs {
        // Signing credentials live outside version control in root keystore.properties
        // (or environment variables on CI). No credentials are hardcoded here.
        val keystorePropsFile = rootProject.file("keystore.properties")
        val keystoreProps = Properties().apply {
            if (keystorePropsFile.exists()) {
                keystorePropsFile.inputStream().use { load(it) }
            }
        }
        val storePath = keystoreProps.getProperty("storeFile") ?: "keystore/tag-release.jks"
        val releaseKeystore = rootProject.file(storePath)
        val ksStorePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("TAG_STORE_PASSWORD")
        val ksKeyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("TAG_KEY_ALIAS")
        val ksKeyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("TAG_KEY_PASSWORD")
        if (releaseKeystore.exists() &&
            !ksStorePassword.isNullOrBlank() &&
            !ksKeyAlias.isNullOrBlank() &&
            !ksKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = ksStorePassword
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
