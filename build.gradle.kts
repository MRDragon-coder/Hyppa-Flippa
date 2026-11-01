// Runs once per Minecraft version listed in settings.gradle.kts (as the versions/<version>
// subprojects). `sc.current` is the version being built; `sc.properties` reads
// stonecutter.properties.toml for that version.
plugins {
    id("net.fabricmc.fabric-loom")
}

val modId: String = sc.properties["mod.id"]
val modVersion: String = sc.properties["mod.version"]

// DO NOT set group = ... (Stonecutter uses it to tell the version projects apart).
version = "$modVersion+${sc.current.version}"
base.archivesName = modId

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        // One shared run/ folder (worlds, options, config) for every version.
        runDirectory = rootProject.file("run")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8" // source contains ➜ / ≥ / ▲ characters
    }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to sc.properties.get<String>("mod.name"),
            "version" to project.version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to sc.properties.get<String>("deps.fabric_loader"),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        inputs.property("modId", modId)
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    }

    // `./gradlew buildAndCollect` builds every version and copies the jars to build/libs/<mod version>/.
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/<mod version>/ in the root project"
        from(named<Jar>("jar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
