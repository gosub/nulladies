package it.lo.exp.nulladies;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class LogActivity extends AppCompatActivity {

    private static final DateTimeFormatter IN_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private List<String[]> entries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        DatabaseHelper db = new DatabaseHelper(this);
        entries = db.getAllActionLog();
        Collections.reverse(entries);

        ListView listView   = findViewById(R.id.log_list);
        TextView emptyText  = findViewById(R.id.log_empty_text);

        if (entries.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            listView.setAdapter(new LogAdapter());
        }
    }

    private class LogAdapter extends BaseAdapter {

        @Override public int getCount()          { return entries.size(); }
        @Override public Object getItem(int pos) { return entries.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_log_entry, parent, false);
            }
            String[] row = entries.get(position);
            String timestamp = row[0];
            String eventType = row[1];
            String taskTitle = row[2];

            TextView primary   = convertView.findViewById(R.id.log_primary);
            TextView secondary = convertView.findViewById(R.id.log_timestamp);

            String label = eventType != null ? eventType : "";
            if (taskTitle != null && !taskTitle.isEmpty()) {
                label += ": " + taskTitle;
            }
            primary.setText(label);

            String formatted = timestamp;
            if (timestamp != null) {
                try {
                    formatted = LocalDateTime.parse(timestamp, IN_FMT).format(OUT_FMT);
                } catch (Exception ignored) {}
            }
            secondary.setText(formatted != null ? formatted : "");

            return convertView;
        }
    }
}
