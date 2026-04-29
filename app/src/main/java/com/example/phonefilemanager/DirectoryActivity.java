package com.example.phonefilemanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DirectoryActivity extends Activity {
    private File currentDir;
    private String highlightPath;
    private TextView pathText;
    private TextView summaryText;
    private DirectoryAdapter adapter;
    private final List<File> entries = new ArrayList<File>();
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        String dir = getIntent().getStringExtra("dir");
        highlightPath = getIntent().getStringExtra("highlight");
        currentDir = dir == null ? android.os.Environment.getExternalStorageDirectory() : new File(dir);
        buildUi();
        loadDirectory(currentDir);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), 0);

        TextView title = new TextView(this);
        title.setText("所在目录");
        Ui.title(title, 28);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        Ui.muted(hint, 13);
        hint.setText("按目录层级浏览文件，并快速返回上一级或直接打开文件。");
        hint.setPadding(0, dp(6), 0, dp(12));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout pathCard = new LinearLayout(this);
        pathCard.setOrientation(LinearLayout.VERTICAL);
        pathCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        pathCard.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 20, density));
        LinearLayout.LayoutParams pathCardParams = new LinearLayout.LayoutParams(-1, -2);
        pathCardParams.bottomMargin = dp(12);
        content.addView(pathCard, pathCardParams);

        TextView pathLabel = new TextView(this);
        Ui.muted(pathLabel, 12);
        pathLabel.setText("当前路径");
        pathCard.addView(pathLabel, new LinearLayout.LayoutParams(-1, -2));

        pathText = new TextView(this);
        Ui.title(pathText, 15);
        pathText.setSingleLine(true);
        pathText.setEllipsize(TextUtils.TruncateAt.START);
        pathText.setPadding(0, dp(8), 0, dp(10));
        pathCard.addView(pathText, new LinearLayout.LayoutParams(-1, -2));

        summaryText = new TextView(this);
        Ui.muted(summaryText, 12);
        pathCard.addView(summaryText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(44));
        actionParams.bottomMargin = dp(12);
        content.addView(actions, actionParams);

        Button back = actionButton("返回", Ui.SURFACE, Ui.TEXT);
        back.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 18, density));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        actions.addView(back, new LinearLayout.LayoutParams(0, -1, 1));

        Button parent = actionButton("上一级", Ui.PRIMARY, Color.WHITE);
        parent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File parentFile = currentDir.getParentFile();
                if (parentFile != null && parentFile.canRead()) {
                    loadDirectory(parentFile);
                }
            }
        });
        LinearLayout.LayoutParams parentParams = new LinearLayout.LayoutParams(0, -1, 1);
        parentParams.leftMargin = dp(10);
        actions.addView(parent, parentParams);

        adapter = new DirectoryAdapter();
        ListView listView = new ListView(this);
        listView.setDividerHeight(dp(10));
        listView.setPadding(0, 0, 0, dp(16));
        listView.setClipToPadding(false);
        listView.setSelector(Ui.round(Color.TRANSPARENT, 1, density));
        listView.addHeaderView(content, null, false);
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
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

    private void loadDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            Toast.makeText(this, "目录不可访问", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDir = directory;
        pathText.setText(directory.getAbsolutePath());
        entries.clear();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                entries.add(file);
            }
        }
        Collections.sort(entries, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() && !right.isDirectory()) return -1;
                if (!left.isDirectory() && right.isDirectory()) return 1;
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        int dirCount = 0;
        for (File file : entries) {
            if (file.isDirectory()) {
                dirCount++;
            }
        }
        summaryText.setText("共 " + entries.size() + " 项 · " + dirCount + " 个目录 · "
                + (entries.size() - dirCount) + " 个文件");
        adapter.notifyDataSetChanged();
    }

    private int dp(int value) {
        return (int) (value * density + 0.5f);
    }

    private class DirectoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public File getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final File file = getItem(position);
            boolean highlighted = highlightPath != null && highlightPath.equals(file.getAbsolutePath());

            LinearLayout row = new LinearLayout(DirectoryActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(highlighted
                    ? Ui.stroke(Ui.SOFT_BLUE, Ui.PRIMARY, 22, density)
                    : Ui.stroke(Ui.SURFACE, Ui.LINE, 22, density));
            row.setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout topLine = new LinearLayout(DirectoryActivity.this);
            topLine.setOrientation(LinearLayout.HORIZONTAL);
            topLine.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(topLine, new LinearLayout.LayoutParams(-1, -2));

            TextView badge = new TextView(DirectoryActivity.this);
            badge.setText(file.isDirectory() ? "目录" : "文件");
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(file.isDirectory() ? Ui.PRIMARY_DARK : Ui.MUTED);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.round(file.isDirectory() ? Ui.SOFT_BLUE : Ui.SOFT_GRAY, 12, density));
            topLine.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(24)));

            TextView name = new TextView(DirectoryActivity.this);
            Ui.title(name, 16);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            name.setText(file.getName());
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
            nameParams.leftMargin = dp(10);
            topLine.addView(name, nameParams);

            TextView meta = new TextView(DirectoryActivity.this);
            Ui.muted(meta, 13);
            if (file.isDirectory()) {
                meta.setText(file.getAbsolutePath());
            } else {
                meta.setText(FileUtils.formatSize(file.length()) + " · " + FileUtils.formatTime(file.lastModified()));
            }
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.START);
            meta.setPadding(0, dp(10), 0, dp(12));
            row.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actionRow = new LinearLayout(DirectoryActivity.this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(actionRow, new LinearLayout.LayoutParams(-1, dp(38)));

            Button action = actionButton(file.isDirectory() ? "进入目录" : "打开文件",
                    file.isDirectory() ? Ui.PRIMARY : Ui.SOFT_BLUE,
                    file.isDirectory() ? Color.WHITE : Ui.PRIMARY_DARK);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (file.isDirectory()) {
                        loadDirectory(file);
                    } else {
                        FileUtils.openFile(DirectoryActivity.this, file);
                    }
                }
            });
            actionRow.addView(action, new LinearLayout.LayoutParams(0, -1, 1));

            Button copy = actionButton("复制路径", Ui.SURFACE, Ui.TEXT);
            copy.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 16, density));
            copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText("path", file.getAbsolutePath()));
                        Toast.makeText(DirectoryActivity.this, "路径已复制", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -1, 1);
            copyParams.leftMargin = dp(8);
            actionRow.addView(copy, copyParams);

            return row;
        }
    }
}
