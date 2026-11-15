package dev.nizay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Весь конфиг клиента. Лежит в .minecraft/config/nizay.json */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nizay.json");
	public static Config I = new Config();

	/** Настройки одного эффекта. size/density 0..100, color: 0 = родной цвет, 1..4 = палитра. */
	public static class Fx { public boolean on = false; public int size = 55, density = 70, color = 0; }

	public static class Wings {
		public boolean on = false;
		public int color = 0, size = 70, glow = 40;
		public float flap = 1.1f;
		public boolean foldIdle = true, firstPerson = false;
	}

	/** Элемент HUD: позиция левого верхнего угла в долях экрана (0..1) и масштаб. Двигается мышью в чате (T). */
	public static class HudEl {
		public boolean on; public float x, y, scale = 1f;
		public HudEl() {}
		public HudEl(boolean on, float x, float y) { this.on = on; this.x = x; this.y = y; }
	}

	public static class Opt {
		public boolean on = false;
		public boolean limiter = true;
		public int fxCap = 700;
		public boolean minParticles = true, noClouds = true, noShadows = true, noAo = false, fastGfx = true, uncap = true;
		public int entityDist = 70, renderCap = 0, simCap = 0, biomeBlend = 1;
		public boolean saved;
		public String origParticles, origClouds, origGfx;
		public boolean origShadows, origAo, origVsync;
		public double origEntDist = 1.0;
		public int origRender, origSim, origBiome, origFps = 120;
	}

	/** Зум: key — код клавиши GLFW (по умолчанию C). */
	public static class Zoom { public boolean on = false; public int key = 67; public int factor = 4; public boolean hold = true, smooth = true, slowMouse = true; }

	/** Fullbright: гамма level% (1500 = 15.0). origGamma хранится на случай аварийного выхода. */
	public static class Bright { public boolean on = false; public int level = 1500; public double origGamma = -1; }

	/** Эффекты удара. style 0..4, пять разных видов. */
	public static class Hit { public boolean on = false; public int style = 0, size = 60, density = 70, color = 0; }

	public static class Friends {
		public boolean arrows = true, logo = true;
		public String server = "";   // адрес relay-сервера (https://...), обычно приходит из лаунчера
	}

	public Map<String, Fx> fx = new LinkedHashMap<>();
	public boolean skyOn = true;
	public String sky = "deep_space";
	public float animSpeed = 1.2f, brightness = 0.8f;
	public Wings wings = new Wings();
	public HudEl hudMark = new HudEl(true, 0.012f, 0.018f);
	public HudEl hudCoords = new HudEl(true, 0.012f, 0.085f);
	public HudEl hudCd = new HudEl(true, 0.012f, 0.2f);
	public Opt opt = new Opt();
	public Zoom zoom = new Zoom();
	public Bright bright = new Bright();
	public Hit hit = new Hit();
	public Friends friends = new Friends();

	/** Главный выключатель. masterSaved помнит, что было включено. */
	public boolean master = true;
	public Map<String, Boolean> masterSaved = new HashMap<>();

	/** Состояние меню: последняя вкладка и прокрутка каждой. */
	public int uiTab = 0, uiFxPage = 0, uiFxSel = 0;
	public float[] uiScroll = new float[16];

	public Fx fx(String id) { return fx.computeIfAbsent(id, k -> new Fx()); }

	// ------------------------------------------------------------------ главный выключатель
	public void setMaster(boolean on) {
		if (on == master) return;
		master = on;
		if (!on) {
			masterSaved.clear();
			save1("sky", skyOn); skyOn = false;
			save1("wings", wings.on); wings.on = false;
			save1("hudMark", hudMark.on); hudMark.on = false;
			save1("hudCoords", hudCoords.on); hudCoords.on = false;
			save1("hudCd", hudCd.on); hudCd.on = false;
			save1("opt", opt.on); opt.on = false;
			save1("zoom", zoom.on); zoom.on = false;
			save1("bright", bright.on); bright.on = false;
			save1("hit", hit.on); hit.on = false;
			save1("arrows", friends.arrows); friends.arrows = false;
			save1("flogo", friends.logo); friends.logo = false;
			for (Map.Entry<String, Fx> e : fx.entrySet()) { save1("fx:" + e.getKey(), e.getValue().on); e.getValue().on = false; }
		} else {
			skyOn = g("sky", skyOn); wings.on = g("wings", wings.on);
			hudMark.on = g("hudMark", hudMark.on); hudCoords.on = g("hudCoords", hudCoords.on); hudCd.on = g("hudCd", hudCd.on);
			opt.on = g("opt", opt.on); zoom.on = g("zoom", zoom.on); bright.on = g("bright", bright.on); hit.on = g("hit", hit.on);
			friends.arrows = g("arrows", friends.arrows); friends.logo = g("flogo", friends.logo);
			for (Map.Entry<String, Fx> e : fx.entrySet()) e.getValue().on = g("fx:" + e.getKey(), e.getValue().on);
			masterSaved.clear();
		}
	}
	private void save1(String k, boolean v) { masterSaved.put(k, v); }
	private boolean g(String k, boolean def) { Boolean b = masterSaved.get(k); return b != null ? b : def; }

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE)) {
					Config c = GSON.fromJson(r, Config.class);
					if (c != null) { I = c; I.fix(); }
				}
			} else {
				I.fx("stardust").on = true;
				I.fx("aether").on = true;
				I.fx("halo").on = true;
				save();
			}
		} catch (Exception e) {
			System.err.println("[Nizay] config load failed: " + e);
		}
	}

	/** Достраивает поля, которых не было в старых/чужих конфигах. */
	public void fix() {
		if (fx == null) fx = new LinkedHashMap<>();
		if (wings == null) wings = new Wings();
		if (hudMark == null) hudMark = new HudEl(true, 0.012f, 0.018f);
		if (hudCoords == null) hudCoords = new HudEl(true, 0.012f, 0.085f);
		if (hudCd == null) hudCd = new HudEl(true, 0.012f, 0.2f);
		if (opt == null) opt = new Opt();
		if (zoom == null) zoom = new Zoom();
		if (bright == null) bright = new Bright();
		if (hit == null) hit = new Hit();
		if (friends == null) friends = new Friends();
		if (masterSaved == null) masterSaved = new HashMap<>();
		if (uiScroll == null || uiScroll.length < 16) uiScroll = new float[16];
		if (sky == null) sky = "deep_space";
	}

	public static void save() {
		try (Writer w = Files.newBufferedWriter(FILE)) {
			GSON.toJson(I, w);
		} catch (Exception e) {
			System.err.println("[Nizay] config save failed: " + e);
		}
	}

	public static Gson gson() { return GSON; }
}
