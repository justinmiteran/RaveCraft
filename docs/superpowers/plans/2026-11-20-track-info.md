# Track Info Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Read the text tags of MP3, FLAC, Ogg and WAV files, show them in the library tooltip, share "Artist — Title" in the queue, and show the time under the mouse on the progress bar.

**Architecture:** A pure-Java tag reader in `core.audio.tags` returns a `TrackInfo` record, one reader per format, and never throws. A pure `TrackInfoCache` decides when to load, re-check or evict, and the client wrapper `TrackInfos` runs the loads on `OffThread`. The library tooltip, `LocalPlayback.add` (shared title) and `PlayerBand` (hover time) consume them. The network is untouched: the shared title travels in the existing `QueueAddPayload` title field.

**Tech Stack:** Java 25, Fabric (MC 26.3, Mojang names), JUnit 5.

**Spec:** [docs/superpowers/specs/2026-11-20-speaker-ui-5b-design.md](../specs/2026-11-20-speaker-ui-5b-design.md) §5 (PR 3), §6, §7.

## Global Constraints

- Tags are read by the mod in pure Java, with no external library (D6). `core` never imports Minecraft.
- Reading is limited to the start of the file and to the last 128 bytes (ID3v1), at most 1 MiB per file. A malformed tag is ignored and never raises an exception to the caller.
- Text is cut to 128 characters. ID3 encodings: ISO-8859-1, UTF-16 with BOM, UTF-16BE, UTF-8.
- The duration comes from the existing `TrackDuration.of(Path)`.
- Cache: on the client, keyed by path and modification date, 512 entries at most, the least recently used leaving first. Loading runs on `OffThread`; until it finishes, the tooltip shows "…".
- "Artist — Title" (em dash `—`, U+2014, with spaces) is used only when the file has both tags; otherwise the file name without its extension.
- The library row keeps the file name (D7). There are no tooltips on queue rows and no favourites (D8).
- Every new text goes in both `en_us.json` and `fr_fr.json`.

## Review Focus

- **An ID3v2 tag bigger than 1 MiB (cover art), with its text frames first.** Expect the title and artist read, the reader stopping at the cap, and nothing thrown. Pinned by `TrackInfoReaderTest.hugeId3TagStillReadsTitle` (Task 1).
- **A size field that runs past the end of the file**, whether an ID3 frame, a FLAC block, a RIFF chunk or an Ogg segment. Expect empty texts and no exception. Pinned by `TrackInfoReaderTest.brokenFilesNeverThrow` (Tasks 1 and 2).
- **A UTF-16 ID3 frame in little-endian with its BOM, holding accented letters.** Expect the letters to come out exactly. Pinned by `TrackInfoReaderTest.id3v23Utf16Bom` (Task 1).
- **Hovering the same file many times in a row.** Expect a single disk read until 5 s have passed, then one re-check. A file edited on disk shows its new tags after the re-check. Pinned by `TrackInfoCacheTest.loadsOnce` and `TrackInfoCacheTest.rechecksAfterDelay` (Task 3).
- **Adding a folder of 200 tracks where some files are unreadable.** Expect every track queued, the unreadable ones under their file name. Pinned by `TrackInfoReaderTest.sharedTitleNeedsBoth` (Task 1) and the fallback in Task 5.

---

## File Structure

| File | Role |
|---|---|
| `src/main/java/com/ravecraft/mod/core/audio/tags/TrackInfo.java` | Record: texts, technical info, `sharedTitle()` |
| `src/main/java/com/ravecraft/mod/core/audio/tags/TrackInfoReader.java` | `read(Path)`: dispatches by extension, never throws, enforces the 1 MiB budget |
| `src/main/java/com/ravecraft/mod/core/audio/tags/Id3Tags.java` | ID3v2.3/2.4, ID3v1, numeric genres |
| `src/main/java/com/ravecraft/mod/core/audio/tags/VorbisComments.java` | Parses a Vorbis comment block; shared by FLAC and Ogg |
| `src/main/java/com/ravecraft/mod/core/audio/tags/ContainerTags.java` | FLAC blocks, Ogg pages and packets, RIFF `LIST`/`INFO` and `fmt ` |
| `src/main/java/com/ravecraft/mod/core/audio/tags/TrackInfoCache.java` | Pure decisions: load, re-check, LRU of 512 |
| `src/client/java/com/ravecraft/mod/client/library/TrackInfos.java` | Client cache on `OffThread`, plus the blocking read for adds |
| `src/client/java/com/ravecraft/mod/client/screen/widget/TrackTooltip.java` | Builds the tooltip lines of a track |
| Modified: `LibraryRow`, `LibraryTree`, `LocalPlayback`, `PlayerBand`, both lang files, `docs/SPEC.md` | |

