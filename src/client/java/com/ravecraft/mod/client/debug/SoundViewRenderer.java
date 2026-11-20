package com.ravecraft.mod.client.debug;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.render.SoundViewSnapshots;
import com.ravecraft.mod.core.acoustics.debug.view.Crossing;
import com.ravecraft.mod.core.acoustics.debug.view.Detour;
import com.ravecraft.mod.core.acoustics.debug.view.LossColors;
import com.ravecraft.mod.core.acoustics.debug.view.PathView;
import com.ravecraft.mod.core.acoustics.debug.view.Ray;
import com.ravecraft.mod.core.acoustics.debug.view.RoomView;
import com.ravecraft.mod.core.acoustics.debug.view.SoundViewMode;
import com.ravecraft.mod.core.acoustics.debug.view.Vec;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the sound view with vanilla gizmos at the end of level extraction: the paths of the loudest speakers (on top
 * of the blocks, which hide exactly what is to be seen) and the room probes. Emits nothing while both layers are off.
 */
public final class SoundViewRenderer {
	private static final int MAX_PATHS = 8;
	private static final int MAX_RAYS = 48;
	private static final float DIRECT_WIDTH = 2.5f;
	private static final float LINE_WIDTH = 1.5f;
	/** Dashes: 0.4 block drawn every 0.8 block, longer on long lines so that one has at most this many. */
	private static final double DASH_PERIOD = 0.8;
	private static final int MAX_DASHES = 16;
	/** Open rays are many and long: fewer dashes keep a room outdoors to a few hundred lines. */
	private static final int MAX_OPEN_RAY_DASHES = 6;
	private static final float SLOT_ALPHA = 0.4f;
	private static final int WHITE = 0xFFFFFFFF;
	private static final double SOURCE_LABEL_HEIGHT = 0.8;
	private static final double ROOM_LABEL_HEIGHT = 1.2;
	private static final double ROW_HEIGHT = 0.25;
	private static final long FAILURE_LOG_MS = 60_000;
	private static long lastFailureLog = Long.MIN_VALUE;

