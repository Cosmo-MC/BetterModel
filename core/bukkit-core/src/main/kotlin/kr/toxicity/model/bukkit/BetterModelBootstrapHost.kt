/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit

import org.bukkit.plugin.Plugin
import java.io.File
import java.io.InputStream
import java.util.jar.Attributes

interface BetterModelBootstrapHost {
    val plugin: Plugin
    val dataFolder: File
    val jarFile: File

    fun getResource(path: String): InputStream?
    fun saveResource(path: String)
    fun attributes(): Attributes
    fun disable()
}
