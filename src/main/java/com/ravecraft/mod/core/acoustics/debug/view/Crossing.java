package com.ravecraft.mod.core.acoustics.debug.view;

/**
 * A block the direct path loses in: its position, what it is ("stone", "air→water", "water"), its loss and the loss
 * from the source up to and including it.
 */
public record Crossing(int x, int y, int z, String name, double lossDb, double cumulativeDb) {
}
