# ROMP by HARFA ProGuard / R8 keep rules
# Note: minifyEnabled is kept false in release so precompiled DEX bridges
# (classes10.dex / classes11.dex) and JNI native symbols remain intact.
-keep class com.rockstargames.gtasa.** { *; }
-keep class com.alyn.resa.** { *; }
-keep class io.flutter.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

