package dev.nizay.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Универсальная частица. Параметры берутся из статических pending-полей (их выставляет Fx.Ctx).
 * Два режима:
 *  - обычный: живёт pLife тиков, плавно появляется/исчезает;
 *  - «непрерывный» (pCont): живёт ровно один тик и интерполируется между двумя заданными позициями —
 *    так орбиты, кольца и крылья двигаются плавно на любом FPS и не отстают от игрока.
 */
public class NizayParticle extends SingleQuadParticle {
	static float pLife = 20, pSize = .2f, pAlpha = 1, pGrav = 0, pSpin = 0, pR = 1, pG = 1, pB = 1;
	public static float pRoll = Float.NaN, pRollPrev = Float.NaN;
	static boolean pCont;
	static double pX0, pY0, pZ0;

	/** Сколько частиц Nizay живо сейчас (для лимитера). */
	public static int alive;

	private final float baseAlpha, spinV, grav;
	private final boolean cont;
	private boolean dead;

	NizayParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.xd = vx; this.yd = vy; this.zd = vz;
		this.cont = pCont;
		this.lifetime = cont ? 0 : Math.max(1, (int) pLife);
		this.quadSize = pSize;
		this.baseAlpha = pAlpha;
		this.grav = pGrav;
		this.spinV = pSpin;
		this.rCol = pR; this.gCol = pG; this.bCol = pB;
		this.hasPhysics = false;
		if (cont) {
			this.xo = pX0; this.yo = pY0; this.zo = pZ0;
			this.alpha = pAlpha;
		} else {
			this.alpha = 0f;
		}
		this.roll = Float.isNaN(pRoll) ? (float) (Math.random() * Math.PI * 2) : pRoll;
		this.oRoll = Float.isNaN(pRollPrev) ? this.roll : pRollPrev;
		pRoll = Float.NaN; pRollPrev = Float.NaN; pCont = false;
		alive++;
	}

	@Override
	public void remove() {
		if (!dead) { dead = true; alive--; }
		super.remove();
	}

	@Override
	public void tick() {
		if (cont) { this.remove(); return; }
		this.xo = x; this.yo = y; this.zo = z;
		this.oRoll = roll;
		if (this.age++ >= this.lifetime) { this.remove(); return; }
		this.yd -= 0.04 * grav;
		this.move(this.xd, this.yd, this.zd);
		this.roll += spinV;
		float f = (float) age / lifetime;
		this.alpha = baseAlpha * Math.min(1f, Math.min(f / .12f, (1f - f) / .45f));
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		public Provider(SpriteSet sprites) { this.sprites = sprites; }

		@Override
		public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level,
				double x, double y, double z, double vx, double vy, double vz, RandomSource random) {
			return new NizayParticle(level, x, y, z, vx, vy, vz, sprites.first());
		}
	}
}
