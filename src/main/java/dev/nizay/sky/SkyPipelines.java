package dev.nizay.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

/** Пайплайны для неба. Шейдеры лежат в assets/nizay/shaders/core. Основано на NuitRenderPipelines (MIT). */
final class SkyPipelines {
	private SkyPipelines() {}

	private static final Identifier SHADER = Identifier.fromNamespaceAndPath("nizay", "core/textured_skybox");
	private static final RenderPipeline.Snippet MATRICES = RenderPipeline.builder()
		.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
		.withUniform("Projection", UniformType.UNIFORM_BUFFER)
		.buildSnippet();

	private static RenderPipeline opaque, additive;

	static RenderPipeline opaque() {
		if (opaque == null) opaque = build("opaque", null);
		return opaque;
	}

	static RenderPipeline additive() {
		if (additive == null) additive = build("additive", new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE));
		return additive;
	}

	private static RenderPipeline build(String name, BlendFunction blend) {
		RenderPipeline.Builder b = RenderPipeline.builder(MATRICES);
		b.withLocation(Identifier.fromNamespaceAndPath("nizay", "pipeline/sky/" + name));
		b.withVertexShader(SHADER);
		b.withFragmentShader(SHADER);
		b.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS);
		b.withDepthWrite(false);
		b.withCull(false);
		if (blend != null) b.withBlend(blend); else b.withoutBlend();
		b.withSampler("Sampler0");
		return b.build();
	}
}
