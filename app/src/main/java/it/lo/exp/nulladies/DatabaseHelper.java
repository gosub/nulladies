package it.lo.exp.nulladies;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "NullaDies";
    private static final String DB_NAME = "nulladies.db";
    private static final int DB_VERSION = 4;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE recurring_tasks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "position INTEGER NOT NULL," +
            "title TEXT," +
            "color TEXT," +
            "type TEXT NOT NULL," +
            "rule_data TEXT" +
        ")");

        db.execSQL("CREATE TABLE todo_queue (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "position INTEGER NOT NULL," +
            "title TEXT NOT NULL," +
            "color TEXT NOT NULL," +
            "state TEXT NOT NULL DEFAULT 'pending'" +
        ")");

        db.execSQL("CREATE TABLE daily_tasks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "date TEXT NOT NULL," +
            "position INTEGER NOT NULL," +
            "title TEXT NOT NULL," +
            "color TEXT NOT NULL," +
            "state TEXT NOT NULL," +
            "source TEXT NOT NULL DEFAULT 'recurring'," +
            "queue_item_id INTEGER," +
            "recurring_task_id INTEGER" +
        ")");

        db.execSQL("CREATE TABLE action_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "timestamp TEXT NOT NULL," +
            "event_type TEXT NOT NULL," +
            "task_title TEXT," +
            "extra_data TEXT" +
        ")");

        db.execSQL("CREATE TABLE settings (" +
            "key TEXT PRIMARY KEY," +
            "value TEXT NOT NULL" +
        ")");

        // Defaults
        ContentValues cv = new ContentValues();
        cv.put("key", "rollover_time");
        cv.put("value", "00:00");
        db.insert("settings", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE daily_tasks ADD COLUMN source TEXT NOT NULL DEFAULT 'recurring'");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE todo_queue ADD COLUMN state TEXT NOT NULL DEFAULT 'pending'");
            db.execSQL("ALTER TABLE daily_tasks ADD COLUMN queue_item_id INTEGER");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE daily_tasks ADD COLUMN recurring_task_id INTEGER");
        }
    }

    // ─── Settings ──────────────────────────────────────────────────────────────

    public String getSetting(String key, String defaultValue) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key});
        try {
            if (c.moveToFirst()) return c.getString(0);
            return defaultValue;
        } finally {
            c.close();
        }
    }

    public void saveSetting(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ─── Action Log ────────────────────────────────────────────────────────────

    public void logAction(String eventType, String taskTitle, String extraData) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("timestamp", java.time.Instant.now().toString());
        cv.put("event_type", eventType);
        cv.put("task_title", taskTitle);
        cv.put("extra_data", extraData);
        db.insert("action_log", null, cv);
    }

    public List<String[]> getActionLogForDate(String date) {
        SQLiteDatabase db = getReadableDatabase();
        // timestamp starts with the date string (ISO format: 2026-03-01T...)
        Cursor c = db.rawQuery(
            "SELECT timestamp, event_type, task_title, extra_data FROM action_log " +
            "WHERE timestamp LIKE ? ORDER BY timestamp ASC",
            new String[]{date + "%"});
        List<String[]> rows = new ArrayList<>();
        while (c.moveToNext()) {
            rows.add(new String[]{c.getString(0), c.getString(1), c.getString(2), c.getString(3)});
        }
        c.close();
        return rows;
    }

    public List<String[]> getAllActionLog() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT timestamp, event_type, task_title, extra_data FROM action_log ORDER BY timestamp ASC",
            null);
        List<String[]> rows = new ArrayList<>();
        while (c.moveToNext()) {
            rows.add(new String[]{c.getString(0), c.getString(1), c.getString(2), c.getString(3)});
        }
        c.close();
        return rows;
    }

    // ─── Day Rollover Check ────────────────────────────────────────────────────

    public boolean needsRollover(String today) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM action_log WHERE event_type='DAY_START' AND timestamp LIKE ?",
            new String[]{today + "%"});
        boolean needs = true;
        if (c.moveToFirst()) {
            needs = c.getInt(0) == 0;
        }
        c.close();
        return needs;
    }

    // ─── Daily Tasks ───────────────────────────────────────────────────────────

    public void generateDailyTasks(String today) {
        SQLiteDatabase db = getWritableDatabase();

        // Clear any existing tasks for today (shouldn't happen, but safety)
        db.delete("daily_tasks", "date=?", new String[]{today});

        List<RecurringTask> recurring = getRecurringTasks();
        LocalDate date = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE);
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Mon..7=Sun

        int position = 0;
        int queueOffset = 0;
        for (RecurringTask rt : recurring) {
            boolean applies = false;
            switch (rt.type) {
                case RecurringTask.TYPE_DAILY:
                    applies = true;
                    break;
                case RecurringTask.TYPE_DAYS_OF_WEEK:
                    if (rt.ruleData != null) {
                        for (String d : rt.ruleData.split(",")) {
                            if (d.trim().equals(String.valueOf(dayOfWeek))) {
                                applies = true;
                                break;
                            }
                        }
                    }
                    break;
                case RecurringTask.TYPE_SPECIFIC_DATES:
                    if (rt.ruleData != null) {
                        for (String d : rt.ruleData.split(",")) {
                            if (d.trim().equals(today)) {
                                applies = true;
                                break;
                            }
                        }
                    }
                    break;
                case RecurringTask.TYPE_QUEUE_SLOT:
                    applies = true;
                    break;
            }

            if (!applies) continue;

            if (RecurringTask.TYPE_QUEUE_SLOT.equals(rt.type)) {
                QueueTask qt = peekQueueTask(db, queueOffset);
                if (qt != null) {
                    insertDailyTask(db, today, position++, qt.title, qt.color, "queue_slot", qt.id, rt.id);
                    queueOffset++;
                }
            } else {
                insertDailyTask(db, today, position++, rt.title, rt.color, "recurring", 0, rt.id);
            }
        }

        Log.d(TAG, "Generated " + position + " daily tasks for " + today);
    }

    private void insertDailyTask(SQLiteDatabase db, String date, int position, String title, String color, String source, int queueItemId, int recurringTaskId) {
        ContentValues cv = new ContentValues();
        cv.put("date", date);
        cv.put("position", position);
        cv.put("title", title);
        cv.put("color", color);
        cv.put("state", DailyTask.STATE_PENDING);
        cv.put("source", source);
        if (queueItemId > 0) cv.put("queue_item_id", queueItemId);
        if (recurringTaskId > 0) cv.put("recurring_task_id", recurringTaskId);
        db.insert("daily_tasks", null, cv);
    }

    private QueueTask peekQueueTask(SQLiteDatabase db, int offset) {
        Cursor c = db.rawQuery(
            "SELECT id, position, title, color FROM todo_queue WHERE state='pending' ORDER BY position ASC LIMIT 1 OFFSET ?",
            new String[]{String.valueOf(offset)});
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        QueueTask qt = new QueueTask();
        qt.id = c.getInt(0);
        qt.position = c.getInt(1);
        qt.title = c.getString(2);
        qt.color = c.getString(3);
        c.close();
        return qt;
    }

    public void fillMissingRecurringTasks(String today) {
        SQLiteDatabase db = getWritableDatabase();
        List<RecurringTask> recurring = getRecurringTasks();
        LocalDate date = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE);
        int dayOfWeek = date.getDayOfWeek().getValue();

        Cursor c = db.rawQuery(
            "SELECT COALESCE(MAX(position), -1) FROM daily_tasks WHERE date=?",
            new String[]{today});
        int maxPos = -1;
        if (c.moveToFirst()) maxPos = c.getInt(0);
        c.close();

        for (RecurringTask rt : recurring) {
            if (RecurringTask.TYPE_QUEUE_SLOT.equals(rt.type)) continue;

            boolean applies = false;
            switch (rt.type) {
                case RecurringTask.TYPE_DAILY:
                    applies = true;
                    break;
                case RecurringTask.TYPE_DAYS_OF_WEEK:
                    if (rt.ruleData != null) {
                        for (String d : rt.ruleData.split(",")) {
                            if (d.trim().equals(String.valueOf(dayOfWeek))) { applies = true; break; }
                        }
                    }
                    break;
                case RecurringTask.TYPE_SPECIFIC_DATES:
                    if (rt.ruleData != null) {
                        for (String d : rt.ruleData.split(",")) {
                            if (d.trim().equals(today)) { applies = true; break; }
                        }
                    }
                    break;
            }
            if (!applies) continue;

            // Check if already generated for today
            Cursor existing = db.rawQuery(
                "SELECT COUNT(*) FROM daily_tasks WHERE date=? AND recurring_task_id=?",
                new String[]{today, String.valueOf(rt.id)});
            boolean alreadyExists = existing.moveToFirst() && existing.getInt(0) > 0;
            existing.close();
            if (alreadyExists) continue;

            insertDailyTask(db, today, ++maxPos, rt.title, rt.color, "recurring", 0, rt.id);
            Log.d(TAG, "Added missing recurring task: " + rt.title);
        }
    }

    public void fillMissingQueueSlots(String today) {
        SQLiteDatabase db = getWritableDatabase();

        // Get all queue slot recurring tasks in position order
        List<RecurringTask> allSlots = new ArrayList<>();
        Cursor c1 = db.rawQuery(
            "SELECT id FROM recurring_tasks WHERE type=? ORDER BY position ASC",
            new String[]{RecurringTask.TYPE_QUEUE_SLOT});
        while (c1.moveToNext()) {
            RecurringTask rt = new RecurringTask();
            rt.id = c1.getInt(0);
            allSlots.add(rt);
        }
        c1.close();
        if (allSlots.isEmpty()) return;

        // Find which recurring slot IDs are already filled for today
        Set<Integer> filledSlotIds = new HashSet<>();
        Cursor c2 = db.rawQuery(
            "SELECT recurring_task_id FROM daily_tasks WHERE date=? AND source='queue_slot'",
            new String[]{today});
        while (c2.moveToNext()) filledSlotIds.add(c2.getInt(0));
        c2.close();

        // Collect unfilled slots in order
        List<Integer> unfilledSlotIds = new ArrayList<>();
        for (RecurringTask rt : allSlots) {
            if (!filledSlotIds.contains(rt.id)) unfilledSlotIds.add(rt.id);
        }
        if (unfilledSlotIds.isEmpty()) return;

        Cursor c3 = db.rawQuery(
            "SELECT COALESCE(MAX(position), -1) FROM daily_tasks WHERE date=?",
            new String[]{today});
        int maxPos = -1;
        if (c3.moveToFirst()) maxPos = c3.getInt(0);
        c3.close();

        // Get pending queue items not already assigned to today
        Cursor c4 = db.rawQuery(
            "SELECT id, title, color FROM todo_queue WHERE state='pending'" +
            " AND id NOT IN (SELECT queue_item_id FROM daily_tasks WHERE date=? AND source='queue_slot' AND queue_item_id IS NOT NULL)" +
            " ORDER BY position ASC LIMIT ?",
            new String[]{today, String.valueOf(unfilledSlotIds.size())});
        int i = 0;
        while (c4.moveToNext() && i < unfilledSlotIds.size()) {
            int qid  = c4.getInt(0);
            String t = c4.getString(1);
            String co = c4.getString(2);
            insertDailyTask(db, today, ++maxPos, t, co, "queue_slot", qid, unfilledSlotIds.get(i++));
            Log.d(TAG, "Filled queue slot with: " + t);
        }
        c4.close();
    }

    public List<DailyTask> getDailyTasks(String date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT dt.id, dt.date, dt.position, dt.title, dt.color, dt.state, dt.source, dt.queue_item_id" +
            " FROM daily_tasks dt LEFT JOIN recurring_tasks rt ON dt.recurring_task_id = rt.id" +
            " WHERE dt.date=? ORDER BY COALESCE(rt.position, dt.position) ASC",
            new String[]{date});
        List<DailyTask> list = new ArrayList<>();
        while (c.moveToNext()) {
            DailyTask t = new DailyTask();
            t.id = c.getInt(0);
            t.date = c.getString(1);
            t.position = c.getInt(2);
            t.title = c.getString(3);
            t.color = c.getString(4);
            t.state = c.getString(5);
            t.source = c.isNull(6) ? "recurring" : c.getString(6);
            t.queueItemId = c.isNull(7) ? 0 : c.getInt(7);
            list.add(t);
        }
        c.close();
        return list;
    }

    public void setDailyTaskState(int id, String state) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("state", state);
        db.update("daily_tasks", cv, "id=?", new String[]{String.valueOf(id)});
        if (DailyTask.STATE_COMPLETED.equals(state)) {
            Cursor c = db.rawQuery(
                "SELECT source, queue_item_id FROM daily_tasks WHERE id=?",
                new String[]{String.valueOf(id)});
            if (c.moveToFirst() && "queue_slot".equals(c.getString(0)) && !c.isNull(1)) {
                markQueueItemDone(c.getInt(1));
            }
            c.close();
        }
    }

    public void markQueueItemDone(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("state", "done");
        db.update("todo_queue", cv, "id=?", new String[]{String.valueOf(id)});
    }

    /** Move task 2 positions down among PENDING tasks. */
    public void pushTaskDown(int taskId, String date) {
        List<DailyTask> tasks = getDailyTasks(date);
        List<DailyTask> pending = new ArrayList<>();
        for (DailyTask t : tasks) {
            if (DailyTask.STATE_PENDING.equals(t.state)) pending.add(t);
        }
        int idx = -1;
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).id == taskId) { idx = i; break; }
        }
        if (idx < 0 || idx + 2 >= pending.size()) {
            // Already near end — move to last pending position
            if (idx >= 0 && pending.size() > 1) {
                swapPositions(pending.get(idx), pending.get(pending.size() - 1));
            }
            return;
        }
        // Swap with 2 positions ahead
        swapPositions(pending.get(idx), pending.get(idx + 2));
    }

    private void swapPositions(DailyTask a, DailyTask b) {
        SQLiteDatabase db = getWritableDatabase();
        int posA = a.position;
        int posB = b.position;
        ContentValues cv = new ContentValues();
        cv.put("position", posB);
        db.update("daily_tasks", cv, "id=?", new String[]{String.valueOf(a.id)});
        cv.put("position", posA);
        db.update("daily_tasks", cv, "id=?", new String[]{String.valueOf(b.id)});
    }

    /** Promote a task to be the first pending task. */
    public void promoteTask(int taskId, String date) {
        List<DailyTask> tasks = getDailyTasks(date);
        List<DailyTask> pending = new ArrayList<>();
        for (DailyTask t : tasks) {
            if (DailyTask.STATE_PENDING.equals(t.state)) pending.add(t);
        }
        if (pending.size() < 2) return;
        DailyTask target = null;
        for (DailyTask t : pending) {
            if (t.id == taskId) { target = t; break; }
        }
        if (target == null) return;
        // Move target to first position by putting it before pending.get(0)
        int firstPos = pending.get(0).position;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("position", firstPos - 1);
        db.update("daily_tasks", cv, "id=?", new String[]{String.valueOf(taskId)});
    }

    /** Split a daily task into two. */
    public void splitDailyTask(int taskId, String date,
                               String title1, String color1,
                               String title2, String color2) {
        List<DailyTask> tasks = getDailyTasks(date);
        int targetPos = -1;
        for (DailyTask t : tasks) {
            if (t.id == taskId) { targetPos = t.position; break; }
        }
        if (targetPos < 0) return;

        SQLiteDatabase db = getWritableDatabase();
        // Shift all tasks at position > targetPos by 1 to make room
        db.execSQL("UPDATE daily_tasks SET position = position + 1 WHERE date=? AND position > ?",
            new Object[]{date, targetPos});
        // Delete original
        db.delete("daily_tasks", "id=?", new String[]{String.valueOf(taskId)});
        // Insert two replacements
        insertDailyTask(db, date, targetPos, title1, color1, "recurring", 0, 0);
        insertDailyTask(db, date, targetPos + 1, title2, color2, "recurring", 0, 0);
    }

    // ─── Debug ─────────────────────────────────────────────────────────────────

    public void resetToday(String today) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("daily_tasks", "date=?", new String[]{today});
        db.delete("action_log", "event_type='DAY_START' AND timestamp LIKE ?",
            new String[]{today + "%"});
        Log.d(TAG, "Debug: reset today " + today);
    }

    public String getDbStats() {
        SQLiteDatabase db = getReadableDatabase();
        String[] tables = {"recurring_tasks", "todo_queue", "daily_tasks", "action_log", "settings"};
        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
            int count = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            sb.append(table).append(": ").append(count).append("\n");
        }
        return sb.toString().trim();
    }

    // ─── Todo Queue ────────────────────────────────────────────────────────────

    public List<QueueTask> getQueueTasks() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT id, position, title, color FROM todo_queue WHERE state='pending' ORDER BY position ASC",
            null);
        List<QueueTask> list = new ArrayList<>();
        while (c.moveToNext()) {
            QueueTask t = new QueueTask();
            t.id = c.getInt(0);
            t.position = c.getInt(1);
            t.title = c.getString(2);
            t.color = c.getString(3);
            list.add(t);
        }
        c.close();
        return list;
    }

    public List<QueueTask> getDoneQueueTasks() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT id, position, title, color FROM todo_queue WHERE state='done' ORDER BY position ASC",
            null);
        List<QueueTask> list = new ArrayList<>();
        while (c.moveToNext()) {
            QueueTask t = new QueueTask();
            t.id = c.getInt(0);
            t.position = c.getInt(1);
            t.title = c.getString(2);
            t.color = c.getString(3);
            list.add(t);
        }
        c.close();
        return list;
    }

    public void addToQueue(String title, String color) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(position), -1) FROM todo_queue", null);
        int maxPos = -1;
        if (c.moveToFirst()) maxPos = c.getInt(0);
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("position", maxPos + 1);
        cv.put("title", title);
        cv.put("color", color);
        db.insert("todo_queue", null, cv);
    }

    public void deleteQueueTask(int id) {
        getWritableDatabase().delete("todo_queue", "id=?", new String[]{String.valueOf(id)});
    }

    public void splitQueueTask(int taskId, String title1, String color1, String title2, String color2) {
        List<QueueTask> tasks = getQueueTasks();
        int targetPos = -1;
        for (QueueTask t : tasks) {
            if (t.id == taskId) { targetPos = t.position; break; }
        }
        if (targetPos < 0) return;

        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE todo_queue SET position = position + 1 WHERE position > ?",
            new Object[]{targetPos});
        db.delete("todo_queue", "id=?", new String[]{String.valueOf(taskId)});

        ContentValues cv1 = new ContentValues();
        cv1.put("position", targetPos);
        cv1.put("title", title1);
        cv1.put("color", color1);
        db.insert("todo_queue", null, cv1);

        ContentValues cv2 = new ContentValues();
        cv2.put("position", targetPos + 1);
        cv2.put("title", title2);
        cv2.put("color", color2);
        db.insert("todo_queue", null, cv2);
    }

    public void moveQueueTaskUp(int id) {
        List<QueueTask> tasks = getQueueTasks();
        for (int i = 1; i < tasks.size(); i++) {
            if (tasks.get(i).id == id) {
                swapQueuePositions(tasks.get(i - 1).id, tasks.get(i - 1).position,
                                   tasks.get(i).id,     tasks.get(i).position);
                return;
            }
        }
    }

    public void moveQueueTaskDown(int id) {
        List<QueueTask> tasks = getQueueTasks();
        for (int i = 0; i < tasks.size() - 1; i++) {
            if (tasks.get(i).id == id) {
                swapQueuePositions(tasks.get(i).id,     tasks.get(i).position,
                                   tasks.get(i + 1).id, tasks.get(i + 1).position);
                return;
            }
        }
    }

    private void swapQueuePositions(int id1, int pos1, int id2, int pos2) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("position", pos2);
        db.update("todo_queue", cv, "id=?", new String[]{String.valueOf(id1)});
        cv.put("position", pos1);
        db.update("todo_queue", cv, "id=?", new String[]{String.valueOf(id2)});
    }

    // ─── Recurring Tasks ───────────────────────────────────────────────────────

    public List<RecurringTask> getRecurringTasks() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT id, position, title, color, type, rule_data FROM recurring_tasks ORDER BY position ASC",
            null);
        List<RecurringTask> list = new ArrayList<>();
        while (c.moveToNext()) {
            RecurringTask t = new RecurringTask();
            t.id = c.getInt(0);
            t.position = c.getInt(1);
            t.title = c.getString(2);
            t.color = c.getString(3);
            t.type = c.getString(4);
            t.ruleData = c.getString(5);
            list.add(t);
        }
        c.close();
        return list;
    }

    public void addRecurringTask(String title, String color, String type, String ruleData) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(position), -1) FROM recurring_tasks", null);
        int maxPos = -1;
        if (c.moveToFirst()) maxPos = c.getInt(0);
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("position", maxPos + 1);
        cv.put("title", title);
        cv.put("color", color);
        cv.put("type", type);
        cv.put("rule_data", ruleData);
        db.insert("recurring_tasks", null, cv);
    }

    public void updateRecurringTask(int id, String title, String color, String type, String ruleData) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("color", color);
        cv.put("type", type);
        cv.put("rule_data", ruleData);
        db.update("recurring_tasks", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteRecurringTask(int id) {
        getWritableDatabase().delete("recurring_tasks", "id=?", new String[]{String.valueOf(id)});
    }

    public void moveRecurringTaskUp(int id) {
        List<RecurringTask> tasks = getRecurringTasks();
        for (int i = 1; i < tasks.size(); i++) {
            if (tasks.get(i).id == id) {
                swapRecurringPositions(tasks.get(i - 1).id, tasks.get(i - 1).position,
                                       tasks.get(i).id,     tasks.get(i).position);
                return;
            }
        }
    }

    public void moveRecurringTaskDown(int id) {
        List<RecurringTask> tasks = getRecurringTasks();
        for (int i = 0; i < tasks.size() - 1; i++) {
            if (tasks.get(i).id == id) {
                swapRecurringPositions(tasks.get(i).id,     tasks.get(i).position,
                                       tasks.get(i + 1).id, tasks.get(i + 1).position);
                return;
            }
        }
    }

    private void swapRecurringPositions(int id1, int pos1, int id2, int pos2) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("position", pos2);
        db.update("recurring_tasks", cv, "id=?", new String[]{String.valueOf(id1)});
        cv.put("position", pos1);
        db.update("recurring_tasks", cv, "id=?", new String[]{String.valueOf(id2)});
    }
}
