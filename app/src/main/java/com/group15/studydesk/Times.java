package com.group15.studydesk;

import java.time.*;

public final class Times {
  public static LocalDateTime local(String time) {
    try {
      return Instant.parse(time).atZone(ZoneId.systemDefault()).toLocalDateTime();
    } catch (Exception e) {
      try {
        return OffsetDateTime.parse(time)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();
      } catch (Exception ignored) {
        return LocalDateTime.parse(time);
      }
    }
  }

  public static Instant instant(String time) {
    try {
      return Instant.parse(time);
    } catch (Exception e) {
      try {
        return OffsetDateTime.parse(time).toInstant();
      } catch (Exception ignored) {
        return LocalDateTime.parse(time).atZone(ZoneId.systemDefault()).toInstant();
      }
    }
  }

  public static String editedInstant(String previous, String edited) {
    if (previous != null
        && !previous.isEmpty()
        && local(previous).equals(LocalDateTime.parse(edited))) return instant(previous).toString();
    return instant(edited).toString();
  }

  public static LocalDate studyDay(String time, int hour) {
    return local(time).minusHours(Math.max(0, Math.min(6, hour))).toLocalDate();
  }
}
