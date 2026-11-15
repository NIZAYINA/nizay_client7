package dev.nizay.util;

import dev.nizay.Config;
import dev.nizay.opt.Optimizer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;

/** Конфиг <-> текстовый код «NZY1:...» (gzip + XOR + base64). Код содержит всё: эффекты, крылья, небо, HUD, функции. */
public final class ConfigCodec {
	private ConfigCodec() {}
	private static final String PREFIX = "NZY1:";
	private static final byte[] KEY = "nizay-client-config".getBytes(StandardCharsets.UTF_8);

	public static String encode(Config c) {
		try {
			Config copy = Config.gson().fromJson(Config.gson().toJson(c), Config.class);
			copy.opt.saved = false; copy.bright.origGamma = -1; copy.uiScroll = new float[16];
			byte[] json = Config.gson().toJson(copy).getBytes(StandardCharsets.UTF_8);
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(json); }
			byte[] z = bo.toByteArray();
			for (int i = 0; i < z.length; i++) z[i] ^= KEY[i % KEY.length];
			return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(z);
		} catch (Exception e) { return ""; }
	}

	/** Применяет код. Возвращает null при успехе или текст ошибки. */
	public static String apply(String code) {
		try {
			code = code.trim().replaceAll("\\s+", "");
			if (!code.startsWith(PREFIX)) return "Not a Nizay config code";
			byte[] z = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
			for (int i = 0; i < z.length; i++) z[i] ^= KEY[i % KEY.length];
			String json = new String(new GZIPInputStream(new ByteArrayInputStream(z)).readAllBytes(), StandardCharsets.UTF_8);
			Config n = Config.gson().fromJson(json, Config.class);
			if (n == null) return "Empty config";
			n.fix();
			Config old = Config.I;
			// свои прежние значения настроек игры не должны потеряться
			boolean wasOpt = old.opt.on; old.opt.on = false; Optimizer.apply();
			n.opt.saved = false; n.bright.origGamma = old.bright.origGamma;
			n.uiTab = old.uiTab; n.uiFxPage = old.uiFxPage; n.uiFxSel = old.uiFxSel; n.uiScroll = old.uiScroll;
			n.friends.server = old.friends.server == null || old.friends.server.isEmpty() ? n.friends.server : old.friends.server;
			Config.I = n; Config.save(); Optimizer.apply();
			return null;
		} catch (Exception e) { return "Invalid code"; }
	}

	// ----- локальные сохранённые конфиги: .minecraft/config/nizay-configs/*.nzy
	private static Path dir() {
		Path d = FabricLoader.getInstance().getConfigDir().resolve("nizay-configs");
		try { Files.createDirectories(d); } catch (Exception ignored) {}
		return d;
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try (var s = Files.list(dir())) { s.filter(p -> p.toString().endsWith(".nzy")).sorted().forEach(p -> out.add(p.getFileName().toString().replace(".nzy", ""))); } catch (Exception ignored) {}
		return out;
	}

	public static String save(String name) {
		try { Files.writeString(dir().resolve(name + ".nzy"), encode(Config.I)); return name; } catch (Exception e) { return null; }
	}
	public static String read(String name) { try { return Files.readString(dir().resolve(name + ".nzy")); } catch (Exception e) { return ""; } }
	public static void delete(String name) { try { Files.deleteIfExists(dir().resolve(name + ".nzy")); } catch (Exception ignored) {} }
}
