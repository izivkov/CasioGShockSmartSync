plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.dokka") version("1.9.20")

    kotlin("plugin.serialization") version "1.9.0"
    id("maven-publish")
}

android {
    namespace = "org.avmedia.translateapi"

    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.translator)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.runtime.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.avmedia"
            artifactId = "translateApi"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}


tasks.dokkaHtml {

    outputDirectory.set(file("${rootDir}/docs"))

    dokkaSourceSets {
        configureEach {
            includes.from("module.md")

            includeNonPublic.set(false)
            skipDeprecated.set(true)
            reportUndocumented.set(false) // Emit warnings about not documented members
        }
    }
}
