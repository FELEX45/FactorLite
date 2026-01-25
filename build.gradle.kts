plugins {
    // Версии плагинов задаём в корне, а в модулях подключаем без version(...)
    kotlin("jvm") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    id("com.android.application") version "8.3.2" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

