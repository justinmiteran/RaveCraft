# Local Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A powered portable speaker plays music from the player's own PC (a file, a folder or an `.m3u`/`.m3u8` playlist), chosen in a minimal in-game browser, heard by that player only, positioned at the speaker.

**Architecture:** Pure-Java pieces (playlist parser, folder browsing, PCM decoders for WAV/FLAC/MP3, mono downmix) live in `core` and are tested in JUnit. The client adapts a decoder to a Minecraft `AudioStream` and plays it through the vanilla sound engine with a `SoundInstance` that overrides `FabricSoundInstance.getAudioStream` (category *Jukebox/Note Blocks*, linear attenuation over 16 blocks). OGG uses Minecraft's own `JOrbisAudioStream`. A minimal screen browses the PC and starts playback. Nothing is sent over the network yet: the relay is step 3.

**Tech Stack:** Fabric 26.3 (Mojang names), Fabric Sound API (`FabricSoundInstance`), JLayer `javazoom:jlayer:1.0.1` (MP3, LGPL), jFLAC `org.jflac:jflac-codec:1.5.2` (FLAC, LGPL 2.1), `javax.sound.sampled` (WAV), JOrbis via `JOrbisAudioStream` (OGG, shipped with Minecraft). Libraries are bundled jar-in-jar with Loom `include`.

**Spec:** [docs/superpowers/specs/2026-11-17-audio-core-design.md](../specs/2026-11-17-audio-core-design.md), section 10, step 2 (`feat/local-playback`).

## Global Constraints

- Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`, Java 25. Mojang names only.
- `src/main` never imports `net.minecraft.client`. `core` never imports Minecraft or Fabric.
- Bundled libraries must have licences compatible with redistribution next to LGPL-3.0-only code; each licence file is checked inside the jar and listed in `LICENSE-THIRD-PARTY.md`.
- Supported audio: `.mp3`, `.ogg`, `.oga`, `.flac`, `.wav`. Playlists: `.m3u`, `.m3u8`.
- No file path ever leaves the client (nothing is networked in this step anyway).
- Decoding and folder listing never run on the render thread.
- Translations in `en_us.json` **and** `fr_fr.json`. Code, comments, commits in English.
- `./gradlew build` green before every commit that closes a task.

## Decisions taken for this step (user-approved 2026-11-17)

| # | Decision |
|---|---|
| L1 | Rendering goes through the **vanilla sound engine** (`FabricSoundInstance.getAudioStream`), not dedicated OpenAL sources. Step 4 reaches the channel's OpenAL source through a mixin for EFX. |
| L2 | Formats: **MP3 + OGG + WAV + FLAC**. |
| L3 | Picking music: a **minimal in-game screen** that browses the PC; it becomes the "My PC" pane of step 5. |
| L4 | Decoders that need no Minecraft class (WAV, FLAC, MP3) live in `core.audio.decode` instead of `client.audio.decode`, so JUnit can test them (spec rule 3.3). OGG stays in the client because it uses `JOrbisAudioStream`. |
| L5 | **Right-click** on a placed speaker opens the screen; **sneak + right-click** toggles power. The screen is the main control from now on (spec 5.1); power moves into it with the network commands of step 3. |
| L6 | Spatialised sources must be mono in OpenAL: every stream is **downmixed to mono 16-bit** before it reaches the sound engine. |
| L7 | Step 2 plays only for the local player, only while the speaker is **placed and powered**. Carrying the speaker, other players hearing it, and "breaking does not stop the music" (D13) arrive with the relay in step 3. |
| L8 | Track title = file name without extension. Tag reading (title, artist, duration) arrives in step 3 with `QueueAdd`. |

## Review Focus

1. **A huge folder** (thousands of files, slow USB key): the screen stays responsive, the listing loads off-thread and shows a loading line.
2. **A corrupt, truncated or unsupported file** in a folder or playlist: it is skipped and the next one plays. No crash, no stuck silence, one log line.
3. **The speaker is broken, powered off or unloaded mid-song**, or the player leaves the world: playback stops within one tick and the decoder is closed.
4. **Playlists from other tools**: relative and absolute paths, `#EXTM3U`/`#EXTINF` lines, blank lines, Windows `\` separators, Latin-1 `.m3u`, missing entries.
5. **Unusual audio formats**: mono files, 8/24-bit or 96 kHz WAV, mono MP3. Each is converted to 16-bit mono and plays at the right pitch.

---

## File Structure

