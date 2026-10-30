package com.luoning.saltlyrics.ui

import com.luoning.saltlyrics.LyricsConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.roundToInt

/** RGB controls preview the same packed color that the world renderer reads. */
class RgbColorScreen(private val parent: Screen) : Screen(tr("saltlyrics.rgb.title")) {
    companion object {
        private fun tr(key: String, vararg args: Any): Component = Component.translatable(key, *args)
        private val shifts = intArrayOf(16, 8, 0)
        private val channelKeys = arrayOf("red", "green", "blue")
    }

    private val fields = mutableListOf<EditBox>()
    private var syncing = false
    private var saveFailed = false
    private var top = 48

    private fun channel(index: Int): Int = (LyricsConfig.textRgb ushr shifts[index]) and 255

    private fun setChannel(index: Int, value: Int) {
        val shift = shifts[index]
        LyricsConfig.textRgb = (LyricsConfig.textRgb and (255 shl shift).inv()) or
            (value.coerceIn(0, 255) shl shift)
    }

    override fun init() {
        fields.clear()
        top = (height / 2 - 66).coerceAtLeast(48)
        val left = width / 2 - 154
        for (index in shifts.indices) {
            val y = top + index * 28
            val field = EditBox(font, left + 252, y, 56, 20, tr("saltlyrics.rgb.${channelKeys[index]}"))
            field.setMaxLength(3)
            field.setFilter { text ->
                text.isEmpty() || (text.all { it in '0'..'9' } && (text.toIntOrNull() ?: 256) <= 255)
            }
            field.value = channel(index).toString()
            val slider = ChannelSlider(index, left, y)
            field.setResponder { text ->
                if (!syncing) text.toIntOrNull()?.let { value ->
                    setChannel(index, value)
                    slider.sync()
                }
            }
            fields += field
            addRenderableWidget(slider)
            addRenderableWidget(field)
        }
        addRenderableWidget(Button.builder(tr("gui.done")) { onClose() }
            .bounds(width / 2 - 100, height - 27, 200, 20).build())
    }

    private inner class ChannelSlider(private val index: Int, x: Int, y: Int) :
        AbstractSliderButton(x, y, 244, 20, Component.empty(), channel(index) / 255.0) {
        init { updateMessage() }

        override fun updateMessage() {
            message = tr("saltlyrics.rgb.channel", tr("saltlyrics.rgb.${channelKeys[index]}"),
                (value * 255).roundToInt())
        }

        override fun applyValue() {
            setChannel(index, (value * 255).roundToInt())
            syncing = true
            try { fields[index].value = channel(index).toString() } finally { syncing = false }
        }

        fun sync() {
            value = channel(index) / 255.0
            updateMessage()
        }
    }

    override fun tick() {
        fields.forEachIndexed { index, field ->
            field.tick()
            if (!field.isFocused && field.value.isEmpty()) field.value = channel(index).toString()
        }
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(g)
        g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)
        g.drawCenteredString(font, tr("saltlyrics.rgb.hint"), width / 2, top - 16, 0xCCCCCC)
        val left = width / 2 - 154
        val previewY = top + 86
        g.fill(left, previewY, left + 154, previewY + 28, 0xB0000000.toInt())
        g.fill(left + 154, previewY, left + 308, previewY + 28, 0xFFE0E0E0.toInt())
        g.drawCenteredString(font, tr("saltlyrics.rgb.preview"), left + 77, previewY + 10,
            0xFF000000.toInt() or LyricsConfig.textRgb)
        g.drawCenteredString(font, tr("saltlyrics.rgb.preview"), left + 231, previewY + 10,
            0xFF000000.toInt() or LyricsConfig.textRgb)
        val hex = String.format(Locale.ROOT, "#%06X", LyricsConfig.textRgb)
        g.drawCenteredString(font, hex, width / 2, previewY + 34, 0xDDDDDD)
        if (saveFailed) g.drawCenteredString(font, tr("saltlyrics.saveFailed"), width / 2, height - 41, 0xFF8888)
        super.render(g, mouseX, mouseY, delta)
    }

    override fun onClose() {
        if (!LyricsConfig.save()) { saveFailed = true; return }
        minecraft!!.setScreen(parent)
    }

    override fun isPauseScreen() = false
}
