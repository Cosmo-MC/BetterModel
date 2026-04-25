/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.bukkit.util

import kr.toxicity.model.bukkit.BetterModelBukkitContext
import org.bukkit.command.CommandSender

val ADVENTURE_PLATFORM get() = BetterModelBukkitContext.audiencePlatform()

fun CommandSender.audience() = ADVENTURE_PLATFORM?.sender(this) ?: this
