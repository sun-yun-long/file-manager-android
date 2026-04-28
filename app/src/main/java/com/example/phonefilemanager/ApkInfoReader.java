package com.example.phonefilemanager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ApkInfoReader {
    private final Context context;
    private final Map<String, ApkInfo> cache = new HashMap<String, ApkInfo>();

    public ApkInfoReader(Context context) {
        this.context = context.getApplicationContext();
    }

    public ApkInfo read(File file) {
        if (file == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (cache.containsKey(path)) {
            return cache.get(path);
        }
        ApkInfo info = parse(file);
        cache.put(path, info);
        return info;
    }

    private ApkInfo parse(File file) {
        PackageManager manager = context.getPackageManager();
        PackageInfo packageInfo = manager.getPackageArchiveInfo(file.getAbsolutePath(), 0);
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            return null;
        }

        ApplicationInfo appInfo = packageInfo.applicationInfo;
        appInfo.sourceDir = file.getAbsolutePath();
        appInfo.publicSourceDir = file.getAbsolutePath();

        String appName;
        try {
            CharSequence label = manager.getApplicationLabel(appInfo);
            appName = label == null ? file.getName() : label.toString();
        } catch (Exception ignored) {
            appName = file.getName();
        }

        String versionName = packageInfo.versionName == null ? "-" : packageInfo.versionName;
        long versionCode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            versionCode = packageInfo.getLongVersionCode();
        } else {
            versionCode = packageInfo.versionCode;
        }

        return new ApkInfo(
                appName,
                packageInfo.packageName == null ? "-" : packageInfo.packageName,
                versionName,
                versionCode,
                appInfo.loadIcon(manager)
        );
    }
}
