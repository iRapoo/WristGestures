import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * Release signing key, looked up in this order:
 * 1. Environment variables (used by the GitHub Actions release workflow):
 *    SIGNING_KEYSTORE_FILE, SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD.
 * 2. `keystore.properties` in the project root (local builds by the maintainer), with keys
 *    storeFile, storePassword, keyAlias, keyPassword. The file is git-ignored.
 * 3. Nothing found: the release APK is signed with the local debug key, so anyone who clones
 *    the project can still build and install it. Such an APK cannot update the official
 *    release (different signature).
 */
data class ReleaseKey(val file: File, val storePassword: String, val alias: String, val keyPassword: String)

fun findReleaseKey(): ReleaseKey? {
    System.getenv("SIGNING_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
        return ReleaseKey(
            file = file(path),
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD").orEmpty(),
            alias = System.getenv("SIGNING_KEY_ALIAS").orEmpty(),
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD").orEmpty(),
        )
    }
    val propsFile = rootProject.file("keystore.properties")
    if (!propsFile.exists()) return null
    val props = Properties().apply { propsFile.inputStream().use(::load) }
    return ReleaseKey(
        file = rootProject.file(props.getProperty("storeFile")),
        storePassword = props.getProperty("storePassword"),
        alias = props.getProperty("keyAlias"),
        keyPassword = props.getProperty("keyPassword"),
    )
}

val releaseKey = findReleaseKey()

android {
    namespace = "xyz.quenix.wristgestures"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.quenix.wristgestures"
        // Wear OS 3 is based on Android 11 (API 30).
        minSdk = 30
        targetSdk = 36
        // CI passes the version from the git tag (v1.2.3 -> 1.2.3); local builds use the defaults.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey.file
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.alias
                keyPassword = releaseKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // The app intentionally uses only the Android framework (no AndroidX) to stay small
    // on watches with 1 GB of RAM and to be easy to read.
    testImplementation("junit:junit:4.13.2")
}
