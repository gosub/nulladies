package it.lo.exp.nulladies;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NullaDies";

    private DatabaseHelper db;
    private DayManager dayManager;

    // UI references
    private LinearLayout currentTaskSection;
    private View currentTaskColorBar;
    private TextView currentTaskTitle;
    private ImageButton btnDone, btnSkip;
    private TextView allDoneText;
    private LinearLayout taskGrid;
    private FloatingActionButton fabSplit;

    // State
    private List<DailyTask> completedTasks = new ArrayList<>();
    private List<DailyTask> pendingTasks   = new ArrayList<>();
    private List<DailyTask> skippedTasks   = new ArrayList<>();
    private int selectedTaskId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        dayManager = new DayManager(db);

        // Bind views
        currentTaskSection  = findViewById(R.id.current_task_section);
        currentTaskColorBar = findViewById(R.id.current_task_color_bar);
        currentTaskTitle    = findViewById(R.id.current_task_title);
        btnDone             = findViewById(R.id.btn_done);
        btnSkip             = findViewById(R.id.btn_skip);
        allDoneText         = findViewById(R.id.all_done_text);
        taskGrid            = findViewById(R.id.task_grid);
        fabSplit            = findViewById(R.id.fab_split);

        // Action buttons
        btnDone.setOnClickListener(v -> onDone());
        btnSkip.setOnClickListener(v -> onSkip());

        // FABs
        findViewById(R.id.fab_quick_add).setOnClickListener(v -> showQuickAddDialog());
        fabSplit.setOnClickListener(v -> {
            DailyTask sel = findSelectedTask();
            if (sel != null) showSplitDialog(sel);
        });

        // Bottom navigation
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_queue) {
                startActivity(new Intent(this, TodoQueueActivity.class));
                return true;
            }
            if (id == R.id.nav_tasks) {
                startActivity(new Intent(this, RecurringTasksActivity.class));
                return true;
            }
            if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            if (id == R.id.nav_log) {
                startActivity(new Intent(this, LogActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        dayManager.checkAndRolloverIfNeeded();
        refreshData();
        // Reset selection if invalid (task no longer exists)
        if (findSelectedTask() == null) {
            selectedTaskId = pendingTasks.isEmpty() ? -1 : pendingTasks.get(0).id;
        }
        refreshUI();
        // Deselect all nav items — this is a launcher screen, not a tab host
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.getMenu().setGroupCheckable(0, true, false);
        for (int i = 0; i < nav.getMenu().size(); i++) {
            nav.getMenu().getItem(i).setChecked(false);
        }
        nav.getMenu().setGroupCheckable(0, true, true);
    }

    // ─── Data ──────────────────────────────────────────────────────────────────

    private void refreshData() {
        String today = today();
        List<DailyTask> all = db.getDailyTasks(today);
        completedTasks.clear();
        pendingTasks.clear();
        skippedTasks.clear();
        for (DailyTask t : all) {
            switch (t.state) {
                case DailyTask.STATE_COMPLETED: completedTasks.add(t); break;
                case DailyTask.STATE_SKIPPED:   skippedTasks.add(t);   break;
                default:                        pendingTasks.add(t);   break;
            }
        }
    }

    private DailyTask findSelectedTask() {
        if (selectedTaskId == -1) return null;
        for (DailyTask t : completedTasks) if (t.id == selectedTaskId) return t;
        for (DailyTask t : pendingTasks)   if (t.id == selectedTaskId) return t;
        for (DailyTask t : skippedTasks)   if (t.id == selectedTaskId) return t;
        return null;
    }

    // ─── UI ────────────────────────────────────────────────────────────────────

    private void refreshUI() {
        buildCurrentTaskCard();
        buildTaskGrid();
    }

    private void buildCurrentTaskCard() {
        DailyTask sel = findSelectedTask();
        if (sel == null) {
            currentTaskSection.setVisibility(View.GONE);
            boolean hadTasks = !completedTasks.isEmpty() || !skippedTasks.isEmpty();
            allDoneText.setVisibility(hadTasks ? View.VISIBLE : View.GONE);
            fabSplit.setVisibility(View.GONE);
            return;
        }
        allDoneText.setVisibility(View.GONE);
        currentTaskSection.setVisibility(View.VISIBLE);

        currentTaskTitle.setText(sel.title);

        GradientDrawable bar = new GradientDrawable();
        bar.setShape(GradientDrawable.RECTANGLE);
        bar.setColor(TaskColor.fromName(sel.color).toArgb());
        currentTaskColorBar.setBackground(bar);

        // Button icons and visibility depend on selected task state
        if (DailyTask.STATE_COMPLETED.equals(sel.state)) {
            btnDone.setImageResource(R.drawable.ic_undo);
            btnDone.setVisibility(View.VISIBLE);
            btnSkip.setVisibility(View.GONE);
        } else if (DailyTask.STATE_SKIPPED.equals(sel.state)) {
            btnSkip.setImageResource(R.drawable.ic_undo);
            btnSkip.setVisibility(View.VISIBLE);
            btnDone.setVisibility(View.GONE);
        } else {
            btnDone.setImageResource(R.drawable.ic_check);
            btnSkip.setImageResource(R.drawable.ic_skip);
            btnDone.setVisibility(View.VISIBLE);
            btnSkip.setVisibility(View.VISIBLE);
        }

        // fab_split visible only for queue-slot tasks
        fabSplit.setVisibility("queue_slot".equals(sel.source) ? View.VISIBLE : View.GONE);
    }

    private void buildTaskGrid() {
        taskGrid.removeAllViews();

        // Order: completed → pending → skipped
        List<DailyTask> all = new ArrayList<>();
        all.addAll(completedTasks);
        all.addAll(pendingTasks);
        all.addAll(skippedTasks);
        if (all.isEmpty()) return;

        float density  = getResources().getDisplayMetrics().density;
        int squarePx   = (int)(36 * density);
        int marginPx   = (int)(3 * density);
        int cornerPx   = (int)(7 * density);
        int gapPx      = (int)(3 * density);
        int screenW    = getResources().getDisplayMetrics().widthPixels;
        int available  = screenW - (int)(16 * density);
        int cols       = Math.max(1, available / (squarePx + marginPx * 2));

        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                taskGrid.addView(row);
            }
            DailyTask task = all.get(i);
            boolean isDone     = DailyTask.STATE_COMPLETED.equals(task.state);
            boolean isSkipped  = DailyTask.STATE_SKIPPED.equals(task.state);
            boolean isSelected = task.id == selectedTaskId;

            int baseColor    = TaskColor.fromName(task.color).toArgb();
            int displayColor = (isDone || isSkipped) ? mutedColor(baseColor) : baseColor;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(squarePx, squarePx);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);

            Drawable bg;
            if (isSelected) {
                GradientDrawable borderLayer = new GradientDrawable();
                borderLayer.setShape(GradientDrawable.RECTANGLE);
                borderLayer.setCornerRadius(cornerPx);
                borderLayer.setColor(0xFF1A1A1A);

                GradientDrawable fillLayer = new GradientDrawable();
                fillLayer.setShape(GradientDrawable.RECTANGLE);
                fillLayer.setCornerRadius(Math.max(0, cornerPx - gapPx));
                fillLayer.setColor(displayColor);

                LayerDrawable ld = new LayerDrawable(new Drawable[]{borderLayer, fillLayer});
                ld.setLayerInset(1, gapPx, gapPx, gapPx, gapPx);
                bg = ld;
            } else {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.RECTANGLE);
                d.setCornerRadius(cornerPx);
                d.setColor(displayColor);
                bg = d;
            }

            if (isDone) {
                // TextView to overlay checkmark
                TextView cell = new TextView(this);
                cell.setLayoutParams(lp);
                cell.setBackground(bg);
                cell.setText("✓");
                cell.setTextColor(0x88000000);
                cell.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, squarePx * 0.55f);
                cell.setGravity(android.view.Gravity.CENTER);
                final int taskId = task.id;
                cell.setOnClickListener(v -> {
                    selectedTaskId = taskId;
                    refreshUI();
                });
                if (row != null) row.addView(cell);
            } else {
                View cell = new View(this);
                cell.setLayoutParams(lp);
                cell.setBackground(bg);
                final int taskId = task.id;
                cell.setOnClickListener(v -> {
                    selectedTaskId = taskId;
                    refreshUI();
                });
                if (row != null) row.addView(cell);
            }
        }
    }

    private static int mutedColor(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[1] *= 0.28f;
        hsv[2] = Math.min(1f, hsv[2] * 1.15f);
        return android.graphics.Color.HSVToColor(hsv);
    }

    // ─── Actions ───────────────────────────────────────────────────────────────

    private void onDone() {
        DailyTask sel = findSelectedTask();
        if (sel == null) return;
        if (DailyTask.STATE_COMPLETED.equals(sel.state)) {
            db.setDailyTaskState(sel.id, DailyTask.STATE_PENDING);
            db.logAction("UNDONE", sel.title, null);
        } else {
            db.setDailyTaskState(sel.id, DailyTask.STATE_COMPLETED);
            db.logAction("COMPLETED", sel.title, null);
        }
        exportOrg();
        refreshData();
        refreshUI();
    }

    private void onSkip() {
        DailyTask sel = findSelectedTask();
        if (sel == null) return;
        if (DailyTask.STATE_SKIPPED.equals(sel.state)) {
            db.setDailyTaskState(sel.id, DailyTask.STATE_PENDING);
            db.logAction("UNSKIPPED", sel.title, null);
        } else {
            db.setDailyTaskState(sel.id, DailyTask.STATE_SKIPPED);
            db.logAction("SKIPPED", sel.title, null);
        }
        exportOrg();
        refreshData();
        refreshUI();
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private void showSplitDialog(DailyTask task) {
        View view = getLayoutInflater().inflate(R.layout.dialog_split, null);
        EditText editTitle1 = view.findViewById(R.id.edit_title1);
        EditText editTitle2 = view.findViewById(R.id.edit_title2);
        LinearLayout colorPicker1 = view.findViewById(R.id.color_picker1);
        LinearLayout colorPicker2 = view.findViewById(R.id.color_picker2);

        editTitle1.setText(task.title);
        editTitle2.setText(task.title);

        TaskColor initColor = TaskColor.fromName(task.color);
        final TaskColor[] sel1 = {initColor};
        final TaskColor[] sel2 = {initColor};

        buildColorPicker(colorPicker1, sel1, initColor);
        buildColorPicker(colorPicker2, sel2, initColor);

        new AlertDialog.Builder(this)
            .setTitle("Split Task")
            .setView(view)
            .setPositiveButton("Split", (d, w) -> {
                String t1 = editTitle1.getText().toString().trim();
                String t2 = editTitle2.getText().toString().trim();
                if (t1.isEmpty() || t2.isEmpty()) return;
                db.splitDailyTask(task.id, today(), t1, sel1[0].name(), t2, sel2[0].name());
                db.logAction("SPLIT", task.title, t1 + "|" + t2);
                exportOrg();
                selectedTaskId = -1;
                refreshData();
                refreshUI();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showQuickAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_quick_add, null);
        EditText editTitle = view.findViewById(R.id.edit_title);
        LinearLayout colorPicker = view.findViewById(R.id.color_picker);

        final TaskColor[] selected = {TaskColor.BLUE};
        buildColorPicker(colorPicker, selected, TaskColor.BLUE);

        new AlertDialog.Builder(this)
            .setTitle("Add to Queue")
            .setView(view)
            .setPositiveButton("Add", (d, w) -> {
                String title = editTitle.getText().toString().trim();
                if (title.isEmpty()) return;
                db.addToQueue(title, selected[0].name());
                db.logAction("QUICK_ADD", title, null);
                exportOrg();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Color Picker Helper ───────────────────────────────────────────────────

    static void buildColorPicker(LinearLayout container, TaskColor[] selectedHolder,
                                  TaskColor initial) {
        container.removeAllViews();
        float density = container.getContext().getResources().getDisplayMetrics().density;
        int sizePx   = (int)(36 * density);
        int marginPx = (int)(3 * density);

        TaskColor[] colors = TaskColor.values();
        View[] views = new View[colors.length];

        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            final TaskColor color = colors[i];
            View v = new View(container.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            v.setLayoutParams(lp);
            views[i] = v;
            applyColorSwatch(v, color, color == initial, density);
            v.setOnClickListener(click -> {
                selectedHolder[0] = color;
                for (int j = 0; j < colors.length; j++) {
                    applyColorSwatch(views[j], colors[j], j == idx, density);
                }
            });
            container.addView(v);
        }
    }

    private static void applyColorSwatch(View v, TaskColor color, boolean selected, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color.toArgb());
        if (selected) {
            d.setStroke((int)(3 * density), 0xFF000000);
        }
        v.setBackground(d);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void exportOrg() {
        new OrgExporter(this, db).exportAsync();
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
