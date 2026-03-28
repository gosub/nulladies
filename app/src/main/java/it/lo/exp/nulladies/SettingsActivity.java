package it.lo.exp.nulladies;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_BACKUP_FOLDER = 1;

    private DatabaseHelper db;
    private TextView rolloverTimeDisplay;
    private TextView backupFolderDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db = new DatabaseHelper(this);

        rolloverTimeDisplay = findViewById(R.id.rollover_time_display);
        backupFolderDisplay = findViewById(R.id.backup_folder_display);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_pick_time).setOnClickListener(v -> showTimePicker());
        findViewById(R.id.btn_pick_folder).setOnClickListener(v -> pickFolder());

        if (BuildConfig.DEBUG) {
            findViewById(R.id.debug_section).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_debug_reset_today).setOnClickListener(v -> resetToday());
            findViewById(R.id.btn_debug_db_stats).setOnClickListener(v -> showDbStats());
            findViewById(R.id.btn_debug_wipe_db).setOnClickListener(v -> confirmWipeDb());
        }

        loadSettings();
    }

    private void loadSettings() {
        String time = db.getSetting("rollover_time", "00:00");
        rolloverTimeDisplay.setText(time);

        String folderUri = db.getSetting("backup_folder_uri", "");
        if (folderUri.isEmpty()) {
            backupFolderDisplay.setText("Not set");
        } else {
            // Display a friendly version of the URI
            try {
                Uri uri = Uri.parse(folderUri);
                String path = uri.getLastPathSegment();
                backupFolderDisplay.setText(path != null ? path : folderUri);
            } catch (Exception e) {
                backupFolderDisplay.setText(folderUri);
            }
        }
    }

    private void showTimePicker() {
        String current = db.getSetting("rollover_time", "00:00");
        int hour = 0, minute = 0;
        try {
            String[] parts = current.split(":");
            hour   = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}

        new TimePickerDialog(this, (view, h, m) -> {
            String newTime = String.format("%02d:%02d", h, m);
            db.saveSetting("rollover_time", newTime);
            rolloverTimeDisplay.setText(newTime);
        }, hour, minute, true).show();
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_BACKUP_FOLDER);
    }

    private void resetToday() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        new AlertDialog.Builder(this)
            .setTitle("Reset today?")
            .setMessage("Deletes today's tasks and DAY_START log entry. The next app resume will regenerate the day.")
            .setPositiveButton("Reset", (d, w) -> {
                db.resetToday(today);
                Toast.makeText(this, "Today reset. Go back and reopen the app.", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showDbStats() {
        new AlertDialog.Builder(this)
            .setTitle("DB stats")
            .setMessage(db.getDbStats())
            .setPositiveButton("OK", null)
            .show();
    }

    private void confirmWipeDb() {
        new AlertDialog.Builder(this)
            .setTitle("Wipe database?")
            .setMessage("Deletes all data. The app will close and start fresh on next launch.")
            .setPositiveButton("Wipe", (d, w) -> {
                db.close();
                getApplicationContext().deleteDatabase("nulladies.db");
                finishAffinity();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_BACKUP_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;
            // Persist permission across reboots
            getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            db.saveSetting("backup_folder_uri", treeUri.toString());
            loadSettings();
        }
    }
}
