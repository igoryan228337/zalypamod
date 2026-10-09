package dev.musicplayer.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public final class MusicLibrary {
    private static final Set<String> EXT=Set.of("mp3","flac","ogg","wav","m4a","aac","opus","aiff","ape","wma","m4b","mp4","mka","3gp");
    private final Path root,stateFile,artworkDir;
    private final List<Track> tracks=new ArrayList<>(); private final Set<String> favorites=new HashSet<>(); private final List<String> recent=new ArrayList<>();
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create(); private final MetadataCache metadata;
    private final ExecutorService scanner=Executors.newSingleThreadExecutor(r->new Thread(r,"MusicPlayer-LibraryScan"));
    private volatile boolean scanning; private volatile long scanGeneration;
    private State state=new State();
    public MusicLibrary(Path root,Path configDir){this.root=root;stateFile=configDir.resolve("library.json");artworkDir=configDir.resolve("artwork");metadata=new MetadataCache(configDir.resolve("metadata-cache.json"));}
    public synchronized void reload(){reloadInternal();}
    public void reloadAsync(Runnable finished){long token=++scanGeneration;scanning=true;scanner.submit(()->{try{reloadInternal();}finally{scanning=false;if(token==scanGeneration&&finished!=null)finished.run();}});}
    public boolean isScanning(){return scanning;}
    private void reloadInternal(){List<Track> next=new ArrayList<>();loadState();try{Files.createDirectories(root);}catch(IOException ignored){}Set<String> live=new HashSet<>();try(var s=Files.walk(root)){s.filter(Files::isRegularFile).filter(this::supported).sorted().forEach(p->{live.add(key(p));Track t=read(p);t.favorite=favorites.contains(t.key());next.add(t);});}catch(IOException ignored){}synchronized(this){tracks.clear();tracks.addAll(next);}metadata.prune(live);metadata.save();saveState();}
    private boolean supported(Path p){String n=p.getFileName().toString();int i=n.lastIndexOf('.');return i>0&&EXT.contains(n.substring(i+1).toLowerCase(Locale.ROOT));}
    private Track read(Path p){Track t=new Track(p);MetadataCache.Entry c=metadata.get(p);if(c!=null){apply(t,c);return t;}try{AudioFile f=AudioFileIO.read(p.toFile());Tag tag=f.getTag();if(tag!=null){String v=tag.getFirst("TITLE");if(v!=null&&!v.isBlank())t.title=v;v=tag.getFirst("ARTIST");if(v!=null&&!v.isBlank())t.artist=v;v=tag.getFirst("ALBUM");if(v!=null&&!v.isBlank())t.album=v;v=tag.getFirst("GENRE");if(v!=null&&!v.isBlank())t.genre=v;v=tag.getFirst("TRACK");try{t.trackNumber=Integer.parseInt((v==null?"":v).replaceAll("[^0-9].*",""));}catch(Exception ignored){}}t.durationMs=f.getAudioHeader().getTrackLength()*1000L;}catch(Exception ignored){}t.artwork=sidecar(p);if(t.artwork==null)t.artwork=embeddedArtwork(p,t.key());MetadataCache.Entry e=new MetadataCache.Entry();try{e.size=Files.size(p);e.modified=Files.getLastModifiedTime(p).toMillis();}catch(IOException ignored){}e.title=t.title;e.artist=t.artist;e.album=t.album;e.genre=t.genre;e.trackNumber=t.trackNumber;e.durationMs=t.durationMs;e.artwork=t.artwork==null?"":t.artwork.toString();metadata.put(p,e);return t;}
    private void apply(Track t,MetadataCache.Entry e){t.title=e.title;t.artist=e.artist;t.album=e.album;t.genre=e.genre;t.trackNumber=e.trackNumber;t.durationMs=e.durationMs;Path side=sidecar(t.file);t.artwork=side!=null?side:(e.artwork==null||e.artwork.isBlank()?null:Path.of(e.artwork));if(t.artwork!=null&&!Files.isRegularFile(t.artwork))t.artwork=null;}
    private Path sidecar(Path p){String n=p.getFileName().toString();int i=n.lastIndexOf('.');String base=i>0?n.substring(0,i):n;for(String ext:List.of("png","jpg","jpeg","webp")){Path x=p.resolveSibling(base+"."+ext);if(Files.isRegularFile(x))return x;}return null;}
    private Path embeddedArtwork(Path p,String key){try{AudioFile f=AudioFileIO.read(p.toFile());Tag tag=f.getTag();if(tag==null||tag.getArtworkList()==null||tag.getArtworkList().isEmpty())return null;var art=tag.getArtworkList().get(0);byte[] data=art.getBinaryData();if(data==null||data.length==0)return null;Files.createDirectories(artworkDir);String ext="jpg";String mime=art.getMimeType();if(mime!=null&&mime.contains("png"))ext="png";else if(mime!=null&&mime.contains("webp"))ext="webp";Path out=artworkDir.resolve(Integer.toHexString(key.hashCode())+"."+ext);if(!Files.isRegularFile(out))Files.write(out,data);return out;}catch(Exception ignored){return null;}}
    private static String key(Path p){return p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);}
    public synchronized List<Track> all(){return List.copyOf(tracks);}
    public synchronized List<Track> search(String q){if(q==null||q.isBlank())return all();String[] tokens=normalize(q).split("\\s+");return tracks.stream().filter(t->{String hay=normalize(t.title+" "+t.artist+" "+t.album+" "+t.genre+" "+t.file.getFileName());for(String token:tokens)if(!token.isBlank()&&!hay.contains(token))return false;return true;}).sorted(Comparator.comparingInt(t->searchScore(t,tokens)).reversed().thenComparing(t->t.title,String.CASE_INSENSITIVE_ORDER)).toList();}
    private static String normalize(Object v){String s=String.valueOf(v==null?"":v).toLowerCase(Locale.ROOT);return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");}
    private static int searchScore(Track t,String[] tokens){String title=normalize(t.title),artist=normalize(t.artist),album=normalize(t.album),file=normalize(t.file.getFileName());int score=0;for(String token:tokens){if(token.isBlank())continue;if(title.equals(token))score+=100;else if(title.startsWith(token))score+=60;else if(title.contains(token))score+=40;if(artist.contains(token))score+=25;if(album.contains(token))score+=20;if(file.contains(token))score+=10;}return score;}
    public synchronized List<String> artistNames(String q){String n=normalize(q);return tracks.stream().map(Track::displayArtist).distinct().filter(v->n.isBlank()||normalize(v).contains(n)).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    public synchronized List<String> albumNames(String q){String n=normalize(q);return tracks.stream().map(Track::displayAlbum).distinct().filter(v->n.isBlank()||normalize(v).contains(n)).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    public synchronized Path artistArtwork(String a){return tracks.stream().filter(t->t.displayArtist().equalsIgnoreCase(a)).map(t->t.artwork).filter(Objects::nonNull).findFirst().orElse(null);}
    public synchronized Path albumArtwork(String a){return tracks.stream().filter(t->t.displayAlbum().equalsIgnoreCase(a)).map(t->t.artwork).filter(Objects::nonNull).findFirst().orElse(null);}
    public synchronized List<Track> artistTracks(String a){return tracks.stream().filter(t->t.displayArtist().equalsIgnoreCase(a)).sorted(Comparator.<Track>comparingInt(t->t.trackNumber<=0?Integer.MAX_VALUE:t.trackNumber).thenComparing(t->t.title,String.CASE_INSENSITIVE_ORDER)).toList();}
    public synchronized List<Track> albumTracks(String a){return tracks.stream().filter(t->t.displayAlbum().equalsIgnoreCase(a)).sorted(Comparator.<Track>comparingInt(t->t.trackNumber<=0?Integer.MAX_VALUE:t.trackNumber).thenComparing(t->t.title,String.CASE_INSENSITIVE_ORDER)).toList();}
    public synchronized List<Track> artists(String q){return artistNames(q).stream().flatMap(a->artistTracks(a).stream()).toList();}
    public synchronized List<Track> albums(String q){return albumNames(q).stream().flatMap(a->albumTracks(a).stream()).toList();}
    public synchronized List<Track> favorites(){return tracks.stream().filter(t->favorites.contains(t.key())).toList();}
    public synchronized List<Track> recent(){Map<String,Track> m=tracks.stream().collect(Collectors.toMap(Track::key,t->t,(a,b)->a));return recent.stream().map(m::get).filter(Objects::nonNull).toList();}
    public synchronized void toggleFavorite(Track t){if(!favorites.remove(t.key()))favorites.add(t.key());t.favorite=favorites.contains(t.key());saveState();}
    public synchronized boolean isFavorite(Track t){return favorites.contains(t.key());}
    public synchronized void markPlayed(Track t){recent.remove(t.key());recent.add(0,t.key());while(recent.size()>100)recent.remove(recent.size()-1);saveState();}
    private void loadState(){try{if(Files.exists(stateFile)){state=gson.fromJson(Files.readString(stateFile),State.class);if(state==null)state=new State();favorites.clear();favorites.addAll(state.favorites==null?List.of():state.favorites);recent.clear();recent.addAll(state.recent==null?List.of():state.recent);}}catch(Exception ignored){state=new State();}}
    private void saveState(){try{Files.createDirectories(stateFile.getParent());state.favorites=new ArrayList<>(favorites);state.recent=new ArrayList<>(recent);Files.writeString(stateFile,gson.toJson(state));}catch(IOException ignored){}}
    public void close(){scanGeneration++;scanner.shutdownNow();metadata.save();}
    private static final class State{List<String> favorites=new ArrayList<>();List<String> recent=new ArrayList<>();}
}
