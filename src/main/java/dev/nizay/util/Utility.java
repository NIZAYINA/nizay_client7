package dev.nizay.util;

import dev.nizay.Config;
import dev.nizay.mixin.OptionInstanceAccessor;
import net.minecraft.client.Minecraft;

/** Fullbright: гамма до 1500%, чтобы видеть в полной темноте. Прежнее значение запоминается и возвращается. */
public final class Utility {
	private Utility() {}

	@SuppressWarnings("unchecked")
	public static void tick(Minecraft mc) {
		if (mc.options == null) return;
		Config.Bright b = Config.I.bright;
		var gamma = mc.options.gamma();
		if (b.on) {
			if (b.origGamma < 0) b.origGamma = gamma.get();
			double v = b.level / 100.0;
			if (Math.abs(gamma.get() - v) > 1e-3) ((OptionInstanceAccessor<Double>) (Object) gamma).nizay$setValue(v);
		} else if (b.origGamma >= 0) {
			((OptionInstanceAccessor<Double>) (Object) gamma).nizay$setValue(Math.min(1.0, b.origGamma));
			b.origGamma = -1;
		}
	}

	/** Вызывается при выходе, чтобы options.txt не сохранил гамму 15. */
	public static void shutdown() {
		try {
			Minecraft mc = Minecraft.getInstance();
			Config.Bright b = Config.I.bright;
			if (mc.options != null && b.origGamma >= 0) ((OptionInstanceAccessor<Double>) (Object) mc.options.gamma()).nizay$setValue(Math.min(1.0, b.origGamma));
		} catch (Throwable ignored) {}
	}
}
