package it.lo.exp.nulladies;

public class RecurringTask {
    public int id;
    public int position;
    public String title;    // null for QUEUE_SLOT
    public String color;    // null for QUEUE_SLOT
    public String type;     // DAILY, DAYS_OF_WEEK, SPECIFIC_DATES, QUEUE_SLOT
    public String ruleData; // null for DAILY/QUEUE_SLOT; "1,3,5" for DAYS_OF_WEEK; "2026-01-15" for SPECIFIC_DATES

    public static final String TYPE_DAILY          = "DAILY";
    public static final String TYPE_DAYS_OF_WEEK   = "DAYS_OF_WEEK";
    public static final String TYPE_SPECIFIC_DATES = "SPECIFIC_DATES";
    public static final String TYPE_QUEUE_SLOT     = "QUEUE_SLOT";

    public String describeType() {
        switch (type) {
            case TYPE_DAILY:          return "Daily";
            case TYPE_DAYS_OF_WEEK:   return "Days: " + ruleData;
            case TYPE_SPECIFIC_DATES: return "Dates: " + ruleData;
            case TYPE_QUEUE_SLOT:     return "Queue Slot";
            default:                  return type;
        }
    }
}
