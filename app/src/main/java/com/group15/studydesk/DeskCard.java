package com.group15.studydesk;

import java.util.*;
import org.json.*;

/**
 * Turns one record into one list card.
 *
 * <p>Shared by the desk list and by the timer hub's Log and Notebook sub-tabs. Those show the same
 * rows the main list shows, and the original reaches them from the timer — so they have to describe a
 * session or a note exactly as the desk does. A second implementation would drift the first time
 * either side changed.
 */
public final class DeskCard {
  private DeskCard() {}

  public static CardAdapter.Card of(Row r, String tag, List<Row> all) {
    String detail = Rules.courseName(all, r);
    String primary = "Open", title = r.title();
    switch (r.kind) {
      case "course" -> {
        long
            a =
                Rules.kind(all, "assignment").stream()
                    .filter(x -> x.course().equals(r.id) && !x.b("done"))
                    .count(),
            e =
                Rules.kind(all, "exam").stream()
                    .filter(x -> x.course().equals(r.id) && !x.b("done"))
                    .count();
        detail =
            r.s("semester")
                + " · "
                + r.n("credits", 1)
                + " credits\n"
                + a
                + " assignments · "
                + e
                + " exams";
      }
      case "assignment", "exam" -> {
        primary = r.b("done") ? "Undo" : "Mark done";
        detail +=
            (detail.isEmpty() ? "" : "\n")
                + (r.s("dueDate").isEmpty() ? "No due date" : r.s("dueDate") + " " + r.s("dueTime"))
                + (r.b("done") ? " · Completed" : "");
        if (r.kind.equals("exam")) {
          detail += "\n" + r.s("difficulty") + " · Study from " + Rules.studyStart(r);
          JSONArray topics = r.json().optJSONArray("topics");
          if (topics != null)
            for (int i = 0; i < topics.length(); i++) {
              JSONObject t = topics.optJSONObject(i);
              if (t != null && !t.optBoolean("done")) {
                title = "Study for " + title + " — " + t.optString("title");
                break;
              }
            }
        }
        detail += "\n" + r.s("notes");
      }
      case "action" -> {
        primary = r.b("done") ? "Undo" : "Mark done";
        detail = r.s("bucket") + (r.b("done") ? " · Completed" : "");
      }
      case "grade" -> {
        title = String.valueOf(r.n("grade", 0));
        detail += " · " + r.s("date") + "\nWeight: " + r.n("weight", 1);
        primary = "Edit";
      }
      case "session" -> {
        title = r.n("durationMinutes", 0) + " minutes";
        detail +=
            "\n"
                + Times.local(r.s("startedAt")).toString().replace('T', ' ')
                + " · Focus "
                + r.s("focusRating")
                + "\n"
                + r.s("notes");
      }
      case "planned" -> {
        detail +=
            "\n"
                + Times.local(r.s("startsAt")).toString().replace('T', ' ')
                + " · "
                + r.n("durationMinutes", 25)
                + " minutes";
        if (!r.s("fulfilledBy").isEmpty()) detail += " · Logged";
        if (!r.s("dismissedAt").isEmpty()) detail += " · Dismissed";
        primary = "Start studying";
      }
      case "lesson" -> {
        detail +=
            "\n"
                + r.s("startsAt")
                + "–"
                + r.s("endsAt")
                + " · "
                + r.s("room")
                + "\nWeekday "
                + r.s("weekday")
                + (r.s("weekParity").isEmpty()
                    ? " · Every week"
                    : r.s("weekParity").equals("1") ? " · Odd weeks" : " · Even weeks");
      }
      case "commitment" -> {
        detail =
            r.s("startsOn")
                + " · "
                + r.s("startTime")
                + "–"
                + r.s("endTime")
                + "\n"
                + (r.s("weekday").isEmpty()
                    ? "One-off"
                    : "Repeats every " + r.n("intervalWeeks", 1) + " weeks");
      }
      case "term" -> {
        detail = r.s("level") + " · " + r.s("startsOn") + " — " + r.s("endsOn");
      }
      case "note" -> {
        detail +=
            "\n"
                + r.s("lessonDate")
                + "\n"
                + r.s("content").substring(0, Math.min(180, r.s("content").length()));
        if (title.isEmpty()) title = "Untitled note";
      }
      case "feed" -> {
        detail = r.s("url") + "\n" + r.s("lastFetched");
        primary = "Refresh feed";
      }
    }
    return new CardAdapter.Card(r, tag.toUpperCase(Locale.ROOT), title, detail.trim(), primary, null);
  }
}
