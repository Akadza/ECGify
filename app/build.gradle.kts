plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("androidx.navigation.safeargs.kotlin") version "2.9.6"
}

android {
    namespace = "com.rimuru.android.ecgify"
    compileSdk = 36

    packagingOptions {
        pickFirst("lib/x86/libc++_shared.so")
        pickFirst("lib/x86_64/libc++_shared.so")
        pickFirst("lib/armeabi-v7a/libc++_shared.so")
        pickFirst("lib/arm64-v8a/libc++_shared.so")
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }


    defaultConfig {
        applicationId = "com.rimuru.android.ecgify"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("io.github.mayuce:AndroidDocumentScanner:1.6.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
    implementation(project(":sdk"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}