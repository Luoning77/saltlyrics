// SPDX-License-Identifier: GPL-3.0-only
// Derived from DangoMusic by Sorihiko and contributors; modified 2026-10-30.
// See THIRD_PARTY_NOTICES.md for source paths and revision.
package com.luoning.saltlyrics.lyrics



/** 逐字歌词里的一个字 / 词 */
class LyricWord(val startMs: Long, val durMs: Long, val text: String)

/** @param words 网易云逐字歌词（yrc）里这一句的每个字 / 词的时间；普通 LRC 歌词为 null
 *  @param endMs 行结束时间；null 表示未知（沿用旧行为：由下一行开始推断）。Salt 歌词提供真实结束时间
 *  @param timesValid 该行时间是否可信；false 的行不用于同步定位（Salt 协议 5.4） */
class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord>? = null,
    val endMs: Long? = null,
    val timesValid: Boolean = true,
    /** Translation field contents and additional rows at the same timestamp, in host order. */
    val translations: List<String> = emptyList(),
)

class Lyrics(rawLines: List<LyricLine>, val sourceName: String) {
    /** 去掉"纯音乐，请欣赏"这类提示（网易云给没歌词的歌塞的一句话） */
    private val raw: List<LyricLine> = parallelLines(rawLines.filterNot { isPlaceholder(it.text) })

    companion object {
        /** Exact timestamps only: never guess a translation from a nearby lyric line. */
        private fun parallelLines(lines: List<LyricLine>): List<LyricLine> =
            lines.groupBy { it.timeMs }.toSortedMap().flatMap { (_, sameTime) ->
                // Invalid rows must not acquire a valid timestamp from a neighboring row.
                val valid = sameTime.filter { it.timesValid && it.text.isNotBlank() }
                val invalid = sameTime.filter { !it.timesValid }
                if (valid.isEmpty()) sameTime else if (valid.size == 1) valid + invalid else {
                    val first = valid.first()
                    val translations = (first.translations + valid.drop(1).flatMap { listOf(it.text) + it.translations })
                        .filter { it.isNotBlank() && it != first.text }.distinct()
                    // Some line-only hosts end the first row at the identical timestamp
                    // of its translation. Use the shared group's positive end, if known.
                    val end = valid.mapNotNull { it.endMs?.takeIf { end -> end > first.timeMs } }.maxOrNull()
                    listOf(LyricLine(first.timeMs, first.text, first.words, end, true, translations)) + invalid
                }
            }

        fun isPlaceholder(text: String): Boolean {
            val t = text.filter { it.isLetterOrDigit() }.lowercase()
            if (t.isEmpty()) return false
            return t == "纯音乐" || t.startsWith("纯音乐请欣赏") || "没有填词的纯音乐" in t ||
                t == "instrumental" || t == "纯音乐请您欣赏"
        }
    }

    /** 网易云常把两句写在同一行（中间隔一个空格），拆成两句的版本 */
    private val split: List<LyricLine> by lazy { LyricSplit.split(raw) }

    /** 当前用的歌词行：开了 Split lines 就是拆开后的 */
    val lines: List<LyricLine> get() = if (LyricSplit.enabled) split else raw

    /** 有没有可用于同步定位的行（整首 timesValid=false 时无法跟唱） */
    val hasSyncableTimes: Boolean get() = lines.any { it.timesValid }

    /**
     * 最后一句「时间可信」且开始时间 <= pos 的下标，没有则 -1。
     * timesValid=false 的行只用于展示顺序，不参与同步定位（Salt 协议 5.4）。
     */
    fun indexAt(pos: Long): Int {
        var ans = -1
        for ((i, line) in lines.withIndex()) {
            if (!line.timesValid) continue
            if (line.timeMs <= pos) ans = i else break
        }
        return ans
    }
}

/**
 * 把"两句写在一行"的歌词拆开：
 *   今はまだ知らない 出会ってすらいない  →  今はまだ知らない / 出会ってすらいない
 * 只在空格两边都是中日韩文字时拆（英文本来就用空格分词，不拆）；全角空格也拆。
 * 有逐字时间（yrc）时，后半句从它第一个字开唱时出现；普通 LRC 按字数比例估时间。
 * 拆出来太短（不到 2 个字或不到 0.5 秒）的就并回前一半，不拆。
 */
object LyricSplit {
    /** Lyrics 模块的 Split lines 开关 */
    @Volatile var enabled = true

    private const val MIN_CHARS = 2
    private const val MIN_MS = 500L

    private fun isCjk(c: Char) = c.code >= 0x2E80 && c != '\u3000'

