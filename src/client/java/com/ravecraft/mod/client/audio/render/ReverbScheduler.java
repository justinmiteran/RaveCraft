package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.occlusion.WorldAcousticGrid;
import com.ravecraft.mod.client.debug.SoundViewEntries;
import com.ravecraft.mod.client.options.OcclusionQuality;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.RetryCooldown;
import com.ravecraft.mod.core.acoustics.debug.view.RoomView;
import com.ravecraft.mod.core.acoustics.Vec;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoFinder;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbDebug;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import com.ravecraft.mod.core.acoustics.reverb.RoomProbe;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;
import com.ravecraft.mod.core.acoustics.reverb.SendRoute;
import com.ravecraft.mod.core.acoustics.reverb.SpaceSlots;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

	/** Voxels the last tick's probes used, and the budget they had (debug screen). */
	private static int lastVoxels;
	private static int lastBudget;

	private static final RetryCooldown TICK_FAILURE_LOG = new RetryCooldown(TICK_FAILURE_LOG_MS);
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
	/** Whether the sound view's rooms layer was on last tick. */
	private static boolean roomsShown;
	private static double listenerX;
	private static double listenerY;
	private static double listenerZ;
	private static long listenerStartTick;
	private static ReverbParams listenerParams = ReverbParams.SILENT;
	private static Echo listenerEcho = Echo.NONE;
	/** The last measure of the listener's space, for the debug screen; null until one ends. */
	private static @Nullable RoomShape listenerShape;

	// The fluid the head was last in, and its reverb, so that staying in it allocates nothing.
	private static @Nullable FluidReverb headFluid;
	private static ReverbParams headFluidParams = ReverbParams.SILENT;

	// The speaker slots S1 and S2.
	private static final RoomProbe[] SLOT_PROBES = new RoomProbe[SpaceSlots.SLOTS];
	private static final boolean[] SLOT_RUNNING = new boolean[SpaceSlots.SLOTS];
	private static final long[] SLOT_START_TICK = new long[SpaceSlots.SLOTS];
	/** Where each slot's probe started (the speaker's centre), for the sound view. */
	private static final double[][] SLOT_ORIGINS = new double[SpaceSlots.SLOTS][3];
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
			Arrays.fill(SLOT_RUNNING, false);
			long now = System.currentTimeMillis();
			if (TICK_FAILURE_LOG.canTry(now)) {
				TICK_FAILURE_LOG.failed(now);
				RaveCraft.LOGGER.error("Reverb tick failed; the spaces keep their last measure", e);
			}
		}
	}

	/** What the probes of one tick share: the settings, the budget, and whether the sound view shows the rooms. */
	private record Pass(ReverbSettings reverb, TraceBudget budget, int rays, double rayLength, boolean showRooms) {
	}

	/** Measures the spaces due this tick, within the tick's budget. */
	private static void measureTick(Minecraft minecraft) {
		tick++;
		AcousticSettings settings = AcousticSettingsClient.get();
		ReverbSettings reverb = settings.reverb();
		ClientLevel level = minecraft.level;
		WorldAcousticGrid grid = OcclusionScheduler.grid();
		boolean showRooms = level != null && SoundViewEntries.mode().rooms();
		toggleRooms(showRooms);
		if (!active(level, grid, reverb)) {
			silence();
			return;
		}
		OcclusionQuality quality = RaveCraftOptions.occlusionQuality().get();
		int rays = quality.reverbRays();
		reprobeIfChanged(grid, reverb, rays);
		lastBudget = quality.reverbVoxelsPerTick();
		Pass pass = new Pass(reverb, new TraceBudget(lastBudget), rays, reverb.rayLength(), showRooms);
		Vec3 ear = AcousticRenderer.listener();

		// The listener's space: a fluid around the head replaces the measure and silences the echo.
		AcousticMedium head = grid.headMedium(ear);
		FluidReverb fluid = head == null ? null : reverb.fluid(head.name());
		ReverbParams targetL;
		Echo echo;
		if (fluid != null) {
			inFluid(fluid, ear, showRooms);
			targetL = headFluidParams;
			echo = Echo.NONE;
		} else {
			measureListener(ear, pass);
			targetL = listenerParams;
			echo = listenerEcho;
		}

		assignSlots(settings, ear, pass);
		runSlots(pass);
		lastVoxels = pass.budget().used();
		publish(targetL, SLOT_PARAMS[0], SLOT_PARAMS[1], echo, reverb.echo());
	}

	/** Follows the sound view's rooms layer: cleared when off, every space measured at once when it turns on. */
	private static void toggleRooms(boolean showRooms) {
		if (!showRooms) {
			SoundViewSnapshots.clearRooms();
		} else if (!roomsShown) {
			// The layer just turned on: measure every space now rather than when each is next due (up to 5 s).
			listenerStarted = false;
			for (int i = 0; i < SpaceSlots.SLOTS; i++) {
				if (!SLOT_RUNNING[i]) {
					SLOT_START_TICK[i] = tick - SPEAKER_PERIOD;
				}
			}
		}
		roomsShown = showRooms;
	}

	/** Whether there is anything to measure: a world, its grid, and reverb on. */
	private static boolean active(@Nullable ClientLevel level, @Nullable WorldAcousticGrid grid, ReverbSettings reverb) {
		if (level == null || grid == null || grid.level() != level) {
			return false;
		}
		return reverb.enabled() && RaveCraftOptions.acousticEffects().get();
	}

	/**
	 * A new world, new settings or a new quality: every space is measured again, keeping its old value until then.
	 */
	private static void reprobeIfChanged(WorldAcousticGrid grid, ReverbSettings reverb, int rays) {
		if (grid == probedGrid && reverb == measured && rays == measuredRays) {
			return;
		}
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

	/** The head is in {@code fluid}: its reverb and chorus replace the listener's space. */
	private static void inFluid(FluidReverb fluid, Vec3 ear, boolean showRooms) {
		if (fluid != headFluid) {
			headFluid = fluid;
			headFluidParams = ReverbParams.of(fluid);
		}
		if (showRooms) {
			SoundViewSnapshots.listenerRoom(RoomView.fluid(new Vec(ear.x, ear.y, ear.z), fluid.medium()));
		}
		AcousticRenderer.headChorus(fluid);
		// Out of the fluid, the space around is measured again at once.
		listenerRunning = false;
		listenerStarted = false;
	}

	/** Out of any fluid: measures the listener's space when it is due, going on with a measure in progress. */
	private static void measureListener(Vec3 ear, Pass pass) {
		if (headFluid != null && pass.showRooms()) {
			// The fluid's view goes; the room shows again once the new measure ends.
			SoundViewSnapshots.listenerRoom(null);
		}
		headFluid = null;
		AcousticRenderer.headChorus(null);
		RoomProbe probe = Objects.requireNonNull(listenerProbe);
		if (!listenerRunning && listenerDue(ear)) {
			listenerX = ear.x;
			listenerY = ear.y;
			listenerZ = ear.z;
			listenerStartTick = tick;
			listenerStarted = true;
			probe.start(ear.x, ear.y, ear.z, pass.rays(), pass.rayLength());
			listenerRunning = true;
		}
		if (listenerRunning && probe.run(pass.budget())) {
			endListener(probe, pass);
		}
	}

	/** The listener's measure ended: its shape, reverb and echo. */
	private static void endListener(RoomProbe probe, Pass pass) {
		ReverbSettings reverb = pass.reverb();
		listenerRunning = false;
		listenerShape = RoomShape.of(probe.hits(), pass.rayLength());
		listenerParams = ReverbParams.of(listenerShape, reverb);
		listenerEcho = EchoFinder.find(probe.hits(), reverb.echo());
		if (pass.showRooms()) {
			SoundViewSnapshots.listenerRoom(RoomView.of(new Vec(listenerX, listenerY, listenerZ), probe.hits(), reverb));
		}
	}

	/** The two loudest audible speakers outside the listener's space take S1 and S2. */
	private static void assignSlots(AcousticSettings settings, Vec3 ear, Pass pass) {
		OUTSIDE.clear();
		BY_ID.clear();
		double sameSpaceLossDb = pass.reverb().sameSpaceLossDb();
		for (RenderStream stream : AcousticRenderer.streams()) {
			if (OcclusionScheduler.isAudible(settings, stream, ear)) {
				sortSpace(stream, sameSpaceLossDb);
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
			updateSlot(i, assigned[i], pass);
		}
		slotSessions = assigned;
	}

	/** Whether {@code stream} is in the listener's space, with hysteresis; one outside is a candidate for a slot. */
	private static void sortSpace(RenderStream stream, double sameSpaceLossDb) {
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

	/**
	 * Slot {@code i} now holds {@code session}, or nothing. A speaker entering the slot is measured at once, even if
	 * the previous one's probe was running; the slot keeps its last value until the probe ends.
	 */
	private static void updateSlot(int i, @Nullable Integer session, Pass pass) {
		if (session == null) {
			SLOT_RUNNING[i] = false;
			SLOT_PARAMS[i] = ReverbParams.SILENT;
			if (pass.showRooms()) {
				SoundViewSnapshots.slotRoom(i, null);
			}
			return;
		}
		boolean entered = !Objects.equals(session, slotSessions[i]);
		if (entered || !SLOT_RUNNING[i] && tick - SLOT_START_TICK[i] >= SPEAKER_PERIOD) {
			RenderStream stream = Objects.requireNonNull(BY_ID.get(session));
			double[] origin = SLOT_ORIGINS[i];
			origin[0] = stream.x;
			origin[1] = stream.y;
			origin[2] = stream.z;
			SLOT_PROBES[i].start(origin[0], origin[1], origin[2], pass.rays(), pass.rayLength());
			SLOT_START_TICK[i] = tick;
			SLOT_RUNNING[i] = true;
		}
	}

	/** Runs the slots' probes within what is left of the budget, and takes the measures that end. */
	private static void runSlots(Pass pass) {
		for (int i = 0; i < SpaceSlots.SLOTS; i++) {
			if (SLOT_RUNNING[i] && SLOT_PROBES[i].run(pass.budget())) {
				endSlot(i, pass);
			}
		}
	}

	private static void endSlot(int i, Pass pass) {
		ReverbSettings reverb = pass.reverb();
		SLOT_RUNNING[i] = false;
		SLOT_PARAMS[i] = ReverbParams.of(RoomShape.of(SLOT_PROBES[i].hits(), pass.rayLength()), reverb);
		if (pass.showRooms()) {
			double[] origin = SLOT_ORIGINS[i];
			SoundViewSnapshots.slotRoom(i, RoomView.of(new Vec(origin[0], origin[1], origin[2]),
					SLOT_PROBES[i].hits(), reverb));
		}
	}

	/** Voxels the last tick's probes used (debug screen). */
	static int lastVoxels() {
		return lastVoxels;
	}

	/** The voxel budget of the last tick (debug screen). */
	static int lastBudget() {
		return lastBudget;
	}

	/** Room probes still running: the listener's and the speaker slots' (debug screen). */
	static int probesRunning() {
		int running = listenerRunning ? 1 : 0;
		for (boolean slot : SLOT_RUNNING) {
			running += slot ? 1 : 0;
		}
		return running;
	}

	/**
	 * The debug screen lines for the listener's space. Client thread only.
	 *
	 * @return the lines about the listener's space
	 */
	public static List<String> debugLines() {
		FluidReverb fluid = headFluid;
		if (fluid == null) {
			return ReverbDebug.lines(probedGrid != null, listenerShape, listenerParams, listenerEcho, null);
		}
		return ReverbDebug.lines(probedGrid != null, listenerShape, headFluidParams, Echo.NONE, fluid.medium());
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
		lastVoxels = 0;
		SoundViewSnapshots.clearRooms();
		probedGrid = null;
		measured = null;
		listenerProbe = null;
		listenerRunning = false;
		listenerStarted = false;
		listenerParams = ReverbParams.SILENT;
		listenerEcho = Echo.NONE;
		listenerShape = null;
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

	private static boolean spacesUnchanged(ReverbParams l, ReverbParams s1, ReverbParams s2) {
		return l.equals(publishedL) && s1.equals(publishedS1) && s2.equals(publishedS2);
	}

	/** Hands the targets to the audio thread, as one snapshot, only when one of them changed. */
	private static void publish(ReverbParams l, ReverbParams s1, ReverbParams s2, Echo echo,
			EchoSettings echoSettings) {
		if (spacesUnchanged(l, s1, s2) && echo.equals(publishedEcho) && echoSettings.equals(publishedEchoSettings)) {
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
