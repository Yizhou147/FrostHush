# 反射（Shizuku / HiddenApiBypass）按类名字符串调用，不涉及混淆类名，无需额外规则。
# pinyin4j 依赖资源文件（pinyindb），保留即可。
-keep class net.sourceforge.pinyin4j.** { *; }
-keep class com.belerweb.** { *; }
