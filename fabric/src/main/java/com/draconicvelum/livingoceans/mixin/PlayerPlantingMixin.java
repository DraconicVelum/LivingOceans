// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans.mixin;
import com.draconicvelum.livingoceans.PlayerPlanting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The public use-block event runs before success; recording here avoids creating sources for failed placements. */
@Mixin(BlockItem.class)
abstract class PlayerPlantingMixin {
    @Inject(method="place",at=@At("RETURN"))
    private void livingoceans$source(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> cir){
        if(context.getPlayer()!=null&&context.getLevel() instanceof ServerLevel level&&cir.getReturnValue().consumesAction())
            PlayerPlanting.placed(level,context.getClickedPos(),context.getPlayer().isShiftKeyDown());
    }
}
