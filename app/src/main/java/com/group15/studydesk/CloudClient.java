package com.group15.studydesk;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

public class CloudClient {
  static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  public final OkHttpClient http;
  final Context c;
  final SecureStore vault;
  final String url, key;

  public CloudClient(Context context) {
    c = context.getApplicationContext();
    vault = new SecureStore(c);
    url = c.getSharedPreferences("settings", 0).getString("cloudUrl", "").replaceAll("/+$", "");
    key = vault.get("cloudKey");
    http =
        new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();
  }

  public boolean configured() {
    return url.startsWith("https://")
        && key != null
        && !key.isEmpty()
        && !url.contains("hkktorzhaqnfqsnlstda");
  }

  public String request(String method, String path, String body, String bearer, String preference)
      throws IOException {
    if (!configured())
      throw new IOException("Configure your own Supabase URL and publishable key in Settings");
    return new RestTransport(http).request(url, key, method, path, body, bearer, preference);
  }

  public JSONObject session() {
    try {
      String raw = vault.get("session");
      return raw == null ? null : new JSONObject(raw);
    } catch (Exception e) {
      return null;
    }
  }

  public synchronized String token() throws Exception {
    JSONObject s = session();
    if (s == null) throw new IOException("Sign in to sync");
    if (s.optLong("expires_at", 0) < System.currentTimeMillis() / 1000 + 60) {
      s =
          new JSONObject(
              request(
                  "POST",
                  "/auth/v1/token?grant_type=refresh_token",
                  new JSONObject().put("refresh_token", s.getString("refresh_token")).toString(),
                  null,
                  null));
      storeSession(s);
    }
    return s.getString("access_token");
  }

  public void storeSession(JSONObject s) throws JSONException {
    if (!s.has("access_token")) return;
    s.put("expires_at", System.currentTimeMillis() / 1000 + s.optLong("expires_in", 3600));
    vault.put("session", s.toString());
    c.getSharedPreferences("session", 0)
        .edit()
        .putString("owner", s.getJSONObject("user").getString("id"))
        .putString("email", s.getJSONObject("user").optString("email"))
        .commit();
  }

  public JSONObject login(String email, String password) throws Exception {
    JSONObject s =
        new JSONObject(
            request(
                "POST",
                "/auth/v1/token?grant_type=password",
                new JSONObject().put("email", email).put("password", password).toString(),
                null,
                null));
    storeSession(s);
    return s;
  }

  public boolean signup(String email, String password) throws Exception {
    String v = verifier();
    vault.put("pkceType", "email");
    String body =
        new JSONObject()
            .put("email", email)
            .put("password", password)
            .put("code_challenge", challenge(v))
            .put("code_challenge_method", "s256")
            .toString();
    JSONObject s =
        new JSONObject(
            request(
                "POST",
                "/auth/v1/signup?redirect_to=" + Uri.encode("studydeskjava://login-callback"),
                body,
                null,
                null));
    storeSession(s);
    return s.has("access_token");
  }

  private String verifier() {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String v = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    vault.put("pkce", v);
    vault.put("pkceTime", String.valueOf(System.currentTimeMillis()));
    return v;
  }

  private static String challenge(String v) throws Exception {
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.US_ASCII)));
  }

  public Uri google() throws Exception {
    if (!configured()) throw new IOException("Configure your own cloud project first");
    String v = verifier();
    vault.put("pkceType", "oauth");
    return Uri.parse(url + "/auth/v1/authorize")
        .buildUpon()
        .appendQueryParameter("provider", "google")
        .appendQueryParameter("redirect_to", "studydeskjava://login-callback")
        .appendQueryParameter("code_challenge", challenge(v))
        .appendQueryParameter("code_challenge_method", "s256")
        .build();
  }

  public void callback(Uri uri) throws Exception {
    String v = vault.get("pkce"),
        time = vault.get("pkceTime"),
        code = uri.getQueryParameter("code");
    if (v == null
        || time == null
        || code == null
        || System.currentTimeMillis() - Long.parseLong(time)
            > ("email".equals(vault.get("pkceType")) ? 24 * 60 * 60 * 1000L : 10 * 60 * 1000L))
      throw new IOException("Authorization expired. Start sign-in again.");
    JSONObject s =
        new JSONObject(
            request(
                "POST",
                "/auth/v1/token?grant_type=pkce",
                new JSONObject().put("auth_code", code).put("code_verifier", v).toString(),
                null,
                null));
    storeSession(s);
    vault.put("pkce", null);
    vault.put("pkceTime", null);
  }

  public void recover(String email) throws Exception {
    String v = verifier();
    request(
        "POST",
        "/auth/v1/recover?redirect_to=" + Uri.encode("studydeskjava://login-callback"),
        new JSONObject()
            .put("email", email)
            .put("code_challenge", challenge(v))
            .put("code_challenge_method", "s256")
            .toString(),
        null,
        null);
  }

  public void updateUser(JSONObject fields) throws Exception {
    request("PUT", "/auth/v1/user", fields.toString(), token(), null);
  }

  public void logout() throws Exception {
    try {
      if (session() != null) request("POST", "/auth/v1/logout?scope=local", "{}", token(), null);
    } finally {
      vault.put("session", null);
      c.getSharedPreferences("session", 0).edit().clear().commit();
    }
  }

  public JSONArray pull(String table, String owner) throws Exception {
    return new RestTransport(http).pull(url, key, token(), table, owner);
  }

  public void push(Row r, String owner) throws Exception {
    JSONObject data = CloudSchema.toCloud(r, owner);
    String conflict = r.kind.equals("attendance") ? "user_id,timetable_entry_id,date" : "id";
    String path = "/rest/v1/" + CloudSchema.TABLES.get(r.kind).name() + "?on_conflict=" + conflict;
    request("POST", path, data.toString(), token(), "resolution=merge-duplicates,return=minimal");
  }
}
