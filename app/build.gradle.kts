plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// OpenRung engine: libbox.aar (sing-box + OpenRung connectcore/brokerapi/punch/WSS in ONE gomobile
// Go runtime) built by third_party/openrung/android/build-libbox-release.sh. It is generated, not
// committed (GPL corresponding source = third_party/openrung + the pins it records).
val libboxAar = file(
    (findProperty("aminLibboxAar") as String?)
        ?: "${rootDir}/third_party/openrung/android/app/libs/libbox.aar"
)

android {
    // The namespace keeps the original Amin Gateway package for R/BuildConfig so the legacy
    // Capsule code is preserved unchanged; the applicationId is new so this experimental build
    // installs beside (never over) an existing Amin Gateway installation.
    namespace = "com.enigma.amingateway"
    compileSdk = 35
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.enigma.aminvpn"
        minSdk = 26 // OpenRung's VPN host contract minimum (Android 8.0).
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.1-armv7-experimental"
        // Ship 32-bit ARM only: the target is the 32-bit ARMv7 Xiaomi TV box. 64-bit ARM devices
        // with 32-bit userspace support can still run it.
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    signingConfigs {
        // Release signing is fail-closed: CI supplies a private keystore via secrets. Without it a
        // release build fails rather than falling back to a public debug key (see the guard below).
        if (System.getenv("AMIN_KEYSTORE_FILE") != null) {
            create("release") {
                storeFile = file(System.getenv("AMIN_KEYSTORE_FILE"))
                storePassword = System.getenv("AMIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AMIN_KEY_ALIAS")
                keyPassword = System.getenv("AMIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }

    packaging {
        jniLibs {
            // Keep libbox.so compressed inside the APK: much smaller download and APK on a
            // low-storage TV box (Android extracts it once at install time).
            useLegacyPackaging = true
            excludes += listOf("**/arm64-v8a/**", "**/x86/**", "**/x86_64/**")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // OpenRung split-tunnel rule sets (geosite/geoip .srs; provenance in assets/rulesets/README.md).
    // Split tunneling is off by default; the files are staged only when it is enabled.
    sourceSets["main"].assets.srcDir("${rootDir}/third_party/openrung/assets")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("openrung.contractVectors", "${rootDir}/third_party/openrung/testdata/contract")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradle.taskGraph.whenReady {
    val releasing = allTasks.any { it.project == project && it.name.startsWith("assembleRelease") }
    if (releasing && System.getenv("AMIN_KEYSTORE_FILE") == null) {
        throw GradleException("Release signing key missing (AMIN_KEYSTORE_FILE). Refusing to build an unsigned or debug-signed release.")
    }
    if (allTasks.any { it.project == project && (it.name.contains("assemble") || it.name.contains("bundle")) } && !libboxAar.exists()) {
        throw GradleException("OpenRung engine missing: run third_party/openrung/android/build-libbox-release.sh first ($libboxAar)")
    }
}

dependencies {
    if (libboxAar.exists()) implementation(files(libboxAar))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
