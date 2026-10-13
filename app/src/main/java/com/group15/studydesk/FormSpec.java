package com.group15.studydesk;

import java.util.*;

/** Small, explicit form descriptions: no reflection or web rendering. */
public final class FormSpec {
  public record Field(
      String key, String label, String type, String options, boolean required, String initial) {}

  private static Field f(String key, String label, String type, boolean required, String initial) {
    return new Field(key, label, type, "", required, initial);
  }

  private static Field choice(String k, String label, String opts, String initial) {
    return new Field(k, label, "choice", opts, false, initial);
  }

  public static List<Field> fields(String kind) {
    List<Field> f = new ArrayList<>();
    String date = java.time.LocalDate.now().toString();
    if (kind.equals("course")) {
      f.add(f("name", "Course name", "text", true, ""));
      f.add(f("credits", "Credits", "number", true, "1"));
      f.add(f("semester", "Period / Semester", "text", false, ""));
      f.add(f("schoolYear", "School year", "text", false, ""));
      f.add(f("color", "Colour (hex)", "text", true, "#8b4a62"));
      return f;
    }
    if (kind.equals("term")) {
      f.add(f("name", "Name", "text", true, ""));
      f.add(choice("level", "Level", "year|semester|jakso", "year"));
      f.add(f("parentId", "Parent term", "term", false, ""));
      f.add(f("startsOn", "Start date", "date", false, date));
      f.add(f("endsOn", "End date", "date", false, ""));
      f.add(f("position", "Position", "number", false, "0"));
      return f;
    }
    if (kind.equals("action")) {
      f.add(f("text", "What do you need to do?", "text", true, ""));
      f.add(choice("bucket", "When", "today|this_week|later", "today"));
      f.add(f("courseId", "Course", "course", false, ""));
      return f;
    }
    if (kind.equals("grade")) {
      f.add(f("subjectId", "Course", "course", true, ""));
      f.add(f("grade", "Grade", "number", true, ""));
      f.add(f("weight", "Weight", "number", true, "1"));
      f.add(f("date", "Date", "date", true, date));
      return f;
    }
    if (kind.equals("session")) {
      f.add(f("subjectId", "Course", "course", false, ""));
      f.add(f("startedAt", "Started at", "datetime", true, date + "T09:00"));
      f.add(f("durationMinutes", "Minutes studied", "number", true, "25"));
      f.add(choice("focusRating", "Focus rating", "|1|2|3|4|5", ""));
      f.add(f("notes", "Notes", "multiline", false, ""));
      return f;
    }
    if (kind.equals("lesson")) {
      f.add(f("subjectId", "Course", "course", false, ""));
      f.add(f("title", "Lesson title", "text", true, ""));
      f.add(f("termId", "Academic term", "term", true, ""));
      f.add(choice("weekday", "Weekday (Sunday = 0)", "0|1|2|3|4|5|6", "1"));
      f.add(f("startsAt", "Start time", "time", true, "09:00"));
      f.add(f("endsAt", "End time", "time", true, "10:00"));
      f.add(f("room", "Room", "text", false, ""));
      f.add(choice("weekParity", "Week pattern", "|1|2", ""));
      f.add(
          f("additionalWeekdays", "Additional weekdays (0–6, comma separated)", "text", false, ""));
      return f;
    }
    if (kind.equals("commitment")) {
      f.add(f("title", "Commitment", "text", true, ""));
      f.add(choice("weekday", "Weekday (empty = one-off)", "|0|1|2|3|4|5|6", ""));
      f.add(f("startsOn", "Starts on", "date", true, date));
      f.add(f("endsOn", "Ends on", "date", false, ""));
      f.add(f("startTime", "Start time", "time", true, "17:00"));
      f.add(f("endTime", "End time", "time", true, "18:00"));
      f.add(f("intervalWeeks", "Repeat every N weeks", "number", false, "1"));
      f.add(f("notes", "Notes", "multiline", false, ""));
      return f;
    }
    if (kind.equals("planned")) {
      f.add(f("subjectId", "Course", "course", false, ""));
      f.add(f("title", "Study plan", "text", false, ""));
      f.add(f("startsAt", "Starts at", "datetime", true, date + "T18:00"));
      f.add(f("durationMinutes", "Minutes", "number", true, "25"));
      f.add(f("notes", "Notes", "multiline", false, ""));
      f.add(f("repeatWeeks", "Repeat interval (weeks)", "number", false, "1"));
      f.add(f("repeatCount", "Number of occurrences (0 = term horizon)", "number", false, "1"));
      return f;
    }
    if (kind.equals("note")) {
      f.add(f("courseId", "Course", "course", false, ""));
      f.add(f("title", "Note title", "text", false, ""));
      f.add(f("lessonDate", "Lesson date", "date", false, ""));
      f.add(f("content", "Note content", "multiline", false, ""));
      return f;
    }
    if (kind.equals("feed")) {
      f.add(f("title", "Feed name", "text", true, ""));
      f.add(f("url", "ICS URL (https)", "text", true, ""));
      f.add(f("courseId", "Default course", "course", false, ""));

      return f;
    }
    f.add(f("courseId", "Course", "course", true, ""));
    f.add(f("title", kind.equals("exam") ? "Exam title" : "Assignment title", "text", true, ""));
    if (kind.equals("assignment")) {
      f.add(
          choice("type", "Type", "Essay|Problem Set|Lab|Reading|Exam|Project|Quiz|Other", "Essay"));
      f.add(f("customType", "Custom type (when Other)", "text", false, ""));
    }
    f.add(f("dueDate", "Due date", "date", kind.equals("exam"), date));
    if (kind.equals("assignment")) f.add(f("dueTime", "Due time (optional)", "time", false, ""));
    if (kind.equals("exam"))
      f.add(choice("difficulty", "Difficulty", "easy|medium|hard|brutal", "medium"));
    f.add(f("notes", "Notes", "multiline", false, ""));
    return f;
  }

