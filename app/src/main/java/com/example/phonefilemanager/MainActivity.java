package com.example.phonefilemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final long CACHE_STALE_MS = 24L * 60L * 60L * 1000L;
    private static final int REQUEST_PICK_APK = 41;

    private enum SortMode {
        TIME("时间"),
        SIZE("大小"),
        NAME("名称");

        final String title;

        SortMode(String title) {
            this.title = title;
        }
    }

    private static class TabItem {
        static final int CATEGORY = 0;
        static final int RECENT = 1;
        static final int LARGE = 2;
        static final int DUPLICATE = 3;

        final int type;
        final String title;
        final FileCategory category;

        TabItem(FileCategory category) {
            this.type = CATEGORY;
            this.category = category;
            this.title = category.title;
        }

        TabItem(int type, String title) {
            this.type = type;
            this.title = title;
            this.category = null;
        }
    }

    private static class FilterOption {
        final String key;
        final String title;

        FilterOption(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService previewExecutor = Executors.newFixedThreadPool(2);
    private final Map<FileCategory, List<FileItem>> files = new EnumMap<FileCategory, List<FileItem>>(FileCategory.class);
    private final List<TabItem> tabs = new ArrayList<TabItem>();
    private final List<TextView> tabCards = new ArrayList<TextView>();
    private final List<TextView> sortChips = new ArrayList<TextView>();
    private final List<FileItem> visibleItems = new ArrayList<FileItem>();
    private final Map<String, Bitmap> imageThumbCache = new ConcurrentHashMap<String, Bitmap>();
    private final Map<String, Bitmap> videoThumbCache = new ConcurrentHashMap<String, Bitmap>();
    private final Map<String, ApkInfo> asyncApkInfoCache = new ConcurrentHashMap<String, ApkInfo>();
    private final Map<String, MediaInfo> asyncMediaInfoCache = new ConcurrentHashMap<String, MediaInfo>();
    private final Set<String> loadingImageThumbPaths = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> loadingVideoThumbPaths = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> loadingApkInfoPaths = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> loadingMediaInfoPaths = Collections.synchronizedSet(new HashSet<String>());

    private TabItem selectedTab;
    private SortMode sortMode = SortMode.TIME;
    private String searchText = "";
    private String selectedFilterKey = "all";
    private long largeThresholdBytes = 50L * 1024L * 1024L;
    private boolean isScanning = false;
    private boolean batchMode = false;
    private boolean overviewExpanded = false;
    private boolean imageGridMode = false;
    private DuplicateAnalyzer.Result duplicateResult =
            new DuplicateAnalyzer.Result(new ArrayList<FileItem>(), new ArrayList<List<FileItem>>(), 0, 0L);
    private StorageOverview storageOverview =
            StorageOverview.from(new ArrayList<FileItem>());
    private String statusNote = "";
    private final Set<String> selectedPaths = new HashSet<String>();

    private TextView statusText;
    private TextView resultText;
    private TextView emptyText;
    private TextView totalSummaryCard;
    private TextView duplicateSummaryCard;
    private TextView largeSummaryCard;
    private TextView apkSummaryCard;
    private TextView overviewCompactText;
    private TextView overviewToggleText;
    private StorageRingView storageRingView;
    private TextView storageUsageText;
    private TextView storageTotalText;
    private TextView apkScanHintText;
    private Button importApkButton;
    private LinearLayout overviewSummaryRow;
    private TextView topDirectoryTitleText;
    private LinearLayout topDirectoryLayout;
    private LinearLayout filterRow;
    private EditText searchInput;
    private Button permissionButton;
    private Button scanButton;
    private Button thresholdButton;
    private Button imageViewModeButton;
    private Button batchButton;
    private Button selectAllButton;
    private Button deleteSelectedButton;
    private Button duplicateSelectButton;
    private Button shareSelectedButton;
    private Button copySelectedPathsButton;
    private FileListAdapter adapter;
    private ApkInfoReader apkInfoReader;
    private MediaInfoReader mediaInfoReader;
    private float density;
    private LinearLayout storageLegendRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        apkInfoReader = new ApkInfoReader(this);
        mediaInfoReader = new MediaInfoReader();
        for (FileCategory category : FileCategory.values()) {
            files.put(category, new ArrayList<FileItem>());
            tabs.add(new TabItem(category));
        }
        tabs.add(new TabItem(TabItem.RECENT, "最近"));
        tabs.add(new TabItem(TabItem.LARGE, "大文件"));
        tabs.add(new TabItem(TabItem.DUPLICATE, "重复"));
        selectedTab = tabs.get(0);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUi();
        if (hasStorageAccess() && countAllFiles() == 0 && !isScanning) {
            if (!loadScanCache()) {
                scanFiles();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        previewExecutor.shutdownNow();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_APK && resultCode == RESULT_OK && data != null) {
            importSelectedApks(data);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        LinearLayout topContent = new LinearLayout(this);
        topContent.setOrientation(LinearLayout.VERTICAL);
        topContent.setPadding(dp(20), dp(16), dp(20), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(10));
        topContent.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("文件管理");
        Ui.title(title, 26);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button searchButton = iconButton("⌕");
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchInput != null) {
                    searchInput.requestFocus();
                }
            }
        });
        header.addView(searchButton, new LinearLayout.LayoutParams(dp(42), dp(42)));

        Button menuButton = iconButton("⋮");
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLargeThresholdDialog();
            }
        });
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        menuParams.leftMargin = dp(6);
        header.addView(menuButton, menuParams);

        statusText = new TextView(this);
        Ui.muted(statusText, 13);
        statusText.setTextColor(Ui.PRIMARY_DARK);
        statusText.setPadding(dp(16), dp(10), dp(16), dp(10));
        statusText.setBackground(Ui.stroke(Ui.SOFT_BLUE, Color.rgb(201, 221, 255), 18, density));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.bottomMargin = dp(12);
        topContent.addView(statusText, statusParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionWrap = new LinearLayout.LayoutParams(-1, dp(38));
        actionWrap.bottomMargin = dp(12);
        topContent.addView(actions, actionWrap);

        permissionButton = actionButton("权限", Ui.SURFACE, Ui.TEXT);
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStorageAccess();
            }
        });
        actions.addView(permissionButton, new LinearLayout.LayoutParams(0, -1, 1));

        scanButton = actionButton("重新扫描", Ui.PRIMARY, Color.WHITE);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanFiles();
            }
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, -1, 1);
        scanParams.leftMargin = dp(10);
        actions.addView(scanButton, scanParams);

        thresholdButton = actionButton("大文件 " + FileUtils.formatSize(largeThresholdBytes), Ui.SOFT_GRAY, Ui.TEXT);
        thresholdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLargeThresholdDialog();
            }
        });
        LinearLayout.LayoutParams thresholdParams = new LinearLayout.LayoutParams(-1, dp(38));
        thresholdParams.bottomMargin = dp(12);
        topContent.addView(thresholdButton, thresholdParams);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setHint("搜索文件名、目录或应用包名");
        searchInput.setHintTextColor(Ui.MUTED);
        searchInput.setTextColor(Ui.TEXT);
        searchInput.setPadding(dp(18), 0, dp(18), 0);
        searchInput.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 22, density));
        searchInput.setElevation(1f * density);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s == null ? "" : s.toString().trim();
                refreshVisibleItems();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(46));
        searchParams.bottomMargin = dp(12);
        topContent.addView(searchInput, searchParams);

        LinearLayout overview = new LinearLayout(this);
        overview.setOrientation(LinearLayout.VERTICAL);
        Ui.card(overview, density);
        overview.setPadding(dp(20), dp(20), dp(20), dp(18));
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(-1, -2);
        overviewParams.bottomMargin = dp(12);
        topContent.addView(overview, overviewParams);

        TextView overviewTitle = new TextView(this);
        Ui.title(overviewTitle, 18);
        overviewTitle.setText("存储空间");
        overview.addView(overviewTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView overviewHint = new TextView(this);
        Ui.muted(overviewHint, 12);
        overviewHint.setText("内部存储、重复文件和高频目录");
        overviewHint.setPadding(0, dp(6), 0, dp(14));
        overview.addView(overviewHint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout storageRow = new LinearLayout(this);
        storageRow.setOrientation(LinearLayout.HORIZONTAL);
        storageRow.setGravity(Gravity.CENTER_VERTICAL);
        storageRow.setPadding(0, 0, 0, dp(12));
        overview.addView(storageRow, new LinearLayout.LayoutParams(-1, -2));

        storageRingView = new StorageRingView(this);
        storageRow.addView(storageRingView, new LinearLayout.LayoutParams(dp(122), dp(122)));

        LinearLayout storageTextColumn = new LinearLayout(this);
        storageTextColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams storageTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        storageTextParams.leftMargin = dp(20);
        storageRow.addView(storageTextColumn, storageTextParams);

        storageUsageText = new TextView(this);
        Ui.title(storageUsageText, 16);
        storageTextColumn.addView(storageUsageText, new LinearLayout.LayoutParams(-1, -2));

        storageTotalText = new TextView(this);
        Ui.muted(storageTotalText, 13);
        storageTotalText.setPadding(0, dp(8), 0, dp(10));
        storageTextColumn.addView(storageTotalText, new LinearLayout.LayoutParams(-1, -2));

        storageLegendRow = new LinearLayout(this);
        storageLegendRow.setOrientation(LinearLayout.HORIZONTAL);
        storageTextColumn.addView(storageLegendRow, new LinearLayout.LayoutParams(-1, -2));

        overviewCompactText = new TextView(this);
        Ui.muted(overviewCompactText, 13);
        overviewCompactText.setSingleLine(false);
        overviewCompactText.setPadding(0, 0, 0, dp(12));
        overview.addView(overviewCompactText, new LinearLayout.LayoutParams(-1, -2));

        overviewToggleText = new TextView(this);
        overviewToggleText.setVisibility(View.GONE);
        overview.addView(overviewToggleText, new LinearLayout.LayoutParams(-1, -2));

        overviewSummaryRow = new LinearLayout(this);
        overviewSummaryRow.setOrientation(LinearLayout.VERTICAL);
        overview.addView(overviewSummaryRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout summaryFirstRow = new LinearLayout(this);
        summaryFirstRow.setOrientation(LinearLayout.HORIZONTAL);
        overviewSummaryRow.addView(summaryFirstRow, new LinearLayout.LayoutParams(-1, -2));

        totalSummaryCard = summaryCard();
        duplicateSummaryCard = summaryCard();
        summaryFirstRow.addView(totalSummaryCard, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams summaryGapParams = new LinearLayout.LayoutParams(0, -2, 1);
        summaryGapParams.leftMargin = dp(10);
        summaryFirstRow.addView(duplicateSummaryCard, summaryGapParams);

        LinearLayout summarySecondRow = new LinearLayout(this);
        summarySecondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams summarySecondParams = new LinearLayout.LayoutParams(-1, -2);
        summarySecondParams.topMargin = dp(10);
        overviewSummaryRow.addView(summarySecondRow, summarySecondParams);

        largeSummaryCard = summaryCard();
        apkSummaryCard = summaryCard();
        summarySecondRow.addView(largeSummaryCard, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams summaryLastParams = new LinearLayout.LayoutParams(0, -2, 1);
        summaryLastParams.leftMargin = dp(10);
        summarySecondRow.addView(apkSummaryCard, summaryLastParams);

        topDirectoryTitleText = new TextView(this);
        Ui.title(topDirectoryTitleText, 14);
        topDirectoryTitleText.setText("主要目录");
        topDirectoryTitleText.setPadding(0, dp(16), 0, dp(8));
        overview.addView(topDirectoryTitleText, new LinearLayout.LayoutParams(-1, -2));

        topDirectoryLayout = new LinearLayout(this);
        topDirectoryLayout.setOrientation(LinearLayout.VERTICAL);
        topDirectoryLayout.setVisibility(View.GONE);
        overview.addView(topDirectoryLayout, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(0, 0, dp(6), 0);
        scrollView.addView(tabRow);
        LinearLayout.LayoutParams tabWrapParams = new LinearLayout.LayoutParams(-1, dp(66));
        topContent.addView(scrollView, tabWrapParams);

        for (final TabItem tab : tabs) {
            TextView card = new TextView(this);
            card.setGravity(Gravity.CENTER);
            card.setTextSize(12);
            card.setTypeface(Typeface.DEFAULT_BOLD);
            card.setSingleLine(false);
            card.setIncludeFontPadding(false);
            card.setLineSpacing(dp(2), 1f);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedTab = tab;
                    selectedFilterKey = "all";
                    clearInvisibleSelections();
                    if (!isImageTabSelected()) {
                        imageGridMode = false;
                    }
                    if (tab.type == TabItem.LARGE || tab.type == TabItem.DUPLICATE) {
                        sortMode = SortMode.SIZE;
                    } else if (tab.type == TabItem.RECENT) {
                        sortMode = SortMode.TIME;
                    }
                    refreshVisibleItems();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(78), dp(54));
            params.rightMargin = dp(8);
            tabRow.addView(card, params);
            tabCards.add(card);
        }

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, dp(38));
        toolsParams.topMargin = 0;
        toolsParams.bottomMargin = dp(8);
        topContent.addView(tools, toolsParams);

        resultText = new TextView(this);
        Ui.title(resultText, 13);
        resultText.setTextColor(Ui.MUTED);
        tools.addView(resultText, new LinearLayout.LayoutParams(0, -1, 1));

        imageViewModeButton = actionButton("网格", Ui.SOFT_GRAY, Ui.TEXT);
        imageViewModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isImageTabSelected()) {
                    return;
                }
                imageGridMode = !imageGridMode;
                refreshVisibleItems();
            }
        });
        imageViewModeButton.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 16, density));
        LinearLayout.LayoutParams imageModeParams = new LinearLayout.LayoutParams(dp(58), dp(32));
        imageModeParams.leftMargin = dp(6);
        tools.addView(imageViewModeButton, imageModeParams);

        for (final SortMode mode : SortMode.values()) {
            TextView chip = new TextView(this);
            chip.setText(mode.title);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sortMode = mode;
                    refreshVisibleItems();
                }
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(52), dp(32));
            chipParams.leftMargin = dp(6);
            tools.addView(chip, chipParams);
            sortChips.add(chip);
        }

        LinearLayout batchActions = new LinearLayout(this);
        batchActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams batchParams = new LinearLayout.LayoutParams(-1, dp(40));
        batchParams.topMargin = 0;
        batchParams.bottomMargin = dp(8);
        topContent.addView(batchActions, batchParams);

        batchButton = actionButton("批量选择", Ui.TEXT, Color.WHITE);
        batchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBatchMode();
            }
        });
        batchActions.addView(batchButton, new LinearLayout.LayoutParams(0, -1, 1));

        selectAllButton = actionButton("全选当前", Ui.SOFT_GRAY, Ui.TEXT);
        selectAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSelectAllVisible();
            }
        });
        LinearLayout.LayoutParams selectAllParams = new LinearLayout.LayoutParams(0, -1, 1);
        selectAllParams.leftMargin = dp(8);
        batchActions.addView(selectAllButton, selectAllParams);

        deleteSelectedButton = actionButton("删除已选", Color.rgb(218, 72, 72), Color.WHITE);
        deleteSelectedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteSelectedWithPreview();
            }
        });
        LinearLayout.LayoutParams deleteSelectedParams = new LinearLayout.LayoutParams(0, -1, 1);
        deleteSelectedParams.leftMargin = dp(8);
        batchActions.addView(deleteSelectedButton, deleteSelectedParams);

        LinearLayout batchExtraActions = new LinearLayout(this);
        batchExtraActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams batchExtraParams = new LinearLayout.LayoutParams(-1, dp(40));
        batchExtraParams.bottomMargin = dp(8);
        topContent.addView(batchExtraActions, batchExtraParams);

        duplicateSelectButton = actionButton("保留最新", Ui.SOFT_GRAY, Ui.TEXT);
        duplicateSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDuplicateCopiesKeepingNewest();
            }
        });
        batchExtraActions.addView(duplicateSelectButton, new LinearLayout.LayoutParams(0, -1, 1));

        shareSelectedButton = actionButton("分享已选", Ui.SOFT_GRAY, Ui.TEXT);
        shareSelectedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSelectedItems();
            }
        });
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, -1, 1);
        shareParams.leftMargin = dp(8);
        batchExtraActions.addView(shareSelectedButton, shareParams);

        copySelectedPathsButton = actionButton("复制路径", Ui.SOFT_GRAY, Ui.TEXT);
        copySelectedPathsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copySelectedPaths();
            }
        });
        LinearLayout.LayoutParams copyPathsParams = new LinearLayout.LayoutParams(0, -1, 1);
        copyPathsParams.leftMargin = dp(8);
        batchExtraActions.addView(copySelectedPathsButton, copyPathsParams);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterScroll.addView(filterRow);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(36));
        filterParams.bottomMargin = dp(10);
        topContent.addView(filterScroll, filterParams);

        apkScanHintText = new TextView(this);
        Ui.muted(apkScanHintText, 12);
        apkScanHintText.setTextColor(Ui.PRIMARY_DARK);
        apkScanHintText.setText("部分聊天软件接收目录可能受系统限制，建议把安装包保存到 Download 后重新扫描");
        apkScanHintText.setPadding(dp(14), dp(10), dp(14), dp(10));
        apkScanHintText.setBackground(Ui.stroke(Ui.SOFT_BLUE, Color.rgb(201, 221, 255), 16, density));
        apkScanHintText.setVisibility(View.GONE);
        LinearLayout.LayoutParams apkHintParams = new LinearLayout.LayoutParams(-1, -2);
        apkHintParams.bottomMargin = dp(8);
        topContent.addView(apkScanHintText, apkHintParams);

        importApkButton = actionButton("选择安装包", Ui.SOFT_BLUE, Ui.PRIMARY);
        importApkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportApkPicker();
            }
        });
        importApkButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams importApkParams = new LinearLayout.LayoutParams(-1, dp(42));
        importApkParams.bottomMargin = dp(8);
        topContent.addView(importApkButton, importApkParams);

        emptyText = new TextView(this);
        Ui.muted(emptyText, 15);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setText("这里暂时没有文件");
        emptyText.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 20, density));
        emptyText.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, dp(92));
        emptyParams.topMargin = dp(8);
        emptyParams.bottomMargin = dp(8);
        ListView listView = new ListView(this);
        listView.setDividerHeight(dp(12));
        listView.setPadding(0, dp(4), 0, dp(18));
        listView.setClipToPadding(false);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setSelector(Ui.round(Color.TRANSPARENT, 1, density));
        listView.addHeaderView(topContent, null, false);
        listView.addFooterView(emptyText, null, false);
        adapter = new FileListAdapter();
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        updateOverviewPanel();
        updatePermissionUi();
        refreshVisibleItems();
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

    private Button iconButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Ui.TEXT);
        button.setTextSize(20);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, dp(2));
        button.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 16, density));
        button.setElevation(1f * density);
        return button;
    }

    private TextView summaryCard() {
        TextView card = new TextView(this);
        card.setTextColor(Ui.TEXT);
        card.setTextSize(12);
        card.setTypeface(Typeface.DEFAULT_BOLD);
        card.setLineSpacing(dp(3), 1f);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Ui.stroke(Ui.SOFT_GRAY, Ui.LINE, 18, density));
        card.setMinHeight(dp(88));
        return card;
    }

    private TextView chipView(String text, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setTextColor(selected ? Color.WHITE : Ui.MUTED);
        chip.setBackground(selected
                ? Ui.round(Ui.PRIMARY, 14, density)
                : Ui.stroke(Ui.SURFACE, Ui.LINE, 14, density));
        return chip;
    }

    private void scanFiles() {
        if (isScanning) {
            return;
        }
        if (!hasStorageAccess()) {
            Toast.makeText(this, "请先开启所有文件访问权限", Toast.LENGTH_SHORT).show();
            requestStorageAccess();
            return;
        }
        setScanning(true);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Map<FileCategory, List<FileItem>> scanned = FileScanner.scanPhoneStorage(MainActivity.this, new FileScanner.ProgressListener() {
                    @Override
                    public void onProgress(final String currentPath, final int foundCount) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isScanning) {
                                    statusText.setText("正在扫描 · 已发现 " + foundCount + " 个 · " + shortPath(currentPath));
                                }
                            }
                        });
                    }
                });
                FileScanCache.save(MainActivity.this, scanned);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        replaceAllFiles(scanned);
                        imageThumbCache.clear();
                        videoThumbCache.clear();
                        setScanning(false);
                        refreshVisibleItems();
                        setStatusNote("上次扫描 " + FileUtils.formatTime(System.currentTimeMillis())
                                + " · 共 " + countAllFiles() + " 个文件");
                    }
                });
            }
        });
    }

    private boolean loadScanCache() {
        FileScanCache.CacheData cache = FileScanCache.load(this);
        if (cache == null || cache.count() == 0) {
            return false;
        }
        replaceAllFiles(cache.files);
        refreshVisibleItems();
        setStatusNote("已加载缓存 · " + FileUtils.formatTime(cache.savedAt)
                + " · " + cache.count() + " 个文件");
        boolean stale = System.currentTimeMillis() - cache.savedAt > CACHE_STALE_MS;
        setStatusNote((stale ? "缓存超过 24 小时，建议重新扫描 · " : "已加载缓存 · ")
                + FileUtils.formatTime(cache.savedAt) + " · " + cache.count() + " 个文件");
        return true;
    }

    private void replaceAllFiles(Map<FileCategory, List<FileItem>> newFiles) {
        files.clear();
        for (FileCategory category : FileCategory.values()) {
            List<FileItem> list = newFiles.get(category);
            files.put(category, list == null ? new ArrayList<FileItem>() : list);
        }
        updateDerivedData();
    }

    private void persistCurrentCache() {
        final Map<FileCategory, List<FileItem>> snapshot = FileScanCache.copyOf(files);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                FileScanCache.save(MainActivity.this, snapshot);
            }
        });
    }

    private void updateDerivedData() {
        List<FileItem> all = allFiles();
        duplicateResult = DuplicateAnalyzer.analyze(all);
        storageOverview = StorageOverview.from(all);
        updateOverviewPanel();
    }

    private void updateOverviewPanel() {
        String largestCategory = storageOverview.largestCategory == null ? "暂无" : storageOverview.largestCategory.title;
        updateStorageUsagePanel();
        if (overviewCompactText != null) {
            overviewCompactText.setText("共 " + storageOverview.totalFiles + " 个文件 · 扫描内容 " + FileUtils.formatSize(storageOverview.totalBytes) + " · 重复可省 " + FileUtils.formatSize(duplicateResult.wastedBytes));
        }
        if (overviewToggleText != null) {
            overviewToggleText.setText("");
        }
        if (overviewSummaryRow != null) {
            overviewSummaryRow.setVisibility(View.VISIBLE);
        }
        if (topDirectoryTitleText != null) {
            topDirectoryTitleText.setVisibility(View.GONE);
        }
        if (topDirectoryLayout != null) {
            topDirectoryLayout.setVisibility(View.GONE);
        }
        if (totalSummaryCard != null) {
            totalSummaryCard.setText("全部文件\n" + storageOverview.totalFiles + " 个\n" + FileUtils.formatSize(storageOverview.totalBytes));
        }
        if (duplicateSummaryCard != null) {
            duplicateSummaryCard.setText("重复文件\n" + duplicateResult.items.size() + " 个\n" + FileUtils.formatSize(duplicateResult.wastedBytes));
        }
        if (largeSummaryCard != null) {
            List<FileItem> largeItems = sourceForTab(new TabItem(TabItem.LARGE, "large"));
            largeSummaryCard.setText("大文件\n" + largeItems.size() + " 个\n" + FileUtils.formatSize(FileUtils.totalSize(largeItems)));
        }
        if (apkSummaryCard != null) {
            List<FileItem> apkItems = files.get(FileCategory.APK);
            apkSummaryCard.setText("安装包\n" + (apkItems == null ? 0 : apkItems.size()) + " 个\n" + FileUtils.formatSize(FileUtils.totalSize(apkItems == null ? new ArrayList<FileItem>() : apkItems)));
        }
        if (topDirectoryLayout != null) {
            topDirectoryLayout.removeAllViews();
            if (storageOverview.topDirectories.isEmpty()) {
                TextView empty = new TextView(this);
                Ui.muted(empty, 12);
                empty.setText("扫描后会显示占用最高的目录");
                topDirectoryLayout.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            } else {
                long maxBytes = storageOverview.topDirectories.get(0).totalBytes;
                for (StorageOverview.DirectoryStat stat : storageOverview.topDirectories) {
                    topDirectoryLayout.addView(directoryStatView(stat, maxBytes), new LinearLayout.LayoutParams(-1, -2));
                }
            }
        }
    }

    private void updateStorageUsagePanel() {
        if (storageRingView == null || storageUsageText == null || storageTotalText == null) {
            return;
        }
        try {
            StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            long total = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
            long available = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
            long used = Math.max(0L, total - available);
            float ratio = total <= 0L ? 0f : Math.min(1f, used / (float) total);
            int percent = Math.round(ratio * 100f);
            storageRingView.setPercent(ratio);
            storageUsageText.setText("内部存储");
            storageTotalText.setText(percent + "% 已使用\n"
                    + "已用 " + FileUtils.formatSize(used) + " · 总容量 " + FileUtils.formatSize(total));
        } catch (RuntimeException e) {
            storageRingView.setPercent(0f);
            storageUsageText.setText("内部存储");
            storageTotalText.setText("暂时无法读取容量");
        }
        if (storageLegendRow != null) {
            storageLegendRow.removeAllViews();
            FileCategory[] legendCategories = new FileCategory[]{
                    FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.DOCUMENT
            };
            for (int i = 0; i < legendCategories.length; i++) {
                FileCategory category = legendCategories[i];
                TextView chip = chipView(category.title, false);
                chip.setTextColor(colorFor(category));
                chip.setTextSize(11);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(28));
                if (i > 0) {
                    params.leftMargin = dp(6);
                }
                storageLegendRow.addView(chip, params);
            }
        }
    }

    private View directoryStatView(StorageOverview.DirectoryStat stat, long maxBytes) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dp(4), 0, dp(8));

        TextView title = new TextView(this);
        title.setText(stat.name + " · " + FileUtils.formatSize(stat.totalBytes));
        Ui.title(title, 13);
        item.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(this);
        Ui.muted(meta, 12);
        meta.setText(stat.fileCount + " 个文件 · " + stat.path);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        meta.setPadding(0, dp(4), 0, dp(6));
        item.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        View track = new View(this);
        track.setBackground(Ui.round(Ui.SOFT_GRAY, 6, density));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(6));
        item.addView(track, trackParams);

        View fill = new View(this);
        fill.setBackground(Ui.round(Ui.PRIMARY, 6, density));
        int width = maxBytes <= 0L ? dp(24)
                : Math.max(dp(24), (int) (((float) stat.totalBytes / (float) maxBytes) * dp(220)));
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(width, dp(6));
        fillParams.topMargin = -dp(6);
        item.addView(fill, fillParams);

        return item;
    }

    private void setStatusNote(String note) {
        statusNote = note == null ? "" : note;
        updatePermissionUi();
    }

    private String shortPath(String path) {
        if (path == null || path.length() == 0) {
            return "内部存储";
        }
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(root)) {
            String relative = path.substring(root.length());
            return relative.length() == 0 ? "内部存储" : relative;
        }
        return path;
    }

    private void toggleBatchMode() {
        batchMode = !batchMode;
        if (!batchMode) {
            selectedPaths.clear();
        }
        updateBatchButtons();
        clearInvisibleSelections();
        updateBatchButtons();
        updateBatchExtraButtons();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        refreshVisibleItems();
    }

    private void updateBatchButtons() {
        if (batchButton != null) {
            batchButton.setText(batchMode ? "完成选择" : "批量选择");
            batchButton.setBackground(batchMode
                    ? Ui.round(Ui.PRIMARY, 18, density)
                    : Ui.round(Ui.TEXT, 18, density));
        }
        if (selectAllButton != null) {
            selectAllButton.setEnabled(batchMode && !visibleItems.isEmpty());
            selectAllButton.setAlpha(selectAllButton.isEnabled() ? 1f : 0.45f);
            selectAllButton.setText(areAllVisibleSelected() ? "取消全选" : "全选当前");
        }
        if (deleteSelectedButton != null) {
            deleteSelectedButton.setEnabled(batchMode && !selectedPaths.isEmpty());
            deleteSelectedButton.setAlpha(deleteSelectedButton.isEnabled() ? 1f : 0.45f);
            deleteSelectedButton.setText("删除已选" + (selectedPaths.isEmpty() ? "" : " (" + selectedPaths.size() + ")"));
        }
    }

    private void updateBatchExtraButtons() {
        if (duplicateSelectButton != null) {
            boolean enabled = selectedTab.type == TabItem.DUPLICATE && duplicateResult.groupCount > 0;
            duplicateSelectButton.setEnabled(enabled);
            duplicateSelectButton.setAlpha(enabled ? 1f : 0.45f);
        }
        if (shareSelectedButton != null) {
            shareSelectedButton.setEnabled(batchMode && !selectedPaths.isEmpty());
            shareSelectedButton.setAlpha(shareSelectedButton.isEnabled() ? 1f : 0.45f);
        }
        if (copySelectedPathsButton != null) {
            copySelectedPathsButton.setEnabled(batchMode && !selectedPaths.isEmpty());
            copySelectedPathsButton.setAlpha(copySelectedPathsButton.isEnabled() ? 1f : 0.45f);
        }
    }

    private void toggleSelectAllVisible() {
        if (!batchMode) {
            return;
        }
        if (areAllVisibleSelected()) {
            for (FileItem item : visibleItems) {
                selectedPaths.remove(item.file.getAbsolutePath());
            }
        } else {
            for (FileItem item : visibleItems) {
                selectedPaths.add(item.file.getAbsolutePath());
            }
        }
        updateBatchButtons();
        updateBatchExtraButtons();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        refreshVisibleItems();
    }

    private void toggleSelectedPath(String path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        updateBatchButtons();
        updateBatchExtraButtons();
    }

    private boolean areAllVisibleSelected() {
        if (visibleItems.isEmpty()) {
            return false;
        }
        for (FileItem item : visibleItems) {
            if (!selectedPaths.contains(item.file.getAbsolutePath())) {
                return false;
            }
        }
        return true;
    }

    private void clearInvisibleSelections() {
        if (selectedPaths.isEmpty()) {
            return;
        }
        Set<String> visibleSet = new HashSet<String>();
        for (FileItem item : visibleItems) {
            visibleSet.add(item.file.getAbsolutePath());
        }
        if (visibleSet.isEmpty()) {
            selectedPaths.clear();
            return;
        }
        List<String> toRemove = new ArrayList<String>();
        for (String path : selectedPaths) {
            if (!visibleSet.contains(path)) {
                toRemove.add(path);
            }
        }
        selectedPaths.removeAll(toRemove);
    }

    private void confirmDeleteSelected() {
        if (!batchMode || selectedPaths.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("批量删除")
                .setMessage("确定删除已选的 " + selectedPaths.size() + " 个文件？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteSelectedItems();
                    }
                })
                .show();
    }

    private void confirmDeleteSelectedWithPreview() {
        if (!batchMode || selectedPaths.isEmpty()) {
            return;
        }
        List<FileItem> items = selectedItems();
        StringBuilder message = new StringBuilder();
        message.append("确定删除已选的 ")
                .append(items.size())
                .append(" 个文件？\n共 ")
                .append(FileUtils.formatSize(totalSizeOf(items)))
                .append("\n\n");
        int previewCount = Math.min(5, items.size());
        for (int i = 0; i < previewCount; i++) {
            message.append(i + 1).append(". ").append(items.get(i).file.getName()).append("\n");
        }
        if (items.size() > previewCount) {
            message.append("还有 ").append(items.size() - previewCount).append(" 个文件");
        }
        new AlertDialog.Builder(this)
                .setTitle("批量删除")
                .setMessage(message.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteSelectedItems();
                    }
                })
                .show();
    }

    private void selectDuplicateCopiesKeepingNewest() {
        if (selectedTab.type != TabItem.DUPLICATE || duplicateResult.groupCount == 0) {
            Toast.makeText(this, "当前没有可选择的重复文件", Toast.LENGTH_SHORT).show();
            return;
        }
        batchMode = true;
        selectedPaths.clear();
        for (List<FileItem> group : duplicateResult.groups) {
            FileItem newest = null;
            for (FileItem item : group) {
                if (newest == null || item.file.lastModified() > newest.file.lastModified()) {
                    newest = item;
                }
            }
            for (FileItem item : group) {
                if (newest != item) {
                    selectedPaths.add(item.file.getAbsolutePath());
                }
            }
        }
        updateBatchButtons();
        updateBatchExtraButtons();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        refreshVisibleItems();
        Toast.makeText(this, "已保留每组最新文件，选中其余重复项", Toast.LENGTH_SHORT).show();
    }

    private void shareSelectedItems() {
        List<FileItem> items = selectedItems();
        if (items.isEmpty()) {
            return;
        }
        if (items.size() == 1) {
            FileUtils.shareFile(this, items.get(0).file);
            return;
        }
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (FileItem item : items) {
            if (item.file.exists() && item.file.isFile()) {
                uris.add(LocalFileProvider.uriFor(this, item.file));
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "没有可分享的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "分享已选文件"));
        } catch (Exception e) {
            Toast.makeText(this, "没有可分享这些文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void copySelectedPaths() {
        List<FileItem> items = selectedItems();
        if (items.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (FileItem item : items) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(item.file.getAbsolutePath());
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("selected_file_paths", builder.toString()));
            Toast.makeText(this, "已复制 " + items.size() + " 个路径", Toast.LENGTH_SHORT).show();
        }
    }

    private List<FileItem> selectedItems() {
        List<FileItem> items = new ArrayList<FileItem>();
        for (String path : selectedPaths) {
            FileItem item = findItemByPath(path);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private long totalSizeOf(List<FileItem> items) {
        long total = 0L;
        for (FileItem item : items) {
            total += item.file.length();
        }
        return total;
    }

    private void deleteSelectedItems() {
        List<String> paths = new ArrayList<String>(selectedPaths);
        int successCount = 0;
        for (String path : paths) {
            FileItem item = findItemByPath(path);
            if (item == null) {
                selectedPaths.remove(path);
                continue;
            }
            File checkedFile = checkedManagedFile(item.file);
            if (checkedFile == null) {
                continue;
            }
            if (!checkedFile.exists() || checkedFile.delete()) {
                removeFromLists(item);
                imageThumbCache.remove(path);
                videoThumbCache.remove(path);
                selectedPaths.remove(path);
                successCount++;
            }
        }
        persistCurrentCache();
        updateBatchButtons();
        updateBatchExtraButtons();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        refreshVisibleItems();
        Toast.makeText(this, "已删除 " + successCount + " 个文件", Toast.LENGTH_SHORT).show();
    }

    private FileItem findItemByPath(String path) {
        for (List<FileItem> list : files.values()) {
            for (FileItem item : list) {
                if (item.file.getAbsolutePath().equals(path)) {
                    return item;
                }
            }
        }
        return null;
    }

    private void setScanning(boolean scanning) {
        isScanning = scanning;
        scanButton.setEnabled(!scanning);
        scanButton.setText(scanning ? "扫描中..." : "重新扫描");
        if (scanning) {
            statusText.setText("正在扫描内部存储，请稍候");
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermissionCompat(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private int checkSelfPermissionCompat(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission);
        }
        return PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 10);
        }
    }

    private void updatePermissionUi() {
        boolean granted = hasStorageAccess();
        permissionButton.setText(granted ? "权限已开" : "开启权限");
        if (!isScanning) {
            statusText.setText(granted
                    ? (statusNote.length() > 0 ? statusNote : "按类型整理内部存储文件")
                    : "需要开启“管理所有文件”权限后才能扫描");
        }
    }

    private List<FilterOption> filterOptionsForSelectedTab() {
        List<FilterOption> options = new ArrayList<FilterOption>();
        options.add(new FilterOption("all", "全部"));
        if (selectedTab.type != TabItem.CATEGORY) {
            return options;
        }
        if (selectedTab.category == FileCategory.DOCUMENT) {
            options.add(new FilterOption("word", "Word"));
            options.add(new FilterOption("excel", "Excel"));
            options.add(new FilterOption("ppt", "PPT"));
            options.add(new FilterOption("txt", "TXT"));
            options.add(new FilterOption("pdf", "PDF"));
        } else if (selectedTab.category == FileCategory.IMAGE) {
            options.add(new FilterOption("jpg", "JPG"));
            options.add(new FilterOption("png", "PNG"));
            options.add(new FilterOption("gif", "GIF"));
            options.add(new FilterOption("heic", "HEIC"));
            options.add(new FilterOption("other", "其他"));
        } else if (selectedTab.category == FileCategory.VIDEO) {
            options.add(new FilterOption("mp4", "MP4"));
            options.add(new FilterOption("mkv", "MKV"));
            options.add(new FilterOption("mov", "MOV"));
            options.add(new FilterOption("avi", "AVI"));
            options.add(new FilterOption("other", "其他"));
        } else if (selectedTab.category == FileCategory.AUDIO) {
            options.add(new FilterOption("mp3", "MP3"));
            options.add(new FilterOption("flac", "FLAC"));
            options.add(new FilterOption("wav", "WAV"));
            options.add(new FilterOption("m4a", "M4A"));
            options.add(new FilterOption("other", "其他"));
        } else if (selectedTab.category == FileCategory.APK) {
            options.add(new FilterOption("apk", "APK"));
            options.add(new FilterOption("apks", "APKS"));
            options.add(new FilterOption("xapk", "XAPK"));
            options.add(new FilterOption("qq", "QQ"));
        }
        addDirectoryFilterOptions(options);
        return options;
    }

    private void addDirectoryFilterOptions(List<FilterOption> options) {
        List<FileItem> source = sourceForSelectedTab();
        final Map<String, Long> sizes = new HashMap<String, Long>();
        final Map<String, String> names = new HashMap<String, String>();
        for (FileItem item : source) {
            File parent = item.file.getParentFile();
            if (parent == null) {
                continue;
            }
            String path = parent.getAbsolutePath();
            Long size = sizes.get(path);
            sizes.put(path, (size == null ? 0L : size.longValue()) + item.file.length());
            names.put(path, parent.getName().length() == 0 ? "内部存储" : parent.getName());
        }
        List<String> paths = new ArrayList<String>(sizes.keySet());
        Collections.sort(paths, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                long delta = sizes.get(right) - sizes.get(left);
                if (delta > 0) return 1;
                if (delta < 0) return -1;
                return names.get(left).compareToIgnoreCase(names.get(right));
            }
        });
        int limit = Math.min(4, paths.size());
        for (int i = 0; i < limit; i++) {
            String path = paths.get(i);
            options.add(new FilterOption("dir:" + path, names.get(path)));
        }
    }

    private void updateFilterChips() {
        if (filterRow == null) {
            return;
        }
        filterRow.removeAllViews();
        List<FilterOption> options = filterOptionsForSelectedTab();
        for (final FilterOption option : options) {
            TextView chip = chipView(option.title, option.key.equals(selectedFilterKey));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFilterKey = option.key;
                    refreshVisibleItems();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(30));
            params.rightMargin = dp(8);
            filterRow.addView(chip, params);
        }
    }

    private boolean matchesSelectedFilter(FileItem item) {
        if ("all".equals(selectedFilterKey) || item == null) {
            return true;
        }
        if (selectedFilterKey.startsWith("dir:")) {
            String path = selectedFilterKey.substring(4);
            String parent = item.file.getParent();
            return parent != null && (parent.equals(path) || parent.startsWith(path + File.separator));
        }
        String lower = item.file.getName().toLowerCase(Locale.US);
        if (selectedTab.type != TabItem.CATEGORY) {
            return true;
        }
        if (selectedTab.category == FileCategory.DOCUMENT) {
            if ("word".equals(selectedFilterKey)) return lower.endsWith(".doc") || lower.endsWith(".docx");
            if ("excel".equals(selectedFilterKey)) return lower.endsWith(".xls") || lower.endsWith(".xlsx");
            if ("ppt".equals(selectedFilterKey)) return lower.endsWith(".ppt") || lower.endsWith(".pptx");
            if ("txt".equals(selectedFilterKey)) return lower.endsWith(".txt");
            if ("pdf".equals(selectedFilterKey)) return lower.endsWith(".pdf");
        } else if (selectedTab.category == FileCategory.IMAGE) {
            if ("jpg".equals(selectedFilterKey)) return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            if ("png".equals(selectedFilterKey)) return lower.endsWith(".png");
            if ("gif".equals(selectedFilterKey)) return lower.endsWith(".gif");
            if ("heic".equals(selectedFilterKey)) return lower.endsWith(".heic") || lower.endsWith(".heif");
            if ("other".equals(selectedFilterKey)) {
                return !(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                        || lower.endsWith(".gif") || lower.endsWith(".heic") || lower.endsWith(".heif"));
            }
        } else if (selectedTab.category == FileCategory.VIDEO) {
            if ("mp4".equals(selectedFilterKey)) return lower.endsWith(".mp4");
            if ("mkv".equals(selectedFilterKey)) return lower.endsWith(".mkv");
            if ("mov".equals(selectedFilterKey)) return lower.endsWith(".mov");
            if ("avi".equals(selectedFilterKey)) return lower.endsWith(".avi");
            if ("other".equals(selectedFilterKey)) {
                return !(lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".avi"));
            }
        } else if (selectedTab.category == FileCategory.AUDIO) {
            if ("mp3".equals(selectedFilterKey)) return lower.endsWith(".mp3");
            if ("flac".equals(selectedFilterKey)) return lower.endsWith(".flac");
            if ("wav".equals(selectedFilterKey)) return lower.endsWith(".wav");
            if ("m4a".equals(selectedFilterKey)) return lower.endsWith(".m4a");
            if ("other".equals(selectedFilterKey)) {
                return !(lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".m4a"));
            }
        } else if (selectedTab.category == FileCategory.APK) {
            if ("apk".equals(selectedFilterKey)) return lower.contains(".apk") && !lower.contains(".apks") && !lower.contains(".xapk");
            if ("apks".equals(selectedFilterKey)) return lower.contains(".apks");
            if ("xapk".equals(selectedFilterKey)) return lower.contains(".xapk");
            if ("qq".equals(selectedFilterKey)) {
                String parent = item.file.getParent() == null ? "" : item.file.getParent().toLowerCase(Locale.US);
                return lower.contains("qq")
                        || parent.contains("qq")
                        || parent.contains("mobileqq")
                        || parent.contains("qqfile_recv")
                        || parent.contains("tencent");
            }
        }
        return true;
    }

    private void refreshVisibleItems() {
        updateTabCards();
        updateSortChips();
        updateFilterChips();
        visibleItems.clear();

        List<FileItem> source = sourceForSelectedTab();
        String query = searchText.toLowerCase(Locale.US);
        for (FileItem item : source) {
            String name = item.file.getName().toLowerCase(Locale.US);
            String parent = item.file.getParent() == null ? "" : item.file.getParent().toLowerCase(Locale.US);
            ApkInfo apkInfo = item.category == FileCategory.APK ? asyncApkInfoCache.get(item.file.getAbsolutePath()) : null;
            if (item.category == FileCategory.APK && apkInfo == null) {
                requestApkInfoCacheAsync(item, true);
            }
            String appName = apkInfo == null ? "" : apkInfo.appName.toLowerCase(Locale.US);
            String packageName = apkInfo == null ? "" : apkInfo.packageName.toLowerCase(Locale.US);
            if (query.length() == 0 || name.contains(query) || parent.contains(query)
                    || appName.contains(query) || packageName.contains(query)) {
                if (matchesSelectedFilter(item)) {
                    visibleItems.add(item);
                }
            }
        }

        Collections.sort(visibleItems, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem left, FileItem right) {
                if (sortMode == SortMode.SIZE) {
                    long delta = right.file.length() - left.file.length();
                    if (delta > 0) return 1;
                    if (delta < 0) return -1;
                } else if (sortMode == SortMode.NAME) {
                    return displayName(left).compareToIgnoreCase(displayName(right));
                } else {
                    long delta = right.file.lastModified() - left.file.lastModified();
                    if (delta > 0) return 1;
                    if (delta < 0) return -1;
                }
                return displayName(left).compareToIgnoreCase(displayName(right));
            }
        });

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateBatchExtraButtons();
        if (resultText != null) {
            String extra = "";
            if (selectedTab.type == TabItem.DUPLICATE) {
                extra = " · " + duplicateResult.groupCount + " 组";
            } else if (selectedTab.type == TabItem.LARGE) {
                extra = " · 阈值 " + FileUtils.formatSize(largeThresholdBytes);
            }
            if (searchText.length() > 0) {
                extra += " · 已筛选";
            }
            resultText.setText(selectedTab.title + " · " + visibleItems.size() + " 个" + extra);
        }
        updateApkScanHint(source.size());
        if (emptyText != null) {
            emptyText.setVisibility(visibleItems.size() == 0 && !isScanning ? View.VISIBLE : View.GONE);
            if (searchText.length() > 0) {
                emptyText.setText("没有找到匹配的文件");
            } else {
                if (selectedTab.type == TabItem.DUPLICATE) {
                    emptyText.setText("暂时没有检测到重复文件");
                } else {
                    emptyText.setText(selectedTab.title + "暂时没有文件");
                }
            }
        }
    }

    private void updateApkScanHint(int sourceCount) {
        if (apkScanHintText == null) {
            return;
        }
        boolean apkTab = selectedTab != null
                && selectedTab.type == TabItem.CATEGORY
                && selectedTab.category == FileCategory.APK;
        boolean show = apkTab && sourceCount <= 1 && !isScanning;
        apkScanHintText.setVisibility(show ? View.VISIBLE : View.GONE);
        if (importApkButton != null) {
            importApkButton.setVisibility(apkTab && !isScanning ? View.VISIBLE : View.GONE);
        }
    }

    private List<FileItem> sourceForSelectedTab() {
        if (selectedTab.type == TabItem.CATEGORY) {
            List<FileItem> source = files.get(selectedTab.category);
            return source == null ? new ArrayList<FileItem>() : source;
        }
        List<FileItem> all = allFiles();
        if (selectedTab.type == TabItem.LARGE) {
            List<FileItem> large = new ArrayList<FileItem>();
            for (FileItem item : all) {
                if (item.file.length() >= largeThresholdBytes) {
                    large.add(item);
                }
            }
            return large;
        }
        if (selectedTab.type == TabItem.DUPLICATE) {
            return duplicateResult.items;
        }
        return all;
    }

    private List<FileItem> allFiles() {
        List<FileItem> all = new ArrayList<FileItem>();
        for (List<FileItem> list : files.values()) {
            all.addAll(list);
        }
        return all;
    }

    private String displayName(FileItem item) {
        if (item.category == FileCategory.APK) {
            ApkInfo info = asyncApkInfoCache.get(item.file.getAbsolutePath());
            if (info == null) {
                requestApkInfoCacheAsync(item, true);
            }
            if (info != null && info.appName != null && info.appName.length() > 0) {
                return info.appName;
            }
        }
        return item.file.getName();
    }

    private void updateTabCards() {
        for (int i = 0; i < tabs.size(); i++) {
            TabItem tab = tabs.get(i);
            TextView card = tabCards.get(i);
            int count = countForTab(tab);
            long size = sizeForTab(tab);
            card.setText(tabIcon(tab) + "  " + tab.title + "\n" + count + " 个");
            boolean selected = tab == selectedTab;
            int accent = tabColor(tab);
            card.setTextColor(selected ? Color.WHITE : Ui.TEXT);
            card.setBackground(selected
                    ? Ui.round(accent, 20, density)
                    : Ui.stroke(Ui.SURFACE, Ui.LINE, 20, density));
            card.setElevation((selected ? 3f : 1f) * density);
        }
    }

    private String tabIcon(TabItem tab) {
        if (tab.type == TabItem.CATEGORY) {
            return shortLabel(tab.category);
        }
        if (tab.type == TabItem.RECENT) return "近";
        if (tab.type == TabItem.LARGE) return "大";
        return "重";
    }

    private int tabColor(TabItem tab) {
        if (tab.type == TabItem.CATEGORY) {
            return colorFor(tab.category);
        }
        if (tab.type == TabItem.RECENT) return Ui.CYAN;
        if (tab.type == TabItem.LARGE) return Ui.RED;
        return Ui.ORANGE;
    }

    private int countForTab(TabItem tab) {
        if (tab.type == TabItem.CATEGORY) {
            List<FileItem> list = files.get(tab.category);
            return list == null ? 0 : list.size();
        }
        return sourceForTab(tab).size();
    }

    private long sizeForTab(TabItem tab) {
        if (tab.type == TabItem.DUPLICATE) {
            return duplicateResult.wastedBytes;
        }
        return FileUtils.totalSize(sourceForTab(tab));
    }

    private List<FileItem> sourceForTab(TabItem tab) {
        if (tab.type == TabItem.CATEGORY) {
            List<FileItem> list = files.get(tab.category);
            return list == null ? new ArrayList<FileItem>() : list;
        }
        List<FileItem> all = allFiles();
        if (tab.type == TabItem.LARGE) {
            List<FileItem> large = new ArrayList<FileItem>();
            for (FileItem item : all) {
                if (item.file.length() >= largeThresholdBytes) {
                    large.add(item);
                }
            }
            return large;
        }
        if (tab.type == TabItem.DUPLICATE) {
            return duplicateResult.items;
        }
        return all;
    }

    private void updateSortChips() {
        updateImageViewModeButton();
        SortMode[] modes = SortMode.values();
        for (int i = 0; i < modes.length; i++) {
            TextView chip = sortChips.get(i);
            boolean selected = modes[i] == sortMode;
            chip.setTextColor(selected ? Color.WHITE : Ui.TEXT);
            chip.setBackground(selected
                    ? Ui.round(Ui.TEXT, 16, density)
                    : Ui.stroke(Ui.SURFACE, Ui.LINE, 16, density));
        }
    }

    private boolean isImageTabSelected() {
        return selectedTab != null
                && selectedTab.type == TabItem.CATEGORY
                && selectedTab.category == FileCategory.IMAGE;
    }

    private boolean isImageGridActive() {
        return isImageTabSelected() && imageGridMode;
    }

    private void updateImageViewModeButton() {
        if (imageViewModeButton == null) {
            return;
        }
        boolean enabled = isImageTabSelected();
        imageViewModeButton.setEnabled(enabled);
        imageViewModeButton.setAlpha(enabled ? 1f : 0.45f);
        imageViewModeButton.setText(imageGridMode && enabled ? "列表" : "网格");
        imageViewModeButton.setTextColor(imageGridMode && enabled ? Color.WHITE : Ui.TEXT);
        imageViewModeButton.setBackground(imageGridMode && enabled
                ? Ui.round(Ui.PRIMARY, 18, density)
                : Ui.stroke(Ui.SURFACE, Ui.LINE, 18, density));
    }

    private void showImportApkPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive",
                "application/octet-stream",
                "application/zip"
        });
        try {
            startActivityForResult(intent, REQUEST_PICK_APK);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void importSelectedApks(Intent data) {
        int success = 0;
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                if (importApkUri(clipData.getItemAt(i).getUri())) {
                    success++;
                }
            }
        } else if (data.getData() != null) {
            if (importApkUri(data.getData())) {
                success++;
            }
        }
        if (success > 0) {
            selectedTab = tabForCategory(FileCategory.APK);
            selectedFilterKey = "all";
            sortMode = SortMode.TIME;
            updateDerivedData();
            refreshVisibleItems();
            persistCurrentCache();
            setStatusNote("已导入 " + success + " 个安装包");
            Toast.makeText(this, "已导入 " + success + " 个安装包", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有导入安装包，请选择 APK/APKS/XAPK 文件", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean importApkUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        String displayName = displayNameForUri(uri);
        if (FileScanner.categoryFor(displayName) != FileCategory.APK) {
            return false;
        }
        File importDir = new File(Environment.getExternalStorageDirectory(), "Download/手机文件管理导入安装包");
        if (!importDir.exists() && !importDir.mkdirs()) {
            return false;
        }
        File target = uniqueImportedFile(importDir, normalizedApkName(displayName));
        InputStream input = null;
        OutputStream output = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                return false;
            }
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (Exception e) {
            if (target.exists()) {
                target.delete();
            }
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
        }
        addImportedApk(target);
        return true;
    }

    private String displayNameForUri(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && name.trim().length() > 0) {
                        return sanitizeFileName(name.trim());
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "imported_" + System.currentTimeMillis() + ".apk";
    }

    private String sanitizeFileName(String name) {
        String sanitized = name.replace('/', '_').replace('\\', '_').replace(':', '_');
        return sanitized.length() == 0 ? "imported_" + System.currentTimeMillis() + ".apk" : sanitized;
    }

    private String normalizedApkName(String name) {
        String sanitized = sanitizeFileName(name);
        String lower = sanitized.toLowerCase(Locale.US);
        String[] markers = {".apks", ".xapk", ".apk"};
        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                return sanitized.substring(0, index + marker.length());
            }
        }
        return sanitized + ".apk";
    }

    private File uniqueImportedFile(File directory, String name) {
        File target = new File(directory, name);
        if (!target.exists()) {
            return target;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        while (target.exists()) {
            target = new File(directory, base + "_" + index + extension);
            index++;
        }
        return target;
    }

    private void addImportedApk(File file) {
        List<FileItem> apkItems = files.get(FileCategory.APK);
        if (apkItems == null) {
            apkItems = new ArrayList<FileItem>();
            files.put(FileCategory.APK, apkItems);
        }
        String path = file.getAbsolutePath();
        for (FileItem item : apkItems) {
            if (item.file.getAbsolutePath().equals(path)) {
                return;
            }
        }
        apkItems.add(new FileItem(file, FileCategory.APK));
    }

    private TabItem tabForCategory(FileCategory category) {
        for (TabItem tab : tabs) {
            if (tab.type == TabItem.CATEGORY && tab.category == category) {
                return tab;
            }
        }
        return selectedTab;
    }

    private void showLargeThresholdDialog() {
        final String[] labels = {"20MB", "50MB", "100MB", "500MB"};
        final long[] values = {
                20L * 1024L * 1024L,
                50L * 1024L * 1024L,
                100L * 1024L * 1024L,
                500L * 1024L * 1024L
        };
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == largeThresholdBytes) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("大文件阈值")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        largeThresholdBytes = values[which];
                        thresholdButton.setText("大文件 " + labels[which]);
                        if (selectedTab.type == TabItem.LARGE) {
                            sortMode = SortMode.SIZE;
                        }
                        refreshVisibleItems();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void showMoreActions(final FileItem item) {
        String[] actions = {"查看详情", "分享文件", "复制路径", "重命名", "删除文件"};
        new AlertDialog.Builder(this)
                .setTitle(displayName(item))
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            openDetail(item);
                        } else if (which == 1) {
                            FileUtils.shareFile(MainActivity.this, item.file);
                        } else if (which == 2) {
                            copyPath(item.file);
                        } else if (which == 3) {
                            showRenameDialog(item);
                        } else {
                            confirmDelete(item);
                        }
                    }
                })
                .show();
    }

    private void openDetail(FileItem item) {
        Intent intent = new Intent(this, FileDetailActivity.class);
        intent.putExtra("path", item.file.getAbsolutePath());
        startActivity(intent);
    }

    private void copyPath(File file) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("file_path", file.getAbsolutePath()));
            Toast.makeText(this, "路径已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialog(final FileItem item) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(item.file.getName());
        input.setSelectAllOnFocus(false);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        renameFileItem(item, input.getText().toString().trim());
                    }
                })
                .show();
    }

    private void renameFileItem(FileItem item, String newName) {
        if (newName.length() == 0 || newName.contains("/") || newName.contains("\\")) {
            Toast.makeText(this, "文件名不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        File sourceFile = checkedManagedFile(item.file);
        if (sourceFile == null) {
            Toast.makeText(this, "文件路径不在允许范围内", Toast.LENGTH_SHORT).show();
            return;
        }
        File parent = item.file.getParentFile();
        if (parent == null) {
            Toast.makeText(this, "无法获取所在目录", Toast.LENGTH_SHORT).show();
            return;
        }
        File target = new File(parent, newName);
        File checkedTarget = checkedManagedTarget(target);
        if (checkedTarget == null) {
            Toast.makeText(this, "目标路径不在允许范围内", Toast.LENGTH_SHORT).show();
            return;
        }
        if (target.exists()) {
            Toast.makeText(this, "同名文件已存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sourceFile.renameTo(checkedTarget)) {
            replaceFileItem(item, new FileItem(checkedTarget, item.category));
            imageThumbCache.remove(item.file.getAbsolutePath());
            persistCurrentCache();
            Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "重命名失败，可能没有权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(final FileItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定删除“" + item.file.getName() + "”？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteFileItem(item);
                    }
                })
                .show();
    }

    private void deleteFileItem(FileItem item) {
        File checkedFile = checkedManagedFile(item.file);
        if (checkedFile == null) {
            Toast.makeText(this, "文件路径不在允许范围内", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!checkedFile.exists()) {
            removeFromLists(item);
            persistCurrentCache();
            Toast.makeText(this, "文件已不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkedFile.delete()) {
            removeFromLists(item);
            imageThumbCache.remove(item.file.getAbsolutePath());
            persistCurrentCache();
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "删除失败，可能没有权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void replaceFileItem(FileItem oldItem, FileItem newItem) {
        List<FileItem> source = files.get(oldItem.category);
        if (source != null) {
            int index = source.indexOf(oldItem);
            if (index >= 0) {
                source.set(index, newItem);
            }
        }
        selectedPaths.remove(oldItem.file.getAbsolutePath());
        selectedPaths.add(newItem.file.getAbsolutePath());
        updateDerivedData();
        refreshVisibleItems();
    }

    private File checkedManagedFile(File file) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File root = Environment.getExternalStorageDirectory().getCanonicalFile();
            String filePath = canonicalFile.getPath();
            String rootPath = root.getPath();
            if ((!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator))
                    || !canonicalFile.isFile()) {
                return null;
            }
            return canonicalFile;
        } catch (IOException e) {
            return null;
        }
    }

    private File checkedManagedTarget(File file) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File root = Environment.getExternalStorageDirectory().getCanonicalFile();
            String filePath = canonicalFile.getPath();
            String rootPath = root.getPath();
            if (!filePath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return canonicalFile;
        } catch (IOException e) {
            return null;
        }
    }

    private void removeFromLists(FileItem item) {
        List<FileItem> source = files.get(item.category);
        if (source != null) {
            source.remove(item);
        }
        visibleItems.remove(item);
        selectedPaths.remove(item.file.getAbsolutePath());
        updateDerivedData();
        refreshVisibleItems();
    }

    private int countAllFiles() {
        int count = 0;
        for (List<FileItem> list : files.values()) {
            count += list.size();
        }
        return count;
    }

    private int dp(int value) {
        return (int) (value * density + 0.5f);
    }

    private class StorageRingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private float percent;

        StorageRingView(Context context) {
            super(context);
        }

        void setPercent(float value) {
            percent = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int size = Math.min(getWidth(), getHeight());
            float stroke = dp(12);
            float center = size / 2f;
            float radius = center - stroke / 2f - dp(4);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Ui.SOFT_GRAY);
            canvas.drawCircle(center, center, radius, paint);

            arcRect.set(center - radius, center - radius, center + radius, center + radius);
            paint.setColor(Ui.PRIMARY);
            canvas.drawArc(arcRect, -90f, Math.max(4f, 360f * percent), false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(Ui.TEXT);
            paint.setTextSize(dp(24));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            canvas.drawText(Math.round(percent * 100f) + "%", center,
                    center - (metrics.ascent + metrics.descent) / 2f - dp(6), paint);

            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(Ui.MUTED);
            paint.setTextSize(dp(12));
            canvas.drawText("已使用", center, center + dp(26), paint);
        }
    }

    private class FileListAdapter extends BaseAdapter {
        private static final int GRID_COLUMNS = 3;

        class FileRowHolder {
            LinearLayout row;
            ImageView leading;
            TextView name;
            TextView secondary;
            TextView meta;
            TextView path;
            CheckBox checkBox;
            FileItem item;
            String itemPath;
        }

        class ImageGridRowHolder {
            LinearLayout row;
            ImageGridCellHolder[] cells = new ImageGridCellHolder[GRID_COLUMNS];
        }

        class ImageGridCellHolder {
            LinearLayout cell;
            ImageView image;
            TextView name;
            CheckBox checkBox;
            String itemPath;
            FileItem item;
        }

        @Override
        public int getCount() {
            if (isImageGridActive()) {
                return (visibleItems.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
            }
            return visibleItems.size();
        }

        @Override
        public FileItem getItem(int position) {
            if (isImageGridActive()) {
                return visibleItems.get(position * GRID_COLUMNS);
            }
            return visibleItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (isImageGridActive()) {
                return imageGridRow(position, convertView);
            }
            FileRowHolder holder;
            if (convertView == null || !(convertView.getTag() instanceof FileRowHolder)) {
                holder = createFileRowHolder();
                convertView = holder.row;
                convertView.setTag(holder);
            } else {
                holder = (FileRowHolder) convertView.getTag();
            }
            bindFileRow(holder, getItem(position));
            return convertView;
        }

        private FileRowHolder createFileRowHolder() {
            final FileRowHolder holder = new FileRowHolder();
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(18), dp(16), dp(18), dp(16));
            row.setElevation(1.2f * density);
            holder.row = row;

            LinearLayout titleLine = new LinearLayout(MainActivity.this);
            titleLine.setOrientation(LinearLayout.HORIZONTAL);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));

            holder.leading = new ImageView(MainActivity.this);
            holder.leading.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.leading.setBackground(Ui.round(Ui.SOFT_GRAY, 16, density));
            titleLine.addView(holder.leading, new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout textColumn = new LinearLayout(MainActivity.this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
            textParams.leftMargin = dp(12);
            titleLine.addView(textColumn, textParams);

            holder.checkBox = new CheckBox(MainActivity.this);
            titleLine.addView(holder.checkBox, new LinearLayout.LayoutParams(-2, -2));

            holder.name = new TextView(MainActivity.this);
            Ui.title(holder.name, 16);
            holder.name.setSingleLine(true);
            holder.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textColumn.addView(holder.name, new LinearLayout.LayoutParams(-1, -2));

            holder.secondary = new TextView(MainActivity.this);
            Ui.muted(holder.secondary, 12);
            holder.secondary.setSingleLine(true);
            holder.secondary.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            holder.secondary.setPadding(0, dp(6), 0, 0);
            textColumn.addView(holder.secondary, new LinearLayout.LayoutParams(-1, -2));

            holder.meta = new TextView(MainActivity.this);
            Ui.muted(holder.meta, 13);
            holder.meta.setPadding(0, dp(10), 0, 0);
            row.addView(holder.meta, new LinearLayout.LayoutParams(-1, -2));

            holder.path = new TextView(MainActivity.this);
            Ui.muted(holder.path, 12);
            holder.path.setSingleLine(true);
            holder.path.setEllipsize(TextUtils.TruncateAt.START);
            holder.path.setPadding(0, dp(6), 0, dp(12));
            row.addView(holder.path, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout buttons = new LinearLayout(MainActivity.this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(buttons, new LinearLayout.LayoutParams(-1, dp(36)));

            Button detail = actionButton("\u8be6\u60c5", Ui.SURFACE, Ui.TEXT);
            detail.setBackground(Ui.stroke(Ui.SURFACE, Ui.LINE, 16, density));
            buttons.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));

            Button folder = actionButton("\u76ee\u5f55", Ui.PRIMARY, Color.WHITE);
            LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(0, -1, 1);
            folderParams.leftMargin = dp(8);
            buttons.addView(folder, folderParams);


            Button more = actionButton("⋮", Ui.SOFT_GRAY, Ui.TEXT);
            LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(0, -1, 1);
            moreParams.leftMargin = dp(8);
            buttons.addView(more, moreParams);

            detail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileItem item = holder.item;
                    if (item != null) openDetail(item);
                }
            });
            folder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileItem item = holder.item;
                    if (item == null) return;
                    File dir = item.file.getParentFile();
                    if (!ExternalDirectoryOpener.open(MainActivity.this, dir)) {
                        Intent intent = new Intent(MainActivity.this, DirectoryActivity.class);
                        intent.putExtra("dir", item.file.getParent());
                        intent.putExtra("highlight", item.file.getAbsolutePath());
                        startActivity(intent);
                    }
                }
            });
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileItem item = holder.item;
                    if (item != null) showMoreActions(item);
                }
            });

            return holder;
        }

        private void bindFileRow(final FileRowHolder holder, final FileItem item) {
            final String itemPath = item.file.getAbsolutePath();
            holder.itemPath = itemPath;
            holder.item = item;
            holder.row.setBackground(selectedPaths.contains(itemPath)
                    ? Ui.stroke(Ui.SOFT_BLUE, Color.rgb(169, 198, 255), 24, density)
                    : Ui.stroke(Ui.SURFACE, Ui.LINE, 24, density));
            holder.name.setText(item.file.getName());
            holder.secondary.setText(item.category.title + " · 加载中…");
            holder.path.setText(item.file.getParent());
            String metaText = item.category.title + " · " + FileUtils.formatSize(item.file.length()) + " · " + FileUtils.formatTime(item.file.lastModified());
            if (selectedTab.type == TabItem.DUPLICATE) {
                metaText = "重复文件 · " + metaText;
            }
            holder.meta.setText(metaText);

            holder.checkBox.setChecked(selectedPaths.contains(itemPath));
            holder.checkBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder.checkBox.isChecked()) {
                        selectedPaths.add(itemPath);
                    } else {
                        selectedPaths.remove(itemPath);
                    }
                    updateBatchButtons();
                    updateBatchExtraButtons();
                    notifyDataSetChanged();
                }
            });

            holder.row.setOnClickListener(batchMode ? new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean checked = !selectedPaths.contains(itemPath);
                    if (checked) {
                        selectedPaths.add(itemPath);
                    } else {
                        selectedPaths.remove(itemPath);
                    }
                    holder.checkBox.setChecked(checked);
                    updateBatchButtons();
                    updateBatchExtraButtons();
                    notifyDataSetChanged();
                }
            } : new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileUtils.openFile(MainActivity.this, item.file);
                }
            });

            holder.leading.setImageDrawable(null);
            holder.leading.setPadding(0, 0, 0, 0);
            holder.leading.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.leading.setBackground(Ui.round(Ui.SOFT_GRAY, 16, density));
            holder.leading.setTag(itemPath);

            requestListMetaAsync(holder, item, itemPath);
            requestLeadingAsync(holder, item, itemPath);
        }

        private View imageGridRow(int rowIndex, View convertView) {
            ImageGridRowHolder holder;
            if (convertView == null || !(convertView.getTag() instanceof ImageGridRowHolder)) {
                holder = new ImageGridRowHolder();
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, 0);
                holder.row = row;
                for (int column = 0; column < GRID_COLUMNS; column++) {
                    ImageGridCellHolder cellHolder = createImageGridCellHolder();
                    holder.cells[column] = cellHolder;
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(146), 1);
                    if (column > 0) params.leftMargin = dp(8);
                    row.addView(cellHolder.cell, params);
                }
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (ImageGridRowHolder) convertView.getTag();
            }

            for (int column = 0; column < GRID_COLUMNS; column++) {
                int index = rowIndex * GRID_COLUMNS + column;
                if (index < visibleItems.size()) {
                    bindImageGridCell(holder.cells[column], visibleItems.get(index));
                } else {
                    clearImageGridCell(holder.cells[column]);
                }
            }
            return convertView;
        }

        private ImageGridCellHolder createImageGridCellHolder() {
            final ImageGridCellHolder holder = new ImageGridCellHolder();
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            cell.setElevation(1.6f * density);
            holder.cell = cell;

            holder.image = new ImageView(MainActivity.this);
            holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.addView(holder.image, new LinearLayout.LayoutParams(-1, 0, 1));

            LinearLayout nameLine = new LinearLayout(MainActivity.this);
            nameLine.setOrientation(LinearLayout.HORIZONTAL);
            nameLine.setGravity(Gravity.CENTER_VERTICAL);
            nameLine.setPadding(0, dp(6), 0, 0);
            cell.addView(nameLine, new LinearLayout.LayoutParams(-1, -2));

            holder.name = new TextView(MainActivity.this);
            Ui.muted(holder.name, 11);
            holder.name.setSingleLine(true);
            holder.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            nameLine.addView(holder.name, new LinearLayout.LayoutParams(0, -2, 1));

            holder.checkBox = new CheckBox(MainActivity.this);
            nameLine.addView(holder.checkBox, new LinearLayout.LayoutParams(-2, -2));

            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder.itemPath == null) return;
                    toggleSelectedPath(holder.itemPath);
                    notifyDataSetChanged();
                }
            });

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder.itemPath == null || holder.item == null) return;
                    if (batchMode) {
                        toggleSelectedPath(holder.itemPath);
                        notifyDataSetChanged();
                    } else {
                        FileUtils.openFile(MainActivity.this, holder.item.file);
                    }
                }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (holder.itemPath == null) return false;
                    if (!batchMode) batchMode = true;
                    toggleSelectedPath(holder.itemPath);
                    notifyDataSetChanged();
                    refreshVisibleItems();
                    return true;
                }
            });
            return holder;
        }

        private void bindImageGridCell(final ImageGridCellHolder holder, final FileItem item) {
            String itemPath = item.file.getAbsolutePath();
            holder.itemPath = itemPath;
            holder.item = item;
            holder.cell.setVisibility(View.VISIBLE);
            holder.cell.setBackground(Ui.round(selectedPaths.contains(itemPath) ? Ui.SOFT_BLUE : Ui.SURFACE, 18, density));
            holder.name.setText(item.file.getName());
            holder.checkBox.setChecked(selectedPaths.contains(itemPath));
            holder.checkBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
            holder.image.setImageDrawable(null);
            holder.image.setBackground(Ui.round(Ui.SOFT_GRAY, 12, density));
            holder.image.setTag(itemPath);
            requestImageThumbnailAsync(item.file, itemPath, holder.image);
        }

        private void clearImageGridCell(ImageGridCellHolder holder) {
            holder.itemPath = null;
            holder.item = null;
            holder.cell.setVisibility(View.INVISIBLE);
            holder.name.setText("");
            holder.checkBox.setChecked(false);
            holder.checkBox.setVisibility(View.GONE);
            holder.image.setImageDrawable(null);
            holder.image.setTag(null);
        }
    }

    private void requestListMetaAsync(final FileListAdapter.FileRowHolder holder, final FileItem item, final String itemPath) {
        if (item.category == FileCategory.APK) {
            ApkInfo cached = asyncApkInfoCache.get(itemPath);
            if (cached != null) {
                if (itemPath.equals(holder.itemPath)) {
                    holder.name.setText(cached.appName);
                    holder.secondary.setText("版本 " + cached.versionName + " · " + cached.packageName);
                    if (cached.icon != null) {
                        holder.leading.setBackground(null);
                        holder.leading.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        holder.leading.setPadding(dp(2), dp(2), dp(2), dp(2));
                        holder.leading.setImageDrawable(cached.icon);
                    }
                }
                return;
            }
            requestApkInfoCacheAsync(item, true);
            return;
        }
        if (item.category == FileCategory.AUDIO || item.category == FileCategory.VIDEO) {
            MediaInfo cached = asyncMediaInfoCache.get(itemPath);
            if (cached != null) {
                if (!itemPath.equals(holder.itemPath)) {
                    return;
                }
                if (item.category == FileCategory.AUDIO) {
                    if (cached.title != null && !"-".equals(cached.title)) {
                        holder.name.setText(cached.title);
                    }
                    holder.secondary.setText(cached.artist + " · " + cached.album + " · " + cached.durationText());
                } else if (cached.durationMs > 0) {
                    holder.secondary.setText("视频 · " + cached.durationText());
                } else {
                    holder.secondary.setText(item.category.title);
                }
                return;
            }
            if (!loadingMediaInfoPaths.add(itemPath)) {
                return;
            }
            previewExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final MediaInfo info = mediaInfoReader.read(item.file, false);
                    loadingMediaInfoPaths.remove(itemPath);
                    if (info != null) {
                        asyncMediaInfoCache.put(itemPath, info);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!itemPath.equals(holder.itemPath)) {
                                return;
                            }
                            if (item.category == FileCategory.AUDIO && info != null) {
                                if (info.title != null && !"-".equals(info.title)) {
                                    holder.name.setText(info.title);
                                }
                                holder.secondary.setText(info.artist + " · " + info.album + " · " + info.durationText());
                            } else if (item.category == FileCategory.VIDEO && info != null && info.durationMs > 0) {
                                holder.secondary.setText("视频 · " + info.durationText());
                            } else {
                                holder.secondary.setText(item.category.title);
                            }
                        }
                    });
                }
            });
            return;
        }
        holder.secondary.setText(item.category.title);
    }

    private void requestLeadingAsync(final FileListAdapter.FileRowHolder holder, final FileItem item, final String itemPath) {
        if (item.category == FileCategory.APK) {
            ApkInfo cached = asyncApkInfoCache.get(itemPath);
            if (cached != null && cached.icon != null) {
                holder.leading.setBackground(null);
                holder.leading.setScaleType(ImageView.ScaleType.FIT_CENTER);
                holder.leading.setPadding(dp(2), dp(2), dp(2), dp(2));
                holder.leading.setImageDrawable(cached.icon);
                return;
            }
            requestApkInfoCacheAsync(item, true);
            return;
        }
        if (item.category == FileCategory.IMAGE) {
            requestImageThumbnailAsync(item.file, itemPath, holder.leading);
            return;
        }
        if (item.category == FileCategory.VIDEO) {
            requestVideoThumbnailAsync(item.file, itemPath, holder.leading);
            return;
        }
        holder.leading.setBackground(Ui.round(colorFor(item.category), 14, density));
    }

    private void requestApkInfoCacheAsync(final FileItem item, final boolean refreshVisibleList) {
        final String itemPath = item.file.getAbsolutePath();
        if (asyncApkInfoCache.containsKey(itemPath)) {
            return;
        }
        if (!loadingApkInfoPaths.add(itemPath)) {
            return;
        }
        previewExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final ApkInfo apkInfo = apkInfoReader.read(item.file);
                loadingApkInfoPaths.remove(itemPath);
                if (apkInfo != null) {
                    asyncApkInfoCache.put(itemPath, apkInfo);
                }
                if (!refreshVisibleList) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshVisibleItems();
                    }
                });
            }
        });
    }

    private void requestImageThumbnailAsync(final File file, final String itemPath, final ImageView imageView) {
        imageView.setTag(itemPath);
        Bitmap cached = imageThumbCache.get(itemPath);
        if (cached != null) {
            imageView.setBackground(null);
            imageView.setImageBitmap(cached);
            return;
        }
        if (!loadingImageThumbPaths.add(itemPath)) {
            return;
        }
        previewExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = thumbnailFor(file);
                loadingImageThumbPaths.remove(itemPath);
                if (bitmap == null) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = imageView.getTag();
                        if (!(tag instanceof String) || !itemPath.equals(tag)) {
                            return;
                        }
                        imageView.setBackground(null);
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private void requestVideoThumbnailAsync(final File file, final String itemPath, final ImageView imageView) {
        imageView.setTag(itemPath);
        Bitmap cached = videoThumbCache.get(itemPath);
        if (cached != null) {
            imageView.setBackground(null);
            imageView.setImageBitmap(cached);
            return;
        }
        if (!loadingVideoThumbPaths.add(itemPath)) {
            return;
        }
        previewExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = videoThumbnailFor(file);
                loadingVideoThumbPaths.remove(itemPath);
                if (bitmap == null) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = imageView.getTag();
                        if (!(tag instanceof String) || !itemPath.equals(tag)) {
                            return;
                        }
                        imageView.setBackground(null);
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private Bitmap thumbnailFor(File file) {
        String path = file.getAbsolutePath();
        Bitmap cached = imageThumbCache.get(path);
        if (cached != null) {
            return cached;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        int target = dp(90);
        while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap != null) {
            imageThumbCache.put(path, bitmap);
        }
        return bitmap;
    }

    private Bitmap videoThumbnailFor(File file) {
        String path = file.getAbsolutePath();
        Bitmap cached = videoThumbCache.get(path);
        if (cached != null) {
            return cached;
        }
        MediaInfo info = mediaInfoReader.read(file, true);
        if (info != null && info.frame != null) {
            videoThumbCache.put(path, info.frame);
            return info.frame;
        }
        return null;
    }

    private String shortLabel(FileCategory category) {
        if (category == FileCategory.DOCUMENT) return "文";
        if (category == FileCategory.IMAGE) return "图";
        if (category == FileCategory.VIDEO) return "视";
        if (category == FileCategory.AUDIO) return "音";
        return "包";
    }

    private int colorFor(FileCategory category) {
        if (category == FileCategory.DOCUMENT) return Ui.ORANGE;
        if (category == FileCategory.IMAGE) return Ui.PRIMARY;
        if (category == FileCategory.VIDEO) return Ui.PURPLE;
        if (category == FileCategory.AUDIO) return Ui.RED;
        return Color.rgb(82, 128, 235);
    }
}
