// SPDX-License-Identifier: GPL-3.0-only
// Derived from DangoMusic by Sorihiko and contributors; modified 2026-10-30.
// See THIRD_PARTY_NOTICES.md for source paths and revision.
package com.luoning.saltlyrics.lyrics

import com.luoning.saltlyrics.*
import com.luoning.saltlyrics.modern.*
import com.luoning.saltlyrics.playback.*
import net.minecraft.client.Minecraft
import java.util.Random
import kotlin.math.*

private const val GROUND_MARGIN = 0.5
private const val SPRING = 12.0

class WorldLyrics {
    private class Floating(
        val lines: List<String>, val startMs: Long, val revealMs: Long,
        var x: Double, var y: Double, var z: Double, var yaw: Float, var pitch: Float,
        val charTimes: LongArray = LongArray(0),
    ) {
        var fadeStartNanos = 0L
        var vYaw = 0.0
        var vPitch = 0.0
        var resumedAt = 0L
    }
    private val random = Random()
    private val fading = mutableListOf<Floating>()
    private var active: Floating? = null
    private var currentKey: String? = null
    private var lastLyrics: Lyrics? = null
    private var lastIndex = -1
    private var lastSongPos = 0L
    private var wasPaused = false
    private var glowPass = false
    private var worldIdentity: Any? = null
    private val deepGlow = DeepGlow()
    private fun appearOf(f: Floating): Float = if (f.resumedAt == 0L) 1f else
        ((System.nanoTime() - f.resumedAt) / 3e8f).coerceIn(0f, 1f)

    fun clear() {
        active = null
        fading.clear()
        currentKey = null
        lastLyrics = null
        lastIndex = -1
        lastSongPos = 0
        wasPaused = false
        groundFloor = Double.NaN
        keepNanos = 0L
    }

    fun render() {
        val mc = Minecraft.getInstance()
        if (worldIdentity !== mc.level) { clear(); worldIdentity = mc.level }
        if (!LyricsConfig.enabled || mc.options.hideGui || mc.level == null) { clear(); return }
        val session = SaltLyricsClient.session
        val snap = session.snapshot()
        session.requestLyrics()
        val lyrics = session.lyrics()
        if (currentKey != snap.track?.key || lastLyrics !== lyrics || LyricSplit.enabled != LyricsConfig.splitLines) {
            clear()
            currentKey = snap.track?.key
            lastLyrics = lyrics
            LyricSplit.enabled = LyricsConfig.splitLines
        }
        val now = snap.positionMs + LyricsConfig.offsetMs
        val usable = snap.track != null && snap.state !in setOf(SessionState.NONE, SessionState.ENDED, SessionState.ERROR)
        val moving = snap.state == SessionState.PLAYING ||
            (snap.state == SessionState.UNKNOWN && now > lastSongPos)
        var idx = if (usable && lyrics?.hasSyncableTimes == true) lyrics.indexAt(now) else -1
        if (idx >= 0) {
            val line = lyrics!!.lines[idx]
            if (line.endMs != null && now >= line.endMs) idx = -1
        }
        if (!moving) {
            active?.let { startFade(it) }; active = null
            wasPaused = true
        } else if (idx != lastIndex || wasPaused || abs(now - lastSongPos) > 1000) {
            active?.let { startFade(it) }; active = null
            if (idx >= 0 && lyrics != null) {
                val line = lyrics.lines[idx]
                val end = line.endMs ?: lyrics.lines.getOrNull(idx + 1)?.timeMs ?: (line.timeMs + 5000)
                if (line.text.isNotBlank()) active = spawn(line, end).also {
                    if (wasPaused || now - line.timeMs > 1000) it.resumedAt = System.nanoTime()
                }
            }
            wasPaused = false
        }
        lastIndex = idx
        lastSongPos = now
        fading.removeAll { System.nanoTime() - it.fadeStartNanos > 400_000_000L }
        if (active == null && fading.isEmpty()) return
        val camera = mc.gameRenderer.mainCamera
        NativeWorld.basePose.identity()
            .rotateX(Math.toRadians(camera.xRot.toDouble()).toFloat())
            .rotateY(Math.toRadians(camera.yRot.toDouble() + 180.0).toFloat())
        keepOnScreen()
        val state = GlState()
        val modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack()
        modelView.pushPose()
        modelView.setIdentity()
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix()
        try {
            if (LyricsConfig.glow && deepGlow.available && LyricsConfig.glowStrength > 0.01f) {
                try {
                    if (deepGlow.begin()) {
                        glowPass = true
                        try { drawWorld(now) } finally { glowPass = false }
                        val (r,g,b) = glowTint()
                        deepGlow.end(r,g,b,LyricsConfig.glowStrength * 4f,LyricsConfig.glowRadius)
                    }
                } catch (e: Exception) { glowPass = false; deepGlow.disable(e) }
                NativeWorld.resync()
            }
            drawWorld(now)
        } finally {
            modelView.popPose()
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix()
            state.restore()
        }
    }

