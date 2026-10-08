package es.jesus.ampodcasts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.util.Arrays;

/** Small disk cache; images never block the UI or get decoded at full poster resolution. */
final class ArtworkStore {
    private final File directory;
    ArtworkStore(Context context) { directory = new File(context.getCacheDir(), "artwork"); directory.mkdirs(); }
    synchronized Bitmap load(String url) throws Exception {
        if (Podcast.artworkUrl(url).isEmpty()) return null;
        File file = new File(directory, Repository.key(url) + ".png");
        if (file.exists()) { Bitmap cached = BitmapFactory.decodeFile(file.getPath()); if (cached != null) { file.setLastModified(System.currentTimeMillis()); return cached; } }
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
        byte[] bytes;
        try {
            if (conn.getResponseCode() != 200 || !"https".equalsIgnoreCase(conn.getURL().getProtocol())) return null;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); if (out.size() > 4_000_000) throw new IOException("Portada demasiado grande"); }
                bytes = out.toByteArray();
            }
        } finally { conn.disconnect(); }
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options); if (bitmap == null) return null;
        AtomicFile atomic = new AtomicFile(file); FileOutputStream output = atomic.startWrite();
        try { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("No se pudo guardar la portada"); atomic.finishWrite(output); }
        catch (Exception e) { atomic.failWrite(output); }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
        if (files != null) {
            Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified)); long total = 0; for (File f : files) total += f.length();
            for (File f : files) { if (total <= 20_000_000) break; if (!f.equals(file) && f.delete()) total -= f.length(); }
        }
        return bitmap;
    }
}
