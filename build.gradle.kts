plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.mahjongcraft"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://shynixn.github.io/MCCoroutine/repository")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // MCCoroutine for Bukkit-safe coroutines
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.20.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.20.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Mahjong4j - core rules engine (pure Java)
    implementation("com.github.mahjong4j:mahjong4j:0.3.2")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("MahjongCraft-${project.version}.jar")

        relocate("org.mahjong4j", "com.mahjongcraft.libs.mahjong4j")
        relocate("kotlinx.serialization", "com.mahjongcraft.libs.serialization")
        relocate("kotlinx.coroutines", "com.mahjongcraft.libs.coroutines")
        relocate("com.github.shynixn.mccoroutine", "com.mahjongcraft.libs.mccoroutine")

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
        minecraftVersion("1.21.4")
    }
}
