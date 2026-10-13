package com.group15.studydesk;

import java.time.*;
import java.util.*;
import org.json.*;

/**
 * Optional, explicitly labelled sample records. Uses the same Room save paths as real user input.
 */
public final class DemoData {
  static Row sample(String kind, String name) {
    Row r = new Row(kind);
    r.id =
        UUID.nameUUIDFromBytes(
                ("studydesk-java-demo:" + kind + ":" + name)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    return r;
  }

  public static void load(Repository repo) {
    LocalDate today = LocalDate.now();
    Row course =
        sample("course", "Mobile Computing")
            .put("name", "Mobile Computing · Sample")
            .put("credits", 5)
            .put("color", "#8b4a62")
            .put("semester", "Autumn")
            .put("schoolYear", String.valueOf(today.getYear()));
    repo.save(course);
    repo.save(
        sample("assignment", "Lab report")
            .put("title", "Lab report · Sample")
            .put("courseId", course.id)
            .put("type", "Lab")
            .put("dueDate", today.plusDays(2).toString())
            .put("dueTime", "18:00")
            .put("notes", "Sample: complete the report and review the results."));
    Row exam =
        sample("exam", "Revision quiz")
            .put("title", "Revision quiz · Sample")
            .put("courseId", course.id)
            .put("dueDate", today.plusDays(7).toString())
            .put("difficulty", "medium");
    JSONArray topics = new JSONArray();
    try {
      topics.put(
          new JSONObject()
              .put("id", "topic-1")
              .put("title", "Activity lifecycle")
              .put("done", false));
      topics.put(
          new JSONObject().put("id", "topic-2").put("title", "Local storage").put("done", false));
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    exam.put("topics", topics);
    repo.save(exam);
    Row term =
        sample("term", "Current term")
            .put("name", "Current term · Sample")
            .put("level", "semester")
            .put("startsOn", today.withDayOfMonth(1).toString())
            .put("endsOn", today.plusMonths(3).withDayOfMonth(1).toString())
            .put("position", 0);
    repo.save(term);
    repo.save(
        sample("lesson", "Lecture")
            .put("title", "Lecture · Sample")
            .put("termId", term.id)
            .put("subjectId", course.id)
            .put("weekday", today.getDayOfWeek().getValue() % 7)
            .put("startsAt", "09:00")
            .put("endsAt", "10:00")
            .put("room", "B101"));
    repo.save(
        sample("planned", "Revision")
            .put("title", "Revision · Sample")
            .put("subjectId", course.id)
            .put("startsAt", today.atTime(18, 0).toString())
            .put("durationMinutes", 25));
    repo.save(
        sample("note", "Revision notes")
            .put("title", "Revision notes · Sample")
            .put("courseId", course.id)
            .put("lessonDate", today.toString())
            .put(
                "content",
                "# Mobile computing\n"
                    + "**Important:** Save state before leaving a screen.\n"
                    + "- [ ] Review the lifecycle\n"
                    + "$$\\frac{completed}{total}$$\n"
                    + "$$2H2 + O2 -> 2H2O$$\n"
                    + "Plan -> Study -> Reflect"));
  }
}
