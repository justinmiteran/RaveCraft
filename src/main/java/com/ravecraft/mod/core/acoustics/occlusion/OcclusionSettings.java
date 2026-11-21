package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Bound;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The server's occlusion model: the media sound travels through, the materials that block it, how a dimension maps
 * to a medium, and the loss conventions. {@code lossDb} is always the broadband loss and {@code highLossDb} the
 * extra treble loss on top of it. Lists keep file order and are immutable.
 */
public record OcclusionSettings(boolean enabled, double maxLossDb, List<AcousticMedium> media, double interfaceScale,
		double interfaceHighRatio, Map<String, String> dimensions, List<AcousticMaterial> materials, boolean detours,
		double bendLossDb, double bendHighLossDb) {
	public static final int MAX_MEDIA = 16;
	public static final int MAX_MATERIALS = 32;
	public static final int MAX_DIMENSIONS = 32;
	public static final int MAX_NAME = 32;
	public static final int MAX_DIMENSION_ID = 64;

	/** The base materials, in table order; they come back with their defaults if the config removes them. */
	public static final List<String> BASE_MATERIALS =
			List.of("foliage", "glass", "wood", "snow", "earth", "stone", "metal", "wool");

	private static final List<AcousticMaterial> BASE_DEFAULTS = List.of(
			new AcousticMaterial("foliage", 2, 6, 0.5),
			new AcousticMaterial("glass", 5, 12, 0.03),
			new AcousticMaterial("wood", 8, 18, 0.1),
			new AcousticMaterial("snow", 10, 24, 0.4),
			new AcousticMaterial("earth", 12, 24, 0.3),
			new AcousticMaterial("stone", 15, 30, 0.02),
			new AcousticMaterial("metal", 18, 20, 0.05),
			new AcousticMaterial("wool", 20, 36, 0.7));
	private static final AcousticMedium AIR = new AcousticMedium("air", 415, 0, 0.05, 0, 0, 0, 1);

	public static final OcclusionSettings DEFAULT = new OcclusionSettings(true, 60, List.of(
			AIR,
			new AcousticMedium("water", 1_480_000, 0, 0.02, 6, 30, 0.8, 0.01),
			new AcousticMedium("lava", 6_000_000, 3, 6, 20, 24, 0.9, 0.05),
			new AcousticMedium("vacuum", 0, 0, 0, 0, 0, 0, 1)),
			0.67, 0.5, Map.of(), BASE_DEFAULTS, true, 6, 10);

	private static final Bound IMPEDANCE = new Bound(0, 100_000_000);
	private static final Bound LOSS_DB = new Bound(0, 40);
	private static final Bound LISTENER_LOSS_DB = new Bound(0, 60);
	private static final Bound MAX_LOSS_DB = new Bound(20, 120);
	private static final Bound HIGH_RATIO = new Bound(0, 2);

	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1," + MAX_NAME + "}");
	private static final Pattern DIMENSION_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	/** Copies the lists and the map, so the settings never change. */
	public OcclusionSettings {
		media = List.copyOf(media);
		materials = List.copyOf(materials);
		dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
	}

	/** The medium called {@code name}, or null if there is none. */
	public @Nullable AcousticMedium medium(String name) {
		for (AcousticMedium m : media) {
			if (m.name().equals(name)) {
				return m;
			}
		}
		return null;
	}

	/** Plain air, the medium everything is surrounded by unless a dimension says otherwise. */
	public AcousticMedium air() {
		AcousticMedium air = medium("air");
		return air != null ? air : AIR;
	}

	/** The medium mapped to a dimension, or air when the dimension is not mapped. */
	public AcousticMedium dimensionMedium(String dimensionId) {
		String name = dimensions.get(dimensionId);
		AcousticMedium m = name == null ? null : medium(name);
		return m != null ? m : air();
	}

	/** The same settings with every value brought into its bounds; {@code warn} hears about each change. */
	public OcclusionSettings clamped(Consumer<String> warn) {
		List<AcousticMedium> keptMedia = clampedMedia(warn);
		Set<String> mediumNames = new LinkedHashSet<>();
		keptMedia.forEach(m -> mediumNames.add(m.name()));
		List<AcousticMaterial> keptMaterials = clampedMaterials(warn);
		Map<String, String> keptDimensions = clampedDimensions(mediumNames, warn);
		return new OcclusionSettings(enabled,
				MAX_LOSS_DB.apply("occlusion.maxLossDb", maxLossDb, warn),
				keptMedia,
				Bound.UNIT.apply("occlusion.interfaceScale", interfaceScale, warn),
				HIGH_RATIO.apply("occlusion.interfaceHighRatio", interfaceHighRatio, warn),
				keptDimensions, keptMaterials, detours,
				LOSS_DB.apply("occlusion.bendLossDb", bendLossDb, warn),
				LOSS_DB.apply("occlusion.bendHighLossDb", bendHighLossDb, warn));
	}

	/** The media with valid, distinct names and their values in bounds; air comes back if missing. */
	private List<AcousticMedium> clampedMedia(Consumer<String> warn) {
		List<AcousticMedium> kept = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (AcousticMedium m : media) {
			if (!validName("occlusion.media", m.name(), seen, warn)) {
				continue;
			}
			String p = "occlusion.media." + m.name() + ".";
			kept.add(new AcousticMedium(m.name(),
					IMPEDANCE.apply(p + "impedance", m.impedance(), warn),
					LOSS_DB.apply(p + "lossDbPerBlock", m.lossDbPerBlock(), warn),
					LOSS_DB.apply(p + "highLossDbPerBlock", m.highLossDbPerBlock(), warn),
					LISTENER_LOSS_DB.apply(p + "listenerLossDb", m.listenerLossDb(), warn),
					LOSS_DB.apply(p + "listenerHighLossDb", m.listenerHighLossDb(), warn),
					Bound.UNIT.apply(p + "listenerBlur", m.listenerBlur(), warn),
					Bound.UNIT.apply(p + "surfaceAbsorption", m.surfaceAbsorption(), warn)));
		}
		return capped("occlusion.media", kept, MAX_MEDIA, List.of(AIR), AcousticMedium::name, warn);
	}

	/** The materials with valid, distinct names and their values in bounds; base materials come back if missing. */
	private List<AcousticMaterial> clampedMaterials(Consumer<String> warn) {
		List<AcousticMaterial> kept = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (AcousticMaterial m : materials) {
			if (!validName("occlusion.materials", m.name(), seen, warn)) {
				continue;
			}
			String p = "occlusion.materials." + m.name() + ".";
			kept.add(new AcousticMaterial(m.name(),
					LOSS_DB.apply(p + "lossDb", m.lossDb(), warn),
					LOSS_DB.apply(p + "highLossDb", m.highLossDb(), warn),
					Bound.UNIT.apply(p + "absorption", m.absorption(), warn)));
		}
		return capped("occlusion.materials", kept, MAX_MATERIALS, BASE_DEFAULTS, AcousticMaterial::name, warn);
	}

	/** The dimension mappings with a valid id and a known medium, up to {@link #MAX_DIMENSIONS}. */
	private Map<String, String> clampedDimensions(Set<String> mediumNames, Consumer<String> warn) {
		Map<String, String> kept = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : dimensions.entrySet()) {
			String id = e.getKey();
			String medium = e.getValue();
			if (id == null || id.length() > MAX_DIMENSION_ID || !DIMENSION_ID.matcher(id).matches()) {
				warn.accept("occlusion.dimensions." + id + " is not a valid dimension id, dropped");
			} else if (medium == null || !mediumNames.contains(medium)) {
				warn.accept("occlusion.dimensions." + id + " names the unknown medium " + medium + ", dropped");
			} else {
				kept.put(id, medium);
			}
		}
		if (kept.size() <= MAX_DIMENSIONS) {
			return kept;
		}
		warn.accept("occlusion.dimensions has " + kept.size() + " entries, keeping the first " + MAX_DIMENSIONS);
		Map<String, String> head = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : kept.entrySet()) {
			if (head.size() < MAX_DIMENSIONS) {
				head.put(e.getKey(), e.getValue());
			}
		}
		return head;
	}

	/** True for a well-formed, not yet seen name; otherwise warns and returns false. */
	private static boolean validName(String list, String name, Set<String> seen, Consumer<String> warn) {
		if (name == null || !NAME.matcher(name).matches()) {
			warn.accept(list + " entry \"" + name + "\" is not a valid name ([a-z0-9_], 1 to " + MAX_NAME
					+ " characters), dropped");
			return false;
		}
		if (!seen.add(name)) {
			warn.accept(list + "." + name + " is defined twice, keeping the first");
			return false;
		}
		return true;
	}

	/**
	 * Keeps the first {@code max} entries (file order), then brings back any required entry the file lacks, replacing
	 * the last entries that are not required when the list is full.
	 */
	private static <T> List<T> capped(String list, List<T> entries, int max, List<T> required,
			Function<T, String> name, Consumer<String> warn) {
		List<T> out = new ArrayList<>(entries);
		if (out.size() > max) {
			warn.accept(list + " has " + out.size() + " entries, keeping the first " + max);
			out = new ArrayList<>(out.subList(0, max));
		}
		Set<String> present = new LinkedHashSet<>();
		out.forEach(e -> present.add(name.apply(e)));
		Set<String> requiredNames = new LinkedHashSet<>();
		required.forEach(e -> requiredNames.add(name.apply(e)));
		List<T> missing = required.stream().filter(e -> !present.contains(name.apply(e))).toList();
		int i = out.size() - 1;
		while (out.size() + missing.size() > max && i >= 0) {
			if (!requiredNames.contains(name.apply(out.get(i)))) {
				out.remove(i);
			}
			i--;
		}
		out.addAll(missing);
		return out;
	}
}
