plugins {
    id("com.android.application")
    kotlin("android")
}

val factorliteVersion: String by project
val factorliteVersionCode: String by project

// LibGDX Android requires native .so libs to be packaged into APK.
// We keep them in a separate configuration and copy into src/main/jniLibs per ABI.
val natives by configurations.creating

android {
    namespace = "com.factorlite.android"
    compileSdk = 34

    sourceSets {
        getByName("main") {
            // Shared assets folder at repo root (used by both Desktop and Android)
            assets.srcDir(file("../assets"))
        }
    }

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

tasks.register("copyAndroidNatives") {
    group = "build"
    description = "Extract LibGDX native .so libs into android/src/main/jniLibs/<abi>/ so they are packaged into the APK."

    doLast {
        val abiMap = mapOf(
            "natives-armeabi-v7a" to "armeabi-v7a",
            "natives-arm64-v8a" to "arm64-v8a",
            "natives-x86" to "x86",
            "natives-x86_64" to "x86_64",
        )

        val outRoot = file("src/main/jniLibs")
        // Clean old natives to avoid stale ABI mix.
        if (outRoot.exists()) outRoot.deleteRecursively()
        outRoot.mkdirs()

        val files = natives.files.toList()
        for (f in files) {
            val abi = abiMap.entries.firstOrNull { f.name.contains(it.key) }?.value ?: continue
            copy {
                from(zipTree(f))
                include("*.so")
                into(File(outRoot, abi))
            }
        }
    }
}

// Ensure natives are present for all builds.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("copyAndroidNatives")
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
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
}

