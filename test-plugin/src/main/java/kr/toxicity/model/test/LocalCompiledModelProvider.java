/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.test;

import kr.toxicity.model.api.asset.CompiledModelAsset;
import kr.toxicity.model.api.asset.CompiledModelProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public record LocalCompiledModelProvider(@NotNull Path root) implements CompiledModelProvider {

    @Override
    public @NotNull Collection<CompiledModelAsset> load() {
        if (!Files.isDirectory(root)) {
            return java.util.List.of();
        }
        try (var stream = Files.list(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                .map(path -> new CompiledModelAsset(
                    path.getFileName().toString(),
                    () -> Files.newInputStream(path)
                ))
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read compiled model directory: " + root, e);
        }
    }
}
