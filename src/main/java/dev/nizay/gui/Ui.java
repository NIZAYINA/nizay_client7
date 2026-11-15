package dev.nizay.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Рисование без «пикселей». Всё считается в физических пикселях экрана (pose масштабируется на 1/guiScale),
 * углы скругляются с антиалиасингом (частичная прозрачность граничных пикселей), текст — TrueType (Inter).
 * Координаты в «дизайн-единицах»: 1 единица = K физических пикселей (K = guiScale * fit).
 */
public final class Ui {
	private Ui() {}

	static GuiGraphics g;
	static float S = 1, K = 1, ox, oy;
	static Font font;

	private static final Identifier F_REG = Identifier.fromNamespaceAndPath("nizay", "inter");
	private static final Identifier F_BOLD = Identifier.fromNamespaceAndPath("nizay", "inter_bold");
	private static final Style REG = Style.EMPTY.withFont(new FontDescription.Resource(F_REG));
	private static final Style BOLD = Style.EMPTY.withFont(new FontDescription.Resource(F_BOLD));

	/** originGui — левый верхний угол дизайн-области в GUI-единицах, fit — коэффициент вписывания (≤1). */
	public static void begin(GuiGraphics gg, float originGuiX, float originGuiY, float fit) {
		Minecraft mc = Minecraft.getInstance();
		g = gg; font = mc.font;
		S = (float) mc.getWindow().getGuiScale();
		K = S * fit; ox = originGuiX * S; oy = originGuiY * S;
		g.pose().pushMatrix();
		g.pose().scale(1f / S, 1f / S);
	}

	public static void end() { g.pose().popMatrix(); }

	static int X(float x) { return Math.round(ox + x * K); }
	static int Y(float y) { return Math.round(oy + y * K); }
	static int L(float v) { return Math.round(v * K); }
	/** Перевод GUI-координат мыши в дизайн-единицы. */
	static float mx(double guiX, float originGuiX, float fit) { return (float) ((guiX - originGuiX) / fit); }

	// ------------------------------------------------------------------ скруглённые прямоугольники (9-slice текстуры)
	private static final Identifier[] RR = {
		Identifier.fromNamespaceAndPath("nizay", "textures/gui/rr12.png"),
		Identifier.fromNamespaceAndPath("nizay", "textures/gui/rr32.png"),
		Identifier.fromNamespaceAndPath("nizay", "textures/gui/rr96.png")};
	private static final int[] RRC = {12, 32, 96};

	private static void b(Identifier t, int T, int dx, int dy, int dw, int dh, int u, int v, int rw, int rh, int color) {
		if (dw <= 0 || dh <= 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, t, dx, dy, (float) u, (float) v, dw, dh, rw, rh, T, T, color);
	}

	/** Прямоугольник в физических пикселях [x0,x1)×[y0,y1) со скруглением R. Один вызов = до 9 блитов вместо сотен fill. */
	static void rectPx(int x0, int y0, int x1, int y1, int R, int color) {
		if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) return;
		R = Math.min(R, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
		if (R <= 1) { g.fill(x0, y0, x1, y1, color); return; }
		int k = R <= 14 ? 0 : R <= 40 ? 1 : 2, c = RRC[k], T = 2 * c; Identifier t = RR[k];
		int xm0 = x0 + R, xm1 = x1 - R, ym0 = y0 + R, ym1 = y1 - R;
		b(t, T, x0, y0, R, R, 0, 0, c, c, color);
		b(t, T, xm0, y0, xm1 - xm0, R, c - 1, 0, 2, c, color);
		b(t, T, xm1, y0, R, R, c, 0, c, c, color);
		b(t, T, x0, ym0, R, ym1 - ym0, 0, c - 1, c, 2, color);
		b(t, T, xm0, ym0, xm1 - xm0, ym1 - ym0, c - 1, c - 1, 2, 2, color);
		b(t, T, xm1, ym0, R, ym1 - ym0, c, c - 1, c, 2, color);
		b(t, T, x0, ym1, R, R, 0, c, c, c, color);
		b(t, T, xm0, ym1, xm1 - xm0, R, c - 1, c, 2, c, color);
		b(t, T, xm1, ym1, R, R, c, c, c, c, color);
	}

	public static void rect(float x, float y, float w, float h, float r, int color) {
		rectPx(X(x), Y(y), X(x + w), Y(y + h), L(r), color);
	}

