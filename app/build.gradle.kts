plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val offlinePreview = providers.gradleProperty("offlinePreview").map { it.toBooleanStrict() }.getOrElse(false)
val firebaseConfigured = !offlinePreview && file("google-services.json").exists()
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "tr.sakin.chat"
    compileSdk = 35
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = if (offlinePreview) "tr.sakin.chat.offline" else "tr.sakin.chat"
        minSdk = 26
        targetSdk = 35
        versionName = if (offlinePreview) "0.2.3-offline-preview" else "0.2.3-preview"
        versionCode = 5
        buildConfigField("boolean", "HAS_FIREBASE", firebaseConfigured.toString())
        buildConfigField("boolean", "OFFLINE_PREVIEW", offlinePreview.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    if (offlinePreview) sourceSets.getByName("main").manifest.srcFile("src/offline/AndroidManifest.xml")
    packaging {
        resources.excludes += setOf("libsignal_jni*.dylib", "signal_jni*.dll", "libsignal_jni*.so")
        jniLibs.excludes += "**/libsignal_jni_testing.so"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.signal:libsignal-android:0.105.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")

    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
