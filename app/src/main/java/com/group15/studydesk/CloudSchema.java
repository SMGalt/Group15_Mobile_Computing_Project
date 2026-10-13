package com.group15.studydesk;

import java.util.*;
import org.json.*;

/** Explicit correspondence to upstream sync.js. No author endpoint is bundled. */
public final class CloudSchema {
  public record Table(String name, String fields) {}

  public static final LinkedHashMap<String, Table> TABLES = new LinkedHashMap<>();

  static {
    TABLES.put(
        "course", new Table("subjects", "name credits semester schoolYear color archivedAt"));
    TABLES.put(
        "assignment", new Table("assignments", "courseId title type dueDate dueTime notes done"));
    TABLES.put("exam", new Table("exams", "courseId title dueDate difficulty notes done topics"));
    TABLES.put("action", new Table("study_actions", "courseId text bucket done"));
    TABLES.put("grade", new Table("grades", "subjectId grade weight date"));
    TABLES.put(
        "session",
        new Table(
            "study_sessions",
            "subjectId startedAt durationMinutes notes focusRating aiDebriefRaw aiSubjectCovered"
                + " aiComprehension aiConfusionFlags aiSessionSummary"));
    TABLES.put("term", new Table("academic_terms", "parentId level name startsOn endsOn position"));
    TABLES.put(
        "lesson",
        new Table(
            "timetable_entries",
            "termId subjectId title weekday startsAt endsAt room color weekParity seriesId"));
    TABLES.put(
        "planned",
        new Table(
            "planned_sessions",
            "subjectId startsAt durationMinutes title notes fulfilledBy dismissedAt seriesId"));
    TABLES.put(
        "commitment",
        new Table(
            "commitments",
            "title color weekday startsOn endsOn startTime endTime notes intervalWeeks"));
    TABLES.put("attendance", new Table("lesson_attendance", "timetableEntryId date status note"));
    TABLES.put(
        "note",
        new Table("notebook_entries", "courseId title lessonDate content sessionId layout"));
    TABLES.put(
        "attachment",
        new Table(
            "assignment_attachments", "assignmentId storagePath fileName mimeType sizeBytes"));
    TABLES.put(
        "noteAttachment",
        new Table("notebook_attachments", "entryId storagePath fileName mimeType sizeBytes"));
  }

  public static String snake(String s) {
    return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  public static String column(String kind, String field) {
    return field.equals("courseId") && !kind.equals("note") ? "subject_id" : snake(field);
  }

  public static JSONObject toCloud(Row r, String owner) throws JSONException {
    JSONObject o = new JSONObject();
    if (!r.kind.equals("attendance")) o.put("id", r.id);
    o.put("user_id", owner);
    o.put("updated_at", r.updatedAt);
    o.put("deleted_at", r.deletedAt == null ? JSONObject.NULL : r.deletedAt);
    JSONObject data = r.json();
    for (String field : TABLES.get(r.kind).fields().split(" ")) {
      Object v = data.opt(field);
      if (v instanceof String && ((String) v).isEmpty()) v = JSONObject.NULL;
      if ((field.equals("startedAt") || field.equals("startsAt") && r.kind.equals("planned"))
          && v instanceof String) v = Times.instant((String) v).toString();
      o.put(column(r.kind, field), v == null ? JSONObject.NULL : v);
    }
    return o;
  }

  public static Row fromCloud(String kind, JSONObject o) throws JSONException {
    Row r = new Row(kind);
    r.id = o.getString("id");
    r.updatedAt = o.optString("updated_at", "");
    r.deletedAt = o.isNull("deleted_at") ? null : o.optString("deleted_at", null);
    for (String f : TABLES.get(kind).fields().split(" ")) {
      Object v = o.opt(column(kind, f));
      if (f.equals("dueTime") && v instanceof String && ((String) v).length() >= 5)
        v = ((String) v).substring(0, 5);
      if ((f.equals("startedAt") || f.equals("startsAt") && kind.equals("planned"))
          && v instanceof String) v = Times.instant((String) v).toString();
      r.put(f, v);
    }
    return r;
  }
}