    private fun startFade(f: Floating) {
        f.fadeStartNanos = System.nanoTime()
        fading.add(f)
    }

    /** 拆成"词"：英文按空格；中日韩每两个字一组。返回每个词（去掉空白） */
    private fun splitWords(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var cjk = 0
        fun flush() {
            if (sb.isNotEmpty()) out += sb.toString()
            sb.setLength(0)
            cjk = 0
        }
        for (c in text) {
            when {
                c.isWhitespace() -> flush()
                c.code >= 0x2E80 -> {
                    if (sb.isNotEmpty() && cjk == 0) flush()   // 英文后面跟中文：分开
                    sb.append(c)
                    cjk++
                    if (cjk >= 2) flush()
                }
                else -> {
                    if (cjk > 0) flush()
                    sb.append(c)
                }
            }
        }
        flush()
        return out
    }

    /** 每个非空白字符的出现时间：有逐字歌词用真实时间，没有就把一句平均分给每个词 */
    private fun charTimes(line: LyricLine, nextMs: Long): LongArray {
        val times = ArrayList<Long>()
        val words = line.words
        if (words != null) {
            for (w in words) {
                val at = (w.startMs - line.timeMs).coerceAtLeast(0L)
                for (c in w.text) if (!c.isWhitespace()) times += at
            }
        } else {
            val toks = splitWords(line.text)
            val dur = (nextMs - line.timeMs).coerceAtLeast(300L)
            val span = min(dur * 0.75, toks.size * 330.0)
            toks.forEachIndexed { i, t ->
                val at = (span * i / max(1, toks.size)).toLong()
                repeat(t.length) { times += at }
            }
        }
        return times.toLongArray()
    }

    /**
     * "Above ground"：歌词中心最低只到眼睛下面 [GROUND_MARGIN] 格（站在平地上时离地还有一格多），
     * 换算成距离 [d] 处最多能往下偏多少度（原版 pitch 向下为正）
     */
    private fun maxPitch(d: Double): Float =
        Math.toDegrees(asin((GROUND_MARGIN / d).coerceIn(0.0, 1.0))).toFloat()

    private fun spawn(line: LyricLine, nextMs: Long): Floating {
        val text = line.text
        val startMs = line.timeMs
        val ct = charTimes(line, nextMs)
        val lines = wrap(text, 200)
        val chars = lines.sumOf { it.length }
        val lineDur = (nextMs - startMs).coerceAtLeast(400L)
        val reveal = min((chars * 55L).coerceAtLeast(250L), (lineDur * 0.6).toLong())

        val player = RenderPlatform.player
        if (player == null) return Floating(lines, startMs, reveal, 0.0, 0.0, 0.0, 0f, 0f, ct)
        val yaw = player.yaw + (random.nextFloat() * 2f - 1f) * LyricsConfig.spread
        val d = LyricsConfig.distance.toDouble()
        var pitch = (player.pitch - 3f - random.nextFloat() * 10f).coerceIn(-60f, 30f)
        if (LyricsConfig.aboveGround) pitch = min(pitch, maxPitch(d))
        val yr = Math.toRadians(yaw.toDouble())
        val pr = Math.toRadians(pitch.toDouble())
        val dx = -sin(yr) * cos(pr)
        val dy = -sin(pr)
        val dz = cos(yr) * cos(pr)
        val ex = player.eyeX
        val ey = player.eyeY
        val ez = player.eyeZ
        return Floating(lines, startMs, reveal, ex + dx * d, ey + dy * d, ez + dz * d, yaw, pitch, ct)
    }

    /** 按像素宽度换行：英文在空格处断，中日韩可以在任意字之间断 */
    private fun wrap(text: String, maxWidth: Int): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var lastSpace = -1
        for (c in text) {
            sb.append(c)
            if (c == ' ') lastSpace = sb.length - 1
            if (strW(sb.toString()) > maxWidth && sb.length > 1) {
                if (lastSpace > 0) {
                    out += sb.substring(0, lastSpace).trim()
                    val rest = sb.substring(lastSpace + 1)
                    sb.setLength(0)
                    sb.append(rest)
                } else {
                    out += sb.substring(0, sb.length - 1).trim()
                    sb.setLength(0)
                    sb.append(c)
                }
                lastSpace = -1
            }
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out.filter { it.isNotEmpty() }
    }

