plugins {
    alias(libs.plugins.convention.standard)
    alias(libs.plugins.shadow)
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val minecraft = property("minecraft_version").toString()
val versionString = version.toString()
val groupString = group.toString()

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
    }
    shadowJar {
        enabled = false
    }
}
