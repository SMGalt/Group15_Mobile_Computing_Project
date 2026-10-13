package com.group15.studydesk;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import okhttp3.*;
import org.json.*;

public final class ProfilePhotos {
  public static void upload(Context c, CloudClient cloud, Uri uri) throws Exception {
    String owner = new Repository(c).owner();
    byte[] raw = IOUtil.read(c.getContentResolver().openInputStream(uri));
    if (raw.length > 10 * 1024 * 1024) throw new IOException("Choose an image under 10 MB");
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
    options.inJustDecodeBounds = false;
    options.inSampleSize = Math.max(1, Math.min(options.outWidth, options.outHeight) / 256);
    Bitmap image = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
    if (image == null) throw new IOException("Unsupported image");
    int size = Math.min(image.getWidth(), image.getHeight());
    Bitmap
        crop =
            Bitmap.createBitmap(
                image, (image.getWidth() - size) / 2, (image.getHeight() - size) / 2, size, size),
        scaled = Bitmap.createScaledBitmap(crop, 256, 256, true);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    scaled.compress(Bitmap.CompressFormat.WEBP, 85, bytes);
    byte[] encoded = bytes.toByteArray();
    if (encoded.length > 262144)
      throw new IOException("Avatar could not be compressed under 256 KB");
    String path = owner + "/avatar.webp";
    Request request =
        new Request.Builder()
            .url(cloud.url + "/storage/v1/object/avatars/" + path)
            .header("apikey", cloud.key)
            .header("Authorization", "Bearer " + cloud.token())
            .header("x-upsert", "true")
            .post(RequestBody.create(encoded, MediaType.get("image/webp")))
            .build();
    try (Response response = cloud.http.newCall(request).execute()) {
      if (!response.isSuccessful())
        throw new IOException("Avatar upload failed (HTTP " + response.code() + ")");
    }
    JSONObject patch =
        new JSONObject()
            .put("avatar_kind", "image")
            .put("updated_at", java.time.Instant.now().toString());
    cloud.request(
        "PATCH", "/rest/v1/profiles?id=eq." + owner, patch.toString(), cloud.token(), null);
    File file = new File(c.getFilesDir(), "avatar-" + owner + ".webp");
    try (OutputStream out = new FileOutputStream(file)) {
      out.write(encoded);
    }
    image.recycle();
    if (crop != image) crop.recycle();
    if (scaled != crop) scaled.recycle();
  }

  public static void remove(Context c, CloudClient cloud) throws Exception {
    String owner = new Repository(c).owner();
    cloud.request(
        "DELETE",
        "/storage/v1/object/avatars",
        new JSONObject().put("prefixes", new JSONArray().put(owner + "/avatar.webp")).toString(),
        cloud.token(),
        null);
    cloud.request(
        "PATCH",
        "/rest/v1/profiles?id=eq." + owner,
        new JSONObject().put("avatar_kind", "glyph").toString(),
        cloud.token(),
        null);
    new File(c.getFilesDir(), "avatar-" + owner + ".webp").delete();
  }
}
