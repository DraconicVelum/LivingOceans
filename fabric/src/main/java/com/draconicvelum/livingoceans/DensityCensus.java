// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Bounded rolling floor-column census. No world/chunk enumeration or chunk loading. */
final class DensityCensus {
    private static final int MAX_CELLS=1024;
    private record Key(ServerLevel level,int x,int z) { }
    private static final class Cell {
        final Key key;final byte[] kinds=new byte[256];final int[] counts=new int[4];int cursor,scanned;
        Cell(Key key){this.key=key;}
        void set(int i,int flags){
            int old=kinds[i];if(old==flags)return;
            for(int species=1;species<=3;species++){
                int bit=1<<(species-1);
                if((old&bit)!=(flags&bit))counts[species]+=(flags&bit)!=0?1:-1;
            }
            kinds[i]=(byte)flags;
        }
    }
    private final LinkedHashMap<Key,Cell> cells=new LinkedHashMap<>(64,0.75f,true);
    private final ArrayDeque<Cell> refresh=new ArrayDeque<>();
    void clear(){cells.clear();refresh.clear();}
    int size(){return cells.size();}
    private Cell cell(ServerLevel level,BlockPos p){
        Key key=new Key(level,p.getX()>>4,p.getZ()>>4);Cell c=cells.get(key);
        if(c!=null)return c;
        if(cells.size()>=MAX_CELLS){var it=cells.entrySet().iterator();Cell old=it.next().getValue();it.remove();refresh.remove(old);}
        c=new Cell(key);cells.put(key,c);refresh.addLast(c);return c;
    }
    private static int index(BlockPos p){return(p.getX()&15)|((p.getZ()&15)<<4);}
    private static int kind(net.minecraft.world.level.block.state.BlockState state){
        Block b=state.getBlock();
        if(b==Blocks.SEAGRASS||b==Blocks.TALL_SEAGRASS)return 1;
        if(b==Blocks.KELP||b==Blocks.KELP_PLANT)return 2;
        if(b instanceof CoralPlantBlock||b instanceof CoralFanBlock||b instanceof CoralWallFanBlock||b instanceof CoralBlock)return 3;
        return 0;
    }
    private static boolean reefBlock(net.minecraft.world.level.block.state.BlockState state){
        return state.getBlock() instanceof CoralBlock||state.is(Blocks.DEAD_TUBE_CORAL_BLOCK)||state.is(Blocks.DEAD_BRAIN_CORAL_BLOCK)
            ||state.is(Blocks.DEAD_BUBBLE_CORAL_BLOCK)||state.is(Blocks.DEAD_FIRE_CORAL_BLOCK)||state.is(Blocks.DEAD_HORN_CORAL_BLOCK);
    }
    private boolean sample(Cell c,OperationBudget budget){
        var l=c.key.level();if(!l.hasChunk(c.key.x(),c.key.z())||!budget.take(3))return false;
        int i=c.cursor,x=(c.key.x()<<4)+(i&15),z=(c.key.z()<<4)+(i>>4);
        int y=l.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);BlockPos p=new BlockPos(x,y,z);
        var state=l.getBlockState(p);int type=kind(state),flags=type==0?0:1<<(type-1);
        // A reef reaching the surface still occupies its column, even with air or grass above it.
        if(reefBlock(l.getBlockState(p.below())))flags|=4|(c.kinds[i]&3); // Conservatively retain known vegetation beneath a solid reef canopy.
        c.set(i,flags);c.cursor=(i+1)&255;c.scanned=Math.min(256,c.scanned+1);return true;
    }
    void advance(OperationBudget budget){
        Cell c=refresh.pollFirst();if(c==null)return;
        if(!c.key.level().hasChunk(c.key.x(),c.key.z())){cells.remove(c.key);return;}
        sample(c,budget);refresh.addLast(c);
    }
    boolean allows(ServerLevel level,BlockPos p,int species,double density,OperationBudget budget){
        Cell c=cell(level,p);
        if(c.scanned<256){while(c.scanned<256&&sample(c,budget)){}return false;}
        int limit=(int)Math.floor(density*256);
        return c.counts[species]-((c.kinds[index(p)]&(1<<(species-1)))!=0?1:0)<limit;
    }
    boolean mature(ServerLevel level,BlockPos p,double density,double threshold,OperationBudget budget){
        Cell c=cell(level,p);
        if(c.scanned<256){while(c.scanned<256&&sample(c,budget)){}return false;}
        return c.counts[3]>=Math.ceil(Math.floor(density*256)*threshold);
    }
    void observed(ServerLevel level,BlockPos p,int species){
        Cell c=cells.get(new Key(level,p.getX()>>4,p.getZ()>>4));
        if(c!=null)c.set(index(p),c.kinds[index(p)]|(1<<(species-1)));
    }
    boolean allowsDecoration(ServerLevel level,BlockPos target,BlockPos anchor,VanillaReefPlans.Plan plan,double density){
        Cell cell=cells.get(new Key(level,target.getX()>>4,target.getZ()>>4));
        if(cell==null||cell.scanned<256)return false;
        int reserved=0;boolean targetReserved=false;
        for(BlockPos offset:plan.footprint()){
            int x=anchor.getX()+offset.getX(),z=anchor.getZ()+offset.getZ();
            if((x>>4)!=(target.getX()>>4)||(z>>4)!=(target.getZ()>>4))continue;
            if((cell.kinds[(x&15)|((z&15)<<4)]&4)==0){reserved++;if(x==target.getX()&&z==target.getZ())targetReserved=true;}
        }
        int additional=(cell.kinds[index(target)]&4)!=0?0:1;
        return cell.counts[3]+reserved+additional-(targetReserved?1:0)<=(int)Math.floor(density*256);
    }
}
