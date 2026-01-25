plugins {
    id("com.android.application")
    kotlin("android")
}

val factorliteVersion: String by project
val factorliteVersionCode: String by project

android {
    namespace = "com.factorlite.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.factorlite.android"
        minSdk = 21
        targetSdk = 34
        versionCode = factorliteVersionCode.toInt()
        versionName = factorliteVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.addAll(
            listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
            ),
        )
    }
}

tasks.register<Copy>("copyDebugApk") {
    group = "build"
    description = "Copy debug APK to a versioned filename (factorlite_v.<version>.apk)."

    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")
    val fromApk = apkDir.map { it.file("android-debug.apk") }
    val outDir = layout.buildDirectory.dir("outputs/apk/versioned")

    from(fromApk)
    into(outDir)
    rename { _ -> "factorlite_v.$factorliteVersion.apk" }
}

// Android tasks are created lazily by AGP; use a lazy matcher instead of tasks.named(...)
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyDebugApk")
}

dependencies {
    val gdxVersion: String by project

    implementation(project(":core"))

    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
}

