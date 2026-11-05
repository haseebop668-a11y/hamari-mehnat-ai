# Preserve the JNI symbol names exported by app/src/main/cpp/native-lib.cpp.
-keep class com.hamari.mehenat.jobhunter.ai.NativeLlama { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
