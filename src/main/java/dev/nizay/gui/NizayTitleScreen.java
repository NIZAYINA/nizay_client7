package dev.nizay.gui;

import dev.nizay.NizayClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Главное меню Nizay: тёмный космос, логотип и скруглённые кнопки в стиле клиента. Заменяет ванильный TitleScreen. */
public class NizayTitleScreen extends Screen {
	private static final Identifier BG = Identifier.fromNamespaceAndPath("nizay", "textures/gui/title_bg.png");
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nizay", "textures/gui/logo.png");
	private record Btn(float x, float y, float w, float h, String label, String ru, Runnable act) {}
	private final List<Btn> btns = new ArrayList<>();
	private float fit, ogx, ogy;
	private static final int W = 300, H = 330;
	private final float[][] stars = new float[70][4];

	public NizayTitleScreen() {
		super(Component.literal("Nizay client"));
		Random r = new Random(7);
		for (float[] s : stars) { s[0] = r.nextFloat(); s[1] = r.nextFloat(); s[2] = r.nextFloat() * 6.28f; s[3] = .4f + r.nextFloat() * .6f; }
	}

	@Override public boolean shouldCloseOnEsc() { return false; }

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* рисуем сами */ }

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		double t = System.nanoTime() / 1e9;
		// фон: медленный «дрейф» по картинке 1280x720 с покрытием экрана
		Ui.begin(g, 0, 0, 1f);
		try {
			float sx = Ui.S * width, sy = Ui.S * height;                 // экран в физ. пикселях
			float scale = Math.max(sx / 1200f, sy / 676f);
			int rw = Math.round(sx / scale), rh = Math.round(sy / scale);
			int u = Math.round(40 + (float) Math.sin(t * .05) * 38), v = Math.round(22 + (float) Math.cos(t * .04) * 20);
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BG, 0, 0, (float) u, (float) v, Math.round(sx), Math.round(sy), rw, rh, 1280, 720);
			g.fill(0, 0, Math.round(sx), Math.round(sy), 0x55000000);
			for (float[] s : stars) {
				float a = .25f + .55f * (float) (.5 + .5 * Math.sin(t * s[3] * 1.6 + s[2]));
				int px = Math.round(s[0] * sx), py = Math.round(s[1] * sy * .85f), z = Math.max(1, Math.round(Ui.S * .9f));
				g.fill(px, py, px + z, py + z, ((int) (a * 255) << 24) | 0xFFFFFF);
			}
		} finally { Ui.end(); }

		fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		ogx = (width - W * fit) / 2f; ogy = (height - H * fit) / 2f;
		float mx = Ui.mx(mouseX, ogx, fit), my = Ui.mx(mouseY, ogy, fit);
		btns.clear();
		Ui.begin(g, ogx, ogy, fit);
		try {
			Ui.image(LOGO, 128, 128, W / 2f - 36, 6, 72, 72);
			Ui.textC("Nizay client", W / 2f, 88, 26, 0xFFF2F2F6, true);
			Ui.textC("Minecraft 1.21.11  |  Fabric", W / 2f, 122, 9.5f, 0xFF8E8E99, false);
			String[] labels = {"Singleplayer", "Multiplayer", "Options", "Accounts", "Quit Game"};
			String[] ru = {"Одиночная игра", "Сетевая игра", "Настройки игры", "Менеджер аккаунтов", "Выйти из игры"};
			Runnable[] acts = {
				() -> minecraft.setScreen(new SelectWorldScreen(this)),
				() -> minecraft.setScreen(new JoinMultiplayerScreen(this)),
				() -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)),
				() -> minecraft.setScreen(new AccountsScreen(this)),
				() -> minecraft.stop()};
			for (int i = 0; i < labels.length; i++) {
				float by = 148 + i * 38, bw = 240, bx = (W - bw) / 2f;
				boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + 32;
				Ui.shadow(bx, by, bw, 32, 11, 0x000000, 90, 7);
				Ui.rectB(bx, by, bw, 32, 11, hov ? 0xFF22222A : 0xF0141418, hov ? 0xFF4A4A55 : 0xFF2A2A31);
				Ui.text(labels[i], bx + 16, by + 6, 11, 0xFFF2F2F6, true);
				Ui.text(ru[i], bx + 16, by + 19, 8, 0xFF7A7A86, false);
				btns.add(new Btn(bx, by, bw, 32, labels[i], ru[i], acts[i]));
			}
			Ui.textC("Nizay client v" + NizayClient.VERSION, W / 2f, H - 12, 8.5f, 0xFF5A5A64, false);
		} finally { Ui.end(); }
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() != 0) return false;
		float mx = Ui.mx(e.x(), ogx, fit), my = Ui.mx(e.y(), ogy, fit);
		for (Btn b : btns) if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) { b.act().run(); return true; }
		return false;
	}
}