Tests:

- `src/test/java/com/ravecraft/mod/core/audio/tags/TrackInfoReaderTest.java`
- `src/test/java/com/ravecraft/mod/core/audio/tags/TrackInfoCacheTest.java`
- a small `TagBytes` helper next to them, which builds files byte by byte.

---

### Task 1: `TrackInfo` and MP3 tags

**Files:**
- Create: `core/audio/tags/TrackInfo.java`, `TrackInfoReader.java`, `Id3Tags.java`
- Test: `src/test/java/com/ravecraft/mod/core/audio/tags/TrackInfoReaderTest.java`, `TagBytes.java`

**Interfaces:**
- Produces:
  - `public record TrackInfo(String title, String artist, String album, String year, String genre, String format, int bitrateKbps, int sampleRateHz, int channels, long durationMs)`:
    - absent text is `""`, an unknown number is `0`, an unknown duration is `-1`;
    - the compact constructor turns null into `""`, trims, and cuts each text to `MAX_TEXT = 128`;
    - `public static TrackInfo empty(String format)` (all texts `""`, numbers 0, duration -1);
    - `public Optional<String> sharedTitle()` returns `artist + " — " + title` when both are non-empty, otherwise empty.
  - `public static TrackInfo TrackInfoReader.read(Path file)`:
    - never throws;
    - `format` comes from the extension: `"MP3"`, `"FLAC"`, `"WAV"`, and for `.ogg`/`.oga`/`.opus` `"Ogg Vorbis"` or `"Ogg Opus"` (`"Ogg"` when the codec is unknown);
    - any other extension gives `TrackInfo.empty(ext.toUpperCase())`;
    - `durationMs` is `TrackDuration.of(file).orElse(-1)`.
  - `TrackInfoReader.BUDGET = 1 << 20` bytes. This is the total read for tags. The MPEG frame search uses its own 64 KiB window right after the tag, as `TrackDuration` does, and ID3v1 adds 128 bytes.

- [ ] **Step 1: Write the failing tests**

`TagBytes` helpers:
- `static byte[] id3v2(int version, byte[]... frames)`: a header with a syncsafe size;
- `static byte[] id3Frame(int version, String id, int encoding, byte[] text)`: v3 has a plain int size, v4 a syncsafe one;
- `static byte[] mpegFrame()`: `FF FB 90 00` followed by 413 zero bytes, i.e. MPEG-1 L3 at 128 kb/s, 44.1 kHz, stereo;
- `static byte[] id3v1(String title, String artist, String album, String year, int genre)`;
- `static byte[] concat(byte[]...)`.

Tests:
- `id3v24Utf8Tags`:
  - build a v4 tag with `TIT2` "Night Drive", `TPE1` "Alex", `TALB` "Neon", `TDRC` "2024-05-01", `TCON` "Synthwave", all with encoding 3, followed by `mpegFrame()`;
  - assert `title`, `artist`, `album`, and `year` equal to `"2024"`;
  - assert `genre` "Synthwave", `format` "MP3", `bitrateKbps` 128, `sampleRateHz` 44100, `channels` 2.
- `id3v23Utf16Bom`: a v3 tag with `TIT2` in encoding 1 (`FF FE` then UTF-16LE "Été") and `TYER` "1999"; assert title `"Été"` and year `"1999"`.
- `id3Utf16BeAndLatin1`:
  - `TPE1` in encoding 2 (UTF-16BE "Zoé") gives `"Zoé"`;
  - `TALB` in encoding 0 (ISO-8859-1 bytes of "Café") gives `"Café"`;
  - a value ending in NUL bytes is trimmed.
