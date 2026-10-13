package com.group15.studydesk;

import android.content.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import okhttp3.*;

/**
 * Baseline feed semantics: recurrence imports DTSTART once; unsupported TZID stays floating local.
 */
public final class CalendarFeed {
  public record Event(
      String uid, String title, String notes, String date, String time, boolean repeating) {}

  public static String idForUid(String uid) throws Exception {
    byte[] h =
        MessageDigest.getInstance("SHA-256")
            .digest(("studydesk.calendar-feed.v1:" + uid).getBytes(StandardCharsets.UTF_8));
    h[6] = (byte) ((h[6] & 15) | 80);
    h[8] = (byte) ((h[8] & 63) | 128);
    java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(h);
    return new UUID(b.getLong(), b.getLong()).toString();
  }

  public static List<Event> parse(String text) {
    List<Event> events = new ArrayList<>();
    String unfolded = text.replace("\r\n", "\n").replaceAll("\n[ \t]", "");
    Map<String, String> e = null;
    for (String line : unfolded.split("\n")) {
      if (line.equals("BEGIN:VEVENT")) {
        e = new HashMap<>();
        continue;
      }
      if (line.equals("END:VEVENT")) {
        if (e != null && e.containsKey("DTSTART") && !"CANCELLED".equals(e.get("STATUS"))) {
          try {
            String raw = e.get("DTSTART"), date, time = "";
            if (raw.matches("[0-9]{8}"))
              date = LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).toString();
            else {
              LocalDateTime dt =
                  LocalDateTime.parse(
                      raw.replace("Z", ""), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
              if (raw.endsWith("Z"))
                dt = dt.toInstant(ZoneOffset.UTC).atZone(ZoneId.systemDefault()).toLocalDateTime();
              date = dt.toLocalDate().toString();
              time = dt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            events.add(
                new Event(
                    e.getOrDefault("UID", ""),
                    unescape(e.getOrDefault("SUMMARY", "Untitled event")),
                    notesFor(e),
                    date,
                    time,
                    e.containsKey("RRULE")));
          } catch (Exception ignored) {
          }
        }
        e = null;
        continue;
      }
      if (e == null) continue;
      int colon = -1;
      boolean quote = false;
      for (int k = 0; k < line.length(); k++) {
        char ch = line.charAt(k);
        if (ch == '"') quote = !quote;
        if (ch == ':' && !quote) {
          colon = k;
          break;
        }
      }
      if (colon <= 0) continue;
      String key = line.substring(0, colon).split(";")[0];
      e.put(key, line.substring(colon + 1));
    }
    return events;
  }

  private static String notesFor(Map<String, String> event) {
    String location = unescape(event.getOrDefault("LOCATION", "")).trim(),
        description = unescape(event.getOrDefault("DESCRIPTION", "")).trim();
    String joined =
        location + (location.isEmpty() || description.isEmpty() ? "" : "\n") + description;
    return joined.length() > 2000 ? joined.substring(0, 1999) + "…" : joined;
  }

  private static String unescape(String s) {
    return s.replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\");
  }

  public static void refresh(Context c, Repository repo, Row feed) throws Exception {
    OkHttpClient http = new OkHttpClient();
    String raw;
    try (Response r = http.newCall(new Request.Builder().url(feed.s("url")).build()).execute()) {
      if (!r.isSuccessful() || r.body() == null)
        throw new java.io.IOException("Feed could not be fetched (HTTP " + r.code() + ")");
      raw = r.body().string();
      if (raw.length() > 5 * 1024 * 1024 || !raw.contains("BEGIN:VCALENDAR"))
        throw new java.io.IOException("Not a supported calendar feed");
    }
    apply(repo, feed, parse(raw));
  }

  public static void apply(Repository repo, Row feed, List<Event> events) throws Exception {
    org.json.JSONArray old = feed.json().optJSONArray("importedIds"),
        current = new org.json.JSONArray();
    Set<String> ids = new HashSet<>();
    int repeats = 0;
    for (Event e : events) {
      if (e.uid.isEmpty()) continue;
      String id = idForUid(e.uid);
      if (!ids.add(id)) continue;
      current.put(id);
      Row r = repo.find("assignment", id);
      if (r == null) r = new Row("assignment");
      if (!r.live()) continue;
      r.id = id;
      r.put("courseId", feed.s("courseId").isEmpty() ? null : feed.s("courseId"))
          .put("title", e.title)
          .put("dueDate", e.date)
          .put("dueTime", e.time.isEmpty() ? null : e.time)
          .put("notes", e.notes)
          .put("type", "imported");
      repo.save(r);
      if (e.repeating) repeats++;
    }
    if (old != null)
      for (int i = 0; i < old.length(); i++)
        if (!ids.contains(old.optString(i))) {
          Row gone = repo.find("assignment", old.optString(i));
          if (gone != null && gone.live()) repo.delete(gone);
        }
    feed.put("lastFetched", java.time.Instant.now().toString())
        .put("repeatingCount", repeats)
        .put("items", events.size())
        .put("importedIds", current);
    repo.save(feed);
  }

  public static void refreshDue(Context c, Repository repo) {
    for (Row f : Rules.kind(repo.all(), "feed")) {
      try {
        if (f.s("lastFetched").isEmpty()
            || Instant.parse(f.s("lastFetched")).isBefore(Instant.now().minusSeconds(6 * 3600)))
          refresh(c, repo, f);
      } catch (Exception ignored) {
      }
    }
  }
}
