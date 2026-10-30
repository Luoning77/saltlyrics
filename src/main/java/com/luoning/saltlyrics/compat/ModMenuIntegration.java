package com.luoning.saltlyrics.compat;

import com.luoning.saltlyrics.ui.LyricsSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded by Mod Menu only; the regular client entrypoint has no Mod Menu dependency. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LyricsSettingsScreen::new;
    }
}