- `id3NumericGenre`: `TCON` "(17)" gives `"Rock"`, "17" gives `"Rock"`, "(200)" gives `""`.
- `id3v1Fallback`: no ID3v2, then `mpegFrame()`, then `id3v1("Old", "Band", "LP", "1987", 13)`; assert `"Old"`, `"Band"`, `"LP"`, `"1987"` and `"Pop"`.
- `hugeId3TagStillReadsTitle`: the header declares 5 MiB; `TIT2` and `TPE1` come first, then 2 MiB of `APIC` padding is actually written; assert title and artist are read.
- `textCutTo128`: a 300-character `TIT2` gives a title of length 128.
- `sharedTitleNeedsBoth`:
  - both tags give `Optional.of("Alex — Night Drive")`;
  - title only gives empty;
  - a file of random bytes named `x.mp3` gives empty texts and empty `sharedTitle()`.
- `brokenFilesNeverThrow` (MP3 part):
  - a frame size larger than the tag;
  - a tag truncated after 6 bytes;
  - a zero-length file;
  - `assertDoesNotThrow` on each, plus empty `title`.

- [ ] **Step 2: Run them, expecting FAIL**

Run `./gradlew test --tests '*TrackInfoReaderTest'`. Expected: compilation fails because `TrackInfo` does not exist.

- [ ] **Step 3: Implement `TrackInfo`, `TrackInfoReader.read`, and `Id3Tags`**

`Id3Tags`:
- `static void readV2(ByteBuffer tag, int version, Map<String, String> out)` fills the keys `title, artist, album, year, genre`:
  - `TIT2`→title, `TPE1`→artist, `TALB`→album, `TDRC` or `TYER`→year (first 4 characters), `TCON`→genre;
  - the first value wins.
- `static void readV1(ByteBuffer last128, Map<String, String> out)` fills only keys that are still missing.
- `static String genre(String raw)` resolves `(n)` and `n` against the standard table of the 80 ID3v1 genres (0 "Blues" … 79 "Hard Rock"); out of range gives `""`.

Rules:
- When the extended-header flag is set (`0x40`), skip the extended header: its size is a plain int in v2.3 and syncsafe in v2.4. Ignore whole-tag unsynchronisation and read the bytes as they are.
- Stop at the first frame whose id is not 4 characters from `A-Z0-9`, or whose size goes past the tag or past the budget.
- Text frames: the first byte is the encoding (0 ISO-8859-1, 1 UTF-16 with BOM, 2 UTF-16BE, 3 UTF-8). Keep up to the first NUL terminator of that encoding.
- `TrackInfoReader.read` opens a `RandomAccessFile` and reads the 10-byte header first, then `min(tagSize, BUDGET)`.
- The MPEG frame search (bitrate, sample rate, channels) reuses `TrackDuration`'s header rules. Make them package-visible in `TrackDuration` through one shared helper, `static Optional<MpegHeader> mpegHeader(int h)` with `record MpegHeader(int kbps, int rate, int channels)`. Do not copy the tables.
- Catch `IOException | RuntimeException` around each format and return what was gathered, or `empty(format)`.

- [ ] **Step 4: Run them, expecting PASS**

