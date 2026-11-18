package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.client.audio.occlusion.WorldAcousticGrid;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.acoustics.occlusion.Occlusion;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionPass;
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
	/** On the snapshot taken before sorting: the audio thread rewrites {@code heardGain} during the sort. */
	private static final Comparator<RenderStream> LOUDEST_FIRST =
			Comparator.comparingDouble((RenderStream stream) -> stream.sortGain).reversed();

	private static final OcclusionRota ROTA = new OcclusionRota();
	private static final List<RenderStream> AUDIBLE = new ArrayList<>();
	private static final List<Integer> AUDIBLE_IDS = new ArrayList<>();
	private static final List<RenderStream> DUE = new ArrayList<>();
	private static final Set<Integer> ALIVE = new HashSet<>();
	private static Map<Integer, RenderStream> byId = new HashMap<>();
	/** Last tick's audible streams, to notice a session that restarted with a new stream under the same id. */
	private static Map<Integer, RenderStream> previousById = new HashMap<>();

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
			previousById.clear();
			byId.clear();
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
				stream.sortGain = stream.heardGain;
				AUDIBLE.add(stream);
			} else {
				stream.occlusionKnown = false;
			}
		}
		AUDIBLE.sort(LOUDEST_FIRST);
		AUDIBLE_IDS.clear();
		ALIVE.clear();
		Map<Integer, RenderStream> swap = previousById;
		previousById = byId;
		byId = swap;
		byId.clear();
		for (RenderStream stream : AUDIBLE) {
			AUDIBLE_IDS.add(stream.sessionId);
			byId.put(stream.sessionId, stream);
			RenderStream previous = previousById.get(stream.sessionId);
			// A stream that replaced another one under the same id is left out once, so that it comes back as new
			// and is traced at once instead of waiting for the old one's slot.
			if (previous == null || previous == stream) {
				ALIVE.add(stream.sessionId);
			}
		}
		ROTA.forget(ALIVE);

		if (!occlusion.enabled()) {
			for (RenderStream stream : AUDIBLE) {
				publish(stream, 0, 0);
			}
			return;
		}

		DUE.clear();
		for (Integer id : ROTA.pick(AUDIBLE_IDS, tick)) {
			DUE.add(byId.get(id));
		}
		OcclusionTracer pathTracer = tracer;
		double rolloff = settings.rolloff();
		OcclusionPass.run(DUE, new TraceBudget(RaveCraftOptions.occlusionQuality().get().voxelsPerTick()),
				new OcclusionPass.Ops<RenderStream>() {
					@Override
					public boolean known(RenderStream stream) {
						return stream.occlusionKnown;
					}

					@Override
					public @Nullable Occlusion occlusion(RenderStream stream, TraceBudget budget) {
						return pathTracer.occlusion(ear.x, ear.y, ear.z, stream.pos.getX() + 0.5,
								stream.pos.getY() + 0.5, stream.pos.getZ() + 0.5, rolloff, range(settings, stream), budget);
					}

					@Override
					public Occlusion direct(RenderStream stream) {
						return OcclusionScheduler.direct(pathTracer, ear, stream.pos.getX() + 0.5,
								stream.pos.getY() + 0.5, stream.pos.getZ() + 0.5);
					}

					@Override
					public void publish(RenderStream stream, Occlusion occlusion) {
						OcclusionScheduler.publish(stream, occlusion.lossDb(), occlusion.highLossDb());
					}

					@Override
					public void unfinished(RenderStream stream) {
						ROTA.unfinished(stream.sessionId);
					}
				});
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