    /** text 里可以拆开的位置：返回每段的起始下标（第一段是 0） */
    private fun cuts(text: String): List<Int> {
        val starts = mutableListOf(0)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == ' ' || c == '\u3000') {
                var j = i
                var fullWidth = false
                while (j < text.length && (text[j] == ' ' || text[j] == '\u3000')) {
                    if (text[j] == '\u3000') fullWidth = true
                    j++
                }
                val prev = text.substring(0, i).trimEnd().lastOrNull()
                val next = text.getOrNull(j)
                if (prev != null && next != null && (fullWidth || (isCjk(prev) && isCjk(next)))) starts += j
                i = j
            } else {
                i++
            }
        }
        return starts
    }

    private class Seg(var text: String, var startMs: Long, var words: MutableList<LyricWord>?) {
        val chars get() = text.count { !it.isWhitespace() }
    }

    fun split(lines: List<LyricLine>): List<LyricLine> {
        val out = ArrayList<LyricLine>(lines.size)
        for ((li, line) in lines.withIndex()) {
            // Splitting each language independently creates unrelated estimated timestamps.
            // Keep the paired text together; the renderer wraps each language visually.
            if (line.translations.isNotEmpty()) {
                out += line
                continue
            }
            val nextMs = lines.getOrNull(li + 1)?.timeMs
            val segs = segments(line, nextMs)
            if (segs.size <= 1) {
                out += line
                continue
            }
            // 太短的并回前一段
            val merged = mutableListOf<Seg>()
            for (sg in segs) {
                val prev = merged.lastOrNull()
                if (prev != null && (sg.chars < MIN_CHARS || prev.chars < MIN_CHARS || sg.startMs - prev.startMs < MIN_MS)) {
                    prev.text = prev.text + " " + sg.text
                    sg.words?.let { w -> prev.words?.addAll(w) }
                } else {
                    merged += sg
                }
            }
            // 最后一段离下一句太近也并回去
            if (merged.size > 1 && nextMs != null && nextMs - merged.last().startMs < MIN_MS) {
                val last = merged.removeAt(merged.size - 1)
                val prev = merged.last()
                prev.text = prev.text + " " + last.text
                last.words?.let { w -> prev.words?.addAll(w) }
            }
            if (merged.size <= 1) {
                out += line
                continue
            }
            for ((i, sg) in merged.withIndex()) {
                val end = merged.getOrNull(i + 1)?.startMs ?: line.endMs
                out += LyricLine(sg.startMs, sg.text, sg.words, end, line.timesValid)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }

    private fun segments(line: LyricLine, nextMs: Long?): List<Seg> {
        val words = line.words
        if (words != null) {
            // 逐字歌词：按每个字在整行里的位置分到各段，段的开始时间 = 段里第一个字的时间
            val full = words.joinToString("") { it.text }
            val starts = cuts(full)
            if (starts.size <= 1) return emptyList()
            val segs = starts.map { Seg("", Long.MAX_VALUE, mutableListOf()) }
            var pos = 0
            for (w in words) {
                val firstChar = w.text.indexOfFirst { !it.isWhitespace() }
                val at = pos + if (firstChar >= 0) firstChar else 0
                val k = starts.indexOfLast { it <= at }.coerceAtLeast(0)
                segs[k].words!!.add(w)
                if (firstChar >= 0 && w.startMs < segs[k].startMs) segs[k].startMs = w.startMs
                pos += w.text.length
            }
            val ok = segs.filter { it.words!!.any { w -> w.text.isNotBlank() } }
            ok.forEach { it.text = it.words!!.joinToString("") { w -> w.text }.trim() }
            return if (ok.size <= 1) emptyList() else ok
        }
        // 普通 LRC：只有整行开始时间，按字数比例估每段的开始时间。
        // 有真实行结束（endMs，如 Salt 歌词）时用它作边界，不用下一行开始推断，避免污染真实时间轴。
        val text = line.text
        val starts = cuts(text)
        if (starts.size <= 1) return emptyList()
        val parts = starts.mapIndexed { i, st -> text.substring(st, starts.getOrNull(i + 1) ?: text.length).trim() }
        val counts = parts.map { p -> p.count { !it.isWhitespace() } }
        val total = counts.sum().coerceAtLeast(1)
        val lineEnd = line.endMs ?: nextMs ?: (line.timeMs + total * 300L)
        val dur = (lineEnd - line.timeMs).coerceAtLeast(0L)
        val singing = dur * 0.85                     // 下一句前通常有一点空白
        var before = 0
        return parts.mapIndexed { i, p ->
            val sg = Seg(p, line.timeMs + (singing * before / total).toLong(), null)
            before += counts[i]
            sg
        }
    }
}
