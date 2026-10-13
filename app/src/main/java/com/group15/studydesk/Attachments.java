package com.group15.studydesk;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

public final class Attachments {
  public static void show(MainActivity a, Repository repo, Row parent) {
    List<Row> files = new ArrayList<>();
    for (Row r : Rules.kind(a.rows, parent.kind.equals("note") ? "noteAttachment" : "attachment"))
      if (r.s(parent.kind.equals("note") ? "entryId" : "assignmentId").equals(parent.id))
        files.add(r);
    String[] names = files.stream().map(r -> r.s("fileName")).toArray(String[]::new);
    new AlertDialog.Builder(a)
        .setTitle("Attachments")
        .setItems(
            names,
            (d, w) ->
                new AlertDialog.Builder(a)
                    .setTitle(names[w])
                    .setItems(
                        new String[] {"Open", "Delete"},
                        (x, y) -> {
                          if (y == 0) open(a, repo, files.get(w));
                          else repo.write(() -> repo.delete(files.get(w)));
                        })
                    .show())
        .setPositiveButton(
            "Add file",
            (d, w) -> {
              a.pendingAttachment = parent.copy();
              a.startActivityForResult(
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("*/*")
                      .addCategory(Intent.CATEGORY_OPENABLE),
                  7301);
            })
        .setNegativeButton("Close", null)
        .show();
  }

  public static void importFile(MainActivity a, Uri uri, Row parent) {
    new Repository(a)
        .write(
            () -> {
              try {
                String name = "attachment";
                long size = 0;
                try (Cursor cur = a.getContentResolver().query(uri, null, null, null, null)) {
                  if (cur != null && cur.moveToFirst()) {
                    int at = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME),
                        sz = cur.getColumnIndex(OpenableColumns.SIZE);
                    if (at >= 0) name = cur.getString(at);
                    if (sz >= 0) size = cur.getLong(sz);
                  }
                }
                if (size > 10 * 1024 * 1024) throw new IOException("Files must be at most 10 MB");
                Repository repo = new Repository(a);
                Row r =
                    new Row(parent.kind.equals("note") ? "noteAttachment" : "attachment")
                        .put(parent.kind.equals("note") ? "entryId" : "assignmentId", parent.id)
                        .put("fileName", name)
                        .put("mimeType", a.getContentResolver().getType(uri));
                File dir = new File(a.getFilesDir(), "attachments");
                dir.mkdirs();
                File target = new File(dir, r.id);
                try (InputStream in = a.getContentResolver().openInputStream(uri);
                    OutputStream out = new FileOutputStream(target)) {
                  byte[] bytes = new byte[8192];
                  int n, total = 0;
                  while ((n = in.read(bytes)) >= 0) {
                    total += n;
                    if (total > 10 * 1024 * 1024) {
                      target.delete();
                      throw new IOException("Files must be at most 10 MB");
                    }
                    out.write(bytes, 0, n);
                  }
                  size = total;
                }
                r.put("localFile", target.getName()).put("sizeBytes", size);
                repo.save(r);
                if (parent.kind.equals("note") && r.s("mimeType").startsWith("image/")) {
                  Row note = repo.find("note", parent.id);
                  repo.save(note.put("content", note.s("content") + "\n![" + r.id + "] " + name));
                }
                a.runOnUiThread(() -> a.message("Attachment saved"));
              } catch (Exception e) {
                a.runOnUiThread(() -> a.message(e.getMessage()));
              }
            });
  }

  public static void upload(Context c, CloudClient client, Row r, String owner) throws Exception {
    File local = new File(new File(c.getFilesDir(), "attachments"), r.s("localFile"));
    if (!local.isFile()) throw new IOException("Attachment file is missing");
    String path = owner + "/" + r.id + "/" + r.s("fileName").replaceAll("[^A-Za-z0-9._-]", "_");
    String bucket = r.kind.equals("attachment") ? "assignment-attachments" : "notebook";
    Request request =
        new Request.Builder()
            .url(client.url + "/storage/v1/object/" + bucket + "/" + path)
            .header("apikey", client.key)
            .header("Authorization", "Bearer " + client.token())
            .header("x-upsert", "true")
            .post(
                RequestBody.create(
                    local,
                    MediaType.parse(
                        r.s("mimeType").isEmpty() ? "application/octet-stream" : r.s("mimeType"))))
            .build();
    try (Response response = client.http.newCall(request).execute()) {
      if (!response.isSuccessful())
        throw new IOException("Attachment upload failed (HTTP " + response.code() + ")");
    }
    r.put("storagePath", path);
  }

  public static void removeCloud(CloudClient client, Row r) throws Exception {
    if (r.s("storagePath").isEmpty()) return;
    String bucket = r.kind.equals("attachment") ? "assignment-attachments" : "notebook";
    client.request(
        "DELETE",
        "/storage/v1/object/" + bucket,
        new JSONObject().put("prefixes", new JSONArray().put(r.s("storagePath"))).toString(),
        client.token(),
        null);
  }

  public static void open(MainActivity a, Repository repo, Row r) {
    repo.write(
        () -> {
          try {
            File dir = new File(a.getFilesDir(), "attachments");
            dir.mkdirs();
            File file = new File(dir, r.s("localFile").isEmpty() ? r.id : r.s("localFile"));
            if (!file.isFile()) {
              CloudClient c = new CloudClient(a);
              String bucket = r.kind.equals("attachment") ? "assignment-attachments" : "notebook";
              Request req =
                  new Request.Builder()
                      .url(
                          c.url
                              + "/storage/v1/object/authenticated/"
                              + bucket
                              + "/"
                              + r.s("storagePath"))
                      .header("apikey", c.key)
                      .header("Authorization", "Bearer " + c.token())
                      .build();
              try (Response response = c.http.newCall(req).execute()) {
                if (!response.isSuccessful() || response.body() == null)
                  throw new IOException("Could not download attachment");
                try (OutputStream out = new FileOutputStream(file)) {
                  IOUtil.copy(response.body().byteStream(), out);
                }
              }
            }
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", file);
            a.runOnUiThread(
                () -> {
                  try {
                    a.startActivity(
                        new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(
                                uri,
                                r.s("mimeType").isEmpty()
                                    ? "application/octet-stream"
                                    : r.s("mimeType"))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                  } catch (Exception e) {
                    a.message("No app installed to open this file");
                  }
                });
          } catch (Exception e) {
            a.runOnUiThread(() -> a.message(e.getMessage()));
          }
        });
  }
}
