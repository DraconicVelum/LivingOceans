// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Called only after successful player placement; the bounded local search never loads a chunk. */
public final class PlayerPlanting {
    public static void placed(ServerLevel level,BlockPos position,boolean shift){
        Config c=LivingOceans.runtimeConfig();if(!c.enabled)return;
        int species=GrowthScheduler.kind(level.getBlockState(position));if(species==0)return;
        Config.Species policy=species==1?c.seagrass:species==2?c.kelp:c.coral;
        if(!policy.enabled)return;
        BlockPos own=new BlockPos(position.getX(),0,position.getZ());
        if(shift&&c.shiftPlantingStartsNewColony){ColonyOrigins.assign(level,position,own);return;}
        if(GrowthScheduler.kind(level.getBlockState(position.below()))==species){
            BlockPos existing=ColonyOrigins.origin(level,position);
            if(ColonyOrigins.within(position,existing,policy.maxColonyRadius)){ColonyOrigins.assign(level,position,existing);return;}
        }
        // Visit nearby columns in expanding rings. Stop at the first healthy same-species colony covering the placement.
        int radius=policy.maxColonyRadius;
        for(int ring=1;ring<=radius;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring||dx*dx+dz*dz>radius*radius)continue;
            int x=position.getX()+dx,z=position.getZ()+dz;
            if(!level.hasChunk(x>>4,z>>4))continue;
            BlockPos source=new BlockPos(x,level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z),z);
            var state=level.getBlockState(source);int type=GrowthScheduler.kind(state);
            if(type==0){
                var below=level.getBlockState(source.below());
                if(below.getBlock() instanceof CoralBlock){source=source.below();state=below;type=3;}
            }
            if(type!=species||!(state.getBlock() instanceof CoralBlock||state.getFluidState().is(FluidTags.WATER)))continue;
            BlockPos origin=ColonyOrigins.origin(level,source);
            if(!ColonyOrigins.within(source,origin,radius)||!ColonyOrigins.within(position,origin,radius))continue;
            ColonyOrigins.assign(level,source,origin);ColonyOrigins.assign(level,position,origin);return;
        }
        ColonyOrigins.assign(level,position,own);
    }
}