  public static void validate(Row row, List<Row> all, String mode, double min, double max) {
    for (Field f : fields(row.kind)) {
      String s = row.s(f.key);
      if (f.required && s.trim().isEmpty())
        throw new IllegalArgumentException(f.label + " is required");
      if (s.isEmpty()) continue;
      try {
        switch (f.type) {
          case "date" -> java.time.LocalDate.parse(s);
          case "time" -> java.time.LocalTime.parse(s);
          case "datetime" -> java.time.LocalDateTime.parse(s);
          case "number" -> {
            double n = Double.parseDouble(s);
            if (!Double.isFinite(n)) throw new IllegalArgumentException();
          }
          case "course" -> {
            if (Rules.byId(all, "course", s) == null) throw new IllegalArgumentException();
          }
          case "term" -> {
            if (Rules.byId(all, "term", s) == null) throw new IllegalArgumentException();
          }
          default -> {}
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Check " + f.label);
      }
    }
    if (row.kind.equals("course") && !row.s("color").matches("#[A-Fa-f0-9]{6}"))
      throw new IllegalArgumentException("Use a six digit colour hex value");
    if (row.kind.equals("assignment")
        && row.s("type").equals("Other")
        && !row.s("customType").isEmpty()) row.put("type", row.s("customType"));
    if (row.kind.equals("lesson") && !row.s("additionalWeekdays").isEmpty()) {
      for (String day : row.s("additionalWeekdays").split(",")) {
        try {
          int n = Integer.parseInt(day.trim());
          if (n < 0 || n > 6) throw new IllegalArgumentException();
        } catch (Exception e) {
          throw new IllegalArgumentException("Additional weekdays must be 0–6");
        }
      }
    }
    if (row.kind.equals("course") && row.n("credits", 0) < 0)
      throw new IllegalArgumentException("Credits must be non-negative");
    if (row.kind.equals("grade")) {
      double grade = row.n("grade", 0);
      double low = mode.equals("ib") ? 1 : mode.equals("us") ? 0 : min,
          high = mode.equals("ib") ? 7 : mode.equals("us") ? 100 : max;
      if (grade < low || grade > high || row.n("weight", 1) < 0)
        throw new IllegalArgumentException(
            "Grade must be " + low + "–" + high + " and weight non-negative");
    }
    if (row.kind.equals("session") || row.kind.equals("planned")) {
      double n = row.n("durationMinutes", 0);
      if (n < 1 || n > 1440) throw new IllegalArgumentException("Duration must be 1–1440 minutes");
      row.put("durationMinutes", Math.round(n));
    }
    if (row.kind.equals("lesson") || row.kind.equals("commitment")) {
      String a = row.s(row.kind.equals("lesson") ? "startsAt" : "startTime"),
          b = row.s(row.kind.equals("lesson") ? "endsAt" : "endTime");
      if (!java.time.LocalTime.parse(b).isAfter(java.time.LocalTime.parse(a)))
        throw new IllegalArgumentException("End time must be after start time");
    }
    if (row.kind.equals("term") || row.kind.equals("commitment")) {
      if (!row.s("startsOn").isEmpty()
          && !row.s("endsOn").isEmpty()
          && java.time.LocalDate.parse(row.s("endsOn"))
              .isBefore(java.time.LocalDate.parse(row.s("startsOn"))))
        throw new IllegalArgumentException("End date must follow start date");
    }
    if (row.kind.equals("term")) {
      String id = row.s("parentId");
      Set<String> seen = new HashSet<>();
      seen.add(row.id);
      while (!id.isEmpty()) {
        if (!seen.add(id)) throw new IllegalArgumentException("Term parent would create a cycle");
        Row parent = Rules.byId(all, "term", id);
        if (parent == null) break;
        id = parent.s("parentId");
      }
      if (row.s("level").equals("year")) row.put("parentId", null);
    }
    if (row.kind.equals("planned")
        && (row.n("repeatCount", 1) < 0
            || row.n("repeatCount", 1) > 60
            || row.n("repeatWeeks", 1) < 1
            || row.n("repeatWeeks", 1) > 4))
      throw new IllegalArgumentException("Repeat interval must be 1–4 weeks and count 0–60");
    if (row.kind.equals("commitment")) {
      if (row.s("weekday").isEmpty()) row.put("endsOn", null).put("intervalWeeks", null);
      else if (row.n("intervalWeeks", 1) < 1 || row.n("intervalWeeks", 1) > 52)
        throw new IllegalArgumentException("Repeat interval must be 1–52");
    }
    if (row.kind.equals("feed") && !row.s("url").startsWith("https://"))
      throw new IllegalArgumentException("Use an HTTPS calendar URL");
  }
}