    // 暖白偏粉的颜色
    private val textRgb get() = LyricsConfig.textRgb

    // ---------------------------------------------------------------- 歌词文字（MC 字体 + 发光）

    private fun strW(s: String): Float = NativeText.width(s)
    private fun charW(c: Char): Float = NativeText.width(c.toString())

    /** 光晕颜色（乘在字本身的颜色上）：0 = 跟随字色 */
    private fun glowTint(): Triple<Float, Float, Float> = when (LyricsConfig.glowColor) {
        1 -> Triple(1f, 0.38f, 0.62f)
        2 -> Triple(0.32f, 0.66f, 1f)
        3 -> Triple(1f, 0.74f, 0.30f)
        else -> Triple(1f, 1f, 1f)
    }

    private class Glyph(val c: Char, val x: Float, val y: Float, val a: Float, val k: Int, val pop: Float = 1f)

    /** 由 (字序号, 第几帧, 种类) 得到一个固定的 -1..1 随机数：同一帧里不管画几遍（发光层、本体）结果都一样 */
    private fun hashRand(k: Int, frame: Long, salt: Int, seed: Int): Float {
        var h = k * 73856093 xor (frame * 19349663L).toInt() xor (salt * 83492791) xor seed
        h = h xor (h ushr 13)
        h *= 0x5bd1e995
        h = h xor (h ushr 15)
        return (h and 0xFFFF) / 32767.5f - 1f
    }

    /**
     * 低帧率 wiggle：每个字每 1/fps 秒换一个随机的小角度、小错位、小缩放，中间保持不动，
     * 就是 AE 里 posterizeTime(8) + wiggle 的一顿一顿手绘感。
     */
    private inline fun withWiggle(g: Glyph, seed: Int, frame: Long, draw: () -> Unit) {
        val amt = LyricsConfig.wiggleAmount
        val wig = LyricsConfig.wiggle && amt > 0.001f
        if (!wig && g.pop == 1f) {
            draw()
            return
        }
        val rot = if (wig) hashRand(g.k, frame, 1, seed) * 9f * amt else 0f
        val dx = if (wig) hashRand(g.k, frame, 2, seed) * 0.8f * amt else 0f
        val dy = if (wig) hashRand(g.k, frame, 3, seed) * 1.6f * amt else 0f
        val sc = (if (wig) 1f + hashRand(g.k, frame, 4, seed) * 0.12f * amt else 1f) * g.pop
        val cx = g.x + charW(g.c) / 2f
        val cy = g.y + 3.5f
        val pen = NativeText
        pen.push()
        pen.translate(cx + dx, cy + dy)
        pen.rotate(rot)
        pen.scale(sc, sc)
        pen.translate(-cx, -cy)
        draw()
        pen.pop()
    }

    private fun drawGlyph(c: Char, x: Float, y: Float, rgb: Int, a: Float, shadow: Boolean) {
        val alpha = (a * 255f).toInt()
        if (alpha < 5) return
        NativeText.pixelText(c.toString(), x, y, (alpha shl 24) or rgb, shadow)
    }

