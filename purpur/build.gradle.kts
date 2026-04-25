plugins {
    alias(libs.plugins.convention.standard)
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":api:bukkit-api"))
    compileOnly("org.purpurmc.purpur:purpur-api:${property("minecraft_version")}-R0.1-SNAPSHOT")
}
