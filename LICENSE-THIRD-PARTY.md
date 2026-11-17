# Third-party libraries

RaveCraft bundles the following libraries unmodified, as separate jars inside the mod jar (Fabric jar-in-jar,
`META-INF/jars/`). Each keeps its own licence and can be replaced by another build of the same version.

| Library | Version | Use | Licence | Source |
|---|---|---|---|---|
| JLayer (JavaZOOM) | 1.0.1 (`javazoom:jlayer`) | MP3 decoding | GNU LGPL, version 2 or (at your option) any later version | https://github.com/umjammer/jlayer (original: http://www.javazoom.net/javalayer/javalayer.html) |
| jFLAC | 1.5.2 (`org.jflac:jflac-codec`) | FLAC decoding | GNU LGPL, version 2 or (at your option) any later version (source headers; the Maven POM names LGPL 2.1) | https://jflac.sourceforge.net/ |
| Concentus | 1.0.2 (`io.github.jaredmdobson:concentus`) | Opus encoding and decoding of relayed audio | BSD-3-Clause (Opus licence); full text shipped in the mod jar at `licenses/concentus-LICENSE.txt` | https://github.com/jaredmdobson/concentus |

The licence terms of JLayer and jFLAC are stated in their source headers (those jars ship no licence file). Full texts:
https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt and https://www.gnu.org/licenses/lgpl-3.0.txt.

OGG Vorbis decoding uses JOrbis, which ships with Minecraft and is not bundled by RaveCraft.
