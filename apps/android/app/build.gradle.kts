plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Ağ + JSON + görsel + kalıcılık
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")   // şifreli kimlik bilgisi
}
