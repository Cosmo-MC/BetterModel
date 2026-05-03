/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit

import org.bukkit.plugin.Plugin

object BetterModelBukkitContext {
    @Volatile
    private var host: BetterModelBootstrapHost? = null

    fun initialize(host: BetterModelBootstrapHost) {
        synchronized(this) {
            this.host = host
        }
    }

    fun plugin(): Plugin = requireNotNull(host) { "BetterModel Bukkit host has not been initialized." }.plugin
}
