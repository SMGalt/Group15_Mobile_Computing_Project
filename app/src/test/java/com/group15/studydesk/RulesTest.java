package com.group15.studydesk;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class RulesTest {
  Row row(String kind, String title) {
    return new Row(kind)
        .put(kind.equals("course") || kind.equals("term") ? "name" : "title", title);
  }

  @Test
  public void nextUpCapsEachTypeAndPreservesManualBeforeSuggestions() {
    LocalDate today = LocalDate.of(2026, 10, 13);
    List<Row> rows = new ArrayList<>();
    Row course = row("course", "Math");
    rows.add(course);
    rows.add(row("action", "").put("text", "Review").put("bucket", "later"));
    for (String kind : new String[] {"assignment", "exam"})
      for (int days : new int[] {8, 1, 7, 3, -1, 0})
        rows.add(
            row(kind, kind + days)
                .put("dueDate", today.plusDays(days).toString())
                .put("courseId", course.id));
    List<Row> result = Rules.nextUp(rows, today);
    assertEquals(7, result.size());
    assertEquals("action", result.get(0).kind);
    assertEquals("assignment0", result.get(1).title());
    assertEquals("assignment1", result.get(2).title());
    assertEquals("assignment3", result.get(3).title());
    assertEquals("exam0", result.get(4).title());
    assertEquals("today", result.get(3).s("bucket"));
  }

  @Test
  public void noActiveCourseAndNoDeadlinesMatchSourceUnlockStates() {
    Row course = row("course", "Math").put("archivedAt", "2026-10-01");
    Row assignment = row("assignment", "A").put("dueDate", "2026-10-14");
    assertTrue(
        Rules.nextUp(Arrays.asList(course, assignment), LocalDate.of(2026, 10, 13)).isEmpty());
    course.put("archivedAt", null);
    assignment.put("done", true);
    assertTrue(
        Rules.nextUp(Arrays.asList(course, assignment), LocalDate.of(2026, 10, 13)).isEmpty());
  }

  @Test
  public void bucketThresholdsAndDifficultyLeadTimes() {
    assertEquals("today", Rules.bucket(3));
    assertEquals("this_week", Rules.bucket(4));
    assertEquals("this_week", Rules.bucket(7));
    assertEquals("later", Rules.bucket(8));
    Row exam = row("exam", "E").put("dueDate", "2026-11-01");
    String[] levels = {"easy", "medium", "hard", "brutal"};
    int[] leads = {3, 7, 14, 21};
    for (int i = 0; i < 4; i++)
      assertEquals(
          LocalDate.of(2026, 11, 1).minusDays(leads[i]),
          Rules.studyStart(exam.put("difficulty", levels[i])));
  }

  @Test
  public void completedManualActionPurgesAtFollowingLocalThreeAm() {
    Row r =
        row("action", "A")
            .put("done", true)
            .put(
                "doneAt",
                ZonedDateTime.of(2026, 10, 13, 23, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                    .toInstant()
                    .toEpochMilli());
    assertTrue(
        Rules.manualVisible(
            r, ZonedDateTime.of(2026, 10, 14, 2, 59, 59, 0, ZoneId.of("Asia/Shanghai"))));
    assertFalse(
        Rules.manualVisible(
            r, ZonedDateTime.of(2026, 10, 14, 3, 0, 0, 0, ZoneId.of("Asia/Shanghai"))));
  }

  @Test
  public void datesSurviveDstAndUtcOffsets() {
    assertEquals(1, Rules.days("2026-03-09", LocalDate.of(2026, 3, 8)));
    Row a = row("assignment", "A").put("dueDate", "2026-10-13");
    assertEquals(Instant.parse("2026-10-13T15:59:00Z"), Rules.dueAt(a, ZoneId.of("Asia/Shanghai")));
    a.put("dueTime", "08:00");
    assertEquals(
        Instant.parse("2026-10-12T23:00:00Z"), Rules.dueReminder(a, ZoneId.of("Asia/Shanghai")));
    a.put("dueTime", "00:30");
    assertNull(Rules.dueReminder(a, ZoneId.of("Asia/Shanghai")));
  }

  @Test
  public void timerUsesAnchorsAcrossBackgroundAndClampsZero() {
    assertEquals(1480, Rules.timerSeconds(1000, 1500, 21000, false));
    assertEquals(0, Rules.timerSeconds(1000, 1500, 99999999, false));
    assertEquals(120, Rules.timerSeconds(1000, 100, 21000, true));
    assertEquals("2:00:01", Rules.clock(7201));
    assertEquals("0:00", Rules.clock(-1));
  }

  @Test
  public void inheritedTermRangeAndParity() {
    Row year = row("term", "Year").put("startsOn", "2026-09-01").put("endsOn", "2026-12-31");
    Row term = row("term", "Semester").put("parentId", year.id);
    Row lesson =
        row("lesson", "Monday").put("termId", term.id).put("weekday", 1).put("weekParity", 1);
    List<Row> rows = Arrays.asList(year, term, lesson);
    assertTrue(Rules.lessonOn(lesson, rows, LocalDate.of(2026, 9, 14)));
    assertFalse(Rules.lessonOn(lesson, rows, LocalDate.of(2026, 9, 7)));
    assertFalse(Rules.lessonOn(lesson, rows, LocalDate.of(2027, 1, 4)));
  }

  @Test
  public void commitmentEveryTwoWeeksAndOneOff() {
    Row c =
        row("commitment", "Training")
            .put("startsOn", "2026-10-05")
            .put("weekday", 1)
            .put("intervalWeeks", 2);
    assertTrue(Rules.commitmentOn(c, LocalDate.of(2026, 10, 19)));
    assertFalse(Rules.commitmentOn(c, LocalDate.of(2026, 10, 12)));
    c.put("weekday", null);
    assertFalse(Rules.commitmentOn(c, LocalDate.of(2026, 10, 19)));
    assertTrue(Rules.commitmentOn(c, LocalDate.of(2026, 10, 5)));
  }

  @Test
  public void freeWindowsMergeOverlapsAndRespectStudyUntil() {
    List<int[]> result =
        Rules.freeWindows(
            new ArrayList<>(
                Arrays.asList(new int[] {540, 600}, new int[] {570, 660}, new int[] {1200, 1300})),
            480,
            1260);
    assertEquals(2, result.size());
    assertArrayEquals(new int[] {480, 540}, result.get(0));
    assertArrayEquals(new int[] {660, 1200}, result.get(1));
  }

  @Test
  public void gpaUsesPerGradeWeightsBeforeCreditWeights() {
    Row c1 = row("course", "A").put("credits", 2), c2 = row("course", "B").put("credits", 1);
    List<Row> rows =
        Arrays.asList(
            c1,
            c2,
            row("grade", "").put("subjectId", c1.id).put("grade", 5).put("weight", 1),
            row("grade", "").put("subjectId", c1.id).put("grade", 7).put("weight", 3),
            row("grade", "").put("subjectId", c2.id).put("grade", 4).put("weight", 1));
    assertEquals(5.67, Rules.gpa(rows, "ib", false), 0.001);
    c1.put("archivedAt", "2026-01-01");
    assertEquals(4, Rules.gpa(rows, "ib", false), 0.001);
    assertEquals(6.5, Rules.gpa(rows, "ib", true), 0.001);
  }

  @Test
  public void zeroWeightsFallbackAndPercentLadder() {
    assertEquals(
        6,
        Rules.effectiveGrade(
            Arrays.asList(
                row("grade", "").put("grade", 5).put("weight", 0),
                row("grade", "").put("grade", 7).put("weight", 0))),
        0);
    assertEquals(4, Rules.points(93), 0);
    assertEquals(3.7, Rules.points(90), 0);
    assertEquals(0, Rules.points(59), 0);
  }

  @Test
  public void mergeComparesInstantsAndDeleteWins() {
    Row local = row("note", "L"), remote = row("note", "R");
    local.updatedAt = "2026-10-13T01:00:00.500Z";
    remote.updatedAt = "2026-10-13T01:00:00.400+00:00";
    assertFalse(Rules.remoteWins(local, remote));
    remote.deletedAt = "2026-10-13T00:00:00Z";
    assertTrue(Rules.remoteWins(local, remote));
    local.deletedAt = remote.deletedAt;
    assertFalse(Rules.remoteWins(local, remote));
    remote.deletedAt = null;
    remote.updatedAt = "2026-10-13T01:00:00.600Z";
    assertTrue(Rules.remoteWins(local, remote));
  }

  @Test
  public void attendanceCloudIdentityUsesNaturalKey() throws Exception {
    Row a =
        row("attendance", "")
            .put("timetableEntryId", "entry")
            .put("date", "2026-10-13")
            .put("status", "present");
    a.updatedAt = "2026-10-13T01:00:00Z";
    JSONObject payload = CloudSchema.toCloud(a, "owner");
    assertFalse(payload.has("id"));
    assertEquals("entry", payload.getString("timetable_entry_id"));
  }

  @Test
  public void courseAndDateColumnMappingsRoundTrip() throws Exception {
    Row a =
        row("assignment", "Report")
            .put("courseId", "course")
            .put("dueDate", "2026-10-15")
            .put("dueTime", "08:00");
    a.updatedAt = "2026-10-13T01:00:00Z";
    JSONObject payload = CloudSchema.toCloud(a, "owner");
    assertEquals("course", payload.getString("subject_id"));
    Row incoming = CloudSchema.fromCloud("assignment", payload.put("due_time", "08:00:00"));
    assertEquals("08:00", incoming.s("dueTime"));
    assertEquals(a.id, incoming.id);
  }

  @Test
  public void calendarParserSkipsCancelledAndPreservesFloatingDates() throws Exception {
    String text =
        "BEGIN:VCALENDAR\n"
            + "BEGIN:VEVENT\n"
            + "UID:one\n"
            + "DTSTART;VALUE=DATE:20261013\n"
            + "SUMMARY:Exam\n"
            + "RRULE:FREQ=WEEKLY\n"
            + "END:VEVENT\n"
            + "BEGIN:VEVENT\n"
            + "UID:two\n"
            + "DTSTART:20261014T090000\n"
            + "STATUS:CANCELLED\n"
            + "END:VEVENT\n"
            + "END:VCALENDAR";
    List<CalendarFeed.Event> events = CalendarFeed.parse(text);
    assertEquals(1, events.size());
    assertEquals("2026-10-13", events.get(0).date());
    assertTrue(events.get(0).repeating());
    assertEquals(CalendarFeed.idForUid("one"), CalendarFeed.idForUid("one"));
    assertNotEquals(CalendarFeed.idForUid("one"), CalendarFeed.idForUid("two"));
  }

  @Test
  public void pearsonNeedsThreeNonConstantPoints() {
    assertNull(Statistics.pearson(new double[] {1, 2}, new double[] {1, 2}));
    assertNull(Statistics.pearson(new double[] {1, 1, 1}, new double[] {1, 2, 3}));
    assertEquals(1, Statistics.pearson(new double[] {1, 2, 3}, new double[] {2, 4, 6}), 0.000001);
  }

  @Test
  public void backupImportRejectsDuplicateIdentityAndBadDates() throws Exception {
    JSONObject r =
        new JSONObject().put("id", "same").put("kind", "assignment").put("dueDate", "2026-10-13");
    JSONObject root =
        new JSONObject().put("records", new JSONArray().put(r).put(new JSONObject(r.toString())));
    try {
      Backup.parse(root);
      fail("Duplicate identity accepted");
    } catch (java.io.IOException expected) {
    }
    JSONObject bad =
        new JSONObject()
            .put(
                "records",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("id", "x")
                            .put("kind", "exam")
                            .put("dueDate", "2026-13-90")));
    try {
      Backup.parse(bad);
      fail("Invalid date accepted");
    } catch (java.time.format.DateTimeParseException expected) {
    }
  }

  @Test
  public void formRequiresValidRelationshipAndTimeRange() {
    Row a = row("assignment", "Essay").put("courseId", "missing").put("dueDate", "2026-10-13");
    try {
      FormSpec.validate(a, new ArrayList<>(), "ib", 0, 10);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    Row c =
        row("commitment", "Club")
            .put("startsOn", "2026-10-13")
            .put("startTime", "18:00")
            .put("endTime", "17:00");
    try {
      FormSpec.validate(c, new ArrayList<>(), "ib", 0, 10);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void repeatUsesMostSpecificTermAndSixtyOccurrenceCap() {
    Row year = row("term", "Year").put("startsOn", "2026-01-01").put("endsOn", "2026-12-31");
    Row semester =
        row("term", "Semester")
            .put("parentId", year.id)
            .put("startsOn", "2026-09-01")
            .put("endsOn", "2026-10-31");
    assertEquals(
        LocalDate.of(2026, 10, 31),
        Rules.repeatHorizon(LocalDate.of(2026, 10, 13), Arrays.asList(year, semester)));
    assertEquals(
        13,
        Rules.repeatDates(LocalDate.of(2026, 10, 13), 1, LocalDate.of(2026, 10, 13).plusWeeks(12))
            .size());
    assertEquals(
        60, Rules.repeatDates(LocalDate.of(2026, 10, 13), 1, LocalDate.of(2126, 10, 13)).size());
  }

  @Test
  public void timestampsKeepTheirInstantAcrossTimezoneAndDstOverlap() throws Exception {
    java.util.TimeZone previous = java.util.TimeZone.getDefault();
    try {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
      assertEquals(Instant.parse("2026-11-01T06:30:00Z"), Times.instant("2026-11-01T06:30:00Z"));
      JSONObject cloud =
          new JSONObject()
              .put("id", "one")
              .put("started_at", "2026-11-01T06:30:00+00:00")
              .put("updated_at", "2026-11-01T06:31:00Z");
      Row session = CloudSchema.fromCloud("session", cloud);
      assertEquals("2026-11-01T06:30:00Z", session.s("startedAt"));
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
      assertEquals(Instant.parse("2026-11-01T06:30:00Z"), Times.instant(session.s("startedAt")));
      assertEquals(LocalDateTime.of(2026, 11, 1, 14, 30), Times.local(session.s("startedAt")));
    } finally {
      java.util.TimeZone.setDefault(previous);
    }
  }

  @Test
  public void corruptBackupPlanningPreferencesAreRejectedBeforeImport() throws Exception {
    try {
      Backup.validateSettings(new JSONObject().put("studyUntil", "NaN"));
      fail();
    } catch (NumberFormatException expected) {
    }
    try {
      Backup.validateSettings(new JSONObject().put("gradeMode", "unknown"));
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @org.junit.Test
  public void editingOtherFieldsPreservesTheLaterDstOverlapInstant() {
    java.util.TimeZone previous = java.util.TimeZone.getDefault();
    try {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
      assertEquals(
          "2026-11-01T06:30:00Z", Times.editedInstant("2026-11-01T06:30:00Z", "2026-11-01T01:30"));
      assertEquals(
          "2026-11-01T07:30:00Z", Times.editedInstant("2026-11-01T06:30:00Z", "2026-11-01T02:30"));
    } finally {
      java.util.TimeZone.setDefault(previous);
    }
  }
}
