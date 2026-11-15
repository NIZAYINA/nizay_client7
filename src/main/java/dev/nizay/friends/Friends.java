package dev.nizay.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nizay.Config;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Друзья и группы. Данные хранит relay-сервер (папка server/), доступ выдаёт лаунчер через <gameDir>/nizay-friends.json
 * ({"server": "...", "token": "...", "nick": "..."}). Мод раз в ~1.5 с отправляет свою позицию и получает позиции группы.
 */
public final class Friends {
	private Friends() {}

	public record Member(String nick, String id, boolean online, double x, double y, double z, String dim, String srv) {}
	public record Friend(String nick, String id, boolean online) {}
	public static final class Snap {
		public String status = "Launch the game from the Nizay launcher", myId = "", myNick = "";
		public List<Friend> friends = new ArrayList<>();
		public List<String> invites = new ArrayList<>();
		public List<Member> group = new ArrayList<>();
		public Set<String> online = new HashSet<>();
		public boolean ok;
	}

	public static volatile Snap snap = new Snap();
	private static volatile double px, py, pz; private static volatile String dim = "", srv = "", nick = "";
	private static String server = "", token = "";
	private static long cfgRead;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
	private static final Style ICON = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("nizay", "icons")));
	private static boolean started;

	public static void start() {
		if (started) return; started = true;
		Thread t = new Thread(() -> {
			while (true) {
				try { poll(); } catch (Throwable e) { Snap s = new Snap(); s.status = "Server unreachable"; snap = s; }
				try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
			}
		}, "nizay-friends");
		t.setDaemon(true); t.start();
	}

	/** Вызывается из клиентского тика: запоминает позицию для отправки. */
	public static void updatePos(Minecraft mc) {
		nick = mc.getUser().getName();
		if (mc.player == null || mc.level == null) { dim = ""; srv = ""; return; }
		px = mc.player.getX(); py = mc.player.getY(); pz = mc.player.getZ();
		dim = mc.level.dimension().toString();
		srv = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
	}

	private static void readCfg() {
		if (System.currentTimeMillis() - cfgRead < 8000) return;
		cfgRead = System.currentTimeMillis();
		try {
			Path f = FabricLoader.getInstance().getGameDir().resolve("nizay-friends.json");
			if (!Files.exists(f)) { server = ""; token = ""; return; }
			JsonObject j = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			server = j.has("server") ? j.get("server").getAsString() : "";
			token = j.has("token") ? j.get("token").getAsString() : "";
		} catch (Exception e) { server = ""; token = ""; }
	}

	private static void poll() throws Exception {
		readCfg();
		if (server.isEmpty() || token.isEmpty()) { Snap s = new Snap(); s.status = "Open Friends in the Nizay launcher"; snap = s; return; }
		JsonObject body = new JsonObject();
		body.addProperty("token", token);
		body.addProperty("x", px); body.addProperty("y", py); body.addProperty("z", pz);
		body.addProperty("dim", dim); body.addProperty("srv", srv);
		JsonObject r = send("/sync", body);
		if (r == null) { Snap s = new Snap(); s.status = "Server unreachable"; snap = s; return; }
		Snap s = new Snap(); s.ok = true; s.status = "Connected";
		if (r.has("me")) { s.myId = r.getAsJsonObject("me").get("id").getAsString(); s.myNick = r.getAsJsonObject("me").get("nick").getAsString(); }
		if (r.has("friends")) for (JsonElement e : r.getAsJsonArray("friends")) { JsonObject o = e.getAsJsonObject(); s.friends.add(new Friend(o.get("nick").getAsString(), o.get("id").getAsString(), o.get("online").getAsBoolean())); }
		if (r.has("invites")) for (JsonElement e : r.getAsJsonArray("invites")) s.invites.add(e.getAsJsonObject().get("from").getAsString());
		if (r.has("online")) for (JsonElement e : r.getAsJsonArray("online")) s.online.add(e.getAsString().toLowerCase(Locale.ROOT));
		if (r.has("group") && r.get("group").isJsonObject()) {
			JsonArray m = r.getAsJsonObject("group").getAsJsonArray("members");
			for (JsonElement e : m) { JsonObject o = e.getAsJsonObject();
				s.group.add(new Member(o.get("nick").getAsString(), o.get("id").getAsString(), o.get("online").getAsBoolean(),
					d(o, "x"), d(o, "y"), d(o, "z"), o.has("dim") ? o.get("dim").getAsString() : "", o.has("srv") ? o.get("srv").getAsString() : "")); }
		}
		snap = s;
	}
	private static double d(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0; }

	private static JsonObject send(String path, JsonObject body) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(server.replaceAll("/+$", "") + path)).timeout(Duration.ofSeconds(5))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) return null;
			return JsonParser.parseString(res.body()).getAsJsonObject();
		} catch (Exception e) { return null; }
	}

	/** Действие (invite / accept / leave) — асинхронно. */
	public static void action(String path, String key, String value) {
		new Thread(() -> {
			JsonObject b = new JsonObject(); b.addProperty("token", token);
			if (key != null) b.addProperty(key, value);
			send(path, b);
		}, "nizay-friends-act").start();
	}

	public static boolean isNizayUser(String name) { return snap.online.contains(name.toLowerCase(Locale.ROOT)); }

	/** Приставка с логотипом к отображаемому нику. */
	public static Component decorate(String name, Component original) {
		if (!Config.I.friends.logo || !snap.ok || !isNizayUser(name)) return original;
		return Component.literal("\uE000 ").withStyle(ICON).append(original);
	}
}
