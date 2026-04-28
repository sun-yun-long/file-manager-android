package com.example.phonefilemanager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DuplicateAnalyzer {
    public static class Result {
        public final List<FileItem> items;
        public final List<List<FileItem>> groups;
        public final int groupCount;
        public final long wastedBytes;

        Result(List<FileItem> items, List<List<FileItem>> groups, int groupCount, long wastedBytes) {
            this.items = items;
            this.groups = groups;
            this.groupCount = groupCount;
            this.wastedBytes = wastedBytes;
        }
    }

    public static Result analyze(List<FileItem> source) {
        Map<Long, List<FileItem>> sizeGroups = new HashMap<Long, List<FileItem>>();
        for (FileItem item : source) {
            if (item == null || item.file == null || !item.file.exists() || !item.file.isFile()) {
                continue;
            }
            long length = item.file.length();
            if (length <= 0L) {
                continue;
            }
            List<FileItem> group = sizeGroups.get(length);
            if (group == null) {
                group = new ArrayList<FileItem>();
                sizeGroups.put(length, group);
            }
            group.add(item);
        }

        List<FileItem> duplicates = new ArrayList<FileItem>();
        List<List<FileItem>> duplicateGroupList = new ArrayList<List<FileItem>>();
        int duplicateGroups = 0;
        long wastedBytes = 0L;
        for (Map.Entry<Long, List<FileItem>> entry : sizeGroups.entrySet()) {
            List<FileItem> sameSize = entry.getValue();
            if (sameSize.size() < 2) {
                continue;
            }
            Map<String, List<FileItem>> hashGroups = new HashMap<String, List<FileItem>>();
            for (FileItem item : sameSize) {
                String hash = sha256(item.file);
                if (hash == null) {
                    continue;
                }
                String key = item.file.length() + ":" + hash;
                List<FileItem> hashGroup = hashGroups.get(key);
                if (hashGroup == null) {
                    hashGroup = new ArrayList<FileItem>();
                    hashGroups.put(key, hashGroup);
                }
                hashGroup.add(item);
            }
            for (List<FileItem> hashGroup : hashGroups.values()) {
                if (hashGroup.size() < 2) {
                    continue;
                }
                duplicateGroups++;
                duplicateGroupList.add(hashGroup);
                duplicates.addAll(hashGroup);
                wastedBytes += (hashGroup.size() - 1L) * hashGroup.get(0).file.length();
            }
        }
        return new Result(duplicates, duplicateGroupList, duplicateGroups, wastedBytes);
    }

    private static String sha256(File file) {
        BufferedInputStream input = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
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
}
