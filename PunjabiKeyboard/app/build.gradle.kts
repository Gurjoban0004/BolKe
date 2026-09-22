plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = providers.gradleProperty("bolkeReleaseStoreFile").orNull
val releaseStorePassword = providers.gradleProperty("bolkeReleaseStorePassword").orNull
val releaseKeyAlias = providers.gradleProperty("bolkeReleaseKeyAlias").orNull
val releaseKeyPassword = providers.gradleProperty("bolkeReleaseKeyPassword").orNull
val hasReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.bolke.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bolke.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.3"
        buildConfigField("String", "LANGUAGE_SERVICE_URL", "\"${providers.gradleProperty("languageServiceUrl").orElse("").get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
