package com.example.phonefilemanager;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FileScanner {
    public interface ProgressListener {
        void onProgress(String currentPath, int foundCount);
    }

    private static class ScanTarget {
        final File directory;
        final int depth;

        ScanTarget(File directory, int depth) {
            this.directory = directory;
            this.depth = depth;
        }
    }

    private static final String[] DOCUMENT_EXTENSIONS = {
            "xls", "xlsx", "ppt", "pptx", "doc", "docx", "txt", "pdf"
    };
    private static final String[] IMAGE_EXTENSIONS = {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"
    };
    private static final String[] VIDEO_EXTENSIONS = {
            "mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "m4v"
    };
    private static final String[] AUDIO_EXTENSIONS = {
            "mp3", "aac", "flac", "wav", "m4a", "ogg", "wma", "amr"
    };
    private static final String[] APK_EXTENSIONS = {
            "apk", "apks", "xapk"
    };
    private static final String[] APK_PATH_HINTS = {
            "qqfile_recv", "mobileqq", "micromsg", "download", "browser", "tencent", "qq"
    };
    private static final String[] APK_HOTSPOT_PATHS = {
            "Download",
            "Download/QQ",
            "QQ",
            "Tencent",
            "tencent",
            "Pictures",
            "Android/media",
            "Android/media/com.tencent.mobileqq",
            "Android/media/com.tencent.mm"
    };

    public static Map<FileCategory, List<FileItem>> scanPhoneStorage(Context context) {
        return scanPhoneStorage(context, null);
    }

    public static Map<FileCategory, List<FileItem>> scanPhoneStorage(Context context, ProgressListener listener) {
        Map<FileCategory, List<FileItem>> result = new EnumMap<>(FileCategory.class);
        Set<String> seenPaths = new HashSet<>();
        for (FileCategory category : FileCategory.values()) {
            result.put(category, new ArrayList<FileItem>());
        }

        scanFilesByDirectory(result, seenPaths, listener);
        scanApkHotspots(result, seenPaths, listener);
        scanFilesByMediaStore(context, result, seenPaths, listener);

        for (List<FileItem> items : result.values()) {
            Collections.sort(items, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem left, FileItem right) {
                    long delta = right.file.lastModified() - left.file.lastModified();
                    if (delta > 0) return 1;
                    if (delta < 0) return -1;
                    return left.file.getName().compareToIgnoreCase(right.file.getName());
                }
            });
        }
        return result;
    }

    private static void scanFilesByDirectory(Map<FileCategory, List<FileItem>> result,
                                             Set<String> seenPaths,
                                             ProgressListener listener) {
        File root = Environment.getExternalStorageDirectory();
        if (root == null || !root.exists()) {
            return;
        }

        ArrayDeque<File> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            File current = stack.pop();
            notifyProgress(listener, current.getAbsolutePath(), seenPaths.size());
            File[] children;
            try {
                children = current.listFiles();
            } catch (SecurityException ignored) {
                continue;
            }

            if (children == null) {
                continue;
            }

            for (File child : children) {
                if (child == null) {
                    continue;
                }
                if (child.isDirectory()) {
                    if (!shouldSkipDirectory(child)) {
                        stack.push(child);
                    }
                    continue;
                }
                if (!child.isFile()) {
                    continue;
                }
                addFile(result, seenPaths, child);
                if (seenPaths.size() % 25 == 0) {
                    notifyProgress(listener, current.getAbsolutePath(), seenPaths.size());
                }
            }
        }
    }

    private static void scanFilesByMediaStore(Context context,
                                              Map<FileCategory, List<FileItem>> result,
                                              Set<String> seenPaths,
                                              ProgressListener listener) {
        if (context == null) {
            return;
        }
        Uri uri = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME
        };
        String selection = buildMediaStoreSelection();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, null, null);
            if (cursor == null) {
                return;
            }
            int dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
            int nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                String path = dataIndex >= 0 ? cursor.getString(dataIndex) : null;
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                if (path == null || path.length() == 0) {
                    continue;
                }
                File file = new File(path);
                if (name != null && categoryFor(name) == FileCategory.APK && file.getName().length() == 0) {
                    addFile(result, seenPaths, file);
                } else {
                    addFile(result, seenPaths, file);
                }
                if (seenPaths.size() % 25 == 0) {
                    notifyProgress(listener, file.getParent(), seenPaths.size());
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static String buildMediaStoreSelection() {
        StringBuilder builder = new StringBuilder();
        String[] tokens = {
                ".xls", ".xlsx", ".ppt", ".pptx", ".doc", ".docx", ".txt", ".pdf",
                ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif",
                ".mp4", ".mkv", ".avi", ".mov", ".3gp", ".webm", ".flv", ".m4v",
                ".mp3", ".aac", ".flac", ".wav", ".m4a", ".ogg", ".wma", ".amr",
                ".apk", ".apks", ".xapk", "apk", "apks", "xapk"
        };
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                builder.append(" OR ");
            }
            builder.append("(")
                    .append(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    .append(" LIKE '%")
                    .append(tokens[i])
                    .append("%' OR ")
                    .append(MediaStore.Files.FileColumns.DATA)
                    .append(" LIKE '%")
                    .append(tokens[i])
                    .append("%')");
        }
        return builder.toString();
    }

    private static void scanApkHotspots(Map<FileCategory, List<FileItem>> result,
                                        Set<String> seenPaths,
                                        ProgressListener listener) {
        File root = Environment.getExternalStorageDirectory();
        if (root == null || !root.exists()) {
            return;
        }
        for (String relativePath : APK_HOTSPOT_PATHS) {
            File directory = new File(root, relativePath);
            scanApkDirectory(directory, result, seenPaths, listener, 5);
        }
    }

    private static void scanApkDirectory(File start,
                                         Map<FileCategory, List<FileItem>> result,
                                         Set<String> seenPaths,
                                         ProgressListener listener,
                                         int maxDepth) {
        if (start == null || !start.exists() || !start.isDirectory()) {
            return;
        }
        ArrayDeque<ScanTarget> stack = new ArrayDeque<>();
        stack.push(new ScanTarget(start, 0));
        Set<String> visited = new HashSet<>();

        while (!stack.isEmpty()) {
            ScanTarget target = stack.pop();
            File current = target.directory;
            String currentPath = current.getAbsolutePath();
            if (visited.contains(currentPath)) {
                continue;
            }
            visited.add(currentPath);
            notifyProgress(listener, currentPath, seenPaths.size());

            File[] children;
            try {
                children = current.listFiles();
            } catch (SecurityException ignored) {
                continue;
            }
            if (children == null) {
                continue;
            }

            for (File child : children) {
                if (child == null) {
                    continue;
                }
                if (child.isDirectory()) {
                    if (target.depth < maxDepth && shouldVisitApkDirectory(child, target.depth)) {
                        stack.push(new ScanTarget(child, target.depth + 1));
                    }
                    continue;
                }
                if (child.isFile() && isApkLikeName(child.getName())) {
                    addFile(result, seenPaths, child);
                    if (seenPaths.size() % 10 == 0) {
                        notifyProgress(listener, currentPath, seenPaths.size());
                    }
                }
            }
        }
    }

    private static boolean shouldVisitApkDirectory(File directory, int depth) {
        if (shouldSkipDirectory(directory)) {
            return false;
        }
        if (depth < 2) {
            return true;
        }
        String path = directory.getAbsolutePath().toLowerCase(Locale.US);
        for (String hint : APK_PATH_HINTS) {
            if (path.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static void addFile(Map<FileCategory, List<FileItem>> result, Set<String> seenPaths, File file) {
        if (file == null) {
            return;
        }
        FileCategory category = categoryFor(file.getName());
        if (category == null) {
            return;
        }
        String path = file.getAbsolutePath();
        if (seenPaths.contains(path)) {
            return;
        }
        seenPaths.add(path);
        result.get(category).add(new FileItem(file, category));
    }

    private static void notifyProgress(ProgressListener listener, String currentPath, int foundCount) {
        if (listener != null) {
            listener.onProgress(currentPath == null ? "" : currentPath, foundCount);
        }
    }

    public static FileCategory categoryFor(String name) {
        String extension = extensionOf(name);
        if (isApkLikeName(name)) return FileCategory.APK;
        if (contains(DOCUMENT_EXTENSIONS, extension)) return FileCategory.DOCUMENT;
        if (contains(IMAGE_EXTENSIONS, extension)) return FileCategory.IMAGE;
        if (contains(VIDEO_EXTENSIONS, extension)) return FileCategory.VIDEO;
        if (contains(AUDIO_EXTENSIONS, extension)) return FileCategory.AUDIO;
        if (contains(APK_EXTENSIONS, extension)) return FileCategory.APK;
        return null;
    }

    private static boolean shouldSkipDirectory(File directory) {
        String path = directory.getAbsolutePath();
        return path.contains("/Android/obb")
                || path.contains("/Android/media/com.google.android.gms");
    }

    private static boolean isApkLikeName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        return lower.contains(".apk") || lower.contains(".apks") || lower.contains(".xapk");
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static boolean contains(String[] values, String value) {
        for (String item : values) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
