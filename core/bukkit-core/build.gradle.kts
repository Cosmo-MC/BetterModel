plugins {
    alias(libs.plugins.convention.publish)
    alias(libs.plugins.convention.bukkit)
    alias(libs.plugins.shadow)
}

dependencies {
    shade(project(":api")) { isTransitive = false }
    shade(project(":api:bukkit-api")) { isTransitive = false }
    shade(project(":core")) { isTransitive = false }
    shade("com.cosmomc:cosmo-pack-system-api:26.3.3") { isTransitive = false }
    shade("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    shade(project(":purpur"))
    rootProject.project("nms").subprojects.forEach {
        shade(project(it.path)) { isTransitive = false }
    }

    shade(libs.bundles.manifestLibrary)
    shade(libs.bundles.shadedLibrary) {
        exclude("net.kyori")
    }

    compileOnly(libs.bundles.manifestLibrary)

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
    dependencies {
        exclude(dependency("org.jetbrains:annotations:.*"))
    }
    relocate("gg.moonflower.molangcompiler", "com.cosmomc.shaded.gg.moonflower.molangcompiler")
    relocate("io.leangen.geantyref", "com.cosmomc.shaded.io.leangen.geantyref")
    relocate("org.objectweb.asm", "com.cosmomc.shaded.org.objectweb.asm")
}

tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "bettermodel-bukkit-core"
        setArtifacts(listOf(tasks.shadowJar.get(), tasks.sourcesJar.get()))
        pom.withXml {
            val dependencies = asNode().get("dependencies")
            if (dependencies is groovy.util.NodeList) {
                dependencies.forEach { asNode().remove(it as groovy.util.Node) }
            }
        }
    }
}
