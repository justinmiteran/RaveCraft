package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.occlusion.WorldAcousticGrid;
import com.ravecraft.mod.client.options.OcclusionQuality;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoFinder;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import com.ravecraft.mod.core.acoustics.reverb.RoomProbe;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;
import com.ravecraft.mod.core.acoustics.reverb.SendRoute;
import com.ravecraft.mod.core.acoustics.reverb.SpaceSlots;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Measures the listener's space and the spaces of the two loudest speakers outside it with room probes, under a
 * voxel budget per tick of its own (the player's occlusion quality), and publishes the reverb targets, the echo, the
 * head's chorus and each stream's reverb slot to the audio thread. A probe in progress keeps the last published
 * value of its slot until it ends. Runs after {@link OcclusionScheduler}, whose grid and occlusion it reads. Client
 * thread only.
 */
public final class ReverbScheduler {
	/** The listener's space is measured again after this many ticks even if the ear did not move. */
	private static final long LISTENER_PERIOD = 40;
	/** ... or once the ear moved this far (blocks) from where the last measure started. */
	private static final double LISTENER_MOVE = 1;
	/** A speaker's space is measured when it enters a slot, then every this many ticks. */
	private static final long SPEAKER_PERIOD = 100;
	/** Logs an unexpected failure of the whole tick at most once a minute. */
	private static final long TICK_FAILURE_LOG_MS = 60_000;

	/** The speaker slots S1 and S2 sit at these reverb slot indexes, after the listener's. */
	private static final int FIRST_SPEAKER_SLOT = 1;

	private static long lastTickFailureLog = Long.MIN_VALUE;
	private static long tick;

	private static final Map<Integer, Double> OUTSIDE = new HashMap<>();
	private static final Map<Integer, RenderStream> BY_ID = new HashMap<>();
	/** Whether each audible session was in the listener's space last tick (hysteresis memory). */
	private static final Map<Integer, Boolean> WAS_SAME = new HashMap<>();
	private static final Map<Integer, Boolean> SAME_NOW = new HashMap<>();

	/** The grid the probes were built on, and the settings their results were measured with. */
	private static @Nullable WorldAcousticGrid probedGrid;
	private static @Nullable ReverbSettings measured;
	private static int measuredRays;

	// The listener's space.
	private static @Nullable RoomProbe listenerProbe;
	private static boolean listenerRunning;
	/** Whether a listener measure was ever started on this grid: if not, one starts at once. */
	private static boolean listenerStarted;
	private static double listenerX;
	private static double listenerY;
	private static double listenerZ;
	private static long listenerStartTick;
	private static ReverbParams listenerParams = ReverbParams.SILENT;
	private static Echo listenerEcho = Echo.NONE;

	// The fluid the head was last in, and its reverb, so that staying in it allocates nothing.
	private static @Nullable FluidReverb headFluid;
	private static ReverbParams headFluidParams = ReverbParams.SILENT;

	// The speaker slots S1 and S2.
	private static final RoomProbe[] SLOT_PROBES = new RoomProbe[SpaceSlots.SLOTS];
	private static final boolean[] SLOT_RUNNING = new boolean[SpaceSlots.SLOTS];
	private static final long[] SLOT_START_TICK = new long[SpaceSlots.SLOTS];
	private static final ReverbParams[] SLOT_PARAMS = {ReverbParams.SILENT, ReverbParams.SILENT};
	private static Integer[] slotSessions = new Integer[SpaceSlots.SLOTS];

	// What the audio thread was last given, to publish only on change.
	private static ReverbParams publishedL = ReverbParams.SILENT;
	private static ReverbParams publishedS1 = ReverbParams.SILENT;
	private static ReverbParams publishedS2 = ReverbParams.SILENT;
	private static Echo publishedEcho = Echo.NONE;
	private static EchoSettings publishedEchoSettings = EchoSettings.DEFAULT;

	private ReverbScheduler() {
	}

	/** Registers the tick, after {@link OcclusionScheduler#register()} so that the grid and occlusion are fresh. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ReverbScheduler::tick);
	}

	/** Never throws: an exception out of END_CLIENT_TICK would crash the client. */
	private static void tick(Minecraft minecraft) {
		try {
			measureTick(minecraft);
		} catch (RuntimeException e) {
			// A probe that failed half-way starts again from scratch at its next turn; the published values stay.
			listenerRunning = false;
			for (int i = 0; i < SpaceSlots.SLOTS; i++) {
				SLOT_RUNNING[i] = false;
			}
			long now = System.currentTimeMillis();
			if (lastTickFailureLog == Long.MIN_VALUE || now - lastTickFailureLog >= TICK_FAILURE_LOG_MS) {
				lastTickFailureLog = now;
				RaveCraft.LOGGER.error("Reverb tick failed; the spaces keep their last measure", e);
			}
		}
	}

	private static void measureTick(Minecraft minecraft) {
		tick++;
		AcousticSettings settings = AcousticSettingsClient.get();
		ReverbSettings reverb = settings.reverb();
		ClientLevel level = minecraft.level;
		WorldAcousticGrid grid = OcclusionScheduler.grid();
		if (level == null || grid == null || grid.level() != level || !reverb.enabled()
				|| !RaveCraftOptions.acousticEffects().get()) {
			silence();
			return;
		}

		OcclusionQuality quality = RaveCraftOptions.occlusionQuality().get();
		int rays = quality.reverbRays();
		double rayLength = reverb.rayLength();
		if (grid != probedGrid || reverb != measured || rays != measuredRays) {
			// A new world, new settings or a new quality: every space is measured again, keeping its old value
			// until then.
			probedGrid = grid;
			measured = reverb;
			measuredRays = rays;
			listenerProbe = new RoomProbe(grid);
			listenerRunning = false;
			listenerStarted = false;
			for (int i = 0; i < SpaceSlots.SLOTS; i++) {
				SLOT_PROBES[i] = new RoomProbe(grid);
				SLOT_RUNNING[i] = false;
				slotSessions[i] = null;
			}
		}
		TraceBudget budget = new TraceBudget(quality.reverbVoxelsPerTick());
		Vec3 ear = AcousticRenderer.listener();

		// The listener's space: a fluid around the head replaces the measure and silences the echo.
		ReverbParams targetL;
		Echo echo;
		AcousticMedium head = grid.headMedium(ear);
		FluidReverb fluid = head == null ? null : reverb.fluid(head.name());
		if (fluid != null) {
			if (fluid != headFluid) {
				headFluid = fluid;
				headFluidParams = ReverbParams.of(fluid);
			}
			targetL = headFluidParams;
			echo = Echo.NONE;
			AcousticRenderer.headChorus(fluid);
			// Out of the fluid, the space around is measured again at once.
			listenerRunning = false;
			listenerStarted = false;
		} else {
			headFluid = null;
			AcousticRenderer.headChorus(null);
			RoomProbe probe = Objects.requireNonNull(listenerProbe);
			if (!listenerRunning && listenerDue(ear)) {
				listenerX = ear.x;
				listenerY = ear.y;
				listenerZ = ear.z;
				listenerStartTick = tick;
				listenerStarted = true;
				probe.start(ear.x, ear.y, ear.z, rays, rayLength);
				listenerRunning = true;
			}
			if (listenerRunning && probe.run(budget)) {
				listenerRunning = false;
				listenerParams = ReverbParams.of(RoomShape.of(probe.hits(), rayLength), reverb);
				listenerEcho = EchoFinder.find(probe.hits(), reverb.echo());
			}
			targetL = listenerParams;
			echo = listenerEcho;
		}

		// The two loudest audible speakers outside the listener's space take S1 and S2.
		OUTSIDE.clear();
		BY_ID.clear();
		double sameSpaceLossDb = reverb.sameSpaceLossDb();
		for (RenderStream stream : AcousticRenderer.streams()) {
			if (OcclusionScheduler.isAudible(settings, stream, ear)) {
				Boolean was = WAS_SAME.get(stream.sessionId);
				boolean same = was == null
						? SpaceSlots.sameSpace(stream.occlusionKnown, stream.occlusionLossDb, sameSpaceLossDb)
						: SpaceSlots.sameSpace(stream.occlusionKnown, stream.occlusionLossDb, sameSpaceLossDb, was);
				SAME_NOW.put(stream.sessionId, same);
				if (!same) {
					OUTSIDE.put(stream.sessionId, stream.heardGain);
					BY_ID.put(stream.sessionId, stream);
				}
			}
		}
		WAS_SAME.clear();
		WAS_SAME.putAll(SAME_NOW);
		SAME_NOW.clear();
		Integer[] assigned = SpaceSlots.assign(OUTSIDE, slotSessions);
		for (RenderStream stream : AcousticRenderer.streams()) {
			stream.reverbSlot = slotOf(assigned, stream.sessionId);
		}
		for (int i = 0; i < SpaceSlots.SLOTS; i++) {
			Integer session = assigned[i];
			boolean entered = !Objects.equals(session, slotSessions[i]);
			if (session == null) {
				SLOT_RUNNING[i] = false;
				SLOT_PARAMS[i] = ReverbParams.SILENT;
				continue;
			}
			// A speaker entering the slot is measured at once, even if the previous one's probe was running; the
			// slot keeps its last value until the probe ends.
			if (entered || (!SLOT_RUNNING[i] && tick - SLOT_START_TICK[i] >= SPEAKER_PERIOD)) {
				RenderStream stream = Objects.requireNonNull(BY_ID.get(session));
				SLOT_PROBES[i].start(stream.pos.getX() + 0.5, stream.pos.getY() + 0.5, stream.pos.getZ() + 0.5, rays,
						rayLength);
				SLOT_START_TICK[i] = tick;
				SLOT_RUNNING[i] = true;
			}
		}
		slotSessions = assigned;
		for (int i = 0; i < SpaceSlots.SLOTS; i++) {
			if (SLOT_RUNNING[i] && SLOT_PROBES[i].run(budget)) {
				SLOT_RUNNING[i] = false;
				SLOT_PARAMS[i] = ReverbParams.of(RoomShape.of(SLOT_PROBES[i].hits(), rayLength), reverb);
			}
		}

		publish(targetL, SLOT_PARAMS[0], SLOT_PARAMS[1], echo, reverb.echo());
	}

	/** Whether the listener's space is due for a new measure: never measured, moved 1 block, or 2 s old. */
	private static boolean listenerDue(Vec3 ear) {
		if (!listenerStarted || tick - listenerStartTick >= LISTENER_PERIOD) {
			return true;
		}
		double dx = ear.x - listenerX;
		double dy = ear.y - listenerY;
		double dz = ear.z - listenerZ;
		return dx * dx + dy * dy + dz * dz >= LISTENER_MOVE * LISTENER_MOVE;
	}

	/** The reverb slot a session sends to: its speaker slot if it holds one, else the listener's. */
	private static int slotOf(Integer[] assigned, int sessionId) {
		for (int i = 0; i < assigned.length; i++) {
			Integer session = assigned[i];
			if (session != null && session == sessionId) {
				return FIRST_SPEAKER_SLOT + i;
			}
		}
		return SendRoute.LISTENER;
	}

	/** No world, no grid or reverb off: every space silent, no echo, no chorus, every stream on the listener slot. */
	private static void silence() {
		probedGrid = null;
		measured = null;
		listenerProbe = null;
		listenerRunning = false;
		listenerStarted = false;
		listenerParams = ReverbParams.SILENT;
		listenerEcho = Echo.NONE;
		headFluid = null;
		for (int i = 0; i < SpaceSlots.SLOTS; i++) {
			SLOT_PROBES[i] = null;
			SLOT_RUNNING[i] = false;
			SLOT_PARAMS[i] = ReverbParams.SILENT;
			slotSessions[i] = null;
		}
		OUTSIDE.clear();
		BY_ID.clear();
		AcousticRenderer.headChorus(null);
		for (RenderStream stream : AcousticRenderer.streams()) {
			stream.reverbSlot = SendRoute.LISTENER;
		}
		publish(ReverbParams.SILENT, ReverbParams.SILENT, ReverbParams.SILENT, Echo.NONE, publishedEchoSettings);
	}

	/** Hands the targets to the audio thread, as one snapshot, only when one of them changed. */
	private static void publish(ReverbParams l, ReverbParams s1, ReverbParams s2, Echo echo,
			EchoSettings echoSettings) {
		if (l.equals(publishedL) && s1.equals(publishedS1) && s2.equals(publishedS2) && echo.equals(publishedEcho)
				&& echoSettings.equals(publishedEchoSettings)) {
			return;
		}
		publishedL = l;
		publishedS1 = s1;
		publishedS2 = s2;
		publishedEcho = echo;
		publishedEchoSettings = echoSettings;
		AcousticRenderer.reverbTargets(l, s1, s2, echo, echoSettings);
	}
}
