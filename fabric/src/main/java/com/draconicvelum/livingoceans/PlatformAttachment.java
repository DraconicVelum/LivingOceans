// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import net.fabricmc.fabric.api.attachment.v1.*;
/** Loader boundary for persistent, unsynchronized chunk metadata. */
final class PlatformAttachment<T> {
    private final AttachmentType<T> type;
    private PlatformAttachment(Identifier id,Codec<T> codec){type=AttachmentRegistry.createPersistent(id,codec);}
    static <T> PlatformAttachment<T> createPersistent(Identifier id,Codec<T> codec){return new PlatformAttachment<>(id,codec);}
    T get(LevelChunk chunk,T fallback){return chunk.getAttachedOrElse(type,fallback);}
    void set(LevelChunk chunk,T value){chunk.setAttached(type,value);}
}
