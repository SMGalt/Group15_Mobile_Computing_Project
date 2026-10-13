package com.group15.studydesk;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class NotebookParityTest {
  JSONObject fixture() throws Exception {
    return new JSONObject(
        new String(
            IOUtil.read(getClass().getResourceAsStream("/baseline-oracles.json")),
            StandardCharsets.UTF_8));
  }

  @Test
  public void mathsMatchesFixedBaselineOutputs() throws Exception {
    JSONArray cases = fixture().getJSONArray("maths");
    for (int i = 0; i < cases.length(); i++) {
      JSONObject c = cases.getJSONObject(i);
      List<NoteSyntax.Node> nodes = NoteSyntax.maths(c.getString("input"));
      if (c.isNull("output")) assertNull(c.getString("input"), nodes);
      else assertEquals(c.getString("input"), c.getString("output"), NoteSyntax.flatten(nodes));
    }
  }

  @Test
  public void chemistryTallyAndUnknownGrammarMatchBaseline() throws Exception {
    JSONArray cases = fixture().getJSONArray("chem");
    for (int i = 0; i < cases.length(); i++) {
      JSONObject c = cases.getJSONObject(i);
      NoteSyntax.Chem parsed = NoteSyntax.chemistry(c.getString("input"));
      assertEquals(c.getString("input"), c.getBoolean("valid"), parsed != null);
      if (parsed == null) continue;
      if (!c.isNull("balanced"))
        assertEquals(c.getString("input"), c.getBoolean("balanced"), parsed.balanced());
      JSONArray tally = c.getJSONArray("tally");
      for (int k = 0; k < tally.length(); k++) {
        JSONObject t = tally.getJSONObject(k);
        assertEquals(
            t.getInt("have"), parsed.left().getOrDefault(t.getString("symbol"), 0).intValue());
        assertEquals(
            t.getInt("need"), parsed.right().getOrDefault(t.getString("symbol"), 0).intValue());
      }
    }
  }

  @Test
  public void layoutHashAndFeedIdentityAreIdenticalToSource() throws Exception {
    for (String name : new String[] {"hashes", "uids"}) {
      JSONArray cases = fixture().getJSONArray(name);
      for (int i = 0; i < cases.length(); i++) {
        JSONObject c = cases.getJSONObject(i);
        assertEquals(
            c.getString(name.equals("hashes") ? "hash" : "id"),
            name.equals("hashes")
                ? NoteLayout.hash(c.getString("input"))
                : CalendarFeed.idForUid(c.getString("input")));
      }
    }
  }

  @Test
  public void staleOrInconsistentLayoutKeepsAuthoritativeContent() throws Exception {
    List<NoteLayout.Box> boxes =
        Arrays.asList(NoteLayout.box(.5, 56, .5, "Second"), NoteLayout.box(0, 0, .5, "First"));
    String content = NoteLayout.join(boxes);
    assertEquals("First\n\nSecond", content);
    JSONObject layout = NoteLayout.write(boxes);
    assertEquals(2, NoteLayout.read(content, layout).size());
    assertEquals(
        "Edited on another device",
        NoteLayout.read("Edited on another device", layout).get(0).text());
    layout.getJSONArray("boxes").getJSONObject(0).put("text", "corrupt");
    assertEquals(content, NoteLayout.read(content, layout).get(0).text());
  }
}
