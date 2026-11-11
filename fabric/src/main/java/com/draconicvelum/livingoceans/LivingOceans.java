// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

public final class LivingOceans implements ModInitializer {
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("livingoceans.json5");
    private static volatile Config config = new Config();
    public static Config runtimeConfig() { return config; }
    private final GrowthScheduler scheduler = new GrowthScheduler();
    private static LivingOceans instance;
    /** Called on the integrated server thread, or with no local server running. */
    public static void applyConfiguration(Config updated){updated.validate();config=updated;if(instance!=null)instance.scheduler.clearDensityCache();}
    GrowthScheduler diagnostics() { return scheduler; }
    private void reload() throws Exception { config = Config.load(path);scheduler.clearDensityCache(); }
    @Override public void onInitialize() { instance=this;
        ColonyOrigins.init();
        KelpHeightLimits.init();
        try { reload(); } catch (Exception e) {
            config.enabled = false;
            LoggerFactory.getLogger("livingoceans").error("Invalid configuration; ecology disabled", e);
        }
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->scheduler.chunkLoaded(level,chunk.getPos()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk)->scheduler.chunkUnloaded(level,chunk.getPos()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level,chunk,oldStatus,newStatus)->{
            if(newStatus==net.minecraft.server.level.FullChunkStatus.INACCESSIBLE)scheduler.chunkUnloaded(level,chunk.getPos());
            else scheduler.chunkLoaded(level,chunk.getPos());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> scheduler.tick(server, config));
        ServerLifecycleEvents.SERVER_STARTED.register(VanillaReefPlans::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{if(success)VanillaReefPlans.load(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {scheduler.reset();KelpHeightLimits.reset();VanillaReefPlans.clear();});
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(Commands.literal("livingoceans")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(scheduler.status(config)), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(context -> {
                    try { reload(); context.getSource().sendSuccess(() -> Component.literal("Living Oceans configuration reloaded."), true); return 1; }
                    catch (Exception e) { context.getSource().sendFailure(Component.literal("Reload rejected; previous configuration retained: " + e.getMessage())); return 0; }
                }))));
    }
}
