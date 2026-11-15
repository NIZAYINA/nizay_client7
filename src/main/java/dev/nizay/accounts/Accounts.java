package dev.nizay.accounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nizay.mixin.MinecraftAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Связь с менеджером аккаунтов лаунчера. Лаунчер пишет <gameDir>/nizay-accounts.json
 * ({"active": "<id>", "accounts": [{"id","name","type"}...]}), мод читает его и при смене
 * записывает "active" обратно — лаунчер подхватывает выбор при следующем чтении.
 */
public final class Accounts {
	private Accounts() {}

	public record Acc(String id, String name, String type) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path file() { return FabricLoader.getInstance().getGameDir().resolve("nizay-accounts.json"); }

	public static List<Acc> list() {
		List<Acc> out = new ArrayList<>();
		try {
			if (!Files.exists(file())) return out;
			try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
				JsonObject j = GSON.fromJson(r, JsonObject.class);
				if (j == null || !j.has("accounts")) return out;
				JsonArray a = j.getAsJsonArray("accounts");
				for (JsonElement e : a) {
					JsonObject o = e.getAsJsonObject();
					out.add(new Acc(o.get("id").getAsString(), o.get("name").getAsString(), o.has("type") ? o.get("type").getAsString() : "offline"));
				}
			}
		} catch (Exception e) {
			System.err.println("[Nizay] accounts read failed: " + e);
		}
		return out;
	}

	public static String activeId() {
		try {
			if (!Files.exists(file())) return null;
			try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
				JsonObject j = GSON.fromJson(r, JsonObject.class);
				return j != null && j.has("active") && !j.get("active").isJsonNull() ? j.get("active").getAsString() : null;
			}
		} catch (Exception e) { return null; }
	}

	public static String currentName() {
		return Minecraft.getInstance().getUser().getName();
	}

	/** Переключает сессию игры на аккаунт (offline) и сообщает лаунчеру. Действует со следующего подключения к серверу. */
	public static boolean select(Acc a) {
		try {
			UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + a.name()).getBytes(StandardCharsets.UTF_8));
			User u = new User(a.name(), id, "0", Optional.empty(), Optional.empty());
			((MinecraftAccessor) Minecraft.getInstance()).nizay$setUser(u);
			writeActive(a.id());
			return true;
		} catch (Throwable t) {
			System.err.println("[Nizay] account switch failed: " + t);
			return false;
		}
	}

	private static void writeActive(String id) {
		try {
			JsonObject j = new JsonObject();
			if (Files.exists(file())) try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
				JsonObject old = GSON.fromJson(r, JsonObject.class);
				if (old != null) j = old;
			}
			j.addProperty("active", id);
			j.addProperty("changedBy", "game");
			try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) { GSON.toJson(j, w); }
		} catch (Exception e) {
			System.err.println("[Nizay] accounts write failed: " + e);
		}
	}
}
