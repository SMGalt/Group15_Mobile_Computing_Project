package com.group15.studydesk;

import androidx.appcompat.app.AlertDialog;
import org.json.*;

public final class AiDebrief {
  public static void show(MainActivity a, Repository repo, Row session) {
    if (!a.getSharedPreferences("settings", 0).getBoolean("aiEnabled", false)) {
      a.message("Enable AI debrief in Settings first");
      return;
    }
    CloudClient c = new CloudClient(a);
    if (!c.configured() || c.session() == null) {
      a.message("AI debrief needs your own configured cloud function and a signed-in account");
      return;
    }
    if (session.s("notes").trim().isEmpty()) {
      a.message("Add study notes before requesting a debrief");
      return;
    }
    new AlertDialog.Builder(a)
        .setTitle("Analyse your study note?")
        .setMessage(
            "This sends the first 400 characters of this session's notes to your configured"
                + " ai-generate function.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Analyse",
            (d, w) ->
                repo.write(
                    () -> {
                      try {
                        String text = session.s("notes");
                        text = text.substring(0, Math.min(400, text.length())).replace('"', '\'');
                        String prompt =
                            "Extract study session data. Return JSON with subjectCovered (1–5"
                                + " words), comprehension (1–5 or null), confusionFlags (string"
                                + " array), sessionSummary (one sentence). Note: "
                                + text;
                        JSONObject result =
                            new JSONObject(
                                c.request(
                                    "POST",
                                    "/functions/v1/ai-generate",
                                    new JSONObject()
                                        .put("prompt", prompt)
                                        .put("json", true)
                                        .put("maxTokens", 200)
                                        .put("temperature", 0.2)
                                        .toString(),
                                    c.token(),
                                    null));
                        String raw = result.getString("text");
                        int start = raw.indexOf('{'), end = raw.lastIndexOf('}');
                        JSONObject parsed = new JSONObject(raw.substring(start, end + 1));
                        String summary = parsed.optString("sessionSummary", "");
                        a.runOnUiThread(
                            () ->
                                new AlertDialog.Builder(a)
                                    .setTitle("Study debrief")
                                    .setMessage(
                                        summary
                                            + "\nComprehension: "
                                            + parsed.opt("comprehension")
                                            + "\n"
                                            + parsed.optJSONArray("confusionFlags"))
                                    .setNegativeButton("Discard", null)
                                    .setPositiveButton(
                                        "Save",
                                        (x, y) ->
                                            repo.write(
                                                () -> {
                                                  session
                                                      .put("aiDebriefRaw", raw)
                                                      .put(
                                                          "aiSubjectCovered",
                                                          parsed.optString("subjectCovered"))
                                                      .put(
                                                          "aiComprehension",
                                                          parsed.optInt("comprehension", 0) >= 1
                                                                  && parsed.optInt(
                                                                          "comprehension", 0)
                                                                      <= 5
                                                              ? parsed.optInt("comprehension")
                                                              : null)
                                                      .put(
                                                          "aiConfusionFlags",
                                                          parsed.optJSONArray("confusionFlags"))
                                                      .put("aiSessionSummary", summary);
                                                  repo.save(session);
                                                }))
                                    .show());
                      } catch (Exception e) {
                        a.runOnUiThread(() -> a.message("Debrief unavailable: " + e.getMessage()));
                      }
                    }))
        .show();
  }
}
