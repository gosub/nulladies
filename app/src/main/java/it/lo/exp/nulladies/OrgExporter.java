package it.lo.exp.nulladies;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class OrgExporter {

    private static final String TAG = "NullaDies";
    private static final String FILENAME = "nulladies_backup.org";

    private final Context context;
    private final DatabaseHelper db;

    public OrgExporter(Context context, DatabaseHelper db) {
        this.context = context;
        this.db = db;
    }

    public void exportAsync() {
        new Thread(this::export).start();
    }

    private void export() {
        String treeUriStr = db.getSetting("backup_folder_uri", "");
        if (treeUriStr.isEmpty()) return;

        try {
            Uri treeUri = Uri.parse(treeUriStr);
            String content = buildOrgContent();
            Uri fileUri = getOrCreateFile(treeUri);
            if (fileUri == null) return;

            ContentResolver cr = context.getContentResolver();
            try (OutputStream os = cr.openOutputStream(fileUri, "wt");
                 OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(content);
                writer.flush();
            }
            Log.d(TAG, "Org export written to " + fileUri);
        } catch (Exception e) {
            Log.e(TAG, "Org export failed: " + e.getMessage());
        }
    }

    private Uri getOrCreateFile(Uri treeUri) {
        try {
            ContentResolver cr = context.getContentResolver();
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

            // Search for existing file
            android.database.Cursor c = cr.query(childrenUri,
                new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                }, null, null, null);
            String existingDocId = null;
            if (c != null) {
                while (c.moveToNext()) {
                    if (FILENAME.equals(c.getString(1))) {
                        existingDocId = c.getString(0);
                        break;
                    }
                }
                c.close();
            }

            if (existingDocId != null) {
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, existingDocId);
            }

            // Create new file
            return DocumentsContract.createDocument(cr, parentDocUri, "text/plain", FILENAME);
        } catch (Exception e) {
            Log.e(TAG, "getOrCreateFile failed: " + e.getMessage());
            return null;
        }
    }

    private String buildOrgContent() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        StringBuilder sb = new StringBuilder();

        sb.append("#+TITLE: NullaDies Backup\n");
        sb.append("#+DATE: [").append(today).append("]\n\n");

        // Config section
        sb.append("* Config\n");

        sb.append("** Recurring Tasks\n");
        for (RecurringTask rt : db.getRecurringTasks()) {
            if (RecurringTask.TYPE_QUEUE_SLOT.equals(rt.type)) {
                sb.append("- [ ] [[QUEUE_SLOT]]\n");
            } else {
                sb.append("- [ ] ").append(rt.title)
                  .append(" :").append(rt.type.toLowerCase()).append(":");
                if (rt.ruleData != null) sb.append(rt.ruleData).append(":");
                if (rt.color != null) sb.append(" :").append(rt.color.toLowerCase()).append(":");
                sb.append("\n");
            }
        }

        sb.append("\n** Todo Queue\n");
        for (QueueTask qt : db.getQueueTasks()) {
            sb.append("- [ ] ").append(qt.title)
              .append(" :").append(qt.color.toLowerCase()).append(":\n");
        }

        // Action log section
        sb.append("\n* Action Log\n");
        String currentDate = null;
        for (String[] row : db.getAllActionLog()) {
            // row = {timestamp, event_type, task_title, extra_data}
            String ts = row[0];
            String date = ts.length() >= 10 ? ts.substring(0, 10) : ts;
            if (!date.equals(currentDate)) {
                currentDate = date;
                sb.append("** ").append(date).append("\n");
            }
            String displayTs = "[" + ts.replace("T", " ").replaceAll("\\..*", "").replace("Z", "") + "]";
            sb.append("- ").append(displayTs).append(" ").append(row[1]);
            if (row[2] != null) sb.append(" \"").append(row[2]).append("\"");
            if (row[3] != null) sb.append(" ").append(row[3]);
            sb.append("\n");
        }

        return sb.toString();
    }
}
