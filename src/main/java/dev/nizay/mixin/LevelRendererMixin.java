package dev.nizay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import dev.nizay.sky.NizaySky;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подмена неба. Схема хуков и имена целей для 1.21.11 взяты из открытого мода Nuit (MIT, FlashyReese/AMereBagatelle).
 * Три шага: (1) принудительно включаем sky-pass даже если сервер/измерение его отключили,
 * (2) подменяем тип skybox NONE -> OVERWORLD, чтобы проход вообще выполнился, (3) вместо ванильного неба рисуем своё.
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererMixin {
	@Unique private static float nizay$tickDelta;

	@Inject(
		method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
		at = @At("HEAD"))
	private void nizay$captureTickDelta(FrameGraphBuilder fg, Camera camera, GpuBufferSlice fog, CallbackInfo ci) {
		nizay$tickDelta = camera.getPartialTickTime();
	}

	@ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private boolean nizay$forceSkyPass(boolean renderSky) {
		return renderSky || NizaySky.active();
	}

	@ModifyExpressionValue(
		method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/SkyRenderState;skybox:Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"))
	private DimensionType.Skybox nizay$skyboxType(DimensionType.Skybox original) {
		return (original == DimensionType.Skybox.NONE && NizaySky.active()) ? DimensionType.Skybox.OVERWORLD : original;
	}

	@Inject(
		method = "method_62215(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
		at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFog(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
			shift = At.Shift.AFTER),
		cancellable = true)
	private static void nizay$drawSky(GpuBufferSlice fog, SkyRenderState state, SkyRenderer renderer, CallbackInfo ci) {
		if (NizaySky.render(fog, nizay$tickDelta)) ci.cancel();
	}
}
