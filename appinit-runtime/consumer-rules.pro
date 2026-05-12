# Keep generated registry classes that are discovered and called by the Gradle plugin.
-keep class com.nd.appinit.processor.AppInitWareHouse$* {
    public static java.util.List getAllAppInitClass();
}
