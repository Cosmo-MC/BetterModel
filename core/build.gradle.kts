plugins {
    alias(libs.plugins.convention.publish)
}

dependencies {
    api(project(":api"))

    compileOnly(libs.bundles.minecraft)
    compileOnly("com.mojang:authlib:7.0.61")

    compileOnly(libs.bundles.core)
    compileOnly(libs.cloud.core)

    implementation("com.cosmomc:cosmo-pack-system:26.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}
