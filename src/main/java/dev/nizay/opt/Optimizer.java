package dev.nizay.opt;

import dev.nizay.Config;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * Модуль Optimization. Меняет настройки видео игры (запоминая прежние значения и возвращая их при выключении)
 * и включает лимитер частиц Nizay (см. Fx.perf). Всё клиентское — на сервере (в т.ч. FunTime) ничего не видно.
 */
public final class Optimizer {
	private Optimizer() {}

	public static void apply() {
		Minecraft mc = Minecraft.getInstance();
		Options o = mc.options;
		Config.Opt c = Config.I.opt;
		if (o == null) return;
		try {
			if (!c.on) { restore(); return; }
			if (!c.saved) {
				c.origParticles = o.particles().get().name();
				c.origClouds = o.cloudStatus().get().name();
				c.origShadows = o.entityShadows().get();
				c.origAo = o.ambientOcclusion().get();
				c.origEntDist = o.entityDistanceScaling().get();
				c.origRender = o.renderDistance().get();
				c.origSim = o.simulationDistance().get();
				c.origBiome = o.biomeBlendRadius().get();
				c.origVsync = o.enableVsync().get();
				c.origFps = o.framerateLimit().get();
				c.saved = true;
			}
			if (c.minParticles) o.particles().set(ParticleStatus.MINIMAL); else o.particles().set(ParticleStatus.valueOf(c.origParticles));
			if (c.noClouds) o.cloudStatus().set(CloudStatus.OFF); else o.cloudStatus().set(CloudStatus.valueOf(c.origClouds));
			o.entityShadows().set(c.noShadows ? Boolean.FALSE : c.origShadows);
			o.ambientOcclusion().set(c.noAo ? Boolean.FALSE : c.origAo);
			o.entityDistanceScaling().set(Math.min(c.origEntDist, c.entityDist / 100.0));
			o.biomeBlendRadius().set(Math.min(c.origBiome, c.biomeBlend));
			if (c.uncap) { o.enableVsync().set(Boolean.FALSE); o.framerateLimit().set(260); } else { o.enableVsync().set(c.origVsync); o.framerateLimit().set(c.origFps); }
			if (c.renderCap > 0) o.renderDistance().set(Math.min(c.origRender, c.renderCap)); else o.renderDistance().set(c.origRender);
			if (c.simCap > 0) o.simulationDistance().set(Math.min(c.origSim, c.simCap)); else o.simulationDistance().set(c.origSim);
		} catch (Throwable t) {
			System.err.println("[Nizay] optimizer failed: " + t);
		}
	}

	private static void restore() {
		Config.Opt c = Config.I.opt;
		if (!c.saved) return;
		Options o = Minecraft.getInstance().options;
		o.particles().set(ParticleStatus.valueOf(c.origParticles));
		o.cloudStatus().set(CloudStatus.valueOf(c.origClouds));
		o.entityShadows().set(c.origShadows);
		o.ambientOcclusion().set(c.origAo);
		o.entityDistanceScaling().set(c.origEntDist);
		o.renderDistance().set(c.origRender);
		o.simulationDistance().set(c.origSim);
		o.biomeBlendRadius().set(c.origBiome);
		o.enableVsync().set(c.origVsync);
		o.framerateLimit().set(c.origFps);
		c.saved = false;
	}
}
