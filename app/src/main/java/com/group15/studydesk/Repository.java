package com.group15.studydesk;

import android.content.*;
import androidx.lifecycle.LiveData;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class Repository {
  public static final ExecutorService IO = Executors.newSingleThreadExecutor();
  final Context context;
  final DeskDatabase db;

  public Repository(Context c) {
    context = c.getApplicationContext();
    db = DeskDatabase.get(c);
  }

  public String owner() {
    return context.getSharedPreferences("session", 0).getString("owner", "guest");
  }

  public LiveData<List<Row>> observe() {
    return db.records().observe(owner());
  }

  public List<Row> all() {
    return db.records().all(owner());
  }

  public Row find(String kind, String id) {
    return db.records().find(owner(), kind, id);
  }

  public void write(Runnable work) {
    IO.execute(
        () -> {
          work.run();
          changed();
        });
  }

  public void save(Row r) {
    if ((r.kind.equals("assignment") || r.kind.equals("exam") || r.kind.equals("action"))
        && !r.json().has("done")) r.put("done", false);
    if (r.kind.equals("exam") && !r.json().has("topics")) r.put("topics", new org.json.JSONArray());
    String instantField =
        r.kind.equals("session") ? "startedAt" : r.kind.equals("planned") ? "startsAt" : null;
    if (instantField != null && !r.s(instantField).isEmpty())
      r.put(instantField, Times.instant(r.s(instantField)).toString());
    r.stamp(owner());
    db.records().upsert(r);
  }

  /**
   * Write a study session together with the plan it answered, or not at all.
   *
   * <p>The pair is one fact — {@code fulfilledBy} is a pointer onto the session row — so half of it
   * is worse than neither: a plan claiming a block that was never stored cannot be repaired from
   * either side.
   */
  public void saveWithPlan(Row session, Row plan) {
    db.runInTransaction(
        () -> {
          save(session);
          if (plan != null) save(plan);
        });
  }

  public void delete(Row r) {
    db.runInTransaction(
        () -> {
          String stamp = Instant.now().toString();
          r.deletedAt = stamp;
          save(r);
          for (Row child : all())
            if (child.live()
                && ((r.kind.equals("course")
                        && (child.kind.equals("assignment")
                            || child.kind.equals("exam")
                            || child.kind.equals("grade"))
                        && child.course().equals(r.id))
                    || (r.kind.equals("assignment")
                        && child.kind.equals("attachment")
                        && child.s("assignmentId").equals(r.id))
                    || (r.kind.equals("note")
                        && child.kind.equals("noteAttachment")
                        && child.s("entryId").equals(r.id)))) {
              child.deletedAt = stamp;
              save(child);
            }
          if (r.kind.equals("feed")) {
            org.json.JSONArray imported = r.json().optJSONArray("importedIds");
            if (imported != null)
              for (int i = 0; i < imported.length(); i++) {
                Row assignment = find("assignment", imported.optString(i));
                if (assignment != null && assignment.live()) delete(assignment);
              }
          }
          if (r.kind.equals("term")) deleteTermChildren(r.id, stamp, new HashSet<>());
        });
  }

  private void deleteTermChildren(String id, String stamp, Set<String> seen) {
    if (!seen.add(id)) return;
    for (Row child : all())
      if (child.live()
          && (child.kind.equals("term") && child.s("parentId").equals(id)
              || child.kind.equals("lesson") && child.s("termId").equals(id))) {
        child.deletedAt = stamp;
        save(child);
        if (child.kind.equals("term")) deleteTermChildren(child.id, stamp, seen);
      }
  }

  public void toggle(Row r) {
    r.put("done", !r.b("done"));
    if (r.kind.equals("action")) r.put("doneAt", r.b("done") ? System.currentTimeMillis() : null);
    save(r);
  }

  public void changed() {
    FeedSync.schedule(context, !Rules.kind(db.records().all(owner()), "feed").isEmpty());
    Reminders.rebuild(context, db.records().all(owner()));
    DeskWidget.refresh(context);
    CloudSync.schedule(context);
  }

  public void purgeActions() {
    for (Row r : all())
      if (r.kind.equals("action")
          && r.live()
          && r.b("done")
          && !Rules.manualVisible(r, java.time.ZonedDateTime.now())) delete(r);
  }
}
