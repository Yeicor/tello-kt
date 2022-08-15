plugins {
    kotlin("multiplatform") version "1.7.10"
    id("com.android.application")
}

group = "org.duckdns.yeicor"
version = "0.0.1"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // Native platforms share sources
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    android()
    iosArm64 {
        binaries {
            framework {
                baseName = "library"
            }
        }
    }

    @Suppress("UNUSED_VARIABLE") sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:2.1.0") // UDP sockets
                implementation("com.soywiz.korlibs.korio:korio:3.0.0") // IO
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val nativeMain by getting
        val nativeTest by getting
        val androidMain by getting {
            dependencies {
                implementation("com.google.android.material:material:1.6.1")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
        val iosArm64Main by getting
        val iosArm64Test by getting
    }
}

@Suppress("UnstableApiUsage")
android {
    compileSdk = 32
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        applicationId = "org.duckdns.yeicor.tellocopilot"
        minSdk = 16
        targetSdk = 32
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
