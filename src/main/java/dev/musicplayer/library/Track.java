package dev.musicplayer.library;

import java.nio.file.Path;
import java.util.Locale;

public final class Track {
    public final Path file;
    public String title, artist, album, genre;
    public int trackNumber;
    public long durationMs;
    public Path artwork;
    public boolean favorite;

    public Track(Path file) { this.file=file; this.title=strip(file.getFileName().toString()); this.artist="Unknown artist"; this.album="Unknown album"; this.genre=""; }
    private static String strip(String n) { int i=n.lastIndexOf('.'); return i>0?n.substring(0,i):n; }
    public String displayArtist(){return artist==null||artist.isBlank()?"Unknown artist":artist;}
    public String displayAlbum(){return album==null||album.isBlank()?"Unknown album":album;}
    public String key(){return file.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);}
    public String durationText(){long s=Math.max(0,durationMs/1000); return String.format("%d:%02d",s/60,s%60);}
}
