package com.example.phonefilemanager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024) return String.format(Locale.CHINA, "%.1f KB", value);
        value = value / 1024.0;
        if (value < 1024) return String.format(Locale.CHINA, "%.1f MB", value);
        value = value / 1024.0;
        return String.format(Locale.CHINA, "%.1f GB", value);
    }

    public static String formatTime(long time) {
        return DATE_FORMAT.format(new Date(time));
    }

    public static long totalSize(Iterable<FileItem> items) {
        long total = 0L;
        for (FileItem item : items) {
            total += item.file.length();
        }
        return total;
    }

    public static void openFile(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = LocalFileProvider.uriFor(context, file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeTypeFor(file.getName()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, "打开文件"));
        } catch (Exception e) {
            Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareFile(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = LocalFileProvider.uriFor(context, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeTypeFor(file.getName()));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, "分享文件"));
        } catch (Exception e) {
            Toast.makeText(context, "没有可分享此文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    public static String mimeTypeFor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "*/*";
    }
}
