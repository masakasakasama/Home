# R8 full mode + resource shrinking is enabled for release.
# Compose and coroutines ship their own consumer rules, so only our
# own entry points need explicit keeps.
-keep class com.masakasakasama.home.MainActivity { *; }
-dontwarn org.jetbrains.annotations.**