```
src/main/java/com/ravecraft/mod/
  core/library/AudioFiles.java          extension rules (audio, playlist)
  core/library/M3uParser.java           .m3u/.m3u8 → list of paths
  core/library/MusicLibrary.java        roots, folder listing, folder/playlist → tracks
  core/library/LibraryEntry.java        record: path, kind (ROOT, FOLDER, TRACK, PLAYLIST), name
  core/audio/Pcm16.java                 16-bit LE helpers: downmix to mono
  core/audio/decode/PcmDecoder.java     interface: format + read(byte[]) of 16-bit signed LE PCM
  core/audio/decode/PcmFormat.java      record: sampleRate, channels
  core/audio/decode/WavDecoder.java     javax.sound.sampled → 16-bit signed LE
  core/audio/decode/FlacDecoder.java    jFLAC
  core/audio/decode/Mp3Decoder.java     JLayer
  core/audio/decode/Decoders.java       open(Path) by extension (OGG → empty: client handles it)
  block/SpeakerScreenOpener.java        static hook the client fills (no client import in main)
src/client/java/com/ravecraft/mod/client/
  audio/MonoPcmAudioStream.java         PcmDecoder → AudioStream, downmixed to mono
  audio/MonoOggAudioStream.java         JOrbisAudioStream → mono AudioStream
  audio/SpeakerSoundInstance.java       tickable sound at the speaker, custom stream
  audio/LocalPlayback.java              per-speaker track list, next on end/failure, stop rules
  screen/SpeakerBrowserScreen.java      minimal PC browser
src/main/resources/assets/ravecraft/sounds.json
src/test/java/com/ravecraft/mod/core/library/*Test.java, core/audio/**/*Test.java
LICENSE-THIRD-PARTY.md
```

---

### Task 1: Bundle the decoder libraries

**Files:**
- Modify: `build.gradle` (dependencies)
- Create: `LICENSE-THIRD-PARTY.md`
- Modify: `README.md` (one line linking the third-party licences)

**Interfaces:**
- Produces: classes `javazoom.jl.decoder.{Bitstream,Decoder,Header,SampleBuffer}` and `org.jflac.{FLACDecoder,metadata.StreamInfo,frame.Frame,util.ByteData}` on the main and test classpaths, inside the shipped jar.

- [ ] **Step 1: Add the dependencies** as `include(implementation("javazoom:jlayer:1.0.1"))` and `include(implementation("org.jflac:jflac-codec:1.5.2"))`.
- [ ] **Step 2: Check the licences.** Run `./gradlew dependencies --configuration runtimeClasspath | grep -E "jlayer|jflac"`, then list each jar (`unzip -l`) and read every `LICENSE*`/`COPYING*`/`META-INF/LICENSE*` file. Expected: LGPL for both. If a jar names a licence that is not LGPL, Apache-2.0, BSD or MIT, **stop and ask the user** before going further.
- [ ] **Step 3: Check the APIs** with `javap` on the four JLayer classes and four jFLAC classes above. Expected: `Bitstream(InputStream)`, `Bitstream.readFrame()`, `Bitstream.closeFrame()`, `Decoder.decodeFrame(Header, Bitstream)` returning `Obuffer`/`SampleBuffer`, `SampleBuffer.getBuffer()/getBufferLength()/getChannelCount()/getSampleFrequency()`; `FLACDecoder(InputStream)`, `readStreamInfo()` or `readMetadata()`, `readNextFrame()`, `decodeFrame(Frame, ByteData)`. Record any name that differs in the task's commit message and use the real one in Task 4.
- [ ] **Step 4: Write `LICENSE-THIRD-PARTY.md`**: one section per library with name, version, licence, upstream URL, and a note that the jar is shipped unmodified and can be replaced. Link it from `README.md`.
- [ ] **Step 5: Build** — `./gradlew build` → `BUILD SUCCESSFUL`; `unzip -l build/libs/ravecraft-*.jar | grep -E "jlayer|jflac"` lists both nested jars.
- [ ] **Step 6: Commit** — `Bundle MP3 and FLAC decoder libraries`.

### Task 2: Audio file rules and M3U parser

**Files:**
- Create: `core/library/AudioFiles.java`, `core/library/M3uParser.java`
- Test: `src/test/java/com/ravecraft/mod/core/library/AudioFilesTest.java`, `M3uParserTest.java`

**Interfaces:**
- Produces:
  - `AudioFiles.isAudio(Path) : boolean`, `AudioFiles.isPlaylist(Path) : boolean` (case-insensitive extensions from the Global Constraints)
  - `M3uParser.parse(List<String> lines, Path baseDir) : List<Path>`
  - `M3uParser.read(Path playlist) throws IOException : List<Path>` (`.m3u8` → UTF-8; `.m3u` → UTF-8, falling back to ISO-8859-1 on a malformed-input error; a UTF-8 BOM is stripped)

