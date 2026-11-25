package com.ravecraft.mod.core.acoustics.radiation;

/**
 * One driver (a loudspeaker unit) of a cabinet.
 *
 * @param azimuthDeg where it points, in degrees to the right of the speaker's axis
 * @param elevationDeg where it points, in degrees above the speaker's axis
 * @param right how far right of the cabinet's centre it is, in metres
 * @param up how far above the cabinet's centre it is, in metres
 * @param front how far in front of the cabinet's centre it is, in metres
 * @param radius its radius, in metres
 * @param lowHz the frequency under which it plays less and less, in hertz; 0 for none
 * @param highHz the frequency over which it plays less and less, in hertz; 0 for none
 */
public record Driver(double azimuthDeg, double elevationDeg, double right, double up, double front, double radius,
		double lowHz, double highHz) {
	/** Both crossover slopes: 24 dB per octave. */
	private static final double SLOPE = 4;

	/** How loud its crossover lets it play at {@code hz}, 0 to 1. */
	double weight(double hz) {
		double low = lowHz == 0 ? 1 : share(hz / lowHz);
		double high = highHz == 0 ? 1 : 1 - share(hz / highHz);
		return low * high;
	}

	private static double share(double ratio) {
		double power = Math.pow(ratio, SLOPE);
		return power / (1 + power);
	}
}
