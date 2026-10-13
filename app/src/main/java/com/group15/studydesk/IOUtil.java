package com.group15.studydesk;

import java.io.*;

public final class IOUtil {
  public static byte[] read(InputStream in) throws IOException {
    try (InputStream source = in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      copy(source, out);
      return out.toByteArray();
    }
  }

  public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) != -1) out.write(b, 0, n);
  }
}
