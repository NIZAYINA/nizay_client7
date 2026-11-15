package dev.nizay;

import dev.nizay.fx.Fx;
import dev.nizay.gui.NizayMenuScreen;
import dev.nizay.gui.NizayTitleScreen;
import dev.nizay.friends.Friends;
import dev.nizay.util.Utility;
import dev.nizay.hud.NizayHud;
import dev.nizay.opt.Optimizer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class NizayClient implements ClientModInitializer {
	public static final String VERSION = "0.3.0";
	private boolean rshiftDown, optApplied;

	@Override
	public void onInitializeClient() {
		Config.load();
		Friends.start();
		Fx.register();
		NizayHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (!optApplied && mc.options != null) { optApplied = true; Optimizer.apply(); }
			if (mc.screen instanceof TitleScreen) mc.setScreen(new NizayTitleScreen());
		});
		Runtime.getRuntime().addShutdownHook(new Thread(() -> { Utility.shutdown(); Config.save(); }));
	}

	private void tick(Minecraft mc) {
		// Right Shift — меню. Опрашиваем GLFW напрямую, чтобы не зависеть от API KeyMapping.
		long win = GLFW.glfwGetCurrentContext();
		boolean down = win != 0 && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		if (down && !rshiftDown && mc.level != null) {
			if (mc.screen == null) mc.setScreen(new NizayMenuScreen());
			else if (mc.screen instanceof NizayMenuScreen) mc.setScreen(null);
		}
		rshiftDown = down;

		Friends.updatePos(mc);
		Utility.tick(mc);
		Fx.tick(mc);
	}
}