Run `./gradlew test --tests '*TrackInfoReaderTest' --tests '*TrackDurationTest'`. Expected: all pass, including the existing duration tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ravecraft/mod/core/audio/tags src/main/java/com/ravecraft/mod/core/audio/decode/TrackDuration.java src/test/java/com/ravecraft/mod/core/audio/tags
git commit -m "Read MP3 text tags in pure Java"
```

### Task 2: FLAC, Ogg and WAV tags

**Files:**
- Create: `core/audio/tags/VorbisComments.java`, `ContainerTags.java`
- Modify: `TrackInfoReader.java`
- Test: `TrackInfoReaderTest.java`, `TagBytes.java`

**Interfaces:**
- Consumes: `TrackInfo`, `TrackInfoReader.read`, `TrackInfoReader.BUDGET` (Task 1).
- Produces: `static void VorbisComments.read(ByteBuffer littleEndian, Map<String, String> out)`. It reads `vendor_length`, the vendor, the count, then each `length` and `KEY=value`. Keys are case-insensitive: `TITLE`, `ARTIST`, `ALBUM`, `DATE` (first 4 characters), `GENRE`. Text is UTF-8, and the first value of a key wins.

- [ ] **Step 1: Write the failing tests**

`TagBytes` gains:
- `flac(byte[] streamInfo34, byte[]... blocks)`;
- `vorbisComment(String... kv)`;
- `oggPage(int seq, byte[]... packets)` (lacing values, CRC left at 0);
- `riff(byte[]... chunks)`;
- `chunk(String id, byte[] body)` (pads to an even size).

Tests:
- `flacVorbisComment`:
  - the STREAMINFO from `TrackDurationTest.flacDurationFromStreamInfo` (44.1 kHz, 2 channels, 16 bits, 88 200 samples);
  - a 300-byte `PICTURE` block (type 6), then `VORBIS_COMMENT` (type 4) holding `title=Rain`, `ARTIST=Mia` and `DATE=2021-03-03`;
  - assert `"Rain"`, `"Mia"`, `"2021"`, `format` "FLAC", `sampleRateHz` 44100, `channels` 2, `bitrateKbps` 1411 (rate × bits × channels / 1000), `durationMs` 2000.
- `oggVorbisComments`:
  - page 0 holds the identification packet (`\x01vorbis`, version 0, 2 channels, 44100 Hz, nominal bitrate 192 000);
  - page 1 holds the comment packet (`\x03vorbis` + `vorbisComment("TITLE=Wave", "ARTIST=Kai")` + framing bit);
  - assert `"Wave"`, `"Kai"`, `"Ogg Vorbis"`, `bitrateKbps` 192, 44100 Hz, 2 channels.
- `oggCommentSpanningPages`: the comment packet is split across pages 1 and 2 (lacing value 255 at the end of page 1, then a continued page). Assert the title is read.
- `oggOpusTags`:
  - `OpusHead` (version 1, 2 channels, pre-skip 312, input rate 48000), then `OpusTags` + `vorbisComment("TITLE=Op")`;
  - assert `"Op"`, `"Ogg Opus"`, `sampleRateHz` 48000, `channels` 2, `bitrateKbps` 0.
- `wavListInfo`:
  - a `fmt ` chunk (PCM, 2 channels, 44100 Hz, byte rate 176 400), then `LIST` `INFO` holding `INAM` "Loop", `IART` "Duo", `IPRD` "EP", `ICRD` "2020", `IGNR` "House", NUL-terminated with odd sizes padded, then `data` of 4 bytes;
  - assert all five texts, `"WAV"`, 1411 kb/s, 44100 Hz, 2 channels.
- `brokenFilesNeverThrow` grows:
  - a FLAC block length past the end of the file;
  - a Vorbis comment count of `0x7FFFFFFF`;
  - an Ogg page whose segment table runs past the end;
  - a RIFF chunk size of `0xFFFFFFF0`;
  - `.ogg`, `.flac` and `.wav` files of random bytes.

- [ ] **Step 2: Run them, expecting FAIL**

Run `./gradlew test --tests '*TrackInfoReaderTest'`. Expected: the new tests fail (empty title).

- [ ] **Step 3: Implement `ContainerTags.flac/ogg/wav(RandomAccessFile, Map<String,String> out, int[] tech)`**

`tech` holds `{bitrateKbps, sampleRateHz, channels}`; `ogg` returns the codec name. Then wire them into `TrackInfoReader.read`.

FLAC:
- walk the block headers (bit 7 = last, 7-bit type, 24-bit length);
- read only the bodies of STREAMINFO (type 0) and VORBIS_COMMENT (type 4);
- `seek` past the others, PICTURE included;
- stop when a block would push the total read past `BUDGET`.

Ogg:
- walk the pages (`OggS`, header of 27 + segment count, then lacing);
- rebuild packets 0 and 1 by concatenating segments until a lacing value is under 255;
- stop at packet 1 or at `BUDGET`;
- packet 0 gives the codec and its technical fields: Vorbis channels at +11, rate at +12, nominal bitrate at +20; Opus channels at +9, input rate at +12 (0 → 48000);
- packet 1 goes through `VorbisComments.read` after its `\x03vorbis` (7 bytes) or `OpusTags` (8 bytes) prefix.

WAV:
- walk the chunks from offset 12;
- in `fmt `, read channels at +2, rate at +4, byte rate at +8 (bitrate = byteRate × 8 / 1000);
- in a `LIST` chunk whose form is `INFO`, map `INAM` title, `IART` artist, `IPRD` album, `ICRD` year (first 4 characters), `IGNR` genre;
- decode RIFF text as strict UTF-8, falling back to ISO-8859-1;
- stop at `data` once `fmt ` is seen and the `LIST` is either seen or absent before `data`;
- seek past the `data` chunk to find a trailing `LIST`, as long as it stays within `BUDGET` bytes read.

- [ ] **Step 4: Run them, expecting PASS**

Run `./gradlew test --tests '*TrackInfoReaderTest'`. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ravecraft/mod/core/audio/tags src/test/java/com/ravecraft/mod/core/audio/tags
git commit -m "Read FLAC, Ogg and WAV text tags"
```

