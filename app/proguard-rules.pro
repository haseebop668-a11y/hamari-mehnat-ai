# Preserve the JNI symbol names exported by app/src/main/cpp/native-lib.cpp.
-keep class com.hamari.mehenat.jobhunter.ai.NativeLlama { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# PDFBox Android can reference the optional JPEG2000 decoder; JP2 support is not bundled.
-dontwarn com.gemalto.jp2.**
