/*
 * This source file is part of BetterModel.
 * Copyright (c) 2025 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.manager

import kr.toxicity.library.armormodel.ArmorImage
import kr.toxicity.library.armormodel.ArmorModel
import kr.toxicity.library.armormodel.ArmorNameMapper
import kr.toxicity.library.armormodel.ArmorPaletteImage
import kr.toxicity.model.api.pack.PackObfuscator
import kr.toxicity.model.api.pack.PackZipper
import kr.toxicity.model.util.*
import net.kyori.adventure.text.format.NamedTextColor
import java.io.File

object ArmorManager : GlobalManager {

    var armor: ArmorModel = ArmorModel.EMPTY
        private set

    override fun reload(
        pipeline: ReloadPipeline,
        zipper: PackZipper
    ) {
        if (!CONFIG.module().playerAnimation) {
            armor = ArmorModel.EMPTY
            return
        }
        val folder = DATA_FOLDER.getOrCreateDirectory("armors")
        val armorsFolder = File(folder, "armors")
        val trimsFolder = File(folder, "armor_trims")
        val palettesFolder = File(folder, "palettes")
        if (!armorsFolder.isDirectory || !trimsFolder.isDirectory || !palettesFolder.isDirectory) {
            warn(
                "Player animation armor assets are missing. ".toComponent(NamedTextColor.YELLOW),
                "Expected pre-bundled assets in ${folder.path}; runtime downloads are disabled.".toComponent(NamedTextColor.YELLOW)
            )
            armor = ArmorModel.EMPTY
            return
        }
        val textures = PackObfuscator.order()
        val models = PackObfuscator.order()
        armor = ArmorModel.builder()
            .namespace(CONFIG.namespace())
            .streamLoader { path -> PLATFORM.getResource(path)!! }
            .armors(pipeline
                .mapParallel(armorsFolder.subFiles(), File::length) { it.toArmorImage() }
                .sortedBy { it.name }
            )
            .armorTrims(pipeline
                .mapParallel(trimsFolder.subFiles(), File::length) { it.toArmorImage() }
                .sortedBy { it.name }
            )
            .palettes(pipeline
                .mapParallel(palettesFolder.subFiles(), File::length) { it.toPaletteImage() }
                .sortedBy { it.name }
            )
            .nameMapper(ArmorNameMapper(
                { textures.obfuscate(it) },
                { models.obfuscate(it) }
            ))
            .flush(false)
            .build()
        armor.builders().forEach {
            zipper.modern().add(
                it.path(),
                256
            ) {
                it.get()
            }
        }
    }

    private fun File.toArmorImage() = runCatching {
        ArmorImage(
            nameWithoutExtension,
            File(this, "armor.png").toImage(),
            File(this, "leggings.png").toImage()
        )
    }.handleFailure {
        "Unable to load this armor image: $path"
    }.getOrNull()

    private fun File.toPaletteImage() = runCatching {
        ArmorPaletteImage(nameWithoutExtension, toImage())
    }.handleFailure {
        "Unable to load this palette image: $path"
    }.getOrNull()
}
