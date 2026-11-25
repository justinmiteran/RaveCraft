# Speaker Radiation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-written speaker cone with a radiation pattern computed from each speaker's cabinet and drivers, heard per band (bass, mids, highs).

**Architecture:** Pure-Java package `core.acoustics.radiation` computes one pattern per `SpeakerKind` at client start. Each audio step, `SpeakerAxis` turns the listener's position into `BandGains`; the loudest band goes into the source gain and the differences into a per-stream `RadiationFilter`. `Directivity` disappears.

**Tech Stack:** Java 25, JUnit 5, Minecraft 26.3 (Mojang names), Fabric.

**Spec:** [2026-11-25-speaker-radiation-design.md](../specs/2026-11-25-speaker-radiation-design.md)

## Global Constraints

- Every command runs from the worktree, with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`.
- No `net.minecraft.client` import in `src/main`; `core` has no Minecraft import.
- Code rules: cognitive ≤ 15, cyclomatic ≤ 10, at most one nested `for`, named constants, Javadoc on classes, public methods (`@param`/`@return`) and methods over 10 lines.
- English code, comments and commits (imperative). No Claude attribution. No push.
- Speed of sound 343 m/s. Bands 63–200, 500–2000, 4000–12000 Hz, 9 geometric frequencies, bounds included.
- Smoothing: 7 points, 10° apart. Grid: every 10° in azimuth and elevation. Floor: −48 dB. Shelf corners: 315 Hz and 2800 Hz, Q 0.707.
- Azimuth is positive to the speaker's right, elevation positive upward; 0/0 is the axis. Right of a horizontal axis `(x, z)` is `(−z, x)`.

## Review Focus

- A stream's first block: it must start with the right timbre, not glide from flat (Task 3, `firstBlockTakesTheGainsAtOnce`).
- The ear exactly at the speaker, or right above or below it: finite gains, flat at distance 0 (Task 4, `earAtTheSpeakerHearsItWhole`, `straightAboveIsFinite`).
- Running around a speaker with loud music: the gains change on every block, with no jump and no clipping (Task 3, `fastChangesStayBoundedWithoutJumps`).
- A band louder off axis than on axis (portable mids, +1.1 dB): the filter never goes above the original (Task 2, `relativeNeverExceedsOne`).
- A broken pattern calculation must not stop the client or the music: speakers play without direction (Task 5, logged by `SpeakerRadiation`; checked by reading, no client test exists).

## File Structure

| File | Role |
|---|---|
| `core/acoustics/radiation/PistonOnSphere.java` | Far field of one piston (§ 4.1) |
| `core/acoustics/radiation/Driver.java`, `Cabinet.java`, `Cabinets.java` | Data (§ 3) |
| `core/acoustics/radiation/BandGains.java` | Three linear gains |
| `core/acoustics/radiation/RadiationPattern.java` | The grid (§ 4.2) and its interpolation |
| `core/acoustics/radiation/RadiationCalculator.java` | Band power of a cabinet in one direction, used to fill the grid |
| `core/acoustics/radiation/RadiationFilter.java` | PCM filter (§ 5.1) |
| `core/acoustics/radiation/RadiationSummary.java` | Numbers of the tooltip (§ 5.4) |
| `core/acoustics/Shelf.java` | RBJ shelf, shared by `Equalizer` and `RadiationFilter` |
| `client/audio/render/SpeakerRadiation.java` | The patterns, computed once off the render thread |

Paths below are relative to `src/main/java/com/ravecraft/mod/` (`core/…`), `src/client/java/com/ravecraft/mod/` (`client/…`) and `src/test/java/com/ravecraft/mod/`.

---

### Task 1: Piston on a sphere

**Files:**
- Create: `core/acoustics/radiation/PistonOnSphere.java`
- Test: `core/acoustics/radiation/PistonOnSphereTest.java`

**Interfaces:**
- Produces: `PistonOnSphere(double pistonRadius, double sphereRadius, double frequency)`; `double real(double cosAngle)`, `double imaginary(double cosAngle)`, `double magnitude(double cosAngle)`; `static final double SPEED_OF_SOUND = 343`.

- [ ] **Step 1: Write the failing test**

```java
class PistonOnSphereTest {
	@Test
	void isOneOnAxis() {
		assertEquals(1, new PistonOnSphere(0.13, 0.48, 1000).magnitude(1), 1e-9);
	}

