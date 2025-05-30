plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "hcmute.edu.vn.ocrscannerproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "hcmute.edu.vn.ocrscannerproject"
        minSdk = 26
        targetSdk = 34
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // AndroidX
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata:2.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.3.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // ML Kit for OCR
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:language-id:17.0.4")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    
    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // CircleImageView for round profile images
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Room for local database
    implementation("androidx.room:room-runtime:2.6.0")
    implementation(libs.navigation.ui)
    implementation(libs.navigation.fragment)
    implementation(libs.preference)
    annotationProcessor("androidx.room:room-compiler:2.6.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // UCrop
    implementation("com.github.Yalantis:uCrop:2.2.8-native")

    // PDF Generation
    implementation("com.itextpdf:itextpdf:5.5.13.3")
    
    // HTTP Client for OpenAI API
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.json:json:20231013") // For JSON handling
}