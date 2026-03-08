/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.manager

import com.cosmomc.packsystem.model.Model
import com.cosmomc.packsystem.model.animation.ModelAnimation
import com.cosmomc.packsystem.model.animation.ModelAnimationChannel
import com.cosmomc.packsystem.model.animation.ModelAnimationDatapoint
import com.cosmomc.packsystem.model.animation.ModelAnimationFormatVersion
import com.cosmomc.packsystem.model.animation.ModelAnimationInterpolator
import com.cosmomc.packsystem.model.animation.ModelAnimationKeyframe
import com.cosmomc.packsystem.model.animation.ModelAnimationScript
import com.cosmomc.packsystem.model.bone.ModelBone
import com.cosmomc.packsystem.model.bone.ModelBoneAnimation
import com.cosmomc.packsystem.model.bone.ModelBoneType
import com.cosmomc.packsystem.model.ModelType
import com.cosmomc.packsystem.model.geometry.ModelHitBox
import com.cosmomc.packsystem.model.geometry.ModelVector3
import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.animation.AnimationIterator
import kr.toxicity.model.api.animation.AnimationProgress
import kr.toxicity.model.api.animation.VectorPoint
import kr.toxicity.model.api.bone.BoneItemMapper
import kr.toxicity.model.api.bone.BoneName
import kr.toxicity.model.api.bone.BoneTags
import kr.toxicity.model.api.bone.BoneRenderContext
import kr.toxicity.model.api.data.Float3
import kr.toxicity.model.api.data.blueprint.AnimationGenerator
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation
import kr.toxicity.model.api.data.blueprint.BlueprintAnimator
import kr.toxicity.model.api.data.blueprint.BlueprintElement
import kr.toxicity.model.api.data.blueprint.ModelBoundingBox
import kr.toxicity.model.api.data.renderer.ModelRenderer
import kr.toxicity.model.api.data.renderer.RendererGroup
import kr.toxicity.model.api.manager.ModelManager
import kr.toxicity.model.api.pack.PackZipper
import kr.toxicity.model.api.platform.PlatformNamespace
import kr.toxicity.model.api.script.AnimationScript
import kr.toxicity.model.api.script.BlueprintScript
import kr.toxicity.model.api.script.TimeScript
import kr.toxicity.model.api.util.InterpolationUtil
import kr.toxicity.model.api.util.function.Float2FloatFunction
import kr.toxicity.model.api.util.function.FloatFunction
import kr.toxicity.model.api.util.interpolator.VectorInterpolator
import kr.toxicity.model.util.CONFIG
import kr.toxicity.model.util.DATA_FOLDER
import kr.toxicity.model.util.PLATFORM
import kr.toxicity.model.util.getOrCreateDirectory
import kr.toxicity.model.util.info
import kr.toxicity.model.util.toComponent
import kr.toxicity.model.util.toImmutableView
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.SequencedMap
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import org.joml.Vector3f

object ModelManagerImpl : ModelManager, GlobalManager {

    private val compiledModelsDirectory = "compiled-models"
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val generalModelMap = linkedMapOf<String, ModelRenderer>()
    private val generalModelView = generalModelMap.toImmutableView()
    private val playerModelMap = linkedMapOf<String, ModelRenderer>()
    private val playerModelView = playerModelMap.toImmutableView()

    @OptIn(ExperimentalSerializationApi::class)
    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
        generalModelMap.clear()
        playerModelMap.clear()

        val compiledAssets = compiledAssets()
        val assets = compiledAssets.assets
        pipeline.status = "Loading compiled models..."
        pipeline.goal = assets.size

        info(
            "Compiled model source: ".toComponent(),
            compiledAssets.description.toComponent()
        )
        info(
            "Compiled model assets found: ".toComponent(),
            assets.size.toString().toComponent(),
            " [".toComponent(),
            assets.joinToString(", ") { it.name() }.ifBlank { "<none>" }.toComponent(),
            "]".toComponent()
        )

        assets.forEach { asset ->
            pipeline.progress()
            runCatching {
                asset.open().use { stream ->
                    json.decodeFromStream<Model>(stream)
                }
            }.onFailure {
                throw IllegalStateException("Unable to load compiled model asset: ${asset.name()}", it)
            }.onSuccess { model ->
                when (model.type) {
                    ModelType.GENERAL -> generalModelMap[model.id] = model.toRenderer(ModelRenderer.Type.GENERAL)
                    ModelType.PLAYER -> playerModelMap[model.id] = model.toRenderer(ModelRenderer.Type.PLAYER)
                }
            }
        }