### Task 3: Info cache

**Files:**
- Create: `core/audio/tags/TrackInfoCache.java`, `src/client/java/com/ravecraft/mod/client/library/TrackInfos.java`
- Test: `src/test/java/com/ravecraft/mod/core/audio/tags/TrackInfoCacheTest.java`

**Interfaces:**
- Consumes: `TrackInfo`, `TrackInfoReader.read` (Task 1).
- Produces:
  - `TrackInfoCache`:
    - `public static final int CAPACITY = 512`, `public static final long RECHECK_MS = 5000`;
    - `public record Lookup(Optional<TrackInfo> info, boolean load, long knownModified)`, where `knownModified` is -1 when nothing is cached;
    - `public Lookup lookup(String path, long nowMs)`;
    - `public void loaded(String path, long modified, TrackInfo info, long nowMs)`;
    - `public void unchanged(String path, long nowMs)`;
    - `public void failed(String path)`.
  - Client:
    - `public static Optional<TrackInfo> TrackInfos.get(Path track)`, client thread; empty while loading;
    - `public static TrackInfo TrackInfos.readNow(Path track)`, any thread; reads directly without the cache.

- [ ] **Step 1: Write the failing tests**

- `loadsOnce`:
  - `lookup("a", 0)` gives `load == true` and empty `info`;
  - a second `lookup("a", 10)` gives `load == false` (loading in progress);
  - after `loaded("a", 7, info, 20)`, `lookup("a", 30)` gives `info` present and `load == false`.
- `rechecksAfterDelay`:
  - after `loaded("a", 7, info, 0)`, `lookup("a", 5000)` gives `info` present, `load == true` and `knownModified == 7`;
  - while that re-check runs, `lookup("a", 5001)` gives `load == false`;
  - `unchanged("a", 5002)` keeps `info`, and the next re-check is due at 10002.
- `changedFileReplaced`: after `loaded("a", 8, other, 5003)`, `lookup` returns `other`.
- `evictsLeastRecentlyUsed`:
  - load 512 paths `p0..p511`, then `lookup("p0", …)` so it becomes the most recently used;
  - load `p512`;
  - `lookup("p1", …)` gives empty `info` and `load == true`, while `p0` is still present.
- `failedAllowsRetry`: after `lookup` then `failed("a")`, the next `lookup` gives `load == true`.

- [ ] **Step 2: Run them, expecting FAIL**

Run `./gradlew test --tests '*TrackInfoCacheTest'`. Expected: compilation fails.

- [ ] **Step 3: Implement**

`TrackInfoCache` is a `LinkedHashMap` in access order with `removeEldestEntry` above `CAPACITY`. Each entry is `(info, modified, checkedAt)`, plus a `Set<String>` of loads in flight.

`TrackInfos.get` is client-only. It calls `lookup(track.toString(), Util.getMillis())`. When `load` is true, it runs `OffThread.run` and passes `failed` to it:
- the task reads `Files.getLastModifiedTime(track).toMillis()`;
- if that equals `knownModified`, it reports unchanged;
- otherwise it calls `TrackInfoReader.read(track)`;
- on the client thread, the result calls `unchanged` or `loaded`.