	/** Прямоугольник с тонкой обводкой (≈1 физ. пиксель). fill должен быть непрозрачным. */
	public static void rectB(float x, float y, float w, float h, float r, int fill, int border) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h), R = L(r), bw = Math.max(1, Math.round(K * .7f));
		rectPx(x0, y0, x1, y1, R, border);
		rectPx(x0 + bw, y0 + bw, x1 - bw, y1 - bw, Math.max(0, R - bw), fill);
	}

	/** Прямая тонкая рамка без скругления (для режима редактирования HUD). */
	public static void frame(float x, float y, float w, float h, int color) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h), t = Math.max(1, Math.round(K * .6f));
		g.fill(x0, y0, x1, y0 + t, color); g.fill(x0, y1 - t, x1, y1, color);
		g.fill(x0, y0 + t, x0 + t, y1 - t, color); g.fill(x1 - t, y0 + t, x1, y1 - t, color);
	}

	/** Мягкая тень: несколько расширяющихся слоёв с убывающей прозрачностью. */
	public static void shadow(float x, float y, float w, float h, float r, int rgb, int maxAlpha, float spread) {
		int n = 5;
		for (int i = n; i >= 1; i--) {
			float t = i / (float) n;
			int al = Math.round(maxAlpha * (1 - t) * (1 - t) * .5f);
			if (al <= 0) continue;
			int e = L(spread * t);
			rectPx(X(x) - e, Y(y) - e + L(spread * .25f), X(x + w) + e, Y(y + h) + e + L(spread * .25f), L(r) + e, (al << 24) | rgb);
		}
	}

	public static int lerp(int c0, int c1, float t) {
		int a0 = c0 >>> 24, r0 = c0 >> 16 & 255, g0 = c0 >> 8 & 255, b0 = c0 & 255;
		int a1 = c1 >>> 24, r1 = c1 >> 16 & 255, g1 = c1 >> 8 & 255, b1 = c1 & 255;
		return (Math.round(a0 + (a1 - a0) * t) << 24) | (Math.round(r0 + (r1 - r0) * t) << 16) | (Math.round(g0 + (g1 - g0) * t) << 8) | Math.round(b0 + (b1 - b0) * t);
	}

	/** Картинка (полная текстура texW×texH, углы скруглены в самом PNG). */
	public static void image(Identifier tex, int texW, int texH, float x, float y, float w, float h) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0, 0f, 0f, x1 - x0, y1 - y0, texW, texH, texW, texH);
	}

	/** Область текстуры (u,v,rw,rh) растянуть на прямоугольник. */
	public static void region(Identifier tex, int texW, int texH, int u, int v, int rw, int rh, float x, float y, float w, float h) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0, (float) u, (float) v, x1 - x0, y1 - y0, rw, rh, texW, texH);
	}

	/** Белая текстура с окраской и поворотом вокруг центра (стрелки). */
	public static void tinted(Identifier tex, int texSize, float cx, float cy, float size, float angleRad, int color) {
		g.pose().pushMatrix();
		g.pose().translate(ox + cx * K, oy + cy * K);
		g.pose().rotate(angleRad);
		int h = Math.round(size * K / 2f);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, -h, -h, 0f, 0f, h * 2, h * 2, texSize, texSize, texSize, texSize, color);
		g.pose().popMatrix();
	}

	/** Картинка, вписанная в квадрат. */
	public static void sprite(Identifier tex, int texSize, float x, float y, float size) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + size), y1 = Y(y + size);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0, 0f, 0f, x1 - x0, y1 - y0, texSize, texSize, texSize, texSize);
	}

	public static void circle(float cx, float cy, float r, int color) { rect(cx - r, cy - r, r * 2, r * 2, r, color); }

	/** Обрезка по прямоугольнику (дизайн-единицы). */
	public static void clipOn(float x, float y, float w, float h) { g.enableScissor(X(x), Y(y), X(x + w), Y(y + h)); }
	public static void clipOff() { g.disableScissor(); }

	// ------------------------------------------------------------------ текст
	private static Component comp(String s, boolean bold) { return Component.literal(s).withStyle(bold ? BOLD : REG); }

	public static float width(String s, float size, boolean bold) { return font.width(comp(s, bold)) * size / 9f; }

	public static void text(String s, float x, float y, float size, int color, boolean bold) {
		g.pose().pushMatrix();
		g.pose().translate(ox + x * K, oy + y * K);
		float sc = K * size / 9f;
		g.pose().scale(sc, sc);
		g.drawString(font, comp(s, bold), 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static void textR(String s, float xRight, float y, float size, int color, boolean bold) {
		text(s, xRight - width(s, size, bold), y, size, color, bold);
	}

	public static void textC(String s, float xCenter, float y, float size, int color, boolean bold) {
		text(s, xCenter - width(s, size, bold) / 2f, y, size, color, bold);
	}

	public static String fit(String s, float maxW, float size, boolean bold) {
		if (width(s, size, bold) <= maxW) return s;
		while (s.length() > 1 && width(s + "…", size, bold) > maxW) s = s.substring(0, s.length() - 1);
		return s + "…";
	}
}
