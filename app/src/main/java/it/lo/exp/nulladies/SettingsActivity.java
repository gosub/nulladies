package it.lo.exp.nulladies;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class SettingsActivity extends Activity {

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

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_pick_time).setOnClickListener(v -> showTimePicker());
        findViewById(R.id.btn_pick_folder).setOnClickListener(v -> pickFolder());

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
