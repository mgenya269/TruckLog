# ==========================================
# НАСТРОЙКИ R8 ДЛЯ TRUCKLOG
# ==========================================

# ВАЖНО: Мы НЕ пишем сюда -keep для com.matreshka.truckhelper.
# Благодаря этому R8 переименует все наши классы в a, b, c и удалит лишнее.

# Оставляем стандартные классы Android, чтобы приложение не падало
-keep class android.** { *; }
-dontwarn android.**

# Оставляем стандартные классы Java и JSON
-keep class java.** { *; }
-dontwarn java.**
-keep class org.json.** { *; }
-dontwarn org.json.**

# Оставляем классы для работы с базой данных SQLite
-keep class android.database.** { *; }
-dontwarn android.database.**

# Оставляем имена файлов и строк для отладки
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
