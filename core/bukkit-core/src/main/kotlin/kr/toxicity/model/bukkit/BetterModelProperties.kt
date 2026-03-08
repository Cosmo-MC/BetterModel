/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit

import com.vdurmont.semver4j.Semver
import kr.toxicity.model.BetterModelEvaluatorImpl
import kr.toxicity.model.api.BetterModelConfig
import kr.toxicity.model.api.BetterModelPlatform.ReloadResult
import kr.toxicity.model.api.bukkit.BetterModelBukkit
import kr.toxicity.model.api.event.PluginEndReloadEvent
import kr.toxicity.model.api.event.PluginStartReloadEvent
import kr.toxicity.model.api.pack.PackZipper
import kr.toxicity.model.api.version.MinecraftVersion.V1_21_11
import kr.toxicity.model.api.version.MinecraftVersion.parse
import kr.toxicity.model.bukkit.configuration.PluginConfiguration
import kr.toxicity.model.bukkit.manager.EntityManager
import kr.toxicity.model.bukkit.manager.PlayerManagerImpl
import kr.toxicity.model.bukkit.scheduler.BukkitScheduler
import kr.toxicity.model.bukkit.scheduler.PaperScheduler
import kr.toxicity.model.manager.*
import kr.toxicity.model.util.callEvent
import kr.toxicity.model.util.handleException
import kr.toxicity.model.util.toComponent
import kr.toxicity.model.util.warn
import org.bukkit.Bukkit

private typealias Latest = kr.toxicity.model.bukkit.nms.v1_21_R7.NMSImpl

internal class BetterModelProperties(
    private val host: BetterModelBootstrapHost
) {
    private lateinit var _config: BetterModelConfig

    val version = parse(Bukkit.getBukkitVersion().substringBefore('-'))
    val nms = when (version) {
        V1_21_11 -> Latest()
        else if BetterModelBukkit.IS_PAPER -> {
            warn(
                "Note: this version is officially untested.".toComponent(),
                "So be careful to use!".toComponent()
            )
            Latest()
        }
        else -> throw RuntimeException("Unsupported version: $version")
    }
    val scheduler = if (BetterModelBukkit.IS_FOLIA) PaperScheduler() else BukkitScheduler()
    val evaluator = BetterModelEvaluatorImpl()
    val eventbus = BukkitModelEventBusImpl()
    @Suppress("DEPRECATION") //To support Spigot :(
    val semver = Semver(host.plugin.description.version, Semver.SemverType.LOOSE)
    val snapshot = runCatching {
        host.attributes().getValue("Dev-Build").toInt()
    }.getOrElse {
        it.handleException("Unable to parse manifest's build data")
        -1
    }
    var config
        get() = _config
        set(value) {
            _config = value
        }
    val managers by lazy {
        listOf(
            ArmorManager,
            ProfileManagerImpl,
            SkinManagerImpl,
            ModelManagerImpl,
            PlayerManagerImpl,
            EntityManager,
            ScriptManagerImpl
        )
    }

    var reloadStartTask: (PackZipper) -> Unit = { callEvent { PluginStartReloadEvent(it) } }
    var reloadEndTask: (ReloadResult) -> Unit = { callEvent { PluginEndReloadEvent(it) } }

    init {
        config = BetterModelConfigImpl(PluginConfiguration.CONFIG.create())
    }
}
