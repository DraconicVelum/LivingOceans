// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import java.lang.reflect.Proxy;
import java.util.*;

/** Small immutable offset templates, compiled by the actual vanilla generators in a virtual water volume. */
final class VanillaReefPlans {
    record Plan(String family,List<Map.Entry<BlockPos,BlockState>> blocks,List<Map.Entry<BlockPos,BlockState>> decoration,List<BlockPos> parents,List<BlockPos> footprint) {
        boolean contains(BlockPos offset){return blocks.stream().anyMatch(e->e.getKey().equals(offset));}
    }
    private static List<Plan> plans=List.of();
    static void clear(){plans=List.of();}
    static void load(MinecraftServer server){
        List<Plan> baked=new ArrayList<>();ServerLevel level=server.overworld();
        var feature=server.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(Identifier.withDefaultNamespace("warm_ocean_vegetation"));
        if(!(feature instanceof SimpleRandomSelectorFeature selector)){clear();return;}
        var branches=selector.features();
        for(int i=0;i<Math.min(15,branches.size());i++)for(int variant=0;variant<4;variant++){
            LinkedHashMap<BlockPos,BlockState> writes=new LinkedHashMap<>();int[] calls={0};
            var anchor=new BlockPos(0,64,0);
            RandomSource random=RandomSource.create(0x4c6976696e674fL+i*31L+variant*997L);
            WorldGenLevel virtual=(WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,method,args)->{
                if(++calls[0]>4096)throw new IllegalStateException("Coral generator exceeded virtual call budget");
                String name=method.getName();
                if(name.equals("getBlockState")||name.equals("getFluidState")||name.equals("isEmptyBlock")){
                    BlockPos p=(BlockPos)args[0];
                    BlockState state=writes.getOrDefault(p,p.getY()<64?Blocks.SAND.defaultBlockState():Blocks.WATER.defaultBlockState());
                    return name.equals("getFluidState")?state.getFluidState():name.equals("isEmptyBlock")?state.isAir():state;
                }
                if(name.equals("setBlock")){
                    BlockPos p=((BlockPos)args[0]).immutable();
                    if(writes.size()>=256||Math.abs(p.getX())>12||Math.abs(p.getZ())>12||p.getY()<60||p.getY()>80)throw new IllegalStateException("Coral generator exceeded virtual volume");
                    writes.put(p,(BlockState)args[1]);return true;
                }
                if(name.equals("getChunk")||name.equals("getChunkSource"))throw new IllegalStateException("Coral template attempted chunk access");
                if(name.equals("scheduleTick")||name.equals("setCurrentlyGenerating"))return null;
                if(name.equals("ensureCanWrite"))return true;
                if(name.equals("getRandom"))return random;
                if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
                if(Set.of("getMinY","getMaxY","getHeight","getSeed","registryAccess","enabledFeatures","dimensionType").contains(name)&&(args==null||args.length==0))return method.invoke(level,args);
                throw new IllegalStateException("Unsupported virtual coral operation: "+name);
            });
            try{
                branches.get(i).value().place(virtual,level.getChunkSource().getGenerator(),random,anchor);
                List<Map.Entry<BlockPos,BlockState>> body=new ArrayList<>(),decoration=new ArrayList<>();
                for(var e:writes.entrySet()){
                    BlockPos offset=e.getKey().subtract(anchor);BlockState state=e.getValue();
                    if(state.getBlock() instanceof CoralBlock)body.add(Map.entry(offset,state));
                    else if(state.getBlock() instanceof CoralPlantBlock||state.getBlock() instanceof CoralFanBlock||state.getBlock() instanceof CoralWallFanBlock||state.is(Blocks.SEA_PICKLE))decoration.add(Map.entry(offset,state));
                }
                if(body.size()<4)continue;
                // Hollow vanilla cuboids may omit their centre: retain the mod's grounded source foundation.
                if(body.stream().noneMatch(e->e.getKey().equals(BlockPos.ZERO)))body.addFirst(Map.entry(BlockPos.ZERO,body.getFirst().getValue()));
                List<BlockPos> parents=new ArrayList<>();Set<BlockPos> footprint=new LinkedHashSet<>();
                for(int child=0;child<body.size();child++){
                    BlockPos position=body.get(child).getKey(),parent=position.below();int best=10;
                    for(int previous=0;previous<child;previous++){
                        BlockPos candidate=body.get(previous).getKey();int distance=position.distManhattan(candidate);
                        if(distance<best&&Math.abs(position.getX()-candidate.getX())<=2&&Math.abs(position.getY()-candidate.getY())<=2&&Math.abs(position.getZ()-candidate.getZ())<=2){parent=candidate;best=distance;}
                    }
                    parents.add(parent);footprint.add(new BlockPos(position.getX(),0,position.getZ()));
                }
                baked.add(new Plan(i%3==0?"tree":i%3==1?"claw":"cuboid",List.copyOf(body),List.copyOf(decoration),List.copyOf(parents),List.copyOf(footprint)));
            }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger("livingoceans").warn("Skipped bounded vanilla coral template {}:{}: {}",i,variant,error.toString());}
        }
        plans=List.copyOf(baked);
        org.slf4j.LoggerFactory.getLogger("livingoceans").info("Compiled {} bounded templates from the vanilla warm-ocean generator",plans.size());
    }
    static Plan select(BlockPos origin,BlockState species){
        if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(species.getBlock()).getNamespace().equals("minecraft"))return null;
        String name=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(species.getBlock()).getPath().replace("_wall_fan", "").replace("_fan", "").replace("_block", "");
        List<Plan> matching=plans.stream().filter(p->net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.blocks.getFirst().getValue().getBlock()).getPath().equals(name+"_block")).toList();
        return matching.isEmpty()?null:matching.get((int)Math.floorMod(origin.getX()*31L+origin.getZ()*17L,matching.size()));
    }
}
