package dev.musicplayer.ui;

import dev.musicplayer.client.MusicPlayerClient;
import dev.musicplayer.library.*;
import dev.musicplayer.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class MusicScreen extends Screen {
    private final MusicPlayerClient mod=MusicPlayerClient.get();
    private String tab="Tracks",query="";
    private int scroll=0;
    private PlaylistManager.Playlist selectedPlaylist;
    private Track dragged;
    private int draggedIndex=-1;
    private int queueDraggedIndex=-1;
    private net.minecraft.client.gui.components.EditBox renameBox, searchBox;
    private String sort="Title";
    private boolean ascending=true;
    private String genreFilter="All";
    private String groupSelection=null;
    private boolean groupShuffle=false;
    private final Set<String> selectedKeys=new LinkedHashSet<>();
    private final String[] tabs={"Home","Tracks","Artists","Albums","Favorites","Recent","History","Queue","Playlists","Themes"};
    public MusicScreen(){super(Component.literal("Music Center"));}
    protected void init(){
        searchBox=new net.minecraft.client.gui.components.EditBox(font,24,12,210,20,Component.literal("Search music"));
        searchBox.setMaxLength(256); searchBox.setValue(query); searchBox.setHint(Component.literal("Search title, artist, album…"));
        searchBox.setResponder(v->{query=v;scroll=0;}); addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Refresh"),b->mod.reloadLibrary()).bounds(width-90,12,78,20).build());
        addRenderableWidget(Button.builder(Component.literal("Shuffle"),b->mod.setShuffle(!mod.CONFIG.shuffle)).bounds(width-180,12,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Sort: "+sort),b->{String[] a={"Title","Artist","Album","Duration","Genre"};int i=Arrays.asList(a).indexOf(sort);sort=a[(i+1)%a.length];clearWidgets();init();}).bounds(width-285,12,98,20).build());
        addRenderableWidget(Button.builder(Component.literal(ascending?"A→Z":"Z→A"),b->{ascending=!ascending;clearWidgets();init();}).bounds(width-335,12,44,20).build());
        addRenderableWidget(Button.builder(Component.literal("Genre: "+genreFilter),b->{List<String> a=genres();int i=a.indexOf(genreFilter);genreFilter=a.get((i+1)%a.size());clearWidgets();init();}).bounds(width-445,12,106,20).build());
        if(selectedKeys.size()>0){ addRenderableWidget(Button.builder(Component.literal("Queue selected ("+selectedKeys.size()+")"),b->{for(Track t:mod.LIBRARY.all())if(selectedKeys.contains(t.key()))mod.QUEUE.add(t);selectedKeys.clear();}).bounds(24,38,132,20).build()); addRenderableWidget(Button.builder(Component.literal("Favorite selected"),b->{for(Track t:mod.LIBRARY.all())if(selectedKeys.contains(t.key())&&!mod.LIBRARY.isFavorite(t))mod.LIBRARY.toggleFavorite(t);selectedKeys.clear();}).bounds(160,38,120,20).build()); }
        if("Playlists".equals(tab)) {
            addRenderableWidget(Button.builder(Component.literal("+ Playlist"),b->{mod.PLAYLISTS.create("New Playlist");clearWidgets();init();}).bounds(width-285,12,98,20).build());
            if(selectedPlaylist!=null){
                addRenderableWidget(Button.builder(Component.literal("Rename"),b->startRename()).bounds(width-180,12,78,20).build());
                addRenderableWidget(Button.builder(Component.literal("Clear"),b->{mod.PLAYLISTS.clear(selectedPlaylist);clearWidgets();init();}).bounds(width-90,12,78,20).build());
            }
        }
        if("History".equals(tab)) { addRenderableWidget(Button.builder(Component.literal("Clear history"),b->{mod.HISTORY.clear();clearWidgets();init();}).bounds(width-110,12,98,20).build()); }
        if("Queue".equals(tab)) {
            addRenderableWidget(Button.builder(Component.literal("Clear queue"),b->{mod.clearQueue();clearWidgets();init();}).bounds(width-180,12,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("Repeat: "+mod.CONFIG.repeat),b->{mod.cycleRepeat();clearWidgets();init();}).bounds(width-285,12,98,20).build());
            addRenderableWidget(Button.builder(Component.literal("Clean duplicates"),b->{mod.removeQueueDuplicates();clearWidgets();init();}).bounds(width-405,12,112,20).build());
            addRenderableWidget(Button.builder(Component.literal("Clear played"),b->{mod.removePlayedQueue();clearWidgets();init();}).bounds(width-525,12,112,20).build());
            addRenderableWidget(Button.builder(Component.literal("Current → top"),b->{mod.moveCurrentToFront();clearWidgets();init();}).bounds(width-645,12,112,20).build());
        }
    }
    private void startRename(){
        if(selectedPlaylist==null)return;
        if(renameBox!=null)return;
        renameBox=new net.minecraft.client.gui.components.EditBox(font,width/2-150,12,180,20,Component.literal("Playlist name"));
        renameBox.setMaxLength(80); renameBox.setValue(selectedPlaylist.name); addRenderableWidget(renameBox); renameBox.setFocused(true);
    }
    private void finishRename(){
        if(renameBox!=null&&selectedPlaylist!=null){mod.PLAYLISTS.rename(selectedPlaylist,renameBox.getValue());renameBox=null;clearWidgets();init();}
    }
    private List<Track> list(){
        List<Track> rows;
        if("Playlists".equals(tab)&&selectedPlaylist!=null) rows=new ArrayList<>(mod.PLAYLISTS.tracks(selectedPlaylist,mod.LIBRARY.all()));
        else if("Artists".equals(tab)&&groupSelection!=null) rows=new ArrayList<>(mod.LIBRARY.artistTracks(groupSelection));
        else if("Albums".equals(tab)&&groupSelection!=null) rows=new ArrayList<>(mod.LIBRARY.albumTracks(groupSelection));
        else rows=new ArrayList<>(switch(tab){case "Favorites"->mod.LIBRARY.favorites();case "Recent"->mod.LIBRARY.recent();case "History"->mod.HISTORY.recent(mod.LIBRARY.all());case "Artists"->mod.LIBRARY.artists(query);case "Albums"->mod.LIBRARY.albums(query);case "Queue"->mod.QUEUE.items();default->mod.LIBRARY.search(query);});
        if(!"All".equals(genreFilter)) rows.removeIf(t->!genreFilter.equalsIgnoreCase(t.genre==null||t.genre.isBlank()?"Unknown":t.genre));
        Comparator<Track> c=switch(sort){case "Artist"->Comparator.comparing(Track::displayArtist,String.CASE_INSENSITIVE_ORDER).thenComparing(t->t.title,String.CASE_INSENSITIVE_ORDER);case "Album"->Comparator.comparing(Track::displayAlbum,String.CASE_INSENSITIVE_ORDER).thenComparingInt(t->t.trackNumber);case "Duration"->Comparator.comparingLong(t->t.durationMs);case "Genre"->Comparator.comparing(t->t.genre==null?"":t.genre,String.CASE_INSENSITIVE_ORDER).thenComparing(t->t.title,String.CASE_INSENSITIVE_ORDER);default->Comparator.comparing(t->t.title,String.CASE_INSENSITIVE_ORDER);};
        if(!"Recent".equals(tab)&&!"Queue".equals(tab)) rows.sort(ascending?c:c.reversed());
        return rows;
    }
    private List<String> genres(){LinkedHashSet<String> s=new LinkedHashSet<>();for(Track t:mod.LIBRARY.all())s.add(t.genre==null||t.genre.isBlank()?"Unknown":t.genre);List<String> r=new ArrayList<>(s);r.sort(String.CASE_INSENSITIVE_ORDER);r.add(0,"All");return r;}
    public void render(GuiGraphics g,int mx,int my,float delta){
        renderBackground(g);g.drawString(font,"Music Center",24,18,0xFFFFFFFF,true);
        g.drawString(font,"Sort: "+sort+"  |  Genre: "+genreFilter+"  |  "+(ascending?"ascending":"descending"),260,40,0xFF777A84,false);
        int tx=24;for(String t:tabs){int tw=font.width(t)+20;boolean sel=t.equals(tab);g.fill(tx,62,tx+tw,84,sel?0xFF3B3F4B:0xFF202229);g.drawString(font,t,tx+10,69,sel?0xFFFFFFFF:0xFFB9BBC4,false);tx+=tw+4;}
        if("Themes".equals(tab)){renderThemes(g);super.render(g,mx,my,delta);return;}
        if("Home".equals(tab)){renderHome(g);super.render(g,mx,my,delta);return;}
        if("Playlists".equals(tab)&&selectedPlaylist==null){renderPlaylists(g);super.render(g,mx,my,delta);return;}
        if(("Artists".equals(tab)||"Albums".equals(tab))&&groupSelection==null){renderGroups(g);super.render(g,mx,my,delta);return;}
        if(("Artists".equals(tab)||"Albums".equals(tab))&&groupSelection!=null){renderGroupDetail(g,mx,my);super.render(g,mx,my,delta);return;}
        List<Track> rows=list();int top=96;int visible=Math.max(1,(height-top-20)/44);int start=Math.min(scroll,Math.max(0,rows.size()-visible));
        for(int i=start;i<Math.min(rows.size(),start+visible);i++){Track t=rows.get(i);int y=top+(i-start)*44;boolean cur=t==mod.currentTrack();boolean selected=selectedKeys.contains(t.key());g.fill(18,y-2,width-18,y+38,selected?0xFF343A49:(cur?0xFF292D38:0xFF17191F));g.drawString(font,t.title,32,y+4,0xFFFFFFFF,cur);g.drawString(font,t.displayArtist()+"  •  "+t.displayAlbum(),32,y+22,0xFF9B9EA8,false);g.drawString(font,t.durationText(),width-92,y+10,0xFF9B9EA8,false);g.drawString(font,t.favorite?"★":"☆",width-48,y+10,0xFFFFFFFF,false);}
        Track now=mod.currentTrack();if(now!=null){long dur=Math.max(1,mod.AUDIO.durationMs()),pos=mod.AUDIO.positionMs();int py=height-42,left=24,right=width-24;g.fill(left,py,right,py+4,0xFF353842);int px=left+(int)((right-left)*Math.min(1d,pos/(double)dur));g.fill(left,py,px,py+4,0xFFFFFFFF);g.drawString(font,String.format("%d:%02d / %d:%02d",pos/60000,(pos/1000)%60,dur/60000,(dur/1000)%60),left,py-15,0xFF9B9EA8,false);}
        g.drawString(font,"Left: play   Right: add next   Middle: favorite   Queue: drag to reorder / right-click remove   ↑/↓: scroll",24,height-14,0xFF777A84,false);
        if("History".equals(tab)) g.drawString(font,"Total plays: "+mod.HISTORY.totalPlays()+"   •   History entries: "+mod.HISTORY.entries().size(),width-300,height-14,0xFF777A84,false);super.render(g,mx,my,delta);
    }
    private void renderGroups(GuiGraphics g){
        List<String> groups="Artists".equals(tab)?mod.LIBRARY.artistNames(query):mod.LIBRARY.albumNames(query);
        int cardW=210, cardH=190, gap=12, cols=Math.max(1,(width-48)/(cardW+gap));
        int rows=(groups.size()+cols-1)/cols, visibleRows=Math.max(1,(height-116)/cardH);
        int startRow=Math.min(scroll,Math.max(0,rows-visibleRows));
        int start=startRow*cols, end=Math.min(groups.size(),start+visibleRows*cols);
        for(int i=start;i<end;i++){
            int local=i-start, col=local%cols, row=local/cols;
            int x=24+col*(cardW+gap), y=96+row*cardH;
            String name=groups.get(i);
            List<Track> ts="Artists".equals(tab)?mod.LIBRARY.artistTracks(name):mod.LIBRARY.albumTracks(name);
            long duration=ts.stream().mapToLong(t->t.durationMs).sum();
            Path art="Artists".equals(tab)?mod.LIBRARY.artistArtwork(name):mod.LIBRARY.albumArtwork(name);
            g.fill(x,y,x+cardW,y+cardH-10,0xFF17191F);
            if(art!=null){var tex=TextureCache.get(art);if(tex!=null)g.blit(tex,x+10,y+10,0,0,cardW-20,cardW-20,cardW-20,cardW-20);}
            else {g.fill(x+10,y+10,x+cardW-10,y+cardW-10,0xFF292C35);g.drawCenteredString(font,"♪",x+cardW/2,y+cardW/2-5,0xFF777B86);}
            g.drawString(font,truncate(name,cardW-28),x+12,y+cardW+2,0xFFFFFFFF,true);
            String sub=ts.size()+" track"+(ts.size()==1?"":"s")+"  •  "+(duration/60000)+":"+String.format("%02d",(duration/1000)%60);
            g.drawString(font,sub,x+12,y+cardW+20,0xFF8F929C,false);
            g.drawString(font,"▶",x+cardW-28,y+cardW+18,0xFFFFFFFF,false);
        }
        g.drawString(font,"Left: open  •  Right: play all  •  Middle: shuffle all  •  Wheel: scroll",24,height-14,0xFF777A84,false);
    }
    private void renderGroupDetail(GuiGraphics g,int mx,int my){
        List<Track> ts="Artists".equals(tab)?mod.LIBRARY.artistTracks(groupSelection):mod.LIBRARY.albumTracks(groupSelection);
        long duration=ts.stream().mapToLong(t->t.durationMs).sum();
        Path art="Artists".equals(tab)?mod.LIBRARY.artistArtwork(groupSelection):mod.LIBRARY.albumArtwork(groupSelection);
        int size=Math.min(220,Math.max(150,height/4));
        g.fill(24,96,24+size,96+size,0xFF17191F);
        if(art!=null){var tex=TextureCache.get(art);if(tex!=null)g.blit(tex,24,96,0,0,size,size,size,size);}
        else {g.fill(24,96,24+size,96+size,0xFF292C35);g.drawCenteredString(font,"♪",24+size/2,96+size/2-5,0xFF777B86);}
        int x=24+size+22;
        g.drawString(font,"Artists".equals(tab)?"ARTIST":"ALBUM",x,100,0xFF8F929C,true);
        g.drawString(font,truncate(groupSelection,width-x-24),x,118,0xFFFFFFFF,true);
        g.drawString(font,ts.size()+" track"+(ts.size()==1?"":"s")+"  •  "+(duration/60000)+":"+String.format("%02d",(duration/1000)%60),x,140,0xFF9B9EA8,false);
        g.drawString(font,"Enter: play all   Shift+Enter: shuffle   Right click: add all to queue",x,164,0xFF777A84,false);
        int top=96+size+22;
        for(int i=0;i<ts.size();i++){Track t=ts.get(i);int y=top+i*42;if(y>height-55)break;boolean cur=t==mod.currentTrack();g.fill(18,y-2,width-18,y+36,cur?0xFF292D38:0xFF17191F);g.drawString(font,String.valueOf(i+1),30,y+8,0xFF777A84,false);g.drawString(font,t.title,58,y+4,0xFFFFFFFF,cur);g.drawString(font,t.durationText(),width-82,y+8,0xFF9B9EA8,false);g.drawString(font,t.favorite?"★":"☆",width-46,y+8,0xFFFFFFFF,false);}
    }

    private String truncate(String s,int max){if(s==null)return "";if(font.width(s)<=max)return s;String ell="…";while(s.length()>1&&font.width(s+ell)>max)s=s.substring(0,s.length()-1);return s+ell;}
    private void renderHome(GuiGraphics g){int y=108;g.drawString(font,"Music Library",24,y,0xFFFFFFFF,true);g.drawString(font,"Tracks: "+mod.LIBRARY.all().size(),24,y+22,0xFF9B9EA8,false);g.drawString(font,"Favorites: "+mod.LIBRARY.favorites().size(),24,y+40,0xFF9B9EA8,false);g.drawString(font,"Recent: "+mod.LIBRARY.recent().size(),24,y+58,0xFF9B9EA8,false);g.drawString(font,"Playlists: "+mod.PLAYLISTS.all().size(),24,y+76,0xFF9B9EA8,false);g.drawString(font,"History plays: "+mod.HISTORY.totalPlays(),24,y+94,0xFF9B9EA8,false);Track now=mod.currentTrack();if(now!=null){g.drawString(font,"Now playing",250,y,0xFFFFFFFF,true);g.drawString(font,truncate(now.title,350),250,y+22,0xFFFFFFFF,false);g.drawString(font,truncate(now.displayArtist(),350),250,y+40,0xFF9B9EA8,false);g.drawString(font,"Press F7 anytime to return here.",250,y+72,0xFF777A84,false);}}
    private void renderPlaylists(GuiGraphics g){int y=104;for(PlaylistManager.Playlist p:mod.PLAYLISTS.all()){g.fill(20,y-4,width-20,y+42,0xFF181A20);g.drawString(font,p.name,34,y+4,0xFFFFFFFF,false);g.drawString(font,p.trackKeys.size()+" tracks",34,y+22,0xFF8F929C,false);y+=52;}g.drawString(font,"Left: open   Right: delete   Inside playlist: drag to reorder   Playlist data: "+mod.configDir.resolve("playlists.json"),24,height-32,0xFF777A84,false);}
    private void renderThemes(GuiGraphics g){int y=104;for(Theme t:mod.THEMES.all()){boolean active=t.id.equals(mod.CONFIG.theme);g.fill(20,y-4,width-20,y+42,active?0xFF2D313D:0xFF181A20);g.drawString(font,t.name,34,y+4,0xFFFFFFFF,active);g.drawString(font,t.id,34,y+22,0xFF8F929C,false);y+=52;}g.drawString(font,"Left: activate   Right: open Theme Studio",24,height-32,0xFF777A84,false);}
    private int modsForMouse(){int m=0;long w=Minecraft.getInstance().getWindow().getWindow();if(GLFW.glfwGetKey(w,GLFW.GLFW_KEY_LEFT_CONTROL)==GLFW.GLFW_PRESS||GLFW.glfwGetKey(w,GLFW.GLFW_KEY_RIGHT_CONTROL)==GLFW.GLFW_PRESS)m|=GLFW.GLFW_MOD_CONTROL;return m;}
    public boolean mouseClicked(double mx,double my,int button){
        int tx=24;for(String t:tabs){int tw=font.width(t)+20;if(my>=62&&my<84&&mx>=tx&&mx<tx+tw){tab=t;scroll=0;selectedPlaylist=null;clearWidgets();init();return true;}tx+=tw+4;}
        if(!"Themes".equals(tab)&&my>=height-62&&my<height-34&&mod.currentTrack()!=null){long dur=Math.max(1,mod.AUDIO.durationMs());double f=Math.max(0,Math.min(1,(mx-24)/(double)Math.max(1,width-48)));mod.seek((long)(dur*f));return true;}
        if("Playlists".equals(tab)&&selectedPlaylist==null&&my>=96){int idx=(int)((my-96)/52);List<PlaylistManager.Playlist> ps=mod.PLAYLISTS.all();if(idx>=0&&idx<ps.size()){PlaylistManager.Playlist p=ps.get(idx);if(button==0){selectedPlaylist=p;scroll=0;clearWidgets();init();}else if(button==1)mod.PLAYLISTS.delete(p);return true;}}
        if(("Artists".equals(tab)||"Albums".equals(tab))&&groupSelection==null&&my>=96&&my<height-34){
            List<String> groups="Artists".equals(tab)?mod.LIBRARY.artistNames(query):mod.LIBRARY.albumNames(query);
            int cardW=210, cardH=190, gap=12, cols=Math.max(1,(width-48)/(cardW+gap));
            int startRow=Math.max(0,scroll), col=(int)((mx-24)/(cardW+gap)), row=(int)((my-96)/cardH)+startRow;
            int index=row*cols+col;
            if(mx>=24&&col>=0&&col<cols&&my>=96&&index>=0&&index<groups.size()){String name=groups.get(index);List<Track> ts="Artists".equals(tab)?mod.LIBRARY.artistTracks(name):mod.LIBRARY.albumTracks(name);
                if(button==0){groupSelection=name;scroll=0;groupShuffle=false;return true;}
                if(button==1&&!ts.isEmpty()){for(Track t:ts)mod.QUEUE.add(t);mod.play(ts.get(0));return true;}
                if(button==2&&!ts.isEmpty()){Collections.shuffle(ts);mod.play(ts.get(0));for(int i=1;i<ts.size();i++)mod.QUEUE.add(ts.get(i));return true;}
            }
        }
        if("Playlists".equals(tab)&&selectedPlaylist!=null&&my>=96&&my<height-62){int row=(int)((my-96)/44)+scroll;List<Track> rows=list();if(row>=0&&row<rows.size()){if(button==1){mod.PLAYLISTS.remove(selectedPlaylist,rows.get(row));return true;}if(button==0){dragged=rows.get(row);draggedIndex=row;return true;}}}
        if(("Artists".equals(tab)||"Albums".equals(tab))&&groupSelection!=null){
            List<Track> ts="Artists".equals(tab)?mod.LIBRARY.artistTracks(groupSelection):mod.LIBRARY.albumTracks(groupSelection);
            int size=Math.min(220,Math.max(150,height/4));
            if(my>=96&&my<96+size&&mx>=24&&mx<24+size){
                if(button==0&&!ts.isEmpty()){mod.play(ts.get(0));return true;}
                if(button==1&&!ts.isEmpty()){for(Track t:ts)mod.QUEUE.add(t);return true;}
                if(button==2&&!ts.isEmpty()){Collections.shuffle(ts);mod.play(ts.get(0));for(int i=1;i<ts.size();i++)mod.QUEUE.add(ts.get(i));return true;}
            }
            int top=96+size+22;
            if(my>=top&&my<height-55){int row=(int)((my-top)/42)+scroll;if(row>=0&&row<ts.size()){Track t=ts.get(row);if(button==0){if((modsForMouse()==GLFW.GLFW_MOD_CONTROL)){if(!selectedKeys.add(t.key()))selectedKeys.remove(t.key());}else mod.play(t);}else if(button==1)mod.queueNext(t);else if(button==2)mod.LIBRARY.toggleFavorite(t);return true;}}
        }
        if("Queue".equals(tab)&&my>=96&&my<height-62){int row=(int)((my-96)/44)+scroll;List<Track> rows=mod.QUEUE.items();if(row>=0&&row<rows.size()){if(button==1){mod.removeQueue(row);return true;}if(button==0){queueDraggedIndex=row;return true;}if(button==2){mod.play(rows.get(row));return true;}}}
        if(!"Themes".equals(tab)&&!("Playlists".equals(tab)&&selectedPlaylist==null)&&!"Queue".equals(tab)&&my>=96&&my<height-62){int row=(int)((my-96)/44)+scroll;List<Track> rows=list();if(row>=0&&row<rows.size()){Track t=rows.get(row);if(button==0){if(modsForMouse()==GLFW.GLFW_MOD_CONTROL){if(!selectedKeys.add(t.key()))selectedKeys.remove(t.key());}else mod.play(t);}else if(button==1)mod.queueNext(t);else if(button==2)mod.LIBRARY.toggleFavorite(t);return true;}}
        if("Themes".equals(tab)&&my>=96){int idx=(int)((my-96)/52);List<Theme> ts=new ArrayList<>(mod.THEMES.all());if(idx>=0&&idx<ts.size()){if(button==0){mod.CONFIG.theme=ts.get(idx).id;mod.saveConfig();}else if(button==1)Minecraft.getInstance().setScreen(new ThemeStudioScreen(ts.get(idx)));return true;}}
        return super.mouseClicked(mx,my,button);
    }
    public boolean mouseReleased(double mx,double my,int button){
        if(button==0&&queueDraggedIndex>=0&&"Queue".equals(tab)){
            int target=(int)((my-96)/44)+scroll;
            if(target>=0&&target<mod.QUEUE.items().size()) mod.moveQueue(queueDraggedIndex,target);
            queueDraggedIndex=-1;return true;
        }
        if(button==0&&dragged!=null&&"Playlists".equals(tab)&&selectedPlaylist!=null){
            int target=(int)((my-96)/44)+scroll;List<Track> rows=list();
            if(target>=0&&target<rows.size()){
                if(draggedIndex>=0 && draggedIndex<selectedPlaylist.trackKeys.size()){
                    int actualTarget=rows.indexOf(dragged);
                    if(actualTarget<0) actualTarget=target;
                    mod.PLAYLISTS.move(selectedPlaylist,draggedIndex,Math.min(selectedPlaylist.trackKeys.size()-1,Math.max(0,target)));
                } else mod.PLAYLISTS.add(selectedPlaylist,dragged);
            }
            dragged=null;draggedIndex=-1;return true;
        }
        dragged=null;draggedIndex=-1;return super.mouseReleased(mx,my,button);
    }
    public boolean mouseScrolled(double mx,double my,double dx,double dy){int step=("Artists".equals(tab)||"Albums".equals(tab))?1:1;scroll=Math.max(0,scroll+(dy>0?-step:step));return true;}
    public boolean charTyped(char c,int modifiers){return super.charTyped(c,modifiers);}
    public boolean keyPressed(int key,int scancode,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){if(renameBox!=null){renameBox=null;clearWidgets();init();return true;}if(groupSelection!=null&&(tab.equals("Artists")||tab.equals("Albums"))){groupSelection=null;scroll=0;return true;}if(selectedPlaylist!=null){selectedPlaylist=null;clearWidgets();init();return true;}onClose();return true;}if((tab.equals("Artists")||tab.equals("Albums"))&&groupSelection!=null&&key==GLFW.GLFW_KEY_ENTER){List<Track> ts=tab.equals("Artists")?mod.LIBRARY.artistTracks(groupSelection):mod.LIBRARY.albumTracks(groupSelection);if(!ts.isEmpty()){if((mods&GLFW.GLFW_MOD_SHIFT)!=0){Collections.shuffle(ts);mod.play(ts.get(0));for(int i=1;i<ts.size();i++)mod.QUEUE.add(ts.get(i));}else{for(Track t:ts)mod.QUEUE.add(t);mod.play(ts.get(0));}}return true;}if(key==GLFW.GLFW_KEY_F5){mod.reloadLibrary();return true;}if(key==GLFW.GLFW_KEY_ENTER&&renameBox!=null){finishRename();return true;}return super.keyPressed(key,scancode,mods);}
}
