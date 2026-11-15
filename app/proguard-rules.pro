# Разрешаем R8 переименовывать наши классы
# (Мы не пишем -keep для com.matreshka.truckhelper, чтобы они стали a, b, c)

# Защищаем только стандартные библиотеки, чтобы приложение не упало
-keep class org.json.** { *; }
-keep class android.** { *; }
-dontwarn android.**
-keep class android.database.** { *; }

# Оставляем имена для отладки (если упадет - увидим где)
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
