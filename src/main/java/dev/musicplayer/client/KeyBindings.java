package dev.musicplayer.client;
import dev.musicplayer.config.PlayerConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings{
 public static KeyMapping playPause,previous,next,volumeUp,volumeDown,toggle,library,settings;
 public static void init(){PlayerConfig c=MusicPlayerClient.CONFIG;String cat="key.categories.musicplayer";
  playPause=reg("key.musicplayer.play_pause",c.keyPlayPause,cat);previous=reg("key.musicplayer.previous",c.keyPrevious,cat);next=reg("key.musicplayer.next",c.keyNext,cat);volumeUp=reg("key.musicplayer.volume_up",c.keyVolumeUp,cat);volumeDown=reg("key.musicplayer.volume_down",c.keyVolumeDown,cat);toggle=reg("key.musicplayer.toggle",c.keyToggle,cat);library=reg("key.musicplayer.library",c.keyLibrary,cat);settings=reg("key.musicplayer.settings",c.keySettings,cat);}
 private static KeyMapping reg(String id,int key,String cat){return KeyBindingHelper.registerKeyBinding(new KeyMapping(id,InputConstants.Type.KEYSYM,key,cat));}
 public static void set(KeyMapping mapping,int key){mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));KeyMapping.resetMapping();}
 public static int code(KeyMapping m){return m.getKey().getValue();}
 public static void syncConfig(){PlayerConfig c=MusicPlayerClient.CONFIG;c.keyPlayPause=code(playPause);c.keyPrevious=code(previous);c.keyNext=code(next);c.keyVolumeUp=code(volumeUp);c.keyVolumeDown=code(volumeDown);c.keyToggle=code(toggle);c.keyLibrary=code(library);c.keySettings=code(settings);MusicPlayerClient.get().saveConfig();}
}
