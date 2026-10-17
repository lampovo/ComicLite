package com.comiclite.basic;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Turns page bytes into a picture that fits the screen, using Android's own decoder.
 * The decoder is asked to shrink the picture by 2, 4, 8… while decoding (cheaper and
 * uses less memory), then the result is scaled exactly to the screen.
 */
final class PageDecoder {
    private PageDecoder() {
    }

    static Bitmap decode(byte[] data, int screenW, int screenH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int w = o.outWidth, h = o.outHeight;
        if (w <= 0 || h <= 0) return null;

        float scale = Math.min((float) screenW / w, (float) screenH / h);
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));

        int sample = 1;
        while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2;

        Bitmap src = null;
        // Not enough memory for a big page — decode it twice smaller and try again.
        for (int attempt = 0; src == null && attempt < 3; attempt++, sample *= 2) {
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                src = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (src == null) return null;
            } catch (OutOfMemoryError e) {
                System.gc();
            }
        }
        if (src == null) throw new OutOfMemoryError("page too large");
        if (src.getWidth() == tw && src.getHeight() == th) return src;

        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
        new Canvas(out).drawBitmap(src, null, new Rect(0, 0, tw, th), new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }
}
