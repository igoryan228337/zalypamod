MUSIC PLAYER 1.21.11 - ONE CLICK BUILD

1. Make sure Windows has Internet access.
2. Double-click BUILD_MOD.bat.
3. The script automatically:
   - finds Java or downloads a portable JDK 21;
   - downloads Gradle 9.2.1;
   - downloads Minecraft/Fabric/Loom/dependencies through Gradle;
   - cleans and builds the mod;
   - copies the finished JAR into dist\;
   - writes a full build.log.

The finished file will be in:
  dist\music-player-1.35.0.jar

You do NOT need to install Gradle manually.
If Java is already installed, the script will use it. Otherwise it keeps a
portable JDK inside .tools and does not modify the Windows Java installation.

If the build fails, send build.log to the developer/assistant.
