package dev.musicplayer.client;

import dev.musicplayer.audio.*;import dev.musicplayer.config.PlayerConfig;import dev.musicplayer.library.*;import dev.musicplayer.theme.*;import dev.musicplayer.ui.*;
import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;import net.minecraft.client.Minecraft;
import java.nio.file.*;import java.util.*;import com.google.gson.*;

public final class MusicPlayerClient implements ClientModInitializer{
 public static final MusicPlayerClient INSTANCE=new MusicPlayerClient();
 public static Path gameDir,musicDir,configDir;public static PlayerConfig CONFIG;public static MusicLibrary LIBRARY;public static PlaybackQueue QUEUE;public static JaveAudioEngine AUDIO;public static ThemeManager THEMES;public static PlaylistManager PLAYLISTS;public static PlayHistory HISTORY;
 private int currentIndex=-1;
 private Path queueFile;
 public static MusicPlayerClient get(){return INSTANCE;}
 public void onInitializeClient(){gameDir=Minecraft.getInstance().gameDirectory.toPath();musicDir=gameDir.resolve("music");configDir=gameDir.resolve("musicplayer");CONFIG=PlayerConfig.load(configDir.resolve("config.json"));THEMES=new ThemeManager(gameDir);LIBRARY=new MusicLibrary(musicDir,configDir);QUEUE=new PlaybackQueue();queueFile=configDir.resolve("queue.json");PLAYLISTS=new PlaylistManager(configDir);HISTORY=new PlayHistory(configDir);AUDIO=new JaveAudioEngine(configDir.resolve("cache"));KeyBindings.init();MusicHud.init();ScreenOverlay.init();LIBRARY.reloadAsync(this::loadQueue);ClientTickEvents.END_CLIENT_TICK.register(this::tick);Runtime.getRuntime().addShutdownHook(new Thread(this::close,"MusicPlayer-Shutdown"));}
 private void tick(Minecraft mc){while(KeyBindings.playPause.consumeClick())togglePlay();while(KeyBindings.previous.consumeClick())previous();while(KeyBindings.next.consumeClick())next();while(KeyBindings.volumeUp.consumeClick())setVolume(CONFIG.volume+.05f);while(KeyBindings.volumeDown.consumeClick())setVolume(CONFIG.volume-.05f);while(KeyBindings.toggle.consumeClick()){CONFIG.visible=!CONFIG.visible;saveConfig();}while(KeyBindings.library.consumeClick())mc.setScreen(new MusicScreen());while(KeyBindings.settings.consumeClick())mc.setScreen(new dev.musicplayer.ui.SettingsCenterScreen());HudInteraction.tick();Track audioError=AUDIO.takeError();if(audioError!=null){String msg=AUDIO.errorMessage();NotificationManager.show("Audio error",audioError.title,msg);Track recovered=QUEUE.next();if(recovered!=null){currentIndex=LIBRARY.all().indexOf(recovered);AUDIO.play(recovered);LIBRARY.markPlayed(recovered);HISTORY.record(recovered);saveQueue();}else AUDIO.stop();}else{Track finished=AUDIO.takeFinished();if(finished!=null)next();}}
 public synchronized void play(Track t){if(t==null)return;List<Track> list=LIBRARY.all();currentIndex=list.indexOf(t);if(QUEUE.items().isEmpty())QUEUE.set(list,currentIndex,CONFIG.shuffle,CONFIG.repeat);else {QUEUE.shuffle(CONFIG.shuffle);QUEUE.repeat(CONFIG.repeat);QUEUE.playNow(t);}saveQueue();AUDIO.setVolume(CONFIG.volume);AUDIO.play(t);LIBRARY.markPlayed(t);HISTORY.record(t);NotificationManager.show("Now playing",t.title,t.artist);}
 public synchronized void togglePlay(){if(AUDIO.current()==null){List<Track> a=LIBRARY.all();if(!a.isEmpty())play(a.get(0));return;}if(AUDIO.playing())AUDIO.pause();else AUDIO.resume();}
 public synchronized void next(){Track t=QUEUE.next();if(t!=null){currentIndex=LIBRARY.all().indexOf(t);AUDIO.play(t);LIBRARY.markPlayed(t);HISTORY.record(t);saveQueue();NotificationManager.show("Now playing",t.title,t.artist);}}
 public synchronized void previous(){if(AUDIO.current()!=null&&AUDIO.positionMs()>3000){seek(0);return;}Track t=QUEUE.previous();if(t!=null){currentIndex=LIBRARY.all().indexOf(t);AUDIO.play(t);LIBRARY.markPlayed(t);saveQueue();}}
 public void setVolume(float v){CONFIG.volume=Math.max(0,Math.min(1,v));AUDIO.setVolume(CONFIG.volume);saveConfig();}
 public void seek(long ms){AUDIO.seek(Math.max(0,ms));}
 public void queue(Track t){QUEUE.add(t);saveQueue();NotificationManager.show("Queue","Added to queue",t.title);}
 public void queueNext(Track t){QUEUE.addNext(t);saveQueue();NotificationManager.show("Queue","Playing next",t.title);}
 public void removeQueue(int i){QUEUE.remove(i);saveQueue();}
 public void moveQueue(int from,int to){QUEUE.moveTo(from,to);saveQueue();}
 public void clearQueue(){QUEUE.clear();saveQueue();}
 public int removeQueueDuplicates(){int n=QUEUE.removeDuplicates();saveQueue();return n;}
 public int removePlayedQueue(){int n=QUEUE.removeBeforeCurrent();saveQueue();return n;}
 public void moveCurrentToFront(){QUEUE.moveCurrentToFront();saveQueue();}
 public void setShuffle(boolean x){CONFIG.shuffle=x;QUEUE.shuffle(x);saveConfig();saveQueue();}
 public void cycleRepeat(){String r=CONFIG.repeat;CONFIG.repeat="off".equals(r)?"all":"all".equals(r)?"one":"off";QUEUE.repeat(CONFIG.repeat);saveConfig();saveQueue();}
 public Track currentTrack(){return AUDIO.current();}
 public Theme theme(){return THEMES.get(CONFIG.theme);}
 public void reloadLibrary(){LIBRARY.reloadAsync(this::restoreQueueTracks);}
 public void saveConfig(){CONFIG.save(configDir.resolve("config.json"));}
 public void saveTheme(Theme t){THEMES.save(t);}
 private void loadQueue(){try{if(!Files.exists(queueFile))return;QueueState s=new Gson().fromJson(Files.readString(queueFile),QueueState.class);if(s==null||s.keys==null)return;restoreQueueTracks(s);}catch(Exception ignored){}}
 private void restoreQueueTracks(){loadQueue();}
 private void restoreQueueTracks(QueueState state){List<Track> all=LIBRARY.all();Map<String,Track> by=new HashMap<>();for(Track t:all)by.put(t.key(),t);List<Track> items=new ArrayList<>();for(String k:state.keys){Track t=by.get(k);if(t!=null)items.add(t);}if(!items.isEmpty()){int idx=Math.max(-1,Math.min(state.index,items.size()-1));QUEUE.set(items,idx,state.shuffle,state.repeat);}}
 private void saveQueue(){try{if(queueFile==null)return;QueueState s=new QueueState();for(Track t:QUEUE.items())s.keys.add(t.key());s.index=QUEUE.index();s.shuffle=QUEUE.shuffle();s.repeat=QUEUE.repeat();Files.createDirectories(queueFile.getParent());Files.writeString(queueFile,new GsonBuilder().setPrettyPrinting().create().toJson(s));}catch(Exception ignored){}}
 private static final class QueueState{List<String> keys=new ArrayList<>();int index=-1;boolean shuffle;String repeat="all";}
 private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
 public void close(){if(!closed.compareAndSet(false,true))return;saveQueue();if(AUDIO!=null)AUDIO.close();if(LIBRARY!=null)LIBRARY.close();TextureCache.clear();}
}