- [ ] **Step 1: Write the failing tests:**
  - `isAudio`: `song.MP3`, `a.flac`, `b.oga` → true; `c.txt`, `noext`, `list.m3u` → false. `isPlaylist`: `x.M3U8` → true.
  - `parse`: lines `#EXTM3U`, `#EXTINF:123,Artist - Title`, `""`, `  `, `music\\a.mp3`, `C:\\abs\\b.flac` (or `/abs/b.flac` on POSIX, chosen with `File.separatorChar`) give exactly two paths: `baseDir.resolve("music/a.mp3").normalize()` and the absolute one unchanged. Leading and trailing spaces are trimmed. Lines starting with `file:///` are converted with `Path.of(URI)`.
  - `read`: a `@TempDir` file written in ISO-8859-1 containing `chanson_é.mp3` resolves to a path whose file name is `chanson_é.mp3`. A `.m3u8` with a BOM resolves its first entry.
- [ ] **Step 2: Run** `./gradlew test --tests "*M3uParserTest" --tests "*AudioFilesTest"` → FAIL (classes missing).
- [ ] **Step 3: Implement** both classes. The parser keeps entries that do not exist on disk: existence is checked in Task 3.
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Commit** — `Add audio file rules and M3U parser`.

### Task 3: Music library browsing

**Files:**
- Create: `core/library/LibraryEntry.java`, `core/library/MusicLibrary.java`
- Test: `src/test/java/com/ravecraft/mod/core/library/MusicLibraryTest.java`

**Interfaces:**
- Consumes: `AudioFiles`, `M3uParser` (Task 2).
- Produces:
  - `record LibraryEntry(Path path, Kind kind, String name)` with `enum Kind { ROOT, FOLDER, TRACK, PLAYLIST }`; `name` is the file name, or for a track the file name without extension.
  - `MusicLibrary.roots() : List<LibraryEntry>`: `File.listRoots()` plus the user's home folder and its `Music` folder when they exist.
  - `MusicLibrary.list(Path folder) throws IOException : List<LibraryEntry>`: folders first, then playlists and tracks; each group sorted case-insensitively; hidden entries and other extensions skipped; at most `MAX_ENTRIES = 2000` entries.
  - `MusicLibrary.tracksOf(LibraryEntry entry) throws IOException : List<Path>`: TRACK → itself; FOLDER → its direct audio files in `list` order; PLAYLIST → `M3uParser.read`, keeping only existing audio files.

- [ ] **Step 1: Write the failing tests** with `@TempDir`: a folder holding `b.mp3`, `A.flac`, `notes.txt`, `.hidden.mp3`, `mix.m3u`, a sub-folder `zeta` → `list` gives `[zeta(FOLDER), mix.m3u(PLAYLIST), A(TRACK), b(TRACK)]`. `tracksOf(folder)` gives `[A.flac, b.mp3]`. `tracksOf(playlist)` for a playlist naming `b.mp3`, `missing.mp3` and `notes.txt` gives `[b.mp3]`. A folder with 2 100 audio files lists exactly 2 000.
- [ ] **Step 2: Run** `./gradlew test --tests "*MusicLibraryTest"` → FAIL.
- [ ] **Step 3: Implement** `MusicLibrary` with `Files.newDirectoryStream` (no recursion). A hidden entry is one where `Files.isHidden` is true or the name starts with `.`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `Add music library browsing`.

### Task 4: PCM decoders and mono downmix

**Files:**
- Create: `core/audio/Pcm16.java`, `core/audio/decode/{PcmFormat,PcmDecoder,WavDecoder,FlacDecoder,Mp3Decoder,Decoders}.java`
- Test: `src/test/java/com/ravecraft/mod/core/audio/Pcm16Test.java`, `core/audio/decode/DecodersTest.java`
- Test-only dependency: `testImplementation "net.sourceforge.javaflacencoder:java-flac-encoder:0.3.7"` (generates the FLAC fixture; never shipped)

**Interfaces:**
- Consumes: Task 1 libraries, `AudioFiles` (Task 2).
- Produces:
  - `record PcmFormat(int sampleRate, int channels)`
  - `interface PcmDecoder extends Closeable { PcmFormat format(); int read(byte[] buffer) throws IOException; }`. `read` fills 16-bit signed little-endian interleaved samples, returns the byte count (always a whole number of frames), and returns `-1` at the end.
  - `Pcm16.downmixToMono(byte[] src, int length, int channels) : byte[]`: averages the channels of each frame and clamps to the 16-bit range; `channels == 1` returns a copy of the first `length` bytes.
  - `Decoders.open(Path) throws IOException : Optional<PcmDecoder>`: `.wav` → `WavDecoder`, `.flac` → `FlacDecoder`, `.mp3` → `Mp3Decoder`, `.ogg/.oga` → `Optional.empty()` (client side), anything else → `IOException`.