    /**
     * 逐字出现：每个字按顺序在 revealMs 内依次淡入并从下往上浮起一点；
     * 发光由 [DeepGlow] 另外做（先把字画到发光画布上），这里只画字本身。
     */
    private fun drawAnimated(lines: List<String>, elapsedMs: Long, revealMs: Long, alphaMul: Float, charTimes: LongArray? = null) {
        val flashMode = charTimes != null && charTimes.isNotEmpty()
        var n = 0   // 非空白字符序号
        val total = lines.sumOf { it.length }.coerceAtLeast(1)
        val lineH = 11f
        var y = -(lines.size * lineH) / 2f
        var k = 0
        val glyphs = ArrayList<Glyph>()
        for (line in lines) {
            var x = -strW(line) / 2f
            for (c in line) {
                if (flashMode && !c.isWhitespace()) {
                    // 按词闪出：到了这个词的时间，整个词一下子出现，先放大再弹回原大小
                    val at = charTimes!![min(n, charTimes.size - 1)]
                    n++
                    val t = (elapsedMs - at) / 160f
                    if (t >= 0f) {
                        val a = (t * 3f).coerceIn(0f, 1f) * alphaMul
                        val back = (1f - t).coerceIn(0f, 1f)
                        val pop = 1f + 0.55f * back * back
                        if (a > 0.02f) glyphs += Glyph(c, x, y, a, k, pop)
                    }
                } else if (!flashMode) {
                    val appearAt = k.toFloat() / total * revealMs
                    var a = ((elapsedMs - appearAt) / 180f).coerceIn(0f, 1f)
                    a = a * a * (3f - 2f * a)                 // smoothstep
                    val alpha = a * alphaMul
                    if (alpha > 0.02f && !c.isWhitespace()) glyphs += Glyph(c, x, y + (1f - a) * 5f, alpha, k)
                }
                x += charW(c)
                k++
            }
            y += lineH
        }
        if (glyphs.isEmpty()) return


        val seed = lines.hashCode()
        val frame = System.currentTimeMillis() * LyricsConfig.wiggleFps.coerceAtLeast(1) / 1000L
        for (g in glyphs) withWiggle(g, seed, frame) { drawGlyph(g.c, g.x, g.y, textRgb, g.a, !glowPass) }

    }

    // ---------------------------------------------------------------- 不跑出屏幕

    private var keepNanos = 0L

    /**
     * "Above ground" 的最低高度，平滑地跟着镜头走：
     * 用每帧插值过的镜头位置（不是一秒 20 次跳变的玩家位置），往上慢慢跟、往下快一点跟，
     * 跳起来时字只会被柔和地托起一点，不会一顿一顿地抽。
     */
    private var groundFloor = Double.NaN

    private fun wrapDegrees(d: Double): Double {
        var v = d % 360.0
        if (v >= 180.0) v -= 360.0
        if (v < -180.0) v += 360.0
        return v
    }

    /**
     * "Stay on screen"：歌词（连同字的宽度）超出视野时，沿着离视线最近的方向滑回屏幕边上。
     * 用视线方向的偏角（左右 / 上下）判断，视野大小按设置里的 FOV 和窗口宽高比算；离镜头的距离不变。
     * 每帧在画之前做一次，发光层和字本身用的是同一个位置。
     *
     * 往回拉用的是"临界阻尼弹簧"：有速度、会慢慢加速和减速，不会一碰到边就突然开始动、一回来就突然停，
     * 跳跃 / 飞行这种上下来回的时候也是顺的。"Above ground" 的最低高度也算成一个边界交给同一个弹簧。
     */
    private fun keepOnScreen() {
        val now = System.nanoTime()
        val dt = if (keepNanos == 0L) 0.016 else ((now - keepNanos) / 1e9).coerceIn(0.0, 0.1)
        keepNanos = now
        if (!LyricsConfig.stayOnScreen) return
        val platform = RenderPlatform
        val player = platform.player ?: return
        val world = NativeWorld

        if (LyricsConfig.aboveGround) {
            val target = world.cameraY - GROUND_MARGIN
            groundFloor = if (groundFloor.isNaN()) target else {
                val tau = if (target > groundFloor) 0.3 else 0.15
                groundFloor + (target - groundFloor) * (1.0 - exp(-dt / tau))
            }
        } else {
            groundFloor = Double.NaN
        }

        val vHalf = platform.fov / 2.0
        val aspect = platform.windowWidth.toDouble() / platform.windowHeight.coerceAtLeast(1)
        val hHalf = Math.toDegrees(atan(tan(Math.toRadians(vHalf)) * aspect))
        val s = 0.03 * LyricsConfig.scale * (LyricsConfig.distance / 6.5)

        fun follow(f: Floating) {
            val rx = f.x - world.cameraX
            val ry = f.y - world.cameraY
            val rz = f.z - world.cameraZ
            val len = sqrt(rx * rx + ry * ry + rz * rz)
            if (len < 1e-4) return

            // 字本身占的角度：留出一半宽 / 高，再多留 2 度边距
            val halfW = Math.toDegrees(atan2(f.lines.maxOf { strW(it) } * s / 2.0, len))
            val halfH = Math.toDegrees(atan2(f.lines.size * 11.0 * s / 2.0, len))
            val hLim = max(0.0, hHalf - halfW - 2.0)
            val vLim = max(0.0, vHalf - halfH - 2.0)

            val curYaw = Math.toDegrees(atan2(-rx, rz))
            val curPitch = Math.toDegrees(asin((-ry / len).coerceIn(-1.0, 1.0)))
            val dYaw = wrapDegrees(curYaw - player.yaw)
            val dPitch = curPitch - player.pitch

            // 往下最多到哪：屏幕下边，以及（Above ground）不低于地面高度，哪个更靠上用哪个
            var lowLim = vLim
            if (!groundFloor.isNaN()) {
                val floorPitch = Math.toDegrees(asin(((world.cameraY - groundFloor) / len).coerceIn(-1.0, 1.0)))
                lowLim = min(lowLim, floorPitch - player.pitch)
            }
            val tYaw = dYaw.coerceIn(-hLim, hLim)
            val tPitch = if (lowLim >= -vLim) dPitch.coerceIn(-vLim, lowLim) else lowLim

            // 偏出去多少（在边界以内就是 0）
            val eYaw = dYaw - tYaw
            val ePitch = dPitch - tPitch
            if (eYaw == 0.0 && ePitch == 0.0 && abs(f.vYaw) < 0.05 && abs(f.vPitch) < 0.05) {
                f.vYaw = 0.0
                f.vPitch = 0.0
                return
            }

            // 临界阻尼弹簧，分成小步算（帧率低时也稳定）
            var moveYaw = 0.0
            var movePitch = 0.0
            var left = dt
            while (left > 1e-6) {
                val h = min(left, 1.0 / 240.0)
                left -= h
                f.vYaw += (-SPRING * SPRING * (eYaw - moveYaw) - 2.0 * SPRING * f.vYaw) * h
                f.vPitch += (-SPRING * SPRING * (ePitch - movePitch) - 2.0 * SPRING * f.vPitch) * h
                moveYaw += f.vYaw * h
                movePitch += f.vPitch * h
            }

            val newYaw = curYaw + moveYaw
            val newPitch = (curPitch + movePitch).coerceIn(-89.0, 89.0)
            val yr = Math.toRadians(newYaw)
            val pr = Math.toRadians(newPitch)
            f.x = world.cameraX - sin(yr) * cos(pr) * len
            f.y = world.cameraY - sin(pr) * len
            f.z = world.cameraZ + cos(yr) * cos(pr) * len
            // 不朝向镜头时，字的朝向跟着一起转，免得斜着看
            f.yaw = (f.yaw + wrapDegrees(newYaw - curYaw)).toFloat()
            f.pitch = (f.pitch + (newPitch - curPitch)).toFloat().coerceIn(-90f, 90f)
        }

        active?.let { follow(it) }
        fading.forEach { follow(it) }
    }

