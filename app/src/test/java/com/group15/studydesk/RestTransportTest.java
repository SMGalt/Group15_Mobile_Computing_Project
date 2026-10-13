package com.group15.studydesk;

import static org.junit.Assert.*;

import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;

/** These are local HTTP contracts, not evidence of live Supabase or Google login. */
public class RestTransportTest {
  MockWebServer server;
  RestTransport transport;
  String base;

  @Before
  public void start() throws Exception {
    server = new MockWebServer();
    server.start();
    base = server.url("/").toString().replaceAll("/$", "");
    transport = new RestTransport(new OkHttpClient());
  }

  @After
  public void stop() throws Exception {
    server.shutdown();
  }

  @Test
  public void sendsActualAuthenticatedRequestAndUpsertPreference() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(201).setBody("[]"));
    transport.request(
        base,
        "test-key",
        "POST",
        "/rest/v1/assignments?on_conflict=id",
        "{\"id\":\"one\"}",
        "test-token",
        "resolution=merge-duplicates,return=minimal");
    RecordedRequest r = server.takeRequest();
    assertEquals("POST", r.getMethod());
    assertEquals("test-key", r.getHeader("apikey"));
    assertEquals("Bearer test-token", r.getHeader("Authorization"));
    assertEquals("resolution=merge-duplicates,return=minimal", r.getHeader("Prefer"));
    assertEquals("one", new JSONObject(r.getBody().readUtf8()).getString("id"));
  }

  @Test
  public void pagesPastServerDefaultThousandRows() throws Exception {
    JSONArray page = new JSONArray();
    for (int i = 0; i < 1000; i++) page.put(new JSONObject().put("id", i));
    server.enqueue(new MockResponse().setBody(page.toString()));
    server.enqueue(new MockResponse().setBody("[{\"id\":1000}]"));
    assertEquals(1001, transport.pull(base, "key", "token", "subjects", "user").length());
    assertTrue(server.takeRequest().getPath().contains("offset=0"));
    assertTrue(server.takeRequest().getPath().contains("offset=1000"));
  }

  @Test
  public void failedSecondPageFailsWholePullInsteadOfReturningPartial() throws Exception {
    JSONArray page = new JSONArray();
    for (int i = 0; i < 1000; i++) page.put(i);
    server.enqueue(new MockResponse().setBody(page.toString()));
    server.enqueue(new MockResponse().setResponseCode(503).setBody("service down"));
    try {
      transport.pull(base, "key", "token", "subjects", "user");
      fail("Partial pull accepted");
    } catch (java.io.IOException expected) {
      assertTrue(expected.getMessage().contains("503"));
    }
  }

  @Test
  public void authenticationErrorsAreFailuresAndDoNotExposeBodySecrets() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("secret-password=test"));
    try {
      transport.request(
          base,
          "key",
          "POST",
          "/auth/v1/token?grant_type=password",
          "{\"email\":\"test@example.invalid\",\"password\":\"fixture\"}",
          null,
          null);
      fail();
    } catch (java.io.IOException expected) {
      assertFalse(expected.getMessage().contains("secret-password"));
    }
    assertEquals("/auth/v1/token?grant_type=password", server.takeRequest().getPath());
  }

  @Test
  public void deleteTombstoneIsTransmittedAndServerFailureRemainsVisible() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(409));
    Row r = new Row("assignment").put("title", "Deleted");
    r.deletedAt = "2026-10-13T01:00:00Z";
    r.updatedAt = r.deletedAt;
    try {
      transport.request(
          base,
          "key",
          "POST",
          "/rest/v1/assignments",
          CloudSchema.toCloud(r, "user").toString(),
          "token",
          null);
      fail();
    } catch (java.io.IOException expected) {
    }
    JSONObject sent = new JSONObject(server.takeRequest().getBody().readUtf8());
    assertEquals(r.deletedAt, sent.getString("deleted_at"));
  }
}
