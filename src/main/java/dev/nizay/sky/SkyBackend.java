package dev.nizay.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Низкоуровневая отрисовка сетки. Основано на NuitRenderBackend (MIT, FlashyReese / AMereBagatelle). */
final class SkyBackend {
	private SkyBackend() {}

	static GpuBufferSlice transforms(Matrix4f mv, Vector4f color) {
		return RenderSystem.getDynamicUniforms().writeTransform(mv, color, new Vector3f(), new Matrix4f());
	}

	static void draw(RenderPipeline pipeline, MeshData mesh, GpuBufferSlice dyn, Identifier texture) {
		try (mesh) {
			GpuBuffer vb = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
			GpuBuffer ib;
			VertexFormat.IndexType it;
			if (mesh.indexBuffer() == null) {
				RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
				ib = seq.getBuffer(mesh.drawState().indexCount());
				it = seq.type();
			} else {
				ib = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
				it = mesh.drawState().indexType();
			}
			RenderTarget rt = Minecraft.getInstance().getMainRenderTarget();
			GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : rt.getColorTextureView();
			GpuTextureView depth = rt.useDepth ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : rt.getDepthTextureView()) : null;
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nizay sky", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				pass.setVertexBuffer(0, vb);
				pass.setIndexBuffer(ib, it);
				ScissorState sc = RenderSystem.getScissorStateForRenderTypeDraws();
				if (sc.enabled()) pass.enableScissor(sc.x(), sc.y(), sc.width(), sc.height());
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", dyn);
				if (texture != null) {
					AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
					pass.bindTexture("Sampler0", tex.getTextureView(), tex.getSampler());
				}
				pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
			}
		}
	}
}
