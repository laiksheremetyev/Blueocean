package com.blueocean.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Dialog;
import android.net.Uri;
import android.provider.MediaStore;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.text.DateFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final String TAG = "BlueOcean";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final int CAMERA_PERMISSION_REQUEST = 1002;
    private static final int WATERMARK_PICK_REQUEST = 1003;
    private static final long PRIVATE_GATE_HOLD_MS = 3_200L;
    private static final int COLOR_BG_TOP = Color.rgb(9, 18, 31);
    private static final int COLOR_BG_BOTTOM = Color.rgb(5, 31, 53);
    private static final int COLOR_PANEL = Color.rgb(18, 28, 43);
    private static final int COLOR_PANEL_STROKE = Color.rgb(40, 62, 84);
    private static final int COLOR_BRASS = Color.rgb(10, 132, 255);
    private static final int COLOR_COPPER = Color.rgb(0, 180, 216);
    private static final int COLOR_TEXT = Color.rgb(242, 248, 255);
    private static final int COLOR_MUTED = Color.rgb(145, 163, 184);
    private static final int COLOR_TEAL = Color.rgb(32, 211, 238);
    private static final int COLOR_NOTES_BG = Color.rgb(7, 13, 22);
    private static final int COLOR_NOTE_CARD = Color.rgb(237, 247, 255);
    private static final int COLOR_NOTE_CARD_ALT = Color.rgb(194, 224, 246);
    private static final int COLOR_NOTE_TEXT = Color.rgb(35, 35, 38);

    private PinStore pinStore;
    private PatternStore patternStore;
    private VaultStore vaultStore;
    private PhotoStore photoStore;
    private BackupQueue backupQueue;
    private BackupConfigStore backupConfigStore;
    private DeviceIdentityStore deviceIdentityStore;
    private NoteStore noteStore;
    private EditText pendingLocationInput;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraSession;
    private ImageReader imageReader;
    private AspectTextureView cameraPreview;
    private Size previewSize;
    private Size jpegSize;
    private boolean flashSupported;
    private boolean flashEnabled;
    private int sensorOrientation;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageView flashButton;
    private EditText cameraCommentInput;
    private EditText cameraLocationInput;
    private String activePin = "";
    private Runnable backTarget;
    private String currentScreen = "entry";
    private String mapReturnScreen = "vault";
    private String pendingWatermarkSlot = "top";
    private AlertDialog activeWatermarkDialog;
    private int currentTgFolderId = -1;
    private int tgRootFolderId = -1;
    private String currentMegaFolder = "";
    private MapView activeMapView;
    private TextView mapCoordsText;
    private TextView mapUserBubble;
    private View mapUserPin;
    private int optionsScrollY;
    private boolean useFrontCamera;
    private float currentZoom = 1f;
    private float maxDigitalZoom = 1f;
    private Rect sensorArraySize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pinStore = new PinStore(this);
        patternStore = new PatternStore(this);
        vaultStore = new VaultStore(this);
        photoStore = new PhotoStore(this);
        backupQueue = new BackupQueue(this);
        backupConfigStore = new BackupConfigStore(this);
        deviceIdentityStore = new DeviceIdentityStore(this);
        noteStore = new NoteStore(this);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        showEntryScreen();
    }

    private void showEntryScreen() {
        activePin = "";
        if ("Калькулятор".equals(optionPrefs().getString("entryMode", "Калькулятор"))) {
            try {
                pinStore.ensureDefault("1234");
            } catch (Exception ignored) {
            }
            showCalculatorUnlock();
        } else {
            showNotesHome();
        }
    }

    private void showNotesHome() {
        currentScreen = "notes";
        setBackTarget(null);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        root.setPadding(dp(20), dp(30), dp(20), dp(18));
        root.setBackgroundColor(themedNotesBackground());

        List<NoteEntry> notes = noteStore.readNotes();
        int noteCount = notes.size();

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(66), 0, dp(8));
        root.addView(brand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ImageView icon = new ImageView(this);
        icon.setImageResource(launcherIconResource());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMargins(0, 0, dp(10), 0);
        brand.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText("Blue Ocean");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        installVeryLongPress(title);
        brand.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView count = new TextView(this);
        count.setText(noteCount + " " + noteWord(noteCount));
        count.setTextColor(Color.rgb(160, 160, 165));
        count.setTextSize(16);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, 0, 0, dp(110));
        root.addView(count, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView menu = notesIcon("☰", 30);
        menu.setOnClickListener(view -> toast("Папки заметок пока пустые"));
        toolbar.addView(menu);

        TextView spacer = new TextView(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView search = notesIcon("⌕", 34);
        search.setOnClickListener(view -> showSearchNotes());
        toolbar.addView(search);
        TextView more = notesIcon("⋮", 32);
        more.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Blue Ocean")
                .setItems(new String[]{"Новая заметка", "Обновить"}, (dialog, which) -> {
                    if (which == 0) {
                        showAddNote();
                    } else {
                        showNotesHome();
                    }
                })
                .show());
        toolbar.addView(more);

        TextView year = new TextView(this);
        year.setText("2026");
        year.setTextColor(Color.WHITE);
        year.setTextSize(18);
        year.setTypeface(Typeface.DEFAULT_BOLD);
        year.setPadding(0, dp(48), 0, dp(18));
        root.addView(year, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        List<NoteEntry> visible = new ArrayList<>(notes);

        for (int i = 0; i < visible.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            addNoteCard(row, visible.get(i), i % 4 == 0);
            if (i + 1 < visible.size()) {
                addNoteCard(row, visible.get(i + 1), i % 4 != 0);
            } else {
                TextView empty = new TextView(this);
                row.addView(empty, new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пусто");
            empty.setTextColor(Color.rgb(150, 150, 154));
            empty.setTextSize(18);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(80), 0, dp(80));
            grid.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        TextView compose = notesFab("✎");
        compose.setOnClickListener(view -> showAddNote());
        root.addView(compose);

        setContentView(wrap(root));
    }

    private void showAddNote() {
        LinearLayout root = baseScreen("Заголовок");

        EditText title = textInput("Заголовок");
        EditText body = textInput("Текст заметки");
        body.setMinLines(4);
        body.setMaxLines(8);
        root.addView(title);
        root.addView(body);

        Button save = primaryButton("Сохранить заметку");
        root.addView(save);
        save.setOnClickListener(view -> {
            String titleValue = title.getText().toString();
            String bodyValue = body.getText().toString();
            if (titleValue.trim().isEmpty() && bodyValue.trim().isEmpty()) {
                toast("Заметка пустая");
                return;
            }
            noteStore.addNote(titleValue, bodyValue);
            showNotesHome();
        });

        root.addView(backButton());
        setContentView(wrap(root));
    }

    private String noteWord(int count) {
        int mod10 = Math.abs(count) % 10;
        int mod100 = Math.abs(count) % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "заметка";
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "заметки";
        }
        return "заметок";
    }

    private void showSearchNotes() {
        LinearLayout root = baseScreen("Поиск");
        EditText query = textInput("Поиск по заметкам");
        query.setSingleLine(true);
        root.addView(query);

        Button find = primaryButton("Найти");
        find.setOnClickListener(view -> {
            String needle = query.getText().toString().trim().toLowerCase(Locale.ROOT);
            LinearLayout results = baseScreen("Результаты");
            for (NoteEntry note : noteStore.readNotes()) {
                String haystack = (note.title + "\n" + note.body).toLowerCase(Locale.ROOT);
                if (needle.isEmpty() || haystack.contains(needle)) {
                    addPanel(results, note.title.isEmpty() ? "Без названия" : note.title, note.body);
                }
            }
            Button back = secondaryButton("Назад к заметкам");
            back.setOnClickListener(v -> showNotesHome());
            results.addView(back);
            setContentView(wrap(results));
        });
        root.addView(find);
        root.addView(backButton());
        setContentView(wrap(root));
    }

    private void showEditNote(NoteEntry note) {
        if (note.id.startsWith("demo-")) {
            LinearLayout root = baseScreen(note.title.isEmpty() ? "Заголовок" : note.title);
            addPanel(root, "Заметка", note.body);
            Button copy = primaryButton("Создать похожую");
            copy.setOnClickListener(view -> {
                noteStore.addNote(note.title, note.body);
                showNotesHome();
            });
            root.addView(copy);
            root.addView(backButton());
            setContentView(wrap(root));
            return;
        }

        LinearLayout root = baseScreen("Заголовок");
        EditText title = textInput("Заголовок");
        title.setText(note.title);
        EditText body = textInput("Текст заметки");
        body.setText(note.body);
        body.setMinLines(5);
        body.setMaxLines(10);
        root.addView(title);
        root.addView(body);

        Button save = primaryButton("Сохранить");
        save.setOnClickListener(view -> {
            noteStore.updateNote(note.id, title.getText().toString(), body.getText().toString());
            showNotesHome();
        });
        root.addView(save);

        Button delete = secondaryButton("Удалить заметку");
        delete.setOnClickListener(view -> {
            noteStore.deleteNote(note.id);
            showNotesHome();
        });
        root.addView(delete);
        root.addView(backButton());
        setContentView(wrap(root));
    }

    private TextView notesIcon(String value, int sizeSp) {
        TextView icon = new TextView(this);
        icon.setText(value);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(sizeSp);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(dp(8), dp(4), dp(8), dp(4));
        return icon;
    }

    private TextView notesFab(String value) {
        TextView fab = new TextView(this);
        fab.setText(value);
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(34);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(roundDrawable(themeAccent(), 999, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(74), dp(74));
        params.gravity = Gravity.RIGHT;
        params.setMargins(0, dp(18), dp(4), 0);
        fab.setLayoutParams(params);
        return fab;
    }

    private void addNoteCard(LinearLayout row, NoteEntry note, boolean alt) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.setMargins(0, 0, dp(16), dp(28));
        row.addView(column, columnParams);

        TextView preview = new TextView(this);
        preview.setText(note.body == null || note.body.trim().isEmpty() ? "Пустая заметка" : note.body.trim());
        preview.setTextColor(themeNoteTextColor());
        preview.setTextSize(16);
        preview.setLineSpacing(dp(6), 1f);
        preview.setMaxLines(8);
        preview.setPadding(dp(18), dp(18), dp(18), dp(18));
        preview.setBackground(roundDrawable(themeNoteCardColor(alt), 20, 0, Color.TRANSPARENT));
        column.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
        ));
        preview.setOnClickListener(view -> showEditNote(note));

        TextView title = new TextView(this);
        title.setText(note.title == null || note.title.trim().isEmpty() ? "Без названия" : note.title.trim());
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(4));
        title.setOnClickListener(view -> showEditNote(note));
        column.addView(title);

        TextView date = new TextView(this);
        date.setText(DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(note.createdAtMs)));
        date.setTextColor(Color.rgb(150, 150, 154));
        date.setTextSize(15);
        date.setGravity(Gravity.CENTER);
        column.addView(date);
    }

    private void openPrivateGate() {
        try {
            pinStore.ensureDefault("1234");
        } catch (Exception e) {
            toast("Не удалось подготовить вход");
            return;
        }
        if ("Заметки".equals(optionPrefs().getString("entryMode", "Калькулятор"))) {
            openPinGate();
        } else {
            showCalculatorUnlock();
        }
    }

    private void openPinGate() {
        if (pinStore.hasPin()) {
            showUnlock();
        } else {
            showSetupPin();
        }
    }

    private void showSetupPattern() {
        LinearLayout root = baseScreen("Blue Ocean");

        TextView hint = text("Задайте графический ключ.");
        root.addView(hint);

        PatternPad pad = addPatternPad(root);
        TextView status = text("Выберите минимум 4 точки.");
        root.addView(status);

        final String[] first = {""};

        Button next = primaryButton("Запомнить рисунок");
        root.addView(next);
        next.setOnClickListener(view -> {
            String pattern = pad.pattern();
            if (pattern.length() < 4) {
                toast("Нужно минимум 4 точки");
                return;
            }
            if (first[0].isEmpty()) {
                first[0] = pattern;
                pad.clear();
                status.setText("Повторите тот же рисунок.");
                next.setText("Сохранить ключ");
                return;
            }
            if (!first[0].equals(pattern)) {
                first[0] = "";
                pad.clear();
                status.setText("Рисунок не совпал. Задайте ключ заново.");
                next.setText("Запомнить рисунок");
                return;
            }
            try {
                patternStore.setup(pattern);
                toast("Графический ключ сохранен");
                openPinGate();
            } catch (Exception e) {
                toast("Не удалось сохранить ключ");
            }
        });

        Button clear = secondaryButton("Очистить рисунок");
        clear.setOnClickListener(view -> pad.clear());
        root.addView(clear);

        root.addView(backButton());
        setContentView(wrap(root));
    }

    private void showPatternUnlock() {
        LinearLayout root = baseScreen("Blue Ocean");

        TextView hint = text("Повторите графический ключ, затем откроется PIN.");
        root.addView(hint);

        PatternPad pad = addPatternPad(root);

        Button unlock = primaryButton("Продолжить");
        root.addView(unlock);
        unlock.setOnClickListener(view -> {
            long lockedFor = patternStore.lockedForMs();
            if (lockedFor > 0L) {
                toast("Повторите через " + Math.max(1, lockedFor / 1000) + " сек.");
                return;
            }
            try {
                if (patternStore.verify(pad.pattern())) {
                    pad.clear();
                    openPinGate();
                } else {
                    pad.clear();
                    toast("Рисунок не совпал");
                }
            } catch (Exception e) {
                toast("Проверка не удалась");
            }
        });

        Button clear = secondaryButton("Очистить рисунок");
        clear.setOnClickListener(view -> pad.clear());
        root.addView(clear);

        root.addView(backButton());
        setContentView(wrap(root));
    }

    private void showCalculatorUnlock() {
        currentScreen = "calculator";
        setBackTarget(this::showNotesHome);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM);
        root.setPadding(dp(12), dp(28), dp(12), safeBottomPad());
        root.setBackgroundColor(Color.BLACK);

        TextView display = new TextView(this);
        display.setText("0");
        display.setTextColor(Color.WHITE);
        display.setTextSize(58);
        display.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        display.setSingleLine(true);
        root.addView(display, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        final StringBuilder typed = new StringBuilder();
        String[][] keys = {
                {"AC", "+/-", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"0", ".", "="}
        };

        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(86)
            ));
            for (String key : rowKeys) {
                TextView cell = calculatorKey(key);
                cell.setOnClickListener(view -> handleCalculatorKey(key, typed, display));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), "0".equals(key) ? 2f : 1f);
                params.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(cell, params);
            }
        }

        setContentView(root);
    }

    private TextView calculatorKey(String key) {
        TextView cell = new TextView(this);
        cell.setText(key);
        cell.setGravity(Gravity.CENTER);
        cell.setTextSize(30);
        cell.setTypeface(Typeface.DEFAULT_BOLD);
        boolean operator = "÷×-+=".contains(key);
        boolean utility = "AC".equals(key) || "+/-".equals(key) || "%".equals(key);
        int fill = operator ? Color.rgb(255, 149, 0) : utility ? Color.rgb(214, 214, 214) : Color.rgb(78, 78, 78);
        int textColor = utility ? Color.BLACK : Color.WHITE;
        cell.setTextColor(textColor);
        cell.setBackground(roundDrawable(fill, 999, 0, Color.TRANSPARENT));
        return cell;
    }

    private void handleCalculatorKey(String key, StringBuilder typed, TextView display) {
        if ("AC".equals(key)) {
            typed.setLength(0);
            display.setText("0");
            return;
        }
        if ("=".equals(key)) {
            String value = typed.toString();
            typed.setLength(0);
            display.setText("0");
            unlockWithPin(value);
            return;
        }
        if ("0123456789".contains(key)) {
            if (typed.length() < 12) {
                typed.append(key);
            }
            display.setText(typed.length() == 0 ? "0" : typed.toString());
            return;
        }
        display.setText("0");
    }

    private void unlockWithPin(String value) {
        long lockedFor = pinStore.lockedForMs();
        if (lockedFor > 0L) {
            toast("Повторите через " + Math.max(1, lockedFor / 1000) + " сек.");
            return;
        }

        try {
            if (pinStore.verify(value)) {
                activePin = value;
                showVault();
            } else {
                toast("Ошибка");
            }
        } catch (Exception e) {
            toast("Не удалось открыть");
        }
    }

    private void showSetupPin() {
        currentScreen = "setupPin";
        LinearLayout root = baseScreen("Blue Ocean");

        TextView hint = text("Выберите PIN минимум из 4 цифр. Он открывает зашифрованный фото-отсек на этом устройстве.");
        root.addView(hint);

        EditText pin = pinInput("PIN");
        EditText confirm = pinInput("Повторите PIN");
        root.addView(pin);
        root.addView(confirm);

        Button save = primaryButton("Сохранить PIN");
        root.addView(save);
        save.setOnClickListener(view -> {
            String first = pin.getText().toString();
            String second = confirm.getText().toString();
            if (first.length() < 4) {
                toast("PIN должен быть минимум из 4 цифр");
                return;
            }
            if (!first.equals(second)) {
                toast("PIN не совпадает");
                return;
            }
            try {
                pinStore.setup(first);
                activePin = first;
                pin.setText("");
                confirm.setText("");
                showVault();
            } catch (Exception e) {
                toast("Не удалось сохранить PIN");
            }
        });

        root.addView(backButton());
        setContentView(wrap(root));
    }

    private void showUnlock() {
        currentScreen = "unlock";
        LinearLayout root = baseScreen("Blue Ocean");

        TextView hint = text("Введите PIN, чтобы открыть зашифрованное хранилище.");
        root.addView(hint);

        EditText pin = pinInput("PIN");
        root.addView(pin);

        Button unlock = primaryButton("Разблокировать");
        root.addView(unlock);
        unlock.setOnClickListener(view -> {
            long lockedFor = pinStore.lockedForMs();
            if (lockedFor > 0L) {
                toast("Повторите через " + Math.max(1, lockedFor / 1000) + " сек.");
                return;
            }

            try {
                String value = pin.getText().toString();
                if (pinStore.verify(value)) {
                    activePin = value;
                    pin.setText("");
                    showVault();
                } else {
                    pin.setText("");
                    toast("Неверный PIN");
                }
            } catch (Exception e) {
                toast("Не удалось открыть");
            }
        });

        root.addView(backButton());
        setContentView(wrap(root));
    }

    private void showVault() {
        currentScreen = "vault";
        setBackTarget(this::showNotesHome);
        LinearLayout root = baseScreen("Blue Ocean");

        Button gallery = secondaryButton("🖼  Галерея и Облако");
        root.addView(gallery);
        gallery.setOnClickListener(view -> showCloudAndGallery());

        Button camera = primaryButton("Камера");
        camera.setOnClickListener(view -> showCamera());
        root.addView(camera);

        Button map = secondaryButton("Карта");
        map.setOnClickListener(view -> showCameraMapPanel());
        root.addView(map);

        Button options = secondaryButton("Опции");
        options.setOnClickListener(view -> showOptions());
        root.addView(options);

        Button lock = primaryButton("Закрыть");
        root.addView(lock);
        lock.setOnClickListener(view -> showEntryScreen());

        setContentView(wrap(root));
    }

    private void showCamera() {
        currentScreen = "camera";
        setBackTarget(() -> {
            stopCamera();
            showVault();
        });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        cameraPreview = new AspectTextureView(this);
        root.addView(cameraPreview, new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        top.setPadding(dp(18), dp(28), dp(18), dp(10));
        top.setBackgroundColor(themeCameraBarColor());
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(94)
        );
        topParams.gravity = Gravity.TOP;
        root.addView(top, topParams);

        flashButton = cameraTopControl(R.drawable.ic_camera_flash);
        flashButton.setOnClickListener(view -> toggleFlash());
        top.addView(flashButton, new LinearLayout.LayoutParams(0, dp(56), 1f));

        ImageView switchCam = cameraTopControl(R.drawable.ic_camera_flip);
        switchCam.setOnClickListener(view -> {
            useFrontCamera = !useFrontCamera;
            currentZoom = 1f;
            stopCamera();
            startCameraWhenReady();
        });
        top.addView(switchCam, new LinearLayout.LayoutParams(0, dp(56), 1f));

        ImageView fast = cameraTopControl(R.drawable.ic_camera_fast);
        if (optionPrefs().getBoolean("fastModeEnabled", false)) {
            fast.setBackground(roundDrawable(Color.argb(130, 0, 180, 216), 999, 1, Color.rgb(125, 211, 252)));
        }
        fast.setOnClickListener(view -> {
            boolean enabled = !optionPrefs().getBoolean("fastModeEnabled", false);
            optionPrefs().edit().putBoolean("fastModeEnabled", enabled).apply();
            toast(enabled ? "Быстрый режим включен" : "Быстрый режим выключен");
            showCamera();
        });
        top.addView(fast, new LinearLayout.LayoutParams(0, dp(56), 1f));

        ImageView back = cameraTopControl(R.drawable.ic_camera_menu);
        back.setOnClickListener(view -> {
            stopCamera();
            showVault();
        });
        top.addView(back, new LinearLayout.LayoutParams(0, dp(56), 1f));

        TextView coords = new TextView(this);
        coords.setText("55.826460, 37.755461          ±15 м        ●");
        coords.setTextColor(Color.WHITE);
        coords.setTextSize(17);
        coords.setTypeface(Typeface.DEFAULT_BOLD);
        coords.setGravity(Gravity.CENTER_VERTICAL);
        coords.setPadding(dp(16), 0, dp(10), 0);
        coords.setBackgroundColor(Color.argb(185, 23, 27, 32));
        FrameLayout.LayoutParams coordParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        coordParams.gravity = Gravity.TOP;
        coordParams.topMargin = dp(94);
        root.addView(coords, coordParams);

        if (optionPrefs().getBoolean("crosshairEnabled", false)) {
            TextView crosshair = new TextView(this);
            crosshair.setText("+");
            crosshair.setTextColor(Color.argb(230, 235, 246, 255));
            crosshair.setTextSize(46);
            crosshair.setGravity(Gravity.CENTER);
            crosshair.setShadowLayer(4f, 0f, 0f, Color.BLACK);
            root.addView(crosshair, new FrameLayout.LayoutParams(dp(86), dp(86), Gravity.CENTER));
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(22), dp(12), dp(22), dp(16));
        bottom.setBackgroundColor(themeCameraBarColor());
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(120)
        );
        bottomParams.gravity = Gravity.BOTTOM;
        bottomParams.bottomMargin = safeNavBarMargin();
        root.addView(bottom, bottomParams);

        ImageView gallery = cameraBottomIcon(R.drawable.ic_camera_gallery);
        gallery.setOnClickListener(view -> showCloudAndGallery());
        bottom.addView(gallery, new LinearLayout.LayoutParams(0, dp(90), 1f));

        TextView shutter = cameraShutter();
        shutter.setOnClickListener(view -> capturePhoto());
        LinearLayout.LayoutParams shutterParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        shutterParams.setMargins(dp(10), 0, dp(10), 0);
        bottom.addView(shutter, shutterParams);

        ImageView map = cameraBottomIcon(R.drawable.ic_camera_map);
        map.setOnClickListener(view -> showCameraMapPanel());
        bottom.addView(map, new LinearLayout.LayoutParams(0, dp(90), 1f));

        setContentView(root);
        startCameraWhenReady();
    }

    private ImageView cameraTopControl(int drawableRes) {
        ImageView control = new ImageView(this);
        control.setImageResource(drawableRes);
        control.setPadding(dp(14), dp(12), dp(14), dp(12));
        control.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return control;
    }

    private TextView cameraShutter() {
        TextView shutter = new TextView(this);
        shutter.setText("");
        shutter.setGravity(Gravity.CENTER);
        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        outer.setColor(Color.TRANSPARENT);
        outer.setStroke(dp(5), Color.WHITE);
        shutter.setBackground(outer);
        return shutter;
    }

    private ImageView cameraBottomIcon(int drawableRes) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawableRes);
        icon.setPadding(dp(18), dp(20), dp(18), dp(20));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return icon;
    }

    private TextView cameraRoundOverlay(String label) {
        TextView control = new TextView(this);
        control.setText(label);
        control.setTextColor(Color.WHITE);
        control.setTextSize(26);
        control.setGravity(Gravity.CENTER);
        control.setBackground(roundDrawable(Color.argb(130, 70, 130, 180), 999, 0, Color.TRANSPARENT));
        return control;
    }

    private void showCameraMapPanel() {
        boolean fromCamera = "camera".equals(currentScreen);
        if (!"map".equals(currentScreen)) {
            mapReturnScreen = fromCamera ? "camera" : currentScreen;
        }
        if (fromCamera) {
            stopCamera();
        }
        destroyActiveMapView();
        currentScreen = "map";
        setBackTarget("camera".equals(mapReturnScreen) ? this::showCamera : this::showVault);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(7, 25, 38));

        View mapContent = createMapContentView();
        frame.addView(mapContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        mapCoordsText = mapOverlayText("Геопозиция: не определена");
        FrameLayout.LayoutParams coordsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        coordsParams.gravity = Gravity.TOP;
        coordsParams.setMargins(dp(14), dp(22), dp(14), 0);
        frame.addView(mapCoordsText, coordsParams);

        LinearLayout zoomControls = new LinearLayout(this);
        zoomControls.setOrientation(LinearLayout.VERTICAL);
        zoomControls.setGravity(Gravity.CENTER);
        TextView plus = mapRoundButton("+");
        plus.setOnClickListener(view -> {
            if (activeMapView != null) {
                activeMapView.getModel().mapViewPosition.zoomIn(true);
            }
        });
        zoomControls.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView locate = mapRoundButton("⌖");
        locate.setTextSize(24);
        locate.setOnClickListener(view -> centerMapOnUserLocation());
        LinearLayout.LayoutParams locateParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        locateParams.setMargins(0, dp(10), 0, dp(10));
        zoomControls.addView(locate, locateParams);
        TextView minus = mapRoundButton("-");
        minus.setOnClickListener(view -> {
            if (activeMapView != null) {
                activeMapView.getModel().mapViewPosition.zoomOut(true);
            }
        });
        zoomControls.addView(minus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(dp(62), dp(178));
        zoomParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        zoomParams.setMargins(0, 0, dp(14), 0);
        frame.addView(zoomControls, zoomParams);

        mapUserBubble = mapUserBubbleView();
        mapUserBubble.setVisibility(View.GONE);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(dp(238), dp(48));
        bubbleParams.gravity = Gravity.CENTER;
        bubbleParams.setMargins(0, 0, 0, dp(104));
        frame.addView(mapUserBubble, bubbleParams);

        mapUserPin = new UserMapMarkerView(this);
        mapUserPin.setVisibility(View.GONE);
        FrameLayout.LayoutParams pinParams = new FrameLayout.LayoutParams(dp(56), dp(72));
        pinParams.gravity = Gravity.CENTER;
        pinParams.setMargins(0, 0, 0, dp(42));
        frame.addView(mapUserPin, pinParams);

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(18), dp(14), dp(18), safeBottomPad());
        overlay.setGravity(Gravity.BOTTOM);
        String map = optionPrefs().getString("mapPack", "Карта не выбрана");
        TextView title = new TextView(this);
        title.setText(map);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setShadowLayer(8f, 0f, 2f, Color.BLACK);
        overlay.addView(title);
        TextView hint = text(optionPrefs().getBoolean("attachMapToPhoto", false)
                ? "Прикрепление к фото включено"
                : "Перемещай карту пальцем. ⌖ центрирует по GPS.");
        hint.setTextColor(Color.rgb(213, 236, 246));
        overlay.addView(hint);
        Button choose = secondaryButton("Выбрать карту");
        choose.setOnClickListener(view -> chooseMapPack());
        overlay.addView(choose);
        Button attach = primaryButton(optionPrefs().getBoolean("attachMapToPhoto", false)
                ? "Не прикреплять к фото"
                : "Прикреплять карту к фото");
        attach.setOnClickListener(view -> {
            boolean enabled = !optionPrefs().getBoolean("attachMapToPhoto", false);
            optionPrefs().edit().putBoolean("attachMapToPhoto", enabled).apply();
            toast(enabled ? "Карта будет прикрепляться к фото" : "Прикрепление карты выключено");
            showCameraMapPanel();
        });
        overlay.addView(attach);
        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> {
            destroyActiveMapView();
            Runnable target = backTarget;
            if (target != null) {
                target.run();
            } else {
                showVault();
            }
        });
        overlay.addView(back);
        frame.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(frame);
        centerMapOnUserLocation();
    }

    private TextView mapOverlayText(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setSingleLine(false);
        view.setBackground(roundDrawable(Color.argb(205, 7, 28, 43), 16, 1, Color.argb(170, 125, 211, 252)));
        return view;
    }

    private TextView mapRoundButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(28);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundDrawable(Color.argb(215, 8, 38, 58), 999, 1, Color.rgb(125, 211, 252)));
        return button;
    }

    private TextView mapUserBubbleView() {
        TextView bubble = new TextView(this);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(13);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(10), 0, dp(10), 0);
        bubble.setSingleLine(false);
        bubble.setBackground(roundDrawable(Color.argb(225, 4, 30, 44), 14, 1, Color.rgb(45, 211, 191)));
        return bubble;
    }

    private View createMapContentView() {
        File mapFile = selectedMapFile();
        if (mapFile == null || !mapFile.exists() || mapFile.length() == 0L) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(28), dp(28), dp(28), dp(28));
            empty.setBackgroundColor(Color.rgb(7, 25, 38));
            TextView title = new TextView(this);
            title.setText("Карта не скачана");
            title.setTextColor(Color.WHITE);
            title.setTextSize(24);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView body = text("Выбери регион и скачай `.map` через Опции -> Выбор карт. После скачивания эта кнопка откроет настоящую offline-карту.");
            body.setGravity(Gravity.CENTER);
            body.setTextColor(Color.rgb(213, 236, 246));
            empty.addView(body);
            return empty;
        }

        try {
            AndroidGraphicFactory.createInstance(getApplication());
            MapView mapView = new MapView(this);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.getMapScaleBar().setVisible(true);
            mapView.getModel().mapViewPosition.setCenter(new LatLong(55.7558, 37.6173));
            mapView.getModel().mapViewPosition.setZoomLevel((byte) 10);
            TileCache tileCache = AndroidUtil.createTileCache(
                    this,
                    "blue-ocean-map-cache",
                    mapView.getModel().displayModel.getTileSize(),
                    1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor()
            );
            MapDataStore mapDataStore = new MapFile(mapFile);
            TileRendererLayer renderer = new TileRendererLayer(
                    tileCache,
                    mapDataStore,
                    mapView.getModel().mapViewPosition,
                    AndroidGraphicFactory.INSTANCE
            );
            renderer.setXmlRenderTheme(MapsforgeThemes.DEFAULT);
            mapView.getLayerManager().getLayers().add(renderer);
            activeMapView = mapView;
            return mapView;
        } catch (Exception e) {
            Log.e(TAG, "mapsforge map failed", e);
            LinearLayout error = new LinearLayout(this);
            error.setOrientation(LinearLayout.VERTICAL);
            error.setGravity(Gravity.CENTER);
            error.setPadding(dp(28), dp(28), dp(28), dp(28));
            error.setBackgroundColor(Color.rgb(7, 25, 38));
            TextView title = new TextView(this);
            title.setText("Карта не открылась");
            title.setTextColor(Color.WHITE);
            title.setTextSize(24);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            error.addView(title);
            TextView body = text(mapFile.getName() + "\n" + (e.getMessage() == null ? "Ошибка renderer" : e.getMessage()));
            body.setGravity(Gravity.CENTER);
            body.setTextColor(Color.rgb(213, 236, 246));
            error.addView(body);
            return error;
        }
    }

    private File selectedMapFile() {
        String file = optionPrefs().getString("mapPack", "");
        if (file == null || file.trim().isEmpty() || "Russia".equals(file) || "Карта не выбрана".equals(file)) {
            return null;
        }
        return selectedMapFileForName(file);
    }

    private void destroyActiveMapView() {
        if (activeMapView != null) {
            try {
                activeMapView.destroyAll();
            } catch (Exception ignored) {
            }
            activeMapView = null;
        }
        mapCoordsText = null;
        mapUserBubble = null;
        mapUserPin = null;
    }

    private void centerMapOnUserLocation() {
        pendingLocationInput = null;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
            return;
        }

        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            updateMapCoords(null, "Геосервис недоступен");
            return;
        }

        try {
            Location location = bestLastLocation(manager);
            if (location != null) {
                applyMapLocation(location);
                return;
            }

            List<String> providers = manager.getProviders(true);
            if (providers.isEmpty()) {
                updateMapCoords(null, "Включите геолокацию на телефоне");
                return;
            }

            updateMapCoords(null, "Ищу геопозицию...");
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    applyMapLocation(location);
                    manager.removeUpdates(this);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    updateMapCoords(null, "Геолокация отключена");
                }
            };
            manager.requestLocationUpdates(providers.get(0), 0L, 0f, listener);
        } catch (SecurityException e) {
            updateMapCoords(null, "Нет разрешения на геопозицию");
        } catch (Exception e) {
            updateMapCoords(null, "Не удалось получить геопозицию");
        }
    }

    private void applyMapLocation(Location location) {
        updateMapCoords(location, null);
        if (activeMapView != null) {
            LatLong center = new LatLong(location.getLatitude(), location.getLongitude());
            activeMapView.getModel().mapViewPosition.setCenter(center);
            if (activeMapView.getModel().mapViewPosition.getZoomLevel() < 14) {
                activeMapView.getModel().mapViewPosition.setZoomLevel((byte) 14);
            }
        }
    }

    private void updateMapCoords(Location location, String status) {
        if (mapCoordsText == null) {
            return;
        }
        if (location == null) {
            mapCoordsText.setText(status == null ? "Геопозиция: не определена" : status);
            return;
        }
        String value = String.format(
                Locale.US,
                "GPS %.6f, %.6f   ±%.0f м",
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f
        );
        mapCoordsText.setText(value);
        if (mapUserBubble != null) {
            mapUserBubble.setText(String.format(
                    Locale.US,
                    "%.6f\n%.6f",
                    location.getLatitude(),
                    location.getLongitude()
            ));
            mapUserBubble.setVisibility(View.VISIBLE);
        }
        if (mapUserPin != null) {
            mapUserPin.setVisibility(View.VISIBLE);
        }
    }

    private void showBackupSettings() {
        currentScreen = "backup";
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("TGFinder");

        addPanel(root, "Статус", "Папка: " + backupConfigStore.tgFolderName()
                + "\nURL: " + backupConfigStore.tgFinderUrl()
                + "\nДоступ: " + (backupConfigStore.isDevicePaired() ? "device token" : "Telegram user id")
                + "\nОжидают отправки: " + backupQueue.pendingCount());

        Button run = secondaryButton("Отправить очередь в TGFinder");
        run.setOnClickListener(view -> {
            runTgFinderBackup();
        });
        root.addView(run);

        Button queue = secondaryButton("Посмотреть очередь");
        queue.setOnClickListener(view -> showBackupQueue());
        root.addView(queue);

        Button options = secondaryButton("Опции");
        options.setOnClickListener(view -> showOptions());
        root.addView(options);

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);

        setContentView(wrap(root));
    }

    private void showMegaCloud() {
        showMegaCloud("");
    }

    private void showMegaCloud(String folder) {
        currentScreen = "mega";
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("MEGA");

        if (!backupConfigStore.hasMegaTarget()) {
            addPanel(root, "Статус", "MEGA не подключено. Включите MEGA и подключите аккаунт в настройках синхронизации.");
            Button options = secondaryButton("Настройки синхронизации");
            options.setOnClickListener(view -> showSyncOptions());
            root.addView(options);

            Button back = secondaryButton("Назад");
            back.setOnClickListener(view -> showCloudAndGallery());
            root.addView(back);
            setContentView(wrap(root));
            return;
        }

        addPanel(root, "Загрузка", "Получаю папки и файлы MEGA...");
        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);
        setContentView(wrap(root));

        String targetFolder = (folder == null || folder.trim().isEmpty()) ? backupConfigStore.megaFolder() : folder.trim();
        new Thread(() -> {
            MegaRelayClient.Listing listing = new MegaRelayClient.Listing(targetFolder, new ArrayList<>(), new ArrayList<>());
            String error = "";
            try {
                listing = megaRelayClient().listFolder(targetFolder);
            } catch (Exception e) {
                error = e.getMessage() == null ? "MEGA list failed" : e.getMessage();
                Log.e(TAG, "MEGA list failed", e);
            }
            MegaRelayClient.Listing finalListing = listing;
            String finalError = error;
            handler.post(() -> renderMegaFiles(finalListing, finalError));
        }, "BlueOceanMegaList").start();
    }

    private void renderMegaFiles(MegaRelayClient.Listing listing, String error) {
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("MEGA");
        currentMegaFolder = listing.folder == null || listing.folder.isEmpty() ? backupConfigStore.megaFolder() : listing.folder;
        if (!error.isEmpty()) {
            addPanel(root, "Ошибка", error);
        } else if (listing.folders.isEmpty() && listing.files.isEmpty()) {
            addPanel(root, "Пусто", "В этой папке MEGA пока нет файлов.");
        } else {
            addPanel(root, "Содержимое",
                    "Аккаунт: " + backupConfigStore.megaEmail()
                            + "\nПапка: " + currentMegaFolder
                            + "\nПапок: " + listing.folders.size()
                            + "\nФайлов: " + listing.files.size());
            for (MegaRelayClient.RemoteFolder folder : listing.folders) {
                Button open = secondaryButton("▦  " + (folder.name.isEmpty() ? "Папка" : folder.name) + " · " + folder.objectCount);
                open.setOnClickListener(view -> showMegaCloud(folder.path));
                root.addView(open);
            }
            for (MegaRelayClient.RemoteFile file : listing.files) {
                String size = file.fileSize <= 0 ? "размер неизвестен" : file.fileSize + " байт";
                addPanel(root, file.name.isEmpty() ? "Без имени" : file.name, file.path + "\n" + size);
                Button download = secondaryButton("Скачать этот файл");
                download.setOnClickListener(view -> importRemoteMegaFile(file));
                root.addView(download);
            }
        }
        Button createFolder = secondaryButton("Новая папка");
        createFolder.setOnClickListener(view -> showCreateMegaFolderDialog(currentMegaFolder));
        root.addView(createFolder);

        Button refresh = primaryButton("Обновить список");
        refresh.setOnClickListener(view -> showMegaCloud(currentMegaFolder));
        root.addView(refresh);
        Button queue = secondaryButton("Очередь отправки");
        queue.setOnClickListener(view -> showBackupQueue());
        root.addView(queue);
        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> {
            String parent = parentMegaFolder(currentMegaFolder);
            if (!parent.isEmpty() && !currentMegaFolder.equals(backupConfigStore.megaFolder())) {
                showMegaCloud(parent);
            } else {
                showCloudAndGallery();
            }
        });
        root.addView(back);
        setContentView(wrap(root));
    }

    private void showCreateMegaFolderDialog(String parentFolder) {
        EditText input = compactInput("Название папки");
        new AlertDialog.Builder(this)
                .setTitle("Новая папка MEGA")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Введите название папки");
                        return;
                    }
                    createMegaFolder(parentFolder, name);
                })
                .show();
    }

    private void createMegaFolder(String parentFolder, String name) {
        toast("Создаю папку MEGA...");
        new Thread(() -> {
            String error = "";
            String createdPath = "";
            try {
                createdPath = megaRelayClient().createFolder(parentFolder, name);
            } catch (Exception e) {
                error = e.getMessage() == null ? "MEGA mkdir failed" : e.getMessage();
                Log.e(TAG, "MEGA mkdir failed", e);
            }
            String finalError = error;
            String finalCreatedPath = createdPath;
            handler.post(() -> {
                if (finalError.isEmpty()) {
                    toast("Папка создана");
                    showMegaCloud(parentFolder == null || parentFolder.trim().isEmpty() ? backupConfigStore.megaFolder() : parentFolder);
                } else {
                    toast("MEGA: " + finalError);
                    showMegaCloud(currentMegaFolder);
                }
            });
        }, "BlueOceanMegaMkdir").start();
    }

    private String parentMegaFolder(String folder) {
        String clean = folder == null ? "" : folder.trim();
        int slash = clean.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        return clean.substring(0, slash);
    }

    private void showOptions() {
        currentScreen = "options";
        setBackTarget(this::showVault);
        LinearLayout root = baseScreen("Опции");

        addSettingsSwitch(root, "✍", "Текст по умолчанию", "Автоматически добавляется к новым фотографиям.", "defaultTextEnabled", false);
        addSettingsRow(root, "T", "Редактировать текст", defaultTextSummary(), () -> showDefaultTextOptions());
        addColorSettingsRow(root, "Цвет текста", "textColor", Color.WHITE, () -> chooseSettingColor("textColor", "Цвет текста"));
        addSettingsSwitch(root, "▣", "Фон текста", "Подложка под надписи на фото.", "textBackgroundEnabled", true);
        addColorSettingsRow(root, "Цвет фона текста", "textBgColor", Color.BLACK, () -> chooseSettingColor("textBgColor", "Цвет фона текста"));
        addSettingsSwitch(root, "⊕", "Перекрестие по центру", "Показывать центр кадра во время съемки.", "crosshairEnabled", false);
        addSettingsRow(root, "❤", "Выбор эмодзи", optionPrefs().getString("defaultEmoji", "❤"), () -> chooseEmoji());
        addSettingsRow(root, "▤", "Водяной знак верхний", watermarkSummary("top"), () -> showWatermarkOptions("top"));
        addSettingsRow(root, "▤", "Водяной знак нижний", watermarkSummary("bottom"), () -> showWatermarkOptions("bottom"));
        addSettingsSwitch(root, "🛡", "Защита от погрешности", "Не сохранять координаты при плохой точности GPS.", "gpsAccuracyProtection", false);
        addSettingsSwitch(root, "⌖", "Показывать координаты", "Отображать координаты поверх фотографии.", "showCoordinates", true);
        addSettingsSwitch(root, "⌁", "Следить за дистанцией", "Предупреждать при приближении к точкам последних фото.", "distanceTracking", false);
        addSettingsRow(root, "30", "Безопасная дистанция", optionPrefs().getInt("safeDistance", 30) + " м", () -> chooseSafeDistance());
        addSettingsRow(root, "⌫", "Очистить последние координаты", "Удалить сохраненные точки последних фотографий.", () -> toast("Координаты очищены"));
        addSettingsRow(root, "▧", "Качество изображения", optionPrefs().getString("imageQuality", "MEDIUM"), () -> chooseImageQuality());
        addSettingsSwitch(root, "▣", "Вибрация", "Виброотклик при нажатии на кнопку съемки.", "vibrationEnabled", true);
        addSettingsSwitch(root, "▱", "Отображать точки на карте", "Показывать GPS-точки на карте.", "mapPointsEnabled", true);
        addSettingsRow(root, "🗺", "Выбор карт", optionPrefs().getString("mapPack", "Russia"), () -> chooseMapPack());
        addSettingsRow(root, "☾", "Тема приложения", optionPrefs().getString("theme", "Системная тема"), () -> chooseTheme());
        addSettingsRow(root, "⌘", "Экран входа", optionPrefs().getString("entryMode", "Калькулятор"), () -> chooseEntryMode());
        addSettingsRow(root, "▦", "Иконка приложения", optionPrefs().getString("appIcon", optionPrefs().getString("entryMode", "Калькулятор")), () -> chooseAppIcon());
        addSettingsSwitch(root, "◉", "Маскировать приложение", "Оставлять внешний вид приложения похожим на заметки.", "maskAppEnabled", true);
        addSettingsSwitch(root, "↯", "Быстрый режим", "Минимум подтверждений при съемке и сохранении.", "fastModeEnabled", false);
        addSettingsSwitch(root, "⌨", "Запускать клавиатуру при старте", "Автоматически открывать ввод для заметок.", "keyboardOnStart", false);
        addSettingsRow(root, "🔐", "Изменить пароль", "PIN для входа через калькулятор", () -> showSetupPin());
        addSettingsRow(root, "☁", "Синхронизация", "MEGA, TGFinder, device pairing", () -> showSyncOptions());
        addSettingsRow(root, "ⓘ", "О программе", "Blue Ocean v2", () -> showAbout());

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);

        ScrollView scroll = wrap(root, optionsScrollY);
        scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> optionsScrollY = scrollY);
        setContentView(scroll);
    }

    private void showSyncOptions() {
        currentScreen = "sync";
        setBackTarget(this::showOptions);
        LinearLayout root = baseScreen("Опции");

        addPanel(root, "Каналы синхронизации",
                "Включенные каналы появляются в меню «Галерея и Облако».\n"
                        + "Активно сейчас: " + backupConfigStore.enabledTargetsText());

        Switch megaEnabled = new Switch(this);
        megaEnabled.setText("MEGA включено");
        megaEnabled.setTextColor(themeTextColor());
        megaEnabled.setTextSize(16);
        megaEnabled.setChecked(backupConfigStore.isMegaEnabled());
        megaEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backupConfigStore.setMegaEnabled(isChecked);
            showSyncOptions();
        });
        root.addView(megaEnabled);

        Switch tgEnabled = new Switch(this);
        tgEnabled.setText("TGFinder включен");
        tgEnabled.setTextColor(themeTextColor());
        tgEnabled.setTextSize(16);
        tgEnabled.setChecked(backupConfigStore.isTgFinderEnabled());
        tgEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backupConfigStore.setTgFinderEnabled(isChecked);
            showSyncOptions();
        });
        root.addView(tgEnabled);

        addPanel(root, "Привязка устройства",
                "Статус: " + (backupConfigStore.isDevicePaired() ? "устройство привязано" : "работает по Telegram user id")
                        + "\nTelegram user id: " + tgUserStatusText()
                        + "\n\nНажмите «Привязать через TGFinder». Приложение создаст короткий код, а бот сам возьмет ваш Telegram ID.");

        Button sendPair = secondaryButton("Привязать через TGFinder");
        sendPair.setOnClickListener(view -> startTelegramPairing());
        root.addView(sendPair);

        Button verifyPair = secondaryButton("Проверить привязку");
        verifyPair.setOnClickListener(view -> verifyTgFinderPairing());
        root.addView(verifyPair);

        Button restoreTg = secondaryButton("Восстановить TGFinder");
        restoreTg.setOnClickListener(view -> {
            backupConfigStore.restoreTgFinderDefaults();
            toast("TGFinder восстановлен");
            showSyncOptions();
        });
        root.addView(restoreTg);

        EditText megaEmail = textInput("MEGA e-mail");
        megaEmail.setSingleLine(true);
        megaEmail.setText(backupConfigStore.megaEmail());
        root.addView(megaEmail);

        EditText megaFolder = textInput("Папка MEGA");
        megaFolder.setSingleLine(true);
        megaFolder.setText(backupConfigStore.megaFolder());
        root.addView(megaFolder);

        EditText megaRelayUrl = textInput("MEGA relay API URL");
        megaRelayUrl.setSingleLine(true);
        megaRelayUrl.setText(backupConfigStore.megaRelayUrl());
        root.addView(megaRelayUrl);

        Button connectMega = secondaryButton("Подключить аккаунт MEGA");
        connectMega.setOnClickListener(view -> showMegaConnectDialog(
                megaEmail.getText().toString(),
                megaFolder.getText().toString(),
                megaRelayUrl.getText().toString()
        ));
        root.addView(connectMega);

        EditText tgUrl = textInput("TGFinder API URL");
        tgUrl.setSingleLine(true);
        tgUrl.setText(backupConfigStore.tgFinderUrl());
        root.addView(tgUrl);

        EditText tgUserId = textInput("Telegram user id вручную");
        tgUserId.setSingleLine(true);
        tgUserId.setInputType(InputType.TYPE_CLASS_NUMBER);
        tgUserId.setText(backupConfigStore.tgUserId());
        root.addView(tgUserId);

        EditText tgFolder = textInput("Папка/топик TGFinder");
        tgFolder.setSingleLine(true);
        tgFolder.setText(backupConfigStore.tgFolderName());
        root.addView(tgFolder);

        Button save = primaryButton("Сохранить");
        save.setOnClickListener(view -> {
            backupConfigStore.save(
                    megaEmail.getText().toString(),
                    megaFolder.getText().toString(),
                    megaRelayUrl.getText().toString(),
                    tgUrl.getText().toString(),
                    tgUserId.getText().toString(),
                    tgFolder.getText().toString(),
                    ""
            );
            if (!tgUserId.getText().toString().trim().isEmpty()) {
                backupConfigStore.setDevicePaired(false);
            }
            toast("Опции сохранены");
            showSyncOptions();
        });
        root.addView(save);

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);

        setContentView(wrap(root));
    }

    private SharedPreferences optionPrefs() {
        return getSharedPreferences("blue_ocean_options", MODE_PRIVATE);
    }

    private void addSettingsRow(LinearLayout root, String icon, String title, String subtitle, Runnable action) {
        LinearLayout row = settingsRowBase(icon, title, subtitle);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(themeMutedColor());
        arrow.setTextSize(30);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(view -> action.run());
        root.addView(row);
    }

    private void addColorSettingsRow(LinearLayout root, String title, String key, int defaultColor, Runnable action) {
        int color = optionPrefs().getInt(key, defaultColor);
        LinearLayout row = settingsRowBase("■", title, settingColorName(key, defaultColor));
        TextView swatch = new TextView(this);
        swatch.setText("");
        swatch.setBackground(roundDrawable(color, 999, 1, Color.rgb(190, 210, 225)));
        row.addView(swatch, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(themeMutedColor());
        arrow.setTextSize(30);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(view -> action.run());
        root.addView(row);
    }

    private void addSettingsSwitch(LinearLayout root, String icon, String title, String subtitle, String key, boolean defaultValue) {
        LinearLayout row = settingsRowBase(icon, title, subtitle);
        Switch toggle = new Switch(this);
        toggle.setChecked(optionPrefs().getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> optionPrefs().edit().putBoolean(key, isChecked).apply());
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        row.addView(toggle);
        root.addView(row);
    }

    private LinearLayout settingsRowBase(String icon, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(roundDrawable(themePanelColor(), 0, 1, themePanelStroke()));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(themeAccent());
        iconView.setTextSize(22);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(38), dp(54)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(8), 0, dp(8), 0);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(themeTextColor());
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(titleView);
        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(themeMutedColor());
        subView.setTextSize(13);
        subView.setMaxLines(2);
        texts.addView(subView);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(1));
        row.setLayoutParams(params);
        return row;
    }

    private String settingColorName(String key, int defaultColor) {
        int color = optionPrefs().getInt(key, defaultColor);
        return colorName(color);
    }

    private String colorName(int color) {
        if (color == Color.LTGRAY) return "Серый";
        if (color == Color.rgb(0, 150, 150)) return "Бирюзовый";
        if (color == Color.WHITE) return "Белый";
        if (color == Color.YELLOW) return "Желтый";
        if (color == Color.RED) return "Красный";
        if (color == Color.rgb(160, 0, 160)) return "Фиолетовый";
        if (color == Color.rgb(140, 150, 0)) return "Оливковый";
        if (color == Color.rgb(0, 0, 170)) return "Темно-синий";
        if (color == Color.GRAY) return "Темно-серый";
        if (color == Color.rgb(0, 150, 0)) return "Зеленый";
        if (color == Color.rgb(0, 255, 0)) return "Лайм";
        if (color == Color.rgb(170, 0, 0)) return "Бордовый";
        if (color == Color.CYAN) return "Голубой";
        if (color == Color.BLACK) return "Черный";
        if (color == Color.BLUE) return "Электрик";
        if (color == Color.MAGENTA) return "Малиновый";
        return "Пользовательский";
    }

    private void chooseSettingColor(String key, String title) {
        String[] names = {"Серый", "Бирюзовый", "Белый", "Желтый", "Красный", "Фиолетовый", "Оливковый", "Синий", "Темно-серый", "Зеленый", "Лайм", "Бордовый", "Голубой", "Черный", "Электрик", "Малиновый", "Аква", "Небесный", "Морская волна", "Индиго", "Коралловый", "Янтарный", "Мята", "Графит"};
        int[] colors = {
                Color.LTGRAY, Color.rgb(0, 150, 150), Color.WHITE, Color.YELLOW,
                Color.RED, Color.rgb(160, 0, 160), Color.rgb(140, 150, 0), Color.rgb(0, 0, 170),
                Color.GRAY, Color.rgb(0, 150, 0), Color.rgb(0, 255, 0), Color.rgb(170, 0, 0),
                Color.CYAN, Color.BLACK, Color.BLUE, Color.MAGENTA,
                Color.rgb(0, 188, 212), Color.rgb(56, 189, 248), Color.rgb(20, 184, 166), Color.rgb(79, 70, 229),
                Color.rgb(251, 113, 133), Color.rgb(245, 158, 11), Color.rgb(110, 231, 183), Color.rgb(31, 41, 55)
        };
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(themeInputColor());
        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));
        grid.setBackgroundColor(themeInputColor());
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            String name = names[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView swatch = new TextView(this);
            swatch.setText("");
            swatch.setBackground(roundDrawable(color, 999, 1, Color.rgb(170, 190, 210)));
            item.addView(swatch, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView label = new TextView(this);
            label.setText(name);
            label.setTextColor(themeTextColor());
            label.setTextSize(9);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            item.addView(label);
            item.setOnClickListener(view -> {
                optionPrefs().edit().putInt(key, color).apply();
                showOptions();
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(58);
            params.height = dp(58);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(item, params);
        }
        scroll.addView(grid);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(310)));

        Button custom = secondaryButton("HSL настройка");
        custom.setOnClickListener(view -> showHslColorDialog(key, title));
        panel.addView(custom);

        showDarkPanelDialog(title, panel);
    }

    private void showHslColorDialog(String key, String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(10));
        panel.setBackgroundColor(themeInputColor());

        TextView preview = new TextView(this);
        preview.setText("");
        panel.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        int current = optionPrefs().getInt(key, "textBgColor".equals(key) ? Color.BLACK : Color.WHITE);
        float[] hsv = new float[3];
        Color.colorToHSV(current, hsv);
        SeekBar hue = new SeekBar(this);
        hue.setMax(360);
        hue.setProgress((int) hsv[0]);
        SeekBar saturation = new SeekBar(this);
        saturation.setMax(100);
        saturation.setProgress(Math.round(hsv[1] * 100f));
        SeekBar lightness = new SeekBar(this);
        lightness.setMax(100);
        lightness.setProgress(Math.round(hsv[2] * 100f));
        TextView hueLabel = text("Тон");
        TextView saturationLabel = text("Насыщенность");
        TextView lightnessLabel = text("Яркость");
        panel.addView(hueLabel);
        panel.addView(hue);
        panel.addView(saturationLabel);
        panel.addView(saturation);
        panel.addView(lightnessLabel);
        panel.addView(lightness);

        final int[] selectedColor = {current};
        Runnable update = () -> {
            selectedColor[0] = Color.HSVToColor(hsv);
            preview.setBackground(roundDrawable(selectedColor[0], 6, 1, themePanelStroke()));
            optionPrefs().edit().putInt(key, selectedColor[0]).apply();
        };
        SeekBar.OnSeekBarChangeListener listener = new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hsv[0] = hue.getProgress();
                hsv[1] = saturation.getProgress() / 100f;
                hsv[2] = Math.max(0.05f, lightness.getProgress() / 100f);
                update.run();
            }
        };
        hue.setOnSeekBarChangeListener(listener);
        saturation.setOnSeekBarChangeListener(listener);
        lightness.setOnSeekBarChangeListener(listener);
        update.run();

        Button apply = primaryButton("Готово");
        apply.setOnClickListener(view -> {
            optionPrefs().edit().putInt(key, selectedColor[0]).apply();
            showOptions();
        });
        panel.addView(apply);
        showDarkPanelDialog(title, panel);
    }

    private void showDarkPanelDialog(String title, LinearLayout panel) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(panel)
                .setPositiveButton("Закрыть", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(themePanelColor()));
            }
        });
        dialog.show();
    }

    private void showDarkGridDialog(String title, GridLayout panel) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(panel)
                .setPositiveButton("Закрыть", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(themePanelColor()));
            }
        });
        dialog.show();
    }

    private void chooseEmoji() {
        String[] emojis = normalEmojiSet();
        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(8);
        grid.setPadding(dp(12), dp(12), dp(12), dp(4));
        for (String emoji : emojis) {
            TextView cell = new TextView(this);
            cell.setText(emoji);
            cell.setTextSize(28);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundColor(Color.TRANSPARENT);
            cell.setOnClickListener(view -> {
                optionPrefs().edit().putString("defaultEmoji", emoji).apply();
                showOptions();
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(48);
            params.height = dp(48);
            params.setMargins(0, 0, 0, 0);
            grid.addView(cell, params);
        }
        scroll.addView(grid);
        new AlertDialog.Builder(this)
                .setTitle("Выбор эмодзи")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void chooseEditorEmoji(PhotoEditorView editor, EditText labelInput) {
        String[] emojis = normalEmojiSet();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(themeInputColor());
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(8);
        for (String emoji : emojis) {
            TextView cell = new TextView(this);
            cell.setText(emoji);
            cell.setTextSize(26);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundColor(Color.TRANSPARENT);
            cell.setOnClickListener(view -> {
                editor.setMode(PhotoEditorView.MODE_TEXT);
                editor.setPendingLabel(emoji);
                labelInput.setText(emoji);
                toast("Тапните по фото");
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(42);
            params.height = dp(42);
            grid.addView(cell, params);
        }
        panel.addView(grid);
        showDarkPanelDialog("Эмодзи", panel);
    }

    private String[] normalEmojiSet() {
        return new String[]{
                "😀", "😃", "😄", "😁", "😆", "🙂", "😉", "😊",
                "😎", "😍", "😘", "😗", "😙", "😚", "🤗", "🤔",
                "😐", "😶", "🙄", "😏", "😮", "😲", "😳", "🥺",
                "😢", "😭", "😤", "😡", "🤬", "😱", "😴", "🤫",
                "👍", "👎", "👌", "✌️", "🤝", "🙏", "👏", "💪",
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                "🔥", "⭐", "✨", "⚡", "☀️", "🌙", "🌊", "☁️",
                "📍", "🧭", "🗺️", "📸", "🎯", "🚩", "✅", "❌",
                "⬆️", "⬇️", "⬅️", "➡️", "⭕", "❗", "❓", "💬"
        };
    }

    private void chooseSafeDistance() {
        String[] labels = {"10 м", "30 м", "50 м", "100 м", "300 м"};
        int[] values = {10, 30, 50, 100, 300};
        new AlertDialog.Builder(this)
                .setTitle("Безопасная дистанция")
                .setItems(labels, (dialog, which) -> {
                    optionPrefs().edit().putInt("safeDistance", values[which]).apply();
                    showOptions();
                })
                .show();
    }

    private void chooseImageQuality() {
        String[] values = {"LOW", "MEDIUM", "HIGH"};
        String[] descriptions = {
                "Меньший размер файла",
                "Оптимальный баланс",
                "Максимальное качество"
        };
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), 0);
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            LinearLayout row = settingsRowBase("▧", value, descriptions[i]);
            row.setOnClickListener(view -> {
                optionPrefs().edit().putString("imageQuality", value).apply();
                showOptions();
            });
            panel.addView(row);
        }
        new AlertDialog.Builder(this)
                .setTitle("Качество изображения")
                .setView(panel)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void chooseTheme() {
        String[] values = {"Blue Ocean", "Светлая", "Темная", "Глубокий океан"};
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(themeInputColor());
        for (String value : values) {
            LinearLayout row = settingsRowBase("☾", value, "Тема интерфейса");
            row.setOnClickListener(view -> {
                optionPrefs().edit().putString("theme", value).apply();
                showOptions();
            });
            panel.addView(row);
        }
        showDarkPanelDialog("Тема приложения", panel);
    }

    private String defaultTextSummary() {
        String value = optionPrefs().getString("defaultText1", "");
        return value.isEmpty() ? "Не задан" : value;
    }

    private void showDefaultTextOptions() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(8), dp(14), 0);
        panel.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(tabs);
        EditText text = new EditText(this);
        text.setHint("Введите текст");
        text.setTextColor(themeTextColor());
        text.setHintTextColor(themeMutedColor());
        text.setTextSize(16);
        text.setMinLines(3);
        text.setMaxLines(5);
        text.setPadding(dp(14), dp(10), dp(14), dp(10));
        text.setBackground(inputBackground());
        text.setMinLines(3);
        text.setMaxLines(5);
        final int[] slot = {1};
        List<TextView> tabButtons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            int tab = i;
            TextView button = new TextView(this);
            button.setText(String.valueOf(i));
            button.setTextColor(themeTextColor());
            button.setTextSize(16);
            button.setGravity(Gravity.CENTER);
            button.setBackground(roundDrawable(tab == 1 ? themeAccent() : Color.rgb(46, 54, 68), 6, 0, Color.TRANSPARENT));
            button.setOnClickListener(view -> {
                optionPrefs().edit().putString("defaultText" + slot[0], text.getText().toString()).apply();
                slot[0] = tab;
                text.setText(optionPrefs().getString("defaultText" + tab, ""));
                updateDefaultTextTabs(tabButtons, tab);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
            params.setMargins(0, 0, dp(8), dp(8));
            tabs.addView(button, params);
            tabButtons.add(button);
        }
        text.setText(optionPrefs().getString("defaultText1", ""));
        updateDefaultTextTabs(tabButtons, 1);
        panel.addView(text);

        TextView counter = text("0/250 символов, 1/5 строк");
        panel.addView(counter);
        text.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                counter.setText(Math.min(250, s.length()) + "/250 символов, " + slot[0] + "/5 строк");
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Текст по умолчанию")
                .setView(panel)
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Хорошо", (dialog, which) -> {
                    optionPrefs().edit().putString("defaultText" + slot[0], text.getText().toString()).apply();
                    showOptions();
                })
                .show();
    }

    private void updateDefaultTextTabs(List<TextView> buttons, int activeSlot) {
        for (int i = 0; i < buttons.size(); i++) {
            TextView button = buttons.get(i);
            boolean active = i + 1 == activeSlot;
            button.setTextColor(active ? Color.rgb(6, 18, 28) : themeTextColor());
            button.setBackground(roundDrawable(active ? themeAccent() : themeInputColor(), 6, 1, active ? themeAccentSecond() : themePanelStroke()));
        }
    }

    private void chooseEntryMode() {
        String[] values = {"Калькулятор", "Заметки"};
        new AlertDialog.Builder(this)
                .setTitle("Экран входа")
                .setItems(values, (dialog, which) -> {
                    String mode = values[which];
                    optionPrefs().edit()
                            .putString("entryMode", mode)
                            .putString("appIcon", mode)
                            .apply();
                    applyLauncherIcon(mode);
                    showOptions();
                })
                .show();
    }

    private void chooseAppIcon() {
        GridLayout panel = new GridLayout(this);
        panel.setColumnCount(2);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackgroundColor(themeInputColor());
        addIconChoice(panel, R.drawable.ic_launcher_calc, "Калькулятор");
        addIconChoice(panel, R.drawable.ic_launcher_notes, "Заметки");
        showDarkGridDialog("Изменить иконку приложения", panel);
    }

    private int launcherIconResource() {
        String fallback = optionPrefs().getString("entryMode", "Калькулятор");
        String name = optionPrefs().getString("appIcon", fallback);
        if ("Заметки".equals(name)) return getResources().getIdentifier("ic_launcher_notes", "drawable", getPackageName());
        return getResources().getIdentifier("ic_launcher_calc", "drawable", getPackageName());
    }

    private void addIconChoice(GridLayout panel, int iconRes, String name) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(8), dp(6), dp(8));
        item.setBackground(roundDrawable(themePanelColor(), 10, 1, themePanelStroke()));

        TextView iconView = new TextView(this);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackgroundResource(iconRes);
        item.addView(iconView, new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(themeTextColor());
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setPadding(0, dp(6), 0, 0);
        item.addView(label);

        item.setOnClickListener(view -> {
            optionPrefs().edit().putString("appIcon", name).apply();
            applyLauncherIcon(name);
            showOptions();
        });
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(94);
        params.height = dp(102);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        panel.addView(item, params);
    }

    private void applyLauncherIcon(String name) {
        String selected = "Заметки".equals(name) ? "LauncherNotes" : "LauncherCalculator";
        String[] aliases = {"LauncherCalculator", "LauncherNotes"};
        PackageManager manager = getPackageManager();
        String classPackage = MainActivity.class.getPackage().getName();
        try {
            ComponentName selectedComponent = new ComponentName(this, classPackage + "." + selected);
            manager.setComponentEnabledSetting(
                    selectedComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            Log.e(TAG, "enable launcher alias failed", e);
            toast("Не удалось включить новую иконку");
            return;
        }
        for (String alias : aliases) {
            if (alias.equals(selected)) {
                continue;
            }
            ComponentName component = new ComponentName(this, classPackage + "." + alias);
            try {
                manager.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                );
            } catch (Exception e) {
                Log.w(TAG, "disable launcher alias failed: " + alias, e);
            }
        }
    }

    private String watermarkSummary(String slot) {
        String pos = optionPrefs().getString(watermarkKey(slot, "Position"), defaultWatermarkPosition(slot));
        int opacity = optionPrefs().getInt(watermarkKey(slot, "Opacity"), 70);
        int scale = optionPrefs().getInt(watermarkKey(slot, "Scale"), 50);
        boolean crop = optionPrefs().getBoolean(watermarkKey(slot, "Crop"), false);
        boolean selected = !optionPrefs().getString(watermarkKey(slot, "Uri"), "").isEmpty();
        return (selected ? "Выбран" : "Не выбран") + " · " + pos + " · " + scale + "% · " + opacity + "% · crop " + (crop ? "вкл" : "выкл");
    }

    private void showWatermarkOptions(String slot) {
        if (activeWatermarkDialog != null && activeWatermarkDialog.isShowing()) {
            activeWatermarkDialog.dismiss();
        }
        pendingWatermarkSlot = slot;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), 0);

        String uri = optionPrefs().getString(watermarkKey(slot, "Uri"), "");
        WatermarkPreviewView preview = new WatermarkPreviewView(this, optionPrefs(), slot);
        if (!uri.isEmpty()) {
            try (InputStream input = getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
                preview.setWatermark(BitmapFactory.decodeStream(input));
            } catch (Exception e) {
                Log.w(TAG, "watermark preview failed", e);
            }
        }
        panel.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(430)
        ));
        TextView dragHint = text("1 палец: переместить. 2 пальца: масштаб и поворот. В crop-режиме 1 палец двигает область crop.");
        panel.addView(dragHint);

        Button choose = primaryButton("Выбрать изображение");
        choose.setOnClickListener(view -> pickWatermarkImage(slot));
        panel.addView(choose);

        TextView scaleLabel = text("Масштаб: " + optionPrefs().getInt(watermarkKey(slot, "Scale"), 50) + "%");
        panel.addView(scaleLabel);
        SeekBar scale = new SeekBar(this);
        scale.setMax(100);
        scale.setProgress(optionPrefs().getInt(watermarkKey(slot, "Scale"), 50));
        scale.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.max(10, progress);
                optionPrefs().edit().putInt(watermarkKey(slot, "Scale"), value).apply();
                scaleLabel.setText("Масштаб: " + value + "%");
                preview.invalidate();
            }
        });
        panel.addView(scale);

        TextView opacityLabel = text("Прозрачность: " + optionPrefs().getInt(watermarkKey(slot, "Opacity"), 70) + "%");
        panel.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(optionPrefs().getInt(watermarkKey(slot, "Opacity"), 70));
        opacity.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                optionPrefs().edit().putInt(watermarkKey(slot, "Opacity"), progress).apply();
                opacityLabel.setText("Прозрачность: " + progress + "%");
                preview.invalidate();
            }
        });
        panel.addView(opacity);

        Switch crop = new Switch(this);
        crop.setText("Crop по области водяного знака");
        crop.setTextColor(themeTextColor());
        crop.setChecked(optionPrefs().getBoolean(watermarkKey(slot, "Crop"), false));
        crop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            optionPrefs().edit().putBoolean(watermarkKey(slot, "Crop"), isChecked).apply();
            preview.setCropMode(isChecked);
            preview.invalidate();
        });
        panel.addView(crop);
        preview.setCropMode(crop.isChecked());

        Button position = secondaryButton("Расположение: " + optionPrefs().getString(watermarkKey(slot, "Position"), defaultWatermarkPosition(slot)));
        position.setOnClickListener(view -> chooseWatermarkPosition(slot));
        panel.addView(position);

        activeWatermarkDialog = new AlertDialog.Builder(this)
                .setTitle("Водяной знак " + ("top".equals(slot) ? "верхний" : "нижний"))
                .setView(panel)
                .setPositiveButton("Закрыть", (dialog, which) -> showOptions())
                .create();
        activeWatermarkDialog.setOnDismissListener(dialog -> {
            if (activeWatermarkDialog == dialog) {
                activeWatermarkDialog = null;
            }
        });
        activeWatermarkDialog.show();
    }

    private void chooseWatermarkPosition(String slot) {
        if (activeWatermarkDialog != null && activeWatermarkDialog.isShowing()) {
            activeWatermarkDialog.dismiss();
        }
        String[] values = {"Сверху слева", "Сверху справа", "По центру", "Снизу слева", "Снизу справа", "Свободно"};
        new AlertDialog.Builder(this)
                .setTitle("Расположение")
                .setItems(values, (dialog, which) -> {
                    optionPrefs().edit().putString(watermarkKey(slot, "Position"), values[which]).apply();
                    showWatermarkOptions(slot);
                })
                .show();
    }

    private void pickWatermarkImage(String slot) {
        pendingWatermarkSlot = slot;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, WATERMARK_PICK_REQUEST);
    }

    private String watermarkKey(String slot, String suffix) {
        return "watermark" + ("top".equals(slot) ? "Top" : "Bottom") + suffix;
    }

    private String defaultWatermarkPosition(String slot) {
        return "top".equals(slot) ? "Сверху справа" : "Снизу справа";
    }

    private void chooseMapPack() {
        String[] labels = {
                "central-fed-district.map · 752M",
                "crimean-fed-district.map · 33M",
                "north-caucasus-fed-district.map · 135M",
                "northwestern-fed-district.map · 1.3G",
                "south-fed-district.map · 296M",
                "volga-fed-district.map · 771M",
                "ural-fed-district.map · 609M",
                "siberian-fed-district.map · 1.6G",
                "far-eastern-fed-district-1.map · 1.5G",
                "far-eastern-fed-district-2.map · 52M",
                "kaliningrad.map · 24M",
                "Открыть каталог mapsforge"
        };
        String[] files = {
                "central-fed-district.map",
                "crimean-fed-district.map",
                "north-caucasus-fed-district.map",
                "northwestern-fed-district.map",
                "south-fed-district.map",
                "volga-fed-district.map",
                "ural-fed-district.map",
                "siberian-fed-district.map",
                "far-eastern-fed-district-1.map",
                "far-eastern-fed-district-2.map",
                "kaliningrad.map"
        };
        new AlertDialog.Builder(this)
                .setTitle("Выбор карт")
                .setItems(labels, (dialog, which) -> {
                    if (which == labels.length - 1) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/russia/"));
                        startActivity(intent);
                        return;
                    }
                    String file = files[which];
                    optionPrefs().edit()
                            .putString("mapPack", file)
                            .putString("mapPackUrl", "https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/russia/" + file)
                            .apply();
                    File local = selectedMapFileForName(file);
                    if (local.exists()) {
                        toast("Карта выбрана: " + file);
                        if ("map".equals(currentScreen)) {
                            showCameraMapPanel();
                        } else {
                            showOptions();
                        }
                    } else {
                        confirmDownloadMapPack(file);
                    }
                })
                .show();
    }

    private void confirmDownloadMapPack(String file) {
        new AlertDialog.Builder(this)
                .setTitle("Карта не скачана")
                .setMessage(file + "\n\nВыбрана как активная карта. Скачать файл сейчас?")
                .setNegativeButton("Не сейчас", (dialog, which) -> {
                    if ("map".equals(currentScreen)) {
                        showCameraMapPanel();
                    } else {
                        showOptions();
                    }
                })
                .setPositiveButton("Скачать", (dialog, which) -> downloadMapPack(file))
                .show();
    }

    private void downloadMapPack(String file) {
        String url = "https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/russia/" + file;
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("DownloadManager unavailable");
            }
            DownloadManager.Request request = new DownloadManager.Request(android.net.Uri.parse(url));
            request.setTitle("Blue Ocean map: " + file);
            request.setDescription("Mapsforge Russia");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, file);
            manager.enqueue(request);
            toast("Скачивание карты начато");
            if ("map".equals(currentScreen)) {
                showCameraMapPanel();
            } else {
                showOptions();
            }
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(intent);
        }
    }

    private File selectedMapFileForName(String file) {
        File appDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (appDir != null) {
            return new File(appDir, file);
        }
        return new File(getFilesDir(), file);
    }

    private void showAbout() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), 0);
        TextView brand = new TextView(this);
        brand.setText("🐙\nBLUE OCEAN");
        brand.setTextColor(Color.rgb(125, 211, 252));
        brand.setTextSize(34);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(12), 0, dp(16));
        panel.addView(brand);
        TextView info = text("Приложение создано для приватной работы с зашифрованными фото, заметками, локальной галереей и резервной отправкой в TGFinder/MEGA.\n\nНазвание: Blue Ocean v2\nВерсия: 0.2.0\nРазработчик: Blue Ocean");
        panel.addView(info);
        new AlertDialog.Builder(this)
                .setTitle("Blue Ocean v2")
                .setView(panel)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showMegaConnectDialog(String email, String folder, String relayUrl) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), 0);

        EditText password = compactInput("Пароль MEGA");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(password);

        TextView hint = text("Пароль отправляется на backend один раз для подключения аккаунта. В Android он не сохраняется.");
        panel.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle("MEGA аккаунт")
                .setView(panel)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Подключить", (dialog, which) -> connectMegaAccount(email, folder, relayUrl, password.getText().toString()))
                .show();
    }

    private void connectMegaAccount(String email, String folder, String relayUrl, String password) {
        if (email == null || email.trim().isEmpty()) {
            toast("Введите MEGA e-mail");
            return;
        }
        if (password == null || password.isEmpty()) {
            toast("Введите пароль MEGA");
            return;
        }
        toast("Подключаю MEGA...");
        new Thread(() -> {
            String error = "";
            MegaRelayClient.Status status = null;
            try {
                MegaRelayClient client = new MegaRelayClient(
                        relayUrl,
                        backupConfigStore.tgUserId(),
                        deviceIdentityStore.deviceId(),
                        deviceIdentityStore.deviceToken()
                );
                status = client.connect(email, password, folder);
            } catch (Exception e) {
                error = e.getMessage() == null ? "MEGA connect failed" : e.getMessage();
                Log.e(TAG, "MEGA connect failed", e);
            }
            String finalError = error;
            MegaRelayClient.Status finalStatus = status;
            handler.post(() -> {
                if (finalError.isEmpty() && finalStatus != null) {
                    backupConfigStore.setMegaEnabled(true);
                    toast("MEGA: " + (finalStatus.connected ? "подключено" : "зарегистрировано на backend"));
                } else {
                    toast("MEGA: " + finalError);
                }
                showSyncOptions();
            });
        }, "BlueOceanMegaConnect").start();
    }

    private void runMegaBackup() {
        if (!backupConfigStore.hasMegaTarget()) {
            toast("Включите MEGA и задайте e-mail/папку");
            return;
        }
        List<BackupQueue.Item> pending = backupQueue.pendingItemsForTarget("mega");
        if (pending.isEmpty()) {
            toast("Очередь MEGA пуста");
            return;
        }
        toast("Отправляю encrypted blobs в MEGA relay...");
        new Thread(() -> {
            int uploaded = 0;
            int failed = 0;
            String error = "";
            try {
                MegaRelayClient client = megaRelayClient();
                for (BackupQueue.Item item : pending) {
                    try {
                        byte[] encryptedBlob = photoStore.loadEncryptedBlob(item.blobId);
                        client.uploadEncryptedBlob(backupConfigStore.megaFolder(), "blueocean-" + item.blobId + ".blob", encryptedBlob);
                        backupQueue.markUploaded(item.blobId, "mega");
                        uploaded++;
                    } catch (Exception itemError) {
                        failed++;
                        backupQueue.markFailed(item.blobId, "mega", itemError.getMessage());
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "MEGA upload failed" : e.getMessage();
                Log.e(TAG, "MEGA backup failed", e);
            }
            int finalUploaded = uploaded;
            int finalFailed = failed;
            String finalError = error;
            handler.post(() -> {
                if (finalError.isEmpty()) {
                    toast("MEGA: отправлено " + finalUploaded + ", ошибок " + finalFailed);
                } else {
                    toast("MEGA: " + finalError);
                }
                showBackupQueue();
            });
        }, "BlueOceanMegaBackup").start();
    }

    private void runTgFinderBackup() {
        if (!backupConfigStore.hasTgFinderTarget()) {
            toast("Задайте TGFinder URL");
            return;
        }
        List<BackupQueue.Item> pending = backupQueue.pendingItemsForTarget("tgfinder");
        if (pending.isEmpty()) {
            toast("Очередь TGFinder пуста");
            return;
        }
        toast("Отправляю encrypted blobs в TGFinder...");
        new Thread(() -> {
            int uploaded = 0;
            int failed = 0;
            String error = "";
            try {
                TgFinderClient client = tgFinderClient();
                TgFinderClient.Workspace workspace = client.workspace();
                if (!workspace.ready || workspace.rootFolderId <= 0) {
                    throw new IllegalStateException(workspace.hint.isEmpty() ? "Нажмите /start в TGFinder боте" : workspace.hint);
                }
                int folderId = client.findOrCreateFolder(workspace.rootFolderId, backupConfigStore.tgFolderName());
                for (BackupQueue.Item item : pending) {
                    try {
                        byte[] encryptedBlob = photoStore.loadEncryptedBlob(item.blobId);
                        client.uploadEncryptedBlob(folderId, "blueocean-" + item.blobId + ".blob", encryptedBlob);
                        backupQueue.markUploaded(item.blobId, "tgfinder");
                        uploaded++;
                    } catch (Exception itemError) {
                        failed++;
                        backupQueue.markFailed(item.blobId, "tgfinder", itemError.getMessage());
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "TGFinder upload failed" : e.getMessage();
                Log.e(TAG, "TGFinder backup failed", e);
            }
            int finalUploaded = uploaded;
            int finalFailed = failed;
            String finalError = error;
            handler.post(() -> {
                if (finalError.isEmpty()) {
                    toast("TGFinder: отправлено " + finalUploaded + ", ошибок " + finalFailed);
                } else {
                    toast("TGFinder: " + normalizeTgFinderError(finalError));
                }
                showBackupSettings();
            });
        }, "BlueOceanTgFinderBackup").start();
    }

    private void sharePairCommand() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, deviceIdentityStore.pairCommand());
        intent.setPackage("org.telegram.messenger");
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.content.Intent fallback = new android.content.Intent(android.content.Intent.ACTION_SEND);
            fallback.setType("text/plain");
            fallback.putExtra(android.content.Intent.EXTRA_TEXT, deviceIdentityStore.pairCommand());
            android.content.Intent chooser = android.content.Intent.createChooser(fallback, "Отправить команду /pair");
            startActivity(chooser);
        }
    }

    private String tgUserStatusText() {
        if (backupConfigStore.isDevicePaired()) {
            String value = backupConfigStore.tgUserId();
            return value.isEmpty() ? "проверяется по device token" : value;
        }
        return backupConfigStore.tgUserId().isEmpty() ? "не задан" : backupConfigStore.tgUserId();
    }

    private void startTelegramPairing() {
        toast("Создаю код привязки TGFinder...");
        new Thread(() -> {
            TgFinderClient.PairingStart pairing = null;
            String error = "";
            try {
                TgFinderClient client = new TgFinderClient(
                        backupConfigStore.tgFinderUrl(),
                        "",
                        deviceIdentityStore.deviceId(),
                        deviceIdentityStore.deviceToken(),
                        false
                );
                pairing = client.startPairing();
            } catch (Exception e) {
                error = e.getMessage() == null ? "TGFinder pairing failed" : e.getMessage();
                Log.e(TAG, "TGFinder pairing start failed", e);
            }
            TgFinderClient.PairingStart finalPairing = pairing;
            String finalError = error;
            handler.post(() -> {
                if (finalPairing != null && !finalPairing.botStartUrl.isEmpty()) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Blue Ocean pair", finalPairing.pairCommand));
                    }
                    showPairingDialog(finalPairing);
                } else {
                    toast("TGFinder: " + normalizeTgFinderError(finalError));
                }
            });
        }, "BlueOceanTgFinderPairingStart").start();
    }

    private void showPairingDialog(TgFinderClient.PairingStart pairing) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), 0);
        TextView code = text("Код привязки:\n" + pairing.pairCommand
                + "\n\nЯ уже скопировал его в буфер. Если Telegram не отправит код сам, просто вставьте эту команду боту TGFinder в личку.");
        panel.addView(code);
        new AlertDialog.Builder(this)
                .setTitle("TGFinder")
                .setView(panel)
                .setPositiveButton("Открыть TGFinder", (dialog, which) -> {
                    openTgFinderPairing(pairing);
                    pollPairingStatus(0);
                })
                .setNegativeButton("Скопировать", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Blue Ocean pair", pairing.pairCommand));
                    }
                    toast("Код скопирован");
                })
                .show();
    }

    private void openTgFinderPairing(TgFinderClient.PairingStart pairing) {
        String code = pairing.code == null ? "" : pairing.code.trim();
        Intent tgIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=ExplorerTG_bot&start=pair_" + code));
        try {
            startActivity(tgIntent);
            toast("В TGFinder нажмите Start или отправьте скопированный /pair код");
            return;
        } catch (Exception ignored) {
            // fall through
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pairing.botStartUrl)));
            toast("В TGFinder нажмите Start или отправьте скопированный /pair код");
        } catch (Exception openError) {
            sharePairText(pairing.pairCommand);
        }
    }

    private void pollPairingStatus(int attempt) {
        if (attempt >= 10 || backupConfigStore.isDevicePaired()) {
            return;
        }
        handler.postDelayed(() -> new Thread(() -> {
            int resolvedUserId = 0;
            try {
                TgFinderClient client = new TgFinderClient(
                        backupConfigStore.tgFinderUrl(),
                        "",
                        deviceIdentityStore.deviceId(),
                        deviceIdentityStore.deviceToken(),
                        true
                );
                resolvedUserId = client.resolveTelegramUserId();
            } catch (Exception ignored) {
                // User may not have pressed Start yet.
            }
            int finalResolvedUserId = resolvedUserId;
            handler.post(() -> {
                if (finalResolvedUserId > 0) {
                    backupConfigStore.save(
                            backupConfigStore.megaEmail(),
                            backupConfigStore.megaFolder(),
                            backupConfigStore.megaRelayUrl(),
                            backupConfigStore.tgFinderUrl(),
                            String.valueOf(finalResolvedUserId),
                            backupConfigStore.tgFolderName(),
                            backupConfigStore.tgFinderToken()
                    );
                    backupConfigStore.setDevicePaired(true);
                    toast("TGFinder привязан: " + finalResolvedUserId);
                    if ("sync".equals(currentScreen)) {
                        showSyncOptions();
                    }
                } else {
                    pollPairingStatus(attempt + 1);
                }
            });
        }, "BlueOceanTgFinderPairPoll").start(), 3_000);
    }

    private void sharePairText(String text) {
        android.content.Intent fallback = new android.content.Intent(android.content.Intent.ACTION_SEND);
        fallback.setType("text/plain");
        fallback.putExtra(android.content.Intent.EXTRA_TEXT, text);
        startActivity(android.content.Intent.createChooser(fallback, "Отправить код привязки"));
    }

    private void verifyTgFinderPairing() {
        toast("Проверяю привязку TGFinder...");
        refreshPairedTelegramUserId(true);
    }

    private void refreshPairedTelegramUserId(boolean showResult) {
        if (!backupConfigStore.isDevicePaired() && !showResult) {
            return;
        }
        new Thread(() -> {
            int resolvedUserId = 0;
            String error = "";
            try {
                TgFinderClient client = new TgFinderClient(
                        backupConfigStore.tgFinderUrl(),
                        "",
                        deviceIdentityStore.deviceId(),
                        deviceIdentityStore.deviceToken(),
                        true
                );
                resolvedUserId = client.resolveTelegramUserId();
            } catch (Exception e) {
                error = e.getMessage() == null ? "TGFinder pairing failed" : e.getMessage();
                Log.e(TAG, "TGFinder pairing check failed", e);
            }
            int finalResolvedUserId = resolvedUserId;
            String finalError = error;
            handler.post(() -> {
                if (finalError.isEmpty() && finalResolvedUserId > 0) {
                    Log.i(TAG, "TGFinder device resolved as " + finalResolvedUserId);
                    String previousUserId = backupConfigStore.tgUserId();
                    backupConfigStore.save(
                            backupConfigStore.megaEmail(),
                            backupConfigStore.megaFolder(),
                            backupConfigStore.megaRelayUrl(),
                            backupConfigStore.tgFinderUrl(),
                            String.valueOf(finalResolvedUserId),
                            backupConfigStore.tgFolderName(),
                            backupConfigStore.tgFinderToken()
                    );
                    backupConfigStore.setDevicePaired(true);
                    if (showResult) {
                        toast("TGFinder: " + finalResolvedUserId);
                    }
                } else {
                    if (!finalError.isEmpty()) {
                        Log.e(TAG, "TGFinder device resolve failed: " + finalError);
                    }
                    if (showResult) {
                        backupConfigStore.setDevicePaired(false);
                        toast("Отправьте /pair боту TGFinder");
                    }
                }
                if (showResult) {
                    showSyncOptions();
                }
            });
        }, "BlueOceanTgFinderPairing").start();
    }

    private String normalizeTgFinderError(String error) {
        String value = error == null ? "" : error;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("device не привязан") || lower.contains("device token")) {
            backupConfigStore.setDevicePaired(false);
            return "доступ устройства сброшен. Отправьте /pair боту или нажмите «Восстановить TGFinder».";
        }
        return value;
    }

    private TgFinderClient tgFinderClient() {
        boolean paired = backupConfigStore.isDevicePaired();
        return new TgFinderClient(
                backupConfigStore.tgFinderUrl(),
                paired ? "" : backupConfigStore.tgUserId(),
                deviceIdentityStore.deviceId(),
                deviceIdentityStore.deviceToken(),
                paired
        );
    }

    private MegaRelayClient megaRelayClient() {
        boolean paired = backupConfigStore.isDevicePaired();
        return new MegaRelayClient(
                backupConfigStore.megaRelayUrl(),
                paired ? "" : backupConfigStore.tgUserId(),
                deviceIdentityStore.deviceId(),
                deviceIdentityStore.deviceToken()
        );
    }

    private void importFromTgFinder() {
        if (!backupConfigStore.hasTgFinderTarget()) {
            toast("Задайте TGFinder URL");
            return;
        }
        toast("Скачиваю encrypted blobs из TGFinder...");
        new Thread(() -> {
            int imported = 0;
            String error = "";
            try {
                TgFinderClient client = tgFinderClient();
                TgFinderClient.Workspace workspace = client.workspace();
                if (!workspace.ready || workspace.rootFolderId <= 0) {
                    throw new IllegalStateException(workspace.hint.isEmpty() ? "Нажмите /start в TGFinder боте" : workspace.hint);
                }
                int folderId = client.findOrCreateFolder(workspace.rootFolderId, backupConfigStore.tgFolderName());
                for (TgFinderClient.RemoteFile file : client.listFiles(folderId)) {
                    if (!file.filename.endsWith(".blob")) {
                        continue;
                    }
                    byte[] encryptedBlob = client.downloadFile(file.id);
                    PhotoStore.SavedPhoto saved = photoStore.importEncryptedBlob(encryptedBlob);
                    vaultStore.addEntry("Импорт TGFinder: " + file.filename, "", saved.id, saved.encryptedSize);
                    imported++;
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "TGFinder import failed" : e.getMessage();
                Log.e(TAG, "TGFinder import failed", e);
            }
            int finalImported = imported;
            String finalError = error;
            handler.post(() -> {
                if (finalError.isEmpty()) {
                    toast("Импортировано: " + finalImported);
                    showVault();
                } else {
                    toast("TGFinder: " + normalizeTgFinderError(finalError));
                    showBackupSettings();
                }
            });
        }, "BlueOceanTgFinderImport").start();
    }

    private void showTelegramGroupBrowser() {
        showTelegramGroupBrowser(-1);
    }

    private void showTelegramGroupBrowser(int folderId) {
        currentScreen = "telegram";
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("Супергруппа");
        addPanel(root, "Загрузка", "Получаю список файлов из TGFinder...");
        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);
        setContentView(wrap(root));

        new Thread(() -> {
            TgFinderClient.Listing listing = new TgFinderClient.Listing(new ArrayList<>(), new ArrayList<>());
            String error = "";
            try {
                TgFinderClient client = tgFinderClient();
                TgFinderClient.Workspace workspace = client.workspace();
                if (!workspace.ready || workspace.rootFolderId <= 0) {
                    throw new IllegalStateException(workspace.hint.isEmpty() ? "Нажмите /start в TGFinder боте" : workspace.hint);
                }
                tgRootFolderId = workspace.rootFolderId;
                int targetFolderId = folderId > 0 ? folderId : workspace.rootFolderId;
                currentTgFolderId = targetFolderId;
                listing = client.listFolder(targetFolderId);
            } catch (Exception e) {
                error = e.getMessage() == null ? "TGFinder list failed" : e.getMessage();
                Log.e(TAG, "TGFinder list failed", e);
            }
            TgFinderClient.Listing finalListing = listing;
            String finalError = error;
            handler.post(() -> renderTelegramGroupFiles(finalListing, finalError));
        }, "BlueOceanTgFinderList").start();
    }

    private void renderTelegramGroupFiles(TgFinderClient.Listing listing, String error) {
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("Супергруппа");
        if (!error.isEmpty()) {
            addPanel(root, "Ошибка", normalizeTgFinderError(error));
        } else if (listing.folders.isEmpty() && listing.files.isEmpty()) {
            addPanel(root, "Пусто", "В выбранной папке/топике TGFinder файлов нет.");
        } else {
            addPanel(root, "Содержимое", "Папок: " + listing.folders.size() + "\nФайлов: " + listing.files.size());
            for (TgFinderClient.RemoteFolder folder : listing.folders) {
                Button open = secondaryButton("▦  " + (folder.name.isEmpty() ? "Папка" : folder.name) + " · " + folder.objectCount);
                open.setOnClickListener(view -> showTelegramGroupBrowser(folder.id));
                root.addView(open);
            }
            for (TgFinderClient.RemoteFile file : listing.files) {
                String size = file.fileSize <= 0 ? "размер неизвестен" : file.fileSize + " байт";
                addPanel(root, file.filename.isEmpty() ? "Без имени" : file.filename, "ID: " + file.id + "\n" + size);
                Button download = secondaryButton("Скачать этот файл");
                download.setOnClickListener(view -> importRemoteTgFile(file));
                root.addView(download);
            }
        }
        Button refresh = primaryButton("Обновить список");
        refresh.setOnClickListener(view -> showTelegramGroupBrowser(currentTgFolderId));
        root.addView(refresh);
        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> {
            if (tgRootFolderId > 0 && currentTgFolderId > 0 && currentTgFolderId != tgRootFolderId) {
                showTelegramGroupBrowser(tgRootFolderId);
            } else {
                showCloudAndGallery();
            }
        });
        root.addView(back);
        setContentView(wrap(root));
    }

    private void importRemoteTgFile(TgFinderClient.RemoteFile file) {
        toast("Скачиваю " + (file.filename.isEmpty() ? "файл" : file.filename));
        new Thread(() -> {
            String error = "";
            try {
                byte[] encryptedBlob = tgFinderClient().downloadFile(file.id);
                PhotoStore.SavedPhoto saved = photoStore.importEncryptedBlob(encryptedBlob);
                vaultStore.addEntry("Импорт TGFinder: " + file.filename, "", saved.id, saved.encryptedSize);
            } catch (Exception e) {
                error = e.getMessage() == null ? "TGFinder download failed" : e.getMessage();
                Log.e(TAG, "TGFinder selected import failed", e);
            }
            String finalError = error;
            handler.post(() -> {
                if (finalError.isEmpty()) {
                    toast("Файл скачан в Blue Ocean");
                    showJournal();
                } else {
                    toast("TGFinder: " + normalizeTgFinderError(finalError));
                    showTelegramGroupBrowser();
                }
            });
        }, "BlueOceanTgFinderSelectedImport").start();
    }

    private void importRemoteMegaFile(MegaRelayClient.RemoteFile file) {
        toast("Скачиваю " + (file.name.isEmpty() ? "файл" : file.name));
        new Thread(() -> {
            String error = "";
            try {
                byte[] encryptedBlob = megaRelayClient().downloadFile(file.path);
                PhotoStore.SavedPhoto saved = photoStore.importEncryptedBlob(encryptedBlob);
                VaultEntry entry = vaultStore.addEntry("Импорт MEGA: " + file.name, "", saved.id, saved.encryptedSize);
                assignImportedGalleryFolder(entry, file.path);
            } catch (Exception e) {
                error = e.getMessage() == null ? "MEGA download failed" : e.getMessage();
                Log.e(TAG, "MEGA selected import failed", e);
            }
            String finalError = error;
            handler.post(() -> {
                if (finalError.isEmpty()) {
                    toast("Файл скачан в Blue Ocean");
                    showJournal();
                } else {
                    toast("MEGA: " + finalError);
                    showMegaCloud(currentMegaFolder);
                }
            });
        }, "BlueOceanMegaSelectedImport").start();
    }

    private void assignImportedGalleryFolder(VaultEntry entry, String remotePath) {
        String folder = galleryFolderFromRemotePath(remotePath);
        if (folder.isEmpty()) {
            return;
        }
        Set<String> folders = galleryFolders();
        folders.add(folder);
        saveGalleryFolders(folders);
        optionPrefs().edit().putString("galleryEntryFolder." + entry.id, folder).apply();
    }

    private String galleryFolderFromRemotePath(String remotePath) {
        String value = remotePath == null ? "" : remotePath.trim();
        int slash = value.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        String folder = value.substring(0, slash);
        String root = backupConfigStore.megaFolder();
        if (folder.equals(root)) {
            return root;
        }
        if (folder.startsWith(root + "/")) {
            return folder.substring(root.length() + 1);
        }
        return folder;
    }

    private void showBackupQueue() {
        currentScreen = "queue";
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("Очередь");
        List<BackupQueue.Item> items = backupQueue.allItems();
        if (items.isEmpty()) {
            addPanel(root, "Пусто", "Очередь отправки в MEGA и TGFinder пока пустая.");
        } else {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            for (BackupQueue.Item item : items) {
                StringBuilder body = new StringBuilder();
                body.append("Blob: ").append(item.blobId);
                body.append("\nКанал: ").append(item.target.isEmpty() ? "не указан" : item.target);
                body.append("\nСтатус: ").append(item.status.isEmpty() ? "unknown" : item.status);
                body.append("\nРазмер: ").append(item.encryptedSize).append(" байт");
                if (item.createdAtMs > 0L) {
                    body.append("\nСоздано: ").append(format.format(new Date(item.createdAtMs)));
                }
                if (!item.error.isEmpty()) {
                    body.append("\nОшибка: ").append(item.error);
                }
                addPanel(root, "Encrypted backup", body.toString());
            }
        }

        if (backupConfigStore.isMegaEnabled()) {
            Button runMega = primaryButton("Отправить pending в MEGA");
            runMega.setOnClickListener(view -> runMegaBackup());
            root.addView(runMega);
        }

        if (backupConfigStore.isTgFinderEnabled()) {
            Button runTg = primaryButton("Отправить pending в TGFinder");
            runTg.setOnClickListener(view -> runTgFinderBackup());
            root.addView(runTg);
        }

        Button settings = secondaryButton("Настройки облака");
        settings.setOnClickListener(view -> showBackupSettings());
        root.addView(settings);

        Button clear = secondaryButton("Очистить очередь");
        clear.setOnClickListener(view -> confirmClearQueue());
        root.addView(clear);

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);

        setContentView(wrap(root));
    }

    private void confirmClearQueue() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить очередь?")
                .setMessage("Будут удалены только записи очереди отправки. Зашифрованные фото останутся в Blue Ocean.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    try {
                        backupQueue.clear();
                        toast("Очередь очищена");
                        showBackupQueue();
                    } catch (Exception e) {
                        toast("Не удалось очистить очередь");
                    }
                })
                .show();
    }

    private void startCameraWhenReady() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        startCameraThread();
        if (cameraPreview.isAvailable()) {
            openCamera();
        } else {
            cameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    configurePreviewTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    stopCamera();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }
        cameraThread = new HandlerThread("BlueOceanCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void openCamera() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (manager == null) {
                toast("Камера недоступна");
                return;
            }
            String cameraId = chooseCamera(manager);
            if (cameraId == null) {
                toast("Камера не найдена");
                return;
            }
            configureCameraSizes(manager, cameraId);
            if (jpegSize == null || previewSize == null) {
                toast("Камера не отдала размеры");
                return;
            }

            imageReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] jpeg = new byte[buffer.remaining()];
                    buffer.get(jpeg);
                    saveCapturedPhoto(jpeg);
                } catch (Exception e) {
                    Log.e(TAG, "Saving captured image failed", e);
                    handler.post(() -> toast("Не удалось открыть редактор фото"));
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }, cameraHandler);

            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "CameraDevice error: " + error);
                    camera.close();
                    cameraDevice = null;
                    handler.post(() -> toast("Ошибка камеры"));
                }
            }, cameraHandler);
        } catch (Exception e) {
            Log.e(TAG, "openCamera failed", e);
            toast("Не удалось открыть камеру");
        }
    }

    private void configureCameraSizes(CameraManager manager, String cameraId) throws CameraAccessException {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        flashSupported = flashAvailable != null && flashAvailable;
        Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        sensorOrientation = orientation == null ? 0 : orientation;
        Float maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        maxDigitalZoom = maxZoom == null ? 1f : Math.max(1f, maxZoom);
        sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return;
        }

        Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
        jpegSize = chooseReasonableSize(jpegSizes, 1920, 1080);

        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        previewSize = chooseReasonableSize(previewSizes, 1280, 720);

        handler.post(() -> {
            updateFlashButton();
            if (cameraPreview != null && previewSize != null) {
                if (isDisplayPortrait()) {
                    cameraPreview.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                } else {
                    cameraPreview.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                }
                cameraPreview.post(() -> configurePreviewTransform(cameraPreview.getWidth(), cameraPreview.getHeight()));
            }
        });

        Log.i(TAG, "Camera sizes: jpeg=" + jpegSize + " preview=" + previewSize);
    }

    private Size chooseReasonableSize(Size[] sizes, int maxWidth, int maxHeight) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }

        Size best = null;
        long bestPixels = 0L;
        for (Size size : sizes) {
            int width = size.getWidth();
            int height = size.getHeight();
            long pixels = (long) width * (long) height;
            if (width <= maxWidth && height <= maxHeight && pixels > bestPixels) {
                best = size;
                bestPixels = pixels;
            }
        }

        if (best != null) {
            return best;
        }

        best = sizes[0];
        bestPixels = (long) best.getWidth() * (long) best.getHeight();
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * (long) size.getHeight();
            if (pixels < bestPixels) {
                best = size;
                bestPixels = pixels;
            }
        }
        return best;
    }

    private String chooseCamera(CameraManager manager) throws CameraAccessException {
        String fallback = null;
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (fallback == null) {
                fallback = cameraId;
            }
            int target = useFrontCamera ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
            if (facing != null && facing == target) {
                return cameraId;
            }
        }
        return fallback;
    }

    private void startPreview() {
        try {
            if (cameraDevice == null || cameraPreview == null || !cameraPreview.isAvailable()) {
                return;
            }
            SurfaceTexture texture = cameraPreview.getSurfaceTexture();
            if (texture == null) {
                return;
            }
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            configurePreviewTransform(cameraPreview.getWidth(), cameraPreview.getHeight());
            Surface previewSurface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            applyPreviewFlashMode(previewRequestBuilder);
            applyZoomCrop(previewRequestBuilder);
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            cameraSession = session;
                            try {
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "setRepeatingRequest failed", e);
                                handler.post(() -> toast("Preview недоступен"));
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Camera session configuration failed");
                            handler.post(() -> toast("Не удалось настроить камеру"));
                        }
                    },
                    cameraHandler
            );
        } catch (Exception e) {
            Log.e(TAG, "startPreview failed", e);
            handler.post(() -> toast("Preview не запустился"));
        }
    }

    private void capturePhoto() {
        try {
            if (cameraDevice == null || cameraSession == null || imageReader == null) {
                toast("Камера еще запускается");
                return;
            }
            CaptureRequest.Builder capture = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            capture.addTarget(imageReader.getSurface());
            capture.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            capture.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            applyZoomCrop(capture);
            if (flashSupported && flashEnabled) {
                capture.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            } else {
                capture.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            }
            cameraSession.capture(capture.build(), null, cameraHandler);
        } catch (Exception e) {
            Log.e(TAG, "capturePhoto failed", e);
            toast("Не удалось сделать фото");
        }
    }

    private void saveCapturedPhoto(byte[] jpeg) {
        handler.post(() -> showPhotoEditor(jpeg));
    }

    private void showPhotoEditor(byte[] jpeg) {
        currentScreen = "editor";
        setBackTarget(this::showCamera);
        stopCamera();

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        if (bitmap == null) {
            toast("Фото не прочиталось");
            showVault();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        root.setPadding(dp(10), dp(10), dp(10), safeBottomPad());
        root.setBackgroundColor(Color.BLACK);

        PhotoEditorView editor = new PhotoEditorView(this, bitmap);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        editorParams.setMargins(0, 0, 0, dp(8));
        root.addView(editor, editorParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackground(roundDrawable(Color.rgb(18, 18, 18), 18, 1, Color.rgb(55, 55, 55)));
        root.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        EditText labelInput = compactInput("Текст или эмодзи");

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        panel.addView(tools, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView draw = editorTool("✎");
        draw.setOnClickListener(view -> {
            editor.setMode(PhotoEditorView.MODE_DRAW);
            toast("Карандаш");
        });
        tools.addView(draw, toolParams());

        TextView arrow = editorTool("↗");
        arrow.setOnClickListener(view -> {
            editor.setMode(PhotoEditorView.MODE_ARROW);
            toast("Стрелка");
        });
        tools.addView(arrow, toolParams());

        TextView textMode = editorTool("T");
        textMode.setOnClickListener(view -> {
            editor.setMode(PhotoEditorView.MODE_TEXT);
            toast("Тапните по фото для текста");
        });
        tools.addView(textMode, toolParams());

        TextView emoji = editorTool("🙂");
        emoji.setOnClickListener(view -> {
            editor.setMode(PhotoEditorView.MODE_TEXT);
            chooseEditorEmoji(editor, labelInput);
        });
        tools.addView(emoji, toolParams());

        TextView clear = editorTool("↶");
        clear.setOnClickListener(view -> editor.clearMarkup());
        tools.addView(clear, toolParams());

        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setGravity(Gravity.CENTER);
        panel.addView(colors, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addColorTool(colors, editor, Color.rgb(255, 204, 82));
        addColorTool(colors, editor, Color.WHITE);
        addColorTool(colors, editor, Color.rgb(56, 189, 248));
        addColorTool(colors, editor, Color.rgb(34, 197, 94));
        addColorTool(colors, editor, Color.rgb(248, 113, 113));

        labelInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                editor.setMode(PhotoEditorView.MODE_TEXT);
                editor.setPendingLabel(labelInput.getText().toString());
            }
        });
        labelInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                editor.setPendingLabel(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        panel.addView(labelInput);

        cameraCommentInput = compactInput("Комментарий");
        cameraLocationInput = compactInput("Координаты или место");
        panel.addView(cameraCommentInput);
        panel.addView(cameraLocationInput);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView coordinates = editorAction("⌖");
        coordinates.setOnClickListener(view -> fillCoordinates(cameraLocationInput));
        actions.addView(coordinates, toolParams());

        TextView cancel = editorAction("×");
        cancel.setOnClickListener(view -> showVault());
        actions.addView(cancel, toolParams());

        TextView save = editorSaveAction("✓");
        save.setOnClickListener(view -> saveEditedPhoto(editor.exportJpeg()));
        actions.addView(save, toolParams());

        setContentView(root);
    }

    private void saveEditedPhoto(byte[] jpeg) {
        try {
            byte[] finalJpeg = applyWatermarks(jpeg);
            PhotoStore.SavedPhoto saved = photoStore.saveJpeg(finalJpeg, activePin);
            String comment = cameraCommentInput == null ? "" : cameraCommentInput.getText().toString();
            String location = cameraLocationInput == null ? "" : cameraLocationInput.getText().toString();
            vaultStore.addEntry(comment.isEmpty() ? "Фото с камеры" : comment, location, saved.id, saved.encryptedSize);
            if (backupConfigStore.hasEnabledTarget()) {
                backupQueue.enqueuePhoto(saved.id, saved.encryptedSize, backupConfigStore.isMegaEnabled(), backupConfigStore.isTgFinderEnabled());
            }
            handler.post(() -> {
                if (backupConfigStore.hasEnabledTarget()) {
                    toast("Фото зашифровано и добавлено в очередь: " + backupConfigStore.enabledTargetsText());
                } else {
                    toast("Фото зашифровано. Синхронизация выключена");
                }
                showVault();
            });
        } catch (Exception e) {
            Log.e(TAG, "saveEditedPhoto failed", e);
            handler.post(() -> toast("Не удалось зашифровать фото"));
        }
    }

    private byte[] applyWatermarks(byte[] jpeg) {
        Bitmap base = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        if (base == null) {
            return jpeg;
        }
        Bitmap mutable = base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        drawWatermarkSlot(canvas, mutable.getWidth(), mutable.getHeight(), "top");
        drawWatermarkSlot(canvas, mutable.getWidth(), mutable.getHeight(), "bottom");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mutable.compress(Bitmap.CompressFormat.JPEG, 92, out);
        if (mutable != base) {
            mutable.recycle();
        }
        base.recycle();
        return out.toByteArray();
    }

    private void drawWatermarkSlot(Canvas canvas, int width, int height, String slot) {
        String uri = optionPrefs().getString(watermarkKey(slot, "Uri"), "");
        if (uri.isEmpty()) {
            return;
        }
        Bitmap watermark = null;
        try (InputStream input = getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
            watermark = BitmapFactory.decodeStream(input);
            if (watermark == null) {
                return;
            }
            int scalePercent = Math.max(10, optionPrefs().getInt(watermarkKey(slot, "Scale"), 50));
            float targetWidth = width * (scalePercent / 100f);
            boolean crop = optionPrefs().getBoolean(watermarkKey(slot, "Crop"), false);
            float ratio = targetWidth / Math.max(1, watermark.getWidth());
            float targetHeight = crop ? targetWidth * optionPrefs().getFloat(watermarkKey(slot, "CropAspect"), 0.32f) : watermark.getHeight() * ratio;

            String pos = optionPrefs().getString(watermarkKey(slot, "Position"), defaultWatermarkPosition(slot));
            float margin = Math.max(24f, width * 0.035f);
            float left;
            float top;
            if ("Свободно".equals(pos)) {
                left = optionPrefs().getFloat(watermarkKey(slot, "FreeX"), 0.5f) * width - targetWidth / 2f;
            } else if (pos.contains("слева")) {
                left = margin;
            } else if (pos.contains("центру")) {
                left = (width - targetWidth) / 2f;
            } else {
                left = width - targetWidth - margin;
            }
            if ("Свободно".equals(pos)) {
                top = optionPrefs().getFloat(watermarkKey(slot, "FreeY"), 0.5f) * height - targetHeight / 2f;
            } else if (pos.contains("Сверху")) {
                top = margin;
            } else if (pos.contains("центру")) {
                top = (height - targetHeight) / 2f;
            } else {
                top = height - targetHeight - margin;
            }
            left = Math.max(margin, Math.min(width - targetWidth - margin, left));
            top = Math.max(margin, Math.min(height - targetHeight - margin, top));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setAlpha(Math.max(0, Math.min(255, optionPrefs().getInt(watermarkKey(slot, "Opacity"), 70) * 255 / 100)));
            Rect src = crop ? manualWatermarkCropSource(watermark, slot) : new Rect(0, 0, watermark.getWidth(), watermark.getHeight());
            RectF dst = new RectF(left, top, left + targetWidth, top + targetHeight);
            canvas.save();
            canvas.rotate(optionPrefs().getFloat(watermarkKey(slot, "Rotation"), 0f), dst.centerX(), dst.centerY());
            canvas.drawBitmap(watermark, src, dst, paint);
            canvas.restore();
        } catch (Exception e) {
            Log.w(TAG, "watermark failed: " + slot, e);
        } finally {
            if (watermark != null) {
                watermark.recycle();
            }
        }
    }

    private Rect manualWatermarkCropSource(Bitmap watermark, String slot) {
        float aspect = optionPrefs().getFloat(watermarkKey(slot, "CropAspect"), 0.32f);
        float cropW = optionPrefs().getFloat(watermarkKey(slot, "CropW"), 0.85f);
        float cropH = Math.max(0.08f, Math.min(0.95f, cropW * aspect));
        float cx = optionPrefs().getFloat(watermarkKey(slot, "CropX"), 0.5f);
        float cy = optionPrefs().getFloat(watermarkKey(slot, "CropY"), 0.5f);
        int left = Math.round((cx - cropW / 2f) * watermark.getWidth());
        int top = Math.round((cy - cropH / 2f) * watermark.getHeight());
        int right = Math.round((cx + cropW / 2f) * watermark.getWidth());
        int bottom = Math.round((cy + cropH / 2f) * watermark.getHeight());
        left = Math.max(0, Math.min(watermark.getWidth() - 1, left));
        top = Math.max(0, Math.min(watermark.getHeight() - 1, top));
        right = Math.max(left + 1, Math.min(watermark.getWidth(), right));
        bottom = Math.max(top + 1, Math.min(watermark.getHeight(), bottom));
        return new Rect(left, top, right, bottom);
    }

    private void toggleFlash() {
        if (!flashSupported) {
            toast("На этой камере вспышка недоступна");
            return;
        }
        flashEnabled = !flashEnabled;
        updateFlashButton();
        try {
            if (cameraSession != null && previewRequestBuilder != null) {
                applyPreviewFlashMode(previewRequestBuilder);
                cameraSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "toggleFlash failed", e);
            toast("Не удалось переключить вспышку");
        }
    }

    private void updateFlashButton() {
        if (flashButton == null) {
            return;
        }
        if (!flashSupported) {
            flashButton.setEnabled(false);
            return;
        }
        flashButton.setEnabled(true);
        flashButton.setAlpha(flashEnabled ? 1f : 0.45f);
    }

    private void applyPreviewFlashMode(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        if (flashSupported && flashEnabled) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void applyZoomCrop(CaptureRequest.Builder builder) {
        if (sensorArraySize == null || currentZoom <= 1.01f) {
            return;
        }
        float zoom = Math.min(maxDigitalZoom, Math.max(1f, currentZoom));
        int cropWidth = Math.round(sensorArraySize.width() / zoom);
        int cropHeight = Math.round(sensorArraySize.height() / zoom);
        int left = sensorArraySize.left + (sensorArraySize.width() - cropWidth) / 2;
        int top = sensorArraySize.top + (sensorArraySize.height() - cropHeight) / 2;
        builder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + cropWidth, top + cropHeight));
    }

    private void setCameraZoom(float zoom) {
        currentZoom = Math.min(maxDigitalZoom, Math.max(1f, zoom));
        if (cameraSession == null || previewRequestBuilder == null) {
            return;
        }
        try {
            applyZoomCrop(previewRequestBuilder);
            cameraSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
            toast(String.format(Locale.ROOT, "Зум %.1fx", currentZoom));
        } catch (Exception e) {
            Log.w(TAG, "zoom failed", e);
        }
    }

    private boolean isDisplayPortrait() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;
    }

    private void configurePreviewTransform(int viewWidth, int viewHeight) {
        if (cameraPreview == null || previewSize == null || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        boolean swapped = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;
        float bufferWidth = swapped ? previewSize.getHeight() : previewSize.getWidth();
        float bufferHeight = swapped ? previewSize.getWidth() : previewSize.getHeight();
        RectF bufferRect = new RectF(0, 0, bufferWidth, bufferHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                viewWidth / bufferWidth,
                viewHeight / bufferHeight
        );
        matrix.postScale(scale, scale, centerX, centerY);
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        }
        cameraPreview.setTransform(matrix);
    }

    private int jpegOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees;
        switch (rotation) {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            case Surface.ROTATION_0:
            default:
                degrees = 0;
                break;
        }
        return (sensorOrientation + degrees) % 360;
    }

    private void stopCamera() {
        try {
            if (cameraSession != null) {
                cameraSession.close();
                cameraSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            if (cameraThread != null) {
                cameraThread.quitSafely();
                cameraThread = null;
                cameraHandler = null;
            }
            previewSize = null;
            jpegSize = null;
            previewRequestBuilder = null;
            flashButton = null;
        } catch (Exception ignored) {
        }
    }

    private void showAddEntry() {
        LinearLayout root = baseScreen("Новая запись");

        TextView hint = text("Эта запись сохранится в зашифрованный metadata.enc. Комментарий и геопозиция не пишутся в открытом виде.");
        root.addView(hint);

        EditText comment = textInput("Комментарий");
        EditText location = textInput("Геопозиция или место");
        root.addView(comment);
        root.addView(location);

        Button coordinates = secondaryButton("Взять координаты");
        root.addView(coordinates);
        coordinates.setOnClickListener(view -> fillCoordinates(location));

        Button save = primaryButton("Зашифровать и сохранить");
        root.addView(save);
        save.setOnClickListener(view -> {
            String commentValue = comment.getText().toString();
            String locationValue = location.getText().toString();
            if (commentValue.trim().isEmpty() && locationValue.trim().isEmpty()) {
                toast("Добавьте комментарий или место");
                return;
            }
            try {
                vaultStore.addEntry(commentValue, locationValue);
                comment.setText("");
                location.setText("");
                toast("Запись сохранена");
                showVault();
            } catch (Exception e) {
                toast("Не удалось сохранить запись");
            }
        });

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);

        setContentView(wrap(root));
    }

    private void fillCoordinates(EditText locationInput) {
        pendingLocationInput = locationInput;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
            return;
        }

        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            toast("Геосервис недоступен");
            return;
        }

        try {
            Location location = bestLastLocation(manager);
            if (location != null) {
                setCoordinates(locationInput, location);
                return;
            }

            List<String> providers = manager.getProviders(true);
            if (providers.isEmpty()) {
                toast("Включите геолокацию на телефоне");
                return;
            }

            toast("Ищу координаты...");
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    setCoordinates(locationInput, location);
                    manager.removeUpdates(this);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    toast("Геолокация отключена");
                }
            };
            manager.requestLocationUpdates(providers.get(0), 0L, 0f, listener);
        } catch (SecurityException e) {
            toast("Нет разрешения на геопозицию");
        } catch (Exception e) {
            toast("Не удалось получить координаты");
        }
    }

    private Location bestLastLocation(LocationManager manager) {
        Location best = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate == null) {
                    continue;
                }
                if (best == null || candidate.getTime() > best.getTime()) {
                    best = candidate;
                }
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return best;
    }

    private void setCoordinates(EditText input, Location location) {
        String value = String.format(
                Locale.US,
                "%.6f, %.6f",
                location.getLatitude(),
                location.getLongitude()
        );
        input.setText(value);
        toast("Координаты добавлены");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) {
            if (requestCode == CAMERA_PERMISSION_REQUEST) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraWhenReady();
                } else {
                    toast("Разрешение на камеру не выдано");
                }
            }
            return;
        }
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        if (granted && pendingLocationInput != null) {
            fillCoordinates(pendingLocationInput);
        } else if (granted && "map".equals(currentScreen)) {
            centerMapOnUserLocation();
        } else {
            toast("Разрешение на геопозицию не выдано");
        }
    }

    private void showJournal() {
        currentScreen = "journal";
        setBackTarget(this::showCloudAndGallery);
        LinearLayout root = baseScreen("Галерея");
        addGalleryModeTabs(root);

        try {
            List<VaultEntry> entries = vaultStore.readEntries();
            String mode = optionPrefs().getString("galleryMode", "Список");
            String folder = currentGalleryFolder();
            if (entries.isEmpty()) {
                addPanel(root, "Пусто", "Пока нет зашифрованных записей. Создайте первую запись в фото-отсеке.");
            } else if ("Папки".equals(mode)) {
                renderGalleryFolders(root, entries);
            } else {
                List<VaultEntry> visible = filterGalleryEntries(entries, folder);
                if (!folder.isEmpty()) {
                    addPanel(root, "Папка", folder + "\nЗаписей: " + visible.size());
                }
                if (visible.isEmpty()) {
                    addPanel(root, "Пусто", folder.isEmpty() ? "В галерее пока нет файлов." : "В этой папке пока нет файлов.");
                } else if ("Сетка".equals(mode)) {
                    renderGalleryGrid(root, visible);
                } else {
                    renderGalleryList(root, visible);
                }
            }
        } catch (Exception e) {
            addPanel(root, "Ошибка", "Не удалось расшифровать metadata.enc.");
        }

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showCloudAndGallery());
        root.addView(back);

        setContentView(wrap(root));
    }

    private void addGalleryModeTabs(LinearLayout root) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(12), dp(12), dp(12), dp(12));
        holder.setBackground(roundDrawable(themePanelColor(), 12, 1, themePanelStroke()));
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        holderParams.setMargins(0, dp(34), 0, dp(18));

        TextView label = new TextView(this);
        label.setText("Вид галереи");
        label.setTextColor(themeMutedColor());
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(4), 0, dp(4), dp(8));
        holder.addView(label);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        String active = optionPrefs().getString("galleryMode", "Список");
        String[] values = {"Список", "Сетка", "Папки"};
        for (String value : values) {
            TextView tab = new TextView(this);
            tab.setText(value);
            tab.setTextSize(14);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            boolean selected = value.equals(active);
            tab.setTextColor(selected ? Color.rgb(4, 18, 28) : themeTextColor());
            tab.setBackground(roundDrawable(selected ? themeAccent() : themePanelColor(), 8, 1, selected ? themeAccentSecond() : themePanelStroke()));
            tab.setOnClickListener(view -> {
                optionPrefs().edit().putString("galleryMode", value).apply();
                showJournal();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            modes.addView(tab, params);
        }
        holder.addView(modes, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(holder, holderParams);
    }

    private void renderGalleryList(LinearLayout root, List<VaultEntry> entries) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (VaultEntry entry : entries) {
            addPanel(root, "Запись", galleryEntryBody(entry, format));
            Button actions = secondaryButton("⋮ Действия");
            actions.setOnClickListener(view -> showEntryMenu(entry));
            root.addView(actions);
        }
    }

    private void renderGalleryGrid(LinearLayout root, List<VaultEntry> entries) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT);
        for (VaultEntry entry : entries) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(10), dp(10), dp(10), dp(10));
            cell.setBackground(roundDrawable(themePanelColor(), 8, 1, themePanelStroke()));
            TextView icon = new TextView(this);
            icon.setText(entry.photoBlobId.isEmpty() ? "◇" : "▧");
            icon.setTextSize(38);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(themeAccent());
            cell.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
            TextView title = text(entry.comment.isEmpty() ? "Фото" : entry.comment);
            title.setMaxLines(2);
            cell.addView(title);
            TextView date = text(format.format(new Date(entry.createdAtMs)));
            date.setTextColor(themeMutedColor());
            cell.addView(date);
            cell.setOnClickListener(view -> showEntryMenu(entry));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(154);
            params.height = dp(166);
            params.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(cell, params);
        }
        root.addView(grid);
    }

    private void renderGalleryFolders(LinearLayout root, List<VaultEntry> entries) {
        Button all = secondaryButton("▦  Все файлы · " + entries.size());
        all.setOnClickListener(view -> {
            optionPrefs().edit()
                    .putString("galleryFolder", "")
                    .putString("galleryMode", "Список")
                    .apply();
            showJournal();
        });
        root.addView(all);

        Button create = primaryButton("Создать папку");
        create.setOnClickListener(view -> showCreateGalleryFolderDialog());
        root.addView(create);

        Set<String> folders = galleryFolders();
        if (folders.isEmpty()) {
            addPanel(root, "Папки", "Папок пока нет.");
            return;
        }
        for (String folder : folders) {
            int count = filterGalleryEntries(entries, folder).size();
            Button open = secondaryButton("▣  " + folder + " · " + count);
            open.setOnClickListener(view -> {
                optionPrefs().edit()
                        .putString("galleryFolder", folder)
                        .putString("galleryMode", "Сетка")
                        .apply();
                showJournal();
            });
            root.addView(open);
        }
    }

    private List<VaultEntry> filterGalleryEntries(List<VaultEntry> entries, String folder) {
        if (folder == null || folder.isEmpty()) {
            return entries;
        }
        List<VaultEntry> out = new ArrayList<>();
        for (VaultEntry entry : entries) {
            if (folder.equals(galleryFolderForEntry(entry.id))) {
                out.add(entry);
            }
        }
        return out;
    }

    private String currentGalleryFolder() {
        return optionPrefs().getString("galleryFolder", "");
    }

    private String galleryFolderForEntry(String entryId) {
        return optionPrefs().getString("galleryEntryFolder." + entryId, "");
    }

    private Set<String> galleryFolders() {
        return new java.util.TreeSet<>(optionPrefs().getStringSet("galleryFolders", new HashSet<>()));
    }

    private void saveGalleryFolders(Set<String> folders) {
        optionPrefs().edit().putStringSet("galleryFolders", new HashSet<>(folders)).apply();
    }

    private void showCreateGalleryFolderDialog() {
        EditText input = compactInput("Название папки");
        new AlertDialog.Builder(this)
                .setTitle("Новая папка")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Введите название папки");
                        return;
                    }
                    Set<String> folders = galleryFolders();
                    folders.add(name);
                    saveGalleryFolders(folders);
                    toast("Папка создана");
                    showJournal();
                })
                .show();
    }

    private void showMoveToGalleryFolder(VaultEntry entry) {
        Set<String> folders = galleryFolders();
        List<String> items = new ArrayList<>();
        items.add("Без папки");
        items.addAll(folders);
        items.add("Создать новую папку...");
        String[] values = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Переместить")
                .setItems(values, (dialog, which) -> {
                    String selected = values[which];
                    if ("Создать новую папку...".equals(selected)) {
                        showCreateAndMoveGalleryFolder(entry);
                        return;
                    }
                    optionPrefs().edit()
                            .putString("galleryEntryFolder." + entry.id, "Без папки".equals(selected) ? "" : selected)
                            .apply();
                    toast("Папка обновлена");
                    showJournal();
                })
                .show();
    }

    private void showCreateAndMoveGalleryFolder(VaultEntry entry) {
        EditText input = compactInput("Название папки");
        new AlertDialog.Builder(this)
                .setTitle("Новая папка")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Введите название папки");
                        return;
                    }
                    Set<String> folders = galleryFolders();
                    folders.add(name);
                    saveGalleryFolders(folders);
                    optionPrefs().edit().putString("galleryEntryFolder." + entry.id, name).apply();
                    toast("Перемещено");
                    showJournal();
                })
                .show();
    }

    private String galleryEntryBody(VaultEntry entry, DateFormat format) {
        StringBuilder body = new StringBuilder();
        body.append("Время: ").append(format.format(new Date(entry.createdAtMs)));
        if (!entry.comment.isEmpty()) {
            body.append("\nКомментарий: ").append(entry.comment);
        }
        if (!entry.location.isEmpty()) {
            body.append("\nМесто: ").append(entry.location);
        }
        if (!entry.photoBlobId.isEmpty()) {
            body.append("\nФото blob: ").append(entry.photoBlobId);
            body.append("\nРазмер: ").append(entry.encryptedPhotoSize).append(" байт");
        }
        return body.toString();
    }

    private void showCloudAndGallery() {
        currentScreen = "cloud";
        setBackTarget(this::showVault);
        LinearLayout root = baseScreen("Галерея и Облако");

        addPanel(root, "Разделы", "Галерея хранит encrypted blobs на устройстве.\nАктивные каналы: " + backupConfigStore.enabledTargetsText());

        Button local = secondaryButton("🖼  Внутренняя галерея");
        local.setOnClickListener(view -> showJournal());
        root.addView(local);

        if (backupConfigStore.isMegaEnabled()) {
            Button mega = secondaryButton("☁  Облако MEGA");
            mega.setOnClickListener(view -> showMegaCloud());
            root.addView(mega);
        }

        if (backupConfigStore.isTgFinderEnabled()) {
            Button tg = secondaryButton("✈  TGFinder");
            tg.setOnClickListener(view -> showBackupSettings());
            root.addView(tg);

            Button telegram = secondaryButton("▦  Просмотр супергруппы");
            telegram.setOnClickListener(view -> showTelegramGroupBrowser());
            root.addView(telegram);
        }

        if (backupConfigStore.hasEnabledTarget()) {
            Button queue = secondaryButton("⇅  Очередь отправки");
            queue.setOnClickListener(view -> showBackupQueue());
            root.addView(queue);
        } else {
            Button sync = secondaryButton("☁  Включить облако");
            sync.setOnClickListener(view -> showSyncOptions());
            root.addView(sync);
        }

        Button back = secondaryButton("Назад");
        back.setOnClickListener(view -> showVault());
        root.addView(back);

        setContentView(wrap(root));
    }

    private void showEntryMenu(VaultEntry entry) {
        List<String> actions = new ArrayList<>();
        actions.add("Свойства");
        if (!entry.photoBlobId.isEmpty()) {
            actions.add("Расшифровать");
            actions.add("Сохранить на устройство");
            actions.add("Сохранить в облако и группу");
        }
        actions.add("Переместить в папку");
        actions.add("Удалить");

        String[] items = actions.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Действия")
                .setItems(items, (dialog, which) -> {
                    String action = items[which];
                    if ("Свойства".equals(action)) {
                        showEntryProperties(entry);
                    } else if ("Расшифровать".equals(action)) {
                        showDecryptedPhoto(entry);
                    } else if ("Сохранить на устройство".equals(action)) {
                        saveEntryToDeviceGallery(entry);
                    } else if ("Сохранить в облако и группу".equals(action)) {
                        enqueueExistingPhoto(entry);
                    } else if ("Переместить в папку".equals(action)) {
                        showMoveToGalleryFolder(entry);
                    } else if ("Удалить".equals(action)) {
                        confirmDeleteEntry(entry);
                    }
                })
                .show();
    }

    private void showEntryProperties(VaultEntry entry) {
        StringBuilder body = new StringBuilder();
        body.append("ID: ").append(entry.id);
        body.append("\nВремя: ").append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.createdAtMs)));
        if (!entry.comment.isEmpty()) {
            body.append("\nКомментарий: ").append(entry.comment);
        }
        if (!entry.location.isEmpty()) {
            body.append("\nМесто: ").append(entry.location);
        }
        if (!entry.photoBlobId.isEmpty()) {
            body.append("\nBlob: ").append(entry.photoBlobId);
            body.append("\nEncrypted size: ").append(entry.encryptedPhotoSize).append(" байт");
        }
        new AlertDialog.Builder(this)
                .setTitle("Свойства")
                .setMessage(body.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void enqueueExistingPhoto(VaultEntry entry) {
        if (entry.photoBlobId.isEmpty()) {
            toast("У записи нет фото");
            return;
        }
        try {
            if (!backupConfigStore.hasEnabledTarget()) {
                toast("Синхронизация выключена");
                return;
            }
            backupQueue.enqueuePhoto(entry.photoBlobId, entry.encryptedPhotoSize, backupConfigStore.isMegaEnabled(), backupConfigStore.isTgFinderEnabled());
            toast("Добавлено в очередь: " + backupConfigStore.enabledTargetsText());
            showJournal();
        } catch (Exception e) {
            toast("Не удалось поставить в очередь");
        }
    }

    private void saveEntryToDeviceGallery(VaultEntry entry) {
        if (entry.photoBlobId.isEmpty()) {
            toast("У записи нет фото");
            return;
        }
        try {
            byte[] jpeg = photoStore.loadJpeg(entry.photoBlobId, activePin);
            String filename = "BlueOcean_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(entry.createdAtMs)) + ".jpg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Blue Ocean");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("MediaStore insert failed");
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("MediaStore output failed");
                }
                out.write(jpeg);
                out.flush();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, ready, null, null);
            }
            toast("Сохранено на устройство");
            showJournal();
        } catch (Exception e) {
            Log.e(TAG, "save to device failed", e);
            toast("Не удалось сохранить на устройство");
        }
    }

    private void confirmDeleteEntry(VaultEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить запись?")
                .setMessage("Запись будет удалена из metadata.enc. Если есть фото, encrypted blob тоже будет удален с устройства.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> deleteEntry(entry))
                .show();
    }

    private void deleteEntry(VaultEntry entry) {
        try {
            vaultStore.deleteEntry(entry.id);
            if (!entry.photoBlobId.isEmpty()) {
                photoStore.deleteBlob(entry.photoBlobId);
            }
            toast("Удалено");
            showJournal();
        } catch (Exception e) {
            toast("Не удалось удалить");
        }
    }

    private void showDecryptedPhoto(VaultEntry entry) {
        LinearLayout root = baseScreen("Расшифровка");

        try {
            byte[] jpeg = photoStore.loadJpeg(entry.photoBlobId, activePin);
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (bitmap == null) {
                addPanel(root, "Ошибка", "Blob расшифрован, но изображение не прочиталось.");
            } else {
                ImageView image = new ImageView(this);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setBackgroundColor(Color.BLACK);
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(420)
                );
                imageParams.setMargins(0, 0, 0, dp(16));
                root.addView(image, imageParams);

                addPanel(root, "Свойства", "Фото показано только в памяти.\nBlob: " + entry.photoBlobId + "\nEncrypted size: " + entry.encryptedPhotoSize + " байт");
            }
        } catch (Exception e) {
            addPanel(root, "Ошибка", "Не удалось расшифровать blob.");
        }

        Button back = secondaryButton("Назад в журнал");
        back.setOnClickListener(view -> showJournal());
        root.addView(back);

        setContentView(wrap(root));
    }

    @Override
    protected void onPause() {
        stopCamera();
        destroyActiveMapView();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ((activePin == null || activePin.isEmpty())
                && "Калькулятор".equals(optionPrefs().getString("entryMode", "Калькулятор"))) {
            showCalculatorUnlock();
        }
    }

    private LinearLayout baseScreen(String titleText) {
        if (backTarget == null) {
            setBackTarget(activePin == null || activePin.isEmpty() ? this::showNotesHome : this::showVault);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        root.setPadding(dp(24), dp(24), dp(24), safeBottomPad());
        root.setBackground(screenBackground());

        addHeader(root, titleText, "", false);

        return root;
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(themeTextColor());
        view.setLineSpacing(dp(2), 1.0f);
        view.setPadding(0, 0, 0, dp(16));
        return view;
    }

    private EditText pinInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setImportantForAutofill(EditText.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSingleLine(true);
        input.setTextColor(themeTextColor());
        input.setHintTextColor(themeMutedColor());
        input.setTextSize(18);
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        input.setBackground(inputBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(18));
        input.setLayoutParams(params);
        return input;
    }

    private EditText textInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImportantForAutofill(EditText.IMPORTANT_FOR_AUTOFILL_NO);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setTextColor(themeTextColor());
        input.setHintTextColor(themeMutedColor());
        input.setTextSize(17);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(inputBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(14));
        input.setLayoutParams(params);
        return input;
    }

    private EditText compactInput(String hint) {
        EditText input = textInput(hint);
        input.setSingleLine(true);
        input.setMinLines(1);
        input.setMaxLines(1);
        input.setTextSize(15);
        input.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, 0);
        input.setLayoutParams(params);
        return input;
    }

    private TextView editorTool(String label) {
        TextView tool = new TextView(this);
        tool.setText(label);
        tool.setTextColor(Color.WHITE);
        tool.setTextSize(20);
        tool.setGravity(Gravity.CENTER);
        tool.setTypeface(Typeface.DEFAULT_BOLD);
        tool.setBackground(roundDrawable(Color.rgb(15, 48, 68), 8, 1, Color.rgb(56, 189, 248)));
        return tool;
    }

    private TextView editorAction(String label) {
        TextView action = editorTool(label);
        action.setTextSize(22);
        return action;
    }

    private TextView editorSaveAction(String label) {
        TextView action = new TextView(this);
        action.setText(label);
        action.setTextColor(Color.rgb(20, 18, 12));
        action.setTextSize(16);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setBackground(roundDrawable(COLOR_BRASS, 8, 1, COLOR_TEAL));
        return action;
    }

    private LinearLayout.LayoutParams toolParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(4), dp(6), dp(4), dp(2));
        return params;
    }

    private void addColorTool(LinearLayout row, PhotoEditorView editor, int color) {
        TextView swatch = new TextView(this);
        swatch.setText("");
        swatch.setBackground(roundDrawable(color, 6, 1, Color.rgb(230, 245, 255)));
        swatch.setOnClickListener(view -> editor.setMarkupColor(color));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(28), 1f);
        params.setMargins(dp(5), dp(6), dp(5), dp(2));
        row.addView(swatch, params);
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(21, 19, 14));
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(buttonBackground(COLOR_BRASS, COLOR_COPPER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, dp(14));
        button.setLayoutParams(params);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = primaryButton(label);
        button.setTextColor(themeTextColor());
        button.setBackground(buttonBackground(themePanelColor(), themePanelColor()));
        return button;
    }

    private Button backButton() {
        Button button = secondaryButton("Назад к заметкам");
        button.setOnClickListener(view -> showNotesHome());
        return button;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int safeBottomPad() {
        return dp(96);
    }

    private int safeNavBarMargin() {
        return dp(48);
    }

    private void setBackTarget(Runnable target) {
        backTarget = target;
    }

    private ScrollView wrap(LinearLayout root) {
        return wrap(root, 0);
    }

    private ScrollView wrap(LinearLayout root, int initialScrollY) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(screenBackground());
        scroll.addView(root);
        scroll.post(() -> scroll.scrollTo(0, Math.max(0, initialScrollY)));
        return scroll;
    }

    private void addHeader(LinearLayout root, String title, String subtitle, boolean privateAccess) {
        TextView gear = new TextView(this);
        gear.setText("◆  ◇  ◆");
        gear.setTextColor(themeAccent());
        gear.setTextSize(18);
        gear.setGravity(Gravity.CENTER);
        root.addView(gear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(themeTextColor());
        titleView.setTextSize(30);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(8), 0, 0);
        root.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (privateAccess) {
            installVeryLongPress(titleView);
        }

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(themeMutedColor());
            subtitleView.setTextSize(14);
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setPadding(0, dp(4), 0, dp(24));
            root.addView(subtitleView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        } else {
            TextView spacer = new TextView(this);
            spacer.setPadding(0, 0, 0, dp(20));
            root.addView(spacer, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private void addPanel(LinearLayout root, String title, String body) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(panelBackground());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(themeAccent());
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(titleView);

        TextView bodyView = new TextView(this);
        bodyView.setText(body);
        bodyView.setTextColor(themeTextColor());
        bodyView.setTextSize(16);
        bodyView.setLineSpacing(dp(3), 1.0f);
        bodyView.setPadding(0, dp(8), 0, 0);
        panel.addView(bodyView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(16));
        root.addView(panel, params);
    }

    private GradientDrawable screenBackground() {
        String theme = currentTheme();
        int top = COLOR_BG_TOP;
        int bottom = COLOR_BG_BOTTOM;
        if ("Светлая".equals(theme)) {
            top = Color.rgb(225, 243, 255);
            bottom = Color.rgb(181, 224, 247);
        } else if ("Темная".equals(theme)) {
            top = Color.rgb(6, 6, 8);
            bottom = Color.rgb(18, 18, 22);
        } else if ("Глубокий океан".equals(theme)) {
            top = Color.rgb(0, 10, 24);
            bottom = Color.rgb(0, 38, 66);
        }
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom}
        );
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(themePanelColor());
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), themePanelStroke());
        return drawable;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(themeInputColor());
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), themePanelStroke());
        return drawable;
    }

    private GradientDrawable buttonBackground(int start, int end) {
        if (start == COLOR_BRASS && end == COLOR_COPPER) {
            start = themeAccent();
            end = themeAccentSecond();
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end}
        );
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), themeAccentSecond());
        return drawable;
    }

    private String currentTheme() {
        return optionPrefs().getString("theme", "Blue Ocean");
    }

    private int themedNotesBackground() {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return Color.rgb(220, 241, 252);
        if ("Темная".equals(theme)) return Color.BLACK;
        if ("Глубокий океан".equals(theme)) return Color.rgb(0, 12, 26);
        return COLOR_NOTES_BG;
    }

    private int themeNoteCardColor(boolean alt) {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return alt ? Color.rgb(183, 224, 246) : Color.rgb(235, 248, 255);
        if ("Темная".equals(theme)) return alt ? Color.rgb(42, 42, 48) : Color.rgb(30, 30, 35);
        if ("Глубокий океан".equals(theme)) return alt ? Color.rgb(15, 78, 113) : Color.rgb(9, 52, 83);
        if ("Blue Ocean".equals(theme)) return alt ? Color.rgb(17, 82, 116) : Color.rgb(12, 58, 91);
        return alt ? COLOR_NOTE_CARD_ALT : COLOR_NOTE_CARD;
    }

    private int themeNoteTextColor() {
        return "Светлая".equals(currentTheme()) ? COLOR_NOTE_TEXT : COLOR_TEXT;
    }

    private int themeTextColor() {
        return "Светлая".equals(currentTheme()) ? Color.rgb(18, 36, 48) : COLOR_TEXT;
    }

    private int themeMutedColor() {
        return "Светлая".equals(currentTheme()) ? Color.rgb(75, 103, 120) : COLOR_MUTED;
    }

    private int themePanelColor() {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return Color.rgb(239, 249, 255);
        if ("Темная".equals(theme)) return Color.rgb(24, 24, 28);
        if ("Глубокий океан".equals(theme)) return Color.rgb(4, 25, 45);
        return COLOR_PANEL;
    }

    private int themeCameraBarColor() {
        String theme = currentTheme();
        if ("Темная".equals(theme)) return Color.rgb(17, 17, 19);
        if ("Глубокий океан".equals(theme)) return Color.rgb(2, 23, 39);
        return Color.rgb(12, 31, 48);
    }

    private int themeInputColor() {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return Color.rgb(224, 243, 255);
        if ("Темная".equals(theme)) return Color.rgb(18, 18, 22);
        if ("Глубокий океан".equals(theme)) return Color.rgb(3, 20, 36);
        return Color.rgb(12, 21, 34);
    }

    private int themePanelStroke() {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return Color.rgb(131, 180, 205);
        if ("Темная".equals(theme)) return Color.rgb(58, 58, 66);
        if ("Глубокий океан".equals(theme)) return Color.rgb(20, 82, 118);
        return COLOR_PANEL_STROKE;
    }

    private int themeAccent() {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return Color.rgb(0, 122, 204);
        if ("Темная".equals(theme)) return Color.rgb(80, 80, 88);
        if ("Глубокий океан".equals(theme)) return Color.rgb(0, 116, 184);
        return COLOR_BRASS;
    }

    private int themeAccentSecond() {
        String theme = currentTheme();
        if ("Светлая".equals(theme)) return Color.rgb(31, 180, 220);
        if ("Темная".equals(theme)) return Color.rgb(56, 56, 64);
        if ("Глубокий океан".equals(theme)) return Color.rgb(0, 180, 216);
        return COLOR_COPPER;
    }

    private GradientDrawable roundDrawable(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private void installVeryLongPress(TextView target) {
        target.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(this::openPrivateGate, PRIVATE_GATE_HOLD_MS);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float x = event.getX();
                    float y = event.getY();
                    if (x >= 0 && x <= view.getWidth() && y >= 0 && y <= view.getHeight()) {
                        return true;
                    }
                }
                handler.removeCallbacksAndMessages(null);
                return true;
            }
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        if ("camera".equals(currentScreen)) {
            stopCamera();
            showVault();
        } else if ("editor".equals(currentScreen)) {
            showCamera();
        } else if ("options".equals(currentScreen) || "cloud".equals(currentScreen)) {
            showVault();
        } else if ("map".equals(currentScreen)) {
            destroyActiveMapView();
            if (backTarget != null) {
                backTarget.run();
            } else {
                showVault();
            }
        } else if ("sync".equals(currentScreen)) {
            showOptions();
        } else if ("backup".equals(currentScreen) || "queue".equals(currentScreen) || "journal".equals(currentScreen) || "telegram".equals(currentScreen)) {
            showCloudAndGallery();
        } else if ("vault".equals(currentScreen)) {
            showEntryScreen();
        } else if ("calculator".equals(currentScreen) || "notes".equals(currentScreen)) {
            moveTaskToBack(true);
        } else {
            showVault();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (cameraDevice != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            float delta = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 0.2f : -0.2f;
            setCameraZoom(currentZoom + delta);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WATERMARK_PICK_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            optionPrefs().edit().putString(watermarkKey(pendingWatermarkSlot, "Uri"), data.getData().toString()).apply();
            toast("Водяной знак выбран");
            showWatermarkOptions(pendingWatermarkSlot);
        }
    }

    private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private static final class MapPreviewView extends android.view.View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MapPreviewView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            canvas.drawColor(Color.rgb(8, 29, 43));

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(19, 92, 113));
            for (int i = 0; i < 7; i++) {
                float y = h * (0.14f + i * 0.13f);
                RectF river = new RectF(-w * 0.18f, y - dpStatic(18), w * 1.18f, y + dpStatic(20));
                canvas.drawOval(river, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpStatic(2));
            paint.setColor(Color.rgb(64, 137, 124));
            for (int i = 1; i < 7; i++) {
                float x = w * i / 7f;
                canvas.drawLine(x, 0, x + w * 0.12f, h, paint);
            }
            paint.setColor(Color.rgb(77, 105, 77));
            for (int i = 1; i < 9; i++) {
                float y = h * i / 9f;
                canvas.drawLine(0, y, w, y - h * 0.08f, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpStatic(4));
            paint.setColor(Color.rgb(231, 244, 248));
            canvas.drawCircle(w / 2f, h / 2f, dpStatic(18), paint);
            canvas.drawLine(w / 2f - dpStatic(34), h / 2f, w / 2f + dpStatic(34), h / 2f, paint);
            canvas.drawLine(w / 2f, h / 2f - dpStatic(34), w / 2f, h / 2f + dpStatic(34), paint);
        }

        private static int dpStatic(int value) {
            return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
        }
    }

    private static final class UserMapMarkerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        UserMapMarkerView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float radius = Math.min(w, h) * 0.18f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(45, 14, 165, 233));
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.43f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(Color.argb(130, 125, 211, 252));
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.42f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(90, 0, 0, 0));
            canvas.drawCircle(cx + 2f, cy + 4f, radius + 8f, paint);

            android.graphics.Path direction = new android.graphics.Path();
            direction.moveTo(cx, cy - radius - 18f);
            direction.lineTo(cx - 8f, cy - radius + 8f);
            direction.quadTo(cx, cy - radius + 2f, cx + 8f, cy - radius + 8f);
            direction.close();
            paint.setColor(Color.rgb(14, 165, 233));
            paint.setShadowLayer(5f, 0f, 2f, Color.argb(120, 0, 0, 0));
            canvas.drawPath(direction, paint);
            paint.clearShadowLayer();

            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, radius + 7f, paint);
            paint.setColor(Color.rgb(14, 165, 233));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, radius * 0.42f, paint);
        }
    }

    private PatternPad addPatternPad(LinearLayout root) {
        PatternPad pad = new PatternPad();
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(8), 0, dp(18));

        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 3; col++) {
                int dot = row * 3 + col + 1;
                TextView cell = patternCell(dot);
                pad.add(dot, cell);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(74), dp(74));
                params.setMargins(dp(6), dp(6), dp(6), dp(6));
                line.addView(cell, params);
            }
            grid.addView(line);
        }

        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return pad;
    }

    private TextView patternCell(int dot) {
        TextView cell = new TextView(this);
        cell.setText("○");
        cell.setTextSize(30);
        cell.setGravity(Gravity.CENTER);
        cell.setTextColor(COLOR_BRASS);
        cell.setBackground(inputBackground());
        cell.setOnClickListener(view -> {
            Object tag = view.getTag();
            if (tag instanceof PatternPad) {
                ((PatternPad) tag).select(dot, cell);
            }
        });
        return cell;
    }

    private final class PatternPad {
        private final List<Integer> sequence = new ArrayList<>();
        private final Set<Integer> selected = new HashSet<>();
        private final List<TextView> cells = new ArrayList<>();

        void add(int dot, TextView cell) {
            cell.setTag(this);
            cells.add(cell);
        }

        void select(int dot, TextView cell) {
            if (selected.contains(dot)) {
                return;
            }
            selected.add(dot);
            sequence.add(dot);
            cell.setText("●");
            cell.setTextColor(COLOR_TEXT);
            cell.setBackground(buttonBackground(COLOR_TEAL, COLOR_COPPER));
        }

        String pattern() {
            StringBuilder out = new StringBuilder();
            for (int dot : sequence) {
                out.append(dot);
            }
            return out.toString();
        }

        void clear() {
            sequence.clear();
            selected.clear();
            for (TextView cell : cells) {
                cell.setText("○");
                cell.setTextColor(COLOR_BRASS);
                cell.setBackground(inputBackground());
            }
        }
    }
}
