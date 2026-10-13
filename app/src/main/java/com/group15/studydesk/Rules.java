package com.group15.studydesk;

import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.json.*;

/** Ports of ActionsView, deadlines, dueAt, timetable, commitments, GPA and merge rules. */
public final class Rules {
  public static List<Row> kind(List<Row> rows, String kind) {
    List<Row> out = new ArrayList<>();
    for (Row r : rows) if (r.live() && r.kind.equals(kind)) out.add(r);
    return out;
  }

  public static Row byId(List<Row> rows, String kind, String id) {
    for (Row r : rows) if (r.live() && r.kind.equals(kind) && r.id.equals(id)) return r;
    return null;
  }

  public static long days(String date, LocalDate today) {
    return ChronoUnit.DAYS.between(today, LocalDate.parse(date));
  }

  public static long horizon(String type, String window) {
    if (window.equals("all")) return Long.MAX_VALUE;
    if (window.equals("smart")) return type.equals("Reading") ? 3 : 14;
    try {
      return Long.parseLong(window);
    } catch (Exception e) {
      return 14;
    }
  }

  public static String bucket(long d) {
    return d <= 3 ? "today" : d <= 7 ? "this_week" : "later";
  }

  public static LocalDate studyStart(Row e) {
    int n =
        switch (e.s("difficulty")) {
          case "easy" -> 3;
          case "hard" -> 14;
          case "brutal" -> 21;
          default -> 7;
        };
    return LocalDate.parse(e.s("dueDate")).minusDays(n);
  }

  public static boolean manualVisible(Row r, ZonedDateTime now) {
    if (!r.b("done")) return true;
    long done = (long) r.n("doneAt", 0);
    if (done == 0) return false;
    return now.toInstant()
        .isBefore(
            Instant.ofEpochMilli(done)
                .atZone(now.getZone())
                .toLocalDate()
                .plusDays(1)
                .atTime(3, 0)
                .atZone(now.getZone())
                .toInstant());
  }

  public static List<Row> nextUp(List<Row> rows, LocalDate today) {
    List<Row> result = new ArrayList<>();
    boolean active = false;
    for (Row c : kind(rows, "course")) if (c.s("archivedAt").isEmpty()) active = true;
    if (!active) return result;
    int total = 0;
    for (Row r : rows)
      if (r.live()
          && (r.kind.equals("assignment") || r.kind.equals("exam"))
          && !r.b("done")
          && !r.s("dueDate").isEmpty()) total++;
    if (total == 0) return result;
    for (Row r : kind(rows, "action"))
      if (manualVisible(
          r,
          today.equals(LocalDate.now())
              ? ZonedDateTime.now()
              : today.atTime(12, 0).atZone(ZoneId.systemDefault()))) result.add(r);
    for (String k : new String[] {"assignment", "exam"}) {
      List<Row> eligible = new ArrayList<>();
      for (Row r : kind(rows, k))
        if (!r.b("done") && !r.s("dueDate").isEmpty() && days(r.s("dueDate"), today) >= 0)
          eligible.add(r);
      eligible.sort(Comparator.comparing(r -> r.s("dueDate")));
      for (Row r : eligible.subList(0, Math.min(3, eligible.size()))) {
        Row copy = r.copy();
        copy.put("bucket", bucket(days(r.s("dueDate"), today)));
        result.add(copy);
      }
    }
    return result;
  }

  public static Instant dueAt(Row a, ZoneId zone) {
    String date = a.s("dueDate");
    if (date.isEmpty()) return null;
    LocalTime t = a.s("dueTime").isEmpty() ? LocalTime.of(23, 59) : LocalTime.parse(a.s("dueTime"));
    return LocalDate.parse(date).atTime(t).atZone(zone).toInstant();
  }

  public static Instant dueReminder(Row a, ZoneId zone) {
    LocalDate date = LocalDate.parse(a.s("dueDate"));
    LocalTime time =
        a.s("dueTime").isEmpty()
            ? LocalTime.of(9, 0)
            : LocalTime.parse(a.s("dueTime")).minusHours(1);
    if (!a.s("dueTime").isEmpty() && LocalTime.parse(a.s("dueTime")).isBefore(LocalTime.of(1, 0)))
      return null;
    if (time.isAfter(LocalTime.of(9, 0))) time = LocalTime.of(9, 0);
    return date.atTime(time).atZone(zone).toInstant();
  }

  public static long timerSeconds(long anchor, int seconds, long now, boolean stopwatch) {
    long elapsed = Math.max(0, (now - anchor) / 1000);
    return stopwatch ? seconds + elapsed : Math.max(0, seconds - elapsed);
  }

  public static String clock(long s) {
    s = Math.max(0, s);
    return s >= 3600
        ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
  }

  public static boolean remoteWins(Row local, Row remote) {
    if (local == null) return true;
    if (remote.deletedAt != null && local.deletedAt == null) return true;
    try {
      return Instant.parse(remote.updatedAt).isAfter(Instant.parse(local.updatedAt));
    } catch (Exception e) {
      return local.updatedAt.isEmpty();
    }
  }

