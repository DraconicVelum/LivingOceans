// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/** Per-chunk ecological scheduling with bounded, loaded-only server-thread work. */
public final class GrowthScheduler {
    private static final TagKey<Biome> ALLOWED = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("livingoceans", "growth_allowed"));
    private static final TagKey<Biome> DENIED = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("livingoceans", "growth_denied"));
    private static final TagKey<Biome> REEFS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("livingoceans", "reefs_allowed"));
    private static final TagKey<Block> SUBSTRATE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("livingoceans", "natural_substrates"));
    private long ticks, attempts, changes, nanos, peakNanos;
    private int operations, peakOperations;
    private final DensityCensus density=new DensityCensus();
    private final ChunkGrowthQueue chunks=new ChunkGrowthQueue();
    void chunkLoaded(ServerLevel level,net.minecraft.world.level.ChunkPos pos){chunks.loaded(level,pos,ticks);}
    void chunkUnloaded(ServerLevel level,net.minecraft.world.level.ChunkPos pos){chunks.unloaded(level,pos);}
    long attemptsInChunk(ServerLevel level,net.minecraft.world.level.ChunkPos pos){return chunks.attempts(level,pos);}
    private record ReefRoot(ServerLevel level,int x,int z) { }
    private final java.util.LinkedHashMap<ReefRoot,BlockPos> foundations=new java.util.LinkedHashMap<>(64,0.75f,true);
    void clearDensityCache(){density.clear();foundations.clear();chunks.reschedule(ticks);}
    private long nativeSeeds,chancePassed,wetTargets,validSubstrates,densityRejected,radiusRejected;
    int operationsLastTick() { return operations; }
    long changesMade() { return changes; }
    long attemptsMade() { return attempts; }
    record Placement(BlockPos source,BlockPos target,BlockPos origin,int radius) { }
    java.util.function.Consumer<Placement> placementObserver;
    public void reset() { ticks = attempts = changes = nanos = peakNanos = 0; operations = peakOperations = 0; nativeSeeds=chancePassed=wetTargets=validSubstrates=densityRejected=radiusRejected=0;density.clear();foundations.clear();chunks.clear(); }
    public String status(Config c) {
        return "Living Oceans enabled=" + c.enabled + " coral=" + c.coral.enabled + " seagrass=" + c.seagrass.enabled
            + " kelp=" + c.kelp.enabled + " | session attempts=" + attempts + " changes=" + changes
            + " | operations last/peak/limit=" + operations + "/" + peakOperations + "/" + c.maxOperationsPerTick+" perChunkLimit="+c.maxOperationsPerChunk
            + " | last/peak ns=" + nanos + "/" + peakNanos + " | interval=" + c.intervalTicks
            + " attempts/chunk/cycle=" + c.attemptsPerChunk + " distance=" + c.maxDistance
            + " | seeds/chance/water/substrate="+nativeSeeds+"/"+chancePassed+"/"+wetTargets+"/"+validSubstrates
            + " densityRejected="+densityRejected+" colonyRadiusRejected="+radiusRejected
            + " reefGrowth="+c.reefGrowthEnabled+" reefMaturity="+c.reefMaturityThreshold
            + " "+chunks.status()+" configuredChunkRadius="+c.playerChunkRadius
            + " colonyRadii="+c.coral.maxColonyRadius+"/"+c.seagrass.maxColonyRadius+"/"+c.kelp.maxColonyRadius;
    }
    public void tick(MinecraftServer server, Config c) {
        operations = 0;
        if (!c.enabled) return;
        ticks++;
        long start = System.nanoTime();
        OperationBudget budget = new OperationBudget(c.maxOperationsPerTick);
        density.advance(budget);
        chunks.run(server,c,budget,ticks,(level,source,local)->{attempts++;tryGrow(level,source,c,local);});
        operations = budget.used(); peakOperations = Math.max(peakOperations, operations);
        nanos = System.nanoTime()-start; peakNanos = Math.max(peakNanos, nanos);
    }
    static int activeRadius(int configured,int simulation,int requested){return Math.max(0,Math.min(configured,Math.min(simulation,requested)));}
    private BlockState read(ServerLevel level, BlockPos pos, OperationBudget budget) {
        if (!level.isInWorldBounds(pos) || !budget.take() || !level.hasChunk(pos.getX()>>4, pos.getZ()>>4)) return null;
        return level.getBlockState(pos);
    }
    private static boolean water(BlockState s) { return s != null && s.is(Blocks.WATER) && s.getFluidState().isSource(); }
    static int kind(BlockState s) {
        if (s == null) return 0;
        if (s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS)) return 1;
        if (s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)) return 2;
        if (s.getBlock() instanceof CoralPlantBlock || s.getBlock() instanceof CoralFanBlock || s.getBlock() instanceof CoralWallFanBlock || s.getBlock() instanceof CoralBlock) return 3;
        return 0;
    }
    void tryGrow(ServerLevel level, BlockPos source, Config c, OperationBudget budget) {
        if (!c.enabled) return;
        if (!budget.take()) return;
        BlockPos origin=ColonyOrigins.origin(level,source);
        BlockState seed = read(level, source, budget);
        BlockPos reefSource = seed != null && seed.getBlock() instanceof CoralBlock ? source : null;
        int type = kind(seed);
        if (type == 0 && water(seed)) {
            BlockState reef = read(level, source.below(), budget);
            if (reef != null && reef.getBlock() instanceof CoralBlock) {
                reefSource = source.below();
                seed = coralVariant(reef, "");
                type = kind(seed);
            }
        }
        if (type == 0) return;
        if(type==3&&reefSource==null){
            BlockState belowSeed=read(level,source.below(),budget);
            if(belowSeed!=null&&belowSeed.getBlock() instanceof CoralBlock)reefSource=source.below();
        }
        if (reefSource == null && !seed.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) return;
        density.observed(level,source,type);
        nativeSeeds++;
        Config.Species policy = type == 1 ? c.seagrass : type == 2 ? c.kelp : c.coral;
        if(!ColonyOrigins.within(source,origin,policy.maxColonyRadius)){radiusRejected++;return;}
        if (!policy.enabled || level.getRandom().nextDouble() >= policy.growthChance) return;
        chancePassed++;
        if (type == 3 && reefSource != null && level.getRandom().nextBoolean()) {
            // Sample a lower face as well as the top of existing reefs, without a volume scan.
            BlockPos faceSource = reefSource.below(level.getRandom().nextInt(3));
            BlockState support = read(level, faceSource, budget);
            if (support != null && support.getBlock() instanceof CoralBlock)
                growOnReef(level, faceSource, support, origin, c, budget);
            return;
        }
        if (type == 3 && c.reefGrowthEnabled
            && density.mature(level, source, policy.maxAreaDensity, c.reefMaturityThreshold, budget)) {
            // Mature habitats invest in their reef instead of filling every remaining slot with floor plants.
            if(level.getRandom().nextInt(4)==0)growReef(level, source, seed, origin, c, budget);
            return;
        }
        int dx = level.getRandom().nextInt(c.maxDistance*2+1)-c.maxDistance;
        int dz = level.getRandom().nextInt(c.maxDistance*2+1)-c.maxDistance;
        if (dx == 0 && dz == 0 || dx*dx+dz*dz > c.maxDistance*c.maxDistance) return;
        BlockPos target = source.offset(dx, 0, dz);
        if(!ColonyOrigins.within(target,origin,policy.maxColonyRadius)){radiusRejected++;return;}
        if(!budget.take())return;
        if(ColonyOrigins.known(level,target)&&!ColonyOrigins.origin(level,target).equals(origin))return;
        for (int cx = (target.getX()-1)>>4; cx <= (target.getX()+1)>>4; cx++)
            for (int cz = (target.getZ()-1)>>4; cz <= (target.getZ()+1)>>4; cz++)
                if (!level.hasChunk(cx, cz)) return;
        if(!budget.take())return;
        target=new BlockPos(target.getX(),level.getHeight(Heightmap.Types.OCEAN_FLOOR,target.getX(),target.getZ()),target.getZ());
        if (!water(read(level, target, budget)) || !water(read(level, target.above(), budget))) return;
        wetTargets++;
        if (!budget.take() || !level.getWorldBorder().isWithinBounds(target)) return;
        var biome = level.getBiome(target);
        if (!biome.is(ALLOWED) || biome.is(DENIED)) return;
        BlockState below = read(level, target.below(), budget);
        if (below == null || !(below.is(SUBSTRATE) || type == 3 && below.getBlock() instanceof CoralBlock)) return;
        if(type==3&&c.reefGrowthEnabled&&below.getBlock() instanceof CoralBlock
            &&density.mature(level,source,policy.maxAreaDensity,c.reefMaturityThreshold,budget)){
            BlockPos base=reefBase(level,origin,budget);
            if(base!=null&&inReefPlan(target,base,origin,seed))return;
        }
        validSubstrates++;
        if(!density.allows(level,target,type,policy.maxAreaDensity,budget)){densityRejected++;return;}
        // Follow the loaded seabed profile instead of projecting a flat line through rising terrain.
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int previousY=source.getY();
        for (int i = 1; i <= steps; i++){
            int x=source.getX()+Math.round((float)dx*i/steps),z=source.getZ()+Math.round((float)dz*i/steps);
            if(!level.hasChunk(x>>4,z>>4))return;
            int y=target.getY();
            if(i<steps){if(!budget.take())return;y=level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);}
            if(Math.abs(y-previousY)>c.maxTerrainStep)return;
            if(i<steps){
                BlockState corridor=read(level,new BlockPos(x,y,z),budget);
                if(corridor==null||!(water(corridor)||kind(corridor)!=0&&corridor.getFluidState().is(net.minecraft.tags.FluidTags.WATER)))return;
            }
            previousY=y;
        }
        int occupied = 0;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockState nearby = read(level, target.offset(x,0,z), budget);
            if (nearby == null) return;
            if (kind(nearby) != 0) occupied++;
        }
        if ((occupied+1)/9.0 > policy.maxDensity) { densityRejected++; return; }
        if (!budget.take() || level.getMaxLocalRawBrightness(target) < c.minimumLight) return;
        boolean tall = type == 1 && level.getRandom().nextBoolean();
        BlockState plant = type == 1 ? (tall ? Blocks.TALL_SEAGRASS : Blocks.SEAGRASS).defaultBlockState()
            : type == 2 ? Blocks.KELP.defaultBlockState() : seed.getBlock() instanceof CoralBlock ? coralVariant(seed, "") : seed;
        if(plant==null)return;
        // Wall fans require orientation-specific support; not propagated in this MVP.
        if (plant.getBlock() instanceof CoralWallFanBlock) return;
        if (!budget.take() || !plant.canSurvive(level, target)) return;
        // Reserve the tall worst case for either grass outcome; a tight budget must not bias the 50/50 choice.
        if (!budget.take(type == 1 ? 4 : 3)) return;
        if (tall) {
            DoublePlantBlock.placeAt(level, plant, target, Block.UPDATE_ALL);
            placed(level, source, target, origin, type, policy);
        } else if (level.setBlock(target, plant, Block.UPDATE_ALL)) {
            placed(level, source, target, origin, type, policy);
        }
    }

    private void placed(ServerLevel level, BlockPos source, BlockPos target, BlockPos origin, int type, Config.Species policy) {
        ColonyOrigins.assign(level,source,origin);ColonyOrigins.assign(level,target,origin);changes++;
        density.observed(level,target,type);
        if(placementObserver!=null)placementObserver.accept(new Placement(source.immutable(),target.immutable(),origin,policy.maxColonyRadius));
    }

    private static BlockState coralVariant(BlockState seed, String suffix) {
        if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(seed.getBlock()).getNamespace().equals("minecraft"))return null;
        String name=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(seed.getBlock()).getPath();
        name=name.replace("_wall_fan", "").replace("_fan", "").replace("_block", "");
        BlockState variant=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name+suffix)).defaultBlockState();
        return kind(variant)==3?variant:null;
    }

    /** Incremental bounded coral mound: never invokes a world-generation feature. */
    private void growReef(ServerLevel level, BlockPos source, BlockState seed, BlockPos origin, Config c, OperationBudget budget) {
        var plan=VanillaReefPlans.select(origin,seed);if(plan==null)return;
        BlockPos base=reefBase(level,origin,budget);if(base==null)return;
        BlockPos root=base.above();
        BlockState rootState=read(level,root,budget);
        if(rootState!=null && kind(rootState)==3 && !(rootState.getBlock() instanceof CoralBlock)){
            BlockState foundation=coralVariant(rootState,"_block");if(foundation==null)return;
            // Explicit exception: the original living coral becomes the solid reef foundation.
            if(!ColonyOrigins.within(root,source,c.maxDistance)||!reefTarget(level,root,origin,c,budget,true)||!budget.take(3))return;
            if(level.setBlock(root,foundation,Block.UPDATE_ALL))placed(level,source,root,origin,3,c.coral);
            return;
        }
        int index=level.getRandom().nextInt(plan.blocks().size());
        var chosen=plan.blocks().get(index);
        BlockPos target=base.above().offset(chosen.getKey());
        if(!ColonyOrigins.within(target,source,c.maxDistance)||!reefTarget(level,target,origin,c,budget))return;
        // Vanilla branches include diagonal steps; use the recorded earlier generator block as support.
        BlockPos parent=base.above().offset(plan.parents().get(index));
        BlockState support=read(level,parent,budget);
        if(support==null||!(support.getBlock() instanceof CoralBlock||parent.equals(target.below())&&support.is(SUBSTRATE))||!budget.take(3))return;
        if(level.setBlock(target,chosen.getValue(),Block.UPDATE_ALL))placed(level,source,target,origin,3,c.coral);
    }

    /** Vanilla decorative corals attach to the upper and four lateral faces; no underside variant exists. */
    void growOnReef(ServerLevel level, BlockPos source, BlockState support, BlockPos origin, Config c, OperationBudget budget) {
        Direction face=Direction.values()[level.getRandom().nextInt(6)];
        if(face==Direction.DOWN)return;
        BlockPos target=source.relative(face);
        BlockPos reefAnchor=null;VanillaReefPlans.Plan reservedPlan=null;
        if(c.reefGrowthEnabled&&density.mature(level,source,c.coral.maxAreaDensity,c.reefMaturityThreshold,budget)){
            BlockPos base=reefBase(level,origin,budget);
            // Leave future skeleton positions free, otherwise early fans seal the growing tip.
            if(base!=null&&inReefPlan(target,base,origin,support))return;
            if(base!=null){reefAnchor=base.above();reservedPlan=VanillaReefPlans.select(origin,support);}
        }
        if(!reefTarget(level,target,origin,c,budget))return;
        if(reservedPlan!=null&&!density.allowsDecoration(level,target,reefAnchor,reservedPlan,c.coral.maxAreaDensity))return;
        BlockState plant=coralVariant(support,face==Direction.UP?(level.getRandom().nextBoolean()?"":"_fan"):"_wall_fan");
        if(plant==null)return;
        if(face!=Direction.UP)plant=plant.setValue(BlockStateProperties.HORIZONTAL_FACING,face);
        if(!budget.take()||!plant.canSurvive(level,target)||!budget.take(3))return;
        if(level.setBlock(target,plant,Block.UPDATE_ALL))placed(level,source,target,origin,3,c.coral);
    }

    private BlockPos reefBase(ServerLevel level,BlockPos origin,OperationBudget budget){
        if(!level.hasChunk(origin.getX()>>4,origin.getZ()>>4))return null;
        ReefRoot key=new ReefRoot(level,origin.getX(),origin.getZ());BlockPos cached=foundations.get(key);
        if(cached!=null){
            BlockState ground=read(level,cached,budget);
            if(ground!=null&&ground.is(SUBSTRATE))return cached;
            foundations.remove(key);return null;
        }
        if(!level.hasChunk(origin.getX()>>4,origin.getZ()>>4)||!budget.take())return null;
        BlockPos base=new BlockPos(origin.getX(),level.getHeight(Heightmap.Types.OCEAN_FLOOR,origin.getX(),origin.getZ())-1,origin.getZ());
        for(int i=0;i<16;i++){
            BlockState state=read(level,base,budget);if(state==null)return null;
            if(state.is(SUBSTRATE)){
                if(foundations.size()>=64)foundations.remove(foundations.keySet().iterator().next());
                foundations.put(key,base);return base;
            }
            if(kind(state)!=3)return null;
            base=base.below();
        }
        return null;
    }

    static boolean inReefPlan(BlockPos p,BlockPos base,BlockPos origin,BlockState species){
        var plan=VanillaReefPlans.select(origin,species);
        return plan!=null&&plan.contains(p.subtract(base.above()));
    }

    private boolean reefTarget(ServerLevel level,BlockPos target,BlockPos origin,Config c,OperationBudget budget){
        return reefTarget(level,target,origin,c,budget,false);
    }
    private boolean reefTarget(ServerLevel level,BlockPos target,BlockPos origin,Config c,OperationBudget budget,boolean rootReplacement){
        if(!c.enabled||!c.coral.enabled||!ColonyOrigins.within(target,origin,c.coral.maxColonyRadius))return false;
        // Neighbor-aware vanilla survival checks must never touch an unloaded chunk.
        for(int cx=(target.getX()-1)>>4;cx<=(target.getX()+1)>>4;cx++)for(int cz=(target.getZ()-1)>>4;cz<=(target.getZ()+1)>>4;cz++)
            if(!level.hasChunk(cx,cz))return false;
        BlockState existing=read(level,target,budget);
        boolean replace=rootReplacement&&target.getX()==origin.getX()&&target.getZ()==origin.getZ()
            &&kind(existing)==3&&!(existing.getBlock() instanceof CoralBlock);
        if(!(water(existing)||replace)||!budget.take()||!level.getWorldBorder().isWithinBounds(target))return false;
        if(ColonyOrigins.known(level,target)&&!ColonyOrigins.origin(level,target).equals(origin))return false;
        if(!budget.take())return false;
        var biome=level.getBiome(target);if(!biome.is(ALLOWED)||biome.is(DENIED)||!c.allowReefsOutsideWarmOcean&&!biome.is(REEFS))return false;
        if(!density.allows(level,target,3,c.coral.maxAreaDensity,budget))return false;
        int occupied=0;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            BlockPos nearby=target.offset(x,0,z);
            BlockState s=read(level,nearby,budget);if(s==null)return false;
            // A connected skeleton is one reef, not a crowd of independent plants.
            // Its footprint still consumes the ordinary area capacity.
            boolean ownSkeleton=s.getBlock() instanceof CoralBlock&&ColonyOrigins.known(level,nearby)&&ColonyOrigins.origin(level,nearby).equals(origin);
            if(kind(s)!=0&&!ownSkeleton)occupied++;
        }
        // Converting the source changes no occupancy; existing crowding must not prevent maturation.
        return (replace||(occupied+1)/9.0<=c.coral.maxDensity)&&budget.take()&&level.getMaxLocalRawBrightness(target)>=c.minimumLight;
    }
}

