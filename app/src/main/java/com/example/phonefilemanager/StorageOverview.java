package com.example.phonefilemanager;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StorageOverview {
    public static class DirectoryStat {
        public final String name;
        public final String path;
        public long fileCount;
        public long totalBytes;

        DirectoryStat(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    public final long totalBytes;
    public final int totalFiles;
    public final FileCategory largestCategory;
    public final long largestCategoryBytes;
    public final List<DirectoryStat> topDirectories;

    private StorageOverview(long totalBytes,
                            int totalFiles,
                            FileCategory largestCategory,
                            long largestCategoryBytes,
                            List<DirectoryStat> topDirectories) {
        this.totalBytes = totalBytes;
        this.totalFiles = totalFiles;
        this.largestCategory = largestCategory;
        this.largestCategoryBytes = largestCategoryBytes;
        this.topDirectories = topDirectories;
    }

    public static StorageOverview from(List<FileItem> allFiles) {
        long totalBytes = 0L;
        int totalFiles = 0;
        long largestCategoryBytes = 0L;
        FileCategory largestCategory = null;
        long[] categoryBytes = new long[FileCategory.values().length];
        List<DirectoryStat> topDirectories = new ArrayList<DirectoryStat>();
        List<DirectoryStat> directoryIndex = new ArrayList<DirectoryStat>();
        File root = Environment.getExternalStorageDirectory();
        String rootPath = root == null ? null : root.getAbsolutePath();

        for (FileItem item : allFiles) {
            if (item == null || item.file == null || !item.file.exists() || !item.file.isFile()) {
                continue;
            }
            long bytes = item.file.length();
            totalBytes += bytes;
            totalFiles++;
            categoryBytes[item.category.ordinal()] += bytes;

            DirectoryStat stat = findOrCreate(directoryIndex, topLevelName(rootPath, item.file), topLevelPath(rootPath, item.file));
            stat.fileCount++;
            stat.totalBytes += bytes;
        }

        for (FileCategory category : FileCategory.values()) {
            long bytes = categoryBytes[category.ordinal()];
            if (bytes > largestCategoryBytes) {
                largestCategoryBytes = bytes;
                largestCategory = category;
            }
        }

        topDirectories.addAll(directoryIndex);
        Collections.sort(topDirectories, new Comparator<DirectoryStat>() {
            @Override
            public int compare(DirectoryStat left, DirectoryStat right) {
                long delta = right.totalBytes - left.totalBytes;
                if (delta > 0) return 1;
                if (delta < 0) return -1;
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        if (topDirectories.size() > 3) {
            topDirectories = new ArrayList<DirectoryStat>(topDirectories.subList(0, 3));
        }

        return new StorageOverview(totalBytes, totalFiles, largestCategory, largestCategoryBytes, topDirectories);
    }

    private static DirectoryStat findOrCreate(List<DirectoryStat> stats, String name, String path) {
        for (DirectoryStat stat : stats) {
            if (stat.path.equals(path)) {
                return stat;
            }
        }
        DirectoryStat stat = new DirectoryStat(name, path);
        stats.add(stat);
        return stat;
    }

    private static String topLevelName(String rootPath, File file) {
        if (rootPath == null) {
            return "内部存储";
        }
        String path = file.getAbsolutePath();
        if (!path.startsWith(rootPath)) {
            return "其他目录";
        }
        String relative = path.substring(rootPath.length());
        String[] parts = relative.split("/");
        for (String part : parts) {
            if (part != null && part.length() > 0) {
                return part;
            }
        }
        return "内部存储";
    }

    private static String topLevelPath(String rootPath, File file) {
        if (rootPath == null) {
            return file.getParent() == null ? "/" : file.getParent();
        }
        String name = topLevelName(rootPath, file);
        if ("内部存储".equals(name)) {
            return rootPath;
        }
        if ("其他目录".equals(name)) {
            return file.getParent() == null ? file.getAbsolutePath() : file.getParent();
        }
        return rootPath + File.separator + name;
    }
}
