plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.mahjongplay"
version = "1.5"

repositories {
    maven {
        name = "AliyunMavenCentral"
        url = uri("https://maven.aliyun.com/repository/central")
    }
    maven {
        name = "GoogleMavenCentral"
        url = uri("https://maven-central.storage-download.googleapis.com/maven2")
    }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://shynixn.github.io/MCCoroutine/repository")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:26.2.build.128-stable")

    // MCCoroutine for Bukkit-safe coroutines
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.20.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.20.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Mahjong4j - core rules engine (pure Java)
    implementation("com.github.mahjong4j:mahjong4j:0.3.2")

    // GlowingEntities - per-player entity glow without ProtocolLib
    implementation("fr.skytasul:glowingentities:2.0.1")
}

kotlin {
    jvmToolchain(25)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("MahjongPlay-${project.version}.jar")

        relocate("org.mahjong4j", "com.mahjongplay.libs.mahjong4j")
        relocate("fr.skytasul.glowingentities", "com.mahjongplay.libs.glowingentities")
        relocate("kotlinx.serialization", "com.mahjongplay.libs.serialization")
        relocate("kotlinx.coroutines", "com.mahjongplay.libs.coroutines")
        relocate("com.github.shynixn.mccoroutine", "com.mahjongplay.libs.mccoroutine")

        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    runServer {
        minecraftVersion("26.2")
    }
}
