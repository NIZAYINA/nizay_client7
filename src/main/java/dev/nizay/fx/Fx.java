package dev.nizay.fx;

import dev.nizay.Config;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/** Реестр и логика 30 эффектов. 1–15 — по референсам, 16–30 — мои. */
public final class Fx {
	private Fx() {}

	public static final String[] IDS = {
		"stardust", "prism", "aether", "ember", "frost", "petal", "pulse", "void", "silk", "firefly",
		"crystal", "comet", "halo", "rune", "mist",
		"helix", "spark", "lava", "star", "wisp", "bubble", "gravity", "pixel", "heart", "smoke",
		"rainbow", "flare", "moon", "wind", "confetti"
	};
	public static final String[] NAMES = {
		"Stardust Trail", "Prism Ripple", "Aether Orbs", "Ember Drift", "Frost Bloom",
		"Petal Storm", "Pulse Rings", "Void Rift", "Silk Ribbons", "Firefly Cloud",
		"Crystal Shards", "Comet Burst", "Halo", "Rune Circle", "Mist Veil",
		"Neon Helix", "Thunder Sparks", "Lava Bloom", "Starfall", "Soul Wisps",
		"Bubble Pop", "Gravity Rings", "Pixel Rain", "Heartbeat", "Shadow Smoke",
		"Rainbow Trail", "Sun Flares", "Moon Dust", "Wind Swirl", "Confetti Pop"
	};
	public static final String[] DESCS = {
		"Sparkling gold dust follows your steps", "Iridescent rings spread across the ground", "Six glowing orbs circle your body",
		"Warm embers rise and fade into smoke", "Ice crystals blossom under your feet", "A swirl of petals spirals around you",
		"Soft shock rings expand from the chest", "A dark vortex opens at your feet", "Flowing ribbons trail from the shoulders",
		"Fireflies drift and flicker around you", "Faceted shards float in orbit", "Comets streak past with long tails",
		"A glowing ring hovers above the head", "A rotating magic circle on the ground", "Low drifting mist wraps the ground",
		"Twin neon strands spiral up your body", "Crackling bolts jump around you", "Molten orbs bubble up from your feet",
		"Stars rain down from above", "Pale wisps rise and sway", "Bubbles float up and pop",
		"Silver rings bob around your waist", "Green pixels fall like digital rain", "Hearts pulse upward",
		"Dark smoke puffs curl at your feet", "A hue-cycling trail follows you", "Warm flares burst from the chest",
		"Silver crescents drift in the air", "White streaks swirl around you", "Confetti bursts when you jump"
	};

	public static final String[] DESCS_RU = {
		"Золотая пыль тянется за шагами", "Радужные кольца расходятся по земле", "Шесть светящихся шаров кружат вокруг",
		"Тёплые угольки поднимаются вверх", "Ледяные кристаллы расцветают под ногами", "Вихрь лепестков вокруг персонажа",
		"Мягкие ударные кольца из груди", "Тёмная воронка у ног", "Ленты, развевающиеся от плеч",
		"Светлячки летают и мерцают", "Гранёные осколки на орбите", "Кометы с длинными хвостами",
		"Светящееся кольцо над головой", "Вращающийся магический круг", "Низкий дрейфующий туман",
		"Две неоновые спирали вокруг тела", "Электрические искры вокруг", "Лавовые пузыри из-под ног",
		"Звёзды падают сверху", "Бледные огоньки поднимаются вверх", "Пузыри всплывают и лопаются",
		"Серебряные кольца у пояса", "Зелёный цифровой дождь", "Сердечки поднимаются вверх",
		"Тёмный дым клубится у ног", "Радужный след при ходьбе", "Тёплые вспышки из груди",
		"Серебряные полумесяцы в воздухе", "Белые росчерки ветра", "Конфетти при прыжке"
	};

	/** Палитра тинта: 0 = родной цвет, дальше жёлтый, голубой, розовый, фиолетовый (как на референсе). */
	public static final float[][] PALETTE = {
		{1, 1, 1}, {1.0f, .85f, .55f}, {.55f, .8f, 1f}, {1f, .6f, .8f}, {.75f, .6f, 1f}
	};
	public static final int[] PALETTE_RGB = {0xFFFFFF, 0xFFD98C, 0x8CCCFF, 0xFF99CC, 0xBF99FF};

