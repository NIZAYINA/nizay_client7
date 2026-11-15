package dev.nizay.fx;

import dev.nizay.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** 5 эффектов удара по игроку/мобу. Срабатывают, когда цель под прицелом получила урон сразу после вашего взмаха. */
public final class HitFx {
	private HitFx() {}

	public static final String[] NAMES = {"Spark Burst", "Shock Ring", "Ember Splash", "Frost Shatter", "Void Slash"};
	public static final String[] DESCS = {"Взрыв золотых искр", "Расходящееся ударное кольцо", "Огненные брызги вверх", "Ледяные осколки в стороны", "Тёмный росчерк с кометами"};

	private static final Map<Integer, Long> LAST = new HashMap<>();
	private static long tick;
	private static final Fx.Ctx C = new Fx.Ctx();

	public static void tick(Minecraft mc) {
		tick++;
		Config.Hit h = Config.I.hit;
		LocalPlayer pl = mc.player; ClientLevel lv = mc.level;
		if (!h.on || pl == null || lv == null || mc.isPaused()) return;
		Entity t = mc.crosshairPickEntity;
		if (!(t instanceof LivingEntity le) || !pl.swinging) return;
		if (le.hurtTime <= 0 || le.hurtTime < le.hurtDuration - 2) return;
		Long last = LAST.get(le.getId());
		if (last != null && tick - last < 8) return;
		LAST.put(le.getId(), tick);
		if (LAST.size() > 64) LAST.clear();
		C.lv = lv; C.r = lv.getRandom(); C.s = .5f + h.size / 100f; C.d = (.3f + 1.4f * h.density / 100f) * Fx.perf;
		C.tint = Fx.PALETTE[Math.max(0, Math.min(4, h.color))]; C.cap = 900;
		spawn(h.style, le.getX(), le.getY() + le.getBbHeight() * .55, le.getZ());
	}

	private static void spawn(int style, double x, double y, double z) {
		Fx.Ctx c = C; float s = c.s;
		switch (style) {
			case 0 -> {
				for (int i = 0, n = c.n(16); i < n; i++) {
					double a = c.r.nextDouble() * Math.PI * 2, e = (c.r.nextDouble() - .3) * 1.2, v = .08 + c.r.nextDouble() * .1;
					c.p("star", x, y, z, Math.cos(a) * v, e * .08, Math.sin(a) * v, 14, .22f * s, 1, .05f, 0, null);
				}
				c.p("flare", x, y, z, 0, 0, 0, 6, .9f * s, 1, 0, 0, null);
			}
			case 1 -> {
				for (int k = 0; k < 2; k++) for (int i = 0; i < 22; i++) {
					double a = i * Math.PI * 2 / 22, v = .12 + k * .06;
					c.p("pulse", x + Math.cos(a) * .2, y, z + Math.sin(a) * .2, Math.cos(a) * v, 0, Math.sin(a) * v, 12, .16f * s, 1, 0, 0, null);
				}
				c.p("flare", x, y, z, 0, 0, 0, 5, .8f * s, .9f, 0, 0, null);
			}
			case 2 -> {
				for (int i = 0, n = c.n(18); i < n; i++)
					c.p(i % 3 == 0 ? "lava" : "ember", x, y, z, c.rd(.08), .06 + c.r.nextDouble() * .1, c.rd(.08), 22, .18f * s, 1, .5f, 0, null);
			}
			case 3 -> {
				for (int i = 0, n = c.n(14); i < n; i++) {
					double a = c.r.nextDouble() * Math.PI * 2, v = .06 + c.r.nextDouble() * .08;
					c.p("crystal", x, y, z, Math.cos(a) * v, .03 + c.r.nextDouble() * .06, Math.sin(a) * v, 20, .2f * s, 1, .6f, (float) c.rd(.3), null);
				}
				c.p("frost", x, y - .3, z, 0, 0, 0, 14, .9f * s, .8f, 0, 0, null);
			}
			default -> {
				for (int i = 0, n = c.n(10); i < n; i++)
					c.p("comet", x + c.rd(.5), y + .6 + c.r.nextDouble() * .4, z + c.rd(.5), .18, -.16, c.rd(.05), 7, .6f * s, 1, 0, 0, null);
				for (int i = 0, n = c.n(10); i < n; i++) c.p("void", x, y, z, c.rd(.1), c.rd(.08), c.rd(.1), 14, .26f * s, .95f, 0, .2f, null);
			}
		}
	}
}
