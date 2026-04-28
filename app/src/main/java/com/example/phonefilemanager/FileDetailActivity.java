package com.example.phonefilemanager;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("文件详情");
        Ui.title(title, 28);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        if (file == null || !file.exists()) {
            TextView empty = new TextView(this);
            empty.setText("文件不存在");
            Ui.muted(empty, 15);
            empty.setPadding(0, dp(20), 0, 0);
            root.addView(empty);
            return;
        }

        LinearLayout card = card();
        addInfo(card, "名称", file.getName());
        addInfo(card, "大小", FileUtils.formatSize(file.length()));
        addInfo(card, "修改时间", FileUtils.formatTime(file.lastModified()));
        addInfo(card, "所在目录", file.getParent());
        addInfo(card, "完整路径", file.getAbsolutePath());
        FileCategory category = FileScanner.categoryFor(file.getName());
        addInfo(card, "类型", category == null ? "未知" : category.title);

        if (category == FileCategory.APK) {
            ApkInfo apk = apkInfoReader.read(file);
            if (apk != null) {
                addInfo(card, "应用名", apk.appName);
                addInfo(card, "版本", apk.versionName + " (" + apk.versionCode + ")");
                addInfo(card, "包名", apk.packageName);
            }
        } else if (category == FileCategory.AUDIO || category == FileCategory.VIDEO) {
            MediaInfo info = mediaInfoReader.read(file, false);
            if (info != null) {
                addInfo(card, "标题", info.title);
                addInfo(card, "艺术家", info.artist);
                addInfo(card, "专辑", info.album);
                addInfo(card, "时长", info.durationText());
            }
        }
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonWrap = new LinearLayout.LayoutParams(-1, dp(46));
        buttonWrap.topMargin = dp(14);
        root.addView(buttons, buttonWrap);

        Button open = actionButton(category == FileCategory.APK ? "安装" : "打开", Ui.PRIMARY, Color.WHITE);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileUtils.openFile(FileDetailActivity.this, file);
            }
        });
        buttons.addView(open, new LinearLayout.LayoutParams(0, -1, 1));

        Button folder = actionButton("进入目录", Ui.SOFT_GRAY, Ui.TEXT);
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
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(0, -1, 1);
        folderParams.leftMargin = dp(10);
        buttons.addView(folder, folderParams);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 20, density));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
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
        button.setTextSize(15);
        button.setAllCaps(false);
        Ui.button(button, bg, density);
        return button;
    }

    private int dp(int value) {
        return (int) (value * density + 0.5f);
    }
}
