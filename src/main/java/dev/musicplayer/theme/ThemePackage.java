package dev.musicplayer.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Portable .mptheme 2.0 package manifest and safety checks. */
public final class ThemePackage {
    public static final int FORMAT_VERSION = 2;
    public String format = "mptheme";
    public int version = FORMAT_VERSION;
    public String themeId = "default";
    public String themeName = "Midnight Glass";
    public String preview = "preview.png";
    public List<String> assets = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_ENTRY = 32L * 1024L * 1024L;
    private static final long MAX_TOTAL = 256L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 4096;

    public static ThemePackage fromTheme(Theme t) {
        ThemePackage p = new ThemePackage();
        p.themeId = t.id;
        p.themeName = t.name;
        return p;
    }

    public String json() { return GSON.toJson(this); }

    public static ThemePackage parse(String json) {
        try { return GSON.fromJson(json, ThemePackage.class); }
        catch (Exception e) { return null; }
    }

    public static Validation validate(Path archive) {
        if (archive == null || !Files.isRegularFile(archive)) return Validation.bad("Archive does not exist");
        long total = 0; int count = 0; boolean theme = false; boolean manifest = false;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (++count > MAX_ENTRIES) return Validation.bad("Too many files");
                String n = e.getName().replace('\\','/');
                if (n.isBlank() || n.startsWith("/") || n.contains("../") || n.contains("..\\") || n.matches("^[A-Za-z]:.*"))
                    return Validation.bad("Unsafe path: " + n);
                if (e.isDirectory()) continue;
                long size = e.getSize();
                if (size > MAX_ENTRY) return Validation.bad("File too large: " + n);
                if (size > 0) total += size;
                if (total > MAX_TOTAL) return Validation.bad("Theme is too large");
                if (n.equals("theme.json")) theme = true;
                if (n.equals("manifest.json")) manifest = true;
            }
        } catch (Exception e) { return Validation.bad("Invalid ZIP: " + e.getMessage()); }
        if (!theme) return Validation.bad("theme.json is missing");
        return Validation.ok(manifest ? "Valid .mptheme 2.0 package" : "Valid legacy .mptheme package");
    }

    public static final class Validation {
        public final boolean valid; public final String message;
        private Validation(boolean valid, String message) { this.valid = valid; this.message = message; }
        public static Validation ok(String m) { return new Validation(true,m); }
        public static Validation bad(String m) { return new Validation(false,m); }
    }
}