  public static LocalDate[] termRange(List<Row> rows, String id) {
    LocalDate start = null, end = null;
    Set<String> seen = new HashSet<>();
    while (!id.isEmpty() && seen.add(id)) {
      Row term = byId(rows, "term", id);
      if (term == null) break;
      if (start == null && !term.s("startsOn").isEmpty())
        start = LocalDate.parse(term.s("startsOn"));
      if (end == null && !term.s("endsOn").isEmpty()) end = LocalDate.parse(term.s("endsOn"));
      id = term.s("parentId");
    }
    return new LocalDate[] {start, end};
  }

  public static boolean lessonOn(Row r, List<Row> rows, LocalDate day) {
    if ((int) r.n("weekday", -1) != day.getDayOfWeek().getValue() % 7) return false;
    LocalDate[] range = termRange(rows, r.s("termId"));
    if (range[0] == null) return false;
    if (range[0] != null && day.isBefore(range[0]) || range[1] != null && day.isAfter(range[1]))
      return false;
    int parity = (int) r.n("weekParity", 0);
    if (parity != 0 && range[0] != null) {
      LocalDate monday = range[0].with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      long week =
          ChronoUnit.WEEKS.between(
                  monday, day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
              + 1;
      if ((week % 2 == 1 ? 1 : 2) != parity) return false;
    }
    return true;
  }

  public static boolean commitmentOn(Row r, LocalDate day) {
    String start = r.s("startsOn"), end = r.s("endsOn");
    if (start.isEmpty()) return false;
    LocalDate first = LocalDate.parse(start);
    if (day.isBefore(first) || !end.isEmpty() && day.isAfter(LocalDate.parse(end))) return false;
    String weekday = r.s("weekday");
    if (weekday.isEmpty()) return day.equals(first);
    if (Integer.parseInt(weekday) != day.getDayOfWeek().getValue() % 7) return false;
    int interval = (int) r.n("intervalWeeks", 1);
    return interval <= 1
        || ChronoUnit.WEEKS.between(
                    first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                % interval
            == 0;
  }

  public static java.time.LocalDate repeatHorizon(java.time.LocalDate start, List<Row> rows) {
    java.time.LocalDate end = null;
    int best = -1;
    for (Row term : kind(rows, "term")) {
      java.time.LocalDate[] range = termRange(rows, term.id);
      if (range[0] == null
          || range[1] == null
          || start.isBefore(range[0])
          || start.isAfter(range[1])) continue;
      int depth = 0;
      Row t = term;
      while (t != null && !t.s("parentId").isEmpty() && depth < 8) {
        depth++;
        t = byId(rows, "term", t.s("parentId"));
      }
      if (depth > best) {
        best = depth;
        end = range[1];
      }
    }
    return end == null ? start.plusWeeks(12) : end;
  }

  public static List<java.time.LocalDate> repeatDates(
      java.time.LocalDate start, int interval, java.time.LocalDate end) {
    List<java.time.LocalDate> dates = new ArrayList<>();
    dates.add(start);
    if (interval < 1) return dates;
    while (dates.size() < 60) {
      java.time.LocalDate next = dates.get(dates.size() - 1).plusWeeks(interval);
      if (end != null && next.isAfter(end)) break;
      dates.add(next);
    }
    return dates;
  }

  public static double effectiveGrade(List<Row> grades) {
    if (grades.isEmpty()) return Double.NaN;
    double sum = 0, w = 0;
    for (Row g : grades) {
      double weight = g.n("weight", 1);
      sum += g.n("grade", 0) * weight;
      w += weight;
    }
    if (w == 0) {
      for (Row g : grades) sum += g.n("grade", 0);
      return sum / grades.size();
    }
    return sum / w;
  }

  public static double points(double p) {
    double[] limits = {93, 90, 87, 83, 80, 77, 73, 70, 67, 60};
    double[] values = {4, 3.7, 3.3, 3, 2.7, 2.3, 2, 1.7, 1.3, 1};
    for (int i = 0; i < limits.length; i++) if (p >= limits[i]) return values[i];
    return 0;
  }

  public static double gpa(List<Row> rows, String mode, boolean archived) {
    double sum = 0, w = 0;
    for (Row c : kind(rows, "course")) {
      if (c.s("archivedAt").isEmpty() == archived) continue;
      List<Row> own = new ArrayList<>();
      for (Row g : kind(rows, "grade")) if (g.course().equals(c.id)) own.add(g);
      double value = effectiveGrade(own);
      if (Double.isNaN(value)) continue;
      double credits = c.n("credits", 1);
      if (credits == 0) credits = 1;
      sum += (mode.equals("us") ? points(value) : value) * credits;
      w += credits;
    }
    return w == 0 ? 0 : Math.round(sum / w * 100) / 100.0;
  }

  public static String courseName(List<Row> rows, Row r) {
    Row c = byId(rows, "course", r.course());
    return c == null ? "" : c.title();
  }

  public static List<int[]> freeWindows(List<int[]> blocked, int start, int end) {
    blocked.sort(Comparator.comparingInt(x -> x[0]));
    List<int[]> free = new ArrayList<>();
    int cursor = start;
    for (int[] b : blocked) {
      if (b[0] > cursor) free.add(new int[] {cursor, Math.min(end, b[0])});
      cursor = Math.max(cursor, b[1]);
      if (cursor >= end) break;
    }
    if (cursor < end) free.add(new int[] {cursor, end});
    free.removeIf(a -> a[1] <= a[0]);
    return free;
  }

  public static int minutes(String t) {
    return LocalTime.parse(t).getHour() * 60 + LocalTime.parse(t).getMinute();
  }
}
