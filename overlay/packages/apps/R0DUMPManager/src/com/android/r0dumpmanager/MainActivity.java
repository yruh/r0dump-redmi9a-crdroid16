package com.android.r0dumpmanager;

import androidx.activity.ComponentActivity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends ComponentActivity {
    private static final String LOG_TAG = "[R0DUMP]";
    private static final String R0DUMP_SETTING_ENABLED = "r0dump.dump.enabled";
    private static final String R0DUMP_SETTING_GLOBAL_RUNTIME_ENABLED =
            "r0dump.dump.global_runtime_enabled";
    private static final String R0DUMP_SETTING_TARGET_PACKAGE = "r0dump.dump.target_package";
    private static final String R0DUMP_SETTING_OUTPUT_ROOT = "r0dump.dump.output_root";
    private static final String R0DUMP_SETTING_DELAY_MS = "r0dump.dump.delay_ms";
    private static final String R0DUMP_SETTING_TARGET_PROCESS = "r0dump.dump.target_process";
    private static final String R0DUMP_SETTING_CLASS_PREFIX = "r0dump.dump.class_prefix";
    private static final String R0DUMP_SETTING_MAX_METHODS = "r0dump.dump.max_methods";
    private static final String R0DUMP_SETTING_DUMP_CONSTRUCTORS = "r0dump.dump.dump_constructors";
    private static final String R0DUMP_SETTING_DUMP_METHODS = "r0dump.dump.dump_methods";
    private static final String R0DUMP_SETTING_STRATEGY_MASK = "r0dump.dump.strategy_mask";
    private static final String R0DUMP_SETTING_MAX_RECORDS = "r0dump.dump.max_records";
    private static final String R0DUMP_SETTING_MAX_SECONDS = "r0dump.dump.max_seconds";
    private static final String R0DUMP_SETTING_STOP_AFTER_COMPLETE = "r0dump.dump.stop_after_complete";
    private static final String R0DUMP_SETTING_CLASS_WALK_MODE = "r0dump.dump.class_walk_mode";
    private static final String R0DUMP_SETTING_CLASS_WALK_THREADS = "r0dump.dump.class_walk.threads";
    private static final String R0DUMP_SETTING_PROCESS_MODE = "r0dump.dump.process_mode";
    private static final String R0DUMP_SETTING_EXIT_OBSERVER_ENABLED =
            "r0dump.dump.exit_observer.enabled";
    private static final String R0DUMP_SETTING_ART_CLASSLOADER_SCAN_ENABLED =
            "r0dump.dump.art_classloader_scan.enabled";
    private static final String R0DUMP_SETTING_LOADED_CLASS_TABLE_SCAN_ENABLED =
            "r0dump.dump.loaded_class_table_scan.enabled";
    private static final String R0DUMP_SETTING_MANIFEST_COMPONENT_SEED_ENABLED =
            "r0dump.dump.manifest_component_seed.enabled";
    private static final String R0DUMP_SETTING_FORCE_BACKFILL_ENABLED =
            "r0dump.dump.force_backfill.enabled";
    private static final String R0DUMP_SETTING_FORCE_BACKFILL_MAX_METHODS =
            "r0dump.dump.force_backfill.max_methods";
    private static final String R0DUMP_SETTING_FORCE_BACKFILL_ONLY_STATIC =
            "r0dump.dump.force_backfill.only_static";
    private static final String R0DUMP_SETTING_FORCE_BACKFILL_SKIP_NATIVE =
            "r0dump.dump.force_backfill.skip_native";
    private static final String R0DUMP_SETTING_FORCE_BACKFILL_SKIP_CONSTRUCTOR =
            "r0dump.dump.force_backfill.skip_constructor";
    private static final String R0DUMP_SETTING_FORCE_BACKFILL_CLASS_PREFIX =
            "r0dump.dump.force_backfill.class_prefix";
    private static final String R0DUMP_SETTING_RAW_DEXDATA_MIRROR_ENABLED =
            "r0dump.dump.raw_dexdata_mirror.enabled";
    private static final String R0DUMP_SETTING_ASYNC_EXPORT_ENABLED =
            "r0dump.dump.async_export.enabled";
    private static final String R0DUMP_SETTING_ANR_PROTECTION_ENABLED =
            "r0dump.dump.anr_protection.enabled";
    private static final String R0DUMP_SETTING_RUN_ID = "r0dump.dump.run_id";
    private static final String EXTRA_R0DUMP_ACTION = "r0dump_action";
    private static final String EXTRA_R0DUMP_TARGET_PACKAGE = "r0dump_target_package";
    private static final String EXTRA_R0DUMP_OUTPUT_ROOT = "r0dump_output_root";
    private static final String EXTRA_R0DUMP_TARGET_PROCESS = "r0dump_target_process";
    private static final String EXTRA_R0DUMP_RAW_MIRROR = "r0dump_raw_mirror";
    private static final String EXTRA_R0DUMP_FINISH = "r0dump_finish";
    private static final String AUTOMATION_ACTION_START = "start";
    private static final String AUTOMATION_ACTION_STOP = "stop";
    private static final String AUTOMATION_ACTION_SCAN = "scan";
    private static final String AUTOMATION_ACTION_REPAIR = "repair";
    // Some launcher activities perform a delayed hand-off to a second activity. Keep the
    // manager alive during that hand-off so Android's background-activity-start guard does not
    // reject the second launch (App Cloner is one example).
    private static final long AUTOMATION_TARGET_HANDOFF_GRACE_MS = 20000L;

    public static final int STRATEGY_CLASS_WALK = 1 << 0;
    public static final int STRATEGY_APP_CREATE = 1 << 1;
    public static final int STRATEGY_ACTIVITY_CREATE = 1 << 2;
    public static final int STRATEGY_REAL_INVOKE = 1 << 3;
    public static final int STRATEGY_LOAD_METHOD = 1 << 4;
    public static final int STRATEGY_DEX_LOAD = 1 << 5;
    public static final int STRATEGY_REGISTER_DEX = 1 << 6;
    public static final int STRATEGY_IN_MEMORY_DEX = 1 << 7;
    public static final int STRATEGY_DEFINE_CLASS = 1 << 8;
    public static final int STRATEGY_LOAD_CLASS = 1 << 9;
    public static final int STRATEGY_RESOLVE_METHOD = 1 << 10;
    public static final int STRATEGY_FORCE_BACKFILL = 1 << 11;
    public static final int STRATEGY_FORCE_BACKFILL_BEFORE = 1 << 12;
    public static final int STRATEGY_FORCE_BACKFILL_AFTER = 1 << 13;
    public static final int STRATEGY_OPEN_COMMON = 1 << 14;
    public static final int STRATEGY_OPEN_DEX_FILES_FROM_OAT = 1 << 15;
    public static final int STRATEGY_VDEX_OPEN_ALL_DEX_FILES = 1 << 16;
    public static final int STRATEGY_OAT_DEX_FILE_OPEN = 1 << 17;
    public static final int STRATEGY_VERIFY_CLASS = 1 << 18;
    public static final int STRATEGY_CLASS_INIT_BEFORE = 1 << 19;
    public static final int STRATEGY_CLASS_INIT_AFTER = 1 << 20;
    public static final int STRATEGY_INTERPRETER_EXECUTE = 1 << 21;
    public static final int STRATEGY_JIT_METHOD_ENTERED = 1 << 22;
    public static final int STRATEGY_JIT_COMPILE = 1 << 23;
    public static final int STRATEGY_REFLECT_METHOD_INVOKE = 1 << 24;
    public static final int STRATEGY_INSTRUMENT_METHOD_ENTER = 1 << 25;
    public static final int STRATEGY_INSTRUMENT_METHOD_EXIT = 1 << 26;
    public static final int STRATEGY_JAVA_CLASS_LOADER_ROUTE = 1 << 27;
    public static final int STRATEGY_JAVA_DEXFILE_ROUTE = 1 << 28;
    public static final int STRATEGY_DEFINE_CLASS_NATIVE = 1 << 29;
    public static final int STRATEGY_OAT_REGISTER = 1 << 30;
    public static final int STRATEGY_IMAGE_SPACE_DEX = 1 << 31;
    // Keep startup responsive.  DEFINE_CLASS performs synchronous ART-side work and is
    // intentionally an opt-in lab strategy; the delayed class walk supplies method records.
    private static final int DEFAULT_STRATEGY_MASK =
            STRATEGY_CLASS_WALK | STRATEGY_APP_CREATE
                    | STRATEGY_ACTIVITY_CREATE | STRATEGY_IN_MEMORY_DEX;
    private static final int LEGACY_DEFAULT_STRATEGY_MASK =
            DEFAULT_STRATEGY_MASK | STRATEGY_DEFINE_CLASS;
    private static final String DEFAULT_OUTPUT_ROOT = "/sdcard/Download/R0DUMP";
    private static final String STATUS_FILE_NAME = "status.json";
    private static final String LEGACY_STATUS_FILE_NAME = "_r0dump_status.json";
    private static final String FALLBACK_STATUS_FILE_NAME = "r0dump_status.json";
    private static final String[] STATUS_FILE_NAMES = {
            STATUS_FILE_NAME,
            LEGACY_STATUS_FILE_NAME,
            FALLBACK_STATUS_FILE_NAME
    };
    private static final String LANGUAGE_SYSTEM = "";
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String LANGUAGE_CHINESE_SIMPLIFIED = "zh-CN";
    private static final String UI_PREFS = "r0dump_manager_ui";
    private static final String UI_LANGUAGE_TAG = "language_tag";
    private static final String UI_LOGCAT_R0DUMP_ONLY = "logcat_r0dump_only";
    private static final String UI_DEFINE_CLASS_DEFAULT_MIGRATED =
            "define_class_default_migrated";
    private static final String UI_STARTUP_SAFE_DEFAULT_MIGRATED =
            "startup_safe_default_migrated";
    private static final String UI_PERFORMANCE_DEFAULTS_MIGRATED =
            "performance_defaults_migrated_v2";
    private static final String DEFAULT_DELAY_MS = "10000";
    private static final long STATUS_STALE_GRACE_SECONDS = 15L;
    private static final long UI_REFRESH_DEBOUNCE_MS = 120L;
    private static final int LOGCAT_LINE_LIMIT = 300;
    private static final int LOGCAT_BUFFER_LINE_LIMIT = 2000;


    private static final Pattern DEX_FILE_PATTERN = Pattern.compile(
            "^(?:dex|dexfixed|dexcontainer)_([0-9a-fA-F]+)_(\\d+)_(0x[0-9a-fA-F]+)_(.+)\\.dex$");
    private static final Pattern DEXDATA_FILE_PATTERN = Pattern.compile(
            "^dexdata_([0-9a-fA-F]+)_(\\d+)_(0x[0-9a-fA-F]+)_(.+)\\.bin$");
    private static final Pattern DEXRECON_FILE_PATTERN = Pattern.compile(
            "^dexrecon_([0-9a-fA-F]+)_(\\d+)_m\\d+_c\\d+_(0x[0-9a-fA-F]+)_(.+)\\.dex$");
    private static final int DEX_HEADER_SIZE = 0x70;
    private static final int DEX_ENDIAN_TAG = 0x12345678;
    private static final int MAX_REBUILT_DEX_SIZE = 512 * 1024 * 1024;
    private static final int REPAIR_IO_BUFFER_SIZE = 256 * 1024;
    private static final int MAX_RECORD_LINE_CHARS = 8 * 1024 * 1024;
    private static final int MAX_CODE_ITEM_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DEX_HEADER_SNAPSHOT_BYTES = 4096;
    // Keep a malformed or accidentally selected output tree from exhausting the manager heap.
    private static final int MAX_REPAIR_SCAN_FILES = 20000;
    private static final int MAX_REPAIR_SCAN_DEPTH = 12;
    private static final long MAX_REPAIR_SCAN_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_REPAIR_DEX_FILES = 1024;
    private static final int MAX_REPAIR_RECORD_FILES = 256;
    private static final int MAX_INDEXED_METHOD_RECORDS = 100000;
    private static final int MAX_STATUS_FILE_BYTES = 1024 * 1024;
    private static final int MAX_STATUS_SCAN_DEPTH = 8;
    private static final int MAX_STATUS_SCAN_FILES = 10000;

    private UiConfig mConfig = new UiConfig();
    private final List<AppEntry> mAllAppEntries = new ArrayList<>();
    private final AtomicBoolean mStatusRefreshRunning = new AtomicBoolean(false);
    private final AtomicBoolean mStartRunning = new AtomicBoolean(false);
    private final AtomicBoolean mScanRunning = new AtomicBoolean(false);
    private final AtomicBoolean mLogcatRefreshRunning = new AtomicBoolean(false);
    private final Object mLogcatLock = new Object();
    private final ArrayList<String> mLogcatLines = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Object mUiRefreshLock = new Object();
    private volatile Runnable mComposeRefreshCallback;
    private final Runnable mComposeRefreshRunnable = () -> {
        synchronized (mUiRefreshLock) {
            mComposeRefreshPosted = false;
        }
        Runnable callback = mComposeRefreshCallback;
        if (callback != null) {
            callback.run();
        }
    };
    private final AtomicBoolean mRepairRunning = new AtomicBoolean(false);
    private volatile boolean mComposeRefreshPosted;
    private volatile boolean mAppsLoaded;
    private volatile boolean mLastAppListIncludeSystem;
    private volatile RepairPlan mCachedRepairPlan;
    private volatile String mLastActionValue = "";
    private volatile String mStatusSummaryValue = "";
    private volatile String mRepairProgressValue = "";
    private volatile String mLogcatValue = "";
    private volatile boolean mLogcatR0dumpOnly = true;
    private volatile String mActiveRunId = "";

    private static final class AppEntry implements Comparable<AppEntry> {
        final String label;
        final String packageName;
        final boolean system;
        final boolean launchable;

        AppEntry(String label, String packageName, boolean system, boolean launchable) {
            this.label = label;
            this.packageName = packageName;
            this.system = system;
            this.launchable = launchable;
        }

        @Override
        public String toString() {
            if (packageName == null || packageName.isEmpty()) {
                return label;
            }
            String badge = system ? "SYS" : "USER";
            String entry = launchable ? "" : " · 无入口";
            return label + "  [" + badge + entry + "]\n" + packageName;
        }

        @Override
        public int compareTo(AppEntry other) {
            if (launchable != other.launchable) {
                return launchable ? -1 : 1;
            }
            if (system != other.system) {
                return system ? 1 : -1;
            }
            return label.compareToIgnoreCase(other.label);
        }
    }

    private static final class RepairStats {
        int seen;
        int applied;
        int skipped;
        int duplicates;
        int dexInputs;
        int rawDataInputs;
        int recordFiles;
        int repairedDex;
        int rebuiltRawDex;
        int duplicateOutputs;
        long bytesWritten;
        File zipFile;
    }

    private static final class DexKey {
        final String locationHash;
        final long dexSize;
        final String checksum;

        DexKey(String locationHash, long dexSize, String checksum) {
            this.locationHash = locationHash != null ? locationHash.toLowerCase() : "";
            this.dexSize = dexSize;
            this.checksum = checksum != null ? checksum.toLowerCase() : "";
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof DexKey)) {
                return false;
            }
            DexKey key = (DexKey) other;
            return dexSize == key.dexSize
                    && locationHash.equals(key.locationHash)
                    && checksum.equals(key.checksum);
        }

        @Override
        public int hashCode() {
            int result = locationHash.hashCode();
            result = 31 * result + Long.hashCode(dexSize);
            result = 31 * result + checksum.hashCode();
            return result;
        }
    }

    private static final class RawDexHeaderSnapshot {
        final byte[] header;
        final int fileSize;
        final int dataOff;

        RawDexHeaderSnapshot(byte[] header, int fileSize, int dataOff) {
            this.header = header;
            this.fileSize = fileSize;
            this.dataOff = dataOff;
        }
    }

    private static final class RawDexRebuildFile {
        final int codeItemOffsetBias;

        RawDexRebuildFile(int codeItemOffsetBias) {
            this.codeItemOffsetBias = codeItemOffsetBias;
        }
    }

    private static final class RepairRecordPointer {
        final File file;
        final long offset;

        RepairRecordPointer(File file, long offset) {
            this.file = file;
            this.offset = offset;
        }
    }

    private static final class RepairRecordIndex {
        final Map<DexKey, List<RepairRecordPointer>> methodRecords = new HashMap<>();
        final Map<DexKey, RawDexHeaderSnapshot> headerSnapshots = new HashMap<>();
        int malformedLines;
        int staleRunLines;
        int indexedRecords;
        boolean truncated;
    }

    private static final class RepairPlan {
        final String packageName;
        final File projectDir;
        final File outputDir;
        final File zipFile;
        final List<File> dexFiles;
        final List<File> recordFiles;

        RepairPlan(String packageName, File projectDir, File outputDir, File zipFile,
                List<File> dexFiles, List<File> recordFiles) {
            this.packageName = packageName;
            this.projectDir = projectDir;
            this.outputDir = outputDir;
            this.zipFile = zipFile;
            this.dexFiles = dexFiles;
            this.recordFiles = recordFiles;
        }
    }

    private static final class RepairScanBudget {
        int files;
        long bytes;
        boolean truncated;
    }

    public static final class UiConfig {
        public String targetPackage = "";
        public String targetProcess = "";
        public String outputRoot = DEFAULT_OUTPUT_ROOT;
        public String delayMs = DEFAULT_DELAY_MS;
        public String classPrefix = "";
        public String maxMethods = "0";
        public String maxRecords = "50000";
        public String maxSeconds = "300";
        public String classWalkMode = "load_all";
        public String classWalkThreads = "1";
        public String processMode = "main_only";
        public String forceBackfillMaxMethods = "200";
        public String forceBackfillClassPrefix = "";
        public boolean dumpConstructors = true;
        public boolean dumpMethods = true;
        public boolean stopAfterComplete = true;
        public boolean globalRuntime = false;
        public boolean showSystemApps = false;
        public boolean artClassLoaderScanEnabled = true;
        public boolean loadedClassTableScanEnabled = true;
        public boolean manifestComponentSeedEnabled = true;
        public boolean forceBackfillEnabled = false;
        public boolean forceBackfillOnlyStatic = true;
        public boolean forceBackfillSkipNative = true;
        public boolean forceBackfillSkipConstructor = true;
        public boolean rawDexDataMirrorEnabled = false;
        public boolean asyncExportEnabled = false;
        public boolean anrProtectionEnabled = false;
        public int strategyMask = DEFAULT_STRATEGY_MASK;

        public UiConfig copy() {
            UiConfig out = new UiConfig();
            out.targetPackage = targetPackage;
            out.targetProcess = targetProcess;
            out.outputRoot = outputRoot;
            out.delayMs = delayMs;
            out.classPrefix = classPrefix;
            out.maxMethods = maxMethods;
            out.maxRecords = maxRecords;
            out.maxSeconds = maxSeconds;
            out.classWalkMode = classWalkMode;
            out.classWalkThreads = classWalkThreads;
            out.processMode = processMode;
            out.forceBackfillMaxMethods = forceBackfillMaxMethods;
            out.forceBackfillClassPrefix = forceBackfillClassPrefix;
            out.dumpConstructors = dumpConstructors;
            out.dumpMethods = dumpMethods;
            out.stopAfterComplete = stopAfterComplete;
            out.globalRuntime = globalRuntime;
            out.showSystemApps = showSystemApps;
            out.artClassLoaderScanEnabled = artClassLoaderScanEnabled;
            out.loadedClassTableScanEnabled = loadedClassTableScanEnabled;
            out.manifestComponentSeedEnabled = manifestComponentSeedEnabled;
            out.forceBackfillEnabled = forceBackfillEnabled;
            out.forceBackfillOnlyStatic = forceBackfillOnlyStatic;
            out.forceBackfillSkipNative = forceBackfillSkipNative;
            out.forceBackfillSkipConstructor = forceBackfillSkipConstructor;
            out.rawDexDataMirrorEnabled = rawDexDataMirrorEnabled;
            out.asyncExportEnabled = asyncExportEnabled;
            out.anrProtectionEnabled = anrProtectionEnabled;
            out.strategyMask = strategyMask;
            return out;
        }
    }

    public static final class UiAppEntry {
        public String label = "";
        public String packageName = "";
        public boolean system;
        public boolean launchable;
    }

    public static final class UiFileEntry {
        public String name = "";
        public String path = "";
        public String size = "";
        public String updated = "";
    }

    public static final class UiStatusInfo {
        public boolean available;
        public boolean readError;
        public boolean stale;
        public String message = "";
        public String filePath = "";
        public String packageName = "";
        public String processName = "";
        public String phaseRaw = "";
        public String phaseLabel = "";
        public String outputDir = "";
        public String startedAt = "";
        public String updatedAt = "";
        public String strategies = "";
        public int pid;
        public long dexFilesWritten;
        public long dexDataFilesWritten;
        public long methodRecordsWritten;
        public long duplicateMethodsSkipped;
        public long nonstandardDexMethodsSkipped;
        public long invalidMethodsSkipped;
        public long forceBackfillAttempts;
        public long forceBackfillSuccess;
        public long forceBackfillFailed;
        public long forceBackfillSkippedByGuard;
        public long forceBackfillInvokedUnchanged;
        public long forceBackfillInvokeExceptions;
        public long forceBackfillChangedSuccess;
        public long classLoaderCandidates;
        public long classLoadersWalked;
        public long classLoaderDexElements;
        public long classLoaderUniqueCookies;
        public long loadedClassTableClasses;
        public long manifestComponentClasses;
        public long manifestSeedDumped;
        public String classLoaders = "";
    }

    public static final class UiSnapshot {
        public UiConfig config = new UiConfig();
        public ArrayList<UiAppEntry> apps = new ArrayList<>();
        public ArrayList<UiFileEntry> dexFiles = new ArrayList<>();
        public ArrayList<UiFileEntry> recordFiles = new ArrayList<>();
        public ArrayList<UiFileEntry> repairedFiles = new ArrayList<>();
        public UiStatusInfo status = new UiStatusInfo();
        public String selectedLabel = "";
        public String selectedPackage = "";
        public String phase = "未配置";
        public String phaseDescription = "";
        public String lastAction = "";
        public String statusSummary = "";
        public String artifactsSummary = "";
        public String repairSummary = "";
        public String repairProgress = "";
        public String scanDir = "";
        public String outputDir = "";
        public String zipPath = "";
        public String statusPath = "";
        public int dexCount;
        public int recordCount;
        public boolean dumpEnabled;
        public boolean statusDumping;
        public boolean statusComplete;
        public boolean statusRefreshRunning;
        public boolean repairRunning;
        public boolean actionRunning;
        public boolean scanRunning;
        public boolean logcatRunning;
        public boolean logcatR0dumpOnly = true;
        public String logcatText = "";
    }

    public enum UiAction {
        SAVE,
        START,
        STOP,
        LAUNCH,
        FORCE_STOP,
        ORIGINAL_PRESET,
        DYNAMIC_DEX_PRESET,
        DEXPROTECTOR_PRESET,
        REFRESH_STATUS,
        REFRESH_LOGCAT,
        CLEAR_LOGCAT,
        SCAN_OUTPUT,
        REPAIR_DEX
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "manager onCreate");
        mLogcatR0dumpOnly = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getBoolean(UI_LOGCAT_R0DUMP_ONLY, true);
        loadConfig();
        loadApps();
        R0DumpComposeUiKt.installR0DumpComposeUi(this);
        handleAutomationIntent(getIntent(), initialAutomationCallerUid());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfig();
        notifyComposeChanged();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAutomationIntent(intent, currentAutomationCallerUid());
    }

    private void handleAutomationIntent(Intent intent, int callerUid) {
        if (intent == null) {
            return;
        }
        String action = text(intent.getStringExtra(EXTRA_R0DUMP_ACTION));
        if (action.isEmpty()) {
            action = text(intent.getAction());
        }
        if (action.isEmpty()) {
            return;
        }
        String normalized = action.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".start")) {
            normalized = AUTOMATION_ACTION_START;
        } else if (normalized.endsWith(".stop")) {
            normalized = AUTOMATION_ACTION_STOP;
        } else if (normalized.endsWith(".scan")) {
            normalized = AUTOMATION_ACTION_SCAN;
        } else if (normalized.endsWith(".repair")) {
            normalized = AUTOMATION_ACTION_REPAIR;
        }
        if (!AUTOMATION_ACTION_START.equals(normalized)
                && !AUTOMATION_ACTION_STOP.equals(normalized)
                && !AUTOMATION_ACTION_SCAN.equals(normalized)
                && !AUTOMATION_ACTION_REPAIR.equals(normalized)) {
            return;
        }
        if (!isTrustedAutomationCaller(callerUid)) {
            Log.w(LOG_TAG, "ignored automation intent from uid=" + callerUid);
            return;
        }

        boolean changed = false;
        String pkg = cleanAutomationExtra(intent.getStringExtra(EXTRA_R0DUMP_TARGET_PACKAGE));
        if (!pkg.isEmpty()) {
            mConfig.globalRuntime = false;
            mConfig.targetPackage = pkg;
            changed = true;
        }
        String outputRoot = cleanAutomationExtra(intent.getStringExtra(EXTRA_R0DUMP_OUTPUT_ROOT));
        if (!outputRoot.isEmpty()) {
            mConfig.outputRoot = outputRoot;
            changed = true;
        }
        if (intent.hasExtra(EXTRA_R0DUMP_TARGET_PROCESS)) {
            String targetProcess = cleanAutomationExtra(
                    intent.getStringExtra(EXTRA_R0DUMP_TARGET_PROCESS));
            if (!targetProcess.equals(text(intent.getStringExtra(EXTRA_R0DUMP_TARGET_PROCESS)))) {
                Log.w(LOG_TAG, "ignored suspicious automation target process extra");
            }
            mConfig.targetProcess = targetProcess;
            changed = true;
        }
        if (intent.hasExtra(EXTRA_R0DUMP_RAW_MIRROR)) {
            mConfig.rawDexDataMirrorEnabled = intent.getBooleanExtra(EXTRA_R0DUMP_RAW_MIRROR, false);
            changed = true;
        }
        if (changed) {
            saveConfigPreservingEnabled(false);
        }

        Log.i(LOG_TAG, "manager automation requested action=" + normalized
                + " package=" + packageName()
                + " outputRoot=" + nonEmpty(mConfig.outputRoot, DEFAULT_OUTPUT_ROOT)
                + " process=" + text(mConfig.targetProcess)
                + " rawMirror=" + mConfig.rawDexDataMirrorEnabled);
        boolean finishOnComplete = intent.getBooleanExtra(EXTRA_R0DUMP_FINISH, false);
        if (AUTOMATION_ACTION_START.equals(normalized)) {
            startDumpAndLaunchInBackground(true, finishOnComplete);
        } else if (AUTOMATION_ACTION_STOP.equals(normalized)) {
            stopDump();
            Log.i(LOG_TAG, "manager automation stop done package=" + packageName());
            if (finishOnComplete) {
                mMainHandler.postDelayed(this::finish, 250);
            }
        } else if (AUTOMATION_ACTION_SCAN.equals(normalized)) {
            scanOutputAndFillLatestInBackground();
            if (finishOnComplete) {
                mMainHandler.postDelayed(this::finish, 500);
            }
        } else {
            startRepairInBackground(true, finishOnComplete);
        }
    }

    private int initialAutomationCallerUid() {
        try {
            return getLaunchedFromUid();
        } catch (Throwable failure) {
            Log.w(LOG_TAG, "unable to identify initial automation caller", failure);
            return android.os.Process.INVALID_UID;
        }
    }

    private int currentAutomationCallerUid() {
        try {
            return getCurrentCaller().getUid();
        } catch (Throwable failure) {
            Log.w(LOG_TAG, "unable to identify new-intent automation caller", failure);
            return android.os.Process.INVALID_UID;
        }
    }

    private boolean isTrustedAutomationCaller(int callerUid) {
        return callerUid == android.os.Process.myUid()
                || callerUid == android.os.Process.ROOT_UID
                || callerUid == android.os.Process.SYSTEM_UID
                || callerUid == android.os.Process.SHELL_UID;
    }

    public void setComposeRefreshCallback(Runnable callback) {
        mComposeRefreshCallback = callback;
    }

    private void notifyComposeChanged() {
        if (mComposeRefreshCallback == null) {
            return;
        }
        synchronized (mUiRefreshLock) {
            if (mComposeRefreshPosted) {
                return;
            }
            mComposeRefreshPosted = true;
        }
        mMainHandler.postDelayed(mComposeRefreshRunnable, UI_REFRESH_DEBOUNCE_MS);
    }

    private UiConfig buildUiConfig() {
        return copyConfig(mConfig);
    }

    private void applyUiConfig(UiConfig config) {
        mConfig = copyConfig(config);
    }

    private UiConfig copyConfig(UiConfig in) {
        UiConfig out = in != null ? in.copy() : new UiConfig();
        out.targetPackage = text(out.targetPackage);
        out.targetProcess = text(out.targetProcess);
        out.outputRoot = nonEmpty(out.outputRoot, DEFAULT_OUTPUT_ROOT);
        out.delayMs = nonEmpty(out.delayMs, DEFAULT_DELAY_MS);
        out.classPrefix = text(out.classPrefix);
        out.maxMethods = nonEmpty(out.maxMethods, "0");
        out.maxRecords = nonEmpty(out.maxRecords, "50000");
        out.maxSeconds = nonEmpty(out.maxSeconds, "300");
        out.classWalkMode = nonEmpty(out.classWalkMode, "load_all");
        // Class walking intentionally uses one bounded worker.
        out.classWalkThreads = "1";
        out.asyncExportEnabled = false;
        out.forceBackfillOnlyStatic = true;
        out.forceBackfillSkipNative = true;
        out.forceBackfillSkipConstructor = true;
        out.processMode = nonEmpty(out.processMode, "main_only");
        out.forceBackfillMaxMethods = nonEmpty(out.forceBackfillMaxMethods, "200");
        out.forceBackfillClassPrefix = text(out.forceBackfillClassPrefix);
        out.strategyMask = normalizeStrategyMask(out.strategyMask);
        return out;
    }

    private int normalizeStrategyMask(int mask) {
        if ((mask & (STRATEGY_FORCE_BACKFILL
                | STRATEGY_FORCE_BACKFILL_BEFORE
                | STRATEGY_FORCE_BACKFILL_AFTER)) != 0) {
            mask |= STRATEGY_FORCE_BACKFILL
                    | STRATEGY_FORCE_BACKFILL_BEFORE
                    | STRATEGY_FORCE_BACKFILL_AFTER;
        }
        return mask;
    }

    private String nonEmpty(String value, String fallback) {
        String clean = text(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private UiAppEntry toUiAppEntry(AppEntry entry) {
        UiAppEntry out = new UiAppEntry();
        out.label = entry.label;
        out.packageName = entry.packageName;
        out.system = entry.system;
        out.launchable = entry.launchable;
        return out;
    }

    private UiFileEntry toUiFileEntry(File file) {
        UiFileEntry out = new UiFileEntry();
        out.name = file.getName();
        out.path = file.getAbsolutePath();
        out.size = formatFileSize(file.length());
        out.updated = DateFormat.getDateTimeInstance().format(new Date(file.lastModified()));
        return out;
    }

    public UiSnapshot getComposeSnapshot(boolean scanFiles, String appQuery, boolean includeSystem) {
        ensureInstalledAppEntries(includeSystem);
        UiSnapshot snapshot = new UiSnapshot();
        snapshot.config = buildUiConfig();
        for (AppEntry entry : filteredAppEntries(appQuery)) {
            if (snapshot.apps.size() >= 80) break;
            snapshot.apps.add(toUiAppEntry(entry));
        }
        RepairPlan plan = repairPlanForSnapshot(scanFiles);
        snapshot.selectedPackage = plan.packageName;
        AppEntry selected = findAppEntry(plan.packageName);
        snapshot.selectedLabel = selected != null ? selected.label : plan.packageName;
        boolean enabled = Settings.Global.getInt(getContentResolver(), R0DUMP_SETTING_ENABLED, 0) == 1;
        boolean savedTarget = plan.packageName.equals(Settings.Global.getString(
                getContentResolver(), R0DUMP_SETTING_TARGET_PACKAGE));
        File status = findLatestStatusFile(false);
        UiStatusInfo statusInfo = buildUiStatusInfo(status);
        snapshot.dumpEnabled = enabled;
        snapshot.phase = plan.packageName.isEmpty() ? "未配置"
                : (!statusInfo.phaseRaw.isEmpty()
                        ? friendlyPhase(statusInfo.phaseRaw)
                        : selectedPhaseLabel(enabled, savedTarget));
        snapshot.phaseDescription = plan.packageName.isEmpty()
                ? "选择目标 App 后配置会即时保存。"
                : (isStatusTerminalPhase(statusInfo.phaseRaw)
                ? "当前运行批次已结束，可检查 DEX 验证结果或执行修复。"
                : (enabled ? "R0DUMP 已启用。等待延迟、触发点和 class-walk 完成。"
                : (savedTarget ? "配置已即时保存，可开始 dump 或继续调整策略。"
                : "目标已选择，配置会即时保存，点击开始dump即可写入启用状态。")));
        snapshot.lastAction = mLastActionValue.isEmpty() ? getString(R.string.log_ready) : mLastActionValue;
        snapshot.statusSummary = mStatusSummaryValue.isEmpty()
                ? getString(R.string.status_not_refreshed) : mStatusSummaryValue;
        snapshot.artifactsSummary = buildArtifactsSummaryText(plan);
        snapshot.repairSummary = buildRepairSummaryText(plan);
        snapshot.repairProgress = mRepairProgressValue.isEmpty()
                ? "修复尚未开始。扫描当前 App 后，R0DUMP 会按 method records 匹配 DEX/raw dexdata 并打包输出。"
                : mRepairProgressValue;
        snapshot.scanDir = plan.projectDir.getAbsolutePath();
        snapshot.outputDir = plan.outputDir.getAbsolutePath();
        snapshot.zipPath = plan.zipFile.getAbsolutePath();
        snapshot.status = statusInfo;
        snapshot.statusPath = snapshot.status.filePath;
        snapshot.statusDumping = isStatusActivePhase(snapshot.status.phaseRaw);
        snapshot.statusComplete = isStatusTerminalPhase(snapshot.status.phaseRaw);
        snapshot.statusRefreshRunning = mStatusRefreshRunning.get();
        snapshot.dexCount = plan.dexFiles.size();
        snapshot.recordCount = plan.recordFiles.size();
        snapshot.repairRunning = mRepairRunning.get();
        snapshot.actionRunning = mStartRunning.get();
        snapshot.scanRunning = mScanRunning.get();
        snapshot.logcatRunning = mLogcatRefreshRunning.get();
        snapshot.logcatR0dumpOnly = mLogcatR0dumpOnly;
        snapshot.logcatText = mLogcatValue;
        for (File dex : plan.dexFiles) snapshot.dexFiles.add(toUiFileEntry(dex));
        for (File record : plan.recordFiles) snapshot.recordFiles.add(toUiFileEntry(record));
        if (scanFiles && plan.outputDir.exists()) {
            List<File> repaired = new ArrayList<>();
            collectFiles(plan.outputDir, repaired);
            Collections.sort(repaired);
            for (File file : repaired) snapshot.repairedFiles.add(toUiFileEntry(file));
        }
        return snapshot;
    }

    public UiSnapshot updateConfigFromCompose(UiConfig config) {
        applyUiConfig(config);
        saveConfigPreservingEnabled(false);
        updateRepairSummary(buildRepairPlan(false));
        return getComposeSnapshot(false, "", mConfig.showSystemApps);
    }

    public UiSnapshot runComposeAction(UiConfig config, UiAction action) {
        applyUiConfig(config);
        UiAction selected = action != null ? action : UiAction.SCAN_OUTPUT;
        switch (selected) {
            case SAVE:
                saveConfigPreservingEnabled();
                break;
            case START:
                startDumpAndLaunchInBackground();
                break;
            case STOP:
                stopDump();
                break;
            case LAUNCH:
                launchTarget();
                break;
            case FORCE_STOP:
                forceStopTarget();
                break;
            case ORIGINAL_PRESET:
                applyOriginalPreset();
                saveConfigPreservingEnabled();
                break;
            case DYNAMIC_DEX_PRESET:
                applyDynamicDexPreset();
                saveConfigPreservingEnabled();
                break;
            case DEXPROTECTOR_PRESET:
                applyDexProtectorPreset();
                saveConfigPreservingEnabled();
                break;
            case REFRESH_STATUS:
                refreshStatusInBackground(false);
                break;
            case REFRESH_LOGCAT:
                refreshLogcatInBackground(false);
                break;
            case CLEAR_LOGCAT:
                clearLogcatPreview();
                break;
            case SCAN_OUTPUT:
                scanOutputAndFillLatestInBackground();
                break;
            case REPAIR_DEX:
                repairDexFromUi();
                break;
        }
        return getComposeSnapshot(false, "", mConfig.showSystemApps);
    }

    public UiSnapshot selectAppFromCompose(String packageName, boolean includeSystem, String query) {
        mConfig.showSystemApps = includeSystem;
        mConfig.targetPackage = text(packageName);
        saveConfigPreservingEnabled(false);
        updateRepairSummary(buildRepairPlan(false));
        return getComposeSnapshot(false, query, includeSystem);
    }

    public UiSnapshot refreshAppsFromCompose(boolean includeSystem) {
        mConfig.showSystemApps = includeSystem;
        refreshInstalledAppEntries(includeSystem);
        return getComposeSnapshot(false, "", includeSystem);
    }

    public String currentLanguageTagFromCompose() {
        String saved = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(UI_LANGUAGE_TAG, LANGUAGE_SYSTEM);
        if (saved != null && !saved.isEmpty()) {
            return saved;
        }
        return currentSystemLanguageTag();
    }

    public String cycleLanguageFromCompose() {
        return cycleLanguage();
    }

    private String currentSystemLanguageTag() {
        LocaleList locales = getResources().getConfiguration().getLocales();
        if (locales == null || locales.isEmpty()) {
            return LANGUAGE_SYSTEM;
        }
        return locales.get(0).toLanguageTag();
    }

    private String cycleLanguage() {
        String current = currentLanguageTagFromCompose();
        String next = current != null && current.toLowerCase(Locale.ROOT).startsWith("en")
                ? LANGUAGE_CHINESE_SIMPLIFIED : LANGUAGE_ENGLISH;
        getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .edit()
                .putString(UI_LANGUAGE_TAG, next)
                .apply();
        return next;
    }

    private String selectedPhaseLabel(boolean enabled, boolean savedTarget) {
        if (enabled) {
            return getString(R.string.phase_dumping);
        }
        return getString(savedTarget ? R.string.phase_saved : R.string.phase_pending_save);
    }


    private void loadApps() {
        refreshInstalledAppEntries(mConfig.showSystemApps);
    }

    private void ensureInstalledAppEntries(boolean includeSystem) {
        if (!mAppsLoaded || mLastAppListIncludeSystem != includeSystem) {
            refreshInstalledAppEntries(includeSystem);
        }
    }

    private void refreshInstalledAppEntries(boolean includeSystem) {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        Set<String> launchablePackages = new HashSet<>();
        for (ResolveInfo info : infos) {
            if (info.activityInfo != null && info.activityInfo.packageName != null) {
                launchablePackages.add(info.activityInfo.packageName);
            }
        }
        mAllAppEntries.clear();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            String pkg = app.packageName;
            String label = String.valueOf(app.loadLabel(pm));
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (system && !includeSystem) {
                continue;
            }
            boolean launchable = launchablePackages.contains(pkg);
            mAllAppEntries.add(new AppEntry(label, pkg, system, launchable));
        }
        Collections.sort(mAllAppEntries);
        mLastAppListIncludeSystem = includeSystem;
        mAppsLoaded = true;
    }

    private List<AppEntry> filteredAppEntries(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        String currentPkg = packageName();
        List<AppEntry> entries = new ArrayList<>();
        AppEntry selected = null;
        for (AppEntry entry : mAllAppEntries) {
            boolean matched = q.isEmpty()
                    || entry.label.toLowerCase().contains(q)
                    || entry.packageName.toLowerCase().contains(q);
            if (currentPkg.equals(entry.packageName)) {
                selected = entry;
            }
            if (matched) {
                entries.add(entry);
            }
        }
        if (selected != null && q.isEmpty()) {
            entries.remove(selected);
            entries.add(0, selected);
        }
        if (entries.isEmpty()) {
            entries.add(new AppEntry("未选择应用", "", false, false));
        }
        return entries;
    }

    private AppEntry findAppEntry(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        for (AppEntry entry : mAllAppEntries) {
            if (packageName.equals(entry.packageName)) {
                return entry;
            }
        }
        return null;
    }


    private void loadConfig() {
        UiConfig loaded = new UiConfig();
        loaded.globalRuntime = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_GLOBAL_RUNTIME_ENABLED, 0) == 1;
        mActiveRunId = text(Settings.Global.getString(
                getContentResolver(), R0DUMP_SETTING_RUN_ID));
        String targetPackage = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_TARGET_PACKAGE);
        loaded.targetPackage = loaded.globalRuntime && "*".equals(targetPackage)
                ? "" : text(targetPackage);
        loaded.outputRoot = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_OUTPUT_ROOT);
        loaded.delayMs = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_DELAY_MS);
        loaded.targetProcess = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_TARGET_PROCESS);
        loaded.classPrefix = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_CLASS_PREFIX);
        loaded.maxMethods = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_MAX_METHODS);
        loaded.maxRecords = normalizeLimitDefault(
                Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_MAX_RECORDS), "50000");
        loaded.maxSeconds = normalizeLimitDefault(
                Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_MAX_SECONDS), "300");
        loaded.classWalkMode = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_CLASS_WALK_MODE);
        loaded.classWalkThreads = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_CLASS_WALK_THREADS);
        loaded.processMode = Settings.Global.getString(getContentResolver(), R0DUMP_SETTING_PROCESS_MODE);
        loaded.forceBackfillMaxMethods = Settings.Global.getString(
                getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_MAX_METHODS);
        loaded.forceBackfillClassPrefix = Settings.Global.getString(
                getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_CLASS_PREFIX);
        loaded.dumpConstructors = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_DUMP_CONSTRUCTORS, 1) == 1;
        loaded.dumpMethods = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_DUMP_METHODS, 1) == 1;
        loaded.stopAfterComplete = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_STOP_AFTER_COMPLETE, 1) == 1;
        loaded.artClassLoaderScanEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_ART_CLASSLOADER_SCAN_ENABLED, 1) == 1;
        loaded.loadedClassTableScanEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_LOADED_CLASS_TABLE_SCAN_ENABLED, 1) == 1;
        loaded.manifestComponentSeedEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_MANIFEST_COMPONENT_SEED_ENABLED, 1) == 1;
        loaded.forceBackfillEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_ENABLED, 0) == 1;
        loaded.forceBackfillOnlyStatic = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_ONLY_STATIC, 1) == 1;
        loaded.forceBackfillSkipNative = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_SKIP_NATIVE, 1) == 1;
        loaded.forceBackfillSkipConstructor = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_SKIP_CONSTRUCTOR, 1) == 1;
        loaded.rawDexDataMirrorEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_RAW_DEXDATA_MIRROR_ENABLED, 0) == 1;
        loaded.asyncExportEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_ASYNC_EXPORT_ENABLED, 0) == 1;
        loaded.anrProtectionEnabled = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_ANR_PROTECTION_ENABLED, 0) == 1;
        loaded.strategyMask = Settings.Global.getInt(
                getContentResolver(), R0DUMP_SETTING_STRATEGY_MASK, DEFAULT_STRATEGY_MASK);
        // Older builds promoted DEFINE_CLASS into the default mask.  Migrate only that exact
        // legacy default so an intentionally customized mask is left untouched.
        android.content.SharedPreferences uiPrefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        if (!uiPrefs.getBoolean(UI_STARTUP_SAFE_DEFAULT_MIGRATED, false)) {
            if (loaded.strategyMask == LEGACY_DEFAULT_STRATEGY_MASK) {
                loaded.strategyMask = DEFAULT_STRATEGY_MASK;
                Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_STRATEGY_MASK,
                        loaded.strategyMask);
                mLastActionValue = "已迁移为启动安全默认：DEFINE_CLASS 改为手动开启。";
            }
            uiPrefs.edit().putBoolean(UI_STARTUP_SAFE_DEFAULT_MIGRATED, true).apply();
        }
        // The previous build used a 60-second class-walk delay.  Migrate only
        // that untouched legacy value so an explicitly chosen delay survives.
        if (!uiPrefs.getBoolean(UI_PERFORMANCE_DEFAULTS_MIGRATED, false)) {
            if (DEFAULT_DELAY_MS.equals(text(loaded.delayMs))
                    || "60000".equals(text(loaded.delayMs))) {
                loaded.delayMs = DEFAULT_DELAY_MS;
                Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_DELAY_MS,
                        DEFAULT_DELAY_MS);
            }
            uiPrefs.edit().putBoolean(UI_PERFORMANCE_DEFAULTS_MIGRATED, true).apply();
        }
        // Preserve the marker used by pre-release builds; it is no longer used to enable the
        // performance-sensitive strategy.
        if (!uiPrefs.getBoolean(UI_DEFINE_CLASS_DEFAULT_MIGRATED, false)) {
            uiPrefs.edit().putBoolean(UI_DEFINE_CLASS_DEFAULT_MIGRATED, true).apply();
        }
        loaded.showSystemApps = false;
        mConfig = copyConfig(loaded);
        updateRepairSummary(buildRepairPlan(false));
    }

    private void saveConfigPreservingEnabled() {
        saveConfigPreservingEnabled(true);
    }

    private void saveConfigPreservingEnabled(boolean verbose) {
        saveConfig(Settings.Global.getInt(getContentResolver(), R0DUMP_SETTING_ENABLED, 0) == 1, verbose);
    }

    private void saveConfig(boolean enabled) {
        saveConfig(enabled, true);
    }

    private void saveConfig(boolean enabled, boolean verbose) {
        saveConfig(enabled, verbose, mActiveRunId);
    }

    private void saveConfig(boolean enabled, boolean verbose, String runId) {
        mConfig = copyConfig(mConfig);
        boolean globalRuntimeEnabled = mConfig.globalRuntime;
        String pkg = globalRuntimeEnabled ? "*" : packageName();
        String output = nonEmpty(mConfig.outputRoot, DEFAULT_OUTPUT_ROOT);
        String delay = nonEmpty(mConfig.delayMs, DEFAULT_DELAY_MS);
        String targetProcess = text(mConfig.targetProcess);
        String classPrefix = text(mConfig.classPrefix);
        String maxMethods = nonEmpty(mConfig.maxMethods, "0");
        String maxRecords = nonEmpty(mConfig.maxRecords, "50000");
        String maxSeconds = nonEmpty(mConfig.maxSeconds, "300");
        String classWalkMode = nonEmpty(mConfig.classWalkMode, "load_all");
        String classWalkThreads = nonEmpty(mConfig.classWalkThreads, "1");
        String processMode = nonEmpty(mConfig.processMode, "main_only");
        String forceMaxMethods = nonEmpty(mConfig.forceBackfillMaxMethods, "200");
        String forceClassPrefix = text(mConfig.forceBackfillClassPrefix);
        int strategyMask = strategyMaskFromUi();
        boolean forceBackfillEnabled = mConfig.forceBackfillEnabled;
        if (forceBackfillEnabled
                && (strategyMask & STRATEGY_FORCE_BACKFILL) != 0
                && classPrefix.isEmpty()
                && forceClassPrefix.isEmpty()) {
            forceBackfillEnabled = false;
            mConfig.forceBackfillEnabled = false;
            if (verbose) {
                log(getString(R.string.log_force_backfill_prefix_required));
            } else {
                mLastActionValue = getString(R.string.log_force_backfill_prefix_required);
            }
        }
        // Disable first so a newly starting process never observes a partially written config.
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ENABLED, 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_GLOBAL_RUNTIME_ENABLED,
                globalRuntimeEnabled ? 1 : 0);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_TARGET_PACKAGE, pkg);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_OUTPUT_ROOT, output);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_DELAY_MS, delay);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_TARGET_PROCESS, targetProcess);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_CLASS_PREFIX, classPrefix);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_MAX_METHODS, maxMethods);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_MAX_RECORDS, maxRecords);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_MAX_SECONDS, maxSeconds);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_CLASS_WALK_MODE, classWalkMode);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_CLASS_WALK_THREADS,
                classWalkThreads);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_PROCESS_MODE, processMode);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_EXIT_OBSERVER_ENABLED, 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ART_CLASSLOADER_SCAN_ENABLED,
                mConfig.artClassLoaderScanEnabled ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_LOADED_CLASS_TABLE_SCAN_ENABLED,
                mConfig.loadedClassTableScanEnabled ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_MANIFEST_COMPONENT_SEED_ENABLED,
                mConfig.manifestComponentSeedEnabled ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_DUMP_CONSTRUCTORS,
                mConfig.dumpConstructors ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_DUMP_METHODS,
                mConfig.dumpMethods ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_STOP_AFTER_COMPLETE,
                mConfig.stopAfterComplete ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_ENABLED,
                forceBackfillEnabled ? 1 : 0);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_MAX_METHODS,
                forceMaxMethods);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_ONLY_STATIC,
                mConfig.forceBackfillOnlyStatic ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_SKIP_NATIVE,
                mConfig.forceBackfillSkipNative ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_SKIP_CONSTRUCTOR,
                mConfig.forceBackfillSkipConstructor ? 1 : 0);
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_FORCE_BACKFILL_CLASS_PREFIX,
                forceClassPrefix);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_RAW_DEXDATA_MIRROR_ENABLED,
                mConfig.rawDexDataMirrorEnabled ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ASYNC_EXPORT_ENABLED,
                mConfig.asyncExportEnabled ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ANR_PROTECTION_ENABLED,
                mConfig.anrProtectionEnabled ? 1 : 0);
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_STRATEGY_MASK, strategyMask);
        String committedRunId = nonEmpty(runId, "pending");
        Settings.Global.putString(getContentResolver(), R0DUMP_SETTING_RUN_ID, committedRunId);
        mActiveRunId = committedRunId;
        // This is the transaction commit marker consumed by ActivityThread.
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ENABLED, enabled ? 1 : 0);
        updateRepairSummary(buildRepairPlan(false));
        if (!verbose) {
            return;
        }
        if (enabled && (strategyMask & (STRATEGY_APP_CREATE | STRATEGY_ACTIVITY_CREATE)) == 0) {
            log(getString(R.string.log_warn_no_arm_trigger));
        }
        logStrategyWarnings(strategyMask);
        if (enabled && globalRuntimeEnabled) {
            log(getString(R.string.log_global_runtime_enabled));
        }
        log(getString(R.string.log_config_saved, String.valueOf(enabled), pkg, targetProcess, output,
                Integer.toHexString(strategyMask)));
        if (!enabled) {
            log(getString(R.string.log_operation_complete));
        }
    }

    private void logStrategyWarnings(int strategyMask) {
        if ((strategyMask & STRATEGY_IN_MEMORY_DEX) != 0) {
            log(getString(R.string.log_advice_in_memory_dex));
        }
        if ((strategyMask & STRATEGY_REGISTER_DEX) != 0) {
            log(getString(R.string.log_warn_register_dex));
        }
        if ((strategyMask & STRATEGY_DEFINE_CLASS) != 0) {
            log(getString(R.string.log_warn_define_class));
        }
        if ((strategyMask & STRATEGY_LOAD_CLASS) != 0) {
            log(getString(R.string.log_warn_load_class));
        }
        if ((strategyMask & STRATEGY_RESOLVE_METHOD) != 0) {
            log(getString(R.string.log_warn_resolve_method));
        }
        if ((strategyMask & STRATEGY_FORCE_BACKFILL) != 0) {
            log(getString(R.string.log_warn_force_backfill));
        }
        if ((strategyMask & (STRATEGY_OPEN_COMMON | STRATEGY_OPEN_DEX_FILES_FROM_OAT
                | STRATEGY_VDEX_OPEN_ALL_DEX_FILES | STRATEGY_OAT_DEX_FILE_OPEN)) != 0) {
            log(getString(R.string.log_warn_dex_open_points));
        }
        if ((strategyMask & (STRATEGY_INTERPRETER_EXECUTE | STRATEGY_JIT_METHOD_ENTERED
                | STRATEGY_JIT_COMPILE | STRATEGY_REFLECT_METHOD_INVOKE
                | STRATEGY_INSTRUMENT_METHOD_ENTER | STRATEGY_INSTRUMENT_METHOD_EXIT)) != 0) {
            log(getString(R.string.log_warn_hot_execution_points));
        }
        if ((strategyMask & (STRATEGY_JAVA_CLASS_LOADER_ROUTE | STRATEGY_JAVA_DEXFILE_ROUTE)) != 0) {
            log(getString(R.string.log_warn_java_route_points));
        }
        if ((strategyMask & STRATEGY_RESOLVE_METHOD) != 0
                && (strategyMask & STRATEGY_REAL_INVOKE) != 0) {
            log(getString(R.string.log_warn_resolve_real_invoke));
        }
        int sensitiveCount = 0;
        if ((strategyMask & STRATEGY_REGISTER_DEX) != 0) {
            sensitiveCount++;
        }
        if ((strategyMask & STRATEGY_DEFINE_CLASS) != 0) {
            sensitiveCount++;
        }
        if ((strategyMask & STRATEGY_LOAD_CLASS) != 0) {
            sensitiveCount++;
        }
        if ((strategyMask & STRATEGY_RESOLVE_METHOD) != 0) {
            sensitiveCount++;
        }
        if ((strategyMask & STRATEGY_FORCE_BACKFILL) != 0) {
            sensitiveCount++;
        }
        if ((strategyMask & (STRATEGY_OPEN_COMMON | STRATEGY_OPEN_DEX_FILES_FROM_OAT
                | STRATEGY_VDEX_OPEN_ALL_DEX_FILES | STRATEGY_OAT_DEX_FILE_OPEN
                | STRATEGY_VERIFY_CLASS | STRATEGY_CLASS_INIT_BEFORE | STRATEGY_CLASS_INIT_AFTER
                | STRATEGY_INTERPRETER_EXECUTE | STRATEGY_JIT_METHOD_ENTERED
                | STRATEGY_JIT_COMPILE | STRATEGY_REFLECT_METHOD_INVOKE
                | STRATEGY_INSTRUMENT_METHOD_ENTER | STRATEGY_INSTRUMENT_METHOD_EXIT
                | STRATEGY_JAVA_CLASS_LOADER_ROUTE | STRATEGY_JAVA_DEXFILE_ROUTE
                | STRATEGY_DEFINE_CLASS_NATIVE | STRATEGY_OAT_REGISTER
                | STRATEGY_IMAGE_SPACE_DEX)) != 0) {
            sensitiveCount++;
        }
        if (sensitiveCount > 1) {
            log(getString(R.string.log_warn_multiple_sensitive));
        }
    }

    private int strategyMaskFromUi() {
        return normalizeStrategyMask(mConfig.strategyMask);
    }

    private void setStrategyMask(int mask) {
        mConfig.strategyMask = normalizeStrategyMask(mask);
    }

    private void applyOriginalPreset() {
        setStrategyMask(DEFAULT_STRATEGY_MASK);
        mConfig.delayMs = DEFAULT_DELAY_MS;
        mConfig.classWalkMode = "load_all";
        mConfig.classWalkThreads = "1";
        mConfig.processMode = "main_only";
        mConfig.maxMethods = "0";
        mConfig.maxRecords = "50000";
        mConfig.maxSeconds = "300";
        mConfig.dumpMethods = true;
        mConfig.dumpConstructors = true;
        mConfig.stopAfterComplete = true;
        mConfig.artClassLoaderScanEnabled = true;
        mConfig.loadedClassTableScanEnabled = true;
        mConfig.manifestComponentSeedEnabled = true;
        mConfig.forceBackfillEnabled = false;
        mConfig.rawDexDataMirrorEnabled = false;
        mConfig.asyncExportEnabled = false;
        mConfig.anrProtectionEnabled = false;
        mConfig.globalRuntime = false;
        log(getString(R.string.log_preset_original));
    }

    private void applyDynamicDexPreset() {
        setStrategyMask(DEFAULT_STRATEGY_MASK | STRATEGY_IN_MEMORY_DEX | STRATEGY_DEX_LOAD
                | STRATEGY_DEFINE_CLASS);
        mConfig.delayMs = DEFAULT_DELAY_MS;
        mConfig.classWalkMode = "load_all";
        mConfig.classWalkThreads = "1";
        mConfig.processMode = "main_only";
        mConfig.dumpMethods = true;
        mConfig.dumpConstructors = true;
        mConfig.stopAfterComplete = false;
        mConfig.artClassLoaderScanEnabled = true;
        mConfig.loadedClassTableScanEnabled = true;
        mConfig.manifestComponentSeedEnabled = true;
        mConfig.forceBackfillEnabled = false;
        mConfig.rawDexDataMirrorEnabled = true;
        mConfig.asyncExportEnabled = false;
        mConfig.anrProtectionEnabled = false;
        log(getString(R.string.log_preset_dynamic_dex));
    }

    private void applyDexProtectorPreset() {
        setStrategyMask(STRATEGY_APP_CREATE | STRATEGY_ACTIVITY_CREATE
                | STRATEGY_IN_MEMORY_DEX | STRATEGY_DEFINE_CLASS);
        mConfig.delayMs = "5000";
        mConfig.classWalkMode = "loaded_only";
        mConfig.classWalkThreads = "1";
        mConfig.processMode = "main_only";
        mConfig.maxMethods = "0";
        mConfig.maxRecords = "50000";
        mConfig.maxSeconds = "300";
        mConfig.dumpMethods = true;
        mConfig.dumpConstructors = true;
        mConfig.stopAfterComplete = true;
        mConfig.artClassLoaderScanEnabled = true;
        mConfig.loadedClassTableScanEnabled = false;
        mConfig.manifestComponentSeedEnabled = false;
        mConfig.forceBackfillEnabled = false;
        mConfig.forceBackfillMaxMethods = "200";
        mConfig.forceBackfillOnlyStatic = true;
        mConfig.forceBackfillSkipNative = true;
        mConfig.forceBackfillSkipConstructor = true;
        mConfig.rawDexDataMirrorEnabled = true;
        mConfig.asyncExportEnabled = false;
        mConfig.anrProtectionEnabled = true;
        mConfig.globalRuntime = false;
        log(getString(R.string.log_preset_dexprotector));
    }

    private void startDumpAndLaunchInBackground() {
        startDumpAndLaunchInBackground(false, false);
    }

    private void startDumpAndLaunchInBackground(boolean automation, boolean finishOnComplete) {
        UiConfig startConfig = copyConfig(mConfig);
        String pkg = startConfig.globalRuntime ? "" : text(startConfig.targetPackage);
        if ("*".equals(pkg)) {
            pkg = "";
        }
        if (pkg.isEmpty() && !startConfig.globalRuntime) {
            log(getString(R.string.log_target_package_empty));
            if (automation) {
                Log.e(LOG_TAG, "manager automation start failed: empty target package");
            }
            if (finishOnComplete) {
                mMainHandler.postDelayed(this::finish, 250);
            }
            return;
        }
        if (!mStartRunning.compareAndSet(false, true)) {
            log(getString(R.string.log_action_already_running));
            if (automation) {
                Log.i(LOG_TAG, "manager automation start ignored: already running");
            }
            if (finishOnComplete) {
                mMainHandler.postDelayed(this::finish, 250);
            }
            return;
        }
        mLastActionValue = getString(R.string.log_dump_starting);
        notifyComposeChanged();
        final String launchPkg = pkg;
        new Thread(() -> {
            boolean launchPosted = false;
            try {
                applyUiConfig(startConfig);
                prepareTargetOutputDirectory(launchPkg);
                String runId = System.currentTimeMillis() + "-"
                        + UUID.randomUUID().toString().substring(0, 8);
                mActiveRunId = runId;
                mCachedRepairPlan = null;
                // onResume can reload the previous Settings snapshot while this worker starts.
                // Commit the generated run ID explicitly so it cannot regress to "pending".
                saveConfig(true, true, runId);
                if (launchPkg.isEmpty()) {
                    log(getString(R.string.log_global_runtime_saved_no_launch));
                    log(getString(R.string.log_operation_complete));
                    if (automation) {
                        Log.i(LOG_TAG, "manager automation start done package=* outputRoot="
                                + nonEmpty(startConfig.outputRoot, DEFAULT_OUTPUT_ROOT)
                                + " launch=false");
                    }
                    if (finishOnComplete) {
                        mMainHandler.postDelayed(this::finish, 250);
                    }
                    return;
                }
                forceStopPackage(launchPkg);
                launchPosted = true;
                mMainHandler.post(() -> {
                    try {
                        boolean launched = launchPackage(launchPkg);
                        log(getString(R.string.log_operation_complete));
                        if (automation) {
                            Log.i(LOG_TAG, "manager automation start done package=" + launchPkg
                                    + " outputRoot="
                                    + nonEmpty(startConfig.outputRoot, DEFAULT_OUTPUT_ROOT)
                                    + " launch=" + launched);
                        }
                        if (finishOnComplete) {
                            finishAfterAutomationLaunch(launchPkg);
                        }
                    } finally {
                        mStartRunning.set(false);
                        notifyComposeChanged();
                    }
                });
            } catch (Throwable t) {
                log("开始 dump 失败: " + t);
                if (automation) {
                    Log.e(LOG_TAG, "manager automation start failed", t);
                }
                if (finishOnComplete) {
                    mMainHandler.postDelayed(this::finish, 250);
                }
            } finally {
                if (!launchPosted) {
                    mStartRunning.set(false);
                    notifyComposeChanged();
                }
            }
        }, "r0dump-start").start();
    }

    private void stopDump() {
        Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ENABLED, 0);
        log(getString(R.string.log_dump_stopped));
    }

    private void finishAfterAutomationLaunch(String packageName) {
        mMainHandler.postDelayed(() -> {
            if (!isFinishing()) {
                Log.i(LOG_TAG, "automation hand-off grace elapsed package=" + packageName);
                finish();
            }
        }, AUTOMATION_TARGET_HANDOFF_GRACE_MS);
    }

    private void launchTarget() {
        launchPackage(packageName());
    }

    private void forceStopTarget() {
        forceStopPackage(packageName());
    }

    private void scanOutputAndFillLatestInBackground() {
        if (!mScanRunning.compareAndSet(false, true)) {
            return;
        }
        mLastActionValue = getString(R.string.log_scan_started);
        notifyComposeChanged();
        new Thread(() -> {
            try {
                scanOutputAndFillLatest();
            } finally {
                mScanRunning.set(false);
                notifyComposeChanged();
            }
        }, "r0dump-scan-output").start();
    }

    private void scanOutputAndFillLatest() {
        RepairPlan plan = buildRepairPlan(true);
        updateRepairSummary(plan);
        log(getString(R.string.log_scan_done,
                plan.dexFiles.size(), plan.recordFiles.size(), plan.projectDir));
    }

    private String buildArtifactsSummaryText(RepairPlan plan) {
        if (plan == null) {
            plan = buildRepairPlan(true);
        }
        StringBuilder summary = new StringBuilder();
        summary.append("目标 App: ")
                .append(plan.packageName.isEmpty()
                        ? getString(R.string.status_empty_value) : plan.packageName)
                .append('\n');
        summary.append("扫描目录: ").append(plan.projectDir.getAbsolutePath()).append('\n');
        summary.append("DEX/raw data 文件: ").append(plan.dexFiles.size()).append('\n');
        for (File dex : plan.dexFiles) {
            summary.append("  • ").append(dex.getName())
                    .append("  ").append(formatFileSize(dex.length())).append('\n');
        }
        summary.append("Method Records: ").append(plan.recordFiles.size()).append('\n');
        for (File record : plan.recordFiles) {
            summary.append("  • ").append(record.getName())
                    .append("  ").append(formatFileSize(record.length())).append('\n');
        }
        File status = findLatestStatusFile();
        summary.append("状态文件: ")
                .append(status != null ? status.getAbsolutePath() : "未发现")
                .append('\n');
        summary.append("修复输出: ").append(plan.outputDir.getAbsolutePath()).append('\n');
        summary.append("ZIP: ").append(plan.zipFile.getName());
        return summary.toString();
    }


    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public void requestLogcatRefreshFromCompose(boolean quiet) {
        refreshLogcatInBackground(quiet);
    }

    public UiSnapshot setLogcatR0dumpOnlyFromCompose(boolean r0dumpOnly) {
        if (mLogcatR0dumpOnly != r0dumpOnly) {
            mLogcatR0dumpOnly = r0dumpOnly;
            getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(UI_LOGCAT_R0DUMP_ONLY, r0dumpOnly)
                    .apply();
            clearLogcatPreview();
        }
        refreshLogcatInBackground(true);
        return getComposeSnapshot(false, "", mConfig.showSystemApps);
    }

    public UiSnapshot clearLogcatFromCompose() {
        clearLogcatPreview();
        return getComposeSnapshot(false, "", mConfig.showSystemApps);
    }

    private void clearLogcatPreview() {
        synchronized (mLogcatLock) {
            mLogcatLines.clear();
            mLogcatValue = "";
        }
        mLastActionValue = getString(R.string.logcat_display_cleared);
        notifyComposeChanged();
    }

    private void refreshLogcatInBackground(boolean quiet) {
        if (!mLogcatRefreshRunning.compareAndSet(false, true)) {
            return;
        }
        notifyComposeChanged();
        new Thread(() -> {
            try {
                mLogcatValue = mergeLogcatSnapshot(readLogcatSnapshot(mLogcatR0dumpOnly));
                if (!quiet) {
                    log(getString(R.string.logcat_refreshed));
                } else {
                    notifyComposeChanged();
                }
            } catch (Throwable t) {
                mLogcatValue = getString(R.string.logcat_read_failed, t);
                if (!quiet) {
                    log(getString(R.string.logcat_read_failed, t));
                } else {
                    notifyComposeChanged();
                }
            } finally {
                mLogcatRefreshRunning.set(false);
                notifyComposeChanged();
            }
        }, "r0dump-logcat-refresh").start();
    }

    private String mergeLogcatSnapshot(List<String> snapshotLines) {
        synchronized (mLogcatLock) {
            HashSet<String> seen = new HashSet<>(mLogcatLines);
            for (String line : snapshotLines) {
                if (line == null || line.isEmpty() || !seen.add(line)) {
                    continue;
                }
                mLogcatLines.add(line);
            }
            while (mLogcatLines.size() > LOGCAT_BUFFER_LINE_LIMIT) {
                mLogcatLines.remove(0);
            }
            if (mLogcatLines.isEmpty()) {
                return mLogcatR0dumpOnly
                        ? getString(R.string.logcat_empty_r0dump)
                        : getString(R.string.logcat_empty, 0);
            }
            StringBuilder out = new StringBuilder();
            for (String line : mLogcatLines) {
                out.append(line).append('\n');
            }
            return out.toString();
        }
    }

    private List<String> readLogcatSnapshot(boolean r0dumpOnly) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t",
                String.valueOf(LOGCAT_LINE_LIMIT))
                .redirectErrorStream(true)
                .start();
        ArrayList<String> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!r0dumpOnly || isR0dumpLogLine(line)) {
                    out.add(line);
                }
            }
        }
        int code = process.waitFor();
        if (out.isEmpty() && code != 0 && !r0dumpOnly) {
            out.add(getString(R.string.logcat_empty, code));
        }
        return out;
    }

    private boolean isR0dumpLogLine(String line) {
        if (line == null) {
            return false;
        }
        return line.toLowerCase(Locale.ROOT).contains("r0dump");
    }

    public UiSnapshot refreshStatusFromCompose(boolean quiet) {
        refreshStatusInBackground(quiet);
        return getComposeSnapshot(false, "", mConfig.showSystemApps);
    }

    private void refreshStatus() {
        refreshStatusInBackground(false);
    }

    private void refreshStatusInBackground(boolean quiet) {
        if (!mStatusRefreshRunning.compareAndSet(false, true)) {
            return;
        }
        notifyComposeChanged();
        new Thread(() -> {
            try {
                refreshStatusInternal(quiet);
            } finally {
                mStatusRefreshRunning.set(false);
                notifyComposeChanged();
            }
        }, "r0dump-status-refresh").start();
    }

    private void refreshStatusInternal(boolean quiet) {
        File status = findLatestStatusFile(!quiet);
        if (status == null) {
            setStatusSummary(getString(R.string.status_waiting_for_file));
            if (!quiet) {
                log(getString(R.string.log_status_refreshed));
            }
            return;
        }
        try {
            String content = readBoundedUtf8File(status, MAX_STATUS_FILE_BYTES);
            JSONObject object = new JSONObject(content);
            boolean stale = normalizeStaleStatus(object);
            setStatusSummary(formatStatusSummary(object, status));
            if (stale) {
                mLastActionValue = "已收口已退出进程留下的旧 DUMP 状态。";
            }
            String statusRunId = object.optString("run_id", "");
            String phase = object.optString("phase", "");
            if (!mConfig.globalRuntime && !"all".equals(text(mConfig.processMode))
                    && mConfig.stopAfterComplete && isStatusTerminalPhase(phase)
                    && !statusRunId.isEmpty() && statusRunId.equals(mActiveRunId)) {
                Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ENABLED, 0);
            }
            if (!quiet) {
                log(getString(R.string.log_status_refreshed));
            }
        } catch (Throwable t) {
            setStatusSummary(getString(R.string.status_read_failed, status, t));
            if (!quiet) {
                log(getString(R.string.log_status_refreshed));
            }
        }
    }

    private void setStatusSummary(String value) {
        mStatusSummaryValue = value;
        notifyComposeChanged();
    }

    private String formatStatusSummary(JSONObject object, File statusFile) {
        StringBuilder summary = new StringBuilder();
        summary.append(getString(R.string.status_visual_target,
                object.optString("package", packageName()),
                object.optString("process", "unknown"),
                object.optInt("pid", 0))).append('\n');
        summary.append(getString(R.string.status_visual_phase,
                friendlyPhase(object.optString("phase", "unknown")))).append('\n');
        summary.append(getString(R.string.status_visual_counts,
                object.optLong("dex_files_written", 0),
                object.optLong("method_records_written", 0),
                object.optLong("duplicate_methods_skipped", 0))).append('\n');
        summary.append("Raw dexdata: ")
                .append(object.optLong("dexdata_files_written", 0))
                .append('\n');
        summary.append(getString(R.string.status_visual_skips,
                object.optLong("nonstandard_dex_methods_skipped",
                        object.optLong("compact_dex_methods_skipped", 0)),
                object.optLong("invalid_methods_skipped", 0))).append('\n');
        summary.append(getString(R.string.status_visual_backfill,
                object.optLong("force_backfill_attempts", 0),
                forceBackfillChangedSuccessFromStatus(object),
                object.optLong("force_backfill_invoked_unchanged", 0),
                object.optLong("force_backfill_skipped_by_guard", 0),
                forceBackfillInvokeExceptionsFromStatus(object),
                object.optLong("force_backfill_failed", 0))).append('\n');
        summary.append("ClassLoader: ")
                .append(object.optLong("classloaders_walked", 0))
                .append("/")
                .append(object.optLong("classloader_candidates", 0))
                .append(" · dexElements=")
                .append(object.optLong("classloader_dex_elements", 0))
                .append(" · loadedClasses=")
                .append(object.optLong("loaded_class_table_classes", 0))
                .append(" · manifestComponents=")
                .append(object.optLong("manifest_component_classes", 0))
                .append(" · manifestDumped=")
                .append(object.optLong("manifest_seed_dumped", 0))
                .append('\n');
        long updated = object.optLong("updated_at", 0);
        if (updated > 0) {
            summary.append(getString(R.string.status_visual_updated,
                    DateFormat.getDateTimeInstance().format(new Date(updated * 1000L)))).append('\n');
        }
        summary.append(getString(R.string.status_visual_strategies,
                readableJsonValue(object.opt("strategies")))).append('\n');
        summary.append(getString(R.string.status_visual_paths,
                object.optString("output_dir", ""),
                statusFile.getAbsolutePath()));
        if (object.optBoolean("_display_stale", false)) {
            summary.append('\n').append("状态已收口：记录中的进程已退出，已停止显示为正在 DUMP。");
        }
        return summary.toString();
    }

    private UiStatusInfo buildUiStatusInfo(File statusFile) {
        UiStatusInfo out = new UiStatusInfo();
        if (statusFile == null || !statusFile.exists()) {
            out.message = mStatusSummaryValue.isEmpty()
                    ? getString(R.string.status_waiting_for_file) : mStatusSummaryValue;
            return out;
        }
        out.filePath = statusFile.getAbsolutePath();
        try {
            String content = readBoundedUtf8File(statusFile, MAX_STATUS_FILE_BYTES);
            JSONObject object = new JSONObject(content);
            out.stale = normalizeStaleStatus(object);
            out.available = true;
            out.packageName = cleanStatusText(object.optString("package", packageName()));
            out.processName = cleanStatusText(object.optString("process", "unknown"));
            out.pid = object.optInt("pid", 0);
            out.phaseRaw = object.optString("phase", "");
            out.phaseLabel = friendlyPhase(out.phaseRaw);
            out.outputDir = cleanStatusText(object.optString("output_dir", ""));
            out.startedAt = formatEpochSeconds(object.optLong("started_at", 0));
            out.updatedAt = formatEpochSeconds(object.optLong("updated_at", 0));
            out.strategies = readableJsonValue(object.opt("strategies"));
            out.dexFilesWritten = object.optLong("dex_files_written", 0);
            out.dexDataFilesWritten = object.optLong("dexdata_files_written", 0);
            out.methodRecordsWritten = object.optLong("method_records_written", 0);
            out.duplicateMethodsSkipped = object.optLong("duplicate_methods_skipped", 0);
            out.nonstandardDexMethodsSkipped = object.optLong("nonstandard_dex_methods_skipped",
                    object.optLong("compact_dex_methods_skipped", 0));
            out.invalidMethodsSkipped = object.optLong("invalid_methods_skipped", 0);
            out.forceBackfillAttempts = object.optLong("force_backfill_attempts", 0);
            out.forceBackfillSuccess = object.optLong("force_backfill_success", 0);
            out.forceBackfillFailed = object.optLong("force_backfill_failed", 0);
            out.forceBackfillSkippedByGuard = object.optLong("force_backfill_skipped_by_guard", 0);
            out.forceBackfillInvokedUnchanged = object.optLong("force_backfill_invoked_unchanged", 0);
            out.forceBackfillInvokeExceptions = forceBackfillInvokeExceptionsFromStatus(object);
            out.forceBackfillChangedSuccess = forceBackfillChangedSuccessFromStatus(object);
            out.classLoaderCandidates = object.optLong("classloader_candidates", 0);
            out.classLoadersWalked = object.optLong("classloaders_walked", 0);
            out.classLoaderDexElements = object.optLong("classloader_dex_elements", 0);
            out.classLoaderUniqueCookies = object.optLong("classloader_unique_cookies", 0);
            out.loadedClassTableClasses = object.optLong("loaded_class_table_classes", 0);
            out.manifestComponentClasses = object.optLong("manifest_component_classes", 0);
            out.manifestSeedDumped = object.optLong("manifest_seed_dumped", 0);
            out.classLoaders = readableClassLoaders(object.optJSONArray("classloaders"));
        } catch (Throwable t) {
            out.readError = true;
            out.message = getString(R.string.status_read_failed, statusFile, t);
        }
        return out;
    }

    private String readableClassLoaders(JSONArray loaders) {
        if (loaders == null || loaders.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < loaders.length(); i++) {
            JSONObject loader = loaders.optJSONObject(i);
            if (loader == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append('#').append(loader.optInt("index", i + 1))
                    .append(' ')
                    .append(cleanStatusText(loader.optString("loader", "")))
                    .append('\n')
                    .append("baseDex=").append(loader.optBoolean("base_dex", false))
                    .append(" · dexElements=").append(loader.optLong("dex_elements", 0))
                    .append(" · uniqueCookies=").append(loader.optLong("unique_cookies", 0))
                    .append(" · dexClasses=").append(loader.optLong("dex_class_names", 0))
                    .append(" · loadedClasses=")
                    .append(loader.optLong("loaded_class_table_classes", 0))
                    .append(" · manifestComponents=")
                    .append(loader.optLong("manifest_component_classes", 0))
                    .append(" · manifestDumped=")
                    .append(loader.optLong("manifest_seed_dumped_delta", 0))
                    .append(" · dumpedDelta=").append(loader.optLong("dumped_delta", 0));
        }
        return out.toString();
    }

    private long forceBackfillChangedSuccessFromStatus(JSONObject object) {
        return object.optLong("force_backfill_changed_success",
                object.optLong("force_backfill_success", 0));
    }

    private long forceBackfillInvokeExceptionsFromStatus(JSONObject object) {
        if (object.has("force_backfill_invoke_exceptions")) {
            return object.optLong("force_backfill_invoke_exceptions", 0);
        }
        long legacyFailed = object.optLong("force_backfill_failed", 0);
        long guardSkipped = object.optLong("force_backfill_skipped_by_guard", 0);
        long unchanged = object.optLong("force_backfill_invoked_unchanged", 0);
        return Math.max(0, legacyFailed - guardSkipped - unchanged);
    }

    private String formatEpochSeconds(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        return DateFormat.getDateTimeInstance().format(new Date(seconds * 1000L));
    }

    private String cleanStatusText(String value) {
        String text = text(value);
        return text.isEmpty() ? getString(R.string.status_empty_value) : text;
    }

    private boolean normalizeStaleStatus(JSONObject object) {
        if (object == null || !isStatusActivePhase(object.optString("phase", ""))) {
            return false;
        }
        final int pid = object.optInt("pid", 0);
        if (pid <= 0 || isStatusProcessAlive(pid)) {
            return false;
        }
        final long updated = object.optLong("updated_at", 0L);
        final long now = System.currentTimeMillis() / 1000L;
        if (updated > 0L && (now < updated || now - updated < STATUS_STALE_GRACE_SECONDS)) {
            return false;
        }
        try {
            object.put("phase", "stopped");
            object.put("stop_reason", "process_exit");
            object.put("runtime_enabled", false);
            object.put("_display_stale", true);
        } catch (Throwable ignored) {
            return false;
        }
        // A dead target must not leave the global switch armed for the next
        // process.  The next explicit start writes a fresh run id.
        try {
            Settings.Global.putInt(getContentResolver(), R0DUMP_SETTING_ENABLED, 0);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private boolean isStatusProcessAlive(int pid) {
        if (new File("/proc/" + pid).exists()) {
            return true;
        }
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (manager != null) {
                List<ActivityManager.RunningAppProcessInfo> processes =
                        manager.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process != null && process.pid == pid) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String readStatusPhase(File status) {
        if (status == null || !status.exists()) {
            return "";
        }
        try {
            String content = readBoundedUtf8File(status, MAX_STATUS_FILE_BYTES);
            JSONObject object = new JSONObject(content);
            return object.optString("phase", "");
        } catch (Throwable t) {
            return "";
        }
    }

    private String readBoundedUtf8File(File file, int maxBytes) throws IOException {
        if (file == null || !file.isFile() || file.length() > maxBytes) {
            throw new IOException("状态文件无效或过大: " + file);
        }
        try (FileInputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream(
                        (int) Math.min(file.length(), 16 * 1024L))) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (read > maxBytes - total) {
                    throw new IOException("状态文件读取超过上限: " + file);
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private boolean isStatusPhase(File status, String phase) {
        return phase.equals(readStatusPhase(status));
    }

    private boolean isStatusTerminal(File status) {
        return isStatusTerminalPhase(readStatusPhase(status));
    }

    private boolean isStatusTerminalPhase(String phase) {
        return "complete".equals(phase) || "stopped".equals(phase) || "stopped_by_limit".equals(phase);
    }

    private boolean isStatusActivePhase(String phase) {
        return "configured".equals(phase) || "waiting_delay".equals(phase)
                || "class_walk".equals(phase) || "dumping".equals(phase)
                || "force_backfill_changed".equals(phase)
                || "force_backfill_unchanged".equals(phase);
    }

    private String friendlyPhase(String phase) {
        if ("dumping".equals(phase)) {
            return getString(R.string.phase_dumping);
        }
        if ("configured".equals(phase)) {
            return getString(R.string.phase_configured);
        }
        if ("waiting_delay".equals(phase)) {
            return getString(R.string.phase_waiting_delay);
        }
        if ("class_walk".equals(phase)) {
            return getString(R.string.phase_class_walk);
        }
        if ("class_walk_failed".equals(phase)) {
            return getString(R.string.phase_class_walk_failed);
        }
        if ("complete".equals(phase)) {
            return getString(R.string.phase_complete);
        }
        if ("stopped".equals(phase) || "stopped_by_limit".equals(phase)) {
            return getString(R.string.phase_stopped);
        }
        return phase == null || phase.isEmpty() ? getString(R.string.phase_unknown) : phase;
    }

    private String readableJsonValue(Object value) {
        if (value == null) {
            return getString(R.string.status_empty_value);
        }
        String text = String.valueOf(value);
        text = text.replace("[", "").replace("]", "").replace("\"", "");
        text = text.replace(",", ", ");
        return text.trim().isEmpty() ? getString(R.string.status_empty_value) : text;
    }

    private File findLatestStatusFile() {
        return findLatestStatusFile(false);
    }

    private File findLatestStatusFile(boolean deepScan) {
        File newest = null;
        Set<String> seen = new HashSet<>();
        for (File status : candidateStatusFiles()) {
            String path = status.getAbsolutePath();
            if (seen.add(path)) {
                newest = newestStatusFile(newest, status);
            }
        }
        if (newest != null || !deepScan) {
            return newest;
        }
        for (File root : candidateStatusSearchDirs()) {
            newest = findLatestStatusFileInDir(root, newest);
        }
        return newest;
    }

    private List<File> candidateStatusFiles() {
        List<File> files = new ArrayList<>();
        for (File root : candidateStatusSearchDirs()) {
            for (String statusFileName : STATUS_FILE_NAMES) {
                files.add(new File(root, statusFileName));
            }
        }
        return files;
    }

    private List<File> candidateStatusSearchDirs() {
        List<File> roots = new ArrayList<>();
        String outputRoot = nonEmpty(mConfig.outputRoot, DEFAULT_OUTPUT_ROOT);
        String process = text(mConfig.targetProcess);
        String pkg = packageName();
        File currentProject = currentProjectDir();
        roots.addAll(projectDirCandidates());
        if (currentProject != null) {
            roots.add(currentProject);
            String processDir = !process.isEmpty() && !"*".equals(process) ? process : pkg;
            if (!processDir.isEmpty()) {
                roots.add(new File(currentProject, sanitize(processDir)));
            }
        }
        if (!outputRoot.isEmpty()) {
            if (!process.isEmpty() && !"*".equals(process)) {
                roots.add(new File(outputRoot, sanitize(process)));
            }
            if (!pkg.isEmpty()) {
                roots.add(new File(outputRoot, sanitize(pkg)));
            }
        }
        if (!pkg.isEmpty()) {
            roots.add(new File(DEFAULT_OUTPUT_ROOT, sanitize(pkg)));
            roots.addAll(appPrivateOutputRoots());
        }
        if (pkg.isEmpty() && !outputRoot.isEmpty()) {
            roots.add(new File(outputRoot));
        }
        return roots;
    }

    private File findLatestStatusFileInDir(File root, File newest) {
        return findLatestStatusFileInDir(root, newest, 0, new RepairScanBudget(),
                new HashSet<String>());
    }

    private File findLatestStatusFileInDir(File root, File newest, int depth,
            RepairScanBudget budget, Set<String> visited) {
        if (root == null || !root.exists()) {
            return newest;
        }
        if (depth > MAX_STATUS_SCAN_DEPTH || budget.files >= MAX_STATUS_SCAN_FILES) {
            budget.truncated = true;
            return newest;
        }
        try {
            if (Files.isSymbolicLink(root.toPath())
                    || !visited.add(root.getCanonicalPath())) {
                return newest;
            }
        } catch (IOException ignored) {
            return newest;
        }
        if (root.isFile()) {
            budget.files++;
            return newestStatusFile(newest, root);
        }
        if (depth > 0 && ("repaired".equals(root.getName())
                || root.getName().startsWith(".repaired-"))) {
            return newest;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return newest;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                newest = findLatestStatusFileInDir(
                        file, newest, depth + 1, budget, visited);
            } else {
                if (budget.files >= MAX_STATUS_SCAN_FILES) {
                    budget.truncated = true;
                    break;
                }
                budget.files++;
                newest = newestStatusFile(newest, file);
            }
        }
        return newest;
    }

    private File newestStatusFile(File newest, File candidate) {
        if (candidate == null || !candidate.exists()
                || !isR0DumpStatusFileName(candidate.getName())) {
            return newest;
        }
        if (newest == null || candidate.lastModified() > newest.lastModified()) {
            return candidate;
        }
        return newest;
    }

    private boolean isR0DumpStatusFileName(String name) {
        for (String statusFileName : STATUS_FILE_NAMES) {
            if (statusFileName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String pathSummary() {
        String pkg = packageName();
        String process = text(mConfig.targetProcess);
        String exported = DEFAULT_OUTPUT_ROOT + "/"
                + sanitize(!process.isEmpty() && !"*".equals(process) ? process : pkg);
        String working = pkg.isEmpty()
                ? getString(R.string.path_unknown_until_target)
                : "/sdcard/Android/data/" + sanitize(pkg) + "/files/r0dump/<process>";
        return getString(R.string.path_summary, working, exported);
    }

    private boolean isDumpDexFileName(String name) {
        // Standard ART dump: dex_<locationHash>_<size>_0x<checksum>_<STRATEGY>.dex.
        // Raw ART data dump: dexdata_<locationHash>_<size>_0x00000000_<STRATEGY>.bin.
        // Keep the historical *_dexfile*.dex matcher so older dumps remain repairable.
        return name != null && ((name.endsWith(".dex")
                && (name.startsWith("dex_")
                        || name.startsWith("dexfixed_")
                        || name.startsWith("dexcontainer_")
                        || name.startsWith("dexrecon_")
                        || name.contains("_dexfile")))
                || isDumpDexDataFileName(name));
    }

    private boolean isDumpDexDataFileName(String name) {
        return name != null && DEXDATA_FILE_PATTERN.matcher(name).matches();
    }

    private boolean isRepairRecordFileName(String name) {
        if (name == null || isDumpDexDataFileName(name)) {
            return false;
        }
        return (name.startsWith("methods_") && name.endsWith(".jsonl"))
                || (name.startsWith("methods_raw_") && name.endsWith(".jsonl"))
                || (name.endsWith(".bin") && !name.startsWith("dexdata_"));
    }

    private boolean launchPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            log(getString(R.string.log_target_package_empty));
            return false;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            log(getString(R.string.log_launch_entry_missing, pkg));
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launch);
        log(getString(R.string.log_launched, pkg));
        return true;
    }

    private void forceStopPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            log(getString(R.string.log_target_package_empty));
            return;
        }
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.forceStopPackage(pkg);
        log(getString(R.string.log_force_stopped, pkg));
    }

    /** Prepare the target-owned external files root before ART writes its fallback output. */
    private void prepareTargetOutputDirectory(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        File root = new File("/sdcard/Android/data/" + sanitize(pkg) + "/files/r0dump");
        if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory()) {
            log("无法预创建目标 App 的私有输出目录: " + root);
        }
    }

    private List<File> candidateOutputDirs() {
        List<File> roots = new ArrayList<>();
        String outputRoot = nonEmpty(mConfig.outputRoot, DEFAULT_OUTPUT_ROOT);
        String process = text(mConfig.targetProcess);
        String pkg = packageName();
        File currentProject = currentProjectDir();
        roots.addAll(projectDirCandidates());
        if (currentProject != null) {
            roots.add(currentProject);
        }
        if (!outputRoot.isEmpty()) {
            roots.add(new File(outputRoot));
            if (!process.isEmpty() && !"*".equals(process)) {
                roots.add(new File(outputRoot, sanitize(process)));
            }
            if (!pkg.isEmpty()) {
                roots.add(new File(outputRoot, sanitize(pkg)));
            }
        }
        if (!pkg.isEmpty()) {
            roots.add(new File(DEFAULT_OUTPUT_ROOT, sanitize(pkg)));
            roots.addAll(appPrivateOutputRoots());
        }
        return roots;
    }

    private File currentProjectDir() {
        String pkg = packageName();
        if (pkg.isEmpty()) {
            return null;
        }
        List<File> candidates = projectDirCandidates();
        for (File candidate : candidates) {
            if (containsDumpArtifacts(candidate)) {
                return candidate;
            }
        }
        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<File> appPrivateOutputRoots() {
        List<File> roots = new ArrayList<>();
        String pkg = packageName();
        if (pkg.isEmpty()) {
            return roots;
        }
        String safePackage = sanitize(pkg);
        roots.add(new File("/sdcard/Android/data/" + safePackage + "/files/r0dump"));
        roots.add(new File("/data/user/0/" + safePackage + "/files/r0dump"));
        roots.add(new File("/data/data/" + safePackage + "/files/r0dump"));
        return roots;
    }

    private List<File> projectDirCandidates() {
        List<File> candidates = new ArrayList<>();
        String pkg = packageName();
        if (pkg.isEmpty()) {
            return candidates;
        }
        String outputRoot = nonEmpty(mConfig.outputRoot, DEFAULT_OUTPUT_ROOT);
        String runId = nonEmpty(mActiveRunId, text(Settings.Global.getString(
                getContentResolver(), R0DUMP_SETTING_RUN_ID)));
        String safeRun = runId.isEmpty() || "pending".equals(runId)
                ? "" : sanitize(runId);
        List<File> bases = new ArrayList<>();
        bases.add(new File(outputRoot, sanitize(pkg)));
        bases.addAll(appPrivateOutputRoots());
        for (File base : bases) {
            if (!safeRun.isEmpty()) {
                candidates.add(new File(base, safeRun));
            }
            candidates.add(base);
        }
        return candidates;
    }

    private boolean containsDumpArtifacts(File root) {
        if (root == null || !root.isDirectory()) {
            return false;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isFile() && (isDumpDexFileName(file.getName())
                    || isRepairRecordFileName(file.getName())
                    || isR0DumpStatusFileName(file.getName()))) {
                return true;
            }
            if (file.isDirectory()) {
                File[] nested = file.listFiles();
                if (nested == null) {
                    continue;
                }
                for (File child : nested) {
                    if (child.isFile() && (isDumpDexFileName(child.getName())
                            || isRepairRecordFileName(child.getName())
                            || isR0DumpStatusFileName(child.getName()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private RepairPlan repairPlanForSnapshot(boolean scanFiles) {
        RepairPlan base = buildRepairPlan(false);
        if (scanFiles) {
            RepairPlan scanned = buildRepairPlan(true);
            mCachedRepairPlan = scanned;
            return scanned;
        }
        RepairPlan cached = mCachedRepairPlan;
        if (isSameRepairTarget(base, cached)) {
            return cached;
        }
        return base;
    }

    private boolean isSameRepairTarget(RepairPlan left, RepairPlan right) {
        return left != null && right != null
                && left.packageName.equals(right.packageName)
                && left.projectDir.getAbsolutePath().equals(right.projectDir.getAbsolutePath())
                && left.outputDir.getAbsolutePath().equals(right.outputDir.getAbsolutePath());
    }

    private RepairPlan buildRepairPlan(boolean scanFiles) {
        String pkg = packageName();
        File projectDir = currentProjectDir();
        if (projectDir == null) {
            projectDir = new File(DEFAULT_OUTPUT_ROOT);
        }
        File outputDir = new File(projectDir, "repaired");
        File zipFile = new File(outputDir, sanitize(pkg.isEmpty() ? "r0dump" : pkg)
                + "_r0dump_repaired.zip");
        List<File> dexFiles = new ArrayList<>();
        List<File> recordFiles = new ArrayList<>();
        if (scanFiles) {
            List<File> files = new ArrayList<>();
            RepairScanBudget budget = new RepairScanBudget();
            collectFiles(projectDir, files, 0, budget, new HashSet<String>());
            if (budget.truncated) {
                log("修复扫描达到保护上限，已截断: files=" + budget.files
                        + " bytes=" + budget.bytes);
            }
            for (File file : files) {
                String name = file.getName();
                if (isDumpDexFileName(name) && !isRepairedOutput(file)) {
                    dexFiles.add(file);
                } else if (isRepairRecordFileName(name)) {
                    recordFiles.add(file);
                }
            }
            Collections.sort(dexFiles);
            Collections.sort(recordFiles);
            if (dexFiles.size() > MAX_REPAIR_DEX_FILES) {
                log("DEX 输入超过扫描上限，保留前 " + MAX_REPAIR_DEX_FILES + " 个");
                dexFiles.subList(MAX_REPAIR_DEX_FILES, dexFiles.size()).clear();
            }
            if (recordFiles.size() > MAX_REPAIR_RECORD_FILES) {
                log("method record 文件超过扫描上限，保留前 "
                        + MAX_REPAIR_RECORD_FILES + " 个");
                recordFiles.subList(MAX_REPAIR_RECORD_FILES, recordFiles.size()).clear();
            }
        }
        return new RepairPlan(pkg, projectDir, outputDir, zipFile, dexFiles, recordFiles);
    }

    private boolean isRepairedOutput(File file) {
        String path = file.getAbsolutePath();
        return path.contains("/repaired/") || path.contains("_r0dump_repaired");
    }

    private String buildRepairSummaryText(RepairPlan plan) {
        if (plan == null) {
            plan = buildRepairPlan(false);
        }
        return getString(R.string.repair_plan_summary,
                plan.packageName.isEmpty() ? getString(R.string.status_empty_value) : plan.packageName,
                plan.projectDir.getAbsolutePath(),
                plan.dexFiles.size(),
                plan.recordFiles.size(),
                plan.outputDir.getAbsolutePath(),
                plan.zipFile.getName());
    }

    private void updateRepairSummary(RepairPlan plan) {
        if (plan != null && (!plan.dexFiles.isEmpty() || !plan.recordFiles.isEmpty())) {
            mCachedRepairPlan = plan;
        }
    }

    private void updateRepairProgress(String phase, int percent) {
        Runnable update = () -> {
            mRepairProgressValue = "修复阶段: " + phase + "\n进度: " + percent + "%";
            notifyComposeChanged();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run();
        } else {
            runOnUiThread(update);
        }
    }


    private void clearRepairOutputDir(File outputDir) {
        if (outputDir == null || !outputDir.exists()) {
            return;
        }
        if (!"repaired".equals(outputDir.getName())) {
            log("跳过清理非 repaired 输出目录: " + outputDir);
            return;
        }
        File[] files = outputDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            deleteRecursively(file);
        }
    }

    private void deleteRecursively(File file) {
        deleteRecursively(file, 0);
    }

    private void deleteRecursively(File file, int depth) {
        if (file == null || !file.exists()) {
            return;
        }
        if (depth > 64) {
            log("清理目录超过深度上限: " + file);
            return;
        }
        if (Files.isSymbolicLink(file.toPath())) {
            if (!file.delete() && file.exists()) {
                log("清理符号链接失败: " + file);
            }
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child, depth + 1);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            log("清理旧修复产物失败: " + file);
        }
    }

    private void collectFiles(File root, List<File> out, int depth,
            RepairScanBudget budget, Set<String> visited) {
        if (root == null || !root.exists()) {
            return;
        }
        if (depth > MAX_REPAIR_SCAN_DEPTH || budget.truncated) {
            budget.truncated = true;
            return;
        }
        if (depth > 0 && ("repaired".equals(root.getName())
                || root.getName().startsWith(".repaired-staging-")
                || root.getName().startsWith(".repaired-backup-"))) {
            return;
        }
        try {
            if (Files.isSymbolicLink(root.toPath())
                    || !visited.add(root.getCanonicalPath())) {
                return;
            }
        } catch (IOException ignored) {
            return;
        }
        if (root.isFile()) {
            if (budget.files >= MAX_REPAIR_SCAN_FILES
                    || root.length() > MAX_REPAIR_SCAN_BYTES - budget.bytes) {
                budget.truncated = true;
                return;
            }
            out.add(root);
            budget.files++;
            budget.bytes += root.length();
            return;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files, (left, right) -> left.getAbsolutePath()
                .compareTo(right.getAbsolutePath()));
        for (File file : files) {
            collectFiles(file, out, depth + 1, budget, visited);
        }
    }

    private void collectFiles(File root, List<File> out) {
        collectFiles(root, out, 0, new RepairScanBudget(), new HashSet<String>());
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void repairDexFromUi() {
        startRepairInBackground(false, false);
    }

    private void startRepairInBackground(boolean automation, boolean finishOnComplete) {
        if (!mRepairRunning.compareAndSet(false, true)) {
            log(getString(R.string.log_repair_already_running));
            if (automation) {
                Log.i(LOG_TAG, "manager automation repair ignored: already running");
            }
            if (finishOnComplete) {
                mMainHandler.postDelayed(this::finish, 250);
            }
            return;
        }
        mRepairProgressValue = "修复已启动，正在后台扫描产物。";
        notifyComposeChanged();
        new Thread(() -> {
            try {
                updateRepairProgress("扫描产物", 5);
                final RepairPlan plan = buildRepairPlan(true);
                updateRepairSummary(plan);
                log(getString(R.string.log_repair_started, plan.dexFiles.size(),
                        plan.recordFiles.size(), plan.projectDir));
                updateRepairProgress("读取 method records", 8);
                RepairStats stats = repairProject(plan);
                updateRepairProgress("完成", 100);
                runOnUiThread(() -> {
                    mRepairRunning.set(false);
                    updateRepairSummary(buildRepairPlan(true));
                    log(getString(R.string.log_repair_done, stats.dexInputs, stats.recordFiles,
                            stats.repairedDex, stats.seen, stats.applied, stats.skipped,
                            stats.duplicates + stats.duplicateOutputs, stats.bytesWritten,
                            stats.zipFile));
                    if (automation) {
                        Log.i(LOG_TAG, "manager automation repair done package="
                                + plan.packageName
                                + " dexInputs=" + stats.dexInputs
                                + " rawDataInputs=" + stats.rawDataInputs
                                + " rebuiltRawDex=" + stats.rebuiltRawDex
                                + " recordFiles=" + stats.recordFiles
                                + " repairedDex=" + stats.repairedDex
                                + " seen=" + stats.seen
                                + " applied=" + stats.applied
                                + " skipped=" + stats.skipped
                                + " duplicates=" + (stats.duplicates + stats.duplicateOutputs)
                                + " bytesWritten=" + stats.bytesWritten
                                + " zip=" + stats.zipFile);
                    }
                    if (finishOnComplete) {
                        finish();
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    mRepairRunning.set(false);
                    mRepairProgressValue = "修复失败: " + t;
                    notifyComposeChanged();
                    log(getString(R.string.log_repair_failed, t));
                    if (automation) {
                        Log.e(LOG_TAG, "manager automation repair failed", t);
                    }
                    if (finishOnComplete) {
                        finish();
                    }
                });
            }
        }, "r0dump-repair").start();
    }

    private RepairStats repairProject(RepairPlan plan) throws Exception {
        if (plan.packageName.isEmpty()) {
            throw new IOException(getString(R.string.log_target_package_empty));
        }
        if (plan.dexFiles.isEmpty()) {
            throw new IOException(getString(R.string.error_no_dex_files, plan.projectDir));
        }
        boolean hasRawDexData = false;
        for (File dexFile : plan.dexFiles) {
            if (isDumpDexDataFileName(dexFile.getName())) {
                hasRawDexData = true;
                break;
            }
        }
        if (hasRawDexData && plan.recordFiles.isEmpty()) {
            log("raw dexdata 未发现 method record，将只尝试直接识别标准 DEX: "
                    + plan.projectDir);
        }
        RepairStats total = new RepairStats();
        total.dexInputs = plan.dexFiles.size();
        total.recordFiles = plan.recordFiles.size();
        final File stagingDir = new File(plan.projectDir,
                ".repaired-staging-" + UUID.randomUUID().toString());
        boolean committed = false;
        try {
            if (!stagingDir.mkdirs() && !stagingDir.isDirectory()) {
                throw new IOException("无法创建修复 staging 目录: " + stagingDir);
            }
            updateRepairProgress("建立 method record 索引", 10);
            RepairRecordIndex recordIndex = indexRepairRecords(plan.recordFiles);
            if (recordIndex.malformedLines > 0) {
                log("忽略格式错误的 method record: " + recordIndex.malformedLines);
            }
            Set<String> outputHashes = new HashSet<>();
            Set<String> outputNames = new HashSet<>();
            List<File> repairedFiles = new ArrayList<>();
            int dexIndex = 0;
            int dexCount = Math.max(1, plan.dexFiles.size());
            for (File dexFile : plan.dexFiles) {
            dexIndex++;
            boolean rawDexData = isDumpDexDataFileName(dexFile.getName());
            if (rawDexData) {
                total.rawDataInputs++;
            }
            DexKey key = parseDexKey(dexFile);
            if (key == null) {
                total.skipped++;
                log(getString(R.string.log_skip_unmatched_dex_name, dexFile.getName()));
                continue;
            }
            updateRepairProgress("匹配 " + (rawDexData ? "raw dexdata " : "DEX ")
                            + dexIndex + "/" + dexCount,
                    15 + (int) ((dexIndex - 1) * 25L / dexCount));
            File tempOutput = new File(stagingDir,
                    ".repairing_" + dexIndex + "_" + dexFile.getName() + ".tmp");
            deleteIfExists(tempOutput);
            try {
                int codeItemOffsetBias = 0;
                boolean standardDexOutput = false;
                if (hasDexMagic(dexFile)) {
                    copyFile(dexFile, tempOutput);
                    normalizeDexHeaderFields(tempOutput);
                    standardDexOutput = true;
                    if (rawDexData) {
                        total.rebuiltRawDex++;
                        log("raw dexdata 实际已包含 DEX magic，按标准 DEX 输出: "
                                + dexFile.getName());
                    }
                } else {
                    updateRepairProgress("重建 headerless dex " + dexIndex + "/" + dexCount,
                            28 + (int) ((dexIndex - 1) * 12L / dexCount));
                    RawDexHeaderSnapshot snapshot = recordIndex.headerSnapshots.get(key);
                    RawDexRebuildFile rebuilt = rebuildRawDexDataToDexFile(
                            dexFile, snapshot, tempOutput);
                    if (rebuilt != null) {
                        codeItemOffsetBias = rebuilt.codeItemOffsetBias;
                        standardDexOutput = true;
                        total.rebuiltRawDex++;
                        log("已从 ART header 快照重建标准 DEX: " + dexFile.getName()
                                + " fileSize=" + tempOutput.length()
                                + " dataOff=" + codeItemOffsetBias);
                    } else {
                        total.skipped++;
                        log((rawDexData ? "raw dexdata" : "headerless dex")
                                + " 无法重建标准 DEX，跳过而不是中断修复: "
                                + dexFile.getName());
                        deleteIfExists(tempOutput);
                        continue;
                    }
                }
                updateRepairProgress("Patch code_item " + dexIndex + "/" + dexCount,
                        40 + (int) ((dexIndex - 1) * 30L / dexCount));
                RepairStats one = applyRecordIndexToDexFile(
                        tempOutput, key, recordIndex, codeItemOffsetBias);
                mergeRepairStats(total, one);
                if (!standardDexOutput) {
                    total.skipped++;
                    deleteIfExists(tempOutput);
                    continue;
                }
                updateRepairProgress("修复 DEX header", 70);
                repairDexHeader(tempOutput);
                validateDexHeader(tempOutput, dexFile);
                String hash = md5Hex(tempOutput);
                updateRepairProgress("去重处理", 82);
                if (!outputHashes.add(hash)) {
                    total.duplicateOutputs++;
                    deleteIfExists(tempOutput);
                    continue;
                }
                String outputName = repairedDexName(dexFile, hash, true);
                int collision = 1;
                while (!outputNames.add(outputName)) {
                    int extension = outputName.lastIndexOf('.');
                    String suffix = "_" + collision++;
                    outputName = outputName.substring(0, extension) + suffix
                            + outputName.substring(extension);
                }
                File outputFile = new File(stagingDir, outputName);
                Files.move(tempOutput.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                repairedFiles.add(outputFile);
                total.repairedDex++;
            } catch (OutOfMemoryError oom) {
                total.skipped++;
                deleteIfExists(tempOutput);
                System.gc();
                log("单个 DEX 修复时内存不足，已跳过并继续后续文件: "
                        + dexFile.getName() + " error=" + oom);
            } catch (Exception e) {
                total.skipped++;
                deleteIfExists(tempOutput);
                log("单个 DEX 修复失败，已跳过并继续后续文件: "
                        + dexFile.getName() + " error=" + e);
            }
            }
            if (total.repairedDex == 0) {
                throw new IOException(getString(R.string.error_no_repaired_outputs));
            }
            updateRepairProgress("写入修复 manifest", 88);
            File manifest = writeRepairManifest(stagingDir, repairedFiles);
            List<File> zipInputs = new ArrayList<>(repairedFiles);
            zipInputs.add(manifest);
            File stagedZip = new File(stagingDir, plan.zipFile.getName());
            updateRepairProgress("打包 ZIP", 92);
            writeRepairZip(stagedZip, zipInputs);
            validateRepairZip(stagedZip, zipInputs);
            commitRepairOutput(stagingDir, plan.outputDir);
            committed = true;
            total.zipFile = new File(plan.outputDir, stagedZip.getName());
            return total;
        } finally {
            if (!committed) {
                deleteRecursively(stagingDir);
            }
        }
    }

    private void deleteIfExists(File file) {
        if (file != null && file.exists() && !file.delete()) {
            log("删除临时修复文件失败: " + file);
        }
    }

    private void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(source);
                FileOutputStream out = new FileOutputStream(target)) {
            copyStream(in, out, Long.MAX_VALUE);
        }
    }

    private long copyStream(FileInputStream in, FileOutputStream out, long maxBytes)
            throws IOException {
        byte[] buffer = new byte[REPAIR_IO_BUFFER_SIZE];
        long copied = 0;
        while (copied < maxBytes) {
            int want = (int) Math.min(buffer.length, maxBytes - copied);
            int read = in.read(buffer, 0, want);
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            copied += read;
        }
        return copied;
    }

    private RepairRecordIndex indexRepairRecords(List<File> files) throws IOException {
        RepairRecordIndex index = new RepairRecordIndex();
        String expectedRunId = text(mActiveRunId);
        recordFiles:
        for (File file : files) {
            try (RandomAccessFile records = new RandomAccessFile(file, "r")) {
                while (records.getFilePointer() < records.length()) {
                    long offset = records.getFilePointer();
                    String line;
                    try {
                        line = readBoundedRecordLine(records, MAX_RECORD_LINE_CHARS);
                    } catch (OutOfMemoryError oom) {
                        index.malformedLines++;
                        break;
                    }
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    if (line.isEmpty() || line.length() > MAX_RECORD_LINE_CHARS
                            || !line.contains("dex_location_hash")) {
                        continue;
                    }
                    try {
                        JSONObject object = new JSONObject(line);
                        DexKey key = dexKeyFromRecord(object);
                        if (key == null) {
                            index.malformedLines++;
                            continue;
                        }
                        String recordRunId = text(object.optString("run_id", ""));
                        if (!recordRunId.isEmpty() && !expectedRunId.isEmpty()
                                && !recordRunId.equals(expectedRunId)) {
                            index.staleRunLines++;
                            continue;
                        }
                        if (object.has("code_item_b64")) {
                            if (index.indexedRecords >= MAX_INDEXED_METHOD_RECORDS) {
                                index.truncated = true;
                                break recordFiles;
                            }
                            index.methodRecords.computeIfAbsent(key, ignored -> new ArrayList<>())
                                    .add(new RepairRecordPointer(file, offset));
                            index.indexedRecords++;
                        }
                        if (object.optBoolean("dex_header_valid", false)
                                && object.has("dex_header_b64")
                                && !index.headerSnapshots.containsKey(key)) {
                            String encoded = object.optString("dex_header_b64", "");
                            if (!encoded.isEmpty()
                                    && encoded.length() <= MAX_DEX_HEADER_SNAPSHOT_BYTES * 2) {
                                byte[] header = Base64.decode(encoded, Base64.DEFAULT);
                                if (header.length >= DEX_HEADER_SIZE
                                        && header.length <= MAX_DEX_HEADER_SNAPSHOT_BYTES
                                        && hasDexMagic(header)) {
                                    int fileSize = (int) object.optLong(
                                            "dex_header_file_size", readIntLe(header, 32));
                                    int dataOff = (int) object.optLong(
                                            "dex_header_data_off",
                                            header.length >= 112 ? readIntLe(header, 108) : 0);
                                    index.headerSnapshots.put(
                                            key, new RawDexHeaderSnapshot(header, fileSize, dataOff));
                                }
                            }
                        }
                    } catch (Throwable malformed) {
                        index.malformedLines++;
                    }
                }
            }
        }
        if (index.staleRunLines > 0) {
            log("忽略其他 run_id 的 method record: " + index.staleRunLines);
        }
        if (index.truncated) {
            log("method record 索引达到保护上限: " + MAX_INDEXED_METHOD_RECORDS);
        }
        return index;
    }

    private String readBoundedRecordLine(RandomAccessFile file, int maxChars)
            throws IOException {
        StringBuilder line = new StringBuilder(Math.min(maxChars, 4096));
        boolean sawByte = false;
        boolean oversized = false;
        while (true) {
            int value = file.read();
            if (value < 0) {
                break;
            }
            sawByte = true;
            if (value == '\n') {
                break;
            }
            if (value == '\r') {
                long nextOffset = file.getFilePointer();
                int next = file.read();
                if (next != '\n' && next >= 0) {
                    file.seek(nextOffset);
                }
                break;
            }
            if (line.length() < maxChars) {
                line.append((char) (value & 0xff));
            } else {
                oversized = true;
            }
        }
        if (!sawByte && line.length() == 0) {
            return null;
        }
        return oversized ? "" : line.toString();
    }

    private DexKey dexKeyFromRecord(JSONObject object) {
        if (object == null || !object.has("dex_location_hash")
                || !object.has("dex_size") || !object.has("dex_checksum")) {
            return null;
        }
        long size = object.optLong("dex_size", -1);
        String locationHash = text(object.optString("dex_location_hash", ""));
        String checksum = text(object.optString("dex_checksum", ""));
        if (size <= 0 || locationHash.isEmpty() || checksum.isEmpty()) {
            return null;
        }
        return new DexKey(locationHash, size, checksum);
    }

    private RepairStats applyRecordIndexToDexFile(File dexFile, DexKey targetKey,
            RepairRecordIndex index, int codeItemOffsetBias) throws Exception {
        RepairStats stats = new RepairStats();
        Set<String> patchedRanges = new HashSet<>();
        List<RepairRecordPointer> pointers = index.methodRecords.get(targetKey);
        if (pointers == null || pointers.isEmpty()) {
            return stats;
        }
        Map<File, List<Long>> offsetsByFile = new LinkedHashMap<>();
        for (RepairRecordPointer pointer : pointers) {
            offsetsByFile.computeIfAbsent(pointer.file, ignored -> new ArrayList<>())
                    .add(pointer.offset);
        }
        try (RandomAccessFile dex = new RandomAccessFile(dexFile, "rw")) {
            long dexLength = dex.length();
            for (Map.Entry<File, List<Long>> fileEntry : offsetsByFile.entrySet()) {
                try (RandomAccessFile records = new RandomAccessFile(fileEntry.getKey(), "r")) {
                    for (long offset : fileEntry.getValue()) {
                        records.seek(offset);
                        String line = readBoundedRecordLine(records, MAX_RECORD_LINE_CHARS);
                        if (line == null || line.length() > MAX_RECORD_LINE_CHARS) {
                            stats.skipped++;
                            continue;
                        }
                        JSONObject object = new JSONObject(line);
                        DexKey key = dexKeyFromRecord(object);
                        if (!targetKey.equals(key)) {
                            stats.skipped++;
                            continue;
                        }
                        byte[] codeItem;
                        int methodIdx = object.getInt("method_idx");
                        int codeItemLength = object.getInt("code_item_len");
                        String encodedCodeItem = object.getString("code_item_b64");
                        if (codeItemLength <= 0 || codeItemLength > MAX_CODE_ITEM_BYTES
                                || encodedCodeItem.length()
                                        > ((MAX_CODE_ITEM_BYTES + 2L) / 3L) * 4L + 4L) {
                            stats.skipped++;
                            continue;
                        }
                        try {
                            codeItem = Base64.decode(encodedCodeItem, Base64.DEFAULT);
                        } catch (OutOfMemoryError oom) {
                            stats.skipped++;
                            log("跳过过大的 method record，避免修复阶段 OOM: method_idx="
                                    + methodIdx + " file=" + fileEntry.getKey().getName());
                            continue;
                        }
                        applyRepairRecord(dex, dexLength, stats, patchedRanges,
                                methodIdx,
                                object.getInt("code_item_offset"),
                                codeItemLength,
                                codeItem,
                                codeItemOffsetBias);
                    }
                }
            }
        }
        return stats;
    }

    private RawDexRebuildFile rebuildRawDexDataToDexFile(
            File rawDexData, RawDexHeaderSnapshot snapshot, File outputFile) throws IOException {
        if (rawDexData == null || snapshot == null || snapshot.header == null
                || snapshot.header.length < DEX_HEADER_SIZE || !hasDexMagic(snapshot.header)) {
            return null;
        }
        int fileSize = snapshot.fileSize;
        if (fileSize <= 0 && snapshot.header.length >= 36) {
            fileSize = readIntLe(snapshot.header, 32);
        }
        int dataOff = snapshot.dataOff;
        if (dataOff <= 0 && snapshot.header.length >= 112) {
            dataOff = readIntLe(snapshot.header, 108);
        }
        if (fileSize < DEX_HEADER_SIZE || fileSize > MAX_REBUILT_DEX_SIZE
                || dataOff < DEX_HEADER_SIZE || dataOff >= fileSize) {
            log("raw dexdata header 快照字段无效，无法重建: " + rawDexData.getName()
                    + " fileSize=" + fileSize + " dataOff=" + dataOff);
            return null;
        }
        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
            long usable = parent.getUsableSpace();
            if (usable > 0 && fileSize > usable) {
                log("raw dexdata 重建空间不足，跳过: " + rawDexData.getName()
                        + " need=" + fileSize + " usable=" + usable);
                return null;
            }
        }
        long copyLimit = (long) fileSize - dataOff;
        if (copyLimit <= 0) {
            log("raw dexdata 数据区为空，无法重建: " + rawDexData.getName());
            return null;
        }
        try (RandomAccessFile out = new RandomAccessFile(outputFile, "rw");
                FileInputStream in = new FileInputStream(rawDexData)) {
            out.setLength(fileSize);
            out.seek(0);
            out.write(snapshot.header, 0, Math.min(snapshot.header.length, fileSize));
            out.seek(dataOff);
            byte[] buffer = new byte[REPAIR_IO_BUFFER_SIZE];
            long copied = 0;
            while (copied < copyLimit) {
                int want = (int) Math.min(buffer.length, copyLimit - copied);
                int read = in.read(buffer, 0, want);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                copied += read;
            }
            if (copied <= 0) {
                log("raw dexdata 数据区为空，无法重建: " + rawDexData.getName());
                return null;
            }
            if (copied < rawDexData.length()) {
                log("raw dexdata 超过 header file_size，已截断复制: " + rawDexData.getName()
                        + " copied=" + copied + " raw=" + rawDexData.length());
            }
        }
        normalizeDexHeaderFields(outputFile);
        if (!hasDexMagic(outputFile)) {
            return null;
        }
        return new RawDexRebuildFile(dataOff);
    }

    private boolean hasDexMagic(byte[] dex) {
        return dex != null && dex.length >= 4
                && dex[0] == 'd' && dex[1] == 'e' && dex[2] == 'x' && dex[3] == '\n';
    }

    private boolean hasDexMagic(File dexFile) throws IOException {
        byte[] magic = new byte[4];
        try (FileInputStream in = new FileInputStream(dexFile)) {
            return in.read(magic) == magic.length
                    && magic[0] == 'd' && magic[1] == 'e'
                    && magic[2] == 'x' && magic[3] == '\n';
        }
    }

    private void normalizeDexHeaderFields(byte[] dex) {
        if (dex == null || dex.length < DEX_HEADER_SIZE) {
            return;
        }
        int headerSize = readIntLe(dex, 36);
        if (headerSize < DEX_HEADER_SIZE || headerSize > 0x78) {
            headerSize = DEX_HEADER_SIZE;
        }
        writeIntLe(dex, 32, dex.length);
        writeIntLe(dex, 36, headerSize);
        writeIntLe(dex, 40, DEX_ENDIAN_TAG);
    }

    private void normalizeDexHeaderFields(File dexFile) throws IOException {
        try (RandomAccessFile dex = new RandomAccessFile(dexFile, "rw")) {
            if (dex.length() < DEX_HEADER_SIZE) {
                return;
            }
            int headerSize = readIntLe(dex, 36);
            int version = readDexVersion(dex, 0);
            if (version >= 41) {
                // DEX 041 file_size is the size of this entry, not the whole
                // shared container.  Preserve all container geometry.
                if (headerSize != 0x78) {
                    throw new IOException("DEX 041 header_size 无效: " + headerSize);
                }
                if (readIntLe(dex, 40) != DEX_ENDIAN_TAG) {
                    throw new IOException("DEX 041 endian_tag 无效");
                }
                return;
            }
            if (headerSize < DEX_HEADER_SIZE || headerSize > 0x78) {
                headerSize = DEX_HEADER_SIZE;
            }
            writeIntLe(dex, 32, (int) dex.length());
            writeIntLe(dex, 36, headerSize);
            writeIntLe(dex, 40, DEX_ENDIAN_TAG);
        }
    }

    private DexKey parseDexKey(File dexFile) {
        String name = dexFile != null ? dexFile.getName() : "";
        Matcher matcher = DEX_FILE_PATTERN.matcher(name);
        if (!matcher.matches()) {
            matcher = DEXDATA_FILE_PATTERN.matcher(name);
        }
        if (!matcher.matches()) {
            matcher = DEXRECON_FILE_PATTERN.matcher(name);
        }
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new DexKey(
                    matcher.group(1),
                    Long.parseLong(matcher.group(2)),
                    matcher.group(3));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void mergeRepairStats(RepairStats total, RepairStats one) {
        total.seen += one.seen;
        total.applied += one.applied;
        total.skipped += one.skipped;
        total.duplicates += one.duplicates;
        total.bytesWritten += one.bytesWritten;
    }

    private String repairedDexName(File dexFile, String hash, boolean forceDexExtension) {
        String name = dexFile.getName();
        int dot = name.lastIndexOf('.');
        String prefix = hash.length() > 12 ? hash.substring(0, 12) : hash;
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String extension = forceDexExtension ? ".dex" : (dot >= 0 ? name.substring(dot) : ".dex");
        return base + "_r0dump_repaired_" + prefix + extension;
    }

    private File writeRepairManifest(File stagingDir, List<File> repairedFiles)
            throws Exception {
        JSONArray files = new JSONArray();
        for (File file : repairedFiles) {
            JSONObject entry = new JSONObject();
            entry.put("name", file.getName());
            entry.put("size", file.length());
            entry.put("md5", md5Hex(file));
            files.put(entry);
        }
        JSONObject manifest = new JSONObject();
        manifest.put("schema_version", 1);
        manifest.put("generated_at", System.currentTimeMillis());
        manifest.put("files", files);
        File output = new File(stagingDir, "repair_manifest.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        return output;
    }

    private void validateRepairZip(File zipFile, List<File> expectedFiles) throws IOException {
        Map<String, Long> expected = new HashMap<>();
        for (File file : expectedFiles) {
            if (file == null || !file.isFile() || expected.put(file.getName(), file.length()) != null) {
                throw new IOException("ZIP 输入文件无效或重名: " + file);
            }
        }
        int entries = 0;
        byte[] buffer = new byte[REPAIR_IO_BUFFER_SIZE];
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                String name = entry.getName();
                if (name.isEmpty() || name.contains("/") || name.contains("\\")
                        || name.equals(".") || name.equals("..")) {
                    throw new IOException("ZIP entry 路径无效: " + name);
                }
                Long expectedSize = expected.remove(name);
                if (expectedSize == null || entry.isDirectory()) {
                    throw new IOException("ZIP entry 与输出不匹配: " + name);
                }
                long readBytes = 0;
                try (java.io.InputStream input = zip.getInputStream(entry)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            readBytes += read;
                        }
                    }
                }
                if (readBytes != expectedSize || (entry.getSize() >= 0
                        && entry.getSize() != expectedSize)) {
                    throw new IOException("ZIP entry 大小校验失败: " + name);
                }
                entries++;
            }
        }
        if (!expected.isEmpty() || entries != expectedFiles.size()) {
            throw new IOException("ZIP entry 数量校验失败: " + zipFile);
        }
    }

    private void commitRepairOutput(File stagingDir, File outputDir) throws IOException {
        File parent = outputDir.getParentFile();
        if (parent == null) {
            parent = outputDir.getAbsoluteFile().getParentFile();
        }
        if (parent == null) {
            throw new IOException("无法确定修复输出父目录: " + outputDir);
        }
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建修复输出父目录: " + parent);
        }
        File backup = new File(parent, ".repaired-backup-" + UUID.randomUUID().toString());
        boolean backedUp = false;
        try {
            if (outputDir.exists()) {
                movePath(outputDir, backup, false);
                backedUp = true;
            }
            movePath(stagingDir, outputDir, false);
            if (backedUp) {
                deleteRecursively(backup);
            }
        } catch (IOException failure) {
            if (outputDir.exists()) {
                deleteRecursively(outputDir);
            }
            if (backedUp && backup.exists()) {
                movePath(backup, outputDir, false);
            }
            throw failure;
        }
    }

    private void movePath(File source, File target, boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException unsupported) {
            if (replace) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source.toPath(), target.toPath());
            }
        }
    }

    private void writeRepairZip(File zipFile, List<File> repairedFiles) throws IOException {
        File parent = zipFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Set<String> names = new HashSet<>();
            for (File file : repairedFiles) {
                String entryName = file.getName();
                if (!names.add(entryName)) {
                    throw new IOException("重复 ZIP entry: " + entryName);
                }
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(file.toPath(), zip);
                zip.closeEntry();
            }
        }
    }

    private String md5Hex(byte[] data) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(data);
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }

    private String md5Hex(File file) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[REPAIR_IO_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    md5.update(buffer, 0, read);
                }
            }
        }
        return hex(md5.digest());
    }

    private String hex(byte[] digest) {
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }

    private void applyRepairRecord(RandomAccessFile dex, long dexLength, RepairStats stats,
            Set<String> patchedRanges, int methodIdx, int offset, int len, byte[] codeItem,
            int codeItemOffsetBias) throws IOException {
        stats.seen++;
        long patchedOffset = (long) offset + codeItemOffsetBias;
        if (patchedOffset < 0 || len <= 0 || patchedOffset + len > dexLength) {
            int fallback = findCodeOffsetByMethodIdx(dex, methodIdx);
            if (fallback > 0 && (long) fallback + len <= dexLength) {
                log(getString(R.string.log_offset_fallback, methodIdx, (int) patchedOffset, fallback));
                patchedOffset = fallback;
            }
        }
        if (codeItem.length != len || patchedOffset < 0 || len <= 0
                || patchedOffset + len > dexLength) {
            stats.skipped++;
            log(getString(R.string.log_skip_invalid_record, methodIdx, (int) patchedOffset, len));
            return;
        }
        String key = patchedOffset + ":" + len;
        if (!patchedRanges.add(key)) {
            stats.duplicates++;
            return;
        }
        dex.seek(patchedOffset);
        dex.write(codeItem, 0, len);
        stats.applied++;
        stats.bytesWritten += len;
    }

    private void applyRepairRecord(byte[] dex, RepairStats stats, Set<String> patchedRanges,
            int methodIdx, int offset, int len, byte[] codeItem, int codeItemOffsetBias) {
        stats.seen++;
        long biasedOffsetLong = (long) offset + codeItemOffsetBias;
        int patchedOffset = biasedOffsetLong >= 0 && biasedOffsetLong <= Integer.MAX_VALUE
                ? (int) biasedOffsetLong : -1;
        if (patchedOffset < 0 || len <= 0 || patchedOffset + len > dex.length) {
            int fallback = findCodeOffsetByMethodIdx(dex, methodIdx);
            if (fallback > 0 && fallback + len <= dex.length) {
                log(getString(R.string.log_offset_fallback, methodIdx, patchedOffset, fallback));
                patchedOffset = fallback;
            }
        }
        if (codeItem.length != len || patchedOffset < 0 || len <= 0
                || patchedOffset + len > dex.length) {
            stats.skipped++;
            log(getString(R.string.log_skip_invalid_record, methodIdx, patchedOffset, len));
            return;
        }
        String key = patchedOffset + ":" + len;
        if (!patchedRanges.add(key)) {
            stats.duplicates++;
            return;
        }
        System.arraycopy(codeItem, 0, dex, patchedOffset, len);
        stats.applied++;
        stats.bytesWritten += len;
    }


    private int findCodeOffsetByMethodIdx(RandomAccessFile dex, int targetMethodIdx)
            throws IOException {
        long dexLength = dex.length();
        if (dexLength < DEX_HEADER_SIZE) {
            return 0;
        }
        dex.seek(0);
        if (dex.read() != 'd' || dex.read() != 'e' || dex.read() != 'x'
                || dex.read() != '\n') {
            return 0;
        }
        int classDefsSize = readIntLe(dex, 96);
        int classDefsOff = readIntLe(dex, 100);
        long classDefsEnd = (long) classDefsOff + (long) classDefsSize * 32L;
        if (classDefsSize < 0 || classDefsOff <= 0 || classDefsEnd > dexLength) {
            return 0;
        }
        for (int classId = 0; classId < classDefsSize; classId++) {
            long classDefOff = (long) classDefsOff + (long) classId * 32L;
            int classDataOff = readIntLe(dex, classDefOff + 24);
            int codeOff = findCodeOffsetInClassData(
                    dex, classDataOff, targetMethodIdx, dexLength);
            if (codeOff > 0) {
                return codeOff;
            }
        }
        return 0;
    }

    private int findCodeOffsetInClassData(RandomAccessFile dex, long classDataOff,
            int targetMethodIdx, long dexLength) throws IOException {
        if (classDataOff <= 0 || classDataOff >= dexLength) {
            return 0;
        }
        long[] cursor = new long[] { classDataOff };
        int staticFields = readUleb128(dex, cursor, dexLength);
        int instanceFields = readUleb128(dex, cursor, dexLength);
        int directMethods = readUleb128(dex, cursor, dexLength);
        int virtualMethods = readUleb128(dex, cursor, dexLength);
        for (int i = 0; i < staticFields + instanceFields; i++) {
            readUleb128(dex, cursor, dexLength);
            readUleb128(dex, cursor, dexLength);
        }
        int methodIdx = 0;
        for (int i = 0; i < directMethods + virtualMethods; i++) {
            methodIdx += readUleb128(dex, cursor, dexLength);
            readUleb128(dex, cursor, dexLength);
            int codeOff = readUleb128(dex, cursor, dexLength);
            if (methodIdx == targetMethodIdx) {
                return codeOff;
            }
        }
        return 0;
    }

    private int findCodeOffsetByMethodIdx(byte[] dex, int targetMethodIdx) {
        if (dex == null || dex.length < DEX_HEADER_SIZE
                || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') {
            return 0;
        }
        int classDefsSize = readIntLe(dex, 96);
        int classDefsOff = readIntLe(dex, 100);
        if (classDefsSize < 0 || classDefsOff <= 0 || classDefsOff + classDefsSize * 32 > dex.length) {
            return 0;
        }
        for (int classId = 0; classId < classDefsSize; classId++) {
            int classDefOff = classDefsOff + classId * 32;
            int classDataOff = readIntLe(dex, classDefOff + 24);
            int codeOff = findCodeOffsetInClassData(dex, classDataOff, targetMethodIdx);
            if (codeOff > 0) {
                return codeOff;
            }
        }
        return 0;
    }

    private int findCodeOffsetInClassData(byte[] dex, int classDataOff, int targetMethodIdx) {
        if (classDataOff <= 0 || classDataOff >= dex.length) {
            return 0;
        }
        int[] cursor = new int[] { classDataOff };
        int staticFields = readUleb128(dex, cursor);
        int instanceFields = readUleb128(dex, cursor);
        int directMethods = readUleb128(dex, cursor);
        int virtualMethods = readUleb128(dex, cursor);
        for (int i = 0; i < staticFields + instanceFields; i++) {
            readUleb128(dex, cursor);
            readUleb128(dex, cursor);
        }
        int methodIdx = 0;
        for (int i = 0; i < directMethods + virtualMethods; i++) {
            methodIdx += readUleb128(dex, cursor);
            readUleb128(dex, cursor);
            int codeOff = readUleb128(dex, cursor);
            if (methodIdx == targetMethodIdx) {
                return codeOff;
            }
        }
        return 0;
    }

    private int readUleb128(byte[] data, int[] cursor) {
        int result = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            if (cursor[0] >= data.length) {
                return 0;
            }
            int b = data[cursor[0]++] & 0xff;
            result |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        return result;
    }

    private int readUleb128(RandomAccessFile data, long[] cursor, long length) throws IOException {
        int result = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            if (cursor[0] >= length) {
                return 0;
            }
            data.seek(cursor[0]++);
            int b = data.read();
            if (b < 0) {
                return 0;
            }
            result |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        return result;
    }

    private void validateDexHeader(byte[] dex, File dexFile) throws IOException {
        if (dex.length < DEX_HEADER_SIZE
                || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') {
            throw new IOException(getString(R.string.error_invalid_dex, dexFile));
        }
        int fileSize = Integer.toUnsignedLong(readIntLe(dex, 32)) > Integer.MAX_VALUE
                ? -1 : readIntLe(dex, 32);
        int headerSize = readIntLe(dex, 36);
        int mapOff = readIntLe(dex, 52);
        if (fileSize != dex.length || headerSize != DEX_HEADER_SIZE
                || mapOff <= 0 || mapOff > dex.length - 4) {
            throw new IOException(getString(R.string.error_invalid_dex, dexFile));
        }
    }

    private void validateDexHeader(File repairedDex, File sourceDex) throws IOException {
        try {
            validateDexStructure(repairedDex, true);
        } catch (IOException e) {
            throw new IOException(getString(R.string.error_invalid_dex, sourceDex)
                    + ": " + e.getMessage(), e);
        }
    }

    private void repairDexHeader(byte[] dex) throws Exception {
        normalizeDexHeaderFields(dex);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(dex, 32, dex.length - 32);
        byte[] signature = sha1.digest();
        System.arraycopy(signature, 0, dex, 12, 20);

        Adler32 adler32 = new Adler32();
        adler32.update(dex, 12, dex.length - 12);
        writeIntLe(dex, 8, (int) adler32.getValue());
    }

    private void repairDexHeader(File dexFile) throws Exception {
        try (RandomAccessFile dex = new RandomAccessFile(dexFile, "rw")) {
            long length = dex.length();
            if (length < DEX_HEADER_SIZE) {
                throw new IOException(getString(R.string.error_invalid_dex, dexFile));
            }
            List<Long> entryOffsets = findDexEntryOffsets(dex, length);
            for (long entryOffset : entryOffsets) {
                long entrySize = readUnsignedIntLe(dex, entryOffset + 32);
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                updateDigest(dex, entryOffset + 32, entrySize - 32, sha1);
                dex.seek(entryOffset + 12);
                dex.write(sha1.digest());

                Adler32 adler32 = new Adler32();
                updateChecksum(dex, entryOffset + 12, entrySize - 12, adler32);
                writeIntLe(dex, entryOffset + 8, (int) adler32.getValue());
            }
        }
    }

    private List<Long> findDexEntryOffsets(RandomAccessFile dex, long length) throws IOException {
        ArrayList<Long> offsets = new ArrayList<>();
        int version = readDexVersion(dex, 0);
        if (version < 35 || version > 41) {
            throw new IOException("不支持的 DEX 版本: " + version);
        }
        if (version < 41) {
            offsets.add(0L);
            return offsets;
        }
        long offset = 0;
        for (int entry = 0; entry < 1024 && offset < length; entry++) {
            requireDexMagic(dex, offset, length);
            int entryVersion = readDexVersion(dex, offset);
            long headerSize = readUnsignedIntLe(dex, offset + 36);
            long fileSize = readUnsignedIntLe(dex, offset + 32);
            long containerSize = readUnsignedIntLe(dex, offset + 112);
            long headerOffset = readUnsignedIntLe(dex, offset + 116);
            if (entryVersion != 41 || headerSize != 0x78 || headerOffset != offset
                    || containerSize != length || fileSize < 0x78
                    || fileSize > length - offset) {
                throw new IOException("DEX 041 容器条目无效: entry=" + entry
                        + " offset=" + offset + " fileSize=" + fileSize
                        + " containerSize=" + containerSize + " headerOffset=" + headerOffset);
            }
            offsets.add(offset);
            offset += fileSize;
        }
        if (offset != length || offsets.isEmpty()) {
            throw new IOException("DEX 041 容器未完整覆盖文件: end=" + offset
                    + " length=" + length);
        }
        return offsets;
    }

    private void validateDexStructure(File dexFile, boolean verifyIntegrity) throws IOException {
        try (RandomAccessFile dex = new RandomAccessFile(dexFile, "r")) {
            long length = dex.length();
            if (length < DEX_HEADER_SIZE || length > MAX_REBUILT_DEX_SIZE) {
                throw new IOException("文件大小无效: " + length);
            }
            List<Long> entries = findDexEntryOffsets(dex, length);
            boolean container = entries.size() > 1 || readDexVersion(dex, 0) == 41;
            for (long entryOffset : entries) {
                validateDexEntry(dex, length, entryOffset, container, verifyIntegrity);
            }
        }
    }

    private void validateDexEntry(RandomAccessFile dex, long containerLength, long entryOffset,
            boolean container, boolean verifyIntegrity) throws IOException {
        requireDexMagic(dex, entryOffset, containerLength);
        int version = readDexVersion(dex, entryOffset);
        long fileSize = readUnsignedIntLe(dex, entryOffset + 32);
        long headerSize = readUnsignedIntLe(dex, entryOffset + 36);
        long endian = readUnsignedIntLe(dex, entryOffset + 40);
        long dataLimit = container ? containerLength : fileSize;
        long expectedHeaderSize = version >= 41 ? 0x78 : DEX_HEADER_SIZE;
        if (headerSize != expectedHeaderSize || endian != DEX_ENDIAN_TAG
                || fileSize < expectedHeaderSize || fileSize > containerLength - entryOffset
                || (!container && (entryOffset != 0 || fileSize != containerLength))) {
            throw new IOException("DEX header 字段无效: offset=" + entryOffset
                    + " fileSize=" + fileSize + " headerSize=" + headerSize);
        }

        validateDexSection(dex, entryOffset, dataLimit, 56, 60, 4, "string_ids");
        validateDexSection(dex, entryOffset, dataLimit, 64, 68, 4, "type_ids");
        validateDexSection(dex, entryOffset, dataLimit, 72, 76, 12, "proto_ids");
        validateDexSection(dex, entryOffset, dataLimit, 80, 84, 8, "field_ids");
        validateDexSection(dex, entryOffset, dataLimit, 88, 92, 8, "method_ids");
        validateDexSection(dex, entryOffset, dataLimit, 96, 100, 32, "class_defs");
        validateDexSection(dex, entryOffset, dataLimit, 104, 108, 1, "data");

        long mapOff = readUnsignedIntLe(dex, entryOffset + 52);
        if ((mapOff & 3L) != 0 || mapOff == 0 || mapOff > dataLimit - 4) {
            throw new IOException("map_off 越界: " + mapOff + "/" + dataLimit);
        }
        long mapCount = readUnsignedIntLe(dex, mapOff);
        long mapBytes = 4L + mapCount * 12L;
        if (mapCount == 0 || mapCount > 512 || mapBytes > dataLimit - mapOff) {
            throw new IOException("map_list 无效: count=" + mapCount + " off=" + mapOff);
        }
        boolean hasMapSelf = false;
        long previousOffset = -1;
        for (long i = 0; i < mapCount; i++) {
            long item = mapOff + 4L + i * 12L;
            int type = readUnsignedShortLe(dex, item);
            long count = readUnsignedIntLe(dex, item + 4);
            long offset = readUnsignedIntLe(dex, item + 8);
            if (!isKnownDexMapType(type) || count == 0 || offset >= dataLimit
                    || offset <= previousOffset) {
                throw new IOException("map_item 无效: index=" + i + " type=0x"
                        + Integer.toHexString(type) + " count=" + count + " off=" + offset);
            }
            if (type == 0x1000) {
                hasMapSelf = count == 1 && offset == mapOff;
            }
            previousOffset = offset;
        }
        if (!hasMapSelf) {
            throw new IOException("map_list 缺少自身条目");
        }

        if (verifyIntegrity) {
            MessageDigest sha1;
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (Exception e) {
                throw new IOException("SHA-1 初始化失败", e);
            }
            updateDigest(dex, entryOffset + 32, fileSize - 32, sha1);
            byte[] expectedSignature = new byte[20];
            dex.seek(entryOffset + 12);
            dex.readFully(expectedSignature);
            if (!Arrays.equals(expectedSignature, sha1.digest())) {
                throw new IOException("DEX SHA-1 不匹配: offset=" + entryOffset);
            }
            Adler32 adler32 = new Adler32();
            updateChecksum(dex, entryOffset + 12, fileSize - 12, adler32);
            long expectedChecksum = readUnsignedIntLe(dex, entryOffset + 8);
            if (expectedChecksum != adler32.getValue()) {
                throw new IOException("DEX Adler32 不匹配: offset=" + entryOffset);
            }
        }
    }

    private void validateDexSection(RandomAccessFile dex, long entryOffset, long dataLimit,
            int sizeField, int offField, int itemWidth, String label) throws IOException {
        long count = readUnsignedIntLe(dex, entryOffset + sizeField);
        long offset = readUnsignedIntLe(dex, entryOffset + offField);
        if (count == 0) {
            if (offset != 0) {
                throw new IOException(label + " size=0 但 offset=" + offset);
            }
            return;
        }
        if (offset == 0 || (itemWidth > 1 && (offset & 3L) != 0)
                || count > (dataLimit - Math.min(offset, dataLimit)) / itemWidth
                || offset > dataLimit - count * itemWidth) {
            throw new IOException(label + " 越界: count=" + count + " off=" + offset
                    + " limit=" + dataLimit);
        }
    }

    private int readDexVersion(RandomAccessFile dex, long offset) throws IOException {
        requireDexMagic(dex, offset, dex.length());
        dex.seek(offset + 4);
        int a = dex.read();
        int b = dex.read();
        int c = dex.read();
        int terminator = dex.read();
        if (a < '0' || a > '9' || b < '0' || b > '9' || c < '0' || c > '9'
                || terminator != 0) {
            throw new IOException("DEX 版本 magic 无效: offset=" + offset);
        }
        return (a - '0') * 100 + (b - '0') * 10 + (c - '0');
    }

    private void requireDexMagic(RandomAccessFile dex, long offset, long length) throws IOException {
        if (offset < 0 || offset > length - 8) {
            throw new IOException("DEX magic 越界: " + offset);
        }
        dex.seek(offset);
        if (dex.read() != 'd' || dex.read() != 'e' || dex.read() != 'x'
                || dex.read() != '\n') {
            throw new IOException("DEX magic 无效: offset=" + offset);
        }
    }

    private long readUnsignedIntLe(RandomAccessFile data, long offset) throws IOException {
        return Integer.toUnsignedLong(readIntLe(data, offset));
    }

    private int readUnsignedShortLe(RandomAccessFile data, long offset) throws IOException {
        data.seek(offset);
        int low = data.read();
        int high = data.read();
        if ((low | high) < 0) {
            throw new IOException("Unexpected EOF while reading little-endian short at " + offset);
        }
        return low | (high << 8);
    }

    private boolean isKnownDexMapType(int type) {
        return (type >= 0x0000 && type <= 0x0008)
                || (type >= 0x1000 && type <= 0x1003)
                || (type >= 0x2000 && type <= 0x2006)
                || type == 0xf000;
    }

    private void updateDigest(RandomAccessFile file, long offset, long length,
            MessageDigest digest) throws IOException {
        byte[] buffer = new byte[REPAIR_IO_BUFFER_SIZE];
        long remaining = length;
        file.seek(offset);
        while (remaining > 0) {
            int want = (int) Math.min(buffer.length, remaining);
            int read = file.read(buffer, 0, want);
            if (read < 0) {
                break;
            }
            digest.update(buffer, 0, read);
            remaining -= read;
        }
    }

    private void updateChecksum(RandomAccessFile file, long offset, long length,
            Adler32 checksum) throws IOException {
        byte[] buffer = new byte[REPAIR_IO_BUFFER_SIZE];
        long remaining = length;
        file.seek(offset);
        while (remaining > 0) {
            int want = (int) Math.min(buffer.length, remaining);
            int read = file.read(buffer, 0, want);
            if (read < 0) {
                break;
            }
            checksum.update(buffer, 0, read);
            remaining -= read;
        }
    }

    private int readIntLe(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private int readIntLe(RandomAccessFile data, long offset) throws IOException {
        data.seek(offset);
        int b0 = data.read();
        int b1 = data.read();
        int b2 = data.read();
        int b3 = data.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new IOException("Unexpected EOF while reading little-endian int at " + offset);
        }
        return (b0 & 0xff)
                | ((b1 & 0xff) << 8)
                | ((b2 & 0xff) << 16)
                | ((b3 & 0xff) << 24);
    }

    private void writeIntLe(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
    }

    private void writeIntLe(RandomAccessFile data, long offset, int value) throws IOException {
        data.seek(offset);
        data.write(value & 0xff);
        data.write((value >> 8) & 0xff);
        data.write((value >> 16) & 0xff);
        data.write((value >> 24) & 0xff);
    }


    private String packageName() {
        String pkg = text(mConfig.targetPackage);
        return "*".equals(pkg) ? "" : pkg;
    }

    private String text(String value) {
        return value != null ? value.trim() : "";
    }

    private String cleanAutomationExtra(String value) {
        String out = text(value);
        if (out.isEmpty() || "null".equalsIgnoreCase(out)) {
            return "";
        }
        // Shell/Windows adb quoting mistakes can shift a flag such as "--ez" into a string
        // extra. Android package/process names and output values used here should not begin
        // with a command-line option marker, so ignore these polluted values instead of
        // persisting them into Settings.Global.
        if (out.startsWith("--")) {
            return "";
        }
        return out;
    }

    private String normalizeLimitDefault(String value, String previousDefault) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty() || "0".equals(text)) {
            return previousDefault;
        }
        return text;
    }

    private void log(String message) {
        mLastActionValue = message;
        notifyComposeChanged();
        Log.i(LOG_TAG, "manager " + message);
    }
}
