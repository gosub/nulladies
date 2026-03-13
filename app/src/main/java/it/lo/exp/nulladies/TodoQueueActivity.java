package it.lo.exp.nulladies;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class TodoQueueActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private ListView listView;
    private TextView emptyText;
    private List<QueueTask> tasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo_queue);

        db = new DatabaseHelper(this);
        listView  = findViewById(R.id.queue_list);
        emptyText = findViewById(R.id.queue_empty_text);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add) { showAddDialog(); return true; }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        tasks = db.getQueueTasks();
        emptyText.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setAdapter(new QueueAdapter());
    }

    // ─── Adapter ───────────────────────────────────────────────────────────────

    private class QueueAdapter extends BaseAdapter {

        @Override public int getCount()                    { return tasks.size(); }
        @Override public Object getItem(int pos)           { return tasks.get(pos); }
        @Override public long getItemId(int pos)           { return tasks.get(pos).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_queue_task, parent, false);
            }
            QueueTask task = tasks.get(position);

            TextView title = convertView.findViewById(R.id.task_title);
            View colorDot  = convertView.findViewById(R.id.color_dot);
            Button btnUp   = convertView.findViewById(R.id.btn_up);
            Button btnDown = convertView.findViewById(R.id.btn_down);
            Button btnSplit  = convertView.findViewById(R.id.btn_split);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);

            title.setText(task.title);
            applyCircleColor(colorDot, TaskColor.fromName(task.color).toArgb());

            btnUp.setEnabled(position > 0);
            btnDown.setEnabled(position < tasks.size() - 1);

            btnUp.setOnClickListener(v -> {
                db.moveQueueTaskUp(task.id);
                reload();
            });
            btnDown.setOnClickListener(v -> {
                db.moveQueueTaskDown(task.id);
                reload();
            });
            btnSplit.setOnClickListener(v -> showSplitDialog(task));
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(TodoQueueActivity.this)
                    .setTitle("Delete task?")
                    .setMessage("\"" + task.title + "\" will be removed from the queue.")
                    .setPositiveButton("Delete", (d, w) -> {
                        db.deleteQueueTask(task.id);
                        exportOrg();
                        reload();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });

            return convertView;
        }
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private void showAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_quick_add, null);
        EditText editTitle = view.findViewById(R.id.edit_title);
        LinearLayout colorPicker = view.findViewById(R.id.color_picker);

        final TaskColor[] selected = {TaskColor.BLUE};
        MainActivity.buildColorPicker(colorPicker, selected, TaskColor.BLUE);

        new AlertDialog.Builder(this)
            .setTitle("Add to Queue")
            .setView(view)
            .setPositiveButton("Add", (d, w) -> {
                String title = editTitle.getText().toString().trim();
                if (title.isEmpty()) return;
                db.addToQueue(title, selected[0].name());
                exportOrg();
                reload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showSplitDialog(QueueTask task) {
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

        MainActivity.buildColorPicker(colorPicker1, sel1, initColor);
        MainActivity.buildColorPicker(colorPicker2, sel2, initColor);

        new AlertDialog.Builder(this)
            .setTitle("Split Task")
            .setView(view)
            .setPositiveButton("Split", (d, w) -> {
                String t1 = editTitle1.getText().toString().trim();
                String t2 = editTitle2.getText().toString().trim();
                if (t1.isEmpty() || t2.isEmpty()) return;
                db.splitQueueTask(task.id, t1, sel1[0].name(), t2, sel2[0].name());
                exportOrg();
                reload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void exportOrg() {
        new OrgExporter(this, db).exportAsync();
    }

    private static void applyCircleColor(View v, int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
    }
}
