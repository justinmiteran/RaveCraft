package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParamGlideTest {
	private static final ReverbParams HALL = new ReverbParams(3.0, 0.5, 0.3, 0.15, 0.05, 0.04, 0.4, 0.9, 0.994);

	@Test
	void startsSettledOnItsInitialValue() {
		ParamGlide g = new ParamGlide(ReverbParams.SILENT);
		assertEquals(ReverbParams.SILENT, g.at(ReverbParams.SILENT, 1_000));
		assertTrue(g.settled());
	}

	@Test
	void glidesLinearlyOverOneSecond() {
		ParamGlide g = new ParamGlide(ReverbParams.SILENT);
		assertEquals(ReverbParams.SILENT, g.at(HALL, 10_000));
		assertEquals(0.15, g.at(HALL, 10_500).gain(), 1e-12);
		assertEquals(HALL, g.at(HALL, 11_000));
		assertTrue(g.settled());
		assertEquals(HALL, g.at(HALL, 15_000));
	}

	@Test
	void retargetMidGlideStartsFromTheCurrentValue() {
		ParamGlide g = new ParamGlide(ReverbParams.SILENT);
		g.at(HALL, 0);
		ReverbParams mid = g.at(HALL, 500);
		assertEquals(mid, g.at(ReverbParams.SILENT, 500));
		assertEquals(0.075, g.at(ReverbParams.SILENT, 1_000).gain(), 1e-12);
		assertEquals(ReverbParams.SILENT, g.at(ReverbParams.SILENT, 1_500));
	}

	@Test
	void aShiftedClockResumesWhereItStopped() {
		ParamGlide g = new ParamGlide(ReverbParams.SILENT);
		g.at(HALL, 0);
		g.at(HALL, 250);
		g.shift(10_000);
		assertEquals(0.15, g.at(HALL, 10_500).gain(), 1e-12);
	}
}
