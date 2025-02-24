plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.komunikaprototype"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.komunikaprototype"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndkVersion = "25.2.9519653"
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true  // Make sure the assignment is correct
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(libs.pose.detection.common)

    // CameraX core library using the camera2 implementation
    val camerax_version = "1.4.0"
    // The following line is optional, as the core library is included indirectly by camera-camera2
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    // If you want to additionally use the CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    // If you want to additionally use the CameraX View class
    implementation("androidx.camera:camera-view:${camerax_version}")

    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    //Mediapipe
    implementation("com.google.mediapipe:tasks-vision:latest.release")

    //TfLite
    implementation("org.tensorflow:tensorflow-lite:2.8.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.9.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.3.1")

    //Nearby Connections
    implementation("com.google.android.gms:play-services-nearby:18.5.0")

    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.multidex:multidex:2.0.1")

    //Recycle View
    implementation("androidx.recyclerview:recyclerview:1.3.0")

    //Vosk
    implementation("com.alphacephei:vosk-android:0.3.47@aar")

    //ARCore
    implementation ("io.github.sceneview:arsceneview:0.10.0")
}