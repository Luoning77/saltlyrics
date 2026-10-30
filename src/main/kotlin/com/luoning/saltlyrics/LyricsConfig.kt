package com.luoning.saltlyrics

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import com.luoning.saltlyrics.playback.salt.SaltConfig
import java.nio.file.Files

/** One config for settings; credentials remain in a separate local file. */
object LyricsConfig {
    val directory get() = FabricLoader.getInstance().configDir.resolve("saltlyrics")
    var enabled = true
    var scale = 1f
    var distance = 10f
    var spread = 30f
    var facePlayer = true
    var keepAway = true
    var stayOnScreen = false
    var aboveGround = false
    var splitLines = true
    var showTranslations = true
    var wordPop = true
    var wiggle = false
    var wiggleFps = 8
    var wiggleAmount = 0.8f
    var glow = true
    var glowStrength = 0.3f
    var glowRadius = 0.5f
    var glowColor = 0
    var textRgb = 0xF7E1D7
    var offsetMs = 0L
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        val file = directory.resolve("settings.json")
        if (!Files.exists(file)) return
        try {
            val o = Files.newBufferedReader(file).use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            fun b(k: String, d: Boolean) = o.get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: d
            fun n(k: String, d: Float, lo: Float, hi: Float): Float {
                val v = o.get(k)?.asFloat ?: d
                return if (v.isFinite()) v.coerceIn(lo,hi) else d
            }
            enabled=b("enabled",enabled); facePlayer=b("facePlayer",facePlayer)
            keepAway=b("keepAway",keepAway); stayOnScreen=b("stayOnScreen",stayOnScreen)
            aboveGround=b("aboveGround",aboveGround); splitLines=b("splitLines",splitLines)
            showTranslations=b("showTranslations",showTranslations)
            wordPop=b("wordPop",wordPop); wiggle=b("wiggle",wiggle); glow=b("glow",glow)
            scale=n("scale",scale,0.5f,3f); distance=n("distance",distance,3f,40f)
            spread=n("spread",spread,0f,60f); wiggleAmount=n("wiggleAmount",wiggleAmount,0f,2f)
            wiggleFps=n("wiggleFps",8f,4f,24f).toInt()
            glowStrength=n("glowStrength",glowStrength,0f,3f); glowRadius=n("glowRadius",glowRadius,0f,1f)
            glowColor=n("glowColor",0f,0f,3f).toInt()
            textRgb=n("textRgb",textRgb.toFloat(),0f,16777215f).toInt()
            offsetMs=n("offsetMs",0f,-5000f,5000f).toLong()
            SaltConfig.port=n("port",8737f,1f,65535f).toInt()
        } catch (e: Exception) { LyricsLogger.warn("Cannot read SaltLyrics settings: {}",e.javaClass.simpleName) }
    }

    fun save(): Boolean = try {
        Files.createDirectories(directory)
        val values = linkedMapOf<String,Any>(
            "enabled" to enabled,"scale" to scale,"distance" to distance,"spread" to spread,
            "facePlayer" to facePlayer,"keepAway" to keepAway,"stayOnScreen" to stayOnScreen,
            "aboveGround" to aboveGround,"splitLines" to splitLines,"showTranslations" to showTranslations,"wordPop" to wordPop,
            "wiggle" to wiggle,"wiggleFps" to wiggleFps,"wiggleAmount" to wiggleAmount,
            "glow" to glow,"glowStrength" to glowStrength,"glowRadius" to glowRadius,
            "glowColor" to glowColor,"textRgb" to textRgb,"offsetMs" to offsetMs,"port" to SaltConfig.port)
        val temp = directory.resolve("settings.json.tmp")
        Files.newBufferedWriter(temp).use { gson.toJson(values,it) }
        Files.move(temp,directory.resolve("settings.json"),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (e: Exception) {
        LyricsLogger.warn("Cannot save SaltLyrics settings: {}",e.javaClass.simpleName)
        false
    }
}
