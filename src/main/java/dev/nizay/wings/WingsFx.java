package dev.nizay.wings;

import dev.nizay.Config;
import dev.nizay.fx.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Aether Wings v2. Перья — «непрерывные» частицы на один тик: каждое перо задаётся двумя позициями —
 * для текущего тика и для предсказанного следующего (по скорости и повороту тела игрока).
 * Клиент рисует частицы с той же интерполяцией, что и самого игрока, поэтому крылья приклеены к телу
 * и не отстают при ходьбе, беге, прыжках и поворотах.
 */
public final class WingsFx {
	private WingsFx() {}

	public static final float[][] COLORS = {
		null, {.55f, .75f, 1f}, {1f, .6f, .8f}, {1f, .8f, .4f}, {.7f, .55f, 1f}, {.55f, .55f, .6f}
	};
	public static final int[] COLORS_RGB = {0xD8D8F0, 0x8CC0FF, 0xFF99CC, 0xFFCC66, 0xB38CFF, 0x8C8C99};

	private static double phase;
	private static final int PER_SIDE = 18;
	private static final double[] A = new double[PER_SIDE * 2 * 4 + 8], B = new double[PER_SIDE * 2 * 4 + 8];

	public static void tick(Fx.Ctx c, LocalPlayer pl, boolean moving) {
		Config.Wings w = Config.I.wings;
		if (!w.on || pl.isInvisible()) return;
		Minecraft mc = Minecraft.getInstance();
		if (!w.firstPerson && mc.options.getCameraType().isFirstPerson()) return;

		boolean air = false;   // крылья не зависят от прыжков и ходьбы: всегда лёгкое покачивание вверх-вниз
		moving = false;
		double p0 = phase; phase += .11 * w.flap; double p1 = phase;

		double yawDiff = Mth.wrapDegrees(pl.yBodyRot - pl.yBodyRotO);
		if (Math.abs(yawDiff) > 60) yawDiff = 0;
		double yaw0 = pl.yBodyRot, yaw1 = pl.yBodyRot + yawDiff;

		double scale = .4 + w.size / 100.0 * .9;
		float[] col = COLORS[Math.max(0, Math.min(COLORS.length - 1, w.color))];
		boolean sneak = pl.isShiftKeyDown();

		build(A, c.x, c.y, c.z, yaw0, p0, air, moving, sneak, w, scale);
		build(B, c.nx, c.ny, c.nz, yaw1, p1, air, moving, sneak, w, scale);

		int n = 0;
		float size = (float) (.46 * scale);
		for (int side = 0; side < 2; side++) {
			for (int k = 0; k < PER_SIDE; k++, n += 4) {
				c.pc("feather", A[n], A[n + 1], A[n + 2], B[n], B[n + 1], B[n + 2], size, .97f, (float) A[n + 3], (float) B[n + 3], col);
			}
		}
		if (w.glow > 0) {
			for (int side = 0; side < 2; side++) {
				int g = PER_SIDE * 8 + side * 4;
				c.pc("rainbow", A[g], A[g + 1], A[g + 2], B[g], B[g + 1], B[g + 2], (float) (1.7 * scale), w.glow / 100f * .38f, 0, 0, col);
			}
		}
	}

	/** Заполняет позиции и крен каждого пера: [x,y,z,roll] * 18 * 2 стороны, затем 2 точки свечения. */
	private static void build(double[] out, double cx, double cy, double cz, double yawDeg, double ph,
							  boolean air, boolean moving, boolean sneak, Config.Wings w, double scale) {
		double deg = 10 + 11 * Math.sin(ph);   // небольшой мах: чуть вверх, чуть вниз
		if (sneak) deg -= 8;

		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double bx = -fx, bz = -fz;
		double baseY = cy + (sneak ? 1.15 : 1.4);
		double offBack = sneak ? .32 : .2;

		int n = 0;
		for (int si = 0; si < 2; si++) {
			int side = si == 0 ? -1 : 1;
			double px = cx + bx * offBack + rx * side * .12;
			double pz = cz + bz * offBack + rz * side * .12;
			for (int row = 0; row < 3; row++) {
				int feathers = 5 + row;
				double len = (.55 + .42 * row) * scale, rowDeg = deg - row * 9;
				for (int k = 0; k < feathers; k++, n += 4) {
					double fan = (k - (feathers - 1) / 2.0) * (row == 0 ? 11 : 9);
					double ang = Math.toRadians(rowDeg + fan * .6 + 8);
					double dist = len * (.55 + .45 * (k + 1.0) / feathers);
					double lat = Math.cos(ang) * dist * side, up = Math.sin(ang) * dist;
					out[n] = px + rx * lat + bx * .02 * row;
					out[n + 1] = baseY + up - row * .06;
					out[n + 2] = pz + rz * lat + bz * .02 * row;
					out[n + 3] = Math.atan2(Math.sin(ang), Math.cos(ang) * side) + Math.PI / 2;
				}
			}
		}
		for (int si = 0; si < 2; si++) {
			int side = si == 0 ? -1 : 1, g = PER_SIDE * 8 + si * 4;
			out[g] = cx + bx * offBack + rx * side * (.12 + .35 * scale);
			out[g + 1] = baseY + .05;
			out[g + 2] = cz + bz * offBack + rz * side * (.12 + .35 * scale);
		}
	}
}
