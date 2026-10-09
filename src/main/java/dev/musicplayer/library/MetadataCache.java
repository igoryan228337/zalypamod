package dev.musicplayer.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Persistent lightweight metadata/artwork cache. Invalidated by file size + mtime. */
public final class MetadataCache {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Entry> entries = new HashMap<>();
    public MetadataCache(Path file){this.file=file;load();}
    public synchronized Entry get(Path p){
        Entry e=entries.get(key(p));
        if(e==null)return null;
        try{return e.size==Files.size(p)&&e.modified==Files.getLastModifiedTime(p).toMillis()?e:null;}
        catch(IOException x){return null;}
    }
    public synchronized void put(Path p, Entry e){entries.put(key(p),e);}
    public synchronized void prune(Set<String> live){entries.keySet().removeIf(k->!live.contains(k));}
    public synchronized void save(){try{Files.createDirectories(file.getParent());Files.writeString(file,gson.toJson(entries));}catch(IOException ignored){}}
    private void load(){try{if(Files.exists(file)){var m=gson.fromJson(Files.readString(file),Map.class);if(m!=null){var parsed=gson.fromJson(Files.readString(file),new com.google.gson.reflect.TypeToken<Map<String,Entry>>(){}.getType());if(parsed!=null)entries.putAll(parsed);}}}catch(Exception ignored){entries.clear();}}
    private static String key(Path p){return p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);}
    public static final class Entry{
        public long size,modified,durationMs; public String title="",artist="",album="",genre="",artwork=""; public int trackNumber;
        public Entry(){}
    }
}