	@Test
	void bassGoesEverywhere() {
		PistonOnSphere bass = new PistonOnSphere(0.05, 0.1, 20);
		for (double cos = -1; cos <= 1; cos += 0.1) {
			assertEquals(0, Decibels.fromAmplitude(bass.magnitude(cos)), 0.2);
		}
	}

	@Test
	void higherFrequenciesAreMoreDirected() {
		double previous = 1;
		for (double hz : new double[] {100, 500, 2000, 8000}) {
			double side = new PistonOnSphere(0.13, 0.48, hz).magnitude(0);
			assertTrue(side < previous, hz + " Hz");
			previous = side;
		}
	}

	@ParameterizedTest
	@ValueSource(doubles = {0.05, 0.1, 0.5, 1, 2})
	void isFiniteOverTheWholeRange(double radius) {
		for (double hz = 20; hz <= 20_000; hz *= 1.5) {
			PistonOnSphere piston = new PistonOnSphere(radius / 4, radius, hz);
			assertTrue(Double.isFinite(piston.real(-1)) && Double.isFinite(piston.imaginary(-1)), hz + " Hz");
		}
	}

	@Test
	void aPistonAsLargeAsTheSphereIsAccepted() {
		assertTrue(Double.isFinite(new PistonOnSphere(0.5, 0.4, 500).magnitude(-1)));
	}
}
```

- [ ] **Step 2: Run it** — `./gradlew test --tests '*PistonOnSphereTest'`. Expected: compilation failure, `PistonOnSphere` missing.

- [ ] **Step 3: Implement `PistonOnSphere`** with the formula of § 4.1. Upward recurrences: `h_0 = −i·e^{iz}/z`, `h_1 = e^{iz}(−1/z − i/z²)`, `h_{m+1} = (2m+1)/z·h_m − h_{m−1}`, `h'_0 = −h_1`, `h'_m = h_{m−1} − (m+1)/z·h_m`; `P_{m+1} = ((2m+1)x·P_m − m·P_{m−1})/(m+1)`. Real and imaginary parts in two `double[]`. Evaluate once per degree (181 values) divided by `|p(0)|`; the getters interpolate linearly between degrees.

- [ ] **Step 4: Run it** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -am "Compute the sound field of a driver on a cabinet"` (after `git add` of both files).

---

### Task 2: Cabinets and radiation pattern

**Files:**
- Create: `Driver.java`, `Cabinet.java`, `Cabinets.java`, `BandGains.java`, `RadiationCalculator.java`, `RadiationPattern.java`, `RadiationSummary.java` in `core/acoustics/radiation/`
- Test: `core/acoustics/radiation/BandGainsTest.java`, `RadiationPatternTest.java`, `RadiationSummaryTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `record Driver(double azimuthDeg, double elevationDeg, double right, double up, double front, double radius, double lowHz, double highHz)`
  - `record Cabinet(double width, double height, double depth, List<Driver> drivers)` with `double sphereRadius()`
  - `Cabinets.of(SpeakerKind kind)` → `Cabinet`
  - `record BandGains(double low, double mid, double high)` with `FLAT` (1, 1, 1), `double loudest()`, `BandGains relative()` (each divided by the loudest), `BandGains near(double distance)` (from 1 at distance 0 to itself at 1 block, linear)
  - `RadiationPattern.of(Cabinet)`, `RadiationPattern.FLAT`, `BandGains at(double azimuthDeg, double elevationDeg)`, `BandGains around()`
  - `record RadiationSummary(boolean omni, int coverageDeg, int rearLossDb)` with `static RadiationSummary of(RadiationPattern)`

- [ ] **Step 1: Write the failing tests**

```java
class BandGainsTest {
	@Test
	void relativeNeverExceedsOne() {
		BandGains gains = new BandGains(0.99, 1.13, 0.95).relative();
		assertEquals(1, gains.mid(), 1e-12);
		assertEquals(0.99 / 1.13, gains.low(), 1e-12);
		assertEquals(1.13, new BandGains(0.99, 1.13, 0.95).loudest(), 1e-12);
	}

