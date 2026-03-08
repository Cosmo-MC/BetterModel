/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit

import kr.toxicity.model.bukkit.BetterModelLibrary.ADVENTURE_PLATFORM
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.Plugin

object BetterModelBukkitContext {
    @Volatile
    private var host: BetterModelBootstrapHost? = null
    @Volatile
    private var audiences: BukkitAudiences? = null

    fun initialize(host: BetterModelBootstrapHost) {
        synchronized(this) {
            this.host = host
            audiences?.close()
            audiences = null
        }
    }

    fun plugin(): Plugin = requireNotNull(host) { "BetterModel Bukkit host has not been initialized." }.plugin

    fun audiencePlatform(): BukkitAudiences? {
        if (!ADVENTURE_PLATFORM.isLoaded) {
            return null
        }
        audiences?.let { return it }
        synchronized(this) {
            audiences?.let { return it }
            return BukkitAudiences.create(plugin()).also { audiences = it }
        }
    }

    fun closeAudiencePlatform() {
        synchronized(this) {
            audiences?.close()
            audiences = null
        }
    }
}
