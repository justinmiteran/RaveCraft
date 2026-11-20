package com.ravecraft.mod.client.debug;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.render.SoundViewSnapshots;
import com.ravecraft.mod.core.acoustics.debug.view.Crossing;
import com.ravecraft.mod.core.acoustics.debug.view.Detour;
import com.ravecraft.mod.core.acoustics.debug.view.EyeAnchor;
import com.ravecraft.mod.core.acoustics.debug.view.LossColors;
import com.ravecraft.mod.core.acoustics.debug.view.PathView;
import com.ravecraft.mod.core.acoustics.debug.view.Ray;
import com.ravecraft.mod.core.acoustics.debug.view.RoomView;
import com.ravecraft.mod.core.acoustics.debug.view.SoundViewMode;
import com.ravecraft.mod.core.acoustics.debug.view.Vec;
import com.ravecraft.mod.core.acoustics.debug.view.Waypoint;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the sound view with vanilla gizmos at the end of level extraction: the paths of the loudest speakers (on top
 * of the blocks, which hide exactly what is to be seen) and the room probes. The listener's room is summed up on the
 * HUD, since it is measured from the player's own head. Emits nothing while both layers are off.
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
	private static final double SOURCE_LABEL_HEIGHT = 1.3;
	private static final double ROOM_LABEL_HEIGHT = 1.2;
	/** Half the side of the cube marking a detour's waypoint. */
	private static final double WAYPOINT_HALF = 0.15;
	private static final int HUD_TOP = 4;
	private static final int HUD_PADDING = 2;
	private static final int HUD_BACKGROUND = 0x90000000;
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(RaveCraft.MOD_ID, "sound_view");
	private static final long FAILURE_LOG_MS = 60_000;
	private static long lastFailureLog = Long.MIN_VALUE;

	private SoundViewRenderer() {
	}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(context -> emit());
		HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> hud(graphics));
	}

	/** Never throws: an exception out of level extraction would crash the client. */
	private static void emit() {
		SoundViewMode mode = SoundViewEntries.mode();
		if (mode == SoundViewMode.NONE) {
			return;
		}
		try {
			Vec camera = camera();
			if (mode.paths()) {
				int drawn = 0;
				for (PathView path : SoundViewSnapshots.paths()) {
					if (drawn++ >= MAX_PATHS) {
						break;
					}
					path(path, camera);
				}
			}
			if (mode.rooms()) {
				RoomView listener = SoundViewSnapshots.listenerRoom();
				if (listener != null && listener.fluid() == null) {
					rays(listener, 1f, true, false, camera);
				}
				for (int slot = 0; slot < 2; slot++) {
					RoomView room = SoundViewSnapshots.slotRoom(slot);
					if (room != null) {
						slotRoom(slot, room, camera);
					}
				}
			}
		} catch (RuntimeException e) {
			logFailure(e);
		}
	}

	private static Vec camera() {
		return vec(Minecraft.getInstance().gameRenderer.mainCamera().position());
	}

	private static void path(PathView path, Vec camera) {
		Vec3 source = vec3(path.source());
		Vec3 listener = vec3(EyeAnchor.of(path.listener(), camera));
		// The direct path, cut where it enters each block it loses in, each piece the colour of the loss so far.
		Vec3 from = source;
		double cumulative = 0;
		for (Crossing crossing : path.crossings()) {
			Vec3 entry = entry(source, listener, crossing);
			line(from, entry, LossColors.forLoss(cumulative), DIRECT_WIDTH, true);
			from = entry;
			cumulative = crossing.cumulativeDb();
			BlockPos pos = new BlockPos(crossing.x(), crossing.y(), crossing.z());
			Gizmos.cuboid(pos, 0.02f, GizmoStyle.stroke(LossColors.forLoss(crossing.lossDb()))).setAlwaysOnTop();
			text(crossing.name() + " −" + db(crossing.lossDb()) + " dB", Vec3.atCenterOf(pos), WHITE)
					.setAlwaysOnTop();
		}
		double last = path.directMute() ? Double.POSITIVE_INFINITY : cumulative;
		line(from, listener, LossColors.forLoss(last), DIRECT_WIDTH, true);

		for (Detour detour : path.detours()) {
			int colour = LossColors.forLoss(detour.lossDb());
			List<Vec> points = detour.points();
			for (int i = 0; i + 1 < points.size(); i++) {
				Vec end = points.get(i + 1);
				Vec3 to = vec3(i + 2 == points.size() ? EyeAnchor.of(end, camera) : end);
				dashed(vec3(points.get(i)), to, colour, LINE_WIDTH, true, MAX_DASHES);
			}
		}
		// The points the detours bend at (by a door, round a corner), each once, with the loss of every detour
		// through it: detours often share the waypoint by a door.
		for (Waypoint waypoint : Waypoint.of(path.detours())) {
			Vec3 at = vec3(waypoint.point());
			Gizmos.cuboid(new AABB(at.subtract(WAYPOINT_HALF, WAYPOINT_HALF, WAYPOINT_HALF),
					at.add(WAYPOINT_HALF, WAYPOINT_HALF, WAYPOINT_HALF)), GizmoStyle.stroke(LossColors.OPENING))
					.setAlwaysOnTop();
			StringBuilder label = new StringBuilder();
			for (double loss : waypoint.lossesDb()) {
				label.append(label.isEmpty() ? "−" : " / −").append(oneDecimal(loss));
			}
			text(label.append(" dB").toString(), at.add(0, 0.35, 0), LossColors.OPENING).setAlwaysOnTop();
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

	/** The listener's room on the HUD, at the top of the screen. */
	private static void hud(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!SoundViewEntries.mode().rooms() || minecraft.level == null) {
			return;
		}
		try {
			Font font = minecraft.font;
			int centre = graphics.guiWidth() / 2;
			int y = HUD_TOP;
			for (String line : hudLines()) {
				int width = font.width(line);
				graphics.fill(centre - width / 2 - HUD_PADDING, y - 1, centre + width / 2 + HUD_PADDING,
						y + font.lineHeight, HUD_BACKGROUND);
				graphics.text(font, line, centre - width / 2, y, WHITE, false);
				y += font.lineHeight + 1;
			}
		} catch (RuntimeException e) {
			logFailure(e);
		}
	}

	private static List<String> hudLines() {
		List<String> lines = new ArrayList<>(3);
		RoomView room = SoundViewSnapshots.listenerRoom();
		RoomShape shape = room == null ? null : room.shape();
		String fluid = room == null ? null : room.fluid();
		if (fluid != null) {
			lines.add(I18n.get("debug.ravecraft.sound_view.fluid", fluid));
		} else if (room == null || shape == null) {
			lines.add(I18n.get("debug.ravecraft.sound_view.measuring"));
		} else {
			lines.add(I18n.get("debug.ravecraft.sound_view.room", String.format(Locale.ROOT, "%.0f",
					shape.openness() * 100), oneDecimal(shape.meanFreePath()), twoDecimals(shape.absorption()),
					twoDecimals(shape.irregularity())));
			Echo echo = room.echo();
			lines.add(echo.equals(Echo.NONE)
					? I18n.get("debug.ravecraft.sound_view.reverb_no_echo", twoDecimals(room.rt60()))
					: I18n.get("debug.ravecraft.sound_view.reverb", twoDecimals(room.rt60()),
							twoDecimals(echo.delaySeconds()), twoDecimals(echo.gain())));
		}
		int slots = 0;
		for (int slot = 0; slot < 2; slot++) {
			if (SoundViewSnapshots.slotRoom(slot) != null) {
				slots++;
			}
		}
		lines.add(I18n.get("debug.ravecraft.sound_view.slots", slots));
		return lines;
	}

	/** A speaker's room is another room than the player's: drawn on top of the walls that hide it. */
	private static void slotRoom(int slot, RoomView room, Vec camera) {
		rays(room, SLOT_ALPHA, false, true, camera);
		text(I18n.get("debug.ravecraft.sound_view.slot", slot + 1, twoDecimals(room.rt60())),
				vec3(room.origin()).add(0, ROOM_LABEL_HEIGHT, 0), LossColors.withAlpha(WHITE, SLOT_ALPHA + 0.3f))
				.setAlwaysOnTop();
	}

	/**
	 * At most {@value #MAX_RAYS} rays, evenly picked, plus every ray of the echo when {@code echo} is shown. Drawn from
	 * below the eye when the camera is at the origin, else they would be seen end-on.
	 */
	private static void rays(RoomView room, float alpha, boolean echo, boolean onTop, Vec camera) {
		Vec3 measured = vec3(room.origin());
		Vec3 origin = vec3(EyeAnchor.of(room.origin(), camera));
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
				dashed(origin, end, LossColors.withAlpha(LossColors.OPEN_RAY, alpha), LINE_WIDTH, onTop,
						MAX_OPEN_RAY_DASHES);
				continue;
			}
			int colour = LossColors.withAlpha(echoRay ? LossColors.ECHO : LossColors.forAbsorption(ray.absorption()),
					alpha);
			line(origin, end, colour, LINE_WIDTH, onTop);
			GizmoProperties point = Gizmos.point(end, colour, 4f);
			if (onTop) {
				point.setAlwaysOnTop();
			}
			if (echoRay) {
				// Just past the hit, so that the frame is the block that was hit, not the air in front of it.
				Vec3 into = end.add(end.subtract(measured).normalize().scale(0.01));
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
			line(a.add(d.scale(s)), a.add(d.scale(Math.min(s + period / 2, length))), argb, width, onTop);
		}
	}

	private static void line(Vec3 a, Vec3 b, int argb, float width, boolean onTop) {
		GizmoProperties line = Gizmos.line(a, b, argb, width);
		if (onTop) {
			line.setAlwaysOnTop();
		}
	}

	private static Vec vec(Vec3 v) {
		return new Vec(v.x, v.y, v.z);
	}

	private static GizmoProperties text(String text, Vec3 pos, int argb) {
		return Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb));
	}

	private static void logFailure(RuntimeException e) {
		long now = System.currentTimeMillis();
		if (lastFailureLog == Long.MIN_VALUE || now - lastFailureLog >= FAILURE_LOG_MS) {
			lastFailureLog = now;
			RaveCraft.LOGGER.error("Sound view drawing failed", e);
		}
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
