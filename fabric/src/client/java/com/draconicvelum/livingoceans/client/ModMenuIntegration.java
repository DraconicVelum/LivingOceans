// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans.client;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.fabricmc.loader.api.FabricLoader;
public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory(){
        return parent->new OceanConfigScreen(parent,FabricLoader.getInstance().getConfigDir().resolve("livingoceans.json5"));
    }
}
