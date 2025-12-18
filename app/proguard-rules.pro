# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep data classes for Gson
-keepclassmembers class com.batterydrainer.benchmark.data.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
