package it.lo.exp.nulladies;

public class DailyTask {
    public int id;
    public String date;
    public int position;
    public String title;
    public String color;
    public String state; // PENDING, COMPLETED, SKIPPED

    public String source = "recurring";
    public int queueItemId = 0;

    public static final String STATE_PENDING   = "PENDING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_SKIPPED   = "SKIPPED";
}
