package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Decibels;

/** Loss at the boundary between two media, from the transmission coefficient of their impedances. */
public final class Interfaces {
	/** The numerator of the transmission coefficient {@code 4·z1·z2 / (z1 + z2)²}. */
	private static final double TRANSMISSION_FACTOR = 4;

	private Interfaces() {
	}

	/**
	 * Broadband loss when sound crosses from {@code a} to {@code b} (symmetric), scaled by {@code interfaceScale}:
	 * 0 for equal impedances, {@link Double#POSITIVE_INFINITY} when nothing is transmitted.
	 */
	public static double lossDb(AcousticMedium a, AcousticMedium b, double interfaceScale) {
		double z1 = a.impedance();
		double z2 = b.impedance();
		double sum = z1 + z2;
		if (sum == 0) {
			return Double.POSITIVE_INFINITY;
		}
		double transmission = TRANSMISSION_FACTOR * z1 * z2 / (sum * sum);
		if (transmission <= 0) {
			return Double.POSITIVE_INFINITY;
		}
		double loss = -Decibels.fromPower(transmission) * interfaceScale;
		return loss <= 0 ? 0 : loss;
	}

	/** The extra treble loss of an interface, a fixed share of its broadband loss. */
	public static double highLossDb(double lossDb, double interfaceHighRatio) {
		return lossDb * interfaceHighRatio;
	}
}
