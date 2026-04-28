package com.example.phonefilemanager;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FileScanCache {
    private static final String CACHE_NAME = "scan_cache.bin";
    private static final int MAGIC = 0x50464d31;
    private static final int VERSION = 1;

    public static class CacheData {
        public final Map<FileCategory, List<FileItem>> files;
        public final long savedAt;

        CacheData(Map<FileCategory, List<FileItem>> files, long savedAt) {
            this.files = files;
            this.savedAt = savedAt;
        }

        public int count() {
            int count = 0;
            for (List<FileItem> list : files.values()) {
                count += list.size();
            }
            return count;
        }
    }

    public static CacheData load(Context context) {
        File cache = new File(context.getFilesDir(), CACHE_NAME);
        if (!cache.exists() || cache.length() == 0L) {
            return null;
        }

        Map<FileCategory, List<FileItem>> result = emptyMap();
        DataInputStream input = null;
        try {
            input = new DataInputStream(new BufferedInputStream(new FileInputStream(cache)));
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                return null;
            }
            long savedAt = input.readLong();
            int count = input.readInt();
            if (count < 0 || count > 500000) {
                return null;
            }
            for (int i = 0; i < count; i++) {
                String path = input.readUTF();
                input.readUTF();
                input.readLong();
                input.readLong();

                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    continue;
                }
                FileCategory category = FileScanner.categoryFor(file.getName());
                if (category == null) {
                    continue;
                }
                result.get(category).add(new FileItem(file, category));
            }
            sort(result);
            return new CacheData(result, savedAt);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void save(Context context, Map<FileCategory, List<FileItem>> files) {
        File cache = new File(context.getFilesDir(), CACHE_NAME);
        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache)));
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeLong(System.currentTimeMillis());
            List<FileItem> all = allItems(files);
            output.writeInt(all.size());
            for (FileItem item : all) {
                output.writeUTF(item.file.getAbsolutePath());
                output.writeUTF(item.category.name());
                output.writeLong(item.file.length());
                output.writeLong(item.file.lastModified());
            }
        } catch (Exception ignored) {
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static Map<FileCategory, List<FileItem>> copyOf(Map<FileCategory, List<FileItem>> files) {
        Map<FileCategory, List<FileItem>> copy = emptyMap();
        for (FileCategory category : FileCategory.values()) {
            List<FileItem> source = files.get(category);
            if (source != null) {
                copy.get(category).addAll(source);
            }
        }
        return copy;
    }

    private static Map<FileCategory, List<FileItem>> emptyMap() {
        Map<FileCategory, List<FileItem>> result = new EnumMap<FileCategory, List<FileItem>>(FileCategory.class);
        for (FileCategory category : FileCategory.values()) {
            result.put(category, new ArrayList<FileItem>());
        }
        return result;
    }

    private static List<FileItem> allItems(Map<FileCategory, List<FileItem>> files) {
        List<FileItem> all = new ArrayList<FileItem>();
        for (FileCategory category : FileCategory.values()) {
            List<FileItem> source = files.get(category);
            if (source != null) {
                all.addAll(source);
            }
        }
        return all;
    }

    private static void sort(Map<FileCategory, List<FileItem>> files) {
        for (List<FileItem> list : files.values()) {
            Collections.sort(list, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem left, FileItem right) {
                    long delta = right.file.lastModified() - left.file.lastModified();
                    if (delta > 0) return 1;
                    if (delta < 0) return -1;
                    return left.file.getName().compareToIgnoreCase(right.file.getName());
                }
            });
        }
    }
}
