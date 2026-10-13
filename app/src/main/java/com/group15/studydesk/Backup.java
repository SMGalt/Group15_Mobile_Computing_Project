package com.group15.studydesk;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/**
 * Portable native backup, plus import of baseline JSON exports. Never exports tokens or cloud keys.
 */
public final class Backup {
  static final String[] SETTINGS = {
    "mode",
    "widgetPalette",
    "language",
    "gradeMode",
    "scaleMin",
    "scaleMax",
    "scalePass",
    "scaleDirection",
    "weekStart",
    "dayStart",
    "dueWindow",
    "studyUntil",
    "planLead",
    "planStart",
    "notifications",
    "focusChip",
    "focusPin"
  };

  public static void exportTo(Context c, Repository repo, Uri uri) throws Exception {
    JSONObject root =
        new JSONObject()
            .put("format", "StudyDeskJava")
            .put("schemaVersion", 1)
            .put("baseline", "addd6fdcf5a69c7d6f23e8daf13e197197a7a998")
            .put("exportedAt", java.time.Instant.now().toString());
    JSONArray records = new JSONArray();
    Set<String> files = new HashSet<>();
    for (Row r : repo.all()) {
      JSONObject row = r.json();
      row.put("id", r.id)
          .put("kind", r.kind)
          .put("updatedAt", r.updatedAt)
          .put("deletedAt", r.deletedAt == null ? JSONObject.NULL : r.deletedAt);
      records.put(row);
      if (!r.s("localFile").isEmpty()) files.add(r.s("localFile"));
    }
    root.put("records", records);
    CloudClient cloud = new CloudClient(c);
    if (cloud.configured() && cloud.session() != null) {
      JSONObject server = new JSONObject();
      for (String table : new String[] {"feedback", "client_errors"})
        try {
          server.put(table, cloud.pull(table, repo.owner()));
        } catch (Exception e) {
          server.put(table, new JSONObject().put("error", e.getMessage()));
        }
      root.put("server", server);
    }
    JSONObject settings = new JSONObject();
    for (String key : SETTINGS) {
      Object value = c.getSharedPreferences("settings", 0).getAll().get(key);
      if (value != null) settings.put(key, value);
    }
    root.put("settings", settings);
    try (ZipOutputStream out = new ZipOutputStream(c.getContentResolver().openOutputStream(uri))) {
      out.putNextEntry(new ZipEntry("records.json"));
      out.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.closeEntry();
      for (String name : files) {
        if (!name.matches("[A-Za-z0-9-]+")) continue;
        File f = new File(new File(c.getFilesDir(), "attachments"), name);
        if (f.isFile()) {
          out.putNextEntry(new ZipEntry("attachments/" + name));
          try (InputStream in = new FileInputStream(f)) {
            IOUtil.copy(in, out);
          }
          out.closeEntry();
        }
      }
    }
  }

