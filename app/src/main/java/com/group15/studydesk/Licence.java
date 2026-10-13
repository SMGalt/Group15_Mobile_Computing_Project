package com.group15.studydesk;

import android.content.Context;

public final class Licence {
  public static String text(Context c) {
    try {
      return new String(
          IOUtil.read(c.getAssets().open("LICENSE")), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "MIT License · Copyright (c) 2026 Limecore Studio";
    }
  }
}