- [ ] **Step 1: Write the failing tests:**
  - `Pcm16Test`: the stereo frames `(1000, 3000)` and `(-32768, -32768)` give mono `2000` and `-32768`; mono input is returned unchanged; `length` smaller than the array ignores the tail.
  - `DecodersTest`, with fixtures generated into `@TempDir`:
    - a 0.5 s 44.1 kHz stereo 16-bit sine WAV (`AudioSystem.write`) decodes to `PcmFormat(44100, 2)` and 22 050 frames ± 1;
    - an 8-bit mono 22.05 kHz WAV decodes to `PcmFormat(22050, 1)` with 16-bit output (byte count = 2 × frames);
    - the same sine encoded to FLAC with javaFlacEncoder decodes to `PcmFormat(44100, 2)`, its first 1 000 samples equal to the WAV source's;
    - a `.mp3` file filled with random bytes makes `open(...).get().read(...)` either return `-1` or throw `IOException`, never anything else (corrupt input is handled);
    - a `.ogg` file gives `Optional.empty()`; `x.txt` throws `IOException`.
- [ ] **Step 2: Run** `./gradlew test --tests "*Pcm16Test" --tests "*DecodersTest"` → FAIL.
- [ ] **Step 3: Implement.**
  - `WavDecoder`: `AudioSystem.getAudioInputStream(file)` converted to `PCM_SIGNED`, 16-bit, little-endian, same rate and channels.
  - `FlacDecoder`: jFLAC frame by frame, converting 8/24-bit samples to 16-bit.
  - `Mp3Decoder`: JLayer frame by frame (`Bitstream.readFrame` → `Decoder.decodeFrame` → `SampleBuffer`, then `closeFrame`); the format comes from the first decoded frame; JLayer's `BitstreamException`/`DecoderException` are wrapped in `IOException`.
  - Every decoder keeps a leftover buffer so that `read` never splits a frame.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `Add WAV, FLAC and MP3 decoders with mono downmix`.

### Task 5: Sneak to toggle power, right-click to open the screen

**Files:**
- Create: `src/main/java/com/ravecraft/mod/block/SpeakerScreenOpener.java`
- Modify: `block/PortableSpeakerBlock.java` (`useWithoutItem`)
- Modify: `src/gametest/java/com/ravecraft/mod/gametest/PortableSpeakerGameTest.java`

**Interfaces:**
- Produces: `SpeakerScreenOpener.set(Consumer<BlockPos>)` and `SpeakerScreenOpener.open(BlockPos)` (no-op until the client sets it; never imports client code).

- [ ] **Step 1: Write the failing GameTests.**
  - `sneakUseTogglesPower`: a mock survival player with `setShiftKeyDown(true)` uses the block through `h.useBlock(pos, player)` → `POWERED` is true and the data says powered.
  - `plainUseDoesNotTogglePower`: the same without sneaking → `POWERED` stays false.
  - Replace the old `usingBlockTogglesPower`, and change every test that powered a speaker with `h.useBlock(pos)` to a helper `powerOn(h, pos)` that uses a sneaking mock player.
- [ ] **Step 2: Run** `./gradlew runGameTest` → the two new tests FAIL.
- [ ] **Step 3: Implement** `useWithoutItem`: if `player.isSecondaryUseActive()` and on the server, toggle power; otherwise, on the client only, call `SpeakerScreenOpener.open(pos)`. Return `InteractionResult.SUCCESS` in both cases.
- [ ] **Step 4: Run** `./gradlew runGameTest` → all pass.
- [ ] **Step 5: Commit** — `Toggle speaker power with sneak and open a screen on use`.

### Task 6: Client playback engine

**Files:**
- Create: `client/audio/{MonoPcmAudioStream,MonoOggAudioStream,SpeakerSoundInstance,LocalPlayback}.java`
- Create: `src/main/resources/assets/ravecraft/sounds.json`
- Modify: `client/RaveCraftClient.java`

**Interfaces:**
- Consumes: `Decoders`, `PcmDecoder`, `Pcm16` (Task 4).
- Produces:
  - `sounds.json` entry `speaker_stream`: `{"sounds":[{"name":"fabric-sound-api-v1:empty","stream":true,"attenuation_distance":16}]}`.
  - `LocalPlayback.play(BlockPos pos, List<Path> tracks)` (replaces what that speaker was playing), `LocalPlayback.stop(BlockPos pos)`, `LocalPlayback.stopAll()`, `LocalPlayback.nowPlaying(BlockPos) : Optional<Path>`. All are called on the client thread.

