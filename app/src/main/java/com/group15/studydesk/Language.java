package com.group15.studydesk;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Uses the baseline's MIT locale catalogue; native-specific labels fall back to English. */
public final class Language {
  private static final Map<String, Map<String, String>> maps = new HashMap<>();
  private static final Map<String, Map<String, String>> lowerMaps = new HashMap<>();

  private static void flatten(JSONObject j, String path, Map<String, String> out) {
    for (Iterator<String> it = j.keys(); it.hasNext(); ) {
      String k = it.next();
      Object v = j.opt(k);
      if (v instanceof JSONObject) flatten((JSONObject) v, path + k + ".", out);
      else if (v instanceof String) out.put(path + k, (String) v);
    }
  }

  /** Catalogue keys that name navigation chrome, which outranks other senses in a lookup. */
  private static boolean chrome(String key) {
    return key.startsWith("nav.") || key.startsWith("topbar.");
  }

  public static synchronized String translate(Context c, String text) {
    if (text == null || text.isEmpty()) return text;
    String lang = c.getSharedPreferences("settings", 0).getString("language", "en");
    if (lang.equals("en")) return text;
    try {
      if (!maps.containsKey(lang)) {
        Map<String, String> en = new HashMap<>(), local = new HashMap<>(), map = new HashMap<>(), lower = new HashMap<>();
        flatten(
            new JSONObject(
                new String(
                    IOUtil.read(c.getAssets().open("locales/en.json")), StandardCharsets.UTF_8)),
            "",
            en);
        flatten(
            new JSONObject(
                new String(
                    IOUtil.read(c.getAssets().open("locales/" + lang + ".json")),
                    StandardCharsets.UTF_8)),
            "",
            local);
        List<String> keys = new ArrayList<>(en.keySet());
        keys.sort(java.util.Comparator.comparingInt(k -> chrome(k) ? 1 : 0));
        for (String key : keys) {
          String enVal = en.get(key);
          String locVal = local.getOrDefault(key, enVal);
          map.put(enVal, locVal);
          lower.put(enVal.trim().toLowerCase(Locale.ROOT), locVal);
          map.put(key, locVal);
        }
        maps.put(lang, map);
        lowerMaps.put(lang, lower);
      }
      Map<String, String> m = maps.get(lang);
      if (m != null) {
        String direct = m.get(text);
        if (direct != null) return direct;
        Map<String, String> low = lowerMaps.get(lang);
        if (low != null) {
          String fallback = low.get(text.trim().toLowerCase(Locale.ROOT));
          if (fallback != null) return fallback;
        }
      }
      return text;
    } catch (Exception e) {
      return text;
    }
  }
}
