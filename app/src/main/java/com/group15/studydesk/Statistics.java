package com.group15.studydesk;

import android.content.SharedPreferences;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.json.*;

public final class Statistics {
  private static CardAdapter.Card card(String tag, String title, String detail, double[] chart) {
    return new CardAdapter.Card(null, tag, title, detail, "", null, chart);
  }

  public static List<CardAdapter.Card> cards(
      List<Row> rows, SharedPreferences p, boolean analytics) {
    List<CardAdapter.Card> out = new ArrayList<>();
    List<Row> sessions = Rules.kind(rows, "session");
    String mode = p.getString("gradeMode", "ib");
    int dayStart = Integer.parseInt(p.getString("dayStart", "0"));
    LocalDate today = LocalDate.now(),
        monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    double[] weekly = new double[5];
    Map<String, Double> split = new LinkedHashMap<>();
    Set<LocalDate> studyDays = new HashSet<>();
    Map<LocalDate, Double> minutes = new HashMap<>();
    for (Row s : sessions) {
      if (s.s("startedAt").isEmpty()) continue;
      LocalDate date = Times.local(s.s("startedAt")).toLocalDate();
      LocalDate studyDay = Times.studyDay(s.s("startedAt"), dayStart);
      studyDays.add(studyDay);
      double n = s.n("durationMinutes", 0);
      minutes.merge(date, n, Double::sum);
      for (int i = 0; i < 5; i++) {
        LocalDate start = monday.minusWeeks(4 - i);
        if (!date.isBefore(start) && date.isBefore(start.plusWeeks(1))) weekly[i] += n;
      }
      if (!date.isBefore(today.minusDays(29))) split.merge(s.course(), n, Double::sum);
    }
    double average = (weekly[0] + weekly[1] + weekly[2] + weekly[3]) / 4;
    out.add(
        card(
            "THIS WEEK",
            String.format(Locale.US, "%.1f hours", weekly[4] / 60),
            "Prior 4-week average: "
                + String.format(Locale.US, "%.1f hours", average / 60)
                + (average > 0
                    ? String.format(Locale.US, " · %+.0f%%", 100 * (weekly[4] - average) / average)
                    : ""),
            weekly));
    LocalDate current = Times.studyDay(LocalDateTime.now().toString(), dayStart);
    LocalDate cursor = studyDays.contains(current) ? current : current.minusDays(1);
    int streak = 0;
    while (studyDays.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    double[] last14 = new double[14];
    for (int i = 0; i < 14; i++) last14[i] = studyDays.contains(current.minusDays(13 - i)) ? 1 : 0;
    out.add(
        card("STUDY STREAK", streak + " days", "Study day starts at " + dayStart + ":00", last14));
    List<Map.Entry<String, Double>> shares = new ArrayList<>(split.entrySet());
    shares.sort(Map.Entry.<String, Double>comparingByValue().reversed());
    double total = shares.stream().mapToDouble(Map.Entry::getValue).sum();
    for (Map.Entry<String, Double> e : shares) {
      Row c = Rules.byId(rows, "course", e.getKey());
      out.add(
          card(
              "LAST 30 DAYS",
              c == null ? "General study" : c.title(),
              String.format(
                  Locale.US,
                  "%.1f hours · %.0f%%",
                  e.getValue() / 60,
                  total == 0 ? 0 : 100 * e.getValue() / total),
              new double[] {e.getValue(), Math.max(0, total - e.getValue())}));
    }
    TreeSet<String> months = new TreeSet<>();
    for (Row g : Rules.kind(rows, "grade"))
      if (!g.s("date").isEmpty()) months.add(g.s("date").substring(0, 7));
    List<Double> trend = new ArrayList<>();
    StringBuilder trendText = new StringBuilder();
    for (String month : months) {
      List<Row> snapshot = new ArrayList<>();
      for (Row r : rows) {
        if (r.kind.equals("grade")
            && !r.s("date").isEmpty()
            && r.s("date").substring(0, 7).compareTo(month) > 0) continue;
        Row copy = r.copy();
        if (copy.kind.equals("course")) copy.put("archivedAt", null);
        snapshot.add(copy);
      }
      double value = Rules.gpa(snapshot, mode, false);
      if (value > 0) {
        trend.add(value);
        trendText.append(month).append(" · ").append(value).append("\n");
      }
    }
    out.add(
        card(
            "GPA TREND",
            String.format(Locale.US, "%.2f", Rules.gpa(rows, mode, false)),
            trendText.length() == 0 ? "Log grades to see your trend." : trendText.toString(),
            trend.stream().mapToDouble(Double::doubleValue).toArray()));
    List<Double> comprehension = new ArrayList<>();
    Map<String, Integer> flags = new HashMap<>();
    for (Row s : sessions) {
      if (Times.instant(s.s("startedAt")).isBefore(Instant.now().minusSeconds(7 * 86400))) continue;
      if (s.n("aiComprehension", 0) > 0) comprehension.add(s.n("aiComprehension", 0));
      JSONArray a = s.json().optJSONArray("aiConfusionFlags");
      Set<String> own = new HashSet<>();
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          String flag = a.optString(i).trim().toLowerCase(Locale.ROOT);
          if (!flag.isEmpty()) own.add(flag);
        }
      for (String flag : own) flags.merge(flag, 1, Integer::sum);
    }
    if (comprehension.size() >= 2 || flags.values().stream().anyMatch(n -> n >= 2)) {
      StringBuilder text = new StringBuilder();
      flags.entrySet().stream()
          .filter(e -> e.getValue() >= 2)
          .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .forEach(
              e ->
                  text.append(e.getKey()).append(" · ").append(e.getValue()).append(" sessions\n"));
      out.add(
          card(
              "DEBRIEF INSIGHTS",
              comprehension.size() >= 2
                  ? String.format(
                      Locale.US,
                      "%.1f / 5 comprehension",
                      comprehension.stream().mapToDouble(Double::doubleValue).average().orElse(0))
                  : "Recurring confusion",
              text.toString(),
              null));
    }
    if (analytics) {
      LocalDate start =
          today
              .minusDays(181)
              .with(
                  TemporalAdjusters.previousOrSame(
                      p.getString("weekStart", "1").equals("0")
                          ? DayOfWeek.SUNDAY
                          : DayOfWeek.MONDAY));
      double[] heat =
          new double[(int) java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1];
      for (int i = 0; i < heat.length; i++) heat[i] = minutes.getOrDefault(start.plusDays(i), 0.0);
      out.add(
          card(
              "STUDY HEATMAP",
              start + " — " + today,
              "Each square is one day; zero-study days stay visible.",
              heat));
      for (Row c : Rules.kind(rows, "course")) {
        List<Row> grades = new ArrayList<>();
        for (Row g : Rules.kind(rows, "grade"))
          if (g.course().equals(c.id) && !g.s("date").isEmpty()) grades.add(g);
        grades.sort(Comparator.comparing(g -> g.s("date")));
        if (grades.isEmpty()) continue;
        List<Row> running = new ArrayList<>();
        double[] means = new double[grades.size()],
            xs = new double[grades.size()],
            ys = new double[grades.size()];
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < grades.size(); i++) {
          Row g = grades.get(i);
          running.add(g);
          means[i] = weightedMean(running);
          LocalDate date = LocalDate.parse(g.s("date"));
          double studied = 0;
          for (Row s : sessions)
            if (s.course().equals(c.id)) {
              LocalDate d = Times.local(s.s("startedAt")).toLocalDate();
              if (!d.isBefore(date.minusDays(21)) && !d.isAfter(date))
                studied += s.n("durationMinutes", 0);
            }
          xs[i] = studied;
          ys[i] = g.n("grade", 0);
          text.append(g.s("date"))
              .append(" · grade ")
              .append(ys[i])
              .append(" · mean ")
              .append(String.format(Locale.US, "%.2f", means[i]))
              .append(" · ")
              .append(studied)
              .append(" minutes in prior 21 days\n");
        }
        Double r = pearson(xs, ys);
        text.append("Study / grade correlation: ")
            .append(
                r == null
                    ? "Insufficient data or zero variance"
                    : String.format(Locale.US, "r = %.2f", r))
            .append("\nCorrelation does not establish causation.");
        out.add(card("GRADE PROGRESSION", c.title(), text.toString(), means));
      }
    }
    return out;
  }

  public static double weightedMean(List<Row> rows) {
    double sum = 0, w = 0;
    for (Row r : rows) {
      double weight = r.n("weight", 0);
      if (weight > 0) {
        sum += r.n("grade", 0) * weight;
        w += weight;
      }
    }
    return w == 0 ? Double.NaN : sum / w;
  }

  public static Double pearson(double[] x, double[] y) {
    int n = Math.min(x.length, y.length);
    if (n < 3) return null;
    double mx = 0, my = 0;
    for (int i = 0; i < n; i++) {
      mx += x[i];
      my += y[i];
    }
    mx /= n;
    my /= n;
    double numerator = 0, dx = 0, dy = 0;
    for (int i = 0; i < n; i++) {
      double a = x[i] - mx, b = y[i] - my;
      numerator += a * b;
      dx += a * a;
      dy += b * b;
    }
    return dx == 0 || dy == 0 ? null : numerator / Math.sqrt(dx * dy);
  }
}
