package dev.nizay.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Нужен менеджеру аккаунтов: смена ника (offline-сессии) без перезапуска игры. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("user")
	void nizay$setUser(User user);
}
