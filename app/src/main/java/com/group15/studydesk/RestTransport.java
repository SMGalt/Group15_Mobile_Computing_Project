package com.group15.studydesk;

import java.io.*;
import okhttp3.*;
import org.json.*;

/** Testable HTTP transport shared by the production client and local mock-server contract tests. */
public final class RestTransport {
  public final OkHttpClient http;

  public RestTransport(OkHttpClient http) {
    this.http = http;
  }

  public String request(
      String base,
      String key,
      String method,
      String path,
      String body,
      String bearer,
      String preference)
      throws IOException {
    Request.Builder b = new Request.Builder().url(base + path).header("apikey", key);
    if (bearer != null && !bearer.isEmpty()) b.header("Authorization", "Bearer " + bearer);
    if (preference != null) b.header("Prefer", preference);
    b.method(method, body == null ? null : RequestBody.create(body, CloudClient.JSON));
    try (Response r = http.newCall(b.build()).execute()) {
      String response = r.body() == null ? "" : r.body().string();
      if (!r.isSuccessful()) throw new IOException("Cloud request failed (HTTP " + r.code() + ")");
      return response;
    }
  }

  public JSONArray pull(String base, String key, String bearer, String table, String owner)
      throws Exception {
    JSONArray out = new JSONArray();
    for (int offset = 0; ; offset += 1000) {
      String query =
          "/rest/v1/"
              + table
              + "?select=*&"
              + (table.equals("profiles") ? "id" : "user_id")
              + "=eq."
              + java.net.URLEncoder.encode(owner, "UTF-8")
              + "&order=id.asc&offset="
              + offset
              + "&limit=1000";
      JSONArray page = new JSONArray(request(base, key, "GET", query, null, bearer, null));
      for (int i = 0; i < page.length(); i++) out.put(page.get(i));
      if (page.length() < 1000) return out;
    }
  }
}