- [ ] **Step 1: Write `MonoPcmAudioStream`** (implements `AudioStream`): `getFormat()` is 16-bit signed little-endian mono at the decoder's rate; `read(expectedSize)` pulls from the decoder, downmixes and returns a direct `ByteBuffer` (`BufferUtils`/`MemoryUtil` as vanilla does); an empty buffer signals the end.
- [ ] **Step 2: Write `MonoOggAudioStream`**: it wraps `new JOrbisAudioStream(new BufferedInputStream(Files.newInputStream(path)))` and downmixes its 16-bit output when the format has 2 channels.
- [ ] **Step 3: Write `SpeakerSoundInstance`** (extends `AbstractTickableSoundInstance`, `SoundSource.RECORDS`, identifier `ravecraft:speaker_stream`, positioned at the block centre, volume = settings volume / 100, relative = false).
  - `getAudioStream` opens the track asynchronously with `CompletableFuture.supplyAsync(..., net.minecraft.util.Util.ioPool())` and completes exceptionally on failure.
  - `tick()` calls `stop()` when the block at `pos` is not a powered portable speaker.
- [ ] **Step 4: Write `LocalPlayback`.**
  - It keeps one session per speaker: remaining tracks and the current instance.
  - On every `ClientTickEvents.END_CLIENT_TICK` it walks the sessions (no world scan). A session whose instance is no longer active in `SoundManager` moves to the next track; a session that ran out of tracks is removed.
  - A failed open logs one warning with the file name only and moves to the next track.
  - `ClientPlayConnectionEvents.DISCONNECT` calls `stopAll()`.
- [ ] **Step 5: Register** in `RaveCraftClient`: the tick and disconnect events, and `SpeakerScreenOpener.set(pos -> Minecraft.getInstance().setScreen(new SpeakerBrowserScreen(pos)))`, which goes in Task 7.
- [ ] **Step 6: Build** — `./gradlew build` → green (this is client code, so no automated test; the manual check is in Task 8).
- [ ] **Step 7: Commit** — `Play local tracks through a speaker sound instance`.

### Task 7: Minimal browser screen

**Files:**
- Create: `client/screen/SpeakerBrowserScreen.java`
- Modify: `assets/ravecraft/lang/en_us.json`, `fr_fr.json`

**Interfaces:**
- Consumes: `MusicLibrary`, `LibraryEntry` (Task 3), `LocalPlayback` (Task 6).

- [ ] **Step 1: Implement the screen.**
  - **Header:** title, the current folder path, and a "Back" button.
  - **List:** an `ObjectSelectionList` of `LibraryEntry`; click a ROOT or FOLDER to open it.
  - **Bottom row:**
    - "Play": a TRACK plays that track and the rest of its folder, a FOLDER plays its tracks, a PLAYLIST plays its tracks;
    - "Stop";
    - a "Now playing: <name>" line;
    - when the speaker is off, Play is disabled and a hint says "Sneak + right-click to switch it on".
  - **Loading:** listing runs on `Util.ioPool()`, and the result is applied with `minecraft.execute`. While it loads, the list shows a "Loading…" entry; an `IOException` shows "Cannot open this folder".
- [ ] **Step 2: Add the translations** (`screen.ravecraft.speaker_browser.*`) in English and French.
- [ ] **Step 3: Build** — `./gradlew build` → green.
- [ ] **Step 4: Commit** — `Add a minimal speaker browser screen`.

### Task 8: In-game check, docs, PR

**Files:**
- Modify: `docs/SPEC.md` (step 2/5 line), `README.md` if controls are documented there.

- [ ] **Step 1: Check in game (the user, in `runClient`):**
  - each format plays: MP3, OGG, FLAC, WAV, and a mono file;
  - a folder and an `.m3u` play in order;
  - a corrupt file is skipped;
  - the sound fades with distance and pans when you turn your head;
  - the Jukebox/Note Blocks slider changes the volume;
  - breaking or switching off the speaker stops the music, and so does leaving the world;
  - a folder with many files stays responsive.
- [ ] **Step 2: Update `docs/SPEC.md`** with the step 2/5 line (and the change to sneak + right-click for power).
- [ ] **Step 3: Final checks:** `./gradlew runDatagen && ./gradlew build` → green.
- [ ] **Step 4: Commit** — `Update spec progress for local playback`. Ask the user before pushing and opening the PR `Add local music playback`.
