package dev.musicplayer.audio;

import dev.musicplayer.library.Track;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Separate audio engine. JAVE/FFmpeg decodes to PCM WAV; Java Sound owns playback. */
public final class JaveAudioEngine implements AudioEngine {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"MusicPlayer-AudioDecode"));
    private final ScheduledExecutorService watcher=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"MusicPlayer-AudioWatch"));
    private final Path cache;
    private final AtomicLong generation=new AtomicLong();
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Track current, finished, errorTrack;
    private volatile String errorMessage="";
    private volatile Clip clip;
    private volatile float volume=.35f;
    private volatile boolean loading;
    private volatile Path activeWav;
    private volatile long spectrumStamp=-1;
    private volatile long spectrumWallStamp=-1;
    private static final int FFT_N=512;
    private volatile float[] spectrumCache=new float[48];

    public JaveAudioEngine(Path cache){
        this.cache=cache;
        try{Files.createDirectories(cache); cleanupOldCache();}catch(IOException ignored){}
        watcher.scheduleAtFixedRate(this::checkFinished,150,150,TimeUnit.MILLISECONDS);
    }

    public synchronized void play(Track t){
        if(closed.get()||t==null)return;
        long token=generation.incrementAndGet();
        stopClip();
        finished=null; errorTrack=null; errorMessage="";
        current=t; loading=true; spectrumStamp=-1;
        worker.submit(()->decodeAndPlay(t,token));
    }

    private void decodeAndPlay(Track t,long token){
        Path wav=null;
        try{
            Files.createDirectories(cache);
            String safe=Integer.toHexString(t.key().hashCode());
            wav=cache.resolve(safe+".wav");
            if(!Files.isRegularFile(wav)||Files.size(wav)<44){
                AudioAttributes aa=new AudioAttributes();
                aa.setCodec("pcm_s16le"); aa.setBitRate(1411200); aa.setChannels(2); aa.setSamplingRate(44100);
                EncodingAttributes ea=new EncodingAttributes(); ea.setFormat("wav"); ea.setAudioAttributes(aa);
                new Encoder().encode(new MultimediaObject(t.file.toFile()),wav.toFile(),ea);
            }
            if(!isCurrent(t,token))return;
            Clip c=AudioSystem.getClip();
            try(AudioInputStream in=AudioSystem.getAudioInputStream(wav.toFile())) { c.open(in); }
            c.addLineListener(e->{
                if(e.getType()==LineEvent.Type.STOP && c.getMicrosecondLength()>0 &&
                   c.getMicrosecondPosition()>=Math.max(0,c.getMicrosecondLength()-50000) &&
                   current==t && generation.get()==token){ finished=t; }
            });
            setClipVolume(c,volume);
            synchronized(this){
                if(!isCurrent(t,token)){c.close();return;}
                clip=c; activeWav=wav; loading=false; spectrumStamp=-1; spectrumWallStamp=-1; c.start();
            }
        }catch(Exception e){
            synchronized(this){
                if(!isCurrent(t,token))return;
                loading=false; errorTrack=t; errorMessage=shortError(e);
            }
            System.err.println("[MusicPlayer] Audio error for "+t.title+": "+errorMessage);
        }
    }

    private boolean isCurrent(Track t,long token){return !closed.get() && current==t && generation.get()==token;}
    private String shortError(Exception e){
        Throwable x=e; while(x.getCause()!=null && x.getCause()!=x)x=x.getCause();
        String m=x.getMessage(); return m==null||m.isBlank()?x.getClass().getSimpleName():m;
    }
    private void setClipVolume(Clip c,float v){
        try{FloatControl g=(FloatControl)c.getControl(FloatControl.Type.MASTER_GAIN);
            float db=(float)(20*Math.log10(Math.max(.0001,v)));
            g.setValue(Math.max(g.getMinimum(),Math.min(g.getMaximum(),db)));
        }catch(Exception ignored){}
    }
    private synchronized void stopClip(){
        Clip c=clip; clip=null; activeWav=null; loading=false;
        if(c!=null){try{c.stop();}catch(Exception ignored){} try{c.close();}catch(Exception ignored){}}
    }
    public synchronized void pause(){if(clip!=null&&clip.isRunning())clip.stop();}
    public synchronized void resume(){if(clip!=null&&clip.isOpen()&&!clip.isRunning()&&finished==null)clip.start();}
    public synchronized void stop(){generation.incrementAndGet();stopClip();current=null;finished=null;errorTrack=null;errorMessage="";}
    public synchronized void seek(long ms){
        if(clip==null)return;
        long target=Math.max(0,Math.min(ms,clip.getMicrosecondLength()/1000));
        clip.setMicrosecondPosition(target*1000);
        if(target<durationMs()-100)finished=null;
    }
    public synchronized void setVolume(float v){volume=Math.max(0,Math.min(1,v));if(clip!=null)setClipVolume(clip,volume);}
    public boolean playing(){Clip c=clip;return c!=null&&c.isRunning();}
    public long positionMs(){Clip c=clip;return c==null?0:c.getMicrosecondPosition()/1000;}
    public long durationMs(){Clip c=clip;return c!=null?c.getMicrosecondLength()/1000:(current==null?0:current.durationMs);}

    public float[] spectrum(){
        Clip c=clip; if(c==null||activeWav==null||!c.isOpen())return new float[48];
        long stamp=c.getMicrosecondPosition()/50000; long wall=System.nanoTime()/50_000_000L; if(stamp==spectrumStamp&&wall==spectrumWallStamp)return spectrumCache.clone(); spectrumStamp=stamp; spectrumWallStamp=wall;
        float[] out=new float[48];
        try(RandomAccessFile raf=new RandomAccessFile(activeWav.toFile(),"r")){
            long frame=c.getMicrosecondPosition()*44100L/1_000_000L; long pos=44+frame*4;
            raf.seek(Math.max(44,pos-2048*2)); byte[] b=new byte[FFT_N*4]; int n=raf.read(b); int samples=n/2; int N=FFT_N;
            double[] re=new double[N];
            for(int i=0;i<Math.min(N,samples);i++){int lo=b[i*2]&255,hi=b[i*2+1];short v=(short)((hi<<8)|lo);re[i]=v/32768.0*(0.5-0.5*Math.cos(2*Math.PI*i/(N-1)));}
            for(int k=0;k<48;k++){int bin=1+(int)Math.pow((N/2.0-1),k/47.0);double rr=0,ii=0;for(int j=0;j<N;j++){double a=2*Math.PI*bin*j/N;rr+=re[j]*Math.cos(a);ii-=re[j]*Math.sin(a);}out[k]=(float)Math.min(1,Math.sqrt(rr*rr+ii*ii)/40.0);}
        }catch(Exception ignored){}
        spectrumCache=out;return out.clone();
    }
    public Track current(){return current;}
    public Track takeFinished(){Track t=finished;finished=null;return t;}
    public Track takeError(){Track t=errorTrack;errorTrack=null;return t;}
    public String errorMessage(){return errorMessage;}
    public boolean loading(){return loading;}
    private void checkFinished(){
        Clip c=clip; Track t=current;
        if(t!=null&&c!=null&&!loading&&!c.isRunning()&&c.isOpen()&&c.getMicrosecondLength()>0&&
           c.getMicrosecondPosition()>=c.getMicrosecondLength()-50000&&finished==null)finished=t;
    }
    private void cleanupOldCache() throws IOException{try(var s=Files.list(cache)){long cutoff=System.currentTimeMillis()-7L*24*60*60*1000;s.filter(p->p.getFileName().toString().endsWith(".wav")).filter(p->{try{return Files.getLastModifiedTime(p).toMillis()<cutoff;}catch(IOException e){return false;}}).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    public void close(){if(!closed.compareAndSet(false,true))return;generation.incrementAndGet();stopClip();watcher.shutdownNow();worker.shutdownNow();}
}
