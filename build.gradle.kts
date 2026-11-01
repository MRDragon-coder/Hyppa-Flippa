// Runs once per Minecraft version listed in settings.gradle.kts (as the versions/<version>
// subprojects). `sc.current` is the version being built; `sc.properties` reads
// stonecutter.properties.toml for that version.
plugins {
    id("net.fabricmc.fabric-loom")
    id("me.modmuss50.mod-publish-plugin")
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

// --- Releasing: `./gradlew publishMods` uploads every version's jar to Modrinth.
//
// Needs a Modrinth token (modrinth.com/settings/pats, scopes: create/read/write versions) in
// your user-wide ~/.gradle/gradle.properties as `modrinth.token=...`, or in the MODRINTH_TOKEN
// environment variable. Never put it in this project. Without a token, or without
// publish.modrinth_id in stonecutter.properties.toml, it only does a dry run: the upload is
// prepared in build/publishMods/ but nothing is sent.

val modrinthToken = providers.gradleProperty("modrinth.token").orElse(providers.environmentVariable("MODRINTH_TOKEN"))
val modrinthId: String = sc.properties["publish.modrinth_id"]

// The changelog of this release: the "## <mod version>" section of CHANGELOG.md.
val releaseNotes = providers.provider {
    val text = rootProject.file("CHANGELOG.md").readText()
    val heading = Regex("^## ${Regex.escape(modVersion)}\\b.*$", RegexOption.MULTILINE).find(text)
        ?: error("CHANGELOG.md has no \"## $modVersion\" section")
    val rest = text.substring(heading.range.last + 1)
    val end = Regex("^## ", RegexOption.MULTILINE).find(rest)?.range?.first ?: rest.length
    rest.substring(0, end).trim()
}

publishMods {
    file.set(tasks.jar.flatMap { it.archiveFile })
    changelog.set(releaseNotes)
    type.set(STABLE)
    version.set(project.version.toString())
    displayName.set("${sc.properties.get<String>("mod.name")} $modVersion for ${sc.current.version}")
    modLoaders.add("fabric")
    dryRun.set(modrinthToken.map { false }.orElse(true).map { it || modrinthId.isBlank() })

    modrinth {
        projectId.set(modrinthId)
        accessToken.set(modrinthToken)
        minecraftVersions.add(sc.current.version)
        environment.set(CLIENT_ONLY)
        requires("fabric-api")
    }
}
