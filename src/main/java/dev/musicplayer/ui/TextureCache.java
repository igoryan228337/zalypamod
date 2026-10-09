package dev.musicplayer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/** Bounded artwork cache. Evicts old entries and releases DynamicTextures from Minecraft's texture manager. */
public final class TextureCache {
    private static final int MAX_ENTRIES=384;
    private static final LinkedHashMap<String,ResourceLocation> MAP=new LinkedHashMap<>(64,.75f,true);
    private static final Map<String,Long> STAMPS=new HashMap<>();
    private TextureCache(){}
    public static synchronized ResourceLocation get(Path p){
        if(p==null||!Files.isRegularFile(p))return null;
        String k=p.toAbsolutePath().normalize().toString(); long stamp=stamp(p);
        ResourceLocation old=MAP.get(k);
        if(old!=null&&Objects.equals(STAMPS.get(k),stamp))return old;
        if(old!=null)removeInternal(k,old);
        try(InputStream in=Files.newInputStream(p)){
            DynamicTexture t=new DynamicTexture(p.getFileName().toString(),net.minecraft.client.renderer.texture.NativeImage.read(in));
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("musicplayer","file/"+Integer.toHexString((k+stamp).hashCode()));
            Minecraft.getInstance().getTextureManager().register(id,t); MAP.put(k,id);STAMPS.put(k,stamp);evict();return id;
        }catch(Exception e){return null;}
    }
    private static long stamp(Path p){try{return Files.getLastModifiedTime(p).toMillis()^Files.size(p);}catch(Exception e){return -1;}}
    private static void evict(){while(MAP.size()>MAX_ENTRIES){Iterator<Map.Entry<String,ResourceLocation>> it=MAP.entrySet().iterator();if(!it.hasNext())return;var e=it.next();it.remove();STAMPS.remove(e.getKey());try{Minecraft.getInstance().getTextureManager().release(e.getValue());}catch(Exception ignored){}}}
    private static void removeInternal(String k,ResourceLocation id){MAP.remove(k);STAMPS.remove(k);try{Minecraft.getInstance().getTextureManager().release(id);}catch(Exception ignored){}}
    public static synchronized void clear(){for(ResourceLocation id:MAP.values())try{Minecraft.getInstance().getTextureManager().release(id);}catch(Exception ignored){}MAP.clear();STAMPS.clear();}
    public static synchronized int size(){return MAP.size();}
}
