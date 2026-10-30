import org.gradle.kotlin.dsl.implementation
import java.util.Properties

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    kotlin("kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.gps.map.camera.and.time.stamp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gps.map.camera.and.time.stamp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        val erain_studio_version = "2.1"
        val module_update_gdpr_version = "2.0.2"
        val play_services_ads_version = "24.7.0"

        debug {
            buildConfigField("String", "email_feedback", "\"glorymobile88@gmail.com\"")

            buildConfigField("String", "ERAIN_STUDIO_VERSION", "\"$erain_studio_version\"")
            buildConfigField("String", "PLAY_SERVICES_ADS_VERSION", "\"$play_services_ads_version\"")
            buildConfigField("String", "GDPR_MODULE_VERSION", "\"$module_update_gdpr_version\"")
            firebaseCrashlytics {
                mappingFileUploadEnabled = false
            }
        }
        release {
            buildConfigField("String", "ERAIN_STUDIO_VERSION", "\"$erain_studio_version\"")
            buildConfigField("String", "PLAY_SERVICES_ADS_VERSION", "\"$play_services_ads_version\"")
            buildConfigField("String", "GDPR_MODULE_VERSION", "\"$module_update_gdpr_version\"")
            isMinifyEnabled = true
            firebaseCrashlytics {
                mappingFileUploadEnabled = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
            compose = true
            buildConfig = true
            dataBinding = true
            viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.config)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.onesignal)
    implementation(libs.androidx.hilt.navigation.compose)
    val androidx_lifecycle_version = "2.6.1"
    val androidx_fragment_ktx_version = "1.6.1"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${androidx_lifecycle_version}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${androidx_lifecycle_version}")
    implementation("androidx.fragment:fragment-ktx:${androidx_fragment_ktx_version}")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("io.socket:socket.io-client:2.1.0")
    val moshiVersion = "1.15.2"
    kapt("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
    implementation("com.squareup.moshi:moshi-adapters:$moshiVersion")
    val timber_version = "5.0.1"
    implementation("com.jakewharton.timber:timber:${timber_version}")

    val glide_version = "4.15.1"
    implementation("com.github.bumptech.glide:glide:$glide_version")
    kapt("com.github.bumptech.glide:compiler:$glide_version")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("earth.worldwind:worldwind-android:2.1.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.erain.studio)

    implementation(libs.devconfig)
    implementation(libs.module.update.gdpr)
    implementation(libs.play.review.ktx)
    implementation(libs.shimmer)
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.multidex)
}
