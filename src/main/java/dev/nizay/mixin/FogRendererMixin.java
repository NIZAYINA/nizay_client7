package dev.nizay.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.nizay.sky.NizaySky;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Цвет тумана у горизонта подстраиваем под выбранное небо, чтобы дальние чанки не «светились» ванильным цветом. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
	private Vector4f nizay$fogColor(Vector4f original) {
		return NizaySky.fogColor(original);
	}
}
