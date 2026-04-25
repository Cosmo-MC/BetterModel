/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit

import kr.toxicity.model.BetterModelPlatformImpl
import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.BetterModelConfig
import kr.toxicity.model.api.BetterModelEvaluator
import kr.toxicity.model.api.BetterModelLogger
import kr.toxicity.model.api.BetterModelPlatform
import kr.toxicity.model.api.BetterModelPlatform.ReloadResult
import kr.toxicity.model.api.BetterModelPlatform.ReloadResult.Failure
import kr.toxicity.model.api.BetterModelPlatform.ReloadResult.OnReload
import kr.toxicity.model.api.BetterModelPlatform.ReloadResult.Success
import kr.toxicity.model.api.asset.CompiledModelAsset
import kr.toxicity.model.api.asset.CompiledModelProvider
import kr.toxicity.model.api.bukkit.BukkitModelEventBus
import kr.toxicity.model.api.bukkit.BetterModelBukkit
import kr.toxicity.model.api.bukkit.scheduler.BukkitModelScheduler
import kr.toxicity.model.api.manager.ModelManager
import kr.toxicity.model.api.manager.ReloadInfo
import kr.toxicity.model.api.manager.PlayerManager
import kr.toxicity.model.api.manager.ProfileManager
import kr.toxicity.model.api.manager.ScriptManager
import kr.toxicity.model.api.manager.SkinManager
import kr.toxicity.model.api.nms.NMS
import kr.toxicity.model.api.pack.PackMeta
import kr.toxicity.model.api.pack.PackResult
import kr.toxicity.model.api.pack.PackZipper
import kr.toxicity.model.api.version.MinecraftVersion
import kr.toxicity.model.bukkit.command.startBukkitCommand
import kr.toxicity.model.bukkit.util.audience
import kr.toxicity.model.bukkit.util.registerListener
import kr.toxicity.model.manager.ArmorManager
import kr.toxicity.model.manager.GlobalManager
import org.semver4j.Semver
import kr.toxicity.model.manager.ModelManagerImpl
import kr.toxicity.model.manager.ProfileManagerImpl
import kr.toxicity.model.manager.ReloadPipeline
import kr.toxicity.model.manager.ScriptManagerImpl
import kr.toxicity.model.manager.SkinManagerImpl
import kr.toxicity.model.util.LATEST_VERSION
import kr.toxicity.model.util.componentOf
import kr.toxicity.model.util.emptyComponentOf
import kr.toxicity.model.util.handleException
import kr.toxicity.model.util.ifNull
import kr.toxicity.model.util.info
import kr.toxicity.model.util.infoNotNull
import kr.toxicity.model.util.toIndicator
import kr.toxicity.model.util.toComponent
import kr.toxicity.model.util.warn
import kr.toxicity.model.util.withComma
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.format.NamedTextColor.AQUA
import net.kyori.adventure.text.format.NamedTextColor.DARK_RED
import net.kyori.adventure.text.format.NamedTextColor.GREEN
import net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
import net.kyori.adventure.text.format.NamedTextColor.RED
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.ServerLoadEvent
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.jar.JarEntry
import java.util.jar.JarFile

