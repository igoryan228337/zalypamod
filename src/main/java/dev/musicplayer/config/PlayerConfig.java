package dev.musicplayer.config;
import com.google.gson.Gson;import com.google.gson.GsonBuilder;import java.io.*;import java.nio.file.*;
public final class PlayerConfig{
 public int x=24,y=24,width=620,height=128;public float volume=.35f;public boolean visible=true,notifications=true,dynamicCoverColors=false,islandEnabled=true,snapToEdges=false,editHint=false,clickThrough=false,autoDynamicColors=false,shuffle=false;public String repeat="all",theme="default",visualizer="bars",coverMode="static";public int keyPlayPause=320,keyPrevious=263,keyNext=262,keyVolumeUp=265,keyVolumeDown=264,keyToggle=344,keyLibrary=296,keySettings=297;
 public static PlayerConfig load(Path f){try{if(Files.exists(f)){PlayerConfig c=new Gson().fromJson(Files.readString(f),PlayerConfig.class);if(c!=null)return c;}}catch(Exception ignored){}return new PlayerConfig();}
 public void save(Path f){try{Files.createDirectories(f.getParent());Files.writeString(f,new GsonBuilder().setPrettyPrinting().create().toJson(this));}catch(IOException ignored){}}
}
