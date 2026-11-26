package com.z7ui.routinex;

/**
 * 每个 App 只改这一份：清单 key、博客、源码、要备份的配置。
 * 其余类（Updater / Backup / Crashes / AboutActivity）可以原样复制。
 */
public final class AppMeta {

    /** 版本清单 apps 里的 key */
    public static final String KEY = "routinex";

    /** 博客上这个 App 的文章 */
    public static final String BLOG_URL = "https://www.z7ui.cn/routinex/";

    /** 源码 */
    public static final String REPO_URL = "https://github.com/z7ui/RoutineX";

    /** 合集页（更多应用） */
    public static final String MORE_URL = "https://www.z7ui.cn/apps/";

    /** 导出/导入时涉及的 SharedPreferences 文件名 */
    public static final String[] PREFS = {"routinex", "settings"};

    /** 除 SharedPreferences 外，还要一起备份的私有文件（位于 files/ 下） */
    public static final String[] FILES = {};

    private AppMeta() {
    }
}
