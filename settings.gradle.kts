pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
    plugins {
        // Applied per Minecraft version in build.gradle.kts. 26.x is unobfuscated, so this is the
        // no-mappings variant of Loom.
        id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // Every Minecraft version this mod builds for. Per-version settings live in
        // stonecutter.properties.toml.
        versions("26.1.2", "26.2")
        // The version the code in src/ is written for when it's committed / handed off.
        vcsVersion = "26.2"
    }
}

rootProject.name = "hypaflippa"
