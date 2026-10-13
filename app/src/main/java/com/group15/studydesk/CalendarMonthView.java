package com.group15.studydesk;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.*;
import java.time.format.TextStyle;
import java.util.*;

/** Native month cells with actual dates, selection and event indicators; no web content. */
public class CalendarMonthView extends LinearLayout {
  public interface OnDateChangeListener {
    void changed(CalendarMonthView view, int year, int month, int day);
  }

  LocalDate selected = LocalDate.now();
  YearMonth shown = YearMonth.from(selected);
  int firstDay = java.util.Calendar.MONDAY;
  List<Row> records = Collections.emptyList();
  OnDateChangeListener listener;

  public CalendarMonthView(Context c) {
    this(c, null);
  }

  public CalendarMonthView(Context c, AttributeSet a) {
    super(c, a);
    setOrientation(VERTICAL);
    setPadding(dp(16), 0, dp(16), 0);
    drawMonth();
  }

  int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  public void setOnDateChangeListener(OnDateChangeListener l) {
    listener = l;
  }

  public long getDate() {
    return selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  public long getMinDate() {
    return LocalDate.of(1600, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  public long getMaxDate() {
    return LocalDate.of(2500, 12, 31)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();
  }

  public void setDate(long millis) {
    setDate(millis, false, true);
  }

  public void setDate(long millis, boolean animate, boolean center) {
    selected = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    shown = YearMonth.from(selected);
    drawMonth();
  }

  public void setFirstDayOfWeek(int value) {
    if (firstDay != value) {
      firstDay = value;
      drawMonth();
    }
  }

  public void setRecords(List<Row> value) {
    records = value;
    drawMonth();
  }

  Locale locale() {
    return getResources().getConfiguration().getLocales().get(0);
  }

  TextView text(String label, int size) {
    TextView t = new TextView(getContext());
    t.setText(label);
    t.setTextSize(size);
    t.setTextColor(Appearance.color(getContext(), R.attr.deskInk));
    t.setGravity(Gravity.CENTER);
    return t;
  }

  void choose(LocalDate date) {
    selected = date;
    shown = YearMonth.from(date);
    drawMonth();
    if (listener != null)
      listener.changed(this, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
  }

  void moveMonth(int n) {
    YearMonth next = shown.plusMonths(n);
    if (next.getYear() < 1600 || next.getYear() > 2500) return;
    choose(next.atDay(Math.min(selected.getDayOfMonth(), next.lengthOfMonth())));
  }

  int events(LocalDate date) {
    int total = 0;
    for (Row r : records) {
      if (!r.live()) continue;
      try {
        boolean matches =
            switch (r.kind) {
              case "assignment", "exam" -> r.s("dueDate").equals(date.toString());
              case "note" -> r.s("lessonDate").equals(date.toString());
              case "planned" -> Times.local(r.s("startsAt")).toLocalDate().equals(date);
              case "session" -> Times.local(r.s("startedAt")).toLocalDate().equals(date);
              case "lesson" -> Rules.lessonOn(r, records, date);
              case "commitment" -> Rules.commitmentOn(r, date);
              default -> false;
            };
        if (matches) total++;
      } catch (RuntimeException ignored) {
      }
    }
    return total;
  }

  void drawMonth() {
    removeAllViews();
    LinearLayout head = new LinearLayout(getContext());
    head.setGravity(Gravity.CENTER);
    TextView back = text("‹", 26),
        title = text(shown.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale())), 16),
        next = text("›", 26);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    back.setContentDescription("Previous month");
    next.setContentDescription("Next month");
    head.addView(back, new LayoutParams(dp(48), dp(40)));
    head.addView(title, new LayoutParams(0, dp(40), 1));
    head.addView(next, new LayoutParams(dp(48), dp(40)));
    back.setOnClickListener(v -> moveMonth(-1));
    next.setOnClickListener(v -> moveMonth(1));
    addView(head);
    int start = firstDay == java.util.Calendar.SUNDAY ? 0 : 1;
    LinearLayout labels = new LinearLayout(getContext());
    for (int i = 0; i < 7; i++) {
      int weekday = (start + i) % 7;
      TextView label =
          text(
              DayOfWeek.of(weekday == 0 ? 7 : weekday).getDisplayName(TextStyle.NARROW, locale()),
              11);
      label.setTextColor(Appearance.color(getContext(), R.attr.deskMuted));
      labels.addView(label, new LayoutParams(0, dp(24), 1));
    }
    addView(labels);
    int offset = (shown.atDay(1).getDayOfWeek().getValue() % 7 - start + 7) % 7;
    LocalDate first = shown.atDay(1).minusDays(offset);
    for (int week = 0; week < 6; week++) {
      LinearLayout line = new LinearLayout(getContext());
      for (int col = 0; col < 7; col++) {
        LocalDate date = first.plusDays(week * 7 + col);
        int count = events(date);
        TextView cell = text(String.valueOf(date.getDayOfMonth()) + (count > 0 ? " ·" : ""), 13);
        cell.setContentDescription(date.toString() + (count > 0 ? " · " + count + " items" : ""));
        if (date.getMonthValue() != shown.getMonthValue()) cell.setAlpha(.4f);
        if (date.equals(selected)) {
          GradientDrawable bg = new GradientDrawable();
          bg.setColor(Appearance.color(getContext(), R.attr.deskHighlight));
          bg.setCornerRadius(dp(8));
          cell.setBackground(bg);
          cell.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        cell.setOnClickListener(v -> choose(date));
        line.addView(cell, new LayoutParams(0, dp(34), 1));
      }
      addView(line);
    }
  }
}
