package it.lo.exp.nulladies;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NullaDies";

    private DatabaseHelper db;
    private DayManager dayManager;

    // UI references
    private LinearLayout completedSection;
    private View completedDivider;
    private LinearLayout completedGrid;
    private LinearLayout currentTaskSection;
    private View currentTaskColorBar;
    private TextView currentTaskTitle;
    private Button btnDone, btnSkip, btnPush, btnSplit;
    private TextView allDoneText;
    private View remainingDivider;
    private LinearLayout remainingGrid;

    // State
    private List<DailyTask> completedTasks = new ArrayList<>();
    private List<DailyTask> pendingTasks   = new ArrayList<>();
    private List<DailyTask> skippedTasks   = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        dayManager = new DayManager(db);

        // Bind views
        completedSection    = findViewById(R.id.completed_section);
        completedDivider    = findViewById(R.id.completed_divider);
        completedGrid       = findViewById(R.id.completed_grid);
        currentTaskSection  = findViewById(R.id.current_task_section);
        currentTaskColorBar = findViewById(R.id.current_task_color_bar);
        currentTaskTitle    = findViewById(R.id.current_task_title);
        btnDone             = findViewById(R.id.btn_done);
        btnSkip             = findViewById(R.id.btn_skip);
        btnPush             = findViewById(R.id.btn_push);
        btnSplit            = findViewById(R.id.btn_split);
        allDoneText         = findViewById(R.id.all_done_text);
        remainingDivider    = findViewById(R.id.remaining_divider);
        remainingGrid       = findViewById(R.id.remaining_grid);

        // Action buttons
        btnDone.setOnClickListener(v  -> onDone());
        btnSkip.setOnClickListener(v  -> onSkip());
        btnPush.setOnClickListener(v  -> onPush());
        btnSplit.setOnClickListener(v -> onSplit());

        // FAB
        findViewById(R.id.fab_quick_add).setOnClickListener(v -> showQuickAddDialog());

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
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        dayManager.checkAndRolloverIfNeeded();
        refreshData();
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

    // ─── UI ────────────────────────────────────────────────────────────────────

    private void refreshUI() {
        buildCompletedGrid();
        buildCurrentTaskCard();
        buildRemainingGrid();
    }

    private void buildCompletedGrid() {
        completedGrid.removeAllViews();
        if (completedTasks.isEmpty()) {
            completedSection.setVisibility(View.GONE);
            completedDivider.setVisibility(View.GONE);
            return;
        }
        completedSection.setVisibility(View.VISIBLE);
        completedDivider.setVisibility(View.VISIBLE);
        addSquaresToContainer(completedGrid, completedTasks, 28, true);
    }

    private void buildCurrentTaskCard() {
        if (pendingTasks.isEmpty()) {
            currentTaskSection.setVisibility(View.GONE);
            // Show "all done" only if we had tasks (completed or skipped exist)
            boolean hadTasks = !completedTasks.isEmpty() || !skippedTasks.isEmpty();
            allDoneText.setVisibility(hadTasks ? View.VISIBLE : View.GONE);
            return;
        }
        allDoneText.setVisibility(View.GONE);
        currentTaskSection.setVisibility(View.VISIBLE);

        DailyTask current = pendingTasks.get(0);
        currentTaskTitle.setText(current.title);

        GradientDrawable bar = new GradientDrawable();
        bar.setShape(GradientDrawable.RECTANGLE);
        bar.setColor(TaskColor.fromName(current.color).toArgb());
        currentTaskColorBar.setBackground(bar);
    }

    private void buildRemainingGrid() {
        remainingGrid.removeAllViews();
        // Remaining = pending tasks except current (index 0) + skipped tasks
        List<DailyTask> gridTasks = new ArrayList<>();
        if (pendingTasks.size() > 1) {
            gridTasks.addAll(pendingTasks.subList(1, pendingTasks.size()));
        }
        gridTasks.addAll(skippedTasks);

        boolean showDivider = !gridTasks.isEmpty() && !completedTasks.isEmpty();
        remainingDivider.setVisibility(showDivider ? View.VISIBLE : View.GONE);

        if (!gridTasks.isEmpty()) {
            addSquaresToContainer(remainingGrid, gridTasks, 52, false);
        }
    }

    private void addSquaresToContainer(LinearLayout container, List<DailyTask> tasks,
                                       int squareDp, boolean compact) {
        float density = getResources().getDisplayMetrics().density;
        int squarePx  = (int)(squareDp * density);
        int marginPx  = (int)(3 * density);
        int screenW   = getResources().getDisplayMetrics().widthPixels;
        int available = screenW - (int)(16 * density);
        int cols      = Math.max(1, available / (squarePx + marginPx * 2));

        LinearLayout row = null;
        for (int i = 0; i < tasks.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(row);
            }
            DailyTask task = tasks.get(i);
            View square = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(squarePx, squarePx);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            square.setLayoutParams(lp);

            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.RECTANGLE);
            d.setColor(TaskColor.fromName(task.color).toArgb());
            square.setBackground(d);

            if (DailyTask.STATE_SKIPPED.equals(task.state)) {
                square.setAlpha(0.35f);
            }

            if (!compact) {
                final int taskId = task.id;
                square.setOnClickListener(v -> {
                    db.promoteTask(taskId, today());
                    refreshData();
                    refreshUI();
                });
            }
            if (row != null) row.addView(square);
        }
    }

    // ─── Actions ───────────────────────────────────────────────────────────────

    private void onDone() {
        if (pendingTasks.isEmpty()) return;
        DailyTask current = pendingTasks.get(0);
        db.setDailyTaskState(current.id, DailyTask.STATE_COMPLETED);
        db.logAction("COMPLETED", current.title, null);
        exportOrg();
        refreshData();
        refreshUI();
    }

    private void onSkip() {
        if (pendingTasks.isEmpty()) return;
        DailyTask current = pendingTasks.get(0);
        db.setDailyTaskState(current.id, DailyTask.STATE_SKIPPED);
        db.logAction("SKIPPED", current.title, null);
        exportOrg();
        refreshData();
        refreshUI();
    }

    private void onPush() {
        if (pendingTasks.isEmpty()) return;
        DailyTask current = pendingTasks.get(0);
        db.pushTaskDown(current.id, today());
        db.logAction("PUSHED", current.title, null);
        exportOrg();
        refreshData();
        refreshUI();
    }

    private void onSplit() {
        if (pendingTasks.isEmpty()) return;
        showSplitDialog(pendingTasks.get(0));
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
