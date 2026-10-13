package com.group15.studydesk;

import java.util.*;
import org.json.*;

/** Exact content/hash/layout storage contract of upstream notebook/layout.js. */
public final class NoteLayout {
  public record Box(String id, double x, double y, double w, String text) {}

  public static String hash(String text) {
    int h = 0x811c9dc5;
    for (char c : text.toCharArray()) {
      h ^= c;
      h *= 0x01000193;
    }
    return Long.toString(Integer.toUnsignedLong(h), 36);
  }

  public static Box box(double x, double y, double width, String text) {
    x = Math.max(0, Math.min(.82, x));
    return new Box(
        UUID.randomUUID().toString().substring(0, 8),
        x,
        Math.max(0, Math.round(y / 28) * 28),
        Math.max(.18, Math.min(1 - x, width)),
        text);
  }

  public static List<Box> ordered(List<Box> boxes) {
    List<Box> out = new ArrayList<>(boxes);
    out.sort(Comparator.comparingDouble(Box::y).thenComparingDouble(Box::x));
    return out;
  }

  public static String join(List<Box> boxes) {
    List<String> text = new ArrayList<>();
    for (Box b : ordered(boxes)) text.add(b.text);
    return String.join("\n\n", text);
  }

  public static List<Box> read(String content, Object layout) {
    List<Box> boxes = new ArrayList<>();
    try {
      JSONObject stored =
          layout instanceof JSONObject
              ? (JSONObject) layout
              : layout instanceof String ? new JSONObject((String) layout) : null;
      if (stored != null && stored.optJSONArray("boxes") != null) {
        JSONArray list = stored.getJSONArray("boxes");
        for (int i = 0; i < list.length(); i++) {
          JSONObject b = list.getJSONObject(i);
          Box normalized =
              box(b.optDouble("x"), b.optDouble("y"), b.optDouble("w", 1), b.optString("text"));
          boxes.add(
              new Box(
                  b.optString("id", normalized.id),
                  normalized.x,
                  normalized.y,
                  normalized.w,
                  normalized.text));
        }
        if (!boxes.isEmpty()
            && hash(content).equals(stored.optString("hash"))
            && hash(join(boxes)).equals(stored.optString("hash"))) return boxes;
      }
    } catch (Exception ignored) {
    }
    return new ArrayList<>(List.of(box(0, 0, 1, content)));
  }

  public static JSONObject write(List<Box> boxes) throws JSONException {
    JSONArray a = new JSONArray();
    for (Box b : ordered(boxes))
      a.put(
          new JSONObject()
              .put("id", b.id)
              .put("x", b.x)
              .put("y", b.y)
              .put("w", b.w)
              .put("text", b.text));
    return new JSONObject().put("v", 1).put("hash", hash(join(boxes))).put("boxes", a);
  }
}
