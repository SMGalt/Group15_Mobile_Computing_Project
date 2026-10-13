package com.group15.studydesk;

import android.content.*;
import java.time.*;

public final class Appearance {
  public static boolean entitled(Context c) {
    SharedPreferences p = c.getSharedPreferences("appearance", 0);
    String owner = new Repository(c).owner();
    if (!owner.equals(p.getString("owner", ""))) return false;
    try {
      return Instant.parse(p.getString("expires", "")).isAfter(Instant.now());
    } catch (Exception e) {
      return false;
    }
  }

  public static String character(Context c) {
    String preferred = c.getSharedPreferences("settings", 0).getString("character", "free");
    return !preferred.equals("free") && !entitled(c) ? "free" : preferred;
  }

  public static boolean dark(Context c) {
    String character = character(c);
    return character.equals("slate")
        || !character.equals("stacks")
            && !c.getSharedPreferences("settings", 0).getString("mode", "light").equals("light");
  }

  public static void refresh(Context c) throws Exception {
    CloudClient cloud = new CloudClient(c);
    String owner = new Repository(c).owner();
    org.json.JSONArray result =
        new org.json.JSONArray(
            cloud.request(
                "GET",
                "/rest/v1/supporter_entitlements?select=expires_at&user_id=eq." + owner,
                null,
                cloud.token(),
                null));
    c.getSharedPreferences("appearance", 0)
        .edit()
        .putString("owner", owner)
        .putString(
            "expires",
            result.length() > 0 ? result.getJSONObject(0).optString("expires_at", "") : "")
        .commit();
  }

  public static int color(Context c, int attribute) {
    android.util.TypedValue value = new android.util.TypedValue();
    c.getTheme().resolveAttribute(attribute, value, true);
    return value.data;
  }
}
