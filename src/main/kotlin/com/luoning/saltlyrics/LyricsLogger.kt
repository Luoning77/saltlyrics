// SPDX-License-Identifier: GPL-3.0-only
// Derived from DangoMusic by Sorihiko and contributors; modified 2026-10-30.
// See THIRD_PARTY_NOTICES.md for source paths and revision.
package com.luoning.saltlyrics

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class LyricsLogger {
    companion object {
        private val logger: Logger = LogManager.getLogger("SaltLyrics")

        @JvmStatic
        fun info(msg: String, vararg params: Any) {
            logger.info(msg, *params)
        }

        @JvmStatic
        fun warn(msg: String, vararg params: Any) {
            logger.warn(msg, *params)
        }

        @JvmStatic
        fun error(msg: String, vararg params: Any) {
            logger.error(msg, *params)
        }
    }
}