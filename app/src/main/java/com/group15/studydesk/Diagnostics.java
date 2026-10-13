package com.group15.studydesk;

import android.content.*;
import org.json.*;

/**
 * Opt-in only; errors stay in a device-private queue until sent to the user's configured project.
 */
public final class Diagnostics {
  public static void install(Context c) {
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          if (c.getSharedPreferences("settings", 0).getBoolean("errorReports", false)) {
            try {
              String message =
                  error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
              message =
                  message
                      .replaceAll(
                          "(?i)(bearer|token|password|apikey)[=: ]+[^\\s]+", "$1=[redacted]")
                      .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "[email]");
              JSONObject report =
                  new JSONObject()
                      .put("id", java.util.UUID.randomUUID().toString())
                      .put("app", "StudyDesk")
                      .put("app_version", BuildConfig.VERSION_NAME)
                      .put("platform", "android")
                      .put("error_name", error.getClass().getSimpleName())
                      .put("message", message.substring(0, Math.min(300, message.length())))
                      .put("screen", "native")
                      .put("os_version", android.os.Build.VERSION.RELEASE);
              c.getSharedPreferences("diagnostics", 0)
                  .edit()
                  .putString("pending", report.toString())
                  .commit();
            } catch (Exception ignored) {
            }
          }
          if (previous != null) previous.uncaughtException(thread, error);
        });
  }

  public static void sendPending(Context c, CloudClient cloud, String owner) {
    if (!c.getSharedPreferences("settings", 0).getBoolean("errorReports", false)) return;
    String raw = c.getSharedPreferences("diagnostics", 0).getString("pending", null);
    if (raw == null) return;
    try {
      JSONObject report = new JSONObject(raw).put("user_id", owner);
      cloud.request(
          "POST", "/rest/v1/client_errors", report.toString(), cloud.token(), "return=minimal");
      c.getSharedPreferences("diagnostics", 0).edit().remove("pending").commit();
    } catch (Exception ignored) {
    }
  }
}
