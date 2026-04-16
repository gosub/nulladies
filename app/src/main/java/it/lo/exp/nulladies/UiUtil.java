package it.lo.exp.nulladies;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;

public class UiUtil {

    public static void buildColorPicker(LinearLayout container, TaskColor[] selectedHolder,
                                        TaskColor initial) {
        container.removeAllViews();
        float density = container.getContext().getResources().getDisplayMetrics().density;
        int sizePx   = (int)(36 * density);
        int marginPx = (int)(3 * density);

        TaskColor[] colors = TaskColor.values();
        View[] views = new View[colors.length];

        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            final TaskColor color = colors[i];
            View v = new View(container.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            v.setLayoutParams(lp);
            views[i] = v;
            applyColorSwatch(v, color, color == initial, density);
            v.setOnClickListener(click -> {
                selectedHolder[0] = color;
                for (int j = 0; j < colors.length; j++) {
                    applyColorSwatch(views[j], colors[j], j == idx, density);
                }
            });
            container.addView(v);
        }
    }

    public static void applyColorSwatch(View v, TaskColor color, boolean selected, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color.toArgb());
        if (selected) {
            d.setStroke((int)(3 * density), 0xFF000000);
        }
        v.setBackground(d);
    }

    public static void applyCircleColor(View v, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
    }

    public static void exportOrg(Context context, DatabaseHelper db) {
        new OrgExporter(context, db).exportAsync();
    }
}
