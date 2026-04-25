/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.api.asset;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Represents a serialized compiled-model asset that can be loaded by BetterModel.
 * <p>
 * Assets are expected to contain precomputed model payloads produced externally
 * (for example by a pack/build pipeline) rather than raw {@code .bbmodel} files.
 * </p>
 *
 * @param name the logical asset name
 * @param supplier the input stream supplier
 * @since 2.2.0
 */
public record CompiledModelAsset(
    @NotNull String name,
    @NotNull StreamSupplier supplier
) {

    public CompiledModelAsset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(supplier, "supplier");
    }

    /**
     * Opens the asset stream.
     *
     * @return the input stream
     * @throws IOException if the stream cannot be opened
     * @since 2.2.0
     */
    public @NotNull InputStream open() throws IOException {
        return supplier.get();
    }

    /**
     * Supplies an input stream for the asset.
     *
     * @since 2.2.0
     */
    @FunctionalInterface
    public interface StreamSupplier {
        /**
         * Opens the input stream.
         *
         * @return the input stream
         * @throws IOException if the stream cannot be opened
         * @since 2.2.0
         */
        @NotNull InputStream get() throws IOException;
    }
}