	@Test
	void fadesToFlatAtTheSpeaker() {
		BandGains gains = new BandGains(0.8, 0.4, 0.2);
		assertEquals(BandGains.FLAT, gains.near(0));
		assertEquals(0.7, gains.near(0.5).mid(), 1e-12);
		assertEquals(gains, gains.near(1));
		assertEquals(gains, gains.near(30));
	}
}

class RadiationPatternTest {
	private static final RadiationPattern PARTY = RadiationPattern.of(Cabinets.of(SpeakerKind.PARTY));
	private static final RadiationPattern PORTABLE = RadiationPattern.of(Cabinets.of(SpeakerKind.PORTABLE));

	@ParameterizedTest
	@CsvSource({"0, 0, 0, 0", "30, -0.3, -1.3, -2.9", "60, -1.2, -4.0, -7.6", "90, -2.6, -7.6, -12.6",
			"120, -3.5, -11.9, -20.8", "150, -2.8, -15.8, -29.5", "180, -2.2, -11.8, -27.5"})
	void partyMatchesTheReference(double azimuth, double low, double mid, double high) {
		for (double side : new double[] {azimuth, -azimuth}) {
			BandGains gains = PARTY.at(side, 0);
			assertDb(low, gains.low());
			assertDb(mid, gains.mid());
			assertDb(high, gains.high());
		}
	}

	@Test
	void portableIsTheSameAllAround() {
		for (int azimuth = 0; azimuth < 360; azimuth += 5) {
			BandGains gains = PORTABLE.at(azimuth, 0);
			assertEquals(0, Decibels.fromAmplitude(gains.low()), 3);
			assertEquals(0, Decibels.fromAmplitude(gains.mid()), 3);
			assertEquals(0, Decibels.fromAmplitude(gains.high()), 3);
		}
	}

	@Test
	void aRowOfSubsDirectsTheBass() {
		double single = row(1).at(90, 0).low();
		double four = row(4).at(90, 0).low();
		assertEquals(-2.8, Decibels.fromAmplitude(single), 0.5);
		assertEquals(-15.8, Decibels.fromAmplitude(four), 1.5);
	}

	@Test
	void turnedDriversTurnThePattern() {
		Driver driver = new Driver(90, 0, 0, 0, 0, 0.13, 0, 0);
		RadiationPattern pattern = RadiationPattern.of(new Cabinet(0.75, 1, 0.62, List.of(driver)));
		assertTrue(pattern.at(90, 0).high() > 10 * pattern.at(-90, 0).high());
	}

	@Test
	void isContinuousAroundAndAtThePoles() {
		assertEquals(PARTY.at(0, 10), PARTY.at(360, 10));
		assertEquals(PARTY.at(-90, 10), PARTY.at(270, 10));
		assertEquals(PARTY.at(0, 90), PARTY.at(137, 90));
		assertEquals(PARTY.at(359.99, 0).mid(), PARTY.at(0.01, 0).mid(), 1e-3);
		assertEquals(PARTY.at(12, 89.99).mid(), PARTY.at(200, 89.99).mid(), 1e-2);
	}

	@Test
	void neverFallsUnderTheFloor() {
		for (int azimuth = 0; azimuth < 360; azimuth += 10) {
			assertTrue(Decibels.fromAmplitude(PARTY.at(azimuth, 0).high()) >= -48.001);
		}
	}

	@Test
	void aroundIsTheMeanPower() {
		assertEquals(-1.8, Decibels.fromAmplitude(PARTY.around().low()), 0.5);
		assertEquals(-4.9, Decibels.fromAmplitude(PARTY.around().mid()), 0.5);
		assertEquals(-6.8, Decibels.fromAmplitude(PARTY.around().high()), 0.5);
		assertEquals(BandGains.FLAT, RadiationPattern.FLAT.around());
	}

