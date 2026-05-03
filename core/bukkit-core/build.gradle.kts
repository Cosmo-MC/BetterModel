plugins {
    alias(libs.plugins.convention.publish)
    alias(libs.plugins.convention.bukkit)
    alias(libs.plugins.shadow)
}

dependencies {
    shade(project(":api")) { isTransitive = false }
    shade(project(":api:bukkit-api")) { isTransitive = false }
    shade(project(":core")) { isTransitive = false }

    shade(project(":purpur"))
    rootProject.project("nms").subprojects.forEach {
        shade(project(it.path)) { isTransitive = false }
    }

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    api(libs.bundles.manifestLibrary)
    api(libs.armormodel)

    compileOnly("com.cosmomc:cosmo-pack-system-api:26.3.6")

    compileOnly("net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT") {
        exclude("net.byteflux")
    }
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.12.0")
    compileOnly("io.lumine:Mythic-Dist:5.11.2")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.getByName("shade"))
}

tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "bettermodel-bukkit-core"
        setArtifacts(listOf(tasks.shadowJar.get(), tasks.sourcesJar.get()))
        pom.withXml {
            val dependenciesNode = (asNode().get("dependencies") as groovy.util.NodeList)
                .firstOrNull() as? groovy.util.Node ?: return@withXml
            dependenciesNode.children()
                .filterIsInstance<groovy.util.Node>()
                .filter { dependencyNode ->
                    val groupId = (dependencyNode.get("groupId") as groovy.util.NodeList)
                        .firstOrNull()
                        ?.let { it as groovy.util.Node }
                        ?.text()
                    groupId == "com.cosmomc"
                }
                .forEach { dependencyNode ->
                    dependenciesNode.remove(dependencyNode)
                }
        }
    }
}
