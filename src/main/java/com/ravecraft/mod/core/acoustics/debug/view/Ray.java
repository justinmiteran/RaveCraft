package com.ravecraft.mod.core.acoustics.debug.view;

import com.ravecraft.mod.core.acoustics.Vec;

/** A probe ray: where it ended, whether it met nothing, what the surface absorbs, whether it makes the echo. */
public record Ray(Vec end, boolean open, double absorption, boolean echo) {
}
