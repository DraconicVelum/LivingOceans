// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans.mixin;

import com.draconicvelum.livingoceans.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KelpBlock.class)
abstract class KelpPlacementMixin {
    @Inject(method="getStateForPlacement",at=@At("HEAD"),cancellable=true)
    private void livingoceans$stacking(BlockPlaceContext context,CallbackInfoReturnable<BlockState> cir){
        var c=LivingOceans.runtimeConfig();
        if(context.getPlayer()!=null&&!c.kelpHeights.playerStackingBypassesLimit&&context.getLevel() instanceof ServerLevel level){
            var below=context.getClickedPos().below();
            if(level.getBlockState(below).is(Blocks.KELP)&&!KelpHeightLimits.canGrow(level,below,c))cir.setReturnValue(null);
        }
    }
}
