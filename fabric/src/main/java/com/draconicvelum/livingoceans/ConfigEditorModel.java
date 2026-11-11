// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Detached draft shared by both client menus; opening or cancelling never writes. */
public final class ConfigEditorModel {
    public record Setting(String path,String label,String category,Class<?> type,String defaultValue) { }
    private final Path path;
    private final String original;
    private final List<Setting> settings=new ArrayList<>();
    private final Map<String,String> values=new LinkedHashMap<>(),descriptions;
    public ConfigEditorModel(Path path)throws IOException{
        this.path=path;original=Files.exists(path)?Files.readString(path):null;
        Config loaded=Config.read(path);descriptions=Config.descriptions();
        collect(loaded,new Config(),"");
    }
    private void collect(Object loaded,Object defaults,String prefix){
        try{for(var field:loaded.getClass().getFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
            String key=prefix+field.getName();Object current=field.get(loaded),def=field.get(defaults);
            if(field.getType().isPrimitive()){
                settings.add(new Setting(key,humanize(field.getName()),category(key),field.getType(),def.toString()));
                values.put(key,current.toString());
            }else collect(current,def,key+".");
        }}catch(IllegalAccessException e){throw new IllegalStateException("Cannot inspect configuration",e);}
    }
    private static String humanize(String name){String s=name.replaceAll("([a-z])([A-Z])","$1 $2").toLowerCase(Locale.ROOT);return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String category(String key){
        if(key.startsWith("coral.")||key.startsWith("reef")||key.equals("allowReefsOutsideWarmOcean"))return "Coral";
        if(key.startsWith("seagrass."))return "Seagrass";
        if(key.startsWith("kelp."))return "Kelp";
        if(key.startsWith("kelpHeights."))return "Kelp heights";
        return switch(key){case "maxOperationsPerTick","maxOperationsPerChunk","attemptsPerChunk","intervalTicks","playerChunkRadius"->"Performance";case "maxDistance","maxTerrainStep","minimumLight"->"Environment";default->"General";};
    }
    public List<Setting> settings(){return List.copyOf(settings);}
    public String value(Setting s){return values.get(s.path());}
    public void set(Setting s,String value){values.put(s.path(),value);}
    public void reset(Setting s){set(s,s.defaultValue());}
    public void resetAll(){settings.forEach(this::reset);}
    public String description(Setting s){return descriptions.getOrDefault(s.path(),s.label())+" Default: "+s.defaultValue()+".";}
    public Config draft(){
        var object=new Gson().toJsonTree(new Config()).getAsJsonObject();
        for(Setting s:settings){
            String text=value(s);JsonElement v;
            try{
                if(s.type()==boolean.class){
                    if(!text.equals("true")&&!text.equals("false"))throw new IllegalArgumentException(s.label()+" must be true or false.");
                    v=new JsonPrimitive(Boolean.parseBoolean(text));
                }
                else if(s.type()==int.class)v=new JsonPrimitive(Integer.parseInt(text));
                else v=new JsonPrimitive(Double.parseDouble(text));
            }
            catch(NumberFormatException e){throw new IllegalArgumentException(s.category()+": "+s.label()+" must be a valid "+(s.type()==int.class?"whole number.":"number."));}
            String[] parts=s.path().split("\\.");var target=object;
            for(int i=0;i<parts.length-1;i++)target=target.getAsJsonObject(parts[i]);
            target.add(parts[parts.length-1],v);
        }
        Config result=new Gson().fromJson(object,Config.class);result.validate();return result;
    }
    public String error(){try{draft();return null;}catch(IllegalArgumentException e){return e.getMessage();}}
    public Config save()throws IOException{Config result=draft();Config.save(path,result,original);return result;}
}
