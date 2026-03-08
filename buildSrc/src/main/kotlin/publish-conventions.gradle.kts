plugins {
    id("standard-conventions")
    `maven-publish`
}

val artifactBaseId = "${rootProject.name.lowercase()}-$name"
val artifactVersion = project.version.toString()

java {
    withSourcesJar()
}

fun Project.settingValue(key: String): String? {
    val envValue = System.getenv(key)?.takeIf { it.isNotBlank() }
    if (envValue != null) return envValue
    val envFile = rootProject.file(".env")
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

dependencies {
    api(libs.bundles.library)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.cosmomc"
            artifactId = artifactBaseId
            version = artifactVersion

            pom {
                name = artifactBaseId
                description = "BetterModel runtime and platform modules"
            }
        }
    }

    repositories {
        maven {
            name = "cosmomc"
            url = uri(settingValue("COSMO_REPO_URL") ?: "https://repo.cosmomcinfra.com/private")

            credentials {
                username = settingValue("MAVEN_USERNAME") ?: ""
                password = settingValue("MAVEN_PASSWORD") ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
