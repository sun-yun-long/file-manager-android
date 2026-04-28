package com.example.phonefilemanager;

public enum FileCategory {
    DOCUMENT("文档", "doc"),
    IMAGE("图片", "image"),
    VIDEO("视频", "video"),
    AUDIO("音乐", "audio"),
    APK("安装包", "apk");

    public final String title;
    public final String key;

    FileCategory(String title, String key) {
        this.title = title;
        this.key = key;
    }
}
