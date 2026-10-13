package com.group15.studydesk;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.time.*;
import java.util.*;

public class DeskWidget extends AppWidgetProvider {
  protected boolean upcoming() {
    return false;
  }

  public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    Repository.IO.execute(() -> update(c, m, ids, upcoming()));
  }

  static void update(Context c, AppWidgetManager m, int[] ids, boolean upcoming) {
    List<Row> rows = new Repository(c).all();
    List<Row> items = upcoming ? new ArrayList<>() : Rules.nextUp(rows, LocalDate.now());
    if (upcoming) {
      for (Row r : rows)
        if (r.live()
            && (r.kind.equals("assignment") || r.kind.equals("exam"))
            && !r.b("done")
            && !r.s("dueDate").isEmpty()) items.add(r);
      items.sort(Comparator.comparing(r -> r.s("dueDate")));
    }
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < Math.min(5, items.size()); i++) {
      Row r = items.get(i);
      text.append(r.b("done") ? "✓ " : "○ ")
          .append(r.title())
          .append(upcoming ? " · " + r.s("dueDate") : "")
          .append("\n");
    }
    if (items.isEmpty()) text.append("Add a course and a deadline to get started.");
    String palette = c.getSharedPreferences("settings", 0).getString("widgetPalette", "auto");
    if (palette.equals("auto"))
      palette = c.getSharedPreferences("settings", 0).getString("mode", "light");
    if (palette.equals("light")
        && Appearance.character(c).equals("slate")
        && c.getSharedPreferences("settings", 0).getString("widgetPalette", "auto").equals("auto"))
      palette = "dark";
    boolean dark = !palette.equals("light");
    for (int id : ids) {
      RemoteViews view = new RemoteViews(c.getPackageName(), R.layout.widget);
      view.setTextViewText(R.id.widgetTitle, "StudyDesk · " + (upcoming ? "Upcoming" : "Next Up"));
      view.setTextViewText(R.id.widgetBody, text.toString());
      view.setInt(
          R.id.widgetRoot,
          "setBackgroundColor",
          android.graphics.Color.parseColor(
              dark ? (palette.equals("black") ? "#000000" : "#24211c") : "#F5F2ED"));
      view.setTextColor(
          R.id.widgetBody, android.graphics.Color.parseColor(dark ? "#F0E9DD" : "#302A28"));
      view.setTextColor(
          R.id.widgetTitle, android.graphics.Color.parseColor(dark ? "#D9A0B6" : "#8B4A62"));
      Intent open =
          new Intent(c, MainActivity.class).putExtra("screen", upcoming ? "Plan" : "Next Up");
      view.setOnClickPendingIntent(
          R.id.widgetRoot,
          PendingIntent.getActivity(
              c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
      m.updateAppWidget(id, view);
    }
  }

  public static void refresh(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    update(c, m, m.getAppWidgetIds(new ComponentName(c, DeskWidget.class)), false);
    update(c, m, m.getAppWidgetIds(new ComponentName(c, UpcomingWidget.class)), true);
  }
}