- [ ] **Step 4: Run them, expecting PASS**

Run `./gradlew test --tests '*TrackInfoCacheTest'`. Expected: 5 pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ravecraft/mod/core/audio/tags/TrackInfoCache.java src/client/java/com/ravecraft/mod/client/library/TrackInfos.java src/test/java/com/ravecraft/mod/core/audio/tags/TrackInfoCacheTest.java
git commit -m "Cache track info by path and modification date"
```

### Task 4: Library track tooltip

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/screen/widget/TrackTooltip.java`
- Modify: `LibraryRow.java` (lazy tooltip), `LibraryTree.java:rowFor/rowTooltip`, `en_us.json`, `fr_fr.json`

**Interfaces:**
- Consumes: `TrackInfos.get(Path)` (Task 3), `TrackInfo` (Task 1), `PlayerBand.time(long)` (package-visible, existing).
- Produces:
  - `static List<Component> TrackTooltip.lines(Path track, Optional<TrackInfo> info, boolean shift, @Nullable String folder)`;
  - `LibraryRow tooltip(Supplier<List<Component>> tooltip)`. The existing `tooltip(List)` delegates to it, and `tooltip()` calls the supplier, so only the hovered row builds its lines.

- [ ] **Step 1: Make `LibraryRow`'s tooltip a `Supplier`**

Keep `tooltip(List)` as `tooltip(() -> list)`.

- [ ] **Step 2: Implement `TrackTooltip.lines`**

Without Shift:
- line 1: `sharedTitle()`, otherwise the file name without its extension (`AudioFiles.baseName`);
- line 2, gray: the duration through `PlayerBand.time`; omitted when the duration is ≤ 0 or the info is not loaded yet. While loading, it shows `"…"` in gray instead;
- in the flat view only: the existing `in_folder` line, gray;
- last: the existing `tooltip.ravecraft.speaker.shift_hint`, in `DARK_GRAY`.

With Shift:
- the full path;
- then, each in gray and only when non-empty: `track.album`, `track.year`, `track.genre`;
- then the technical line `track.tech`, made of the non-empty parts joined with `" · "`:
  - the format;
  - `track.kbps` (%s kb/s);
  - `track.khz` (%s kHz);
  - `track.channels.mono`, `track.channels.stereo`, or `track.channels.many` (%s channels).
- Numbers use `NumberFormat.getInstance(Locale.forLanguageTag(selected.replace('_', '-')))`, where `selected` is `Minecraft.getInstance().getLanguageManager().getSelected()`. kHz have at most one decimal (44,1 / 48); kb/s use grouping (1 411).
- Info not loaded yet: the path, then `"…"`.

Lang keys, with `KEY = screen.ravecraft.speaker.`:

| Key | en_us | fr_fr |
|---|---|---|
| `track.album` | `Album: %s` | `Album : %s` |
| `track.year` | `Year: %s` | `Année : %s` |
| `track.genre` | `Genre: %s` | `Genre : %s` |
| `track.kbps` | `%s kb/s` | `%s kb/s` |
| `track.khz` | `%s kHz` | `%s kHz` |
| `track.channels.mono` | `mono` | `mono` |
| `track.channels.stereo` | `stereo` | `stéréo` |
| `track.channels.many` | `%s channels` | `%s canaux` |

- [ ] **Step 3: In `LibraryTree.rowFor`, give `TRACK` rows the new tooltip**

The supplier is `() -> TrackTooltip.lines(Path.of(node.id()), TrackInfos.get(Path.of(node.id())), Minecraft.getInstance().hasShiftDown(), model.flat() ? source.parentName(node) : null)`. Folders, playlists and missing rows keep the tooltip they have today.

- [ ] **Step 4: Verify**

