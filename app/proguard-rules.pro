# SceneView / Filament are consumed as prebuilt AARs — no consumer proguard rules
# are strictly required, but we keep the render thread types intact for safety.
-keep class io.github.sceneview.** { *; }
-keep class com.google.android.filament.** { *; }
-dontwarn io.github.sceneview.**

# kotlinx-serialization keeps generated serializers via @Serializable metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