  public static void importFrom(Context c, Repository repo, Uri uri) throws Exception {
    File stage = new File(c.getCacheDir(), "import-" + UUID.randomUUID());
    stage.mkdirs();
    JSONObject root;
    try (BufferedInputStream in =
        new BufferedInputStream(c.getContentResolver().openInputStream(uri))) {
      in.mark(4);
      int first = in.read(), second = in.read();
      in.reset();
      if (first == 'P' && second == 'K') {
        String json = null;
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
          ZipEntry entry;
          while ((entry = zip.getNextEntry()) != null) {
            if (++count > 500) throw new IOException("Too many backup files");
            String name = entry.getName();
            if (!name.equals("records.json") && !name.matches("attachments/[A-Za-z0-9-]+"))
              throw new IOException("Unsupported backup entry");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = zip.read(b)) != -1) {
              total += n;
              if (total > 100L * 1024 * 1024 || data.size() + n > 11 * 1024 * 1024)
                throw new IOException("Backup exceeds import limit");
              data.write(b, 0, n);
            }
            if (name.equals("records.json")) json = data.toString("UTF-8");
            else {
              File f = new File(stage, name.substring("attachments/".length()));
              try (OutputStream out = new FileOutputStream(f)) {
                data.writeTo(out);
              }
            }
          }
        }
        if (json == null) throw new IOException("Missing records.json");
        root = new JSONObject(json);
      } else {
        byte[] data = IOUtil.read(in);
        if (data.length > 11 * 1024 * 1024)
          throw new IOException("JSON export exceeds import limit");
        root = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    List<Row> imported = parse(root);
    validateSettings(root.optJSONObject("settings"));
    repo.db.runInTransaction(
        () -> {
          for (Row row : imported) {
            Row existing = repo.find(row.kind, row.id);
            if (existing == null || Rules.remoteWins(existing, row)) repo.save(row);
          }
        });
    File dir = new File(c.getFilesDir(), "attachments");
    dir.mkdirs();
    File[] files = stage.listFiles();
    if (files != null)
      for (File f : files) {
        File target = new File(dir, f.getName());
        try (InputStream in = new FileInputStream(f);
            OutputStream out = new FileOutputStream(target)) {
          IOUtil.copy(in, out);
        }
        f.delete();
      }
    stage.delete();
    JSONObject settings = root.optJSONObject("settings");
    if (settings != null) {
      SharedPreferences.Editor e = c.getSharedPreferences("settings", 0).edit();
      for (String key : SETTINGS) {
        Object value = settings.opt(key);
        if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else if (value != null) e.putString(key, String.valueOf(value));
      }
      e.commit();
    }
  }

  static void validateSettings(JSONObject settings) throws Exception {
    if (settings == null) return;
    String[][] enums = {
      {"mode", "light", "dark", "black"},
      {"widgetPalette", "auto", "light", "dark", "black"},
      {"language", "en", "zh"},
      {"gradeMode", "ib", "us", "custom"},
      {"weekStart", "0", "1"},
      {"dueWindow", "smart", "3", "7", "14", "30", "all"},
      {"scaleDirection", "up", "down"}
    };
    for (String[] values : enums)
      if (settings.has(values[0])
          && !java.util.Arrays.asList(values)
              .subList(1, values.length)
              .contains(settings.optString(values[0])))
        throw new IOException("Invalid backup setting: " + values[0]);
    for (String key : new String[] {"dayStart", "studyUntil", "planLead"})
      if (settings.has(key)) {
        int n = Integer.parseInt(settings.getString(key));
        if (n < 0 || n > (key.equals("dayStart") ? 6 : 1440))
          throw new IOException("Invalid backup setting: " + key);
      }
    if (settings.has("scaleMin") || settings.has("scaleMax") || settings.has("scalePass")) {
      double min = Double.parseDouble(settings.optString("scaleMin", "0")),
          max = Double.parseDouble(settings.optString("scaleMax", "10")),
          pass = Double.parseDouble(settings.optString("scalePass", "5"));
      if (!Double.isFinite(min)
          || !Double.isFinite(max)
          || !Double.isFinite(pass)
          || min >= max
          || min < -99999
          || max > 99999
          || pass < min
          || pass > max) throw new IOException("Invalid custom scale in backup");
    }
  }

  public static List<Row> parse(JSONObject root) throws Exception {
    List<Row> rows = new ArrayList<>();
    JSONArray records = root.optJSONArray("records");
    if (records == null) {
      records = new JSONArray();
      Map<String, String> names = new LinkedHashMap<>();
      names.put("courses", "course");
      names.put("assignments", "assignment");
      names.put("exams", "exam");
      names.put("actions", "action");
      names.put("grades", "grade");
      names.put("studySessions", "session");
      names.put("academicTerms", "term");
      names.put("timetableEntries", "lesson");
      names.put("plannedSessions", "planned");
      names.put("commitments", "commitment");
      names.put("notes", "note");
      names.put("attendance", "attendance");
      for (String name : names.keySet()) {
        Object collection = root.opt(name);
        if (collection instanceof JSONObject) {
          JSONObject object = (JSONObject) collection;
          JSONArray array = new JSONArray();
          for (Iterator<String> keys = object.keys(); keys.hasNext(); )
            array.put(object.get(keys.next()));
          collection = array;
        }
        if (collection instanceof JSONArray)
          for (int i = 0; i < ((JSONArray) collection).length(); i++)
            records.put(
                new JSONObject(((JSONArray) collection).getJSONObject(i).toString())
                    .put("kind", names.get(name)));
      }
    }
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < records.length(); i++) {
      JSONObject data = records.getJSONObject(i);
      String kind = data.getString("kind"), id = data.getString("id");
      if (!CloudSchema.TABLES.containsKey(kind) && !kind.equals("feed"))
        throw new IOException("Unknown record kind");
      if (id.isEmpty() || !ids.add(kind + ":" + id))
        throw new IOException("Missing or duplicate identity");
      Row r = new Row(kind);
      r.id = id;
      r.updatedAt = data.optString("updatedAt", "");
      r.deletedAt = data.isNull("deletedAt") ? null : data.optString("deletedAt", null);
      data.remove("kind");
      data.remove("id");
      data.remove("updatedAt");
      data.remove("deletedAt");
      r.payload = data.toString();
      for (String field : new String[] {"dueDate", "date", "startsOn", "endsOn", "lessonDate"})
        if (!r.s(field).isEmpty()) java.time.LocalDate.parse(r.s(field));
      rows.add(r);
    }
    if (rows.isEmpty()
        && !root.optString("format").equals("StudyDeskJava")
        && !root.optString("application").equals("StudyDesk"))
      throw new IOException("No study records in this backup");
    return rows;
  }
}
