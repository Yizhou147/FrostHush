# 反射（Shizuku / HiddenApiBypass）按类名字符串调用，不涉及混淆类名，无需额外规则。
# pinyin4j 依赖资源文件（pinyindb），保留即可。
-keep class net.sourceforge.pinyin4j.** { *; }
-keep class com.belerweb.** { *; }

# 内置 Xposed 模块：LSPosed 按 META-INF/xposed/java_init.list 里的全限定类名加载，
# R8 必须保留该类（及实现的 libxposed 接口元数据），否则会被当作死代码裁剪/混淆。
-keep class com.frosthush.app.xposed.** { *; }
