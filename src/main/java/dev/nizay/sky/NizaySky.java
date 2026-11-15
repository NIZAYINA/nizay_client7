package dev.nizay.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import dev.nizay.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Собственное небо: куб с текстурами 3x2 + два слоя «мерцания» с плавным перекрёстным затуханием. */
public final class NizaySky {
	private NizaySky() {}

	record Def(String id, int face, float[] horizon, double rot, boolean fx) {}

	private static final Map<String, Def> DEFS = new HashMap<>();
	private static void d(String id, int face, float r, float g, float b, double rot, boolean fx) {
		DEFS.put(id, new Def(id, face, new float[]{r, g, b}, rot, fx));
	}
	static {
		d("deep_space", 768, .03f, .025f, .06f, .004, true);
		d("nebula", 768, .03f, .015f, .06f, .003, true);
		d("aurora", 768, .01f, .045f, .06f, 0, true);
		d("eclipse", 640, .05f, .04f, .085f, 0, true);
		d("crystal_dusk", 768, .72f, .45f, .55f, 0, true);
		d("sunset", 512, 1f, .62f, .3f, 0, false);
		d("dawn_haze", 256, 1f, .88f, .84f, 0, false);
		d("midnight", 128, .03f, .07f, .2f, 0, false);
		d("mono", 512, .15f, .15f, .17f, 0, false);
	}

	private static final Set<Identifier> LOADED = new HashSet<>();
	private static boolean failed;
	private static long last = System.nanoTime();
	private static double phase;

	/** Включено ли наше небо прямо сейчас (в мире без потолка, т.е. не в Незере). */
	public static boolean active() {
		Config c = Config.I;
		if (!c.skyOn || failed || !DEFS.containsKey(c.sky)) return false;
		ClientLevel lv = Minecraft.getInstance().level;
		return lv != null && !lv.dimensionType().hasCeiling();
	}

	public static Vector4f fogColor(Vector4f orig) {
		if (!active()) return orig;
		float[] h = DEFS.get(Config.I.sky).horizon();
		float m = mod();
		return new Vector4f(Mth.clamp(h[0] * m, 0, 1), Mth.clamp(h[1] * m, 0, 1), Mth.clamp(h[2] * m, 0, 1), orig.w());
	}

	private static float mod() { return Mth.clamp(Config.I.brightness / 0.8f, 0.15f, 1.3f); }

	public static boolean render(GpuBufferSlice fog, float partial) {
		if (!active()) return false;
		try {
			Def d = DEFS.get(Config.I.sky);
			long now = System.nanoTime();
			phase += Math.min(0.25, (now - last) / 1e9) * (Config.I.animSpeed);
			last = now;
			Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix());
			mv.setTranslation(0, 0, 0);
			if (d.rot() > 0) mv.rotate((float) (phase * d.rot()), 0.28f, 0.93f, 0.2f);
			float m = mod();
			layer(d, "", false, mv, m, 1f);
			if (d.fx()) {
				float a1 = (float) (0.5 + 0.5 * Math.sin(phase * 0.9));
				layer(d, "_fx1", true, mv, m, a1);
				layer(d, "_fx2", true, mv, m, 1f - a1);
			}
			return true;
		} catch (Throwable t) {
			failed = true;
			System.err.println("[Nizay] sky render failed, falling back to vanilla: " + t);
			t.printStackTrace();
			return false;
		}
	}

	// Матрицы граней и UV-раскладка 3x2 (как у Nuit): 0 bottom | 1 north | 2 south | 3 top | 4 east | 5 west
	private static final Matrix4f[] FACE = {
		new Matrix4f(),
		new Matrix4f().rotateX(90f * Mth.DEG_TO_RAD),
		new Matrix4f().rotateX(-90f * Mth.DEG_TO_RAD).rotateY(180f * Mth.DEG_TO_RAD),
		new Matrix4f().rotateX(180f * Mth.DEG_TO_RAD),
		new Matrix4f().rotateZ(90f * Mth.DEG_TO_RAD).rotateY(-90f * Mth.DEG_TO_RAD),
		new Matrix4f().rotateZ(-90f * Mth.DEG_TO_RAD).rotateY(90f * Mth.DEG_TO_RAD)
	};
	private static final int[][] TILE = {{0, 0}, {1, 1}, {2, 0}, {1, 0}, {2, 1}, {0, 1}};

	private static void layer(Def d, String suffix, boolean add, Matrix4f mv, float m, float alpha) {
		if (alpha <= 0.003f) return;
		Identifier id = Identifier.fromNamespaceAndPath("nizay", "textures/sky/" + d.id() + suffix + ".png");
		if (LOADED.add(id)) Minecraft.getInstance().getTextureManager().registerAndLoad(id, new SimpleTexture(id));
		RenderPipeline p = add ? SkyPipelines.additive() : SkyPipelines.opaque();
		Vector4f col = add ? new Vector4f(m, m, m, alpha) : new Vector4f(m, m, m, 1f);
		float F = d.face();
		float eu = 0.75f / (3f * F), ev = 0.75f / (2f * F);
		try (ByteBufferBuilder bb = new ByteBufferBuilder(p.getVertexFormat().getVertexSize() * 24)) {
			BufferBuilder b = new BufferBuilder(bb, p.getVertexFormatMode(), p.getVertexFormat());
			for (int f = 0; f < 6; f++) {
				float u0 = TILE[f][0] / 3f + eu, u1 = (TILE[f][0] + 1) / 3f - eu;
				float v0 = TILE[f][1] / 2f + ev, v1 = (TILE[f][1] + 1) / 2f - ev;
				Matrix4f fm = FACE[f];
				b.addVertex(fm, -100f, -100f, -100f).setUv(u0, v0);
				b.addVertex(fm, -100f, -100f, 100f).setUv(u0, v1);
				b.addVertex(fm, 100f, -100f, 100f).setUv(u1, v1);
				b.addVertex(fm, 100f, -100f, -100f).setUv(u1, v0);
			}
			SkyBackend.draw(p, b.buildOrThrow(), SkyBackend.transforms(new Matrix4f(mv), col), id);
		}
	}
}
