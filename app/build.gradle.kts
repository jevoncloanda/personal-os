import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val releaseProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}
fun signingValue(environment: String, property: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: releaseProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("POROS_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("POROS_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("POROS_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("POROS_KEY_PASSWORD", "keyPassword")

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Checks local Poros release signing configuration without exposing credentials."
    doLast {
        val missing = listOf(
            "POROS_KEYSTORE_FILE / storeFile" to releaseStoreFile,
            "POROS_STORE_PASSWORD / storePassword" to releaseStorePassword,
            "POROS_KEY_ALIAS / keyAlias" to releaseKeyAlias,
            "POROS_KEY_PASSWORD / keyPassword" to releaseKeyPassword,
        ).filter { it.second == null }.map { it.first }
        check(missing.isEmpty()) {
            "Poros release signing is incomplete. Configure ${missing.joinToString()} " +
                "through environment variables or ignored keystore.properties. See docs/release-signing.md."
        }
        check(rootProject.file(requireNotNull(releaseStoreFile)).isFile) {
            "Poros release keystore was not found. Check POROS_KEYSTORE_FILE / storeFile."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseSigning)
}

android {
    namespace = "com.jevon.personalos"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jevon.personalos"
        minSdk = 26
        targetSdk = 36
        // Increase versionCode for every distributed release, keeping it identical on both PCs.
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let { rootProject.file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    lint { abortOnError = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    kapt("androidx.room:room-compiler:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Strict streaming JSON parser only; backup field mapping/canonical serialization are explicit.
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
