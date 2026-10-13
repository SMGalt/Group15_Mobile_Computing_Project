package com.group15.studydesk;

import android.content.*;

public class TimerState {
  final SharedPreferences p;

  public TimerState(Context c) {
    p = c.getSharedPreferences("timer", 0);
  }

  public boolean running() {
    return p.getBoolean("running", false);
  }

  public boolean stopwatch() {
    return p.getBoolean("stopwatch", false);
  }

  public String phase() {
    return p.getString("phase", "focus");
  }

  public int duration() {
    return p.getInt("duration", 25 * 60);
  }

  public long secs() {
    return running()
        ? Rules.timerSeconds(
            p.getLong("anchor", 0),
            p.getInt("secs", duration()),
            System.currentTimeMillis(),
            stopwatch())
        : p.getInt("secs", duration());
  }

  public void start(String task, String course, int duration) {
    long now = System.currentTimeMillis();
    SharedPreferences.Editor e =
        p.edit()
            .putBoolean("running", true)
            .putLong("anchor", now)
            .putInt("secs", (int) secs())
            .putString("task", task)
            .putString("course", course)
            .putInt("duration", duration);
    if (p.getLong("sessionStart", 0) == 0) e.putLong("sessionStart", now);
    e.commit();
  }

  public void pause() {
    p.edit().putInt("secs", (int) secs()).putBoolean("running", false).commit();
  }

  public int studied() {
    if (stopwatch()) return (int) secs();
    return Math.max(0, p.getInt("duration", 1500) - (int) secs());
  }

  /**
   * Point a countdown at a new target, restarting the phase.
   *
   * <p>The remaining time moves with the target, which is the whole point: leave the old remainder
   * behind and {@code studied()} reports {@code new duration - old remainder} — a five-minute block
   * retargeted to an hour reads back as forty minutes of study that never happened. The start
   * timestamp goes with the old phase, and a stopwatch counts up toward nothing, so its reading is
   * left alone.
   */
  public void retarget(int duration) {
    SharedPreferences.Editor e = p.edit().putInt("duration", duration);
    if (!stopwatch()) e.putInt("secs", duration).putLong("sessionStart", 0);
    e.commit();
  }

  public void reset() {
    p.edit()
        .putBoolean("running", false)
        .putInt(
            "secs",
            stopwatch()
                ? 0
                : phase().equals("short") ? 300 : phase().equals("long") ? 900 : duration())
        .putLong("sessionStart", 0)
        .commit();
  }

  public void phase(String phase) {
    p.edit()
        .putBoolean("running", false)
        .putString("phase", phase)
        .putInt("secs", phase.equals("focus") ? duration() : phase.equals("short") ? 300 : 900)
        .putLong("sessionStart", 0)
        .commit();
  }
}
