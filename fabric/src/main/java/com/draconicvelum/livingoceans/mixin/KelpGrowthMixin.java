// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans.mixin;

import com.draconicvelum.livingoceans.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only gates kelp, preserving the original random-tick probability and other growing plants. */
@Mixin(GrowingPlantHeadBlock.class)
abstract class KelpGrowthMixin {
    @Inject(method="isValidBonemealTarget",at=@At("HEAD"),cancellable=true)
    private void livingoceans$bonemealTarget(net.minecraft.world.level.LevelReader reader,BlockPos pos,BlockState state,BonemealSource source,CallbackInfoReturnable<Boolean> cir){
        var c=LivingOceans.runtimeConfig();
        if((Object)this instanceof KelpBlock&&!c.kelpHeights.bonemealBypassesLimit&&reader instanceof ServerLevel level&&!KelpHeightLimits.canGrow(level,pos,c))cir.setReturnValue(false);
    }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void livingoceans$natural(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci){
        if((Object)this instanceof KelpBlock&&!KelpHeightLimits.canGrow(level,pos,LivingOceans.runtimeConfig()))ci.cancel();
    }
    @Inject(method="performBonemeal",at=@At("HEAD"),cancellable=true)
    private void livingoceans$bonemeal(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,BonemealSource source,CallbackInfo ci){
        var c=LivingOceans.runtimeConfig();
        if((Object)this instanceof KelpBlock&&!c.kelpHeights.bonemealBypassesLimit&&!KelpHeightLimits.canGrow(level,pos,c))ci.cancel();
    }
}
