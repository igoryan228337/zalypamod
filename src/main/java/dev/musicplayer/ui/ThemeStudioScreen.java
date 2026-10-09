package dev.musicplayer.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.musicplayer.client.MusicPlayerClient;
import dev.musicplayer.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.*;

/**
 * Full layer-oriented Theme Studio. The editor intentionally keeps the theme model
 * JSON-compatible so themes remain portable as .mptheme archives.
 */
public final class ThemeStudioScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int LAYER_TOP = 108;
    private static final int LAYER_ROW = 30;
    private static final int MAX_LAYER_ROWS = 12;

    private final MusicPlayerClient mod = MusicPlayerClient.get();
    private final Theme theme;
    private int selected;
    private int layerScroll;
    private int inspectorScroll;
    private boolean draggingLayer, resizingLayer, draggingTree, dragSnapshotTaken;
    private int dragTreeFrom = -1;
    private double dragX, dragY;
    private EditBox hex1, hex2, nameBox, groupBox;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String copiedStyleJson;
    private boolean timelineOpen = true;
    private final LinkedHashSet<Integer> selectedLayers = new LinkedHashSet<>();
    private final HashSet<String> collapsedGroups = new HashSet<>();
    private boolean multiDrag;

    public ThemeStudioScreen(Theme t) { super(Component.literal("Theme Studio")); theme = t; }

    @Override public void init() {
        hex1 = new EditBox(font, 0, 0, 90, 20, Component.literal("Color"));
        hex2 = new EditBox(font, 0, 0, 90, 20, Component.literal("Color 2"));
        nameBox = new EditBox(font, 0, 0, 180, 20, Component.literal("Layer name"));
        groupBox = new EditBox(font, 0, 0, 110, 20, Component.literal("Group"));
        hex1.setMaxLength(7); hex2.setMaxLength(7); nameBox.setMaxLength(64); groupBox.setMaxLength(32);
        addDrawableChild(hex1); addDrawableChild(hex2); addDrawableChild(nameBox); addDrawableChild(groupBox);
        syncFields();
    }

    private int canvasW() { return Math.max(520, width - 360); }
    private int panelX() { return canvasW() + 10; }
    private void syncFields() {
        if (theme.layers.isEmpty()) { selected = -1; return; }
        selected = Math.max(0, Math.min(selected, theme.layers.size()-1));
        if(selectedLayers.isEmpty()) selectedLayers.add(selected); else selectedLayers.removeIf(i -> i < 0 || i >= theme.layers.size());
        Theme.Layer l = theme.layers.get(selected);
        hex1.setValue(l.color == null ? "#FFFFFF" : l.color);
        hex2.setValue(l.color2 == null ? "#FFFFFF" : l.color2);
        nameBox.setValue(l.label == null ? "Layer" : l.label);
        groupBox.setValue(l.group == null ? "" : l.group);
    }

    private void placeFields() {
        int px = panelX();
        hex1.setPosition(px + 18, height - 64); hex2.setPosition(px + 116, height - 64);
        nameBox.setPosition(px + 18, height - 92); groupBox.setPosition(px + 208, height - 92);
        boolean enabled = selected >= 0 && selected < theme.layers.size();
        hex1.visible = hex2.visible = nameBox.visible = groupBox.visible = enabled;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g); placeFields();
        int cw = canvasW(), px = panelX();
        g.fill(14, 14, cw, height-14, 0xFF101217);
        g.fill(px, 14, width-14, height-14, 0xFF191C23);
        g.drawString(font, "Theme Studio 3.0  •  " + theme.name, 24, 24, 0xFFFFFFFF, true);
        g.drawString(font, "Visual editor", 24, 40, 0xFF858A96, false);
        drawCanvas(g, 24, 56, cw-24, height-112, mx, my);
        drawLayerPanel(g, px, mx, my);
        drawInspector(g, px, mx, my);
        g.drawString(font, "Drag layer = move  •  Shift+drag = resize  •  locked layers cannot move", 24, height-42, 0xFF7D818C, false);
        g.drawString(font, "Ctrl+Z undo  •  Ctrl+Y redo  •  F5 export  •  F6 import  •  Esc save", 24, height-26, 0xFF7D818C, false);
    }

    private void drawCanvas(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Theme.Background b = theme.background;
        int bg1 = parse(b.color, 255), bg2 = parse(b.color2, 255);
        if ("gradient".equalsIgnoreCase(b.type) || !"none".equalsIgnoreCase(b.gradient)) {
            for (int yy=0; yy<h; yy++) {
                double t = yy/(double)Math.max(1,h-1);
                int c = lerp(bg1,bg2,t);
                g.fill(x,y+yy,x+w,y+yy+1,c);
            }
        } else g.fill(x,y,x+w,y+h,bg1);
        g.fill(x+12,y+12,x+w-12,y+h-12,0x44101014);
        for (int i=0;i<theme.layers.size();i++) {
            Theme.Layer l=theme.layers.get(i); if (!l.visible) continue;
            int lx=x+(int)l.x, ly=y+(int)l.y, lw=Math.max(4,(int)l.width), lh=Math.max(4,(int)l.height);
            int color=parse(l.color, (int)(255*Math.max(0,Math.min(1,l.opacity))));
            g.fill(lx,ly,lx+lw,ly+lh,color);
            if (i==selected) { g.fill(lx,ly,lx+lw,ly+2,0xFFFFFFFF); g.fill(lx,ly+lh-2,lx+lw,ly+lh,0xFFFFFFFF); g.fill(lx,ly,lx+2,ly+lh,0xFFFFFFFF); g.fill(lx+lw-2,ly,lx+lw,ly+lh,0xFFFFFFFF); }
            String label=l.label==null?l.id:l.label;
            g.drawString(font,label,lx+5,ly+5,0xFFFFFFFF,true);
        }
    }

    private void drawLayerPanel(GuiGraphics g,int px,int mx,int my) {
        g.drawString(font,"LAYERS",px+16,28,0xFFFFFFFF,true);
        button(g,px+16,38,52,18,"New",0xFF2C303A);
        button(g,px+72,38,64,18,"Dup",0xFF2C303A);
        button(g,px+140,38,52,18,"Del",0xFF2C303A);
        button(g,px+196,38,28,18,"↑",0xFF2C303A);
        button(g,px+228,38,28,18,"↓",0xFF2C303A);
        button(g,px+260,38,42,18,"Undo",0xFF2C303A);
        button(g,px+306,38,42,18,"Redo",0xFF2C303A);
        button(g,px+16,60,54,18,"Copy",0xFF252932);
        button(g,px+74,60,54,18,"Paste",0xFF252932);
        button(g,px+132,60,54,18,"Left",0xFF252932);
        button(g,px+190,60,54,18,"Center",0xFF252932);
        button(g,px+248,60,54,18,"Right",0xFF252932);
        button(g,px+306,60,42,18,timelineOpen?"Time":"Keys",0xFF252932);
        button(g,px+16,82,54,18,"Grp",0xFF252932);
        button(g,px+74,82,54,18,"Lft",0xFF252932);
        button(g,px+132,82,54,18,"Ctr",0xFF252932);
        button(g,px+190,82,54,18,"Rgt",0xFF252932);
        button(g,px+248,82,54,18,"Dist",0xFF252932);
        int y=LAYER_TOP;
        int start=layerScroll, end=Math.min(theme.layers.size(),start+MAX_LAYER_ROWS);
        for(int i=start;i<end;i++) {
            Theme.Layer l=theme.layers.get(i); boolean sel=selectedLayers.contains(i);
            if(l.group!=null&&!l.group.isBlank()&&collapsedGroups.contains(l.group)) continue;
            g.fill(px+12,y-4,px+348,y+24,sel?0xFF343A47:0xFF23262E);
            String prefix=l.visible?"●":"○"; String lock=l.locked?"L":"";
            g.drawString(font,prefix,px+18,y+5,l.visible?0xFF79D8A1:0xFF666A75,false);
            g.drawString(font,lock,px+34,y+5,l.locked?0xFFFFC857:0xFF555A66,false);
            String name=(l.label==null||l.label.isBlank())?l.id:l.label;
            if(l.group!=null&&!l.group.isBlank()) name=(collapsedGroups.contains(l.group)?"> ":"v ")+name;
            if(l.group!=null&&!l.group.isBlank()) name="["+l.group+"] "+name;
            if(name.length()>34) name=name.substring(0,31)+"...";
            g.drawString(font,name,px+50,y+5,0xFFFFFFFF,false);
            g.drawString(font,l.kind==null?"auto":l.kind,px+270,y+5,0xFF858A96,false);
            y+=LAYER_ROW;
        }
        if(theme.layers.size()>MAX_LAYER_ROWS) g.drawString(font,"Wheel/PageUp/PageDown: scroll layers",px+16, y+8,0xFF777C87,false);
    }

    private void drawInspector(GuiGraphics g,int px,int mx,int my) {
        if(selected<0||selected>=theme.layers.size()) return;
        Theme.Layer l=theme.layers.get(selected);
        int top=430-inspectorScroll;
        g.drawString(font,"INSPECTOR",px+16,top,0xFFFFFFFF,true);
        g.drawString(font,"Transform & style",px+16,top+14,0xFF858A96,false);
        row(g,px+16,top+22,"X",fmt(l.x),0); row(g,px+16,top+44,"Y",fmt(l.y),1);
        row(g,px+16,top+66,"Width",fmt(l.width),2); row(g,px+16,top+88,"Height",fmt(l.height),3);
        row(g,px+16,top+110,"Rotation",fmt(l.rotation),4); row(g,px+16,top+132,"Opacity",fmt(l.opacity),5); row(g,px+16,top+154,"Radius",String.valueOf(l.radius),6);
        g.drawString(font,"Visible: "+l.visible+"   Locked: "+l.locked,px+16,top+178,0xFFB5B9C4,false);
        g.drawString(font,"Group: "+(l.group==null||l.group.isBlank()?"(none)":l.group),px+16,top+194,0xFFB5B9C4,false);
        g.drawString(font,"Mask: "+l.mask+"  radius "+l.maskRadius,px+16,top+210,0xFFB5B9C4,false);
        g.drawString(font,"Gradient: "+l.gradient+"  Blend: "+l.blend,px+16,top+226,0xFFB5B9C4,false);
        g.drawString(font,"Animation: "+l.animation+"  speed "+fmt(l.animationSpeed)+"  amp "+fmt(l.animationAmplitude),px+16,top+242,0xFFB5B9C4,false);
        g.drawString(font,"Reactive: "+l.reactive+"  strength "+fmt(l.reactiveAmount)+"  sensitivity "+fmt(l.reactiveSensitivity),px+16,top+258,0xFFB5B9C4,false);
        g.drawString(font,"Glow "+on(l.glow)+"  Shadow "+on(l.shadow)+"  Clip "+on(l.clip),px+16,top+274,0xFFB5B9C4,false);
        g.drawString(font,"Keyframes: "+l.keyframes.size(),px+16,top+290,0xFFD5D7DE,false);
        g.drawString(font,"Background",px+16,top+320,0xFFFFFFFF,true);
        g.drawString(font,"Type "+theme.background.type+"  Gradient "+theme.background.gradient+"  Angle "+fmt(theme.background.gradientAngle),px+16,top+338,0xFFB5B9C4,false);
        g.drawString(font,"Dynamic cover: "+theme.background.dynamicCover+"  strength "+fmt(theme.background.dynamicStrength),px+16,top+354,0xFFB5B9C4,false);
        g.drawString(font,"[F] mask  [G] glow  [H] shadow  [C] clip  [R] reactive",px+16,top+376,0xFF777C87,false);
        g.drawString(font,"[A] animation  [Y] easing  [U] blend  [Ctrl+J/K] keyframes",px+16,top+392,0xFF777C87,false);
        g.drawString(font,"[1-5] reactive source  [+/-] strength",px+16,top+408,0xFF777C87,false);
        g.drawString(font,"[B] background gradient  [V] dynamic cover  [ [ / ] ] angle",px+16,top+424,0xFF777C87,false);
        g.drawString(font,"[Ctrl+G] group  [Ctrl+L] lock  [Ctrl+H] hide",px+16,top+440,0xFF777C87,false);
        if(timelineOpen){
            g.drawString(font,"TIMELINE  "+l.keyframes.size()+" keyframes",px+16,top+466,0xFFFFFFFF,true);
            int tx=px+16, ty=top+484, tw=320; g.fill(tx,ty,tx+tw,ty+2,0xFF3A3E48);
            for(Theme.Keyframe k:l.keyframes){int kx=tx+(int)Math.max(0,Math.min(tw-4,k.time*tw));g.fill(kx,ty-4,kx+5,ty+7,0xFF8AB4FF);}
            g.drawString(font,"J/K add keyframe  •  Ctrl+Delete removes last",tx,ty+16,0xFF777C87,false);
        }
    }

    private void row(GuiGraphics g,int x,int y,String label,String value,int id){g.drawString(font,label+": "+value,x,y,0xFFB5B9C4,false);g.fill(x+190,y-3,x+208,y+11,0xFF292D36);g.fill(x+212,y-3,x+230,y+11,0xFF292D36);g.drawString(font,"-",x+196,y-1,0xFFFFFFFF,false);g.drawString(font,"+",x+218,y-1,0xFFFFFFFF,false);}
    private void button(GuiGraphics g,int x,int y,int w,int h,String s,int c){g.fill(x,y,x+w,y+h,c);g.drawString(font,s,x+(w-font.width(s))/2,y+5,0xFFFFFFFF,false);}
    private String on(boolean v){return v?"ON":"OFF";}
    private String fmt(double v){return String.format(Locale.ROOT,"%.2f",v);}
    private int parse(String s,int a){try{String x=s==null?"FFFFFF":s.replace("#","");return (Math.max(0,Math.min(255,a))<<24)|(Integer.parseInt(x,16)&0xFFFFFF);}catch(Exception e){return (Math.max(0,Math.min(255,a))<<24)|0xFFFFFF;}}
    private int lerp(int a,int b,double t){int ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255,br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;int r=(int)(ar+(br-ar)*t),gg=(int)(ag+(bg-ag)*t),bl=(int)(ab+(bb-ab)*t);return 0xFF000000|(r<<16)|(gg<<8)|bl;}

    private String snapshot(){return GSON.toJson(theme);}
    private void remember(){undo.push(snapshot());while(undo.size()>30)undo.removeLast();redo.clear();}
    private void restore(String json){Theme n=GSON.fromJson(json,Theme.class);theme.id=n.id;theme.name=n.name;theme.version=n.version;theme.background=n.background;theme.visualizer=n.visualizer;theme.cover=n.cover;theme.notification=n.notification;theme.island=n.island;theme.font=n.font;theme.accent=n.accent;theme.secondary=n.secondary;theme.text=n.text;theme.panelRadius=n.panelRadius;theme.shadow=n.shadow;theme.layers=n.layers;theme.root=mod.THEMES.get(theme.id).root;syncFields();mod.saveTheme(theme);}
    private void undo(){if(undo.isEmpty())return;redo.push(snapshot());restore(undo.pop());}
    private void redo(){if(redo.isEmpty())return;undo.push(snapshot());restore(redo.pop());}
    private void mutate(Runnable r){remember();r.run();mod.saveTheme(theme);syncFields();}

    @Override public boolean mouseClicked(double x,double y,int b){
        int px=panelX();
        if(x>=px+12&&x<=px+348&&y>=LAYER_TOP-4&&y<LAYER_TOP+MAX_LAYER_ROWS*LAYER_ROW) {
            int i=layerScroll+(int)((y-(LAYER_TOP-4))/LAYER_ROW);
            if(i>=0&&i<theme.layers.size()){
                int local=(int)x-px;
                if(local<44){mutate(()->theme.layers.get(i).visible=!theme.layers.get(i).visible);return true;}
                if(local<50){mutate(()->theme.layers.get(i).locked=!theme.layers.get(i).locked);return true;}
                if(Screen.hasControlDown()) { if(!selectedLayers.add(i)) selectedLayers.remove(i); selected=i; syncFields(); return true; }
                selectedLayers.clear(); selectedLayers.add(i); selected=i;
                if(l.group!=null&&!l.group.isBlank()&&local>=50&&local<72){ if(collapsedGroups.contains(l.group)) collapsedGroups.remove(l.group); else collapsedGroups.add(l.group); syncFields(); return true; }
                syncFields();dragTreeFrom=i;draggingTree=true;return true;
            }
        }
        if(y>=60&&y<=80&&x>=px+16&&x<=px+348){
            int rel=(int)x-px;
            if(rel<58){copiedStyleJson=selected>=0&&selected<theme.layers.size()?GSON.toJson(theme.layers.get(selected)):null;return true;}
            if(rel<116&&copiedStyleJson!=null&&selected>=0&&selected<theme.layers.size()){mutate(()->pasteStyle(theme.layers.get(selected)));return true;}
            if(rel<174&&selected>=0){mutate(()->theme.layers.get(selected).x=0);return true;}
            if(rel<232&&selected>=0){mutate(()->theme.layers.get(selected).x=Math.max(0,(canvasW()-24-theme.layers.get(selected).width)/2));return true;}
            if(rel<290&&selected>=0){mutate(()->theme.layers.get(selected).x=Math.max(0,canvasW()-48-theme.layers.get(selected).width));return true;}
            if(rel<350){timelineOpen=!timelineOpen;return true;}
        }
        if(y>=82&&y<=104&&x>=px+16&&x<=px+348){
            int rel=(int)x-px;
            if(rel<58&&selected>=0){mutate(()->{String old=theme.layers.get(selected).group; String ng=(old==null||old.isBlank())?"Group 1":old; for(Integer i:selectedLayers) if(i>=0&&i<theme.layers.size()) theme.layers.get(i).group=ng;});return true;}
            if(rel<116){mutate(()->alignSelected(0));return true;}
            if(rel<174){mutate(()->alignSelected(1));return true;}
            if(rel<232){mutate(()->alignSelected(2));return true;}
            if(rel<290){mutate(this::distributeSelected);return true;}
        }
        if(y>=38&&y<=58&&x>=px+16&&x<=px+348){
            int rel=(int)x-px;
            if(rel<68){mutate(()->{theme.layers.add(new Theme.Layer("layer"+System.nanoTime(),"New layer",40,40,140,42));selected=theme.layers.size()-1;});return true;}
            if(rel<136&&selected>=0){mutate(()->duplicateSelected());return true;}
            if(rel<192&&selected>=0){mutate(()->deleteSelected());return true;}
            if(rel<224&&selected>0){mutate(()->Collections.swap(theme.layers,selected,selected-1));selected--;return true;}
            if(rel<256&&selected>=0&&selected<theme.layers.size()-1){mutate(()->Collections.swap(theme.layers,selected,selected+1));selected++;return true;}
            if(rel<302){undo();return true;} if(rel<350){redo();return true;}
        }
        if(selected>=0&&selected<theme.layers.size()){
            Theme.Layer l=theme.layers.get(selected); int top=430-inspectorScroll;
            if(x>=px+190&&x<=px+232){int id=-1;int[] ys={top+22,top+44,top+66,top+88,top+110,top+132,top+154};for(int i=0;i<ys.length;i++)if(y>=ys[i]-4&&y<=ys[i]+13){id=i;break;}if(id>=0){final int fid=id;mutate(()->adjust(l,fid,x<px+211?-1:1));return true;}}
            if(x<canvasW()&&y>=56&&y<height-56&&!l.locked){draggingLayer=true;resizingLayer=Screen.hasShiftDown();dragX=x;dragY=y;dragSnapshotTaken=false;return true;}
        }
        return super.mouseClicked(x,y,b);
    }

    private void alignSelected(int mode){
        if(selectedLayers.isEmpty()) return;
        if(mode==0){double x=selectedLayers.stream().map(i->theme.layers.get(i).x).min(Double::compare).orElse(0.0);for(Integer i:selectedLayers)theme.layers.get(i).x=x;}
        else if(mode==1){double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;for(Integer i:selectedLayers){Theme.Layer l=theme.layers.get(i);min=Math.min(min,l.x);max=Math.max(max,l.x+l.width);}double center=(min+max)/2;for(Integer i:selectedLayers)theme.layers.get(i).x=center-theme.layers.get(i).width/2;}
        else {double right=selectedLayers.stream().map(i->theme.layers.get(i).x+theme.layers.get(i).width).max(Double::compare).orElse(0.0);for(Integer i:selectedLayers)theme.layers.get(i).x=right-theme.layers.get(i).width;}
    }
    private void distributeSelected(){
        if(selectedLayers.size()<3)return;
        List<Theme.Layer> ls=selectedLayers.stream().map(i->theme.layers.get(i)).sorted(Comparator.comparingDouble(l->l.x)).toList();
        double left=ls.get(0).x, right=ls.get(ls.size()-1).x; double step=(right-left)/Math.max(1,ls.size()-1);
        for(int i=1;i<ls.size()-1;i++)ls.get(i).x=left+step*i;
    }

    private void adjust(Theme.Layer l,int id,int dir){double d=dir*(Screen.hasShiftDown()?10:2);switch(id){case 0->l.x+=d;case 1->l.y+=d;case 2->l.width=Math.max(4,l.width+d);case 3->l.height=Math.max(4,l.height+d);case 4->l.rotation+=d;case 5->l.opacity=Math.max(0,Math.min(1,l.opacity+d*.05));case 6->l.radius=Math.max(0,(int)(l.radius+d));}}
    private void pasteStyle(Theme.Layer n){Theme.Layer o=GSON.fromJson(copiedStyleJson,Theme.Layer.class);n.color=o.color;n.color2=o.color2;n.gradient=o.gradient;n.blend=o.blend;n.image=o.image;n.animation=o.animation;n.mask=o.mask;n.maskRadius=o.maskRadius;n.easing=o.easing;n.opacity=o.opacity;n.rotation=o.rotation;n.glow=o.glow;n.shadow=o.shadow;n.clip=o.clip;n.reactive=o.reactive;n.reactiveAmount=o.reactiveAmount;n.reactiveSensitivity=o.reactiveSensitivity;n.reactiveBand=o.reactiveBand;n.animationSpeed=o.animationSpeed;n.animationAmplitude=o.animationAmplitude;n.animationPhase=o.animationPhase;n.animateOpacity=o.animateOpacity;n.animateScale=o.animateScale;}
    private void duplicateSelected(){Theme.Layer o=theme.layers.get(selected),n=new Theme.Layer(o.id+"_copy",o.label+" Copy",o.x+12,o.y+12,o.width,o.height);n.group=o.group;n.kind=o.kind;n.text=o.text;n.color=o.color;n.color2=o.color2;n.gradient=o.gradient;n.blend=o.blend;n.image=o.image;n.animation=o.animation;n.mask=o.mask;n.maskRadius=o.maskRadius;n.easing=o.easing;n.opacity=o.opacity;n.rotation=o.rotation;n.glow=o.glow;n.shadow=o.shadow;n.clip=o.clip;n.locked=false;n.reactive=o.reactive;n.reactiveAmount=o.reactiveAmount;n.reactiveSensitivity=o.reactiveSensitivity;n.reactiveBand=o.reactiveBand;n.animationSpeed=o.animationSpeed;n.animationAmplitude=o.animationAmplitude;n.animationPhase=o.animationPhase;n.animateOpacity=o.animateOpacity;n.animateScale=o.animateScale;for(Theme.Keyframe k:o.keyframes)n.keyframes.add(new Theme.Keyframe(k.time,k.x+12,k.y+12,k.width,k.height,k.rotation,k.opacity,k.scale));theme.layers.add(selected+1,n);selected++;}
    private void deleteSelected(){if(theme.layers.size()<=1)return;theme.layers.remove(selected);selected=Math.max(0,Math.min(selected,theme.layers.size()-1));}

    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){
        if(draggingTree){int target=layerScroll+(int)((y-(LAYER_TOP-4))/LAYER_ROW);if(target>=0&&target<theme.layers.size()&&target!=dragTreeFrom){if(!dragSnapshotTaken){remember();dragSnapshotTaken=true;}Theme.Layer item=theme.layers.remove(dragTreeFrom);theme.layers.add(target,item);selected=target;dragTreeFrom=target;syncFields();}return true;}
        if(draggingLayer&&selected>=0&&selected<theme.layers.size()){
            if(!dragSnapshotTaken){remember();dragSnapshotTaken=true;}
            if(resizingLayer){Theme.Layer l=theme.layers.get(selected);l.width=Math.max(4,l.width+dx);l.height=Math.max(4,l.height+dy);}
            else for(Integer i:selectedLayers){if(i<0||i>=theme.layers.size())continue;Theme.Layer l=theme.layers.get(i);if(!l.locked){l.x+=dx;l.y+=dy;}}
            return true;
        }
        return super.mouseDragged(x,y,b,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int b){if(draggingLayer||draggingTree){mod.saveTheme(theme);}draggingLayer=false;resizingLayer=false;draggingTree=false;dragTreeFrom=-1;dragSnapshotTaken=false;return super.mouseReleased(x,y,b);}

    @Override public boolean mouseScrolled(double x,double y,double hx,double vy){
        if(x>=panelX()){if(y>=LAYER_TOP-8&&y<LAYER_TOP+MAX_LAYER_ROWS*LAYER_ROW+8){layerScroll=Math.max(0,Math.min(Math.max(0,theme.layers.size()-MAX_LAYER_ROWS),layerScroll-(int)Math.signum(vy)));}else inspectorScroll=Math.max(0,Math.min(360,inspectorScroll-(int)vy*18));return true;}
        return super.mouseScrolled(x,y,hx,vy);
    }

    @Override public boolean keyPressed(int key,int scan,int mods){
        if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_Z){undo();return true;}
        if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_Y){redo();return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE){applyTextFields();mod.saveTheme(theme);onClose();return true;}
        if(key==GLFW.GLFW_KEY_ENTER){applyTextFields();return true;}
        if(key==GLFW.GLFW_KEY_F5){exportTheme();return true;}
        if(key==GLFW.GLFW_KEY_F6){importTheme();return true;}
        if(selected>=0&&selected<theme.layers.size()){
            Theme.Layer l=theme.layers.get(selected);
            if(key==GLFW.GLFW_KEY_DELETE){mutate(this::deleteSelected);return true;}
            if(key==GLFW.GLFW_KEY_N){mutate(()->{theme.layers.add(new Theme.Layer("layer"+System.nanoTime(),"New layer",30,30,120,40));selected=theme.layers.size()-1;});return true;}
            if(key==GLFW.GLFW_KEY_D){mutate(this::duplicateSelected);return true;}
            if(key==GLFW.GLFW_KEY_G&&Screen.hasControlDown()){mutate(()->l.group=(l.group==null||l.group.isBlank())?"Group 1":"");return true;}
            if(key==GLFW.GLFW_KEY_L&&Screen.hasControlDown()){mutate(()->l.locked=!l.locked);return true;}
            if(key==GLFW.GLFW_KEY_H&&Screen.hasControlDown()){mutate(()->l.visible=!l.visible);return true;}
            if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){double step=Screen.hasShiftDown()?10:2;mutate(()->{if(key==GLFW.GLFW_KEY_UP)l.y-=step;else l.y+=step;});return true;}
            if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){double step=Screen.hasShiftDown()?10:2;mutate(()->{if(key==GLFW.GLFW_KEY_LEFT)l.x-=step;else l.x+=step;});return true;}
            if(key==GLFW.GLFW_KEY_F){mutate(()->cycleMask(l));return true;}
            if(key==GLFW.GLFW_KEY_G){mutate(()->l.glow=!l.glow);return true;}
            if(key==GLFW.GLFW_KEY_C){mutate(()->l.clip=!l.clip);return true;}
            if(key==GLFW.GLFW_KEY_U){mutate(()->cycleBlend(l));return true;}
            if(key==GLFW.GLFW_KEY_Y){mutate(()->cycleEasing(l));return true;}
            if(key==GLFW.GLFW_KEY_R){mutate(()->cycleReactive(l));return true;}
            if(key==GLFW.GLFW_KEY_A){mutate(()->cycleAnimation(l));return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_J){mutate(()->l.addKeyframe(0));return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_K){mutate(()->l.addKeyframe(1));return true;}
            if(key==GLFW.GLFW_KEY_1||key==GLFW.GLFW_KEY_2||key==GLFW.GLFW_KEY_3||key==GLFW.GLFW_KEY_4||key==GLFW.GLFW_KEY_5){int source=key-GLFW.GLFW_KEY_0;mutate(()->l.reactive=switch(source){case 1->"none";case 2->"bass";case 3->"mid";case 4->"treble";default->"volume";});return true;}
            if(key==GLFW.GLFW_KEY_B){mutate(()->theme.cover.borderWidth=theme.cover.borderWidth>0?0:2);return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_B){mutate(()->{theme.background.gradient="none".equalsIgnoreCase(theme.background.gradient)?"vertical":"none";theme.background.type="gradient".equalsIgnoreCase(theme.background.type)?"color":"gradient";});return true;}
            if(key==GLFW.GLFW_KEY_V&&!Screen.hasControlDown()){mutate(()->theme.visualizer.enabled=!theme.visualizer.enabled);return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_V){mutate(()->theme.background.dynamicCover=!theme.background.dynamicCover);return true;}
            if(key==GLFW.GLFW_KEY_X){mutate(()->theme.visualizer.type=next(new String[]{"bars","wave","radial","dots"},theme.visualizer.type));return true;}
            if(key==GLFW.GLFW_KEY_I){mutate(()->theme.visualizer.sensitivity=Math.max(.1,Math.min(4,theme.visualizer.sensitivity+(Screen.hasShiftDown()?-0.1:.1))));return true;}
            if(key==GLFW.GLFW_KEY_M){mutate(()->theme.visualizer.smoothing=Math.max(0,Math.min(1,theme.visualizer.smoothing+(Screen.hasShiftDown()?-0.05:.05))));return true;}
            if(key==GLFW.GLFW_KEY_O){mutate(()->theme.visualizer.opacity=Math.max(0,Math.min(1,theme.visualizer.opacity+(Screen.hasShiftDown()?-0.05:.05))));return true;}
            if(key==GLFW.GLFW_KEY_COMMA){mutate(()->theme.visualizer.barCount=Math.max(4,theme.visualizer.barCount-(Screen.hasShiftDown()?4:1)));return true;}
            if(key==GLFW.GLFW_KEY_PERIOD){mutate(()->theme.visualizer.barCount=Math.min(128,theme.visualizer.barCount+(Screen.hasShiftDown()?4:1)));return true;}
            if(key==GLFW.GLFW_KEY_P&&!Screen.hasControlDown()){mutate(()->theme.visualizer.particleDensity=Math.max(0,Math.min(2,theme.visualizer.particleDensity+(Screen.hasShiftDown()?-0.05:.05))));return true;}
            if(key==GLFW.GLFW_KEY_W){mutate(()->theme.visualizer.waveThickness=Math.max(.5,Math.min(12,theme.visualizer.waveThickness+(Screen.hasShiftDown()?-0.5:.5))));return true;}
            if(key==GLFW.GLFW_KEY_Q){mutate(()->theme.visualizer.mirror=!theme.visualizer.mirror);return true;}
            if(key==GLFW.GLFW_KEY_E){mutate(()->theme.cover.mode=next(new String[]{"static","pulse","vinyl","cd","circle"},theme.cover.mode));return true;}
            if(key==GLFW.GLFW_KEY_T){mutate(()->theme.cover.scale=Math.max(.5,Math.min(1.5,theme.cover.scale+(Screen.hasShiftDown()?-0.05:.05))));return true;}
            if(key==GLFW.GLFW_KEY_Z){mutate(()->theme.cover.rotationSpeed=Math.max(0,Math.min(180,theme.cover.rotationSpeed+(Screen.hasShiftDown()?-5:5))));return true;}
            if(key==GLFW.GLFW_KEY_LEFT_BRACKET){mutate(()->theme.background.gradientAngle=Math.max(0,theme.background.gradientAngle-5));return true;}
            if(key==GLFW.GLFW_KEY_RIGHT_BRACKET){mutate(()->theme.background.gradientAngle=Math.min(360,theme.background.gradientAngle+5));return true;}if(Screen.hasShiftDown()&&key==GLFW.GLFW_KEY_H){mutate(()->l.animationSpeed=Math.max(0,Math.min(6,l.animationSpeed+(Screen.hasControlDown()?-0.1:.1))));return true;}if(Screen.hasShiftDown()&&key==GLFW.GLFW_KEY_N){mutate(()->l.animationAmplitude=Math.max(0,Math.min(4,l.animationAmplitude+(Screen.hasControlDown()?-0.1:.1))));return true;}if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_P){mutate(()->l.animationPhase+=Screen.hasShiftDown()?-0.1:.1);return true;}if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_R){mutate(()->l.animateOpacity=!l.animateOpacity);return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_A){selectedLayers.clear();for(int i=0;i<theme.layers.size();i++)selectedLayers.add(i);selected=theme.layers.isEmpty()?-1:0;syncFields();return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_G){mutate(()->{String g=l.group==null||l.group.isBlank()?"Group 1":l.group;for(Integer i:selectedLayers)theme.layers.get(i).group=g;});return true;}
            if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_DELETE){mutate(()->{List<Integer> ids=new ArrayList<>(selectedLayers);ids.sort(Comparator.reverseOrder());for(Integer i:ids)if(i>=0&&i<theme.layers.size()&&theme.layers.size()>1)theme.layers.remove((int)i);selectedLayers.clear();if(!theme.layers.isEmpty()){selected=0;selectedLayers.add(0);}syncFields();});return true;}
        }
        return super.keyPressed(key,scan,mods);
    }

    private void applyTextFields(){if(selected<0||selected>=theme.layers.size())return;Theme.Layer l=theme.layers.get(selected);String a=hex1.getValue().trim(),b=hex2.getValue().trim();if(a.matches("#?[0-9a-fA-F]{6}")){remember();l.color=a.startsWith("#")?a.toUpperCase(Locale.ROOT):"#"+a.toUpperCase(Locale.ROOT);}if(b.matches("#?[0-9a-fA-F]{6}"))l.color2=b.startsWith("#")?b.toUpperCase(Locale.ROOT):"#"+b.toUpperCase(Locale.ROOT);l.label=nameBox.getValue();l.group=groupBox.getValue();mod.saveTheme(theme);}
    private void cycleMask(Theme.Layer l){String[] a={"none","rounded","circle","diamond","hexagon"};l.mask=next(a,l.mask);}
    private void cycleBlend(Theme.Layer l){String[] a={"normal","add","multiply","screen","overlay"};l.blend=next(a,l.blend);}
    private void cycleEasing(Theme.Layer l){String[] a={"smooth","linear","sharp"};l.easing=next(a,l.easing);}
    private void cycleReactive(Theme.Layer l){String[] a={"none","bass","mid","treble","volume"};l.reactive=next(a,l.reactive);}
    private void cycleAnimation(Theme.Layer l){String[] a={"none","float","pulse","zoom","rotate","bounce","sway","shake"};l.animation=next(a,l.animation);}
    private String next(String[] a,String cur){int i=0;for(int j=0;j<a.length;j++)if(a[j].equalsIgnoreCase(cur))i=j;return a[(i+1)%a.length];}

    private void exportTheme(){try{FileDialog d=new FileDialog((Frame)null,"Export .mptheme",FileDialog.SAVE);d.setFile(theme.id+".mptheme");d.setVisible(true);if(d.getFile()!=null)mod.THEMES.exportTheme(theme.id,Path.of(d.getDirectory(),d.getFile()));}catch(Exception ignored){}}
    private void importTheme(){try{FileDialog d=new FileDialog((Frame)null,"Import .mptheme",FileDialog.LOAD);d.setVisible(true);if(d.getFile()!=null)mod.THEMES.importTheme(Path.of(d.getDirectory(),d.getFile()));}catch(Exception ignored){}}

    @Override public void onClose(){applyTextFields();mod.saveTheme(theme);super.onClose();}
}
