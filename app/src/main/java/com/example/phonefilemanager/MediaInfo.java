package com.example.phonefilemanager;

import android.graphics.Bitmap;

public class MediaInfo {
    public String title;
    public String artist;
    public String album;
    public long durationMs;
    public Bitmap frame;

    public String durationText() {
        if (durationMs <= 0) {
            return "-";
        }
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long remain = seconds % 60;
        return minutes + ":" + (remain < 10 ? "0" : "") + remain;
    }
}
