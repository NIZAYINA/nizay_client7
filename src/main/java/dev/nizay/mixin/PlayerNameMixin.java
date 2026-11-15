package dev.nizay.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.nizay.friends.Friends;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Логотип Nizay слева от ника игроков, которые тоже играют через лаунчер. */
@Mixin(Player.class)
public abstract class PlayerNameMixin {
	@ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"), require = 0)
	private Component nizay$logo(Component original) {
		return Friends.decorate(((Player) (Object) this).getName().getString(), original);
	}
}
