package com.group15.studydesk;

import android.content.*;
import android.os.*;
import okhttp3.*;
import org.json.*;

/** Supabase Phoenix channel on the foreground lifecycle, plus durable JobScheduler fallback. */
public final class RealtimeClient {
  static RealtimeClient active;
  final Context context;
  final CloudClient cloud;
  final Handler handler = new Handler(Looper.getMainLooper());
  WebSocket socket;
  int reference = 1;
  boolean closed;

  private RealtimeClient(Context c) {
    context = c.getApplicationContext();
    cloud = new CloudClient(context);
  }

  public static synchronized void start(Context c) {
    if (active != null) return;
    CloudClient client = new CloudClient(c);
    if (!client.configured() || client.session() == null) return;
    active = new RealtimeClient(c);
    RealtimeClient instance = active;
    Repository.IO.execute(instance::connect);
  }

  public static synchronized void stop() {
    if (active != null) {
      active.closed = true;
      active.handler.removeCallbacksAndMessages(null);
      if (active.socket != null) active.socket.close(1000, "Background");
      active = null;
    }
  }

  void connect() {
    try {
      String token = cloud.token(), owner = new Repository(context).owner();
      if (closed) return;
      String endpoint =
          cloud.url.replaceFirst("^https:", "wss:")
              + "/realtime/v1/websocket?apikey="
              + android.net.Uri.encode(cloud.key)
              + "&vsn=1.0.0";
      socket =
          cloud.http.newWebSocket(
              new Request.Builder().url(endpoint).build(),
              new WebSocketListener() {
                public void onOpen(WebSocket s, Response r) {
                  try {
                    JSONArray changes = new JSONArray();
                    for (CloudSchema.Table table : CloudSchema.TABLES.values())
                      changes.put(
                          new JSONObject()
                              .put("event", "*")
                              .put("schema", "public")
                              .put("table", table.name())
                              .put("filter", "user_id=eq." + owner));
                    JSONObject payload =
                        new JSONObject()
                            .put("config", new JSONObject().put("postgres_changes", changes))
                            .put("access_token", token);
                    send("realtime:studydesk-native", "phx_join", payload);
                    handler.postDelayed(heartbeat, 25000);
                  } catch (Exception e) {
                    status("Realtime subscription failed");
                  }
                }

                public void onMessage(WebSocket s, String message) {
                  try {
                    JSONObject event = new JSONObject(message);
                    String name = event.optString("event");
                    if (name.equals("postgres_changes")) {
                      handler.removeCallbacks(pull);
                      handler.postDelayed(pull, 400);
                    }
                    if (name.equals("phx_reply")) {
                      JSONObject payload = event.optJSONObject("payload");
                      if (payload != null && payload.optString("status").equals("error"))
                        status("Realtime unavailable; periodic sync remains active");
                      else status("Realtime connected");
                    }
                  } catch (Exception ignored) {
                  }
                }

                public void onFailure(WebSocket s, Throwable t, Response r) {
                  status("Realtime disconnected; retry on foreground");
                  handler.removeCallbacksAndMessages(null);
                  if (!closed)
                    handler.postDelayed(() -> Repository.IO.execute(() -> connect()), 30000);
                }
              });
    } catch (Exception e) {
      status("Realtime waiting for session refresh");
    }
  }

  void send(String topic, String event, JSONObject payload) throws JSONException {
    if (socket != null)
      socket.send(
          new JSONObject()
              .put("topic", topic)
              .put("event", event)
              .put("payload", payload)
              .put("ref", String.valueOf(reference++))
              .toString());
  }

  void pullChanges() {
    CloudSync.schedule(context);
  }

  final Runnable pull = this::pullChanges;
  final Runnable heartbeat =
      new Runnable() {
        public void run() {
          if (closed) return;
          try {
            send("phoenix", "heartbeat", new JSONObject());
          } catch (Exception ignored) {
          }
          handler.postDelayed(this, 25000);
        }
      };

  void status(String value) {
    context.getSharedPreferences("sync", 0).edit().putString("realtime", value).apply();
  }
}
