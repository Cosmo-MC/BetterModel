plugins {
    alias(libs.plugins.convention.bukkit)
}

dependencies {
    shade(project(":api")) { isTransitive = false }
    shade(project(":api:bukkit-api")) { isTransitive = false }
    shade(project(":core")) { isTransitive = false }
    shade("com.cosmomc:cosmo-pack-system:26.0.4") { isTransitive = false }
    shade("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    shade(project(":purpur"))
    rootProject.project("nms").subprojects.forEach {
        compileOnly(it)
    }

    shade(libs.bundles.shadedLibrary) {
        exclude("net.kyori")
        exclude("org.ow2.asm")
        exclude("io.leangen.geantyref")
    }

    compileOnly(libs.bundles.manifestLibrary)
}