    private fun drawWorld(songNow: Long) {
        val world = NativeWorld
        world.begin()

        fun draw(f: Floating, alphaMul: Float, lift: Float) {
            var rx = f.x - world.cameraX
            var ry = f.y - world.cameraY
            var rz = f.z - world.cameraZ
            var len = sqrt(rx * rx + ry * ry + rz * rz)

            // 离得太近（走过去 / 疾跑）就沿视线方向把歌词往后推，保持最小距离
            val minD = LyricsConfig.distance * 0.6
            if (LyricsConfig.keepAway && len in 1e-4..minD) {
                val k = minD / len
                rx *= k; ry *= k; rz *= k
                f.x = world.cameraX + rx
                f.y = world.cameraY + ry
                f.z = world.cameraZ + rz
                len = minD
            }

            val yaw: Float
            val pitch: Float
            if (LyricsConfig.facePlayer && len > 1e-4) {
                // 朝向镜头：根据镜头 → 歌词的方向算出转角
                yaw = Math.toDegrees(atan2(-rx, rz)).toFloat()
                pitch = Math.toDegrees(atan2(-ry, sqrt(rx * rx + rz * rz))).toFloat()
            } else {
                yaw = f.yaw
                pitch = f.pitch
            }
            val s = 0.03f * LyricsConfig.scale * (LyricsConfig.distance / 6.5f)
            world.push(rx, ry + lift, rz, yaw, pitch, s)
            drawAnimated(f.lines, songNow - f.startMs, f.revealMs, alphaMul, if (LyricsConfig.wordPop) f.charTimes else null)
            world.pop()
        }

        drawFading { f, a, lift -> draw(f, a, lift) }
        active?.let { draw(it, appearOf(it), 0f) }
    }

    private fun drawFading(draw: (Floating, Float, Float) -> Unit) {
        val now = System.nanoTime()
        fading.forEach { f ->
            val t = (now - f.fadeStartNanos) / 1e9f
            val a = 1f - t / 0.4f
            if (a > 0f) draw(f, a, t * 0.4f)
        }
    }
}
