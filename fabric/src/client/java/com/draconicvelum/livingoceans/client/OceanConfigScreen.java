// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans.client;

import com.draconicvelum.livingoceans.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;

/** Shared editor registered with Fabric Mod Menu and NeoForge's Mods screen. */
public final class OceanConfigScreen extends Screen {
    private final Screen parent;
    private ConfigEditorModel model;
    private String failure;
    private static final List<String> CATEGORIES=List.of("General","Coral","Seagrass","Kelp","Kelp heights","Environment","Performance");
    public OceanConfigScreen(Screen parent,Path config){
        super(Component.literal("Living Oceans"));this.parent=parent;
        try{model=new ConfigEditorModel(config);}catch(Exception e){failure="Cannot open configuration: "+e.getMessage();}
    }
    @Override protected void init(){
        int w=Math.min(316,width-24),left=(width-w)/2;
        if(model!=null){
            for(int i=0;i<CATEGORIES.size();i++){
                String category=CATEGORIES.get(i);
                addRenderableWidget(Button.builder(Component.literal(category),b->minecraft.gui.setScreen(new CategoryScreen(category))).bounds(left+(i%2)*(w/2+4),64+(i/2)*26,w/2-4,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Reset all"),b->{model.resetAll();failure=null;rebuildWidgets();}).bounds(left,height-28,100,20).build());
            var save=addRenderableWidget(Button.builder(Component.literal("Save & Done"),b->save()).bounds(left+108,height-28,100,20).build());
            save.active=model.error()==null;
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left+w-100,height-28,100,20).build());
    }
    private void save(){
        try{
            Config saved=model.save();
            var server=minecraft.getSingleplayerServer();
            if(server!=null)server.execute(()->LivingOceans.applyConfiguration(saved));
            else LivingOceans.applyConfiguration(saved);
            onClose();
        }catch(Exception e){failure=e.getMessage();rebuildWidgets();}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        graphics.centeredText(font,title,width/2,18,0xFFFFFFFF);
        String scope=minecraft.getConnection()!=null&&minecraft.getSingleplayerServer()==null?"Local worlds only; remote server settings stay unchanged.":"Changes apply to your local worlds when saved.";
        graphics.centeredText(font,scope,width/2,40,0xFFB8E6FF);
        String error=failure!=null?failure:model==null?null:model.error();
        if(error!=null)graphics.textWithWordWrap(font,Component.literal(error),16,height-62,width-32,0xFFFF8888);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}

    private final class CategoryScreen extends Screen {
        private final String category;
        private final List<ConfigEditorModel.Setting> settings;
        private int page,rows;
        private int labelX,labelWidth;
        CategoryScreen(String category){super(Component.literal("Living Oceans - "+category));this.category=category;settings=model.settings().stream().filter(s->s.category().equals(category)).toList();}
        @Override protected void init(){
            rows=Math.max(1,(height-120)/26);page=Math.min(page,(settings.size()-1)/rows);
            int w=Math.min(380,width-24),left=(width-w)/2;
            labelX=left;labelWidth=w-152;
            int start=page*rows;
            for(int i=start;i<Math.min(settings.size(),start+rows);i++){
                var setting=settings.get(i);int y=64+(i-start)*26;
                Tooltip tooltip=Tooltip.create(Component.literal(setting.label()+"\n"+model.description(setting)));
                if(setting.type()==boolean.class){
                    var toggle=addRenderableWidget(Button.builder(Component.literal(Boolean.parseBoolean(model.value(setting))?"On":"Off"),b->{model.set(setting,Boolean.toString(!Boolean.parseBoolean(model.value(setting))));rebuildWidgets();}).bounds(left+w-144,y,88,20).build());
                    toggle.setTooltip(tooltip);
                }else{
                    var field=addRenderableWidget(new EditBox(font,left+w-144,y,88,20,Component.literal(setting.label())));
                    field.setMaxLength(32);field.setValue(model.value(setting));field.setTooltip(tooltip);
                    field.setResponder(value->model.set(setting,value));
                }
                var reset=addRenderableWidget(Button.builder(Component.literal("Reset"),b->{model.reset(setting);rebuildWidgets();}).bounds(left+w-50,y,50,20).build());
                reset.setTooltip(Tooltip.create(Component.literal("Default: "+setting.defaultValue())));
            }
            int pages=(settings.size()+rows-1)/rows;
            var previous=addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page--;rebuildWidgets();}).bounds(width/2-128,height-54,80,20).build());previous.active=page>0;
            var next=addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;rebuildWidgets();}).bounds(width/2+48,height-54,80,20).build());next.active=page+1<pages;
            addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width/2-100,height-28,200,20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
            super.extractRenderState(graphics,mouseX,mouseY,delta);
            graphics.centeredText(font,title,width/2,18,0xFFFFFFFF);
            graphics.centeredText(font,"Hover over a value for its description and limits.",width/2,40,0xFFB8E6FF);
            for(int i=page*rows;i<Math.min(settings.size(),(page+1)*rows);i++){
                String label=settings.get(i).label();
                if(font.width(label)>labelWidth)label=font.plainSubstrByWidth(label,labelWidth-10)+"...";
                graphics.text(font,label,labelX,70+(i-page*rows)*26,0xFFFFFFFF);
            }
            graphics.centeredText(font,(page+1)+" / "+((settings.size()+rows-1)/rows),width/2,height-48,0xFFFFFFFF);
            String error=model.error();
            if(error!=null)graphics.textWithWordWrap(font,Component.literal(error),12,height-72,width-24,0xFFFF8888);
        }
        @Override public void onClose(){minecraft.gui.setScreen(OceanConfigScreen.this);}
    }
}
