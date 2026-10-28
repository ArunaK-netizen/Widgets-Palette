import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
}

dependencies {
    implementation("io.github.cosinekitty:astronomy:2.1.19")
    testImplementation("junit:junit:4.13.2")
}

android {
    namespace = "com.lunarlucent"
    compileSdk = 34
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "com.company.widgetspalette"
        minSdk = 26
        targetSdk = 34
        versionCode = providers.gradleProperty("developmentVersionCode")
            .orNull
            ?.toIntOrNull()
            ?: 1
        versionName = "1.0"
    }

    buildTypes {
        create("development") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            versionNameSuffix = "-dev"
            firebaseAppDistribution {
                providers.gradleProperty("firebaseAppId").orNull?.let { appId = it }
                providers.gradleProperty("firebaseGroups").orNull?.let { groups = it }
                providers.gradleProperty("firebaseTesters").orNull?.let { testers = it }
                releaseNotes = providers.gradleProperty("firebaseReleaseNotes")
                    .orElse("Widget Palette development update")
                    .get()
                artifactType = "APK"
            }
        }
    }
}
