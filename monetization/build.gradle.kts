plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "tpt.dev.monetization"

    buildFeatures {
        viewBinding = true
    }
    compileSdk = 35

    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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

}


kotlin {
    jvmToolchain(11) // đồng bộ với compileOptions
}

configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

dependencies {
    testImplementation(libs.junit)

    // Common
//    implementation(libs.androidx.constraint)
//    implementation(libs.androidx.startup)
//    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    // Admob
    implementation(libs.gma.next.gen.ads)
    implementation(libs.google.ads.mediation.pangle)
    // Audience Network bidding; the adapter brings its compatible Meta SDK.
    implementation(libs.google.ads.mediation.meta) {
        // The adapter's open-ended stdlib dependency can select Kotlin 2.3,
        // whose metadata cannot be read by this project's Kotlin 2.1 compiler.
        // Use the stdlib already supplied by the project's Kotlin plugin.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(libs.gms.adsIdentifier)
    implementation(libs.ump)
    implementation(libs.skeletonlayout)

    implementation(libs.billing.ktx)
    implementation(libs.kotpref.core)
    implementation(libs.kotpref.initializer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.google.material)
    implementation(libs.gson)
    implementation(libs.lottie)
}
