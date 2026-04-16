package it.lo.exp.nulladies;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class JsonBackup {

    private static final String TAG = "NullaDies";
    private static final String FILENAME = "nulladies_backup.json";
    private static final int VERSION = 1;

    // ─── Export ────────────────────────────────────────────────────────────────

    public static void exportAsync(Context context, DatabaseHelper db,
                                   Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                String treeUriStr = db.getSetting("backup_folder_uri", "");
                if (treeUriStr.isEmpty()) {
                    post(onFailure);
                    return;
                }
                Uri treeUri = Uri.parse(treeUriStr);
                String json = buildJson(db).toString(2);
                Uri fileUri = getOrCreateFile(context, treeUri);
                if (fileUri == null) { post(onFailure); return; }

                ContentResolver cr = context.getContentResolver();
                try (OutputStream os = cr.openOutputStream(fileUri, "wt");
                     OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    w.write(json);
                    w.flush();
                }
                Log.d(TAG, "JSON export written to " + fileUri);
                post(onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "JSON export failed: " + e.getMessage());
                post(onFailure);
            }
        }).start();
    }

    private static JSONObject buildJson(DatabaseHelper db) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("exported_at", Instant.now().toString());

        JSONArray recurringArr = new JSONArray();
        for (RecurringTask rt : db.getRecurringTasks()) {
            JSONObject o = new JSONObject();
            o.put("position", rt.position);
            o.put("title", rt.title != null ? rt.title : JSONObject.NULL);
            o.put("color", rt.color != null ? rt.color : JSONObject.NULL);
            o.put("type", rt.type);
            o.put("rule_data", rt.ruleData != null ? rt.ruleData : JSONObject.NULL);
            recurringArr.put(o);
        }
        root.put("recurring_tasks", recurringArr);

        JSONArray queueArr = new JSONArray();
        for (QueueTask qt : db.getQueueTasks()) {
            JSONObject o = new JSONObject();
            o.put("position", qt.position);
            o.put("title", qt.title);
            o.put("color", qt.color);
            queueArr.put(o);
        }
        root.put("todo_queue", queueArr);

        JSONObject settings = new JSONObject();
        settings.put("rollover_time", db.getSetting("rollover_time", "00:00"));
        root.put("settings", settings);

        return root;
    }

    private static Uri getOrCreateFile(Context context, Uri treeUri) {
        try {
            ContentResolver cr = context.getContentResolver();
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

            String existingDocId = null;
            try (android.database.Cursor c = cr.query(childrenUri,
                    new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    }, null, null, null)) {
                if (c != null) {
                    while (c.moveToNext()) {
                        if (FILENAME.equals(c.getString(1))) {
                            existingDocId = c.getString(0);
                            break;
                        }
                    }
                }
            }

            if (existingDocId != null) {
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, existingDocId);
            }
            return DocumentsContract.createDocument(cr, parentDocUri, "application/json", FILENAME);
        } catch (Exception e) {
            Log.e(TAG, "getOrCreateFile failed: " + e.getMessage());
            return null;
        }
    }

    // ─── Import ────────────────────────────────────────────────────────────────

    public static void importAsync(Context context, DatabaseHelper db, Uri fileUri,
                                   Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                ContentResolver cr = context.getContentResolver();
                StringBuilder sb = new StringBuilder();
                try (InputStream is = cr.openInputStream(fileUri);
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                }
                JSONObject root = new JSONObject(sb.toString());
                db.restoreFromBackup(root);
                Log.d(TAG, "JSON import complete");
                post(onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "JSON import failed: " + e.getMessage());
                post(onFailure);
            }
        }).start();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private static void post(Runnable r) {
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }
}
