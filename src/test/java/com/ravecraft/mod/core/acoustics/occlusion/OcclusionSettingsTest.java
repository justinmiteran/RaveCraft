package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OcclusionSettingsTest {
	private static final OcclusionSettings D = OcclusionSettings.DEFAULT;

	private static OcclusionSettings with(List<AcousticMedium> media, Map<String, String> dims,
			List<AcousticMaterial> materials) {
		return new OcclusionSettings(true, 60, media, 0.67, 0.5, dims, materials, true, 6, 10);
	}

	@Test
	void defaultsMatchSpec() {
		assertEquals(List.of("air", "water", "lava", "vacuum"), D.media().stream().map(AcousticMedium::name).toList());
		assertEquals(OcclusionSettings.BASE_MATERIALS, D.materials().stream().map(AcousticMaterial::name).toList());
		assertEquals(List.of("foliage", "glass", "wood", "snow", "earth", "stone", "metal", "wool"),
				OcclusionSettings.BASE_MATERIALS);
		double[][] expected = {{2, 4}, {5, 6}, {8, 10}, {10, 14}, {12, 12}, {15, 15}, {18, 12}, {20, 20}};
		for (int i = 0; i < 8; i++) {
			assertEquals(expected[i][0], D.materials().get(i).lossDb());
			assertEquals(expected[i][1], D.materials().get(i).highLossDb());
		}
		assertEquals(new AcousticMedium("air", 415, 0, 0.05), D.media().get(0));
		assertEquals(new AcousticMedium("water", 1_480_000, 0, 0.02), D.media().get(1));
		assertEquals(new AcousticMedium("lava", 6_000_000, 3, 6), D.media().get(2));
		assertEquals(new AcousticMedium("vacuum", 0, 0, 0), D.media().get(3));
		assertTrue(D.enabled());
		assertEquals(60, D.maxLossDb());
		assertEquals(0.67, D.interfaceScale());
		assertEquals(0.5, D.interfaceHighRatio());
		assertTrue(D.detours());
		assertEquals(6, D.bendLossDb());
		assertEquals(10, D.bendHighLossDb());
		assertTrue(D.dimensions().isEmpty());
	}

	@Test
	void defaultsAreStable() {
		List<String> warnings = new ArrayList<>();
		assertEquals(D, D.clamped(warnings::add));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void clampsEveryBound() {
		List<String> warnings = new ArrayList<>();
		OcclusionSettings s = new OcclusionSettings(true, 500, List.of(
				new AcousticMedium("air", -5, 99, 99),
				new AcousticMedium("water", 2e9, 0, Double.NaN)),
				-1, 9, Map.of(), List.of(new AcousticMaterial("stone", 99, -2)), true, -3, 50)
				.clamped(warnings::add);
		assertEquals(120, s.maxLossDb());
		assertEquals(0, s.interfaceScale());
		assertEquals(2, s.interfaceHighRatio());
		assertEquals(0, s.bendLossDb());
		assertEquals(40, s.bendHighLossDb());
		AcousticMedium air = s.medium("air");
		assertEquals(0, air.impedance());
		assertEquals(40, air.lossDbPerBlock());
		assertEquals(40, air.highLossDbPerBlock());
		assertEquals(100_000_000, s.medium("water").impedance());
		assertEquals(0, s.medium("water").highLossDbPerBlock());
		AcousticMaterial stone = s.materials().stream().filter(m -> m.name().equals("stone")).findFirst().orElseThrow();
		assertEquals(40, stone.lossDb());
		assertEquals(0, stone.highLossDb());
		assertEquals(12, warnings.size(), warnings::toString);
		assertTrue(warnings.contains("occlusion.materials.stone.lossDb = 99.0 is outside [0.0, 40.0], using 40.0"));
	}

	@Test
	void dropsInvalidNames() {
		List<String> warnings = new ArrayList<>();
		Map<String, String> dims = new LinkedHashMap<>();
		dims.put("moon", "vacuum");
		dims.put("ad_astra:moon", "lava2");
		dims.put("ad_astra:mars", "vacuum");
		OcclusionSettings s = with(List.of(new AcousticMedium("", 1, 0, 0), D.media().get(0), D.media().get(3)), dims,
				List.of(new AcousticMaterial("Stone!", 1, 1))).clamped(warnings::add);
		assertNull(s.medium(""));
		assertEquals(Map.of("ad_astra:mars", "vacuum"), s.dimensions());
		assertTrue(s.materials().stream().noneMatch(m -> m.name().equals("Stone!")));
		assertEquals(4, warnings.size(), warnings::toString);
	}

	@Test
	void dedupesByNameFirstWins() {
		List<String> warnings = new ArrayList<>();
		OcclusionSettings s = with(List.of(D.media().get(0)), Map.of(),
				List.of(new AcousticMaterial("stone", 1, 1), new AcousticMaterial("stone", 2, 2)))
				.clamped(warnings::add);
		assertEquals(1, s.materials().stream().filter(m -> m.name().equals("stone")).count());
		assertEquals(1, s.materials().get(0).lossDb());
		assertEquals(1, warnings.size(), warnings::toString);
	}

	@Test
	void capsListsInFileOrder() {
		List<String> warnings = new ArrayList<>();
		List<AcousticMaterial> mats = new ArrayList<>(D.materials());
		for (int i = 0; i < 40; i++) {
			mats.add(new AcousticMaterial("m" + i, 1, 1));
		}
		OcclusionSettings s = with(D.media(), Map.of(), mats).clamped(warnings::add);
		assertEquals(32, s.materials().size());
		List<String> names = s.materials().stream().map(AcousticMaterial::name).toList();
		assertEquals(OcclusionSettings.BASE_MATERIALS, names.subList(0, 8));
		for (int i = 0; i < 24; i++) {
			assertEquals("m" + i, names.get(8 + i));
		}
		assertEquals(1, warnings.size(), warnings::toString);

		List<AcousticMaterial> custom = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			custom.add(new AcousticMaterial("m" + i, 1, 1));
		}
		s = with(D.media(), Map.of(), custom).clamped(w -> {});
		assertEquals(32, s.materials().size());
		names = s.materials().stream().map(AcousticMaterial::name).toList();
		assertTrue(names.containsAll(OcclusionSettings.BASE_MATERIALS));
		assertEquals("m0", names.get(0));
		assertEquals("m23", names.get(23));

		List<AcousticMedium> media = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			media.add(new AcousticMedium("x" + i, 1, 0, 0));
		}
		s = with(media, Map.of(), D.materials()).clamped(w -> {});
		assertEquals(16, s.media().size());
		assertNotNull(s.medium("air"));
		assertEquals("x0", s.media().get(0).name());

		Map<String, String> dims = new LinkedHashMap<>();
		for (int i = 0; i < 40; i++) {
			dims.put("ns:d" + i, "air");
		}
		s = with(D.media(), dims, D.materials()).clamped(w -> {});
		assertEquals(32, s.dimensions().size());
		assertEquals("ns:d0", s.dimensions().keySet().iterator().next());
	}

	@Test
	void restoresAirAndBaseMaterials() {
		OcclusionSettings s = with(List.of(D.media().get(1)), Map.of(),
				D.materials().stream().filter(m -> !m.name().equals("wool")).toList()).clamped(w -> {});
		assertEquals(D.medium("air"), s.medium("air"));
		AcousticMaterial wool = s.materials().stream().filter(m -> m.name().equals("wool")).findFirst().orElseThrow();
		assertEquals(20, wool.lossDb());
		assertEquals(20, wool.highLossDb());
	}

	@Test
	void dimensionMediumFallsBackToAir() {
		assertEquals(D.air(), D.dimensionMedium("minecraft:overworld"));
		OcclusionSettings s = with(D.media(), Map.of("ad_astra:moon", "vacuum"), D.materials());
		assertEquals(D.medium("vacuum"), s.dimensionMedium("ad_astra:moon"));
		assertEquals(D.air(), s.dimensionMedium("minecraft:overworld"));
	}
}
