package dev.musicplayer.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PlaylistManager {
    private final Path file;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private final List<Playlist> playlists=new ArrayList<>();
    public PlaylistManager(Path configDir){file=configDir.resolve("playlists.json");load();}
    public synchronized List<Playlist> all(){return List.copyOf(playlists);}
    public synchronized Playlist create(String name){String n=name==null||name.isBlank()?"New Playlist":name.trim();String base=n;int i=2;while(find(n)!=null)n=base+" "+i++;Playlist p=new Playlist(UUID.randomUUID().toString(),n);playlists.add(p);save();return p;}
    public synchronized void rename(Playlist p,String name){if(p==null||name==null||name.isBlank())return;p.name=name.trim();save();}
    public synchronized void description(Playlist p,String value){if(p!=null){p.description=value==null?"":value;save();}}
    public synchronized void cover(Playlist p,Path image){if(p!=null){p.coverPath=image==null?"":image.toAbsolutePath().toString();save();}}
    public synchronized void sortByTitle(Playlist p,List<Track> library){if(p==null)return;Map<String,Track> m=new HashMap<>();for(Track t:library)m.put(t.key(),t);p.trackKeys.sort(Comparator.comparing(k->{Track t=m.get(k);return t==null?"":t.title;},String.CASE_INSENSITIVE_ORDER));save();}
    public synchronized void exportTo(Playlist p,Path target){if(p==null||target==null)return;try{Files.createDirectories(target.getParent());Files.writeString(target,gson.toJson(p));}catch(IOException ignored){}}
    public synchronized Playlist importFrom(Path source){try{Playlist p=gson.fromJson(Files.readString(source),Playlist.class);if(p==null)return null;p.id=UUID.randomUUID().toString();String base=p.name==null||p.name.isBlank()?"Imported Playlist":p.name;String n=base;int i=2;while(find(n)!=null)n=base+" "+i++;p.name=n;playlists.add(p);save();return p;}catch(Exception ignored){return null;}}
    public synchronized void delete(Playlist p){if(p!=null){playlists.remove(p);save();}}
    public synchronized Playlist find(String name){for(Playlist p:playlists)if(p.name.equalsIgnoreCase(name))return p;return null;}
    public synchronized void add(Playlist p,Track t){if(p!=null&&t!=null&&!p.trackKeys.contains(t.key())){p.trackKeys.add(t.key());save();}}
    public synchronized void addAll(Playlist p,Collection<Track> ts){if(p==null)return;for(Track t:ts)add(p,t);save();}
    public synchronized void remove(Playlist p,Track t){if(p!=null&&t!=null){p.trackKeys.remove(t.key());save();}}
    public synchronized void move(Playlist p,int from,int to){if(p==null||from<0||to<0||from>=p.trackKeys.size()||to>=p.trackKeys.size()||from==to)return;String k=p.trackKeys.remove(from);p.trackKeys.add(to,k);save();}
    public synchronized void clear(Playlist p){if(p!=null){p.trackKeys.clear();save();}}
    public synchronized void removeAll(Playlist p,Collection<Track> ts){if(p==null||ts==null)return;Set<String> keys=new HashSet<>();for(Track t:ts)if(t!=null)keys.add(t.key());p.trackKeys.removeIf(keys::contains);save();}
    public synchronized List<Track> tracks(Playlist p,List<Track> library){if(p==null)return List.of();Map<String,Track> m=new HashMap<>();for(Track t:library)m.put(t.key(),t);List<Track> r=new ArrayList<>();for(String k:p.trackKeys){Track t=m.get(k);if(t!=null)r.add(t);}return r;}
    private void load(){try{if(Files.exists(file)){PlaylistState s=gson.fromJson(Files.readString(file),PlaylistState.class);playlists.clear();if(s!=null&&s.playlists!=null)playlists.addAll(s.playlists);}}catch(Exception ignored){playlists.clear();}}
    private void save(){try{Files.createDirectories(file.getParent());PlaylistState s=new PlaylistState();s.playlists=new ArrayList<>(playlists);Files.writeString(file,gson.toJson(s));}catch(IOException ignored){}}
    public static final class Playlist{public String id,name,description,coverPath;public List<String> trackKeys=new ArrayList<>();public Playlist(){}Playlist(String id,String name){this.id=id;this.name=name;this.description="";this.coverPath="";}}
    private static final class PlaylistState{List<Playlist> playlists=new ArrayList<>();}
}