	/** {@code count} 46 cm subs side by side, one block apart. */
	private static RadiationPattern row(int count) {
		List<Driver> drivers = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			drivers.add(new Driver(0, 0, i - (count - 1) / 2.0, 0, 0, 0.2, 0, 0));
		}
		return RadiationPattern.of(new Cabinet(0.7, 0.9, 0.8, drivers));
	}

	/** ±0.5 dB, ±1.5 dB under −20 dB. */
	private static void assertDb(double expected, double gain) {
		assertEquals(expected, Decibels.fromAmplitude(gain), expected < -20 ? 1.5 : 0.5);
	}
}

class RadiationSummaryTest {
	@Test
	void partyCoversAHundredDegrees() {
		assertEquals(new RadiationSummary(false, 100, 12),
				RadiationSummary.of(RadiationPattern.of(Cabinets.of(SpeakerKind.PARTY))));
	}

	@Test
	void portableIsOmnidirectional() {
		assertTrue(RadiationSummary.of(RadiationPattern.of(Cabinets.of(SpeakerKind.PORTABLE))).omni());
		assertTrue(RadiationSummary.of(RadiationPattern.FLAT).omni());
	}
}
```

- [ ] **Step 2: Run them** — `./gradlew test --tests '*radiation*'`. Expected: compilation failure.

- [ ] **Step 3: Implement the records and `Cabinets`** with the values of § 3. Portable: 0.875 × 0.5625 × 0.5625, drivers at 0°, 90°, 180°, 270°, position 0: radius 0.04 with `highHz` 2500, and radius 0.0125 with `lowHz` 2500. Party: 0.75 × 1 × 0.62; radius 0.13, `up` −0.25, `highHz` 1800; radius 0.025, `up` 0.25, `lowHz` 1800.

- [ ] **Step 4: Implement `RadiationCalculator` and `RadiationPattern`** (§ 4.2).
  - `RadiationCalculator(Cabinet)` (package-private) builds, per band and frequency, one `PistonOnSphere` per distinct radius; `double power(int band, double azimuthDeg, double elevationDeg)` is the mean of `|P|²` over the 9 frequencies at the 7 smoothing points. Around a centre `c`, the points are `cos10°·c + sin10°·(cos t·e1 + sin t·e2)` with `t = 0°, 60°…300°`, `e1` the horizontal vector to the right (azimuth + 90°, elevation 0) and `e2 = c × e1`. Direction vector: right `sin az·cos el`, up `sin el`, front `cos az·cos el`.
  - `RadiationPattern` stores powers `[band][elevation −90…90][azimuth 0…350]` divided by the power at 0/0, at least `10^(−4.8)`; both poles use azimuth 0 for every azimuth. `at` wraps the azimuth, clamps the elevation, interpolates powers bilinearly and returns square roots. `FLAT` holds ones.

- [ ] **Step 5: Implement `RadiationSummary.of`** (§ 5.4): `omni` when `mid` ≥ −3 dB at every degree of elevation 0 (then 360 and 0); otherwise coverage = number of whole degrees with `high` > −6 dB, rounded to 10, and rear = `−mid` in dB at 180°, rounded.

- [ ] **Step 6: Run them** — same command. Expected: PASS, the class in under 5 s.

- [ ] **Step 7: Commit** — `Compute the radiation pattern of a speaker from its drivers`

---

### Task 3: Radiation filter

**Files:**
- Create: `core/acoustics/Shelf.java`, `core/acoustics/radiation/RadiationFilter.java`
- Modify: `core/acoustics/Equalizer.java` (uses `Shelf`; same public API and results)
- Test: `core/acoustics/radiation/RadiationFilterTest.java` (`EqualizerTest` unchanged)

**Interfaces:**
- Consumes: `BandGains`.
- Produces: `Shelf.low(double sampleRate, double cornerHz)`, `Shelf.high(…)`, `void gainDb(double db)`, `double next(double sample)` (RBJ, Q 0.707, transposed direct form II, keeps its state; 0 dB passes the signal bit for bit); `RadiationFilter(double sampleRate)`, `void process(float[] samples, int count, BandGains relative)`.

- [ ] **Step 1: Write the failing test** — 48 kHz, blocks of 4800 samples. Helper `level(double hz, BandGains gains)`: 10 blocks of a sine of amplitude 0.5 through one filter, RMS of the last block over the RMS of the input, in dB.

```java
private static final BandGains BEHIND = db(0, -9.6, -25.3);   // party, straight behind, relative

