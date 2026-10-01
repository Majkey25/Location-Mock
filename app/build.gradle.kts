import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.majkeylab.locationmock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.majkeylab.locationmock"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0-rc.1"
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(
                    requireNotNull(keystoreProperties.getProperty("storeFile")) {
                        "storeFile is missing from keystore.properties"
                    },
                )
                storePassword = requireNotNull(keystoreProperties.getProperty("storePassword")) {
                    "storePassword is missing from keystore.properties"
                }
                keyAlias = requireNotNull(keystoreProperties.getProperty("keyAlias")) {
                    "keyAlias is missing from keystore.properties"
                }
                keyPassword = requireNotNull(keystoreProperties.getProperty("keyPassword")) {
                    "keyPassword is missing from keystore.properties"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("githubRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".github"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    sourceSets {
        getByName("debug") {
            kotlin.directories.add(project.file("src/vpn/java").absolutePath)
            res.directories.add(project.file("src/vpn/res").absolutePath)
            manifest.srcFile("src/vpn/AndroidManifest.xml")
        }
        getByName("githubRelease") {
            kotlin.directories.add(project.file("src/vpn/java").absolutePath)
            res.directories.add(project.file("src/vpn/res").absolutePath)
            manifest.srcFile("src/vpn/AndroidManifest.xml")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("com.wireguard.android:tunnel:1.0.20260102")
    add("githubReleaseImplementation", "com.wireguard.android:tunnel:1.0.20260102")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.20")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
