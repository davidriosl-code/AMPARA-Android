plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
// La integración de Firebase se configura únicamente cuando se añade el JSON propio de EcoResilia.
val ecoFirebaseConfigured = file("google-services.json").exists()
if (ecoFirebaseConfigured) { apply(plugin = "com.google.gms.google-services") }

android {
    namespace = "com.ecoresilia.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ecoresilia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "0.9.0-rc4.5.4-glass-switches"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    val assistantProxy = (project.findProperty("ecoresiliaAssistantProxyUrl") as? String) ?: ""
    defaultConfig {
        buildConfigField("String", "ASSISTANT_PROXY_URL", "\"$assistantProxy\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", ecoFirebaseConfigured.toString())
    }
    buildTypes {
        getByName("release") {
            // Nunca publicar el loopback del emulador en un APK de producción.
            val secureProxy = assistantProxy.takeIf { it.startsWith("https://") } ?: ""
            buildConfigField("String", "ASSISTANT_PROXY_URL", "\"$secureProxy\"")
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Firebase BoM controla las versiones de Auth y Cloud Firestore.
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
