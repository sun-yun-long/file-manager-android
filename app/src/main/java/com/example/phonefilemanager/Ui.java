package com.example.phonefilemanager;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public class Ui {
    public static final int BG = Color.rgb(244, 247, 253);
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT = Color.rgb(15, 23, 42);
    public static final int MUTED = Color.rgb(100, 116, 139);
    public static final int LINE = Color.rgb(214, 225, 240);
    public static final int PRIMARY = Color.rgb(51, 105, 255);
    public static final int PRIMARY_DARK = Color.rgb(32, 78, 218);
    public static final int SOFT_BLUE = Color.rgb(233, 241, 255);
    public static final int SOFT_GRAY = Color.rgb(248, 250, 254);
    public static final int GREEN = Color.rgb(34, 197, 94);
    public static final int ORANGE = Color.rgb(245, 158, 11);
    public static final int RED = Color.rgb(239, 68, 68);
    public static final int PURPLE = Color.rgb(99, 102, 241);
    public static final int CYAN = Color.rgb(14, 165, 233);

    public static GradientDrawable round(int color, float radiusDp, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * density);
        return drawable;
    }

    public static GradientDrawable stroke(int color, int strokeColor, float radiusDp, float density) {
        GradientDrawable drawable = round(color, radiusDp, density);
        drawable.setStroke(Math.max(1, (int) density), strokeColor);
        return drawable;
    }

    public static void title(TextView view, int sp) {
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(TEXT);
        view.setIncludeFontPadding(false);
    }

    public static void muted(TextView view, int sp) {
        view.setTextSize(sp);
        view.setTextColor(MUTED);
        view.setIncludeFontPadding(false);
    }

    public static void button(View view, int color, float density) {
        view.setBackground(round(color, 18, density));
        view.setMinimumHeight(0);
        view.setMinimumWidth(0);
    }

    public static void card(View view, float density) {
        view.setBackground(stroke(SURFACE, LINE, 28, density));
        view.setElevation(2.8f * density);
    }
}
