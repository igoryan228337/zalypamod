package dev.musicplayer.ui;
public final class NotificationManager{
 private static volatile String title="",body="";private static volatile long until=0;
 public static void show(String t,String b){title=t;body=b;until=System.currentTimeMillis()+2600;}
 public static void show(String t,String b,String ignored){show(t,b+" — "+ignored);}
 public static String title(){return title;}public static String body(){return body;}public static boolean active(){return until>System.currentTimeMillis();}
}
