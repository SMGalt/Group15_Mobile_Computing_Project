package com.group15.studydesk;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.*;
import androidx.core.content.ContextCompat;
import java.time.*;
import java.util.*;

public final class Reminders {
  public static void channels(Context c) {
    NotificationManager n = c.getSystemService(NotificationManager.class);
    n.createNotificationChannel(
        new NotificationChannel(
            "deadlines", "Study reminders", NotificationManager.IMPORTANCE_DEFAULT));
    n.createNotificationChannel(
        new NotificationChannel("focus", "Focus timer", NotificationManager.IMPORTANCE_LOW));
  }

  public static boolean allowed(Context c) {
    return (Build.VERSION.SDK_INT < 33
            || ContextCompat.checkSelfPermission(c, "android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED)
        && NotificationManagerCompat.from(c).areNotificationsEnabled();
  }

  private static PendingIntent intent(Context c, int id, Intent i) {
    return PendingIntent.getBroadcast(
        c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static synchronized void rebuild(Context c, List<Row> rows) {
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    SharedPreferences p = c.getSharedPreferences("alarms", 0);
    for (String id : p.getStringSet("ids", new HashSet<>()))
      alarms.cancel(
          intent(
              c,
              Integer.parseInt(id),
              new Intent(c, ReminderReceiver.class).setAction("reminder")));
    Set<String> ids = new HashSet<>();
    if (!c.getSharedPreferences("settings", 0).getBoolean("notifications", true) || !allowed(c)) {
      p.edit().putStringSet("ids", ids).commit();
      return;
    }
    ZoneId zone = ZoneId.systemDefault();
    Instant now = Instant.now();
    int id = 1000;
    List<Row> due = new ArrayList<>();
    for (Row r : rows)
      if (r.live()
          && (r.kind.equals("assignment") || r.kind.equals("exam"))
          && !r.b("done")
          && !r.s("dueDate").isEmpty()) due.add(r);
    due.sort(Comparator.comparing(r -> r.s("dueDate")));
    if (!due.isEmpty())
      for (int d = 0; d < 30; d++) {
        Instant at = LocalDate.now().plusDays(d).atTime(9, 0).atZone(zone).toInstant();
        if (at.isAfter(now)) {
          schedule(c, id, at, "StudyDesk · Next Up", due.get(0).title(), due.get(0), "Next Up");
          ids.add(String.valueOf(id++));
        }
      }
    for (Row r : due) {
      LocalDate date = LocalDate.parse(r.s("dueDate"));
      if (r.kind.equals("exam")) {
        Instant[] times = {
          Rules.studyStart(r).atTime(9, 0).atZone(zone).toInstant(),
          date.minusDays(2).atTime(9, 0).atZone(zone).toInstant(),
          date.atTime(7, 0).atZone(zone).toInstant()
        };
        String[] titles = {"Time to start studying", "Exam in 2 days", "Exam today — good luck"};
        for (int i = 0; i < times.length; i++)
          if (times[i].isAfter(now)) {
            schedule(c, id, times[i], titles[i], r.title(), r, "Plan");
            ids.add(String.valueOf(id++));
          }
      } else {
        Instant[] times = {
          date.minusDays(1).atTime(18, 0).atZone(zone).toInstant(), Rules.dueReminder(r, zone)
        };
        String[] titles = {"Due tomorrow", "Due today"};
        for (int i = 0; i < times.length; i++)
          if (times[i] != null && times[i].isAfter(now)) {
            schedule(c, id, times[i], titles[i], r.title() + " " + r.s("dueTime"), r, "Plan");
            ids.add(String.valueOf(id++));
          }
      }
    }
    List<Row> plans = Rules.kind(rows, "planned");
    plans.sort(Comparator.comparing(r -> r.s("startsAt")));
    int count = 0,
        lead = Integer.parseInt(c.getSharedPreferences("settings", 0).getString("planLead", "30"));
    boolean atStart = c.getSharedPreferences("settings", 0).getBoolean("planStart", true);
    for (Row r : plans) {
      if (count >= 60) break;
      if (!r.s("fulfilledBy").isEmpty() || !r.s("dismissedAt").isEmpty()) continue;
      Instant at = Times.instant(r.s("startsAt"));
      if (!at.isAfter(now) || at.isAfter(now.plusSeconds(30 * 86400))) continue;
      Instant before = at.minusSeconds(lead * 60L);
      if (lead > 0 && before.isAfter(now)) {
        schedule(
            c, id, before, "Study starts in " + lead + " minutes", r.title(), r, "Study plans");
        ids.add(String.valueOf(id++));
        count++;
      }
      if (atStart && count < 60) {
        schedule(c, id, at, "Time to study", r.title(), r, "Study plans");
        ids.add(String.valueOf(id++));
        count++;
      }
    }
    p.edit().putStringSet("ids", ids).commit();
  }

  private static void schedule(
      Context c, int id, Instant at, String title, String body, Row row, String screen) {
    Intent i =
        new Intent(c, ReminderReceiver.class)
            .setAction("reminder")
            .putExtra("notificationId", id)
            .putExtra("title", title)
            .putExtra("body", body)
            .putExtra("kind", row.kind)
            .putExtra("row", row.id)
            .putExtra("owner", new Repository(c).owner())
            .putExtra("screen", screen);
    set(c, at.toEpochMilli(), intent(c, id, i));
  }

  static void set(Context c, long at, PendingIntent p) {
    AlarmManager m = c.getSystemService(AlarmManager.class);
    if (Build.VERSION.SDK_INT < 31 || m.canScheduleExactAlarms())
      m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
    else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
  }

  public static void timer(Context c) {
    TimerState t = new TimerState(c);
    PendingIntent p = intent(c, 999, new Intent(c, ReminderReceiver.class).setAction("timer"));
    c.getSystemService(AlarmManager.class).cancel(p);
    NotificationManager n = c.getSystemService(NotificationManager.class);
    if (!t.running()) {
      n.cancel(998);
      return;
    }
    if (!t.stopwatch()) set(c, System.currentTimeMillis() + t.secs() * 1000, p);
    if (!allowed(c) || !c.getSharedPreferences("settings", 0).getBoolean("focusChip", true)) return;
    Intent open = new Intent(c, MainActivity.class).putExtra("screen", "Timer");
    PendingIntent tap =
        PendingIntent.getActivity(
            c, 998, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder b =
        new NotificationCompat.Builder(c, "focus")
            .setSmallIcon(R.drawable.ic_book)
            .setContentTitle("StudyDesk · " + t.phase())
            .setContentText(t.p.getString("task", ""))
            .setOngoing(true)
            .setContentIntent(tap)
            .setUsesChronometer(true)
            .setChronometerCountDown(!t.stopwatch())
            .setWhen(
                t.stopwatch()
                    ? System.currentTimeMillis() - t.secs() * 1000
                    : System.currentTimeMillis() + t.secs() * 1000)
            .setSilent(true);
    n.notify(998, b.build());
  }
}
