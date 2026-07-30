import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

android {
    namespace = "app.cheesino"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.cheesino"
        minSdk = 24                 // Android 7 — geniş cihaz + Android TV kapsamı
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Yayın imzası — sırlar repoda DEĞİL. `keystore.properties` (gitignore'lu) varsa okunur;
    // yoksa release imzasız kalır (yerel derleme kırılmaz). Örnek için keystore.properties.example.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    signingConfigs {
        // Ortak sabit debug anahtarı — CI ve yerel aynı SHA-1 ile imzalar → Google Sign-In
        // (Firebase) her yerde aynı. YALNIZ DEBUG; yayın anahtarı değil.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystorePropsFile.exists()) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            // R8/küçültme: cihazda doğrulandıktan sonra true yap (proguard-rules.pro hazır).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // keystore.properties varsa yayın anahtarıyla imzala; yoksa imzasız bırak.
            signingConfig = if (keystorePropsFile.exists()) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Android TV (Compose for TV — leanback yerine)
    implementation("androidx.tv:tv-material:1.0.0")

    // Oynatıcı — Media3 (ExoPlayer) birincil: HLS/MP4/DASH.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    // Chromecast — media3 CastPlayer + Google Cast framework (MediaRouteButton AppCompat teması ister)
    implementation("androidx.media3:media3-cast:1.4.1")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // libVLC — MKV/AVI/HEVC ve geniş codec desteği için ikinci oynatıcı motoru (ExoPlayer'ın
    // oynatamadığı/kastığı yayınlarda devreye girer). Büyük native kütüphane.
    implementation("org.videolan.android:libvlc-all:3.6.2")

    // Google Play Billing — Pro satın alma (tek seferlik kilit açma)
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Firebase — hesap (Auth) + cihazlar arası senkron (Firestore)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Google ile giriş (Credential Manager + Google ID) — TV dahil modern akış
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Firebase Task -> coroutine await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Ağ + JSON + görsel + kalıcılık
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")   // şifreli kimlik bilgisi
}
