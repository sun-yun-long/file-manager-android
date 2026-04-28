package com.example.phonefilemanager;

import android.graphics.drawable.Drawable;

public class ApkInfo {
    public final String appName;
    public final String packageName;
    public final String versionName;
    public final long versionCode;
    public final Drawable icon;

    public ApkInfo(String appName, String packageName, String versionName, long versionCode, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.icon = icon;
    }
}
