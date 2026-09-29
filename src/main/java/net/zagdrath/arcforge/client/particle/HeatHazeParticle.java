/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

// arcforge:heat_haze: a faint wavy shimmer that drifts out along its starting motion, rises 0.02 a tick and
// fades over 16-24 ticks.
public class HeatHazeParticle extends SingleQuadParticle {
    private static final float RISE = 0.02F;
    private static final float START_ALPHA = 0.5F;

    private final SpriteSet sprites;

    protected HeatHazeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd + RISE;
        this.zd = zd;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize = 0.5F;
        this.lifetime = 16 + random.nextInt(9);
        setAlpha(START_ALPHA);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        yd = Math.max(yd, RISE);
        setAlpha(START_ALPHA * (1.0F - (float) age / lifetime));
        setSpriteFromAge(sprites);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                RandomSource random) {
            return new HeatHazeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