	private static final Map<String, SimpleParticleType> TYPES = new HashMap<>();

	public static void register() {
		for (String id : allSpriteIds()) {
			SimpleParticleType t = FabricParticleTypes.simple(true);
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("nizay", id), t);
			ParticleFactoryRegistry.getInstance().register(t, NizayParticle.Provider::new);
			TYPES.put(id, t);
		}
	}

	public static String[] allSpriteIds() {
		String[] a = new String[IDS.length + 1];
		System.arraycopy(IDS, 0, a, 0, IDS.length);
		a[IDS.length] = "feather";
		return a;
	}

	// ------------------------------------------------------------------ контекст
	public static final class Ctx {
		public Ctx() {}
		public ClientLevel lv; public double x, y, z, nx, ny, nz; public float yaw; public boolean moving, ground; public long t;
		public float s = 1, d = 1; public float[] tint = PALETTE[0]; public RandomSource r;
		public boolean useHue; public int cap = 700; // для радужных эффектов при "родном" цвете

		public double rd(double a) { return (r.nextDouble() * 2 - 1) * a; }
		public int n(double k) { double v = k * d; int i = (int) v; return i + (r.nextDouble() < v - i ? 1 : 0); }
		public boolean chance(double p) { return r.nextDouble() < p * d; }

		/** Спавн одной частицы. col — доп. множитель цвета (может быть null). */
		public void p(String sprite, double px, double py, double pz, double vx, double vy, double vz,
					  float life, float size, float alpha, float grav, float spin, float[] col) {
			SimpleParticleType type = TYPES.get(sprite);
			if (type == null || NizayParticle.alive > cap) return;
			alpha = Math.min(1f, alpha * SAT); size *= 1.1f;
			float cr = tint[0], cg = tint[1], cb = tint[2];
			if (col != null) { cr *= col[0]; cg *= col[1]; cb *= col[2]; }
			NizayParticle.pLife = life; NizayParticle.pSize = size; NizayParticle.pAlpha = alpha;
			NizayParticle.pGrav = grav; NizayParticle.pSpin = spin;
			NizayParticle.pR = cr; NizayParticle.pG = cg; NizayParticle.pB = cb;
			lv.addParticle(type, true, true, px, py, pz, vx, vy, vz);
		}

		/** «Непрерывная» частица: из (x0,y0,z0) в (x1,y1,z1) за один тик. */
		public void pc(String sprite, double x0, double y0, double z0, double x1, double y1, double z1,
					   float size, float alpha, float roll0, float roll1, float[] col) {
			SimpleParticleType type = TYPES.get(sprite);
			if (type == null || NizayParticle.alive > cap) return;
			float cr = tint[0], cg = tint[1], cb = tint[2];
			if (col != null) { cr *= col[0]; cg *= col[1]; cb *= col[2]; }
			NizayParticle.pSize = size; NizayParticle.pAlpha = Math.min(1f, alpha * SAT);
			NizayParticle.pGrav = 0; NizayParticle.pSpin = 0; NizayParticle.pLife = 0;
			NizayParticle.pR = cr; NizayParticle.pG = cg; NizayParticle.pB = cb;
			NizayParticle.pX0 = x0; NizayParticle.pY0 = y0; NizayParticle.pZ0 = z0;
			NizayParticle.pCont = true;
			NizayParticle.pRollPrev = roll0; NizayParticle.pRoll = roll1;
			lv.addParticle(type, true, true, x1, y1, z1, 0, 0, 0);
		}

		/** Орбита вокруг игрока: a0/a1 — угол в этом и следующем тике, h — высота над ногами. */
		void orb(String sprite, double a0, double a1, double rad, double h0, double h1, float size, float alpha, float spin, float[] col) {
			pc(sprite, x + Math.cos(a0) * rad, y + h0, z + Math.sin(a0) * rad,
				nx + Math.cos(a1) * rad, ny + h1, nz + Math.sin(a1) * rad, size, alpha, (float) (a0 * spin), (float) (a1 * spin), col);
		}

		public void ring(String sprite, double cy, double rad, int pts, double out, double up, float life, float size, float alpha, double rot, boolean hue) {
			for (int i = 0; i < pts; i++) {
				double a = rot + i * Math.PI * 2 / pts, c = Math.cos(a), sn = Math.sin(a);
				float[] col = hue ? hue((float) i / pts) : null;
				p(sprite, x + c * rad, cy, z + sn * rad, c * out, up, sn * out, life, size, alpha, 0, 0, col);
			}
		}
	}

	private static final float[][] HUES = new float[96][];
	static {
		for (int k = 0; k < HUES.length; k++) {
			float h = k / (float) HUES.length;
			int i = (int) (h * 6) % 6; float f = h * 6 - (int) (h * 6), q = 1 - f;
			HUES[k] = switch (i) {
				case 0 -> new float[]{1, f, 0}; case 1 -> new float[]{q, 1, 0}; case 2 -> new float[]{0, 1, f};
				case 3 -> new float[]{0, q, 1}; case 4 -> new float[]{f, 0, 1}; default -> new float[]{1, 0, q};
			};
		}
	}
	public static float[] hue(float h) { h = h - (float) Math.floor(h); return HUES[Math.min(HUES.length - 1, (int) (h * HUES.length))]; }
	/** Насыщенность/яркость эффектов (множитель прозрачности). */
	static final float SAT = 1.25f;
	/** Множитель плотности от лимитера (0.35..1). */
	public static float perf = 1f;
	static final float[] WARM = {1f, .8f, .5f}, VOID_COL = {.6f, .4f, 1f}, HELIX_A = {.4f, 1f, 1f}, HELIX_B = {1f, .4f, 1f};

	// ------------------------------------------------------------------ тик
	private static final Ctx C = new Ctx();
	private static double lx, ly, lz;
	private static boolean wasGround = true;
	private static long tick;

	public static void tick(Minecraft mc) {
		LocalPlayer pl = mc.player; ClientLevel lv = mc.level;
		if (pl == null || lv == null || mc.isPaused()) return;
		tick++;
		if (tick % 20 == 0) updatePerf(mc);
		boolean invis = pl.isInvisible();
		boolean moving = Math.abs(pl.getX() - lx) + Math.abs(pl.getZ() - lz) > 0.02;
		boolean jumped = wasGround && !pl.onGround() && pl.getY() - ly > 0.05;
		lx = pl.getX(); ly = pl.getY(); lz = pl.getZ(); wasGround = pl.onGround();
		if (invis) return; // для невидимых игроков ничего не рисуем

		Ctx c = C;
		c.lv = lv; c.x = pl.getX(); c.y = pl.getY(); c.z = pl.getZ(); c.yaw = pl.getYRot();
		boolean step = pl.onGround() && Math.abs(pl.getY() - pl.yo) > .05;   // подъём по ступеням: вертикаль не предсказываем
		c.nx = c.x + clampV(pl.getX() - pl.xo); c.nz = c.z + clampV(pl.getZ() - pl.zo);
		c.ny = c.y + (step || pl.onGround() ? 0 : clampV(pl.getY() - pl.yo) * .8);
		c.cap = Config.I.opt.on ? Config.I.opt.fxCap : 900;
		c.moving = moving; c.ground = pl.onGround(); c.t = tick; c.r = lv.getRandom();

		for (int i = 0; i < IDS.length; i++) {
			Config.Fx f = Config.I.fx(IDS[i]);
			if (!f.on) continue;
			c.s = .5f + f.size / 100f;
			c.d = (.2f + 1.4f * f.density / 100f) * perf;
			c.tint = PALETTE[Math.max(0, Math.min(4, f.color))];
			c.useHue = f.color == 0;
			run(i, c, jumped);
		}
		dev.nizay.wings.WingsFx.tick(c, pl, moving);
		HitFx.tick(mc);
	}

	private static double clampV(double v) { return Math.abs(v) > 2 ? 0 : v; }

	private static void updatePerf(Minecraft mc) {
		Config.Opt o = Config.I.opt;
		if (!o.on || !o.limiter) { perf = 1f; return; }
		float target = Math.max(.35f, Math.min(1f, mc.getFps() / 70f));
		perf += (target - perf) * .5f;
	}

	// ------------------------------------------------------------------ сами эффекты
	private static void run(int i, Ctx c, boolean jumped) {
		double t = c.t, y0 = c.y;
		float s = c.s;
		switch (i) {
			case 0 -> { // Stardust Trail
				if (c.moving) for (int k = 0, m = c.n(2); k < m; k++)
					c.p("stardust", c.x + c.rd(.2), y0 + .1, c.z + c.rd(.2), c.rd(.01), .012, c.rd(.01), 24, .14f * s, 1, 0, 0, null);
				if (c.chance(.25)) c.p("stardust", c.x + c.rd(.9), y0 + .3 + c.r.nextDouble() * 1.6, c.z + c.rd(.9), 0, .006, 0, 18, .22f * s, 1, 0, 0, null);
			}
			case 1 -> { // Prism Ripple
				if (t % Math.max(6, (int) (16 / c.d)) == 0) c.ring("prism", y0 + .05, .3, 28, .07, 0, 20, .16f * s, .95f, 0, c.useHue);
			}
			case 2 -> { // Aether Orbs
				for (int k = 0; k < 6; k++) {
					double a0 = t * .09 + k * Math.PI / 3, a1 = a0 + .09;
					c.orb("aether", a0, a1, .85, 1.0 + Math.sin(a0 * 1.3 + k) * .12, 1.0 + Math.sin(a1 * 1.3 + k) * .12, .26f * s, 1, 0, null);
				}
			}
			case 3 -> { // Ember Drift
				for (int k = 0, m = c.n(1.5); k < m; k++)
					c.p("ember", c.x + c.rd(.45), y0 + c.r.nextDouble() * .3, c.z + c.rd(.45), c.rd(.01), .035 + c.r.nextDouble() * .03, c.rd(.01), 32, .12f * s, .95f, 0, 0, null);
			}
			case 4 -> { // Frost Bloom
				if (c.chance(.5)) c.p("frost", c.x + c.rd(.8), y0 + .05, c.z + c.rd(.8), 0, .004, 0, 26, .3f * s, .9f, 0, (float) c.rd(.05), null);
				if (c.chance(.3)) c.p("star", c.x + c.rd(.9), y0 + .2 + c.r.nextDouble() * 1.2, c.z + c.rd(.9), 0, .004, 0, 18, .12f * s, 1, 0, 0, null);
			}
			case 5 -> { // Petal Storm
				for (int k = 0, m = c.n(3); k < m; k++) {
					double a0 = t * .22 + k * 2.1, a1 = a0 + .22, h0 = ((t * .03 + k * .3) % 1) * 2.0, h1 = h0 + .03 * 2.0;
					if (h1 > 2.0) continue;
					c.orb("petal", a0, a1, .7 + .1 * k, h0, h1, .24f * s, .95f, 1f, null);
				}
			}
			case 6 -> { // Pulse Rings
				if (t % Math.max(8, (int) (22 / c.d)) == 0) c.ring("pulse", y0 + 1.1, .25, 30, .1, 0, 14, .14f * s, .9f, 0, false);
			}
			case 7 -> { // Void Rift
				for (int k = 0; k < 12; k++) {
					double a0 = t * .15 + k * Math.PI * 2 / 12, rr = .9 * (.4 + .6 * ((k % 4) / 3.0));
					c.orb("void", a0, a0 + .15, rr, .04, .04, .3f * s, .95f, 1f, null);
				}
				if (c.chance(.4)) c.p("wisp", c.x + c.rd(.7), y0 + .1, c.z + c.rd(.7), 0, .04, 0, 18, .34f * s, .6f, 0, 0, VOID_COL);
			}
			case 8 -> { // Silk Ribbons
				double back = Math.toRadians(c.yaw);
				for (int side = -1; side <= 1; side += 2) for (int k = 0; k < c.n(1.5); k++) {
					double lat = side * .35, bx = Math.sin(back), bz = -Math.cos(back);
					double sx = c.x + Math.cos(back) * lat, sz = c.z + Math.sin(back) * lat;
					double w = Math.sin(t * .2 + side) * .02;
					c.p("silk", sx, y0 + 1.45, sz, bx * .02 * -1 + w, -.012, bz * .02 * -1 + w, 26, .35f * s, .85f, 0, 0, side < 0 ? new float[]{.7f, .85f, 1f} : new float[]{.85f, .75f, 1f});
				}
			}
			case 9 -> { // Firefly Cloud
				if (c.chance(.35)) c.p("firefly", c.x + c.rd(1.6), y0 + .3 + c.r.nextDouble() * 1.8, c.z + c.rd(1.6), c.rd(.01), c.rd(.008), c.rd(.01), 40, .13f * s, 1, 0, 0, null);
			}
			case 10 -> { // Crystal Shards
				for (int k = 0; k < 8; k++) {
					double a0 = -t * .06 + k * Math.PI / 4, a1 = a0 - .06;
					c.orb("crystal", a0, a1, 1.0, .5 + (k % 3) * .55 + Math.sin(t * .05 + k) * .1, .5 + (k % 3) * .55 + Math.sin((t + 1) * .05 + k) * .1, .26f * s, 1, 1f, null);
				}
			}
			case 11 -> { // Comet Burst
				if (c.chance(.18)) {
					double a = c.r.nextDouble() * Math.PI * 2;
					c.p("comet", c.x + Math.cos(a) * 2 + 1.2, y0 + 3 + c.r.nextDouble(), c.z + Math.sin(a) * 2, -.18, -.1, c.rd(.03), 12, .7f * s, .95f, 0, 0, null);
				}
			}
			case 12 -> { // Halo
				for (int k = 0; k < 9; k++) {
					double a0 = t * .08 + k * Math.PI * 2 / 9;
					c.orb("halo", a0, a0 + .08, .34, 2.2, 2.2, .2f * s, 1, 0, null);
				}
				c.pc("halo", c.x, y0 + 2.2, c.z, c.nx, c.ny + 2.2, c.nz, .9f * s, .6f, 0, 0, null);
				if (c.chance(.2)) c.p("stardust", c.x + c.rd(.4), y0 + 2.3, c.z + c.rd(.4), 0, .01, 0, 14, .14f * s, 1, 0, 0, null);
			}
			case 13 -> { // Rune Circle
				for (int k = 0; k < 16; k++) {
					double a0 = t * .04 + k * Math.PI * 2 / 16;
					c.orb("rune", a0, a0 + .04, 1.1, .05, .05, .15f * s, .95f, 0, null);
				}
				for (int k = 0; k < 6; k++) {
					double a0 = -t * .06 + k * Math.PI / 3 + (k % 2) * .5;
					c.orb("stardust", a0, a0 - .06, .55, .05, .05, .11f * s, .95f, 0, null);
				}
				if (c.chance(.4)) c.p("stardust", c.x + c.rd(.6), y0 + .2, c.z + c.rd(.6), 0, .03, 0, 20, .12f * s, .95f, 0, 0, null);
			}
			case 14 -> { // Mist Veil
				for (int k = 0, m = c.n(.6); k < m; k++)
					c.p("mist", c.x + c.rd(1.3), y0 + .1, c.z + c.rd(1.3), c.rd(.006), .001, c.rd(.006), 34, 1.0f * s, .34f, 0, 0, null);
			}
			// ------------------------------------------------ мои 15
			case 15 -> { // Neon Helix
				for (int k = 0; k < 2; k++) {
					double a0 = t * .2 + k * Math.PI, h0 = ((t * .035) % 1) * 2.1, h1 = h0 + .035 * 2.1;
					if (h1 > 2.1) continue;
					c.orb("helix", a0, a0 + .2, .6, h0, h1, .16f * s, 1, 0, k == 0 ? HELIX_A : HELIX_B);
				}
			}
			case 16 -> { // Thunder Sparks
				if (c.chance(.35)) c.p("spark", c.x + c.rd(.9), y0 + .2 + c.r.nextDouble() * 1.8, c.z + c.rd(.9), 0, 0, 0, 4, .5f * s, 1, 0, (float) c.rd(.6), null);
			}
			case 17 -> { // Lava Bloom
				for (int k = 0, m = c.n(1.2); k < m; k++)
					c.p("lava", c.x + c.rd(.5), y0 + .05, c.z + c.rd(.5), c.rd(.01), .04 + c.r.nextDouble() * .03, c.rd(.01), 26, .2f * s, .95f, 0, 0, null);
			}
			case 18 -> { // Starfall
				if (c.chance(.7)) c.p("star", c.x + c.rd(2.2), y0 + 4.5, c.z + c.rd(2.2), c.rd(.01), -.12, c.rd(.01), 40, .22f * s, 1, 0, .05f, null);
			}
			case 19 -> { // Soul Wisps
				if (c.chance(.35)) {
					double a = c.r.nextDouble() * Math.PI * 2;
					c.p("wisp", c.x + Math.cos(a) * .9, y0 + .1, c.z + Math.sin(a) * .9, 0, .03, 0, 40, .34f * s, .8f, 0, 0, null);
				}
			}
			case 20 -> { // Bubble Pop
				if (c.chance(.5)) c.p("bubble", c.x + c.rd(.5), y0 + .05, c.z + c.rd(.5), c.rd(.01), .03 + c.r.nextDouble() * .02, c.rd(.01), 30, .18f * s, .85f, 0, 0, null);
			}
			case 21 -> { // Gravity Rings
				for (int ring = 0; ring < 2; ring++) {
					double h0 = 1.0 + Math.sin(t * .08 + ring * Math.PI) * .6, h1 = 1.0 + Math.sin((t + 1) * .08 + ring * Math.PI) * .6;
					for (int k = 0; k < 12; k++) {
						double a0 = t * (ring == 0 ? .05 : -.05) + k * Math.PI / 6;
						c.orb("gravity", a0, a0 + (ring == 0 ? .05 : -.05), .75, h0, h1, .12f * s, .9f, 0, null);
					}
				}
			}
			case 22 -> { // Pixel Rain
				for (int k = 0, m = c.n(1.6); k < m; k++)
					c.p("pixel", c.x + c.rd(1.3), y0 + 3, c.z + c.rd(1.3), 0, -.12, 0, 28, .1f * s, .95f, 0, 0, null);
			}
			case 23 -> { // Heartbeat
				if (c.chance(.14)) c.p("heart", c.x + c.rd(.4), y0 + 1.2, c.z + c.rd(.4), c.rd(.01), .035, c.rd(.01), 28, .22f * s, .95f, 0, 0, null);
			}
			case 24 -> { // Shadow Smoke
				for (int k = 0, m = c.n(.9); k < m; k++)
					c.p("smoke", c.x + c.rd(.6), y0 + .1, c.z + c.rd(.6), c.rd(.008), .018, c.rd(.008), 36, .55f * s, .55f, 0, 0, null);
			}
			case 25 -> { // Rainbow Trail
				if (c.moving) for (int k = 0, m = c.n(2); k < m; k++) {
					float h = (float) ((t * .02 + k * .1) % 1);
					c.p("rainbow", c.x + c.rd(.15), y0 + .15 + c.r.nextDouble() * .2, c.z + c.rd(.15), 0, .006, 0, 26, .22f * s, .95f, 0, 0, c.useHue ? hue(h) : null);
				}
			}
			case 26 -> { // Sun Flares
				if (t % Math.max(10, (int) (24 / c.d)) == 0) for (int k = 0; k < 6; k++) {
					double a = c.r.nextDouble() * Math.PI * 2;
					c.p("flare", c.x, y0 + 1.1, c.z, Math.cos(a) * .12, c.rd(.03), Math.sin(a) * .12, 14, .45f * s, .95f, 0, 0, null);
				}
			}
			case 27 -> { // Moon Dust
				if (c.chance(.3)) c.p("moon", c.x + c.rd(1.1), y0 + .3 + c.r.nextDouble() * 1.8, c.z + c.rd(1.1), c.rd(.01), -.004, c.rd(.01), 36, .2f * s, .9f, 0, (float) c.rd(.1), null);
			}
			case 28 -> { // Wind Swirl
				for (int k = 0; k < 4; k++) {
					double a0 = t * .25 + k * Math.PI / 2, h0 = .3 + ((t * .02 + k * .25) % 1) * 1.6, h1 = h0 + .02 * 1.6;
					if (h1 > 1.9) continue;
					c.orb("wind", a0, a0 + .25, .9, h0, h1, .55f * s, .85f, 0, null);
				}
			}
			case 29 -> { // Confetti Pop (при прыжке)
				if (jumped) for (int k = 0, m = c.n(26); k < m; k++)
					c.p("confetti", c.x, y0 + .2, c.z, c.rd(.18), .1 + c.r.nextDouble() * .15, c.rd(.18), 34, .1f * s, 1, .6f, (float) c.rd(.4), c.useHue ? hue(c.r.nextFloat()) : null);
			}
			default -> {}
		}
	}
}