@Test
void eachBandGetsItsGain() {
	assertEquals(0, level(60, BEHIND), 1);
	assertEquals(-9.6, level(1000, BEHIND), 1);
	assertEquals(-25.3, level(8000, BEHIND), 1);
}

@Test
void equalGainsPassTheSignalUntouched() {
	float[] samples = noise(4800, 1);
	float[] expected = samples.clone();
	new RadiationFilter(48_000).process(samples, samples.length, BandGains.FLAT);
	assertArrayEquals(expected, samples);
}

@Test
void firstBlockTakesTheGainsAtOnce() {
	float[] samples = sine(8000, 4800);
	new RadiationFilter(48_000).process(samples, samples.length, BEHIND);
	assertEquals(-25.3, db(rms(samples, 480, 960)) - db(rms(sine(8000, 4800), 480, 960)), 1.5);
}

@Test
void blocksJoinWithoutJumps() {
	// One filter over 2 blocks of 4800 equals one filter over a block of 9600, sample by sample.
	// (constant gains, 1e-6)
}

@Test
void fastChangesStayBoundedWithoutJumps() {
	// 20 blocks of a 200 Hz sine of amplitude 1, gains alternating FLAT and BEHIND on every block:
	// every sample within [-1, 1], largest step between two samples below 0.05 (a full-scale 200 Hz sine steps 0.027).
}
```

The two commented tests assert exactly what their comments say.

- [ ] **Step 2: Run it** — `./gradlew test --tests '*RadiationFilterTest' --tests '*EqualizerTest'`. Expected: compilation failure.

- [ ] **Step 3: Implement `Shelf`**, move the RBJ coefficients of `Equalizer` into it (the high shelf flips the sign of every `(A−1)`/`(A+1)` cosine term and of `b1`, `a1`), and make `Equalizer` delegate to `Shelf.low`.

- [ ] **Step 4: Implement `RadiationFilter`**: output = mid gain × low shelf (315 Hz, low/mid) × high shelf (2800 Hz, high/mid). Every 48 samples, the three gains move linearly in dB from the previous block's gains to the new ones, reached at the end of the block; the first call starts at its own gains. Output clamped to [−1, 1].

- [ ] **Step 5: Run it** — same command. Expected: PASS.

- [ ] **Step 6: Commit** — `Filter a stream by the direction it is heard from`

---

### Task 4: Direction of the listener

**Files:**
- Modify: `core/acoustics/SpeakerAxis.java` (add only; `directivity` goes in Task 6)
- Test: `core/acoustics/SpeakerAxisTest.java`

**Interfaces:**
- Consumes: `RadiationPattern`, `BandGains`.
- Produces: `BandGains heard(RadiationPattern pattern, double dx, double dy, double dz)`, the offset going from the speaker to the ear, in blocks; includes the near fade.

- [ ] **Step 1: Write the failing tests** (added to `SpeakerAxisTest`)

```java
private static final RadiationPattern PARTY = RadiationPattern.of(Cabinets.of(SpeakerKind.PARTY));
/** A driver turned to the speaker's right, then one turned up. */
private static final RadiationPattern RIGHT = driver(90, 0);
private static final RadiationPattern UP = driver(0, 90);

@Test
void hearsTheFrontAndTheBack() {
	SpeakerAxis east = SpeakerAxis.block(1, 0);
	assertEquals(PARTY.at(0, 0).high(), east.heard(PARTY, 10, 0, 0).high(), 1e-9);
	assertEquals(PARTY.at(180, 0).high(), east.heard(PARTY, -10, 0, 0).high(), 1e-9);
}

@Test
void rightOfASpeakerFacingNorthIsEast() {
	SpeakerAxis north = SpeakerAxis.block(0, -1);
	assertEquals(RIGHT.at(90, 0).high(), north.heard(RIGHT, 10, 0, 0).high(), 1e-9);
	assertEquals(RIGHT.at(-90, 0).high(), north.heard(RIGHT, -10, 0, 0).high(), 1e-9);
}

