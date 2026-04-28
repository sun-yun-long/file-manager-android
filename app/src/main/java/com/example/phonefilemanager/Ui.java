package com.example.phonefilemanager;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public class Ui {
    public static final int BG = Color.rgb(247, 250, 255);
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT = Color.rgb(17, 24, 39);
    public static final int MUTED = Color.rgb(116, 124, 139);
    public static final int LINE = Color.rgb(232, 236, 245);
    public static final int PRIMARY = Color.rgb(58, 111, 255);
    public static final int PRIMARY_DARK = Color.rgb(34, 86, 230);
    public static final int SOFT_BLUE = Color.rgb(236, 243, 255);
    public static final int SOFT_GRAY = Color.rgb(242, 245, 250);
    public static final int GREEN = Color.rgb(47, 198, 133);
    public static final int ORANGE = Color.rgb(255, 177, 32);
    public static final int RED = Color.rgb(255, 92, 77);
    public static final int PURPLE = Color.rgb(129, 93, 255);
    public static final int CYAN = Color.rgb(48, 205, 212);

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
        view.setBackground(round(color, 16, density));
        view.setMinimumHeight(0);
        view.setMinimumWidth(0);
    }

    public static void card(View view, float density) {
        view.setBackground(round(SURFACE, 24, density));
        view.setElevation(2.5f * density);
    }
}
