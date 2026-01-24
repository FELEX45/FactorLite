plugins {
    // Версии плагинов задаём в корне, а в модулях подключаем без version(...)
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

