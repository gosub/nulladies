package it.lo.exp.nulladies;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public class RecurringTasksActivity extends Activity {

    private DatabaseHelper db;
    private ListView listView;
    private TextView emptyText;
    private List<RecurringTask> tasks;

    private static final String[] TYPE_LABELS = {
        "Daily", "Days of Week", "Specific Dates", "Queue Slot"
    };
    private static final String[] TYPE_VALUES = {
        RecurringTask.TYPE_DAILY,
        RecurringTask.TYPE_DAYS_OF_WEEK,
        RecurringTask.TYPE_SPECIFIC_DATES,
        RecurringTask.TYPE_QUEUE_SLOT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring_tasks);

        db = new DatabaseHelper(this);
        listView  = findViewById(R.id.recurring_list);
        emptyText = findViewById(R.id.recurring_empty_text);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_recurring).setOnClickListener(v -> showEditDialog(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        tasks = db.getRecurringTasks();
        emptyText.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setAdapter(new RecurringAdapter());
    }

    // ─── Adapter ───────────────────────────────────────────────────────────────

    private class RecurringAdapter extends BaseAdapter {

        @Override public int getCount()          { return tasks.size(); }
        @Override public Object getItem(int pos) { return tasks.get(pos); }
        @Override public long getItemId(int pos) { return tasks.get(pos).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_recurring_task, parent, false);
            }
            RecurringTask task = tasks.get(position);

            TextView title  = convertView.findViewById(R.id.task_title);
            TextView type   = convertView.findViewById(R.id.task_type);
            View colorDot   = convertView.findViewById(R.id.color_dot);
            Button btnUp    = convertView.findViewById(R.id.btn_up);
            Button btnDown  = convertView.findViewById(R.id.btn_down);
            Button btnEdit  = convertView.findViewById(R.id.btn_edit);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);

            boolean isSlot = RecurringTask.TYPE_QUEUE_SLOT.equals(task.type);
            title.setText(isSlot ? "[Queue Slot]" : task.title);
            type.setText(task.describeType());
            colorDot.setVisibility(isSlot ? View.INVISIBLE : View.VISIBLE);
            if (!isSlot && task.color != null) {
                applyCircleColor(colorDot, TaskColor.fromName(task.color).toArgb());
            }

            btnUp.setEnabled(position > 0);
            btnDown.setEnabled(position < tasks.size() - 1);

            btnUp.setOnClickListener(v -> {
                db.moveRecurringTaskUp(task.id);
                reload();
            });
            btnDown.setOnClickListener(v -> {
                db.moveRecurringTaskDown(task.id);
                reload();
            });
            btnEdit.setOnClickListener(v -> showEditDialog(task));
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(RecurringTasksActivity.this)
                    .setTitle("Delete recurring task?")
                    .setMessage(isSlot ? "Delete this Queue Slot?" :
                        "\"" + task.title + "\" will be removed.")
                    .setPositiveButton("Delete", (d, w) -> {
                        db.deleteRecurringTask(task.id);
                        reload();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });

            return convertView;
        }
    }

    // ─── Add / Edit Dialog ─────────────────────────────────────────────────────

    private void showEditDialog(RecurringTask existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_recurring, null);

        Spinner spinnerType    = view.findViewById(R.id.spinner_type);
        View labelTitle        = view.findViewById(R.id.label_title);
        EditText editTitle     = view.findViewById(R.id.edit_title);
        View labelColor        = view.findViewById(R.id.label_color);
        View colorScroll       = view.findViewById(R.id.color_scroll);
        LinearLayout colorPicker = view.findViewById(R.id.color_picker);
        LinearLayout dowSection = view.findViewById(R.id.days_of_week_section);
        LinearLayout datesSection = view.findViewById(R.id.specific_dates_section);
        EditText editDates     = view.findViewById(R.id.edit_dates);

        CheckBox cbMon = view.findViewById(R.id.cb_mon);
        CheckBox cbTue = view.findViewById(R.id.cb_tue);
        CheckBox cbWed = view.findViewById(R.id.cb_wed);
        CheckBox cbThu = view.findViewById(R.id.cb_thu);
        CheckBox cbFri = view.findViewById(R.id.cb_fri);
        CheckBox cbSat = view.findViewById(R.id.cb_sat);
        CheckBox cbSun = view.findViewById(R.id.cb_sun);
        CheckBox[] dayCbs = {cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbSun};

        // Spinner setup
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, TYPE_LABELS);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(spinnerAdapter);

        // Color picker
        TaskColor initColor = (existing != null && existing.color != null)
            ? TaskColor.fromName(existing.color) : TaskColor.BLUE;
        final TaskColor[] selected = {initColor};
        MainActivity.buildColorPicker(colorPicker, selected, initColor);

        // Spinner selection listener
        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                boolean isSlot = RecurringTask.TYPE_QUEUE_SLOT.equals(TYPE_VALUES[pos]);
                boolean isDow  = RecurringTask.TYPE_DAYS_OF_WEEK.equals(TYPE_VALUES[pos]);
                boolean isDates = RecurringTask.TYPE_SPECIFIC_DATES.equals(TYPE_VALUES[pos]);
                int titleVis = isSlot ? View.GONE : View.VISIBLE;
                labelTitle.setVisibility(titleVis);
                editTitle.setVisibility(titleVis);
                labelColor.setVisibility(titleVis);
                colorScroll.setVisibility(titleVis);
                dowSection.setVisibility(isDow ? View.VISIBLE : View.GONE);
                datesSection.setVisibility(isDates ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Pre-fill from existing
        if (existing != null) {
            editTitle.setText(existing.title);
            // Set spinner to matching type
            for (int i = 0; i < TYPE_VALUES.length; i++) {
                if (TYPE_VALUES[i].equals(existing.type)) {
                    spinnerType.setSelection(i);
                    break;
                }
            }
            // Pre-fill rule data
            if (RecurringTask.TYPE_DAYS_OF_WEEK.equals(existing.type) && existing.ruleData != null) {
                for (String d : existing.ruleData.split(",")) {
                    int dayNum = Integer.parseInt(d.trim());
                    if (dayNum >= 1 && dayNum <= 7) dayCbs[dayNum - 1].setChecked(true);
                }
            } else if (RecurringTask.TYPE_SPECIFIC_DATES.equals(existing.type)) {
                editDates.setText(existing.ruleData != null ? existing.ruleData : "");
            }
        }

        String dialogTitle = existing == null ? "Add Recurring Task" : "Edit Recurring Task";
        new AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(view)
            .setPositiveButton("Save", (d, w) -> {
                int typeIdx = spinnerType.getSelectedItemPosition();
                String type = TYPE_VALUES[typeIdx];
                boolean isSlot = RecurringTask.TYPE_QUEUE_SLOT.equals(type);

                String title = isSlot ? null : editTitle.getText().toString().trim();
                if (!isSlot && (title == null || title.isEmpty())) return;

                String color = isSlot ? null : selected[0].name();
                String ruleData = null;

                if (RecurringTask.TYPE_DAYS_OF_WEEK.equals(type)) {
                    StringBuilder rb = new StringBuilder();
                    for (int i = 0; i < dayCbs.length; i++) {
                        if (dayCbs[i].isChecked()) {
                            if (rb.length() > 0) rb.append(",");
                            rb.append(i + 1);
                        }
                    }
                    ruleData = rb.toString();
                } else if (RecurringTask.TYPE_SPECIFIC_DATES.equals(type)) {
                    ruleData = editDates.getText().toString().trim();
                }

                if (existing == null) {
                    db.addRecurringTask(title, color, type, ruleData);
                } else {
                    db.updateRecurringTask(existing.id, title, color, type, ruleData);
                }
                reload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static void applyCircleColor(View v, int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
    }
}
