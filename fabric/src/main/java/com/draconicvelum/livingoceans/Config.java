// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-only policy, replaced atomically on successful reload. */
public final class Config {
    public boolean enabled = true;
    public boolean shiftPlantingStartsNewColony = true;
    public Species coral = new Species(0.02, 0.20, 0.65,7);
    public Species seagrass = new Species(0.04, 0.32, 0.70);
    public Species kelp = new Species(0.02, 0.16, 0.45);
    public int maxOperationsPerTick = 32;
    public int maxOperationsPerChunk = 32;
    public int attemptsPerChunk = 1;
    public int intervalTicks = 1;
    public int maxDistance = 3;
    public int maxTerrainStep = 1;
    public int playerChunkRadius = 8;
    public int minimumLight = 0;
    public boolean reefGrowthEnabled = true;
    public boolean allowReefsOutsideWarmOcean = true;
    public double reefMaturityThreshold = 0.75;
    public KelpHeights kelpHeights = new KelpHeights();
    public static final class KelpHeights {
        public boolean enabled = true;
        public int minimumHeight = 3;
        public int maximumHeight = 0;
        public int maxInitializationsPerTick = 2;
        public boolean bonemealBypassesLimit = true;
        public boolean playerStackingBypassesLimit = true;
    }
    public static final class Species {
        public boolean enabled = true;
        public int maxColonyRadius = 15;
        public double growthChance;
        public double maxDensity;
        public double maxAreaDensity=0.32;
        public Species(double chance, double density,double local) { growthChance = chance; maxAreaDensity = density;maxDensity=local; }
        public Species(double chance,double density,double local,int radius){this(chance,density,local);maxColonyRadius=radius;}
        void validate() {
            if(!Double.isFinite(maxAreaDensity)||maxAreaDensity<=0||maxAreaDensity>1)throw new IllegalArgumentException("Invalid area density");
            if(maxColonyRadius<1||maxColonyRadius>24)throw new IllegalArgumentException("Colony radius must be 1–24");
            if (!Double.isFinite(growthChance) || growthChance < 0 || growthChance > 1
                || !Double.isFinite(maxDensity) || maxDensity <= 0 || maxDensity > 1)
                throw new IllegalArgumentException("Species probabilities/densities must be finite and within bounds");
        }
    }
    public void validate() {
        if(kelpHeights==null || kelpHeights.minimumHeight<1 || kelpHeights.minimumHeight>384
            ||kelpHeights.maxInitializationsPerTick<1||kelpHeights.maxInitializationsPerTick>64
            ||kelpHeights.maximumHeight<0||kelpHeights.maximumHeight>384
            ||kelpHeights.maximumHeight!=0&&kelpHeights.maximumHeight<kelpHeights.minimumHeight)
            throw new IllegalArgumentException("Invalid kelp height policy");
        if (!Double.isFinite(reefMaturityThreshold) || reefMaturityThreshold < 0 || reefMaturityThreshold > 1)
            throw new IllegalArgumentException("Reef maturity threshold must be within 0–1");
        if (coral == null || seagrass == null || kelp == null) throw new IllegalArgumentException("Missing species");
        coral.validate(); seagrass.validate(); kelp.validate();
        if (maxOperationsPerTick < 1 || maxOperationsPerTick > 4096 || maxOperationsPerChunk<1||maxOperationsPerChunk>4096
            || attemptsPerChunk < 1 || attemptsPerChunk > 4 || intervalTicks < 1 || intervalTicks > 72000
            || maxDistance < 1 || maxDistance > 4 || maxTerrainStep<0||maxTerrainStep>4 || playerChunkRadius < 0 || playerChunkRadius > 32
            || minimumLight < 0 || minimumLight > 15) throw new IllegalArgumentException("Configuration outside safety bounds");
    }
    public static Config read(Path path) throws IOException {
        var document=readDocument(path);
        Config result=new Gson().fromJson(document,Config.class);result.validate();return result;
    }
    private static com.google.gson.JsonObject readDocument(Path path)throws IOException{
        var complete=new Gson().toJsonTree(new Config()).getAsJsonObject();
        if(Files.exists(path)){
            var provided=com.google.gson.JsonParser.parseString(Files.readString(path));
            if(!provided.isJsonObject())throw new IllegalArgumentException("Expected configuration object");
            merge(complete,provided.getAsJsonObject());
        }
        return complete;
    }
    /** Validate before touching the file; reject stale editors instead of losing external edits. */
    public static void save(Path path,Config value,String expectedContent)throws IOException{
        value.validate();
        String current=Files.exists(path)?Files.readString(path):null;
        if(!java.util.Objects.equals(current,expectedContent))throw new IOException("Configuration changed on disk. Reopen the editor before saving.");
        var document=readDocument(path);
        merge(document,new Gson().toJsonTree(value).getAsJsonObject());
        writeAtomically(path,commentedJson(document,"",0,descriptions())+"\n");
    }
    public static java.util.Map<String,String> descriptions()throws IOException{
        try(var stream=Config.class.getResourceAsStream("/assets/livingoceans/config-descriptions.json")){
            if(stream==null)throw new IOException("Missing bundled configuration descriptions");
            try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){
                var descriptions=new java.util.HashMap<String,String>();
                for(var entry:com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().entrySet())
                    for(String key:entry.getKey().split(" / "))descriptions.put(key,entry.getValue().getAsString());
                return java.util.Map.copyOf(descriptions);
            }
        }
    }
    public static Config load(Path path) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String original=Files.exists(path)?Files.readString(path):null;
        var provided=original!=null
            ? com.google.gson.JsonParser.parseString(original)
            : new com.google.gson.JsonObject();
        if(!provided.isJsonObject())throw new IllegalArgumentException("Expected configuration object");
        var complete=gson.toJsonTree(new Config()).getAsJsonObject();
        merge(complete,provided.getAsJsonObject());
        Config result = gson.fromJson(complete, Config.class);
        if (result == null) throw new IllegalArgumentException("Empty configuration");
        result.validate();
        // Emit a JSON5-compatible subset: quoted JSON keys and // comments.
        String documented=commentedJson(complete,"",0,descriptions())+"\n";
        if(!documented.equals(original))writeAtomically(path,documented);
        return result;
    }
    private static String commentedJson(com.google.gson.JsonElement value,String path,int depth,java.util.Map<String,String> descriptions){
        if(!value.isJsonObject())return new Gson().toJson(value);
        String indent="  ".repeat(depth),childIndent=indent+"  ";
        var lines=new java.util.ArrayList<String>();
        for(var entry:value.getAsJsonObject().entrySet()){
            String key=path.isEmpty()?entry.getKey():path+"."+entry.getKey();
            String description=descriptions.get(key);
            String comment=description==null?"":childIndent+"// "+description.replace('\n',' ')+"\n";
            lines.add(comment+childIndent+new Gson().toJson(entry.getKey())+": "+commentedJson(entry.getValue(),key,depth+1,descriptions));
        }
        return "{\n"+String.join(",\n",lines)+"\n"+indent+"}";
    }
    private static void writeAtomically(Path path,String content)throws IOException{
        Path target=path.toAbsolutePath();Files.createDirectories(target.getParent());
        Path temporary=Files.createTempFile(target.getParent(),"livingoceans-",".tmp");
        try{
            Files.writeString(temporary,content);
            try{Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temporary,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void merge(com.google.gson.JsonObject defaults,com.google.gson.JsonObject provided){
        for(var entry:provided.entrySet()){
            var old=defaults.get(entry.getKey());var value=entry.getValue();
            if(old!=null&&old.isJsonObject()&&value.isJsonObject())merge(old.getAsJsonObject(),value.getAsJsonObject());
            else defaults.add(entry.getKey(),value.deepCopy());
        }
    }
}
