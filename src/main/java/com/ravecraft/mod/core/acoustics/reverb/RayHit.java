package com.ravecraft.mod.core.acoustics.reverb;

/**
 * Where one probe ray ended: {@code distance} in blocks and the share of sound the surface it met absorbs. An open
 * ray met nothing within its length: its distance is the ray length and its absorption 1 (it escapes).
 */
public record RayHit(boolean open, double distance, double absorption) {
}
