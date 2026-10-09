package dev.musicplayer.theme;
import com.google.gson.*;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
public final class ThemeManager{
 private final Path dir;private final Gson gson=new GsonBuilder().setPrettyPrinting().create();private final Map<String,Theme> themes=new LinkedHashMap<>();
 public ThemeManager(Path gameDir){dir=gameDir.resolve("musicplayer/themes");reload();}
 public synchronized void reload(){themes.clear();try{Files.createDirectories(dir);if(!Files.exists(dir.resolve("default/theme.json")))save(new Theme());try(var s=Files.list(dir)){s.filter(Files::isDirectory).forEach(p->{try{Path f=p.resolve("theme.json");if(Files.exists(f)){Theme t=gson.fromJson(Files.readString(f),Theme.class);if(t!=null){t.root=p;themes.put(t.id,t);}}}catch(Exception ignored){}});}}catch(IOException ignored){}}
 public synchronized Theme get(String id){Theme t=themes.get(id);return t!=null?t:themes.values().stream().findFirst().orElseGet(Theme::new);}public synchronized Collection<Theme> all(){return List.copyOf(themes.values());}
 public synchronized void save(Theme t){try{Path root=dir.resolve(t.id);Files.createDirectories(root);t.root=root;Files.writeString(root.resolve("theme.json"),gson.toJson(t));themes.put(t.id,t);}catch(IOException ignored){}}
 public synchronized Theme create(String id,String name){Theme t=new Theme();t.id=id.replaceAll("[^a-zA-Z0-9_-]","_");t.name=name;t.layers=Theme.defaultLayers();save(t);return t;}
 public synchronized String copyAsset(Theme t,Path source,String folder){try{Path d=t.root.resolve(folder);Files.createDirectories(d);Path out=d.resolve(source.getFileName().toString());Files.copy(source,out,StandardCopyOption.REPLACE_EXISTING);return t.root.relativize(out).toString().replace('\\','/');}catch(IOException e){return "";}}
 public synchronized void exportTheme(String id,Path archive)throws IOException{
  Theme t=get(id); if(archive==null)throw new IOException("Archive path is null");
  Files.createDirectories(archive.toAbsolutePath().getParent());
  try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(archive))){
   ThemePackage pkg=ThemePackage.fromTheme(t);
   z.putNextEntry(new ZipEntry("manifest.json"));z.write(pkg.json().getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();
   if(t.root!=null&&Files.isDirectory(t.root))try(var s=Files.walk(t.root)){s.filter(Files::isRegularFile).forEach(p->{try{String n=t.root.relativize(p).toString().replace('\\','/');if(!n.equals("manifest.json")){z.putNextEntry(new ZipEntry(n));Files.copy(p,z);z.closeEntry();}}catch(IOException ignored){}});}
  }
 }
 public synchronized boolean validateArchive(Path archive){return ThemePackage.validate(archive).valid;}
 public synchronized void importTheme(Path archive)throws IOException{Files.createDirectories(dir);String base=archive.getFileName().toString().replaceFirst("\\.mptheme$","").replaceAll("[^a-zA-Z0-9_-]","_");if(!validateArchive(archive))throw new IOException("Invalid .mptheme package");Path out=dir.resolve(base);Files.createDirectories(out);try(ZipInputStream z=new ZipInputStream(Files.newInputStream(archive))){ZipEntry e;while((e=z.getNextEntry())!=null){Path p=out.resolve(e.getName()).normalize();if(!p.startsWith(out))continue;if(e.isDirectory())Files.createDirectories(p);else{Files.createDirectories(p.getParent());Files.copy(z,p,StandardCopyOption.REPLACE_EXISTING);}}}reload();}
}
