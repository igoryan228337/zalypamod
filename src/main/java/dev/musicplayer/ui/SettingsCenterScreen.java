package dev.musicplayer.ui;

import dev.musicplayer.client.KeyBindings;
import dev.musicplayer.client.MusicPlayerClient;
import dev.musicplayer.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;import java.awt.Desktop;
import java.util.*;

/** Unified settings hub for player, audio, library, themes, visuals and hotkeys. */
public final class SettingsCenterScreen extends Screen {
 private final MusicPlayerClient mod=MusicPlayerClient.get();
 private String tab="Player", listening=null; private int scroll;
 private final String[] tabs={"Player","Audio","Library","Queue","Themes","Visuals","Notifications","Hotkeys","Advanced"};
 public SettingsCenterScreen(){super(Component.literal("Music Player Settings"));}
 @Override protected void init(){clearWidgets();
  int x=18;for(String t:tabs){String s=t;addRenderableWidget(Button.builder(Component.literal(s),b->{tab=s;listening=null;scroll=0;init();}).bounds(x,28,76,20).build());x+=80;}
  if(tab.equals("Player")) player(); else if(tab.equals("Audio")) audio(); else if(tab.equals("Library")) library(); else if(tab.equals("Queue")) queue(); else if(tab.equals("Themes")) themes(); else if(tab.equals("Visuals")) visuals(); else if(tab.equals("Notifications")) notifications(); else if(tab.equals("Hotkeys")) hotkeys(); else advanced();
  addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-82,height-28,68,20).build());
 }
 private void row(String label,String value,java.util.function.Consumer<Button> action){int y=66+scroll*0;addRenderableWidget(Button.builder(Component.literal(label+": "+value),action).bounds(28,y,Math.min(360,width-56),22).build());scroll++;}
 private void player(){row("Player visible",mod.CONFIG.visible?"ON":"OFF",b->{mod.CONFIG.visible=!mod.CONFIG.visible;mod.saveConfig();init();});row("Notifications",mod.CONFIG.notifications?"ON":"OFF",b->{mod.CONFIG.notifications=!mod.CONFIG.notifications;mod.saveConfig();init();});row("Snap to edges",mod.CONFIG.snapToEdges?"ON":"OFF",b->{mod.CONFIG.snapToEdges=!mod.CONFIG.snapToEdges;mod.saveConfig();init();});row("Island",mod.CONFIG.islandEnabled?"ON":"OFF",b->{mod.CONFIG.islandEnabled=!mod.CONFIG.islandEnabled;mod.saveConfig();init();});row("Visualizer",mod.CONFIG.visualizer,b->{nextVisualizer();});row("Cover mode",mod.CONFIG.coverMode,b->{nextCover();});}
 private void audio(){row("Volume",String.format(Locale.ROOT,"%.0f%%",mod.CONFIG.volume*100),b->{mod.setVolume(mod.CONFIG.volume>=.95?0:mod.CONFIG.volume+.05f);init();});row("Shuffle",mod.CONFIG.shuffle?"ON":"OFF",b->{mod.setShuffle(!mod.CONFIG.shuffle);init();});row("Repeat",mod.CONFIG.repeat,b->{mod.cycleRepeat();init();});}
 private void library(){row("Tracks",String.valueOf(mod.LIBRARY.all().size()),b->{});row("Reload library","Scan music folder",b->{mod.reloadLibrary();init();});row("Open music folder",".minecraft/music",b->{try{Desktop.open(mod.gameDir.resolve("music").toFile());}catch(Exception ignored){}});}
 private void queue(){row("Queue size",String.valueOf(mod.QUEUE.items().size()),b->{});row("Clean duplicates","Run",b->{mod.removeQueueDuplicates();init();});row("Clear played","Run",b->{mod.removePlayedQueue();init();});row("Clear queue","Run",b->{mod.clearQueue();init();});}
 private void themes(){int y=66;for(Theme t:mod.THEMES.all()){Theme tt=t;addRenderableWidget(Button.builder(Component.literal((tt.id.equals(mod.CONFIG.theme)?"✓ ":"")+tt.name),b->{mod.CONFIG.theme=tt.id;mod.saveConfig();init();}).bounds(28,y,260,22).build());addRenderableWidget(Button.builder(Component.literal("Studio"),b->Minecraft.getInstance().setScreen(new ThemeStudioScreen(tt))).bounds(294,y,70,22).build());y+=28;}addRenderableWidget(Button.builder(Component.literal("Reload themes"),b->{mod.THEMES.reload();init();}).bounds(28,y+8,150,22).build());addRenderableWidget(Button.builder(Component.literal("Export active"),b->{try{mod.THEMES.exportTheme(mod.CONFIG.theme,mod.configDir.resolve("exports").resolve(mod.CONFIG.theme+".mptheme"));}catch(Exception ignored){}}).bounds(186,y+8,150,22).build());}
 private void visuals(){row("Visualizer",mod.CONFIG.visualizer,b->{nextVisualizer();});row("Cover",mod.CONFIG.coverMode,b->{nextCover();});row("Dynamic colors",mod.CONFIG.dynamicCoverColors?"ON":"OFF",b->{mod.CONFIG.dynamicCoverColors=!mod.CONFIG.dynamicCoverColors;mod.saveConfig();init();});row("Animated background","Theme Studio",b->Minecraft.getInstance().setScreen(new ThemeStudioScreen(mod.theme())));}
 private void notifications(){row("Enabled",mod.CONFIG.notifications?"ON":"OFF",b->{mod.CONFIG.notifications=!mod.CONFIG.notifications;mod.saveConfig();init();});row("Dynamic cover colors",mod.CONFIG.autoDynamicColors?"ON":"OFF",b->{mod.CONFIG.autoDynamicColors=!mod.CONFIG.autoDynamicColors;mod.saveConfig();init();});}
 private void advanced(){row("Config folder","musicplayer",b->{});row("Reset player position","24,24",b->{mod.CONFIG.x=24;mod.CONFIG.y=24;mod.saveConfig();});row("Reset size","620×128",b->{mod.CONFIG.width=620;mod.CONFIG.height=128;mod.saveConfig();});row("Reload themes","Run",b->{mod.THEMES.reload();init();});}
 private void hotkeys(){int y=66;String[] names={"Play / Pause","Previous","Next","Volume +","Volume -","Show / Hide","Music Center","Settings"};net.minecraft.client.KeyMapping[] maps={KeyBindings.playPause,KeyBindings.previous,KeyBindings.next,KeyBindings.volumeUp,KeyBindings.volumeDown,KeyBindings.toggle,KeyBindings.library,KeyBindings.settings};for(int i=0;i<maps.length;i++){final int idx=i;String key=maps[i].getTranslatedKeyMessage().getString();addRenderableWidget(Button.builder(Component.literal(names[i]+": "+(listening!=null&&listening.equals(names[i])?"PRESS KEY":key)),b->{listening=names[idx];}).bounds(28,y,330,22).build());y+=28;}if(listening!=null)addRenderableWidget(Button.builder(Component.literal("Press any key… (Esc cancels)"),b->{}).bounds(370,66,210,22).build());}
 @Override public boolean keyPressed(int key,int scancode,int mods){if(listening!=null){if(key==GLFW.GLFW_KEY_ESCAPE){listening=null;init();return true;}net.minecraft.client.KeyMapping[] maps={KeyBindings.playPause,KeyBindings.previous,KeyBindings.next,KeyBindings.volumeUp,KeyBindings.volumeDown,KeyBindings.toggle,KeyBindings.library,KeyBindings.settings};String[] names={"Play / Pause","Previous","Next","Volume +","Volume -","Show / Hide","Music Center","Settings"};for(int i=0;i<names.length;i++)if(names[i].equals(listening)){KeyBindings.set(maps[i],key);KeyBindings.syncConfig();break;}listening=null;init();return true;}return super.keyPressed(key,scancode,mods);}
 private void nextVisualizer(){String[] a={"bars","waveform","dots","particles","radial","rings","oscilloscope","mountains"};int i=Arrays.asList(a).indexOf(mod.CONFIG.visualizer);mod.CONFIG.visualizer=a[(i+1)%a.length];mod.saveConfig();init();}
 private void nextCover(){String[] a={"static","vinyl","cd","cassette","pulse","floating","spectrum"};int i=Arrays.asList(a).indexOf(mod.CONFIG.coverMode);mod.CONFIG.coverMode=a[(i+1)%a.length];mod.saveConfig();init();}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){renderBackground(g);g.drawString(font,"Music Player • Settings Center",18,10,0xFFFFFF);g.drawString(font,tab,18,52,0xA8ABB4);super.render(g,mx,my,delta);}
}
