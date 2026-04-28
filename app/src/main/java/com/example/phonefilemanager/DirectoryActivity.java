package com.example.phonefilemanager;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
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
        root.setPadding(dp(18), dp(18), dp(18), 0);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("所在目录");
        Ui.title(title, 28);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        pathText = new TextView(this);
        Ui.muted(pathText, 13);
        pathText.setSingleLine(true);
        pathText.setEllipsize(TextUtils.TruncateAt.START);
        pathText.setPadding(0, dp(8), 0, dp(14));
        root.addView(pathText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(44));
        actionParams.bottomMargin = dp(14);
        root.addView(actions, actionParams);

        Button back = actionButton("返回", Ui.SOFT_GRAY, Ui.TEXT);
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
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private Button actionButton(String text, int bg, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        Ui.button(button, bg, density);
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
                    ? Ui.stroke(Ui.SOFT_BLUE, Ui.PRIMARY, 20, density)
                    : Ui.stroke(Ui.SURFACE, Ui.LINE, 20, density));
            row.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView name = new TextView(DirectoryActivity.this);
            Ui.title(name, 16);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            name.setText((file.isDirectory() ? "目录 · " : "文件 · ") + file.getName());
            row.addView(name, new LinearLayout.LayoutParams(-1, -2));

            TextView meta = new TextView(DirectoryActivity.this);
            Ui.muted(meta, 13);
            if (file.isDirectory()) {
                meta.setText(file.getAbsolutePath());
            } else {
                meta.setText(FileUtils.formatSize(file.length()) + " · " + FileUtils.formatTime(file.lastModified()));
            }
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.START);
            meta.setPadding(0, dp(8), 0, dp(12));
            row.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            Button button = actionButton(file.isDirectory() ? "进入目录" : "打开文件",
                    file.isDirectory() ? Ui.PRIMARY : Ui.SOFT_BLUE,
                    file.isDirectory() ? Color.WHITE : Ui.PRIMARY_DARK);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (file.isDirectory()) {
                        loadDirectory(file);
                    } else {
                        FileUtils.openFile(DirectoryActivity.this, file);
                    }
                }
            });
            row.addView(button, new LinearLayout.LayoutParams(-1, dp(40)));
            return row;
        }
    }
}
