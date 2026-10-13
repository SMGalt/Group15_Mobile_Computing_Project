package com.group15.studydesk;

import android.app.job.*;
import android.content.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/**
 * Room dirty rows are a durable coalescing outbox. Local edits and the queue share one transaction.
 */
public class CloudSync extends JobService {
  public static final java.util.concurrent.ExecutorService NETWORK =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  static final AtomicBoolean busy = new AtomicBoolean();

  public static void schedule(Context c) {
    CloudClient client = new CloudClient(c);
    if (!client.configured() || client.session() == null) return;
    JobScheduler scheduler = c.getSystemService(JobScheduler.class);
    if (scheduler.getPendingJob(1517) == null)
      scheduler.schedule(
          new JobInfo.Builder(1517, new ComponentName(c, CloudSync.class))
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
              .setPeriodic(15 * 60 * 1000)
              .setPersisted(true)
              .build());
    if (busy.compareAndSet(false, true))
      NETWORK.execute(
          () -> {
            try {
              sync(c, false);
            } finally {
              busy.set(false);
            }
          });
  }

  public static void sync(Context c, boolean force) {
    Repository repo = new Repository(c);
    CloudClient client = new CloudClient(c);
    String owner = repo.owner();
    if (owner.equals("guest") || !client.configured() || client.session() == null) return;
    String problem = "";
    int sent = 0;
    for (Row r : repo.db.records().pending(owner)) {
      if (!CloudSchema.TABLES.containsKey(r.kind)) {
        acknowledge(repo, r, r.payload, true);
        continue;
      }
      if (!force && r.retryAt > System.currentTimeMillis()) continue;
      String expectedPayload = r.payload;
      try {
        if ((r.kind.equals("attachment") || r.kind.equals("noteAttachment"))
            && r.s("storagePath").isEmpty()
            && r.live()) Attachments.upload(c, client, r, owner);
        client.push(r, owner);
        if ((r.kind.equals("attachment") || r.kind.equals("noteAttachment")) && !r.live())
          Attachments.removeCloud(client, r);
        acknowledge(repo, r, expectedPayload, true);
        sent++;
      } catch (Exception e) {
        acknowledge(repo, r, expectedPayload, false);
        problem = e.getMessage();
      }
    }
    if (problem.isEmpty())
      try {
        LinkedHashMap<String, JSONArray> remote = new LinkedHashMap<>();
        for (String kind : CloudSchema.TABLES.keySet())
          remote.put(kind, client.pull(CloudSchema.TABLES.get(kind).name(), owner));
        repo.db.runInTransaction(
            () -> {
              try {
                for (String kind : remote.keySet())
                  for (int i = 0; i < remote.get(kind).length(); i++) {
                    Row incoming = CloudSchema.fromCloud(kind, remote.get(kind).getJSONObject(i));
                    Row local = repo.db.records().find(owner, kind, incoming.id);
                    if (kind.equals("attendance")) {
                      for (Row r : repo.db.records().all(owner))
                        if (r.kind.equals(kind)
                            && r.s("timetableEntryId").equals(incoming.s("timetableEntryId"))
                            && r.s("date").equals(incoming.s("date"))) {
                          local = r;
                          break;
                        }
                    }
                    if (Rules.remoteWins(local, incoming)) {
                      if (local != null && kind.equals("course")) {
                        incoming.put("notes", local.json().opt("notes"));
                        if (incoming.s("color").isEmpty()) incoming.put("color", local.s("color"));
                      }
                      if (local != null && kind.equals("action"))
                        incoming.put("doneAt", local.json().opt("doneAt"));
                      incoming.account = owner;
                      incoming.key = owner + ":" + kind + ":" + incoming.id;
                      incoming.dirty = false;
                      repo.db.records().upsert(incoming);
                    } else if (local != null
                        && kind.equals("attendance")
                        && !local.id.equals(incoming.id)) {
                      repo.db.records().removeKey(local.key);
                      local.id = incoming.id;
                      local.key = owner + ":" + kind + ":" + local.id;
                      repo.db.records().upsert(local);
                    }
                  }
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
      } catch (Exception e) {
        problem = e.getMessage();
      }
    FeedbackQueue.send(c, client, owner);
    Diagnostics.sendPending(c, client, owner);
    c.getSharedPreferences("sync", 0)
        .edit()
        .putString("last", java.time.Instant.now().toString())
        .putString("status", problem.isEmpty() ? "Synced " + sent + " changes" : problem)
        .commit();
    Reminders.rebuild(c, repo.db.records().all(owner));
    DeskWidget.refresh(c);
  }

  static void acknowledge(Repository repo, Row snapshot, String expectedPayload, boolean success) {
    repo.db.runInTransaction(
        () -> {
          Row current = repo.db.records().find(snapshot.account, snapshot.kind, snapshot.id);
          if (current == null
              || !current.updatedAt.equals(snapshot.updatedAt)
              || !current.payload.equals(expectedPayload)) return;
          if (success) {
            snapshot.dirty = false;
            snapshot.attempts = 0;
            snapshot.retryAt = 0;
          } else {
            snapshot.attempts = current.attempts + 1;
            snapshot.retryAt =
                System.currentTimeMillis()
                    + Math.min(
                        6 * 60 * 60 * 1000L, 5000L * (1L << Math.min(12, snapshot.attempts)));
          }
          repo.db.records().upsert(snapshot);
        });
  }

  @Override
  public boolean onStartJob(JobParameters p) {
    NETWORK.execute(
        () -> {
          try {
            sync(this, false);
          } finally {
            jobFinished(p, false);
          }
        });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters p) {
    return true;
  }
}
