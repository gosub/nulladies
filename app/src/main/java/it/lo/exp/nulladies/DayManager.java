package it.lo.exp.nulladies;

import android.util.Log;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DayManager {

    private static final String TAG = "NullaDies";
    private final DatabaseHelper db;

    public DayManager(DatabaseHelper db) {
        this.db = db;
    }

    /** Call on every app resume. Rolls over if needed. */
    public void checkAndRolloverIfNeeded() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        String rolloverTimeStr = db.getSetting("rollover_time", "00:00");
        LocalTime rolloverTime;
        try {
            rolloverTime = LocalTime.parse(rolloverTimeStr);
        } catch (Exception e) {
            rolloverTime = LocalTime.MIDNIGHT;
        }

        boolean pastRollover = !LocalTime.now().isBefore(rolloverTime);
        if (!pastRollover) return;

        if (db.needsRollover(today)) {
            Log.d(TAG, "Rolling over day: " + today);
            db.generateDailyTasks(today);
            db.logAction("DAY_START", null, today);
        } else {
            db.fillMissingRecurringTasks(today);
            db.fillMissingQueueSlots(today);
        }
    }
}
