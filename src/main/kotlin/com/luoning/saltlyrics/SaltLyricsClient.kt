package com.luoning.saltlyrics

import com.luoning.saltlyrics.lyrics.WorldLyrics
import com.luoning.saltlyrics.playback.salt.SaltSessionSource
import com.luoning.saltlyrics.ui.LyricsSettingsScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.OptionsScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class SaltLyricsClient : ClientModInitializer {
    companion object {
        val session=SaltSessionSource()
        val renderer=WorldLyrics()
        @JvmStatic fun renderWorld() { renderer.render() }
    }
    override fun onInitializeClient() {
        LyricsConfig.load()
        session.start()
        ScreenEvents.AFTER_INIT.register { mc,screen,width,_ ->
            if(screen is OptionsScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.translatable("saltlyrics.settings")) {
                    mc.setScreen(LyricsSettingsScreen(screen))
                }.bounds(width-106,6,100,20).build())
            }
        }
        val toggle=KeyBindingHelper.registerKeyBinding(KeyMapping("key.saltlyrics.toggle",GLFW.GLFW_KEY_UNKNOWN,"key.categories.saltlyrics"))
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            while(toggle.consumeClick()) {
                LyricsConfig.enabled=!LyricsConfig.enabled
                renderer.clear()
                LyricsConfig.save()
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { session.stop() }
    }
}
