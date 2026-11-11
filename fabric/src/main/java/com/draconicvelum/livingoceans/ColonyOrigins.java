// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;


import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Compact per-column lineage metadata; colony behaviour is bounded around an inherited origin. */
public final class ColonyOrigins {
    private static final List<Long> EMPTY=Collections.nCopies(64,0L);
    private static final PlatformAttachment<List<Long>> TYPE=PlatformAttachment.createPersistent(
        Identifier.fromNamespaceAndPath("livingoceans","colony_origins_v1"),
        Codec.LONG.listOf().validate(v->v.size()==64?DataResult.success(v):DataResult.error(()->"Expected 64 colony words")));
    public static void init() { }
    private static int index(BlockPos p){return (p.getX()&15)|((p.getZ()&15)<<4);}
    private static int code(ServerLevel l,BlockPos p){
        var chunk=l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
        if(chunk==null)return 0;
        var words=TYPE.get(chunk,null);int i=index(p);
        return words==null?0:(int)((words.get(i>>2)>>>((i&3)*16))&65535);
    }
    static boolean known(ServerLevel l,BlockPos p){return code(l,p)!=0;}
    static BlockPos origin(ServerLevel l,BlockPos p){
        int value=code(l,p);
        if(value==0)return new BlockPos(p.getX(),0,p.getZ());
        value--;
        return new BlockPos(p.getX()+((value>>6)&63)-32,0,p.getZ()+(value&63)-32);
    }
    static boolean within(BlockPos p,BlockPos origin,int radius){long dx=(long)p.getX()-origin.getX(),dz=(long)p.getZ()-origin.getZ();return dx*dx+dz*dz<=(long)radius*radius;}
    static void assign(ServerLevel l,BlockPos p,BlockPos origin){
        int dx=origin.getX()-p.getX(),dz=origin.getZ()-p.getZ();
        if(Math.abs(dx)>31||Math.abs(dz)>31)throw new IllegalArgumentException("Colony origin offset out of bounds");
        var chunk=l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
        if(chunk==null)throw new IllegalStateException("Colony chunk unloaded during server-thread placement");
        int i=index(p),shift=(i&3)*16,value=1+((dx+32)<<6)+(dz+32);
        var old=TYPE.get(chunk,EMPTY);
        if(((old.get(i>>2)>>>shift)&65535)==value)return;
        Long[] words=old.toArray(Long[]::new);
        words[i>>2]=(words[i>>2]&~(65535L<<shift))|((long)value<<shift);
        TYPE.set(chunk,List.of(words));
    }
}
