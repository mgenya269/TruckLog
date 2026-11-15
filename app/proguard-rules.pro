# Сохраняем классы для работы с JSON и SharedPreferences
-keep class org.json.** { *; }
-keep class com.matreshka.truckhelper.** { *; }

# Не трогаем стандартные Android-классы
-keep class android.** { *; }
-dontwarn android.**

# Оставляем имена для отладки (если упадёт — будет понятно, где)
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
