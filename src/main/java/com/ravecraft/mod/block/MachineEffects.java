package com.ravecraft.mod.block;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;

/**
 * What a working machine plays and shows on the client: a sound now and then, and a particle at its front.
 *
 * @param sound the sound played while it works
 * @param pitch the sound's base pitch, raised a little at random
 * @param particle the particle shown at the front
 */
public record MachineEffects(SoundEvent sound, float pitch, ParticleOptions particle) {
}
