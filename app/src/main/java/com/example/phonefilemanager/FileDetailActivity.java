package com.example.phonefilemanager;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public class FileDetailActivity extends Activity {
    private float density;
    private File file;
    private ApkInfoReader apkInfoReader;
    private MediaInfoReader mediaInfoReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        apkInfoReader = new ApkInfoReader(this);
        mediaInfoReader = new MediaInfoReader();
        String path = getIntent().getStringExtra("path");
        file = path == null ? null : new File(path);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(20));
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("文件详情");
        Ui.title(title, 28);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        Ui.muted(hint, 13);
        hint.setText("查看文件基础信息，并快速打开文件或回到所在目录。");
        hint.setPadding(0, dp(6), 0, dp(12));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        if (file == null || !file.exists()) {
            TextView empty = new TextView(this);
            empty.setText("文件不存在");
            Ui.muted(empty, 15);
            empty.setPadding(0, dp(20), 0, 0);
            content.addView(empty);
            return;
        }

        FileCategory category = FileScanner.categoryFor(file.getName());
        ApkInfo apk = category == FileCategory.APK ? apkInfoReader.read(file) : null;
        MediaInfo mediaInfo = (category == FileCategory.AUDIO || category == FileCategory.VIDEO)
                ? mediaInfoReader.read(file, false) : null;

        content.addView(headerCard(category, apk, mediaInfo), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout baseCard = card();
        addSectionTitle(baseCard, "基础信息");
        addInfo(baseCard, "名称", file.getName());
        addInfo(baseCard, "大小", FileUtils.formatSize(file.length()));
        addInfo(baseCard, "修改时间", FileUtils.formatTime(file.lastModified()));
        addInfo(baseCard, "所在目录", file.getParent());
        addInfo(baseCard, "完整路径", file.getAbsolutePath());
        addInfo(baseCard, "类型", category == null ? "未知" : category.title);
        content.addView(baseCard, new LinearLayout.LayoutParams(-1, -2));

        if (apk != null || mediaInfo != null) {
            LinearLayout extraCard = card();
            addSectionTitle(extraCard, apk != null ? "安装包信息" : "媒体信息");
            if (apk != null) {
                addInfo(extraCard, "应用名", apk.appName);
                addInfo(extraCard, "版本", apk.versionName + " (" + apk.versionCode + ")");
                addInfo(extraCard, "包名", apk.packageName);
            } else if (mediaInfo != null) {
                addInfo(extraCard, "标题", mediaInfo.title);
                addInfo(extraCard, "艺术家", mediaInfo.artist);
                addInfo(extraCard, "专辑", mediaInfo.album);
                addInfo(extraCard, "时长", mediaInfo.durationText());
            }
            content.addView(extraCard, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(dp(20), dp(12), dp(20), dp(18));
        actionBar.setBackgroundColor(Ui.BG);
        root.addView(actionBar, new LinearLayout.LayoutParams(-1, -2));

        Button open = actionButton(category == FileCategory.APK ? "安装" : "打开", Ui.PRIMARY, Color.WHITE);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileUtils.openFile(FileDetailActivity.this, file);
            }
        });
        actionBar.addView(open, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button folder = actionButton("进入目录", Ui.SURFACE, Ui.TEXT);
        folder.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 18, density));
        folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File dir = file.getParentFile();
                if (!ExternalDirectoryOpener.open(FileDetailActivity.this, dir)) {
                    Intent intent = new Intent(FileDetailActivity.this, DirectoryActivity.class);
                    intent.putExtra("dir", file.getParent());
                    intent.putExtra("highlight", file.getAbsolutePath());
                    startActivity(intent);
                }
            }
        });
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        folderParams.leftMargin = dp(10);
        actionBar.addView(folder, folderParams);
    }

    private LinearLayout headerCard(FileCategory category, ApkInfo apk, MediaInfo mediaInfo) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText(shortLabel(category));
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(20);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(Ui.round(colorFor(category), 20, density));
        card.addView(badge, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
        textParams.leftMargin = dp(14);
        card.addView(textColumn, textParams);

        TextView name = new TextView(this);
        Ui.title(name, 18);
        name.setText(apk != null && !TextUtils.isEmpty(apk.appName) ? apk.appName
                : mediaInfo != null && !TextUtils.isEmpty(mediaInfo.title) ? mediaInfo.title
                : file.getName());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        textColumn.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView categoryText = new TextView(this);
        Ui.muted(categoryText, 13);
        String sub = category == null ? "未知类型" : category.title;
        if (apk != null && !TextUtils.isEmpty(apk.packageName)) {
            sub += " · " + apk.packageName;
        } else if (mediaInfo != null && mediaInfo.durationMs > 0) {
            sub += " · " + mediaInfo.durationText();
        }
        categoryText.setText(sub);
        categoryText.setPadding(0, dp(8), 0, 0);
        textColumn.addView(categoryText, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView title = new TextView(this);
        Ui.title(title, 14);
        title.setText(text);
        title.setPadding(0, 0, 0, dp(10));
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 22, density));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private void addInfo(LinearLayout parent, String label, String value) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        Ui.muted(labelView, 12);
        parent.addView(labelView, new LinearLayout.LayoutParams(-1, -2));

        TextView valueView = new TextView(this);
        valueView.setText(value == null ? "-" : value);
        valueView.setTextSize(15);
        valueView.setTextColor(Ui.TEXT);
        valueView.setSingleLine(false);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(valueView, params);
    }

    private Button actionButton(String text, int bg, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        Ui.button(button, bg, density);
        button.setElevation((bg == Ui.PRIMARY || bg == Ui.PRIMARY_DARK ? 2f : 0.8f) * density);
        return button;
    }

    private int colorFor(FileCategory category) {
        if (category == FileCategory.DOCUMENT) return Ui.ORANGE;
        if (category == FileCategory.IMAGE) return Ui.GREEN;
        if (category == FileCategory.VIDEO) return Ui.RED;
        if (category == FileCategory.AUDIO) return Ui.CYAN;
        if (category == FileCategory.APK) return Ui.PRIMARY;
        return Ui.MUTED;
    }

    private String shortLabel(FileCategory category) {
        if (category == FileCategory.DOCUMENT) return "文";
        if (category == FileCategory.IMAGE) return "图";
        if (category == FileCategory.VIDEO) return "视";
        if (category == FileCategory.AUDIO) return "音";
        if (category == FileCategory.APK) return "安";
        return "件";
    }

    private int dp(int value) {
        return (int) (value * density + 0.5f);
    }
}
