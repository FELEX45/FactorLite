plugins {
    id("com.android.application")
    kotlin("android")
}

import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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
            // Generated launcher icons (so we don't need to commit binary PNGs to the repo)
            res.srcDir(layout.buildDirectory.dir("generated/launcherIconRes"))
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

tasks.register("generateLauncherIcons") {
    group = "build"
    description = "Generate Android launcher icons (ic_launcher/ic_launcher_round) into build/generated/launcherIconRes."

    val outDirProvider = layout.buildDirectory.dir("generated/launcherIconRes")
    outputs.dir(outDirProvider)

    doLast {
        // Headless-friendly rendering
        System.setProperty("java.awt.headless", "true")

        val outRoot = outDirProvider.get().asFile
        if (outRoot.exists()) outRoot.deleteRecursively()
        outRoot.mkdirs()

        // Optional: if user provides their own source icon, we will use it.
        // Recommended: a square PNG 512x512 or 1024x1024 with transparent background.
        //
        // Supported locations (first found wins):
        // - <repo>/android/launcher-icon.png
        // - <repo>/launcher-icon.png
        // - <repo>/иконКа.png (user-provided filename)
        val iconCandidates = listOf(
            file("launcher-icon.png"),
            rootProject.file("launcher-icon.png"),
            rootProject.file("иконКа.png"),
        )
        val userIconFile = iconCandidates.firstOrNull { it.exists() }
        val userIcon: BufferedImage? = if (userIconFile != null) {
            runCatching { ImageIO.read(userIconFile) }.getOrNull()
        } else null

        data class Density(val qualifier: String, val sizePx: Int)
        val densities = listOf(
            Density("mipmap-mdpi", 48),
            Density("mipmap-hdpi", 72),
            Density("mipmap-xhdpi", 96),
            Density("mipmap-xxhdpi", 144),
            Density("mipmap-xxxhdpi", 192),
        )

        fun cropCenterSquare(src: BufferedImage): BufferedImage {
            val side = minOf(src.width, src.height)
            val x = (src.width - side) / 2
            val y = (src.height - side) / 2
            return src.getSubimage(x, y, side, side)
        }

        fun drawFallbackIcon(file: File, size: Int, round: Boolean) {
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

                val bg = AwtColor(18, 22, 28)         // dark slate
                val accent = AwtColor(18, 140, 70)    // green-ish
                val border = AwtColor(255, 255, 255, 36)
                val text = AwtColor(245, 248, 252)

                if (round) {
                    val clip = Ellipse2D.Float(0f, 0f, size.toFloat(), size.toFloat())
                    g.clip = clip
                }

                // background
                g.color = bg
                g.fillRect(0, 0, size, size)

                // accent diagonal stripe
                g.color = accent
                val stripeW = (size * 0.18f).toInt().coerceAtLeast(6)
                g.fillRect((size * 0.10f).toInt(), (size * 0.65f).toInt(), (size * 0.80f).toInt(), stripeW)

                // subtle border
                g.color = border
                g.stroke = BasicStroke((size * 0.03f).coerceAtLeast(2f))
                if (round) {
                    val inset = (size * 0.06f)
                    g.draw(Ellipse2D.Float(inset, inset, size - inset * 2f, size - inset * 2f))
                } else {
                    val inset = (size * 0.06f).toInt()
                    g.drawRect(inset, inset, size - inset * 2, size - inset * 2)
                }

                // "F" letter
                val fontSize = (size * 0.62f).toInt().coerceAtLeast(18)
                g.font = Font("SansSerif", Font.BOLD, fontSize)
                val fm = g.fontMetrics
                val label = "F"
                val tw = fm.stringWidth(label)
                val th = fm.ascent
                val x = (size - tw) / 2
                val y = (size + th) / 2 - (size * 0.06f).toInt()
                g.color = text
                g.drawString(label, x, y)
            } finally {
                g.dispose()
            }
            file.parentFile.mkdirs()
            ImageIO.write(img, "png", file)
        }

        fun drawFromUserIcon(file: File, size: Int, round: Boolean, src: BufferedImage) {
            val sq = cropCenterSquare(src)
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                if (round) g.clip = Ellipse2D.Float(0f, 0f, size.toFloat(), size.toFloat())

                // Add a bit of padding for the round icon so "horns"/edges are less likely to be clipped.
                val pad = if (round) (size * 0.10f).toInt().coerceAtLeast(2) else 0
                val dst = size - pad * 2
                g.drawImage(sq, pad, pad, dst, dst, null)
            } finally {
                g.dispose()
            }
            file.parentFile.mkdirs()
            ImageIO.write(img, "png", file)
        }

        for (d in densities) {
            val dir = File(outRoot, d.qualifier)
            val f1 = File(dir, "ic_launcher.png")
            val f2 = File(dir, "ic_launcher_round.png")
            val src = userIcon
            if (src != null) {
                drawFromUserIcon(f1, d.sizePx, round = false, src = src)
                drawFromUserIcon(f2, d.sizePx, round = true, src = src)
            } else {
                drawFallbackIcon(f1, d.sizePx, round = false)
                drawFallbackIcon(f2, d.sizePx, round = true)
            }
        }
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
    dependsOn("generateLauncherIcons")
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

