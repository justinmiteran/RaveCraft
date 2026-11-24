package com.ravecraft.mod.block;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;

/**
 * What a working machine plays and shows on the client: a sound looped as long as it works, and a particle at its
 * front now and then.
 *
 * @param sound the loop played while it works
 * @param particle the particle shown at the front
 */
public record MachineEffects(SoundEvent sound, ParticleOptions particle) {
}
