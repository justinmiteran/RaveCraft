package com.ravecraft.mod.core.acoustics.debug.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoFinder;
import com.ravecraft.mod.core.acoustics.reverb.RayDirections;
import com.ravecraft.mod.core.acoustics.reverb.RayHit;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;

/**
 * What the sound view shows of a room probe: its rays (open ones end at the ray length), the room shape and RT60 it
 * gave, and its echo. A probe from inside a fluid has no rays: {@code fluid} names the fluid instead.
 */
public record RoomView(Vec origin, List<Ray> rays, @Nullable RoomShape shape, double rt60, Echo echo,
		@Nullable String fluid) {
	public RoomView {
		rays = List.copyOf(rays);
	}

	/** The view of a probe from {@code origin} whose ray i went along {@code RayDirections.sphere(hits.size())[i]}. */
	public static RoomView of(Vec origin, List<RayHit> hits, ReverbSettings settings) {
		double[][] directions = RayDirections.sphere(hits.size());
		Set<Integer> echo = new HashSet<>(EchoFinder.group(hits, settings.echo()));
		List<Ray> rays = new ArrayList<>(hits.size());
		for (int i = 0; i < hits.size(); i++) {
			RayHit hit = hits.get(i);
			double length = hit.open() ? settings.rayLength() : hit.distance();
			rays.add(new Ray(origin.along(directions[i], length), hit.open(), hit.absorption(), echo.contains(i)));
		}
		RoomShape shape = RoomShape.of(hits, settings.rayLength());
		return new RoomView(origin, rays, shape, shape.rt60(settings.maxDecaySeconds()),
				EchoFinder.find(hits, settings.echo()), null);
	}

	/** The view of a listener whose head is in {@code fluid}: no rays, no shape, no echo. */
	public static RoomView fluid(Vec origin, String fluid) {
		return new RoomView(origin, List.of(), null, 0, Echo.NONE, fluid);
	}
}