@Test
void hearsFromAbove() {
	assertEquals(UP.at(0, 45).high(), SpeakerAxis.block(0, 1).heard(UP, 0, 5, 5).high(), 1e-9);
}

@Test
void onTheBackTheSoundGoesBehindTheCarrier() {
	SpeakerAxis back = SpeakerAxis.carried(CarryPlace.BACK, 0, 0);   // body faces south
	assertEquals(1, back.heard(PARTY, 0, 0, -10).high(), 1e-9);
}

@Test
void onTheGroundEveryDirectionIsTheSame() {
	assertEquals(PARTY.around(), SpeakerAxis.GROUND.heard(PARTY, 0, 0, 10));
	assertEquals(PARTY.around(), SpeakerAxis.GROUND.heard(PARTY, 10, 3, 0));
}

@Test
void earAtTheSpeakerHearsItWhole() {
	assertEquals(BandGains.FLAT, SpeakerAxis.block(1, 0).heard(PARTY, 0, 0, 0));
	assertEquals(BandGains.FLAT, SpeakerAxis.GROUND.heard(PARTY, 0, 0, 0));
}

@Test
void straightAboveIsFinite() {
	assertEquals(PARTY.at(0, 90), SpeakerAxis.block(1, 0).heard(PARTY, 0, 4, 0));
	assertEquals(PARTY.at(0, -90), SpeakerAxis.block(1, 0).heard(PARTY, 0, -4, 0));
}

private static RadiationPattern driver(double azimuth, double elevation) {
	return RadiationPattern.of(new Cabinet(0.75, 1, 0.62,
			List.of(new Driver(azimuth, elevation, 0, 0, 0, 0.13, 0, 0))));
}
```

- [ ] **Step 2: Run it** — `./gradlew test --tests '*SpeakerAxisTest'`. Expected: compilation failure, `heard` missing.

- [ ] **Step 3: Implement `heard`**: azimuth `atan2(d·right, d·axis)`, elevation `atan2(dy, hypot(dx, dz))`, in degrees; `around()` when not directed; then `.near(distance)`.

- [ ] **Step 4: Run it** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `Find the direction a speaker is heard from`

---

### Task 5: Client rendering

**Files:**
- Create: `client/audio/render/SpeakerRadiation.java`
- Modify: `client/RaveCraftClient.java`, `client/audio/render/RenderStream.java`, `client/audio/render/AudioWorker.java`, `core/acoustics/Propagation.java`
- Test: `core/acoustics/PropagationTest.java`

**Interfaces:**
- Consumes: Tasks 2 to 4.
- Produces: `Propagation.gain(AcousticSettings, DeviceProfile, double volume, double distance, double directionGain)` (replaces both current variants); `SpeakerRadiation.start()`, `static RadiationPattern pattern(SpeakerKind)`, `static RadiationSummary summary(SpeakerKind)`.

- [ ] **Step 1: Adapt `PropagationTest`** — the existing `gain` calls keep `1` as last argument (now the direction gain); replace `gainUsesTheGivenDirectivity` with:

```java
@Test
void gainUsesTheDirectionGain() {
	double front = Propagation.gain(S, DeviceProfile.PARTY, 1, 4, 1);
	assertEquals(front * 0.25, Propagation.gain(S, DeviceProfile.PARTY, 1, 4, 0.25), EPS);
}
```

- [ ] **Step 2: Run it** — `./gradlew test --tests '*PropagationTest'`. Expected: `gainUsesTheDirectionGain` FAILS (0.25 is read as a cosine: angle 75°).

- [ ] **Step 3: Change `Propagation.gain`**: one method, `… * directionGain`. Update the class Javadoc.

- [ ] **Step 4: Write `SpeakerRadiation`**: `start()` runs a daemon thread `RaveCraft radiation` that computes the pattern and summary of every `SpeakerKind` and publishes the whole immutable map in one volatile write; a `RuntimeException` is logged once and the map stays empty. Without an entry: `RadiationPattern.FLAT` and its summary. Call `start()` from `RaveCraftClient.onInitializeClient`.

- [ ] **Step 5: Wire `RenderStream`**: `facingX`, `facingZ` and `directivity` give way to `volatile SpeakerAxis axis = SpeakerAxis.GROUND` and `volatile SpeakerKind kind = SpeakerKind.PORTABLE`, both set in `refresh`. Add, audio thread only, `BandGains radiation = BandGains.FLAT` and a `RadiationFilter`; `nextBlock` runs it right after the equalizer with `radiation`, also when the block is only consumed.

- [ ] **Step 6: Wire `AudioWorker.weigh`**: `heard = stream.axis.heard(SpeakerRadiation.pattern(stream.kind), dx, dy, dz)`. With effects wanted (`AcousticRenderer.efxWanted()`): direction gain `heard.loudest()`, `stream.radiation = heard.relative()`. Without: direction gain `heard.mid()`, `stream.radiation = BandGains.FLAT`.

- [ ] **Step 7: Run** — `./gradlew build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit** — `Render each speaker by its computed radiation`

