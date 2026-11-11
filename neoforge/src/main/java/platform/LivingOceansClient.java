// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans.client;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
@Mod(value="livingoceans",dist=Dist.CLIENT)
public final class LivingOceansClient {
    public LivingOceansClient(ModContainer container){
        container.registerExtensionPoint(IConfigScreenFactory.class,(mod,parent)->new OceanConfigScreen(parent,FMLPaths.CONFIGDIR.get().resolve("livingoceans.json5")));
    }
}
