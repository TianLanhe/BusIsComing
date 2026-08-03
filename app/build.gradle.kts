import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { stream ->
            load(stream)
        }
    }
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.golink.busiscoming"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.golink.busiscoming"
        minSdk = 25
        targetSdk = 36
        versionCode = 12
        versionName = "1.0"

        testInstrumentationRunner = "com.golink.busiscoming.BusIsComingTestRunner"
        buildConfigField(
            "String",
            "GOOGLE_GEOCODING_API_KEY",
            (
                localProperties.getProperty("GOOGLE_GEOCODING_API_KEY")
                    ?: System.getenv("GOOGLE_GEOCODING_API_KEY")
                    ?: ""
            ).asBuildConfigString()
        )
        buildConfigField("boolean", "FORCE_WEBSITE_UPDATE_CHECK", "false")
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.jsoup)
    implementation(libs.play.services.location)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
}
