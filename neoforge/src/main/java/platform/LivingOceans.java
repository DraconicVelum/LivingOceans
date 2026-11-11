// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
@Mod("livingoceans")
public final class LivingOceans {
 private final java.nio.file.Path path=FMLPaths.CONFIGDIR.get().resolve("livingoceans.json5");
 private static volatile Config config=new Config();
 public static Config runtimeConfig(){return config;}
 private final GrowthScheduler scheduler=new GrowthScheduler();
    private static LivingOceans instance;
    /** Called on the integrated server thread, or with no local server running. */
    public static void applyConfiguration(Config updated){updated.validate();config=updated;if(instance!=null)instance.scheduler.clearDensityCache();}
 private void reload()throws Exception{config=Config.load(path);scheduler.clearDensityCache();}
 public LivingOceans(IEventBus modBus){ instance=this;
  ColonyOrigins.init();KelpHeightLimits.init();PlatformAttachment.REGISTRY.register(modBus);
  try{reload();}catch(Exception e){config.enabled=false;org.slf4j.LoggerFactory.getLogger("livingoceans").error("Invalid configuration; ecology disabled",e);}
  NeoForge.EVENT_BUS.addListener(this::tick);
  NeoForge.EVENT_BUS.addListener(this::loaded);
  NeoForge.EVENT_BUS.addListener(this::unloaded);
  NeoForge.EVENT_BUS.addListener(this::started);
  NeoForge.EVENT_BUS.addListener(this::stopped);
  NeoForge.EVENT_BUS.addListener(this::datapacks);
  NeoForge.EVENT_BUS.addListener(this::commands);
 }
 private void tick(ServerTickEvent.Post e){scheduler.tick(e.getServer(),config);}
 private void loaded(ChunkEvent.Load e){if(e.getLevel() instanceof ServerLevel l){var pos=e.getChunk().getPos();l.getServer().execute(()->{if(l.hasChunk(pos.x(),pos.z()))scheduler.chunkLoaded(l,pos);});}}
 private void unloaded(ChunkEvent.Unload e){if(e.getLevel() instanceof ServerLevel l){var pos=e.getChunk().getPos();l.getServer().execute(()->scheduler.chunkUnloaded(l,pos));}}
 private void started(ServerStartedEvent e){VanillaReefPlans.load(e.getServer());}
 private void stopped(ServerStoppedEvent e){scheduler.reset();KelpHeightLimits.reset();VanillaReefPlans.clear();}
 private void datapacks(OnDatapackSyncEvent e){if(e.getPlayer()==null)VanillaReefPlans.load(e.getPlayerList().getServer());}
 private void commands(RegisterCommandsEvent event){var dispatcher=event.getDispatcher();dispatcher.register(Commands.literal("livingoceans")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(scheduler.status(config)), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(context -> {
                    try { reload(); context.getSource().sendSuccess(() -> Component.literal("Living Oceans configuration reloaded."), true); return 1; }
                    catch (Exception e) { context.getSource().sendFailure(Component.literal("Reload rejected; previous configuration retained: " + e.getMessage())); return 0; }
                })));
 }
}
