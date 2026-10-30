# Third-party source and modifications

Salt Lyrics is a derivative work distributed under **GNU GPL version 3 only**.
The complete license text is in LICENSE. Modified source files are marked with
SPDX identifiers and modification dates. This project is maintained independently
by Luoning77 and is not an official DangoMusic release.

## DangoMusic

- Original project: https://github.com/Sorihiko/DangoMusic
- Authors: Sorihiko and DangoMusic contributors; Salt Player integration by Luoning77.
- Source revision extracted: `d1fac66ff988ee4ba19d26e4cdfdcd014730a566`
- Revision repository: https://github.com/Luoning77/DangoMusic/tree/d1fac66ff988ee4ba19d26e4cdfdcd014730a566
- Upstream license: GPL-3.0 (fabric.mod.json); the GPL v3 text is reproduced unmodified in LICENSE.
- Extraction and modification date: 2026-10-30.

Paths below are relative to DangoMusic at that revision. Destination paths are
relative to `src/main/kotlin/com/luoning/saltlyrics/`.

| Original source | Derived destination and changes |
| --- | --- |
| common/ui/src/main/kotlin/me/sorihiko/dango/module/impls/music/LyricsModule.kt | lyrics/WorldLyrics.kt: floating layout, wrapping, per-character reveal/pop, wiggle, damped view following and fades; replaced module/event framework with a minimal Fabric client and post-world mixin and SaltBridge-only state; removed HUD, cover, text flash and smooth-font dependencies; added explicit line-end handling and once-per-frame fade cleanup |
| modern/src/main/kotlin/me/sorihiko/dango/lyrics/DeepGlow.kt | lyrics/DeepGlow.kt: retained multi-level offscreen glow; removed framework interface and renamed packages/logger |
| modern/src/main/kotlin/me/sorihiko/dango/modern/Gl.kt | modern/Gl.kt: retained GL state save/restore; renamed packages |
| modern/src/main/kotlin/me/sorihiko/dango/modern/Imm.kt | modern/Imm.kt: retained immediate-mode quad and matrix helpers; renamed packages |
| modern/src/main/kotlin/me/sorihiko/dango/modern/ModernGfx.kt | modern/NativeRendering.kt: extracted world transform and pixel-font paths; removed UI/font/texture framework dependencies |
| modern/src/mc1.20.1/kotlin/me/sorihiko/dango/modern/McCompat.kt | modern/McCompat.kt: extracted minimal 1.20.1 rendering compatibility helpers |
| common/core/src/main/kotlin/me/sorihiko/dango/lyrics/LyricsProvider.kt | lyrics/Lyrics.kt: retained lyric models and line splitting; removed online lyric lookup/parsers; split segments now retain line ends and timing validity |
| common/core/src/main/kotlin/me/sorihiko/dango/playback/PlaybackSession.kt | playback/PlaybackSession.kt: retained playback snapshot model |
| common/core/src/main/kotlin/me/sorihiko/dango/playback/salt/ | playback/salt/: retained API, clock, session and pairing; renamed packages, request identifiers and config directory; token writes now report failure |
| common/core/src/main/kotlin/me/sorihiko/dango/DangoLogger.kt | LyricsLogger.kt: renamed logger |
| common/core/src/test/kotlin/me/sorihiko/dango/playback/salt/{SaltClockTest,SaltLyricsMappingTest,SaltSessionSourceTest}.kt | src/test/: retained regression tests with renamed packages |

The post-world render injection in `src/main/java/com/luoning/saltlyrics/mixin/GameRendererMixin.java` is adapted from `modern/versions/1.20.1-fabric/src/main/java/me/sorihiko/dango/fabric/mixin/GameRendererMixin.java`. The camera/model-view setup in `WorldLyrics.kt` derives from `modern/src/main/kotlin/me/sorihiko/dango/modern/ModernDango.kt`.

The Fabric 1.20.1 Gradle configuration is adapted from DangoMusic's modern build.
Gradle wrapper files are copied from that project; Gradle is licensed separately
under Apache-2.0: https://github.com/gradle/gradle/blob/v9.2.1/LICENSE.

Additional changes: lyric parsing retains SaltBridge's translation field; same-time
rows form bilingual cues, with original word timing preserved and translations
rendered below as whole lines. Paired cues are not independently split.

No DangoMusic logos, bundled fonts, music services, player panel, or account
management are included. Minecraft and Fabric dependencies retain their own licenses.
Kotlin standard library is bundled under Apache-2.0 (the Apache license and attribution are included in
`META-INF/licenses/`). SaltBridge remains a separate application accessed over localhost.

When distributing a binary, also make its corresponding source and build files
available under GPL-3.0-only. The sources JAR accompanies local builds; keep the
complete source project available as well (including the Gradle wrapper and this notice).
