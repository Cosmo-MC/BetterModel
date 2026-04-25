/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream
import java.util.Objects
import java.util.jar.Attributes
import java.util.jar.Manifest

class JavaPluginBetterModelHost(
    private val delegate: JavaPlugin,
) : BetterModelBootstrapHost {
    private var manifestAttributes: Attributes? = null

    override val plugin: Plugin
        get() = delegate

    override val dataFolder: File
        get() = delegate.dataFolder

    override val jarFile: File
        get() = File(delegate.javaClass.protectionDomain.codeSource.location.toURI())

    override fun getResource(path: String): InputStream? = delegate.getResource(path)

    override fun saveResource(path: String) {
        delegate.saveResource(path, false)
    }

    override fun attributes(): Attributes {
        manifestAttributes?.let { return it }
        synchronized(this) {
            manifestAttributes?.let { return it }
            delegate.javaClass.classLoader.getResourceAsStream("META-INF/MANIFEST.MF").use { stream ->
                return Manifest(Objects.requireNonNull(stream, "Unable to read plugin manifest")).mainAttributes.also {
                    manifestAttributes = it
                }
            }
        }
    }

    override fun disable() {
        Bukkit.getPluginManager().disablePlugin(delegate)
    }
}
