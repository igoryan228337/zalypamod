package dev.musicplayer.audio;
import dev.musicplayer.library.Track;
public interface AudioEngine {
 void play(Track t); void pause(); void resume(); void stop(); void setVolume(float v); void seek(long ms);
 boolean playing(); long positionMs(); long durationMs(); float[] spectrum(); Track current(); Track takeFinished();
 default boolean loading(){return false;}
 default Track takeError(){return null;}
 default String errorMessage(){return "";}
 default boolean paused(){return current()!=null && !playing() && !loading();}
}