Run `./gradlew build`. Expected: BUILD SUCCESSFUL, all tests pass. This is client UI with no unit test; it is checked in game in Task 6.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/com/ravecraft/mod/client/screen/widget src/main/resources/assets/ravecraft/lang
git commit -m "Show tags and audio details in the library track tooltip"
```

### Task 5: Shared title, and time on the progress bar

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/client/audio/LocalPlayback.java:49-77`, `src/client/java/com/ravecraft/mod/client/screen/widget/PlayerBand.java`, both lang files
- Test: `src/test/java/com/ravecraft/mod/core/audio/tags/TrackInfoReaderTest.java`

**Interfaces:**
- Consumes: `TrackInfos.readNow(Path)` (Task 3), `TrackInfo` (Task 1), `AudioFiles.baseName(Path)`, `OffThread.run` (existing).
- Produces:
  - `public String TrackInfo.queueTitle(String fallback, int max)`: `sharedTitle()`, otherwise `fallback`, cut to `max` characters;
  - `LocalPlayback.titleOf(Path)` becomes `titleOf(Path, TrackInfo)`, equal to `info.queueTitle(AudioFiles.baseName(track), QueueEntry.MAX_TITLE)`.

- [ ] **Step 1: Write the failing test `queueTitleFallsBackAndCuts` in `TrackInfoReaderTest`**

```java
TrackInfo both = new TrackInfo("T".repeat(100), "A".repeat(100), "", "", "", "MP3", 0, 0, 0, -1);
assertEquals(128, both.queueTitle("file", 128).length());
assertTrue(both.queueTitle("file", 128).startsWith("A".repeat(100) + " — "));
assertEquals("file", TrackInfo.empty("MP3").queueTitle("file", 128));
assertEquals("fi", TrackInfo.empty("MP3").queueTitle("file", 2));
```

Run `./gradlew test --tests '*TrackInfoReaderTest'`. Expected: FAIL, because `queueTitle` is not defined. Implement it and run again. Expected: PASS.

- [ ] **Step 2: Make `LocalPlayback.add(BlockPos, List<Path>)` read the tags first**

1. Cap the list as today.
2. Run `OffThread.run(() -> titles, titles -> enqueue(pos, capped, titles), () -> enqueue(pos, capped, fileNameTitles))`. The task computes `titleOf(track, TrackInfos.readNow(track))` for each track. Batching, `TrackTableStore.register` and `sendPending` move unchanged into `enqueue`.
3. A track whose read fails falls back to its file name, since `readNow` never throws.

- [ ] **Step 3: Hover time on `PlayerBand`**

At the end of `extractWidgetRenderState`, when `duration() > 0` and either `seeking` or `overTrack(mouseX, mouseY)`:
- take `ms`: `seekMs` while seeking, otherwise `msAt(mouseX)`;
- call `graphics.setTooltipForNextFrame(font, List.of(Component.translatable(SpeakerSprites.KEY + "time_of", time(ms), time(duration()))), Optional.empty(), mouseX, mouseY)`.

New key `time_of`: `%s / %s` in both languages.

- [ ] **Step 4: Verify**

Run `./gradlew build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/com/ravecraft/mod/client/audio/LocalPlayback.java src/client/java/com/ravecraft/mod/client/screen/widget/PlayerBand.java src/main/resources/assets/ravecraft/lang src/test/java/com/ravecraft/mod/core/audio/tags
git commit -m "Share Artist — Title in the queue and show the time under the mouse"
```

### Task 6: Docs and in-game check

**Files:**
- Modify: `docs/SPEC.md` (line "5b": PR 3 delivered), spec §6 if a ruling changes behaviour

- [ ] **Step 1: Update `docs/SPEC.md`**

Mark `feat/track-info` as delivered: tags in `core` for 4 formats, client cache, track tooltip, shared title, hover time on the bar, to be checked in game.

- [ ] **Step 2: Run `./gradlew build`**

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add docs/SPEC.md
git commit -m "Mark track info delivered in the spec"
```

- [ ] **Step 4: In-game check, done by the user**

Check each of the following in `runClient`:
1. Tooltips on MP3, FLAC, Ogg and WAV files, with and without tags.
2. Shift held down.
3. A file larger than 1 MiB with a cover.
4. A folder added: titles show as "Artist — Title" in the queue and in the toast.
5. Hovering and dragging the progress bar.
6. A track with an unknown duration (no tooltip on the bar).
