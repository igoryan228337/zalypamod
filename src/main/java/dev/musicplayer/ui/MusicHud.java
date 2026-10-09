package dev.musicplayer.ui;

import dev.musicplayer.client.MusicPlayerClient;
import dev.musicplayer.library.Track;
import dev.musicplayer.theme.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Locale;

/** Runtime renderer for the theme layer system. */
public final class MusicHud {
    private static long startNanos = System.nanoTime();
    private MusicHud() {}
    public static void init(){HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath("musicplayer","player"),(g,d)->render(g));}

    public static void render(GuiGraphics g){
        MusicPlayerClient m=MusicPlayerClient.get();
        if(m.CONFIG==null||!m.CONFIG.visible)return;
        Track t=m.currentTrack(); if(t==null)return;
        Theme th=m.theme(); Minecraft mc=Minecraft.getInstance();
        int sw=g.guiWidth(), sh=g.guiHeight();
        int x=Math.max(4,Math.min(m.CONFIG.x,sw-m.CONFIG.width-4)), y=Math.max(4,Math.min(m.CONFIG.y,sh-m.CONFIG.height-4));
        int w=Math.max(220,m.CONFIG.width), h=Math.max(76,m.CONFIG.height);
        double time=(System.nanoTime()-startNanos)/1_000_000_000.0;

        int bg=parse(th.background.color, alpha(th.background.opacity));
        drawBackground(g,th,x,y,w,h,bg);
        if(th.background.image!=null&&!th.background.image.isBlank()&&th.root!=null){
            ResourceLocation id=TextureCache.get(th.root.resolve(th.background.image));
            if(id!=null) g.blit(id,x,y,w,h,0,0,1,1,1,1);
        }
        if(th.background.dynamicCover && t.artwork!=null){
            int dyn=parse(th.accent, (int)(Math.max(0,Math.min(1,th.background.dynamicStrength))*.22*255));
            g.fill(x,y,x+w,y+h,dyn);
        }

        for(Theme.Layer l: th.layers){
            if(l==null||!l.visible)continue;
            renderLayer(g,m,t,th,l,x,y,w,h,time);
        }
        if(th.island.enabled)drawIsland(g,t,th,sw,time);
        if(NotificationManager.active()&&th.notification.enabled)drawNotification(g,th,sw);
        QueueHud.render(g);
    }

    private static void renderLayer(GuiGraphics g,MusicPlayerClient m,Track t,Theme th,Theme.Layer l,int x,int y,int w,int h,double time){
        double[] kf=keyframed(l,time); double reactive=reactiveValue(m,l); double ax=kf[0], ay=kf[1], aw=Math.max(1,kf[2]), ah=Math.max(1,kf[3]); if(l.reactiveAmount!=0){ double k=1+reactive*l.reactiveAmount; aw*=k; ah*=k; ax-=l.width*(k-1)/2; ay-=l.height*(k-1)/2; }
        double[] anim=animate(l,time); double kscale=kf[6]; aw*=kscale; ah*=kscale; ax-=kf[2]*(kscale-1)/2.0; ay-=kf[3]*(kscale-1)/2.0; ax+=anim[0];ay+=anim[1]; aw*=anim[3]; ah*=anim[3]; ax-=l.width*(anim[3]-1)/2.0; ay-=l.height*(anim[3]-1)/2.0;
        int px=x+(int)Math.round(ax), py=y+(int)Math.round(ay), pw=(int)Math.round(aw), ph=(int)Math.round(ah);
        if(pw<=0||ph<=0)return;
        float rot=(float)(kf[5]+anim[2]+(l.reactive!=null&&l.reactive.equalsIgnoreCase("rotation")?reactive*12*l.reactiveSensitivity:0));
        g.pose().pushPose();
        if(rot!=0){g.pose().translate(px+pw/2f,py+ph/2f,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot));g.pose().translate(-pw/2f,-ph/2f,0);px=0;py=0;}
        String kind=l.kind==null?"auto":l.kind.toLowerCase(Locale.ROOT);
        if(kind.equals("cover")||l.id.equals("cover")){drawCover(g,m,t,th,px,py,pw,ph,time,l);}
        else if(kind.equals("progress")||l.id.equals("progress")){drawProgress(g,m,th,px,py,pw,ph);}
        else if(kind.equals("visualizer")||l.id.equals("visualizer")){drawVisualizer(g,m,th,px,py,pw,ph);}
        else if(kind.equals("controls")||l.id.equals("controls")){drawControls(g,m,th,px,py,pw,ph);}
        else if(kind.equals("volume")||l.id.equals("volume")){drawVolume(g,m,th,px,py,pw,ph);}
        else if(kind.equals("title")||l.id.equals("title")){drawText(g,t.title,th.text,l,px,py,pw,ph);}
        else if(kind.equals("artist")||l.id.equals("artist")){drawText(g,t.displayArtist(),th.secondary,l,px,py,pw,ph);}
        else if(kind.equals("album")||l.id.equals("album")){drawText(g,t.displayAlbum(),th.secondary,l,px,py,pw,ph);}
        else if(kind.equals("time")||l.id.equals("time")){drawText(g,formatTime(m.AUDIO.positionMs())+" / "+formatTime(m.AUDIO.durationMs()),th.secondary,l,px,py,pw,ph);}
        else if(kind.equals("text")){drawText(g,l.text==null?l.label:l.text,l.color,l,px,py,pw,ph);}
        else if(kind.equals("image")){drawImage(g,th,l,px,py,pw,ph);}
        else {drawRect(g,l,px,py,pw,ph,time);}
        g.pose().popPose();
    }

    private static double[] keyframed(Theme.Layer l,double t){
        if(l.keyframes==null||l.keyframes.isEmpty()) return new double[]{l.x,l.y,l.width,l.height,l.opacity,l.rotation,1};
        var ks=l.keyframes; if(ks.size()==1){Theme.Keyframe k=ks.get(0);return new double[]{k.x,k.y,k.width,k.height,k.opacity,k.rotation,k.scale};}
        Theme.Keyframe a=ks.get(0),b=ks.get(ks.size()-1);
        if(t<=a.time)b=a; else if(t>=b.time)a=b; else {for(int i=1;i<ks.size();i++){if(t<=ks.get(i).time){a=ks.get(i-1);b=ks.get(i);break;}}}
        if(a==b)return new double[]{a.x,a.y,a.width,a.height,a.opacity,a.rotation,a.scale};
        double u=Math.max(0,Math.min(1,(t-a.time)/Math.max(1e-6,b.time-a.time)));
        String e=l.easing==null?"smooth":l.easing.toLowerCase(Locale.ROOT); if(e.equals("smooth")||e.equals("ease"))u=u*u*(3-2*u); else if(e.equals("sharp"))u=u*u;
        return new double[]{mix(a.x,b.x,u),mix(a.y,b.y,u),mix(a.width,b.width,u),mix(a.height,b.height,u),mix(a.opacity,b.opacity,u),mix(a.rotation,b.rotation,u),mix(a.scale,b.scale,u)};
    }
    private static double kfOpacity(Theme.Layer l,double t){double[] k=keyframed(l,t);return Math.max(0,Math.min(1,k[4]*animationOpacity(l,t)));}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}

    private static double reactiveValue(MusicPlayerClient m,Theme.Layer l){ if(l==null||l.reactive==null||l.reactive.equalsIgnoreCase("none"))return 0; float[] s=m.AUDIO.spectrum(); if(s==null||s.length==0)return 0; double v=0; String r=l.reactive.toLowerCase(Locale.ROOT); if(r.equals("volume"))v=m.CONFIG.volume; else if(r.equals("bass"))v=band(s,0,Math.max(1,(int)(s.length*l.reactiveBand))); else if(r.equals("mid"))v=band(s,s.length/4,Math.max(s.length/4+1,(int)(s.length*(.25+l.reactiveBand)))); else if(r.equals("treble"))v=band(s,s.length/2,Math.min(s.length,Math.max(s.length/2+1,(int)(s.length*(.5+l.reactiveBand))))); else v=band(s,0,s.length); return Math.max(0,Math.min(1,v*l.reactiveSensitivity)); }
    private static double band(float[] s,int a,int b){double v=0;int n=0;for(int i=Math.max(0,a);i<Math.min(s.length,b);i++){v+=Math.max(0,s[i]);n++;}return n==0?0:v/n;}
    private static void drawRect(GuiGraphics g,Theme.Layer l,int x,int y,int w,int h,double time){
        int a=(int)(255*Math.max(0,Math.min(1,kfOpacity(l,time))));
        int c1=parse(l.color,a), c2=parse(l.color2==null?l.color:l.color2,a);
        String gr=l.gradient==null?"none":l.gradient.toLowerCase(Locale.ROOT);
        if(gr.equals("vertical")){ for(int yy=0;yy<h;yy++){int c=lerp(c1,c2,yy/(float)Math.max(1,h-1));g.fill(x,y+yy,x+w,y+yy+1,c);} }
        else if(gr.equals("horizontal")){ for(int xx=0;xx<w;xx++){int c=lerp(c1,c2,xx/(float)Math.max(1,w-1));g.fill(x+xx,y,x+xx+1,y+h,c);} }
        else if(gr.equals("diagonal")||gr.equals("angle")){ double ang=Math.toRadians(l==null?45:45); double cs=Math.cos(ang),sn=Math.sin(ang); double min=-(Math.abs(w*cs)+Math.abs(h*sn))/2.0; double span=Math.max(1,Math.abs(w*cs)+Math.abs(h*sn)); for(int yy=0;yy<h;yy++) for(int xx=0;xx<w;xx++){double t=((xx-w/2.0)*cs+(yy-h/2.0)*sn-min)/span;t=Math.max(0,Math.min(1,t));g.fill(x+xx,y+yy,x+xx+1,y+yy+1,lerp(c1,c2,(float)t));} }
        else g.fill(x,y,x+w,y+h,c1);
        if(l.shadow){g.fill(x+2,y+h,x+w+4,y+h+3,0x55000000);g.fill(x+w,y+2,x+w+3,y+h,0x44000000);}
        if(l.glow){int gc=parse(l.color,45);g.fill(x-2,y-2,x+w+2,y,gc);g.fill(x-2,y+h,x+w+2,y+h+2,gc);g.fill(x-2,y,x,y+h,gc);g.fill(x+w,y,x+w+2,y+h,gc);}
    }
    private static int lerp(int a,int b,float t){t=Math.max(0,Math.min(1,t));int aa=(int)(((a>>>24)&255)*(1-t)+((b>>>24)&255)*t);int ar=(int)(((a>>>16)&255)*(1-t)+((b>>>16)&255)*t);int ag=(int)(((a>>>8)&255)*(1-t)+((b>>>8)&255)*t);int ab=(int)((a&255)*(1-t)+(b&255)*t);return (aa<<24)|(ar<<16)|(ag<<8)|ab;}
    private static void drawBackground(GuiGraphics g,Theme th,int x,int y,int w,int h,int fallback){
        String gr=th.background.gradient==null?"none":th.background.gradient.toLowerCase(Locale.ROOT);
        if(gr.equals("vertical")||gr.equals("horizontal")||gr.equals("diagonal")){Theme.Layer l=new Theme.Layer();l.color=th.background.color;l.color2=th.background.color2;l.gradient=gr;l.opacity=th.background.opacity;drawRect(g,l,x,y,w,h,0);}else g.fill(x,y,x+w,y+h,fallback);
    }
    private static void drawText(GuiGraphics g,String s,String fallback,Theme.Layer l,int x,int y,int w,int h){
        String text=(s==null||s.isBlank())?"":s;
        int c=parse(l.color==null||l.color.equals("#FFFFFF")?fallback:l.color,(int)(255*Math.max(0,Math.min(1,l.opacity))));
        Minecraft.getInstance().font.drawInBatch(text, x, y+Math.max(0,(h-Minecraft.getInstance().font.lineHeight)/2), c, l.shadow, g.pose().last().pose(), net.minecraft.client.renderer.MultiBufferSource.BufferSource.immediate(net.minecraft.client.renderer.RenderType.textIntensity(Minecraft.getInstance().font.getId())), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, 0,0);
    }
    private static void drawImage(GuiGraphics g,Theme th,Theme.Layer l,int x,int y,int w,int h){
        if(th.root==null||l.image==null||l.image.isBlank())return;
        ResourceLocation id=TextureCache.get(th.root.resolve(l.image));if(id!=null)drawMaskedTexture(g,id,x,y,w,h,l.mask,l.maskRadius);
    }

    private static void drawMaskedTexture(GuiGraphics g,ResourceLocation id,int x,int y,int w,int h,String mask,int radius){
        String m=mask==null?"none":mask.toLowerCase(Locale.ROOT);
        if(m.equals("none")||m.equals("rectangle")){g.blit(id,x,y,w,h,0,0,1,1,1,1);return;}
        int r=Math.max(1,Math.min(Math.min(w,h)/2, radius));
        // Software masks are intentionally used only for theme-sized textures. They avoid a global stencil state leak.
        for(int yy=0;yy<h;yy++){
            int start=0,end=w;
            if(m.equals("circle")){double dy=yy+0.5-h/2.0;double rr=Math.sqrt(Math.max(0,(w*w)/4.0-dy*dy));start=(int)Math.max(0,w/2-rr);end=(int)Math.min(w,w/2+rr);}
            else if(m.equals("diamond")){double half=Math.max(1,Math.min(w,h)/2.0);double dy=Math.abs(yy+0.5-h/2.0);double span=Math.max(0,1-dy/half)*w/2.0;start=(int)Math.max(0,w/2-span);end=(int)Math.min(w,w/2+span);}
            else if(m.equals("hexagon")){int edge=Math.max(1,w/6);int inset=yy<edge?(edge-yy):yy>=h-edge?(yy-(h-edge-1)):0;start=Math.min(w/2,inset);end=Math.max(start, w-inset);}
            else if(m.equals("rounded")){int top=Math.min(r,yy),bottom=Math.min(r,h-1-yy);int cut=0;if(top<r){double dy=r-top;cut=(int)Math.max(0,r-Math.sqrt(Math.max(0,r*r-dy*dy)));}else if(bottom<r){double dy=r-bottom;cut=(int)Math.max(0,r-Math.sqrt(Math.max(0,r*r-dy*dy)));}start=Math.min(w/2,cut);end=Math.max(start,w-cut);}
            if(end>start)g.blit(id,x+start,y+yy,end-start,1,(float)start/Math.max(1,w),(float)yy/Math.max(1,h),(float)(end-start)/Math.max(1,w),1.0f,1,1);
        }
    }
    private static void drawCover(GuiGraphics g,MusicPlayerClient m,Track t,Theme th,int x,int y,int w,int h,double time,Theme.Layer l){
        Path art=t.artwork; ResourceLocation id=art==null?null:TextureCache.get(art);
        if(id==null){g.fill(x,y,x+w,y+h,parse(th.secondary,140));return;}
        String mode=th.cover.mode==null?"static":th.cover.mode.toLowerCase(Locale.ROOT);
        float bass=VisualizerEngine.bass(VisualizerEngine.values(m.AUDIO.spectrum(),th.visualizer.smoothing));
        double scale=th.cover.scale;
        if(th.cover.reactiveAmount>0) scale*=1+Math.min(.28,Math.max(0,bass)*th.cover.reactiveAmount*th.cover.reactiveSensitivity);
        if(th.cover.pulse||mode.equals("pulse"))scale*=1+Math.sin(time*4)*th.cover.pulseAmount;
        int cx=x+w/2,cy=y+h/2;int dw=(int)(w*scale),dh=(int)(h*scale);
        float angle=th.cover.rotate||mode.equals("vinyl")||mode.equals("cd")?(float)((time*th.cover.rotationSpeed)%360):0;
        boolean circle=mode.equals("circle")||mode.equals("vinyl")||mode.equals("cd");
        g.pose().pushPose();
        if(angle!=0){g.pose().translate(cx,cy,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));g.pose().translate(-cx,-cy,0);}
        if(mode.equals("cassette")){
            int cw=Math.max(2,dw), ch=Math.max(2,(int)(dh*.72)); int cy2=cy-ch/2;
            g.fill(cx-cw/2,cy2,cx+cw/2,cy2+ch,0xFF24262D);
            g.fill(cx-cw/2+8,cy2+8,cx+cw/2-8,cy2+ch-8,0xFF111216);
            int rr=Math.max(5,ch/5);g.fill(cx-cw/4-rr,cy2+ch/2-rr,cx-cw/4+rr,cy2+ch/2+rr,0xFF777A82);g.fill(cx+cw/4-rr,cy2+ch/2-rr,cx+cw/4+rr,cy2+ch/2+rr,0xFF777A82);
            drawMaskedTexture(g,id,cx-dw/2,cy-dh/2,dw,dh,l.mask,l.maskRadius);
        } else if((mode.equals("vinyl")||mode.equals("cd"))&&th.cover.showDisc){
            int disc=Math.max(2,Math.min(dw,dh));
            g.fill(cx-disc/2,cy-disc/2,cx+disc/2,cy+disc/2,0xFF111111);
            for(int r=disc/2;r>4;r-=Math.max(2,disc/18)){
                int shade=30+Math.min(45,r/5); g.fill(cx-r,cy-r,cx+r,cy+r,(0xFF<<24)|(shade<<16)|(shade<<8)|shade);
            }
            drawMaskedTexture(g,id,cx-disc/2,cy-disc/2,disc,disc,"circle",l.maskRadius);
            if(th.cover.showCenter){g.fill(cx-4,cy-4,cx+4,cy+4,0xFF202020);g.fill(cx-1,cy-1,cx+2,cy+2,parse(th.accent,255));}
        } else if(mode.equals("floating")||mode.equals("float")){
            int bob=(int)(Math.sin(time*2.1)*Math.min(8,h*.06));
            drawMaskedTexture(g,id,cx-dw/2,cy-dh/2+bob,dw,dh,circle?"circle":l.mask,l.maskRadius);
        } else if(mode.equals("pulse")){
            drawMaskedTexture(g,id,cx-dw/2,cy-dh/2,dw,dh,circle?"circle":l.mask,l.maskRadius);
        } else {
            drawMaskedTexture(g,id,cx-dw/2,cy-dh/2,dw,dh,circle?"circle":l.mask,l.maskRadius);
        }
        g.pose().popPose();
        if(th.cover.overlayOpacity>0)g.fill(x,y,x+w,y+h,parse(th.cover.overlayColor,(int)(255*Math.min(1,th.cover.overlayOpacity))));
        if(th.cover.borderWidth>0){int bc=parse(th.cover.borderColor,255);int bw=Math.min(8,(int)th.cover.borderWidth);for(int i=0;i<bw;i++){g.fill(x+i,y+i,x+w-i,y+i+1,bc);g.fill(x+i,y+h-i-1,x+w-i,y+h-i,bc);g.fill(x+i,y+i,x+i+1,y+h-i,bc);g.fill(x+w-i-1,y+i,x+w-i,y+h-i,bc);}}
    }
    private static void drawProgress(GuiGraphics g,MusicPlayerClient m,Theme th,int x,int y,int w,int h){long d=Math.max(1,m.AUDIO.durationMs());double f=Math.max(0,Math.min(1,m.AUDIO.positionMs()/(double)d));int cy=y+Math.max(1,h/2);int trackH=Math.max(2,Math.min(6,h/3));int fill=(int)(w*f);g.fill(x,cy-trackH/2,x+w,cy+trackH/2+1,0x66555555);g.fill(x,cy-trackH/2,x+fill,cy+trackH/2+1,parse(th.accent,255));int r=Math.max(2,Math.min(5,h/2));int knobX=x+fill;g.fill(knobX-r,cy-r,knobX+r+1,cy+r+1,parse(th.text,255));}
    private static void drawControls(GuiGraphics g,MusicPlayerClient m,Theme th,int x,int y,int w,int h){var f=Minecraft.getInstance().font;int c=parse(th.text,255);int cy=y+Math.max(0,(h-f.lineHeight)/2);g.drawString(f,"<<",x,cy,c,true);g.drawString(f,m.AUDIO.playing()?"||":">",x+w/2-3,cy,c,true);g.drawString(f,">>",x+w-f.width(">>"),cy,c,true);}
    private static void drawVolume(GuiGraphics g,MusicPlayerClient m,Theme th,int x,int y,int w,int h){g.fill(x,y+Math.max(1,h/2-2),x+w,y+Math.max(3,h/2+2),0x66555555);g.fill(x,y+Math.max(1,h/2-2),x+(int)(w*m.CONFIG.volume),y+Math.max(3,h/2+2),parse(th.accent,255));}
    private static void drawVisualizer(GuiGraphics g,MusicPlayerClient m,Theme th,int x,int y,int w,int h){
        if(!th.visualizer.enabled)return;
        float[] raw=m.AUDIO.spectrum(); if(raw==null||raw.length==0)return;
        float[] s=VisualizerEngine.values(raw,th.visualizer.smoothing);
        String type=th.visualizer.type==null?"bars":th.visualizer.type.toLowerCase(Locale.ROOT);
        int a=(int)(255*Math.max(0,Math.min(1,th.visualizer.opacity))); int c=parse(th.accent,a);
        int n=Math.max(2,Math.min(th.visualizer.barCount,s.length));
        double sens=Math.max(.05,th.visualizer.sensitivity);
        if(type.equals("wave")||type.equals("waveform")||type.equals("oscilloscope")){
            int prevX=x,prevY=y+h/2; for(int i=0;i<n;i++){int xx=x+(int)(i/(double)(n-1)*Math.max(1,w-1));float v=Math.max(0,Math.min(1,s[(int)(i/(double)n*s.length)]*sens));int yy=y+h/2-(int)(v*h*.45);if(i>0)line(g,prevX,prevY,xx,yy,c,Math.max(1,(int)th.visualizer.waveThickness));prevX=xx;prevY=yy;} return;
        }
        if(type.equals("radial")||type.equals("circle")||type.equals("rings")){
            int cx=x+w/2,cy=y+h/2; double base=Math.min(w,h)*th.visualizer.radius;
            int rings=type.equals("rings")?Math.max(1,Math.min(8,th.visualizer.rings)):1;
            for(int ring=0;ring<rings;ring++){double radius=base*(1+ring*th.visualizer.ringGap);for(int i=0;i<n;i++){double a0=Math.PI*2*i/n+System.nanoTime()/1e9*.05*th.visualizer.rotationSpeed;float v=Math.max(0,Math.min(1,s[i%s.length]*sens));double r1=radius+v*Math.min(w,h)*.22;line(g,cx+(int)(Math.cos(a0)*radius),cy+(int)(Math.sin(a0)*radius),cx+(int)(Math.cos(a0)*r1),cy+(int)(Math.sin(a0)*r1),c,Math.max(1,(int)th.visualizer.thickness));}} return;
        }
        if(type.equals("particles")||type.equals("dots")){
            int count=Math.max(8,(int)(n*th.visualizer.particleDensity)); double t=System.nanoTime()/1e9*th.visualizer.particleSpeed;
            for(int i=0;i<count;i++){double q=(i*.61803398875)%1;int band=Math.min(s.length-1,(int)(q*s.length));double v=Math.max(0,Math.min(1,s[band]*sens));double px=x+q*Math.max(1,w-1);double py=y+h/2+Math.sin(t*(.7+q*1.7)+i)*h*.28*(.3+v);int r=Math.max(1,(int)(1+v*3));g.fill((int)px-r,(int)py-r,(int)px+r+1,(int)py+r+1,c);}return;
        }
        if(type.equals("mountains")||type.equals("filled")){
            int prevX=x,prevY=y+h; for(int i=0;i<n;i++){int xx=x+(int)(i/(double)Math.max(1,n-1)*(w-1));float v=Math.max(0,Math.min(1,s[(int)(i/(double)n*s.length)]*sens));int yy=y+h-(int)(v*h);if(i>0)line(g,prevX,prevY,xx,yy,c,Math.max(1,(int)th.visualizer.thickness));if(th.visualizer.filled)g.fill(Math.min(prevX,xx),yy,Math.max(prevX,xx)+2,y+h,c);prevX=xx;prevY=yy;}return;
        }
        for(int i=0;i<n;i++){int idx=(int)((i/(double)n)*s.length);float v=Math.max(0,Math.min(1,s[idx]*sens));int bh=(int)(v*h);int bx=x+i*w/n;int bw=Math.max(1,w/n-1);g.fill(bx,y+h-bh,bx+bw,y+h,c);if(th.visualizer.mirror)g.fill(bx,y,bx+bw,y+bh/3,parse(th.accent,(int)(120*th.visualizer.opacity)));}
    }
    private static void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color,int thickness){int dx=x2-x1,dy=y2-y1,steps=Math.max(Math.abs(dx),Math.abs(dy));for(int i=0;i<=steps;i++){int x=x1+(steps==0?0:dx*i/steps),y=y1+(steps==0?0:dy*i/steps);g.fill(x,y,x+thickness,y+thickness,color);}}

    private static void drawIsland(GuiGraphics g,Track t,Theme th,int sw,double time){
        int iw=Math.min(sw-12,Math.max(180,th.island.width)),ih=Math.max(28,th.island.height),ix=(sw-iw)/2;
        String pos=th.island.position==null?"top_center":th.island.position.toLowerCase(Locale.ROOT);
        int iy=pos.contains("bottom")?Math.max(4, g.guiHeight()-ih-8):8;
        double pulse=.5+.5*Math.sin(time*th.island.animationSpeed);
        int bg=parse(th.background.color,(int)(255*Math.max(0,Math.min(1,th.island.opacity))));
        g.fill(ix,iy,ix+iw,iy+ih,bg);
        int cover=th.island.showCover?Math.max(20,ih-10):0;
        if(cover>0&&t.artwork!=null){ResourceLocation id=TextureCache.get(t.artwork);if(id!=null)g.blit(id,ix+5,iy+5,cover,cover,0,0,1,1,1,1);}
        int tx=ix+(cover>0?cover+10:10); var f=Minecraft.getInstance().font;
        String title=t.title==null?"":t.title; if(title.length()>42)title=title.substring(0,39)+"...";
        g.drawString(f,title,tx,iy+7,parse(th.text,255),true);
        if(th.island.showArtist&&ih>=40){String a=t.displayArtist();if(a.length()>42)a=a.substring(0,39)+"...";g.drawString(f,a,tx,iy+20,parse(th.secondary,255),false);}
        if(th.island.showProgress){long d=Math.max(1,MusicPlayerClient.get().AUDIO.durationMs());double q=Math.max(0,Math.min(1,MusicPlayerClient.get().AUDIO.positionMs()/(double)d));g.fill(ix+5,iy+ih-4,ix+iw-5,iy+ih-2,0x66444444);g.fill(ix+5,iy+ih-4,ix+5+(int)((iw-10)*q),iy+ih-2,parse(th.accent,255));}
        if(th.island.showVisualizer&&MusicPlayerClient.get().AUDIO.playing()){float[] v=VisualizerEngine.values(MusicPlayerClient.get().AUDIO.spectrum(),th.visualizer.smoothing);int baseX=ix+iw-th.island.visualizerWidth-8;int bars=Math.min(12,v.length);for(int i=0;i<bars;i++){float z=v[(int)(i/(double)bars*v.length)];int bh=(int)(z*(ih-12)*(0.55+.45*pulse));g.fill(baseX+i*4,iy+ih-7-bh,baseX+i*4+2,iy+ih-7,parse(th.accent,180));}}
    }
    private static void drawNotification(GuiGraphics g,Theme th,int sw){int nw=Math.min(sw-12,th.notification.width),nh=th.notification.height,nx=(sw-nw)/2,ny=40;g.fill(nx,ny,nx+nw,ny+nh,parse(th.background.color,245));g.drawString(Minecraft.getInstance().font,NotificationManager.title(),nx+14,ny+12,parse(th.text,255),true);g.drawString(Minecraft.getInstance().font,NotificationManager.body(),nx+14,ny+32,parse(th.secondary,255),false);}
    private static double[] animate(Theme.Layer l,double t){String a=l.animation==null?"none":l.animation.toLowerCase(Locale.ROOT);double speed=Math.max(0,l.animationSpeed),amp=Math.max(0,l.animationAmplitude),phase=l.animationPhase;double u=t*speed+phase;double s=Math.sin(u),c=Math.cos(u);if(a.equals("float"))return new double[]{0,s*4*amp,0,1};if(a.equals("pulse"))return new double[]{0,0,0,1+s*.08*amp};if(a.equals("zoom"))return new double[]{0,0,0,1+s*.10*amp};if(a.equals("rotate")||a.equals("spin"))return new double[]{0,0,(u*25*amp)%360,1};if(a.equals("bounce"))return new double[]{s*3*amp,Math.abs(s)*2*amp,0,1};if(a.equals("sway"))return new double[]{s*5*amp,c*2*amp,s*4*amp,1};if(a.equals("shake"))return new double[]{s*2.5*amp,c*2.5*amp,0,1};return new double[]{0,0,0,1};}
    private static double animationOpacity(Theme.Layer l,double t){if(!l.animateOpacity)return 1;double speed=Math.max(0,l.animationSpeed),amp=Math.max(0,Math.min(1,l.animationAmplitude));double v=.5+.5*Math.sin(t*speed+l.animationPhase);return Math.max(0,Math.min(1,1-amp*.65+v*amp*.65));}
    private static int alpha(double v){return (int)(255*Math.max(0,Math.min(1,v)));}
    private static int parse(String s,int a){try{String x=s==null?"FFFFFF":(s.startsWith("#")?s.substring(1):s);return (Math.max(0,Math.min(255,a))<<24)|(Integer.parseInt(x,16)&0xFFFFFF);}catch(Exception e){return (Math.max(0,Math.min(255,a))<<24)|0xFFFFFF;}}
    private static String formatTime(long ms){long s=Math.max(0,ms/1000);return String.format("%d:%02d",s/60,s%60);}
}