---

### Task 6: Remove the cone, tooltip, docs

**Files:**
- Delete: `core/acoustics/Directivity.java`, `core/acoustics/DirectivityTest.java`
- Modify: `core/acoustics/DeviceProfile.java`, `SpeakerAxis.java`, `AcousticSettings.java`, `config/AcousticSettingsJson.java`, `network/payload/AcousticSettingsPayload.java`, `client/SpeakerTooltip.java`, `assets/ravecraft/lang/en_us.json`, `fr_fr.json`, `docs/SPEC.md`
- Test: `SpeakerAxisTest`, `PropagationTest`, `AcousticSettingsTest`, `AcousticSettingsPayloadTest`, `AudioRelayGameTest` (constructors), and any other use found by `grep -rn irectivity src`

**Interfaces:**
- Consumes: `SpeakerRadiation.summary`.
- Produces: `record DeviceProfile(double powerWatts, double sensitivityDb, double bassCutDb, double bassCutHz)`.

- [ ] **Step 1: Adapt the tests**: drop the last `DeviceProfile` argument everywhere; delete `DirectivityTest` and, in `SpeakerAxisTest`, `groundIsOmni` (keep its `assertFalse(SpeakerAxis.GROUND.directed())` in `groundHasNoAxis`) and `directedKeepsProfile`. Add to `AcousticSettingsPayloadTest`:

```java
@Test
void aProfileTakesFourValues() {
	ByteBuf buf = Unpooled.buffer();
	AcousticSettingsPayload.CODEC.encode(buf, new AcousticSettingsPayload(AcousticSettings.DEFAULT));
	int header = 4 * Double.BYTES;
	int partyPower = header + 4 * Double.BYTES;
	assertEquals(DeviceProfile.PARTY.powerWatts(), buf.getDouble(partyPower));
}
```

- [ ] **Step 2: Run** — `./gradlew test`. Expected: compilation failure (4-argument `DeviceProfile` missing).

- [ ] **Step 3: Remove `Directivity`** and everything that reads it. Update the Javadoc of `DeviceProfile`, `SpeakerAxis` and `AcousticSettingsPayload` (the "portable is not sent" sentence goes).

- [ ] **Step 4: Tooltip**: `directivityText(SpeakerKind)` uses the summary. Keys: `tooltip.ravecraft.speaker.directivity.omni` unchanged; `….cone` is replaced by `….covers` = `"covers %s°, −%s dB behind"` / `"couvre %s°, −%s dB à l'arrière"` (coverage, rear loss).

- [ ] **Step 5: Run** — `./gradlew build` then `./gradlew codeQuality`. Expected: both BUILD SUCCESSFUL; `grep -rn "Directivity" src` finds nothing but the tooltip's own method name.

- [ ] **Step 6: `docs/SPEC.md`**: add the plan's link to the radiation line (it stays unchecked until the in-game check).

- [ ] **Step 7: Commit** — `Drop the hand-written directivity`

---

### Task 7: In-game check

- [ ] Build the jar and give the user the list of § 6 « En jeu ». The PR is opened only on their explicit agreement; the SPEC line is checked once they confirm.
