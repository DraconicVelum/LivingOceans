// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import com.mojang.serialization.Codec;


import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Fixed 512-byte packed payload per touched chunk; no growing position list or world scan. */
public final class KelpHeightLimits {
    private static net.minecraft.server.MinecraftServer lastServer;
    private static int lastTick,initializations;
    private static final List<Long> EMPTY=Collections.nCopies(64,0L);
    private static final PlatformAttachment<List<Long>> CAPS=PlatformAttachment.createPersistent(
        Identifier.fromNamespaceAndPath("livingoceans","kelp_height_caps_v1"),
        Codec.LONG.listOf().validate(v->v.size()==64?com.mojang.serialization.DataResult.success(v):com.mojang.serialization.DataResult.error(()->"Expected 64 kelp height words")));
    public static void init() { }
    public static void reset(){lastServer=null;initializations=0;}
    public static boolean active(Config c){return c.enabled&&c.kelp.enabled&&c.kelpHeights.enabled;}
    public static boolean canGrow(ServerLevel level,BlockPos head,Config c){
        return !active(c)||head.getY()<ceiling(level,head,c);
    }
    static int ceiling(ServerLevel level,BlockPos head,Config c){
        var chunk=level.getChunkSource().getChunkNow(head.getX()>>4,head.getZ()>>4);
        if(chunk==null)return head.getY();
        int index=(head.getX()&15)|((head.getZ()&15)<<4),shift=(index&3)*16;
        var words=CAPS.get(chunk,EMPTY);
        int value=(int)((words.get(index>>2)>>>shift)&65535);
        if(value!=0)return level.getMinY()+value-1;
        if(lastServer!=level.getServer()||lastTick!=level.getServer().getTickCount()){
            lastServer=level.getServer();lastTick=lastServer.getTickCount();initializations=0;
        }
        // Defer a new natural growth event instead of doing an unbounded burst of initializations.
        if(initializations>=c.kelpHeights.maxInitializationsPerTick)return head.getY();
        initializations++;
        int bottom=level.getHeight(Heightmap.Types.OCEAN_FLOOR,head.getX(),head.getZ());
        // Heightmaps give O(1) roots in natural ocean water. A roof requires a bounded local fallback.
        if(bottom>head.getY()||!kelp(level.getBlockState(new BlockPos(head.getX(),bottom,head.getZ())))){
            bottom=head.getY();
            int descent=0;
            while(bottom>level.getMinY()&&descent++<64&&kelp(level.getBlockState(new BlockPos(head.getX(),bottom-1,head.getZ()))))bottom--;
            // Exceptionally tall roofed farms are left to vanilla; no growing root-search loop.
            if(bottom>level.getMinY()&&kelp(level.getBlockState(new BlockPos(head.getX(),bottom-1,head.getZ()))))return level.getMaxY();
        }
        int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,head.getX(),head.getZ())-1;
        int available=Math.max(1,surface-bottom+1);
        int maximum=c.kelpHeights.maximumHeight==0?available:Math.min(available,c.kelpHeights.maximumHeight);
        int minimum=Math.min(c.kelpHeights.minimumHeight,maximum);
        int height=minimum+level.getRandom().nextInt(maximum-minimum+1);
        int cap=bottom+height-1,code=cap-level.getMinY()+1;
        if(code<1||code>65535)return head.getY();
        Long[] changed=words.toArray(Long[]::new);
        changed[index>>2]=(changed[index>>2]&~(65535L<<shift))|((long)code<<shift);
        CAPS.set(chunk,List.of(changed));
        return cap;
    }
    private static boolean kelp(net.minecraft.world.level.block.state.BlockState state){return state.is(Blocks.KELP)||state.is(Blocks.KELP_PLANT);}
}
