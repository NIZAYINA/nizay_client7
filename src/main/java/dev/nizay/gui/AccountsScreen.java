package dev.nizay.gui;

import dev.nizay.accounts.Accounts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Менеджер аккаунтов на титульном экране. Список берётся у лаунчера (nizay-accounts.json). */
public class AccountsScreen extends Screen {
	private final Screen parent;
	private static final int W = 320, ROW = 34;
	private record Hit(float x, float y, float w, float h, Runnable r) {}
	private final List<Hit> hits = new ArrayList<>();
	private float fit, ogx, ogy;
	private String notice = "";

	public AccountsScreen(Screen parent) { super(Component.literal("Accounts")); this.parent = parent; }

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		List<Accounts.Acc> list = Accounts.list();
		int rows = Math.max(1, Math.min(list.size(), 6));
		float H = 118 + rows * ROW;
		fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		ogx = (width - W * fit) / 2f; ogy = (height - H * fit) / 2f;
		float mx = Ui.mx(mouseX, ogx, fit), my = Ui.mx(mouseY, ogy, fit);
		hits.clear();
		Ui.begin(g, ogx, ogy, fit);
		try {
			Ui.shadow(0, 0, W, H, 16, 0x000000, 150, 24);
			Ui.rectB(0, 0, W, H, 16, 0xF4101013, 0xFF2A2A31);
			Ui.text("Accounts", 22, 18, 17, 0xFFF2F2F6, true);
			Ui.text("Synced with the Nizay launcher", 22, 41, 9.5f, 0xFF8E8E99, false);
			Ui.textR("Playing as  " + Accounts.currentName(), W - 22, 24, 9.5f, 0xFFC8C8D0, true);
			String cur = Accounts.currentName();
			float y = 66;
			if (list.isEmpty()) {
				Ui.text("No launcher accounts found.", 22, y + 6, 10, 0xFF8E8E99, false);
				Ui.text("Launch the game from the Nizay launcher.", 22, y + 22, 9, 0xFF5A5A64, false);
			}
			for (int i = 0; i < rows && i < list.size(); i++) {
				Accounts.Acc a = list.get(i);
				boolean isCur = a.name().equals(cur);
				float ry = y + i * ROW;
				boolean hov = mx >= 18 && mx < W - 18 && my >= ry && my < ry + 30;
				Ui.rectB(18, ry, W - 36, 30, 10, hov ? 0xFF1E1E23 : 0xFF17171B, isCur ? 0xFF4A4A55 : 0xFF2A2A31);
				Ui.circle(38, ry + 15, 9, 0xFF24242B);
				Ui.textC(a.name().isEmpty() ? "?" : a.name().substring(0, 1).toUpperCase(), 38, ry + 9, 10.5f, 0xFFF2F2F6, true);
				Ui.text(a.name(), 56, ry + 7, 11, 0xFFF2F2F6, true);
				Ui.text(a.type(), 56 + Ui.width(a.name() + "  ", 11, true), ry + 9, 8.5f, 0xFF8E8E99, false);
				if (isCur) Ui.textR("In use", W - 32, ry + 10, 9, 0xFF8E8E99, false);
				else { Ui.rectB(W - 92, ry + 5, 60, 20, 7, 0xFFEDEDF2, 0xFFEDEDF2); Ui.textC("Use", W - 62, ry + 10, 9.5f, 0xFF0B0B0D, true);
					hits.add(new Hit(18, ry, W - 36, 30, () -> { notice = Accounts.select(a) ? "Switched to " + a.name() : "Could not switch"; })); }
			}
			if (!notice.isEmpty()) Ui.text(notice, 22, H - 52, 9, 0xFF8E8E99, false);
			boolean hb = mx >= W - 108 && mx < W - 18 && my >= H - 40 && my < H - 14;
			Ui.rectB(W - 108, H - 40, 90, 26, 8, hb ? 0xFF1E1E23 : 0xFF1B1B20, 0xFF2A2A31);
			Ui.textC("Back", W - 63, H - 32, 10, 0xFFF2F2F6, true);
			hits.add(new Hit(W - 108, H - 40, 90, 26, () -> minecraft.setScreen(parent)));
		} finally { Ui.end(); }
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() != 0) return false;
		float mx = Ui.mx(e.x(), ogx, fit), my = Ui.mx(e.y(), ogy, fit);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) { h.r().run(); return true; }
		}
		return false;
	}

	@Override public boolean keyPressed(KeyEvent e) {
		if (e.key() == 256) { minecraft.setScreen(parent); return true; }
		return super.keyPressed(e);
	}
}
