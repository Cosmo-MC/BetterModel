/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.api.asset;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Supplies precompiled BetterModel payload assets.
 * <p>
 * Implementations typically fetch serialized model data from an external asset
 * store such as S3, a CDN, or a local cache populated by a pack system.
 * </p>
 *
 * @since 2.2.0
 */
@FunctionalInterface
public interface CompiledModelProvider {

    /**
     * Loads all compiled model assets currently available to BetterModel.
     *
     * @return the compiled model assets
     * @since 2.2.0
     */
    @NotNull Collection<CompiledModelAsset> load();
}
