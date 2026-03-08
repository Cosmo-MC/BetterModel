plugins {
    alias(libs.plugins.convention.standard)
    alias(libs.plugins.shadow)
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val minecraft = property("minecraft_version").toString()
val versionString = version.toString()
val groupString = group.toString()

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaGenerate)
    archiveClassifier = "javadoc"
    from(layout.buildDirectory.dir("dokka/html").orNull?.asFile)
}

runPaper {
    disablePluginJarDetection()
}

tasks {
    runServer {
        pluginJars(fileTree("plugins"))
        pluginJars(project(":platform:paper").tasks.shadowJar.flatMap {
            it.archiveFile
        })
        pluginJars(project(":test-plugin").tasks.jar.flatMap {
            it.archiveFile
        })
        version(minecraft)
    }
    build {
        finalizedBy(
            javadocJar
        )
    }
    shadowJar {
        enabled = false
    }
}