        info(
            "Compiled model reload complete: ".toComponent(),
            "${generalModelMap.size} model(s), ${playerModelMap.size} limb model(s)".toComponent()
        )
    }

    private fun compiledAssets(): CompiledAssets {
        val provider = BetterModel.compiledModelProvider()
        return if (provider != null) {
            CompiledAssets(
                assets = provider.load().toList(),
                description = "provider:${provider.javaClass.name}",
            )
        } else {
            localCompiledAssets()
        }
    }

    private fun localCompiledAssets(): CompiledAssets {
        val folder = DATA_FOLDER.getOrCreateDirectory(compiledModelsDirectory)
        val assets = Files.walk(folder.toPath()).use { stream ->
            stream.filter { path ->
                path.isRegularFile() && path.extension.equals("json", ignoreCase = true)
            }.map { path ->
                kr.toxicity.model.api.asset.CompiledModelAsset(path.fileName.toString()) {
                    Files.newInputStream(path)
                }
            }.toList()
        }
        return CompiledAssets(
            assets = assets,
            description = "directory:${folder.toPath().toAbsolutePath().normalize()}",
        )
    }

    private fun Model.toRenderer(type: ModelRenderer.Type): ModelRenderer {
        val rootElements = bones.mapNotNull { bone -> bone.toBlueprintElement() }
        val groups = LinkedHashMap<BoneName, RendererGroup>()
        bones.mapNotNull { bone -> bone.toRendererGroup() }.forEach { group ->
            groups[group.name()] = group
        }
        return ModelRenderer(
            id,
            type,
            groups as SequencedMap<BoneName, RendererGroup>,
            animations.associateBy { it.name }.mapValues { (_, animation) ->
                animation.toBlueprintAnimation(rootElements, animationFormatVersion)
            }
        )
    }

    private fun ModelBone.toRendererGroup(): RendererGroup? {
        val parent = toBlueprintBone() ?: return null
        val childGroups = LinkedHashMap<BoneName, RendererGroup>()
        children.mapNotNull { child -> child.toRendererGroup() }.forEach { child ->
            childGroups[child.name()] = child
        }
        val itemModelKey = itemModel
        val defaultMapper = parent.name().toItemMapper()
        val namespace = itemModelKey?.toPlatformNamespace()
        val stack = when {
            namespace == null -> null
            defaultMapper !== BoneItemMapper.EMPTY && parent.name().usesPlayerSkinMapper() -> null
            else -> CONFIG.item().get().modelData(0, namespace)
        }
        val itemMapper = when {
            namespace == null -> defaultMapper
            defaultMapper !== BoneItemMapper.EMPTY -> namespaceMapper(defaultMapper, namespace)
            else -> defaultMapper
        }
        return RendererGroup(
            scale,
            stack,
            parent,
            childGroups as SequencedMap<BoneName, RendererGroup>,
            hitBox?.toBoundingBox(),
            itemMapper
        )
    }

    private fun ModelBone.toBlueprintBone(): BlueprintElement.Bone? = when (type) {
        ModelBoneType.GROUP -> BlueprintElement.Group(
            UUID.fromString(uuid),
            BoneName.of(rawName),
            origin.toFloat3(),
            rotation.toFloat3(),
            children.mapNotNull { child -> child.toBlueprintElement() },
            true
        )

        ModelBoneType.LOCATOR -> BlueprintElement.Locator(
            UUID.fromString(uuid),
            BoneName.of(rawName),
            origin.toFloat3()
        )

        ModelBoneType.NULL_OBJECT -> BlueprintElement.NullObject(
            UUID.fromString(uuid),
            BoneName.of(rawName),
            null,
            null,
            origin.toFloat3()
        )
    }

    private fun ModelBone.toBlueprintElement(): BlueprintElement? = when (type) {
        ModelBoneType.GROUP -> toBlueprintBone() as BlueprintElement.Group
        ModelBoneType.LOCATOR -> toBlueprintBone() as BlueprintElement.Locator
        ModelBoneType.NULL_OBJECT -> toBlueprintBone() as BlueprintElement.NullObject
    }

    private fun ModelAnimation.toBlueprintAnimation(
        children: List<BlueprintElement>,
        formatVersion: ModelAnimationFormatVersion,
    ): BlueprintAnimation {
        val loopType = runCatching {
            AnimationIterator.Type.valueOf(loop)
        }.getOrDefault(AnimationIterator.Type.PLAY_ONCE)
        val animatorData = bones.entries.mapNotNull { (rawName, animation) ->
            animation.toAnimatorData(BoneName.of(rawName), formatVersion, length)
        }.associateBy(BlueprintAnimator.AnimatorData::name)
        val animators = AnimationGenerator.createMovements(length, children, animatorData)
        return BlueprintAnimation(
            name,
            loopType,
            length,
            override,
            animators,
            scripts.toBlueprintScript(name, loopType, length),
            animators.values.firstOrNull()?.keyframe()?.toEmpty() ?: AnimationProgress.emptyStorage(length)
        )
    }

    private fun ModelBoneAnimation.toAnimatorData(
        name: BoneName,
        formatVersion: ModelAnimationFormatVersion,
        length: Float,
    ): BlueprintAnimator.AnimatorData? {
        val filtered = keyframes.filter { it.time <= length }
        val position = filtered.filter { it.channel == ModelAnimationChannel.POSITION }.map { it.toVectorPoint(formatVersion, ModelAnimationChannel.POSITION) }
        val rotation = filtered.filter { it.channel == ModelAnimationChannel.ROTATION }.map { it.toVectorPoint(formatVersion, ModelAnimationChannel.ROTATION) }
        val scale = filtered.filter { it.channel == ModelAnimationChannel.SCALE }.map { it.toVectorPoint(formatVersion, ModelAnimationChannel.SCALE) }
        if (position.isEmpty() && rotation.isEmpty() && scale.isEmpty()) {
            return null
        }
        return BlueprintAnimator.AnimatorData(name, position, scale, rotation, rotationGlobal)
    }

    private fun ModelAnimationKeyframe.toVectorPoint(
        formatVersion: ModelAnimationFormatVersion,
        channel: ModelAnimationChannel,
    ): VectorPoint {
        val point = dataPoints.firstOrNull() ?: ModelAnimationDatapoint()
        val mapper: (Vector3f) -> Vector3f = when (channel) {
            ModelAnimationChannel.POSITION -> { value -> formatVersion.convertPosition(value) }
            ModelAnimationChannel.ROTATION -> { value -> formatVersion.convertRotation(value) }
            ModelAnimationChannel.SCALE -> { value -> formatVersion.convertScale(value) }
            else -> { value -> value }
        }
        return VectorPoint(
            point.toFunction().map(mapper).memoize(),
            time,
            VectorPoint.BezierConfig(
                bezierLeftTime?.toVector(),
                bezierLeftValue?.toVector()?.let(mapper),
                bezierRightTime?.toVector(),
                bezierRightValue?.toVector()?.let(mapper),
            ),
            interpolation.toVectorInterpolator(),
        )
    }

    private fun ModelAnimationDatapoint.toFunction(): FloatFunction<Vector3f> {
        val xFunction = x.toFloatFunction()
        val yFunction = y.toFloatFunction()
        val zFunction = z.toFloatFunction()
        return if (
            xFunction is kr.toxicity.model.api.util.function.Float2FloatConstantFunction &&
            yFunction is kr.toxicity.model.api.util.function.Float2FloatConstantFunction &&
            zFunction is kr.toxicity.model.api.util.function.Float2FloatConstantFunction
        ) {
            FloatFunction.of(Vector3f(xFunction.value(), yFunction.value(), zFunction.value()))
        } else {
            FloatFunction { time ->
                Vector3f(
                    xFunction.applyAsFloat(time),
                    yFunction.applyAsFloat(time),
                    zFunction.applyAsFloat(time),
                )
            }
        }
    }

    private fun String?.toFloatFunction(): Float2FloatFunction {
        val text = this?.trim().orEmpty()
        if (text.isEmpty()) {
            return Float2FloatFunction.ZERO
        }
        return text.toFloatOrNull()?.let(Float2FloatFunction::of)
            ?: BetterModel.platform().evaluator().compile(text)
    }

    private fun List<ModelAnimationScript>.toBlueprintScript(
        name: String,
        loopType: AnimationIterator.Type,
        length: Float
    ): BlueprintScript? {
        if (isEmpty()) {
            return null
        }
        val list = ArrayList<TimeScript>(size + 2)
        val sorted = sortedBy(ModelAnimationScript::time)
        if (sorted.first().time > 0F) {
            list += TimeScript.EMPTY
        }
        var before = 0F
        sorted.forEach { frame ->
            val built = frame.scripts.mapNotNull(PLATFORM.scriptManager()::build)
            val script = AnimationScript.of(built).time(InterpolationUtil.roundTime(frame.time - before))
            list += script
            before = frame.time
        }
        val remain = InterpolationUtil.roundTime(length - before)
        if (remain > 0F) {
            list += AnimationScript.EMPTY.time(remain)
        }
        return BlueprintScript(name, loopType, length, list)
    }

    private fun String.toPlatformNamespace(): PlatformNamespace {
        val split = split(':', limit = 2)
        require(split.size == 2) {
            "Invalid item model namespace: $this"
        }
        return PlatformNamespace(split[0], split[1])
    }

    private fun ModelHitBox.toBoundingBox(): ModelBoundingBox = ModelBoundingBox.of(
        min.x.toDouble(),
        min.y.toDouble(),
        min.z.toDouble(),
        max.x.toDouble(),
        max.y.toDouble(),
        max.z.toDouble()
    )

    private fun ModelVector3.toFloat3(): Float3 = Float3(x, y, z)
    private fun ModelVector3.toVector() = Vector3f(x, y, z)

    private fun ModelAnimationInterpolator.toVectorInterpolator(): VectorInterpolator = when (this) {
        ModelAnimationInterpolator.LINEAR -> VectorInterpolator.LINEAR
        ModelAnimationInterpolator.CATMULLROM -> VectorInterpolator.CATMULLROM
        ModelAnimationInterpolator.BEZIER -> VectorInterpolator.BEZIER
        ModelAnimationInterpolator.STEP -> VectorInterpolator.STEP
    }

    private fun ModelAnimationFormatVersion.convertRotation(vector: Vector3f): Vector3f = when (this) {
        ModelAnimationFormatVersion.BLOCKBENCH_5 -> Vector3f(-vector.x, vector.y, -vector.z)
        ModelAnimationFormatVersion.BLOCKBENCH_LEGACY -> Vector3f(vector.x, -vector.y, -vector.z)
    }

    private fun ModelAnimationFormatVersion.convertPosition(vector: Vector3f): Vector3f = when (this) {
        ModelAnimationFormatVersion.BLOCKBENCH_5 -> Vector3f(-vector.x, vector.y, -vector.z).div(16F)
        ModelAnimationFormatVersion.BLOCKBENCH_LEGACY -> Vector3f(vector.x, vector.y, -vector.z).div(16F)
    }

    private fun ModelAnimationFormatVersion.convertScale(vector: Vector3f): Vector3f =
        Vector3f(vector.x - 1F, vector.y - 1F, vector.z - 1F)

    private fun BoneName.usesPlayerSkinMapper(): Boolean = tagged(
        BoneTags.PLAYER_HEAD,
        BoneTags.PLAYER_RIGHT_ARM,
        BoneTags.PLAYER_RIGHT_FOREARM,
        BoneTags.PLAYER_LEFT_ARM,
        BoneTags.PLAYER_LEFT_FOREARM,
        BoneTags.PLAYER_HIP,
        BoneTags.PLAYER_WAIST,
        BoneTags.PLAYER_CHEST,
        BoneTags.PLAYER_RIGHT_LEG,
        BoneTags.PLAYER_RIGHT_FORELEG,
        BoneTags.PLAYER_LEFT_LEG,
        BoneTags.PLAYER_LEFT_FORELEG,
        BoneTags.CAPE
    )

    private fun namespaceMapper(delegate: BoneItemMapper, namespace: PlatformNamespace): BoneItemMapper = object : BoneItemMapper {
        override fun transform() = delegate.transform()

        override fun apply(context: BoneRenderContext, transformedItemStack: kr.toxicity.model.api.util.TransformedItemStack): kr.toxicity.model.api.util.TransformedItemStack {
            return delegate.apply(context, transformedItemStack).modify { item ->
                item.modelData(0, namespace)
            }
        }
    }

    override fun model(name: String): ModelRenderer? = generalModelView[name]
    override fun models(): Collection<ModelRenderer> = generalModelView.values
    override fun modelKeys(): Set<String> = generalModelView.keys
    override fun limb(name: String): ModelRenderer? = playerModelView[name]
    override fun limbs(): Collection<ModelRenderer> = playerModelView.values
    override fun limbKeys(): Set<String> = playerModelView.keys

    private data class CompiledAssets(
        val assets: List<kr.toxicity.model.api.asset.CompiledModelAsset>,
        val description: String,
    )
}
