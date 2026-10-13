package com.group15.studydesk;

import androidx.annotation.NonNull;
import androidx.room.*;
import java.time.Instant;
import java.util.UUID;
import org.json.*;

/**
 * One native Room record. Payload preserves upstream camelCase fields without lossy schema
 * narrowing.
 */
@Entity(
    tableName = "records",
    indices = {
      @Index(
          value = {"account", "kind", "id"},
          unique = true)
    })
public class Row {
  @PrimaryKey @NonNull public String key = "";
  @NonNull public String account = "guest", kind = "", id = "";
  @NonNull public String payload = "{}", updatedAt = "";
  public String deletedAt;
  public boolean dirty;
  public int attempts;
  public long retryAt;

  public Row() {}

  @Ignore
  public Row(String kind) {
    this.kind = kind;
    id = UUID.randomUUID().toString();
  }

  @Ignore
  public JSONObject json() {
    try {
      return new JSONObject(payload);
    } catch (JSONException e) {
      throw new IllegalStateException("Invalid stored record", e);
    }
  }

  @Ignore
  public String s(String field) {
    JSONObject data = json();
    return data.isNull(field) ? "" : data.optString(field, "");
  }

  @Ignore
  public double n(String field, double fallback) {
    return json().optDouble(field, fallback);
  }

  @Ignore
  public boolean b(String field) {
    return json().optBoolean(field, false);
  }

  @Ignore
  public Row put(String field, Object value) {
    try {
      JSONObject a = json();
      a.put(field, value == null ? JSONObject.NULL : value);
      payload = a.toString();
      return this;
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Ignore
  public String title() {
    return s(
        kind.equals("course") || kind.equals("term")
            ? "name"
            : kind.equals("action") ? "text" : "title");
  }

  @Ignore
  public String course() {
    return s(
        kind.equals("grade")
                || kind.equals("session")
                || kind.equals("planned")
                || kind.equals("lesson")
            ? "subjectId"
            : "courseId");
  }

  @Ignore
  public boolean live() {
    return deletedAt == null || deletedAt.isEmpty();
  }

  @Ignore
  public Row copy() {
    Row r = new Row();
    r.key = key;
    r.account = account;
    r.kind = kind;
    r.id = id;
    r.payload = payload;
    r.updatedAt = updatedAt;
    r.deletedAt = deletedAt;
    r.dirty = dirty;
    r.attempts = attempts;
    r.retryAt = retryAt;
    return r;
  }

  public void stamp(String owner) {
    account = owner;
    key = owner + ":" + kind + ":" + id;
    updatedAt = Instant.now().toString();
    dirty = true;
    attempts = 0;
    retryAt = 0;
  }
}
