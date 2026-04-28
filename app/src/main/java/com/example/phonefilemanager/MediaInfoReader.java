package com.example.phonefilemanager;

import android.media.MediaMetadataRetriever;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MediaInfoReader {
    private final Map<String, MediaInfo> cache = new HashMap<String, MediaInfo>();

    public MediaInfo read(File file, boolean needFrame) {
        if (file == null) {
            return null;
        }
        String key = file.getAbsolutePath() + "#" + needFrame;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        MediaInfo info = parse(file, needFrame);
        cache.put(key, info);
        return info;
    }

    private MediaInfo parse(File file, boolean needFrame) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            MediaInfo info = new MediaInfo();
            info.title = clean(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            info.artist = clean(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            info.album = clean(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                try {
                    info.durationMs = Long.parseLong(duration);
                } catch (NumberFormatException ignored) {
                    info.durationMs = 0;
                }
            }
            if (needFrame) {
                try {
                    info.frame = retriever.getFrameAtTime(0);
                } catch (Exception ignored) {
                    info.frame = null;
                }
            }
            return info;
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private String clean(String value) {
        return value == null || value.trim().length() == 0 ? "-" : value.trim();
    }
}
