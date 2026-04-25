fun settingValue(key: String): String? {
    val envValue = System.getenv(key)?.takeIf { it.isNotBlank() }
    if (envValue != null) return envValue
    val envFile = file(".env")
    if (!envFile.exists()) return null
    return envFile.readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .map {
            val index = it.indexOf('=')
            it.substring(0, index).trim() to it.substring(index + 1).trim().trim('"')
        }
        .firstOrNull { (name, _) -> name == key }
        ?.second
        ?.takeIf { it.isNotBlank() }
}

val cosmoRepoUrl = settingValue("COSMO_REPO_URL") ?: "https://repo.cosmomcinfra.com/private"
val cosmoRepoUsername = settingValue("MAVEN_USERNAME") ?: ""
val cosmoRepoPassword = settingValue("MAVEN_PASSWORD") ?: ""

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://repo.alessiodp.com/releases/")
        maven("https://maven.blamejared.com/")
        maven("https://repo.purpurmc.org/snapshots")
        maven("https://maven.citizensnpcs.co/repo/")
        maven("https://mvn.lumine.io/repository/maven-public/")
        maven("https://maven.nucleoid.xyz/")
        maven(cosmoRepoUrl) {
            credentials {
                username = cosmoRepoUsername
                password = cosmoRepoPassword
            }
        }
    }
}

rootProject.name = "BetterModel"

include(
    //api
    "api",
    "api:bukkit-api",

    //core
    "core",
    "core:bukkit-core",

    "purpur",

    "nms:v1_21_R7",
    "nms:v26_R1",

    //test
    "test-plugin"
)
