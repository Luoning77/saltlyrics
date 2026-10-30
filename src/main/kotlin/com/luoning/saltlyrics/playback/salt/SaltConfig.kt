// SPDX-License-Identifier: GPL-3.0-only
// Derived from DangoMusic by Sorihiko and contributors; modified 2026-10-30.
// See THIRD_PARTY_NOTICES.md for source paths and revision.
package com.luoning.saltlyrics.playback.salt

import com.luoning.saltlyrics.LyricsLogger
import com.luoning.saltlyrics.LyricsConfig

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * SaltBridge 连接配置：端口（随模块设置持久化）+ 访问令牌（独立文件，不进日志）。
 *
 * 令牌存放于 config/saltlyrics/salt_token.txt，独立保存：
 * 界面遮罩显示、日志不输出、粘贴时过滤非法字符。
 */
object SaltConfig {

    const val DEFAULT_PORT = 8737

    @Volatile var port: Int = DEFAULT_PORT

    private val tokenFile get() = LyricsConfig.directory.resolve("salt_token.txt")

    @Volatile private var cachedToken: String? = null

    /**
     * 读取令牌：优先本机手动配置（salt_token.txt）；
     * 为空时自动发现 SaltBridge 的配对文件（~/.saltbridge/token.txt，插件启动时自动写出）。
     */
    fun token(): String {
        cachedToken?.let { if (it.isNotEmpty()) return it }
        val manual = readTokenFile()
        if (manual.isNotEmpty()) {
            cachedToken = manual
            return manual
        }
        val discovered = discoverToken()
        cachedToken = discovered
        return discovered
    }

    /** SaltBridge 配对文件路径（用户主目录，跨平台） */
    private fun pairingFile(): File =
        File(System.getProperty("user.home") ?: "", ".saltbridge/token.txt")

    /** 自动发现 SaltBridge 写出的令牌；找不到返回空串 */
    private fun discoverToken(): String {
        val f = pairingFile()
        return try {
            if (f.isFile) sanitize(String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) else ""
        } catch (e: Exception) {
            LyricsLogger.warn("Salt token discover failed: {}", e.toString())
            ""
        }
    }

    private fun readTokenFile(): String = try {
        if (Files.exists(tokenFile)) String(Files.readAllBytes(tokenFile), StandardCharsets.UTF_8).trim() else ""
    } catch (e: Exception) {
        LyricsLogger.warn("Salt token read failed: {}", e.toString())
        ""
    }

    fun saveToken(token: String): Boolean {
        val clean = sanitize(token)
        try {
            Files.createDirectories(tokenFile.parent)
            Files.write(tokenFile, clean.toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            LyricsLogger.warn("Salt token save failed: {}", e.javaClass.simpleName)
            return false
        }
        cachedToken = clean
        return true
    }

    fun clearToken() {
        saveToken("")
    }

    /** 令牌失效（401）后丢弃缓存，下次重新自动发现 */
    fun invalidate() {
        cachedToken = null
    }

    /** 粘贴入口：只留可见 ASCII，限长；令牌本身不进日志 */
    fun sanitize(raw: String): String =
        raw.trim().filter { it.code in 33..126 }.take(128)

    /** 界面遮罩：只显示末 4 位 */
    fun mask(token: String): String = when {
        token.isEmpty() -> ""
        token.length <= 4 -> "••••"
        else -> "••••" + token.takeLast(4)
    }
}
