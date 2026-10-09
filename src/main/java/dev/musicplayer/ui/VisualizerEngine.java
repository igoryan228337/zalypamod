package dev.musicplayer.ui;

/** Shared, throttled FFT post-processing for all visualizers. */
public final class VisualizerEngine {
    private static float[] smooth=new float[0],peak=new float[0],cached=new float[0];
    private static long lastNanos,lastInputNanos;
    private VisualizerEngine(){}
    public static synchronized float[] values(float[] input,double smoothing){
        if(input==null||input.length==0)return new float[0];
        long now=System.nanoTime();
        if(smooth.length!=input.length){smooth=new float[input.length];peak=new float[input.length];cached=new float[input.length];}
        // Cap expensive visualizer post-processing to ~60 Hz while allowing the audio engine to sample independently.
        if(now-lastInputNanos<14_000_000L)return cached.clone();
        lastInputNanos=now;float k=(float)Math.max(.02,Math.min(1,1.0-smoothing));
        for(int i=0;i<input.length;i++){float v=Math.max(0,Math.min(1,input[i]));smooth[i]+= (v-smooth[i])*k;peak[i]=Math.max(smooth[i],peak[i]-.018f);cached[i]=smooth[i];}
        lastNanos=now;return cached.clone();
    }
    public static synchronized float peak(int i){return i<0||i>=peak.length?0:peak[i];}
    public static float band(float[] v,double from,double to){if(v==null||v.length==0)return 0;int a=(int)Math.max(0,Math.min(v.length-1,from*v.length));int b=(int)Math.max(a+1,Math.min(v.length,to*v.length));float sum=0;for(int i=a;i<b;i++)sum+=v[i];return Math.min(1,sum/Math.max(1,b-a));}
    public static float bass(float[] v){return band(v,0,.18);} public static float mid(float[] v){return band(v,.18,.58);} public static float treble(float[] v){return band(v,.58,1);}
    public static double lastUpdateSeconds(){return lastNanos==0?Double.POSITIVE_INFINITY:(System.nanoTime()-lastNanos)/1e9;}
}
