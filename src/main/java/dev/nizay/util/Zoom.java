package dev.nizay.util;

import dev.nizay.Config;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Зум на любую клавишу. Работает через FOV и чувствительность мыши (FOV в игре ограничен 30–110). Вызывается каждый кадр. */
public final class Zoom {
	private Zoom() {}

	private static boolean active, wasDown, toggled;
	private static float cur = 1f;
	private static int baseFov; private static double baseSens;
	private static long last = System.nanoTime();

	public static void frame(Minecraft mc) {
		long now = System.nanoTime(); float dt = Math.min(.1f, (now - last) / 1e9f); last = now;
		Config.Zoom z = Config.I.zoom;
		boolean want = false;
		if (z.on && mc.level != null && mc.screen == null) {
			long win = GLFW.glfwGetCurrentContext();
			boolean down = win != 0 && z.key > 0 && GLFW.glfwGetKey(win, z.key) == GLFW.GLFW_PRESS;
			if (z.hold) want = down; else { if (down && !wasDown) toggled = !toggled; want = toggled; }
			wasDown = down;
		} else { toggled = false; wasDown = false; }

		float target = want ? Math.max(1.2f, z.factor) : 1f;
		cur = z.smooth ? cur + (target - cur) * Math.min(1f, dt * 12f) : target;
		if (Math.abs(cur - target) < .01f) cur = target;

		if (cur > 1.02f) {
			if (!active) { baseFov = mc.options.fov().get(); baseSens = mc.options.sensitivity().get(); active = true; }
			int fov = Math.max(30, Math.min(110, Math.round(baseFov / cur)));
			if (mc.options.fov().get() != fov) mc.options.fov().set(fov);
			if (z.slowMouse) mc.options.sensitivity().set(Math.max(0.01, Math.min(1.0, baseSens / (1 + (cur - 1) * .6))));
		} else if (active) restore(mc);
	}

	public static void restore(Minecraft mc) {
		if (!active) return;
		mc.options.fov().set(baseFov); mc.options.sensitivity().set(baseSens);
		active = false; cur = 1f;
	}
}
