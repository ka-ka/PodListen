package com.einmalfel.podlisten;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This class is in charge of downloading, storing memory-caching images
 */
public class ImageManager {
  private static final String TAG = "IMG";
  private static final int WIDTH_SP = 70;
  private static final int HEIGHT_SP = 70;
  private static final int PAGES_TO_CACHE = 10;
  private final int widthPx;
  private final int heightPx;
  private static ImageManager instance;

  private final LruCache<Long, Bitmap> memoryCache;
  private final Context context;

  @NonNull
  public static ImageManager getInstance() {
    if (instance == null) {
      synchronized (ImageManager.class) {
        if (instance == null) {
          instance = new ImageManager();
        }
      }
    }
    return instance;
  }

  @Nullable
  synchronized public Bitmap getImage(long id) {
    Bitmap result = memoryCache.get(id);
    if (result == null) {
      result = loadFromDisk(id);
      if (result != null) {
        memoryCache.put(id, result);
      }
    }
    return result;
  }

  synchronized public void deleteImage(long id) {
    File file = getImageFile(id);
    if (file != null && file.exists()) {
      if (!file.delete()) {
        Log.e(TAG, "Deletion of " + file.getAbsolutePath() + " failed");
      }
    }
  }

  public void download(long id, URL url) throws IOException {
    File file = getImageFile(id);
    if (file == null) {
      throw new FileNotFoundException("id " + id);
    }
    Log.d(TAG, "Downloading " + url);
    HttpURLConnection urlConnection = null;
    Bitmap bitmap = null;
    try {
      urlConnection = (HttpURLConnection) url.openConnection();
      urlConnection.connect();
      bitmap = BitmapFactory.decodeStream(urlConnection.getInputStream());
      if (bitmap == null) {
        throw new IOException("Failed to load image from " + url);
      }
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, widthPx, heightPx, true);
    // synchronize to be sure that isDownloaded won't return true while image file is being written
    synchronized (this) {
      FileOutputStream stream = new FileOutputStream(file);
      scaled.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
    Log.d(TAG, url.toString() + " written to " + file.getAbsolutePath());
  }

  public synchronized boolean isDownloaded(long id) {
    File file = getImageFile(id);
    return file != null && file.exists();
  }

  @Nullable
  private File getImageFile(long id) {
    File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    return (dir == null ? null : new File(dir, Long.toString(id) + ".png"));
  }

  @Nullable
  private Bitmap loadFromDisk(long id) {
    if (!isDownloaded(id)) {
      return null;
    }
    Log.d(TAG, "Loading " + id + " from sdcard. Cache size before " + getCacheSize());
    File file = getImageFile(id);
    return file == null ? null : BitmapFactory.decodeFile(file.getAbsolutePath());
  }

  private int getCacheSize() {
    int size = 0;
    for (Bitmap b : memoryCache.snapshot().values()) {
      size += b.getByteCount(); // approximate value. TODO use getAllocatedByteCount on 4.4+
    }
    return size;
  }

  private ImageManager() {
    context = PodListenApp.getContext();
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    widthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, WIDTH_SP, metrics);
    heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HEIGHT_SP, metrics);

    Point displaySize = new Point();
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    wm.getDefaultDisplay().getSize(displaySize);
    int imagesPerPage = displaySize.y / heightPx;
    memoryCache = new LruCache<>(PAGES_TO_CACHE * imagesPerPage);
  }
}
