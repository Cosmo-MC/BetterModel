plugins {
    alias(libs.plugins.convention.publish)
}

dependencies {
    api(project(":api"))

    compileOnly(libs.bundles.minecraft)
    compileOnly("com.mojang:authlib:7.0.61")

    compileOnly(libs.bundles.core)
    compileOnly(libs.cloud.core)

    compileOnly("com.cosmomc:cosmo-pack-system-api:26.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}