class BetterModelBootstrap(
    private val host: BetterModelBootstrapHost,
    private val jarType: BetterModelPlatform.JarType = BetterModelPlatform.JarType.PAPER,
) : BetterModelPlatformImpl, BetterModelBukkit {
    private var skipInitialReload = false
    private var compiledModelProvider: CompiledModelProvider? = null
    private var dataFolderOverride: File? = null
    private val onReload = AtomicBoolean()
    private val firstLoad = AtomicBoolean()
    private val adapter = kr.toxicity.model.api.bukkit.platform.BukkitAdapter()
    private lateinit var props: BetterModelProperties

    private val logger = object : BetterModelLogger {
        private val internalLogger by lazy {
            net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(host.plugin.logger.name)
        }

        override fun info(vararg message: net.kyori.adventure.text.Component) {
            synchronized(this) {
                message.forEach(internalLogger::info)
            }
        }

        override fun warn(vararg message: net.kyori.adventure.text.Component) {
            synchronized(this) {
                message.forEach(internalLogger::warn)
            }
        }
    }

    fun skipInitialReload() {
        skipInitialReload = true
    }

    fun compiledModelProvider(provider: CompiledModelProvider?): BetterModelBootstrap {
        compiledModelProvider = provider
        return this
    }

    fun dataFolder(directory: File): BetterModelBootstrap {
        dataFolderOverride = directory
        return this
    }

    fun compiledModelsDirectory(directory: File): BetterModelBootstrap {
        compiledModelProvider = CompiledModelProvider {
            if (!directory.exists()) {
                emptyList()
            } else {
                Files.walk(directory.toPath()).use { stream ->
                    stream.filter { path ->
                        Files.isRegularFile(path) && path.fileName.toString().endsWith(".json", ignoreCase = true)
                    }.map { path ->
                        CompiledModelAsset(path.fileName.toString()) {
                            Files.newInputStream(path)
                        }
                    }.toList()
                }
            }
        }
        return this
    }

    fun onLoad() {
        BetterModelBukkitContext.initialize(host)
        BetterModelLibrary().load(host)
        BetterModel.compiledModelProvider(compiledModelProvider)
        BetterModel.register(this)
        props = runCatching {
            BetterModelProperties(host)
        }.getOrElse {
            warn(
                "Unable to start BetterModel.".toComponent(),
                "Reason: ${it.message ?: "Unknown"}".toComponent(RED),
                "Stack trace: ${it.stackTraceToString()}".toComponent(RED),
                "Plugin will be automatically disabled.".toComponent(DARK_RED)
            )
            host.disable()
            throw it
        }
    }

    fun onEnable() {
        props.managers.forEach(GlobalManager::start)
        if (isSnapshot) warn(
            "This build is dev version: be careful to use it!".toComponent(),
            "Build number: ${props.snapshot}".toComponent(LIGHT_PURPLE)
        )
        startBukkitCommand()
        registerListener(object : Listener {
            @EventHandler
            fun PlayerJoinEvent.join() {
                if (!player.isOp || !config().versionCheck()) return
                props.scheduler.asyncTask {
                    val result = LATEST_VERSION
                    player.audience().infoNotNull(
                        result.release
                            ?.takeIf { props.semver < it.versionNumber() }
                            ?.let { version -> componentOf("New BetterModel release found: ") { append(version.toURLComponent()) } },
                        result.snapshot
                            ?.takeIf { props.semver < it.versionNumber() }
                            ?.let { version -> componentOf("New BetterModel snapshot found: ") { append(version.toURLComponent()) } }
                    )
                }
            }

            @EventHandler
            fun ServerLoadEvent.load() {
                if (skipInitialReload || type != ServerLoadEvent.LoadType.STARTUP) return
                when (val result = reload(ReloadInfo(true, Audience.empty()))) {
                    is Failure -> result.throwable.handleException("Unable to load plugin properly.")
                    is OnReload -> throw RuntimeException("Plugin load failed.")
                    is Success -> info(
                        "Plugin is loaded. (${result.totalTime().withComma()} ms)".toComponent(GREEN),
                        "Minecraft version: ${props.version}, NMS version: ${props.nms.version()}".toComponent(AQUA),
                        "Platform: ${
                            when {
                                BetterModelBukkit.IS_FOLIA -> "Folia"
                                BetterModelBukkit.IS_PURPUR -> "Purpur"
                                BetterModelBukkit.IS_PAPER -> "Paper"
                                else -> "Bukkit"
                            }
                        }".toComponent(AQUA)
                    )
                }
            }
        })
    }

    fun onDisable() {
        if (!firstLoad.get()) return
        props.managers.forEach(GlobalManager::end)
        BetterModel.compiledModelProvider(null)
        BetterModelBukkitContext.closeAudiencePlatform()
    }

    override fun reload(info: ReloadInfo): ReloadResult {
        if (!onReload.compareAndSet(false, true)) return OnReload.INSTANCE
        return runCatching {
            if (!info.skipConfig) props.config = BetterModelConfigImpl(kr.toxicity.model.bukkit.configuration.PluginConfiguration.CONFIG.create())
            val zipper = PackZipper.zipper().also(props.reloadStartTask)
            ReloadPipeline(config().indicator().options.toIndicator(info)).use { pipeline ->
                val time = System.currentTimeMillis()
                props.managers.forEach { it.reload(pipeline, zipper) }
                Success(firstLoad.compareAndSet(false, true), System.currentTimeMillis() - time, disabledPackResult())
            }
        }.getOrElse { Failure(it) }.apply {
            onReload.set(false)
        }.also(props.reloadEndTask)
    }

    private fun disabledPackResult(): PackResult {
        deleteLegacyPackOutput()
        return PackResult(PackMeta.builder().description("BetterModel runtime payload.").build(), null).apply { freeze() }
    }

    private fun deleteLegacyPackOutput() {
        val buildFolder = File(dataFolder().parentFile, config().buildFolderLocation())
        val zipFile = File(dataFolder().parentFile, "${config().buildFolderLocation()}.zip")
        if (zipFile.exists()) zipFile.delete()
        if (buildFolder.exists()) buildFolder.deleteRecursively()
    }

    override fun loadAssets(pipeline: ReloadPipeline, prefix: String, consumer: BiConsumer<String, InputStream>) {
        JarFile(host.jarFile).use { jar ->
            pipeline.forEachParallel(
                jar.entries().asSequence().filter { entry ->
                    entry.name.startsWith(prefix) && entry.name.length > prefix.length + 1 && !entry.isDirectory
                }.toList(),
                JarEntry::getSize
            ) { entry ->
                jar.getInputStream(entry).use { stream ->
                    consumer.accept(entry.name.substring(prefix.length + 1), stream)
                }
            }
        }
    }

    override fun saveResource(resourcePath: String) {
        val targetFolder = dataFolderOverride
        if (targetFolder == null) {
            host.saveResource(resourcePath)
            return
        }
        val target = File(targetFolder, resourcePath)
        if (target.exists()) return
        target.parentFile?.mkdirs()
        host.getResource(resourcePath).ifNull { "Resource '$resourcePath' not found." }.use { input ->
            target.outputStream().use(input::copyTo)
        }
    }
    override fun dataFolder(): File = dataFolderOverride ?: host.dataFolder
    override fun isEnabled(): Boolean = host.plugin.isEnabled
    override fun logger(): BetterModelLogger = logger
    override fun scheduler(): BukkitModelScheduler = props.scheduler
    override fun evaluator(): BetterModelEvaluator = props.evaluator
    override fun eventBus(): BukkitModelEventBus = props.eventbus
    override fun modelManager(): ModelManager = ModelManagerImpl
    override fun playerManager(): PlayerManager = kr.toxicity.model.bukkit.manager.PlayerManagerImpl
    override fun scriptManager(): ScriptManager = ScriptManagerImpl
    override fun skinManager(): SkinManager = SkinManagerImpl
    override fun profileManager(): ProfileManager = ProfileManagerImpl
    override fun config(): BetterModelConfig = props.config
    override fun version(): MinecraftVersion = props.version
    override fun semver(): Semver = props.semver
    override fun nms(): NMS = props.nms
    override fun isSnapshot(): Boolean = props.snapshot > 0
    override fun adapter() = adapter
    override fun jarType(): BetterModelPlatform.JarType = jarType
    override fun getResource(path: String): InputStream? = host.getResource(path)

    @Synchronized
    override fun addReloadStartHandler(consumer: Consumer<PackZipper>) {
        val previous = props.reloadStartTask
        props.reloadStartTask = {
            previous(it)
            consumer.accept(it)
        }
    }

    @Synchronized
    override fun addReloadEndHandler(consumer: Consumer<ReloadResult>) {
        val previous = props.reloadEndTask
        props.reloadEndTask = {
            previous(it)
            consumer.accept(it)
        }
    }
}
