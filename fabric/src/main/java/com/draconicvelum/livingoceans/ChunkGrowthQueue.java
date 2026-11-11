// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.LinkedHashMap;

/** One small scheduling entry per loaded chunk, removed on unload; never a list of block positions. */
final class ChunkGrowthQueue {
    @FunctionalInterface interface Attempt {void run(ServerLevel level,BlockPos pos,OperationBudget budget);}
    private record Key(ServerLevel level,int x,int z) { }
    private static final class Task {long due,attempts;int remaining;}
    private final LinkedHashMap<Key,Task> tasks=new LinkedHashMap<>();
    private int processed;private long maximumDelay,saturatedTicks;
    void loaded(ServerLevel level,ChunkPos pos,long now){tasks.computeIfAbsent(new Key(level,pos.x(),pos.z()),key->{Task task=new Task();task.due=now;return task;});}
    void unloaded(ServerLevel level,ChunkPos pos){tasks.remove(new Key(level,pos.x(),pos.z()));}
    void clear(){tasks.clear();processed=0;maximumDelay=saturatedTicks=0;}
    void reschedule(long now){for(Task task:tasks.values()){task.due=now;task.remaining=0;}}
    long attempts(ServerLevel level,ChunkPos pos){var task=tasks.get(new Key(level,pos.x(),pos.z()));return task==null?0:task.attempts;}
    String status(){return "trackedLoadedChunks="+tasks.size()+" processedChunksLastTick="+processed+" maxObservedChunkDelayTicks="+maximumDelay+" budgetSaturatedTicks="+saturatedTicks;}
    void run(MinecraftServer server,Config c,OperationBudget budget,long now,Attempt attempt){
        processed=0;boolean deferred=false;var players=server.getPlayerList().getPlayers();if(players.isEmpty())return;
        // Independent development samplers can bootstrap their current fixture chunk. Production uses load events.
        if(tasks.isEmpty())for(var player:players)if(player.level().hasChunk(player.chunkPosition().x(),player.chunkPosition().z()))loaded(player.level(),player.chunkPosition(),now);
        int visitLimit=tasks.size();
        for(int visited=0;visited<visitLimit&&budget.take();visited++){
            var iterator=tasks.entrySet().iterator();var entry=iterator.next();Key key=entry.getKey();Task task=entry.getValue();
            if(!key.level.hasChunk(key.x,key.z)){iterator.remove();continue;}
            if(task.due>now){iterator.remove();tasks.put(key,task);continue;}
            boolean eligible=false;
            for(var player:players)if(player.level()==key.level){
                int radius=GrowthScheduler.activeRadius(c.playerChunkRadius,server.getPlayerList().getSimulationDistance(),player.requestedViewDistance());
                if(Math.abs(player.chunkPosition().x()-key.x)<=radius&&Math.abs(player.chunkPosition().z()-key.z)<=radius){eligible=true;break;}
            }
            if(!eligible||!key.level.shouldTickBlocksAt(new BlockPos(key.x<<4,0,key.z<<4))){
                task.due=now+20;task.remaining=0;iterator.remove();tasks.put(key,task);continue;
            }
            int allowance=Math.min(c.maxOperationsPerChunk,Math.max(1,c.maxOperationsPerTick-4));
            // Leave this chunk at the head if the full slice cannot fit; partial tail budgets must not starve it.
            if(budget.remaining()<allowance){deferred=true;break;}
            iterator.remove();tasks.put(key,task);
            OperationBudget local=new OperationBudget(allowance);
            maximumDelay=Math.max(maximumDelay,now-task.due);
            if(task.remaining==0)task.remaining=c.attemptsPerChunk;
            processed++;
            while(task.remaining>0&&local.take()){
                int x=(key.x<<4)+key.level.getRandom().nextInt(16),z=(key.z<<4)+key.level.getRandom().nextInt(16);
                int y=key.level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);
                task.attempts++;task.remaining--;attempt.run(key.level,new BlockPos(x,y,z),local);
            }
            if(!budget.take(local.used()))throw new IllegalStateException("Chunk operation reservation exceeded");
            // There is only one pending cycle, never accumulated elapsed-time or unloaded-chunk catch-up.
            task.due=task.remaining==0?now+c.intervalTicks:now;
        }
        if(deferred||budget.used()>=c.maxOperationsPerTick)saturatedTicks++;
    }
}
