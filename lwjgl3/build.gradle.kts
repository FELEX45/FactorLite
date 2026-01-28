plugins {
    application
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.factorlite.lwjgl3.Lwjgl3LauncherKt")
}

sourceSets {
    named("main") {
        // Shared assets folder at repo root (used by both Desktop and Android)
        resources.srcDir(file("../assets"))
    }
}

dependencies {
    val gdxVersion: String by project

    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

