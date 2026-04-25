plugins {
    java
    kotlin("jvm")
    id("com.github.hierynomus.license")
}

group = "com.cosmomc"
version = property("project_version").toString() + (BUILD_NUMBER?.let { "-SNAPSHOT-$it" } ?: "")

val shade = configurations.create("shade")

configurations.implementation {
    extendsFrom(shade)
}

dependencies {
    testImplementation(kotlin("test"))

    compileOnly(libs.bundles.library)
    testImplementation(libs.bundles.library)
}

tasks {
    test {
        useJUnitPlatform()
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
}

license {
    header = rootProject.file("LICENSE_HEADER")
    includes(setOf(
        "**/*.java",
        "**/*.kt"
    ))
    strictCheck = false
}

tasks.matching { it.name.startsWith("license") }.configureEach {
    enabled = false
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(JAVA_VERSION)
}

kotlin {
    jvmToolchain(JAVA_VERSION)
}

