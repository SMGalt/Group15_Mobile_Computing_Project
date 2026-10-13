package com.group15.studydesk;

import static org.junit.Assert.*;

import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FeedAndSyncStateTest {
  Repository repo;

  @Before
  public void setup() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    c.getSharedPreferences("session", 0).edit().clear().commit();
    c.getSharedPreferences("settings", 0).edit().putBoolean("notifications", false).commit();
    repo = new Repository(c);
    Repository.IO.submit(() -> repo.db.records().clear("guest")).get();
  }

  @Test
  public void changedAndWithdrawnFeedItemsUpdateRoomWithoutUndoingCompletion() throws Exception {
    String date = LocalDate.now().plusDays(2).toString();
    Row feed = new Row("feed").put("title", "Fixture feed");
    Repository.IO
        .submit(
            () -> {
              try {
                CalendarFeed.apply(
                    repo,
                    feed,
                    Arrays.asList(
                        new CalendarFeed.Event("first", "Essay", "Room B101", date, "08:00", false),
                        new CalendarFeed.Event("second", "Quiz", "", date, "", false)));
                Row first = repo.find("assignment", CalendarFeed.idForUid("first"));
                repo.toggle(first);
                CalendarFeed.apply(
                    repo,
                    feed,
                    List.of(
                        new CalendarFeed.Event(
                            "first", "Renamed essay", "Room B102", date, "10:00", false)));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .get();
    assertEquals(
        1,
        Repository.IO.submit(() -> Rules.kind(repo.all(), "assignment").size()).get().intValue());
    Row first =
        Repository.IO
            .submit(
                () -> {
                  try {
                    return repo.find("assignment", CalendarFeed.idForUid("first"));
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .get();
    assertTrue(first.b("done"));
    assertEquals("10:00", first.s("dueTime"));
    assertEquals("Renamed essay", first.title());
    Repository.IO.submit(() -> repo.delete(feed)).get();
    assertEquals(
        0,
        Repository.IO.submit(() -> Rules.kind(repo.all(), "assignment").size()).get().intValue());
  }

  @Test
  public void oldNetworkAcknowledgementCannotOverwriteNewerOfflineEdit() throws Exception {
    Row row = new Row("assignment").put("title", "First edit");
    Row snapshot =
        Repository.IO
            .submit(
                () -> {
                  repo.save(row);
                  return repo.find("assignment", row.id).copy();
                })
            .get();
    Repository.IO
        .submit(
            () -> {
              Row current = repo.find("assignment", row.id);
              repo.save(current.put("title", "Newer offline edit"));
              CloudSync.acknowledge(repo, snapshot, snapshot.payload, true);
            })
        .get();
    Row current = Repository.IO.submit(() -> repo.find("assignment", row.id)).get();
    assertEquals("Newer offline edit", current.title());
    assertTrue(current.dirty);
    Repository.IO
        .submit(() -> CloudSync.acknowledge(repo, current.copy(), current.payload, true))
        .get();
    assertFalse(Repository.IO.submit(() -> repo.find("assignment", row.id).dirty).get());
  }

  @Test
  public void manualLocalDatetimeIsStoredAsAnInstant() throws Exception {
    Row session =
        new Row("session")
            .put("startedAt", LocalDate.now().atTime(9, 0).toString())
            .put("durationMinutes", 25);
    Repository.IO.submit(() -> repo.save(session)).get();
    Row stored = Repository.IO.submit(() -> repo.find("session", session.id)).get();
    assertTrue(stored.s("startedAt").endsWith("Z"));
    assertEquals(9, Times.local(stored.s("startedAt")).getHour());
  }

  @Test
  public void feedbackOutboxIsDurableAndIsolatedByOwner() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    c.getSharedPreferences("feedback_queue", 0).edit().clear().commit();
    org.json.JSONObject record =
        new org.json.JSONObject()
            .put("id", "fixture-message")
            .put("user_id", "test-owner-a")
            .put("message", "Offline feedback");
    FeedbackQueue.enqueue(c, "test-owner-a", record);
    assertEquals(1, FeedbackQueue.pending(c, "test-owner-a").length());
    assertEquals(0, FeedbackQueue.pending(c, "test-owner-b").length());
    assertEquals(
        "Offline feedback",
        FeedbackQueue.pending(new Repository(c).context, "test-owner-a")
            .getJSONObject(0)
            .getString("message"));
    FeedbackQueue.remove(c, "test-owner-a", "fixture-message");
    assertEquals(0, FeedbackQueue.pending(c, "test-owner-a").length());
  }
}