	private SoundViewRenderer() {
	}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(context -> emit());
	}

	/** Never throws: an exception out of level extraction would crash the client. */
	private static void emit() {
		SoundViewMode mode = SoundViewEntries.mode();
		if (mode == SoundViewMode.NONE) {
			return;
		}
		try {
			if (mode.paths()) {
				int drawn = 0;
				for (PathView path : SoundViewSnapshots.paths()) {
					if (drawn++ >= MAX_PATHS) {
						break;
					}
					path(path);
				}
			}
			if (mode.rooms()) {
				RoomView listener = SoundViewSnapshots.listenerRoom();
				if (listener != null) {
					listenerRoom(listener);
				}
				for (int slot = 0; slot < 2; slot++) {
					RoomView room = SoundViewSnapshots.slotRoom(slot);
					if (room != null) {
						slotRoom(slot, room);
					}
				}
			}
		} catch (RuntimeException e) {
			long now = System.currentTimeMillis();
			if (lastFailureLog == Long.MIN_VALUE || now - lastFailureLog >= FAILURE_LOG_MS) {
				lastFailureLog = now;
				RaveCraft.LOGGER.error("Sound view drawing failed", e);
			}
		}
	}

	private static void path(PathView path) {
		Vec3 source = vec3(path.source());
		Vec3 listener = vec3(path.listener());
		// The direct path, cut where it enters each block it loses in, each piece the colour of the loss so far.
		Vec3 from = source;
		double cumulative = 0;
		for (Crossing crossing : path.crossings()) {
			Vec3 entry = entry(source, listener, crossing);
			Gizmos.line(from, entry, LossColors.forLoss(cumulative), DIRECT_WIDTH).setAlwaysOnTop();
			from = entry;
			cumulative = crossing.cumulativeDb();
			BlockPos pos = new BlockPos(crossing.x(), crossing.y(), crossing.z());
			Gizmos.cuboid(pos, 0.02f, GizmoStyle.stroke(LossColors.forLoss(crossing.lossDb()))).setAlwaysOnTop();
			text(crossing.name() + " −" + db(crossing.lossDb()) + " dB", Vec3.atCenterOf(pos), WHITE)
					.setAlwaysOnTop();
		}
		double last = path.directMute() ? Double.POSITIVE_INFINITY : cumulative;
		Gizmos.line(from, listener, LossColors.forLoss(last), DIRECT_WIDTH).setAlwaysOnTop();

		for (Detour detour : path.detours()) {
			int colour = LossColors.forLoss(detour.lossDb());
			List<Vec> points = detour.points();
			for (int i = 0; i + 1 < points.size(); i++) {
				dashed(vec3(points.get(i)), vec3(points.get(i + 1)), colour, LINE_WIDTH, true, MAX_DASHES);
			}
			for (int i = 1; i + 1 < points.size(); i++) {
				BlockPos opening = BlockPos.containing(vec3(points.get(i)));
				Gizmos.cuboid(opening, 0.3f, GizmoStyle.stroke(LossColors.OPENING)).setAlwaysOnTop();
				text("−" + oneDecimal(detour.lossDb()) + " dB", Vec3.atCenterOf(opening), LossColors.OPENING)
						.setAlwaysOnTop();
			}
		}

		String label = path.mute() ? I18n.get("debug.ravecraft.sound_view.source_mute", path.sessionId())
				: I18n.get("debug.ravecraft.sound_view.source", path.sessionId(), oneDecimal(path.lossDb()),
						oneDecimal(path.highLossDb()));
		if (path.truncated() > 0) {
			label += " +" + path.truncated();
		}
		text(label, source.add(0, SOURCE_LABEL_HEIGHT, 0), WHITE).setAlwaysOnTop();
	}

	/**
	 * Where the segment source → listener enters the block of {@code crossing} (slab method), or the source when it
	 * starts inside it.
	 */
	private static Vec3 entry(Vec3 source, Vec3 listener, Crossing crossing) {
		double[] from = {source.x, source.y, source.z};
		double[] delta = {listener.x - source.x, listener.y - source.y, listener.z - source.z};
		int[] min = {crossing.x(), crossing.y(), crossing.z()};
		double enter = 0;
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(delta[axis]) < 1e-12) {
				continue;
			}
			double t0 = (min[axis] - from[axis]) / delta[axis];
			double t1 = (min[axis] + 1 - from[axis]) / delta[axis];
			enter = Math.max(enter, Math.min(t0, t1));
		}
		enter = Math.clamp(enter, 0, 1);
		return source.add(delta[0] * enter, delta[1] * enter, delta[2] * enter);
	}

	private static void listenerRoom(RoomView room) {
		Vec3 origin = vec3(room.origin());
		String fluid = room.fluid();
		if (fluid != null) {
			text(I18n.get("debug.ravecraft.sound_view.fluid", fluid), origin.add(0, ROOM_LABEL_HEIGHT, 0), WHITE);
			return;
		}
		rays(room, 1f, true);
		RoomShape shape = room.shape();
		if (shape == null) {
			return;
		}
		String first = I18n.get("debug.ravecraft.sound_view.room", String.format(Locale.ROOT, "%.0f",
				shape.openness() * 100), oneDecimal(shape.meanFreePath()), twoDecimals(shape.absorption()),
				twoDecimals(shape.irregularity()));
		Echo echo = room.echo();
		String second = echo.equals(Echo.NONE)
				? I18n.get("debug.ravecraft.sound_view.reverb_no_echo", twoDecimals(room.rt60()))
				: I18n.get("debug.ravecraft.sound_view.reverb", twoDecimals(room.rt60()),
						twoDecimals(echo.delaySeconds()), twoDecimals(echo.gain()));
		text(first, origin.add(0, ROOM_LABEL_HEIGHT + ROW_HEIGHT, 0), WHITE);
		text(second, origin.add(0, ROOM_LABEL_HEIGHT, 0), WHITE);
	}

	private static void slotRoom(int slot, RoomView room) {
		rays(room, SLOT_ALPHA, false);
		text(I18n.get("debug.ravecraft.sound_view.slot", slot + 1, twoDecimals(room.rt60())),
				vec3(room.origin()).add(0, ROOM_LABEL_HEIGHT, 0), LossColors.withAlpha(WHITE, SLOT_ALPHA + 0.3f));
	}

	/** At most {@value #MAX_RAYS} rays, evenly picked, plus every ray of the echo when {@code echo} is shown. */
	private static void rays(RoomView room, float alpha, boolean echo) {
		Vec3 origin = vec3(room.origin());
		List<Ray> rays = room.rays();
		int step = Math.max(1, Math.ceilDiv(rays.size(), MAX_RAYS));
		for (int i = 0; i < rays.size(); i++) {
			Ray ray = rays.get(i);
			boolean echoRay = echo && ray.echo();
			if (i % step != 0 && !echoRay) {
				continue;
			}
			Vec3 end = vec3(ray.end());
			if (ray.open()) {
				dashed(origin, end, LossColors.withAlpha(LossColors.OPEN_RAY, alpha), LINE_WIDTH, false,
						MAX_OPEN_RAY_DASHES);
				continue;
			}
			int colour = LossColors.withAlpha(echoRay ? LossColors.ECHO : LossColors.forAbsorption(ray.absorption()),
					alpha);
			Gizmos.line(origin, end, colour, LINE_WIDTH);
			Gizmos.point(end, colour, 4f);
			if (echoRay) {
				// Just past the hit, so that the frame is the block that was hit, not the air in front of it.
				Vec3 into = end.add(end.subtract(origin).normalize().scale(0.01));
				Gizmos.cuboid(BlockPos.containing(into), 0.0f, GizmoStyle.stroke(LossColors.ECHO));
			}
		}
	}

	/** Dashes of half a period, at most {@code maxDashes} of them. */
	private static void dashed(Vec3 a, Vec3 b, int argb, float width, boolean onTop, int maxDashes) {
		double length = a.distanceTo(b);
		if (length <= 0) {
			return;
		}
		double period = Math.max(DASH_PERIOD, length / maxDashes);
		Vec3 d = b.subtract(a).scale(1 / length);
		for (double s = 0; s < length; s += period) {
			GizmoProperties dash = Gizmos.line(a.add(d.scale(s)), a.add(d.scale(Math.min(s + period / 2, length))),
					argb, width);
			if (onTop) {
				dash.setAlwaysOnTop();
			}
		}
	}

	private static GizmoProperties text(String text, Vec3 pos, int argb) {
		return Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb));
	}

	private static Vec3 vec3(Vec v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	/** A loss for a label: one decimal, or ∞ for a mute block. */
	private static String db(double lossDb) {
		return Double.isInfinite(lossDb) ? "∞" : oneDecimal(lossDb);
	}

	private static String oneDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String twoDecimals(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
