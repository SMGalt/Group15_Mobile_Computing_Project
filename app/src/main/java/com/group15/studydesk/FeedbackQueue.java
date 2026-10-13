package com.group15.studydesk;

import android.content.Context;
import org.json.*;

/**
 * Small private, per-account outbox. Feedback retries use the same UUID for idempotent delivery.
 */
public final class FeedbackQueue {
  static final Object LOCK = new Object();

  static String key(String owner) {
    return "pending:" + owner;
  }

  public static JSONArray pending(Context c, String owner) {
    synchronized (LOCK) {
      try {
        return new JSONArray(
            c.getSharedPreferences("feedback_queue", 0).getString(key(owner), "[]"));
      } catch (JSONException e) {
        return new JSONArray();
      }
    }
  }

  public static void enqueue(Context c, String owner, JSONObject record) throws Exception {
    if (owner.equals("guest") || !owner.equals(record.optString("user_id")))
      throw new IllegalArgumentException("Feedback needs your signed-in account");
    synchronized (LOCK) {
      JSONArray items = pending(c, owner);
      if (items.length() >= 50)
        throw new IllegalStateException("50 feedback messages are already waiting to send");
      items.put(record);
      if (!c.getSharedPreferences("feedback_queue", 0)
          .edit()
          .putString(key(owner), items.toString())
          .commit()) throw new IllegalStateException("Could not save feedback locally");
    }
  }

  static void remove(Context c, String owner, String id) throws Exception {
    synchronized (LOCK) {
      JSONArray keep = new JSONArray(), items = pending(c, owner);
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.getJSONObject(i);
        if (!item.optString("id").equals(id)) keep.put(item);
      }
      c.getSharedPreferences("feedback_queue", 0)
          .edit()
          .putString(key(owner), keep.toString())
          .commit();
    }
  }

  static void send(Context c, CloudClient cloud, String owner) {
    JSONArray items = pending(c, owner);
    for (int i = 0; i < items.length(); i++) {
      try {
        if (!new Repository(c).owner().equals(owner)) return;
        JSONObject record = items.getJSONObject(i);
        cloud.request(
            "POST",
            "/rest/v1/feedback?on_conflict=id",
            record.toString(),
            cloud.token(),
            "resolution=ignore-duplicates,return=minimal");
        remove(c, owner, record.getString("id"));
      } catch (Exception error) {
        return;
      }
    }
  }
}
