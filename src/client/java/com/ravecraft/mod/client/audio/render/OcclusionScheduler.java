package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.client.audio.occlusion.WorldAcousticGrid;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.acoustics.occlusion.Occlusion;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionRota;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionTracer;
import com.ravecraft.mod.core.acoustics.occlusion.PathLoss;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;
import com.ravecraft.mod.world.acoustics.BlockAcoustics;
import com.ravecraft.mod.world.acoustics.MediumResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Traces the occlusion of the audible speakers on the client thread, under a voxel budget per tick (the player's
 * occlusion quality), and publishes it into each stream for the audio thread. Each stream is traced every
 * {@value OcclusionRota#PERIOD} ticks, a new one at once. Lives next to {@link AcousticRenderer} so that the
 * streams stay package-private. Client thread only, except the tag reload flag.
 */
public final class OcclusionScheduler {
	private static final Comparator<RenderStream> LOUDEST_FIRST =
			Comparator.comparingDouble((RenderStream stream) -> stream.heardGain).reversed();

	private static final OcclusionRota ROTA = new OcclusionRota();
	private static final List<RenderStream> AUDIBLE = new ArrayList<>();
	private static final List<Integer> AUDIBLE_IDS = new ArrayList<>();
	private static final Set<Integer> ALIVE = new HashSet<>();
	private static final Map<Integer, RenderStream> BY_ID = new HashMap<>();

	/** Set when the client received new tags: the block materials are measured again. */
	private static volatile boolean tagsReloaded;

	// The tracer, block acoustics and medium resolver are always built from the same OcclusionSettings instance:
	// the tracer recognises air by equality with the media the resolver hands out.
	private static @Nullable OcclusionSettings built;
	private static @Nullable BlockAcoustics blocks;
	private static @Nullable MediumResolver media;
	private static @Nullable WorldAcousticGrid grid;
	private static @Nullable OcclusionTracer tracer;
	private static long tick;

	private OcclusionScheduler() {
	}

	/** Registers the tick, after {@link AcousticRenderer#register()} so that the stream states are fresh. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(OcclusionScheduler::tick);
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
			if (client) {
				tagsReloaded = true;
			}
		});
	}

	private static void tick(Minecraft minecraft) {
		tick++;
		AcousticSettings settings = AcousticSettingsClient.get();
		OcclusionSettings occlusion = settings.occlusion();
		BlockAcoustics blockAcoustics = blocks;
		MediumResolver resolver = media;
		if (occlusion != built || blockAcoustics == null || resolver == null) {
			built = occlusion;
			blockAcoustics = blocks = new BlockAcoustics(occlusion);
			resolver = media = new MediumResolver(occlusion);
			grid = null;
			tracer = null;
			tagsReloaded = false;
		} else if (tagsReloaded) {
			tagsReloaded = false;
			blockAcoustics.clearCache();
		}

		ClientLevel level = minecraft.level;
		if (level == null) {
			grid = null;
			tracer = null;
			for (RenderStream stream : AcousticRenderer.streams()) {
				stream.occlusionKnown = false;
			}
			ROTA.forget(Set.of());
			return;
		}
		OcclusionTracer tracer = OcclusionScheduler.tracer;
		if (grid == null || tracer == null || grid.level() != level) {
			grid = new WorldAcousticGrid(level, blockAcoustics, resolver, occlusion);
			tracer = OcclusionScheduler.tracer = new OcclusionTracer(grid, occlusion);
		}

		Vec3 ear = AcousticRenderer.listener();
		AUDIBLE.clear();
		for (RenderStream stream : AcousticRenderer.streams()) {
			if (isAudible(settings, stream, ear)) {
				AUDIBLE.add(stream);
			} else {
				stream.occlusionKnown = false;
			}
		}
		AUDIBLE.sort(LOUDEST_FIRST);
		AUDIBLE_IDS.clear();
		ALIVE.clear();
		BY_ID.clear();
		for (RenderStream stream : AUDIBLE) {
			AUDIBLE_IDS.add(stream.sessionId);
			ALIVE.add(stream.sessionId);
			BY_ID.put(stream.sessionId, stream);
		}
		ROTA.forget(ALIVE);

		if (!occlusion.enabled()) {
			for (RenderStream stream : AUDIBLE) {
				publish(stream, 0, 0);
			}
			return;
		}

		List<Integer> due = ROTA.pick(AUDIBLE_IDS, tick);
		TraceBudget budget = new TraceBudget(RaveCraftOptions.occlusionQuality().get().voxelsPerTick());
		for (int i = 0; i < due.size(); i++) {
			RenderStream stream = BY_ID.get(due.get(i));
			double sx = stream.pos.getX() + 0.5;
			double sy = stream.pos.getY() + 0.5;
			double sz = stream.pos.getZ() + 0.5;
			boolean wholeBudget = budget.used() == 0;
			Occlusion result = tracer.occlusion(ear.x, ear.y, ear.z, sx, sy, sz, settings.rolloff(),
					range(settings, stream), budget);
			if (result == null) {
				if (!wholeBudget) {
					// The rest waits for the next tick, which starts with them.
					for (int j = i; j < due.size(); j++) {
						ROTA.unfinished(due.get(j));
					}
					break;
				}
				// Even a whole tick's budget is not enough for this stream's detours: settle for the straight line,
				// which visits a bounded number of voxels, so that it is never left unmuffled for good.
				result = direct(tracer, ear, sx, sy, sz);
			}
			publish(stream, result.lossDb(), result.highLossDb());
		}
	}

	private static boolean isAudible(AcousticSettings settings, RenderStream stream, Vec3 ear) {
		if (!stream.powered || stream.volume <= 0) {
			return false;
		}
		double dx = ear.x - (stream.pos.getX() + 0.5);
		double dy = ear.y - (stream.pos.getY() + 0.5);
		double dz = ear.z - (stream.pos.getZ() + 0.5);
		return Math.sqrt(dx * dx + dy * dy + dz * dz) < range(settings, stream);
	}

	private static double range(AcousticSettings settings, RenderStream stream) {
		return Propagation.range(settings, settings.portable(), stream.volume);
	}

	/** The straight-line occlusion alone, without a voxel budget (a segment visits a bounded number of voxels). */
	private static Occlusion direct(OcclusionTracer tracer, Vec3 ear, double sx, double sy, double sz) {
		PathLoss path = tracer.trace(ear.x, ear.y, ear.z, sx, sy, sz, new TraceBudget(Integer.MAX_VALUE));
		if (path == null || path.mute()) {
			return Occlusion.MUTE;
		}
		return new Occlusion(path.lossDb(), path.highLossDb());
	}

	/** Both losses first, then the flag: the audio thread never sees a known stream with stale losses. */
	private static void publish(RenderStream stream, double lossDb, double highLossDb) {
		stream.occlusionLossDb = lossDb;
		stream.occlusionHighLossDb = highLossDb;
		stream.occlusionKnown = true;
	}
}
