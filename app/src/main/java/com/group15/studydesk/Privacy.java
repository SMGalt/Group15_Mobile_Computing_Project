package com.group15.studydesk;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

public final class Privacy {
  public static final String VERSION = "2026-10-native1";

  public static void check(Context c) {
    android.content.SharedPreferences p = c.getSharedPreferences("settings", 0);
    String seen = p.getString("privacySeen", null);
    if (seen == null) p.edit().putString("privacySeen", VERSION).apply();
    else if (!VERSION.equals(seen)) show(c);
  }

  public static void show(Context c) {
    String text;
    try {
      text =
          new String(
              IOUtil.read(c.getAssets().open("PRIVACY.txt")),
              java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      text = "Guest records stay on this device. Configure your own project for cloud features.";
    }
    new AlertDialog.Builder(c)
        .setTitle("Data and privacy")
        .setMessage(text)
        .setPositiveButton(
            "Close",
            (d, w) ->
                c.getSharedPreferences("settings", 0)
                    .edit()
                    .putString("privacySeen", VERSION)
                    .apply())
        .setNeutralButton(
            "Original policy",
            (d, w) ->
                c.startActivity(
                    new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://limecore.dev/privacy"))))
        .show();
  }
}
