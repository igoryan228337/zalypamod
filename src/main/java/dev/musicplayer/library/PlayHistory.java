package dev.musicplayer.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Persistent play history with counts and timestamps. */
public final class PlayHistory {
    private final Path file;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private final List<Entry> entries=new ArrayList<>();
    private final Map<String,Integer> counts=new HashMap<>();
    public PlayHistory(Path configDir){file=configDir.resolve("history.json");load();}
    public synchronized void record(Track t){if(t==null)return;String k=t.key();entries.removeIf(e->k.equals(e.key));entries.add(0,new Entry(k,System.currentTimeMillis()));counts.put(k,counts.getOrDefault(k,0)+1);while(entries.size()>500)entries.remove(entries.size()-1);save();}
    public synchronized List<Entry> entries(){return List.copyOf(entries);}
    public synchronized int count(Track t){return t==null?0:counts.getOrDefault(t.key(),0);}
    public synchronized int totalPlays(){return counts.values().stream().mapToInt(Integer::intValue).sum();}
    public synchronized long lastPlayed(Track t){if(t==null)return 0;for(Entry e:entries)if(t.key().equals(e.key))return e.timestamp;return 0;}
    public synchronized List<Track> recent(List<Track> library){Map<String,Track> map=new HashMap<>();for(Track t:library)map.put(t.key(),t);List<Track> out=new ArrayList<>();for(Entry e:entries){Track t=map.get(e.key);if(t!=null)out.add(t);}return out;}
    public synchronized void clear(){entries.clear();counts.clear();save();}
    private void load(){try{if(!Files.exists(file))return;State s=gson.fromJson(Files.readString(file),State.class);if(s!=null){entries.clear();if(s.entries!=null)entries.addAll(s.entries);counts.clear();if(s.counts!=null)counts.putAll(s.counts);}}catch(Exception ignored){}}
    private void save(){try{Files.createDirectories(file.getParent());State s=new State();s.entries=new ArrayList<>(entries);s.counts=new HashMap<>(counts);Files.writeString(file,gson.toJson(s));}catch(IOException ignored){}}
    public static final class Entry{public String key;public long timestamp;public Entry(){}Entry(String k,long t){key=k;timestamp=t;}public String timeText(){return Instant.ofEpochMilli(timestamp).toString();}}
    private static final class State{List<Entry> entries=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();}
}
