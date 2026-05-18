import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.0")

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)

            implementation(libs.coil.compose)
            implementation(libs.coil.network)

            implementation("com.russhwolf:multiplatform-settings:1.3.0")
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")
            implementation("com.russhwolf:multiplatform-settings-coroutines:1.3.0")

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.navigation.compose)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.koin.android)
            implementation(libs.coroutines.android)
            implementation(libs.appauth)
            implementation(libs.workmanager)
            implementation(libs.androidx.security.crypto)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.core)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
        }
    }
}

android {
    namespace = "com.conduit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.conduit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["appAuthRedirectScheme"] = "conduit"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("conduit.keystore")
            storePassword = "conduit123"
            keyAlias = "conduit"
            keyPassword = "conduit123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.conduit.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Conduit"
            packageVersion = "1.0.0"
            linux {
                iconFile.set(project.file("src/desktopMain/resources/conduit_logo.png"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/conduit_logo.png"))
                menuGroup = "Conduit"
                upgradeUuid = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
            }
        }
    }
}

tasks.register("updateDesignTokens") {
    group = "design"
    description = "Updates the design tokens from the nox repository"
    doLast {
        val noxRepoDir = file("../../nox_repo")
        try {
            val process = if (noxRepoDir.exists()) {
                println("Pulling latest design tokens from nox repository...")
                ProcessBuilder("git", "pull")
                    .directory(noxRepoDir)
                    .inheritIO()
                    .start()
            } else {
                println("nox_repo not found. Cloning repository...")
                ProcessBuilder("git", "clone", "https://github.com/pebrd/nox.git", "nox_repo")
                    .directory(file("../.."))
                    .inheritIO()
                    .start()
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                println("Warning: Git execution failed with exit code $exitCode. Using cached/local tokens.")
            }
        } catch (e: Exception) {
            println("Warning: Failed to update design tokens repo: ${e.message}. Using cached/local tokens.")
        }

        val sourceFile = file("../../nox_repo/dist/kotlin/NoxTokens.kt")
        val destFile = file("src/commonMain/kotlin/nox/designsystem/NoxTokens.kt")
        if (sourceFile.exists()) {
            println("Updating NoxTokens.kt...")
            destFile.parentFile.mkdirs()
            val content = sourceFile.readText()
            destFile.writeText(content)
            println("NoxTokens.kt successfully updated!")
        } else {
            if (!destFile.exists()) {
                throw org.gradle.api.GradleException("NoxTokens.kt is missing and could not be fetched from nox_repo. Please check your internet connection.")
            } else {
                println("Warning: NoxTokens.kt not found in nox_repo, but local version exists. Keeping local version.")
            }
        }
    }
}

tasks.matching { it.name.startsWith("compileKotlin") || it.name.startsWith("generate") }.configureEach {
    dependsOn("updateDesignTokens")
}

