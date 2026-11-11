// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;
/** Loader boundary; no attachment synchronization is requested. */
final class PlatformAttachment<T> {
    static final DeferredRegister<AttachmentType<?>> REGISTRY=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,"livingoceans");
    private final java.util.function.Supplier<AttachmentType<T>> type;
    private PlatformAttachment(Identifier id,Codec<T> codec){
        type=REGISTRY.register(id.getPath(),()->AttachmentType.<T>builder(()->{throw new IllegalStateException("Attachment requires an explicit fallback");}).serialize(codec.fieldOf("value")).build());
    }
    static <T> PlatformAttachment<T> createPersistent(Identifier id,Codec<T> codec){return new PlatformAttachment<>(id,codec);}
    T get(LevelChunk chunk,T fallback){T value=chunk.getExistingDataOrNull(type);return value==null?fallback:value;}
    void set(LevelChunk chunk,T value){chunk.setData(type,value);chunk.markUnsaved();}
}
