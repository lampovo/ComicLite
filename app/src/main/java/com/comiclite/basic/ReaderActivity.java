package com.comiclite.basic;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The reader. The visible page is decoded first; then the next and the previous ones are
 * prepared in the background, so turning a page is usually instant. No animations: on e-ink
 * every animation is smearing and delay.
 */
public final class ReaderActivity extends Activity {
    static final String EXTRA_PATH = "path";

    private final Handler ui = new Handler();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final SparseArray<Bitmap> cache = new SparseArray<Bitmap>();
    private final Set<Integer> pending = new HashSet<Integer>();

    private Prefs prefs;
    private PageView view;
    private File file;
    private Comic comic;
    private int page;
    private volatile int wanted;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        view = new PageView(this);
        setContentView(view);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        page = prefs.page(path);
        loader.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Comic c = Comic.open(file, getCacheDir());
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            opened(c);
                        }
                    });
                } catch (final Throwable t) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ReaderActivity.this, getString(R.string.cant_open) + " " + t.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                }
            }
        });
    }

    private void opened(Comic c) {
        if (isFinishing()) {
            closeQuietly(c);
            return;
        }
        if (c.count() == 0) {
            closeQuietly(c);
            Toast.makeText(this, R.string.no_pictures, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        comic = c;
        go(Math.max(0, Math.min(page, c.count() - 1)));
    }

    private void go(int p) {
        page = p;
        wanted = p;
        for (int i = cache.size() - 1; i >= 0; i--) {
            if (Math.abs(cache.keyAt(i) - p) > 1) cache.removeAt(i);
        }
        show();
        request(p);
        request(p + 1);
        request(p - 1);
    }

    private void show() {
        Bitmap b = cache.get(page);
        if (b != null) view.show(b, (page + 1) + "/" + comic.count());
    }

    private void request(final int p) {
        if (comic == null || p < 0 || p >= comic.count() || cache.get(p) != null || pending.contains(p)) return;
        if (view.getWidth() == 0) return;
        pending.add(p);
        final Comic c = comic;
        final int w = view.getWidth(), h = view.getHeight();
        loader.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b = null;
                if (Math.abs(p - wanted) <= 1) {
                    try {
                        b = PageDecoder.decode(c.read(p), w, h);
                    } catch (Throwable ignored) {
                        // Broken page or not enough memory: it simply stays empty.
                    }
                }
                final Bitmap bitmap = b;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(p);
                        if (comic != c || bitmap == null) return;
                        cache.put(p, bitmap);
                        if (p == page) show();
                    }
                });
            }
        });
    }

    private void next() {
        if (comic == null) return;
        if (page + 1 < comic.count()) go(page + 1);
        else Toast.makeText(this, R.string.last_page, Toast.LENGTH_SHORT).show();
    }

    private void prev() {
        if (comic != null && page > 0) go(page - 1);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                next();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                prev();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Swallow the release too, otherwise volume keys also beep.
        boolean ours = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_UP;
        return ours || super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (comic != null) prefs.savePage(file.getPath(), page, comic.count());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        final Comic c = comic;
        comic = null;
        cache.clear();
        loader.execute(new Runnable() {
            @Override
            public void run() {
                closeQuietly(c);
            }
        });
        loader.shutdown();
    }

    private static void closeQuietly(Comic c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    /** The page on a white screen; tap the right third for next, the left third for previous. */
    private final class PageView extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        private String number;

        PageView(Context c) {
            super(c);
            setBackgroundColor(Color.WHITE);
            text.setColor(Color.BLACK);
            text.setTextSize(13 * c.getResources().getDisplayMetrics().density);
        }

        void show(Bitmap b, String n) {
            bitmap = b;
            number = n;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (comic == null || w == 0) return;
            cache.clear();
            go(page);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.WHITE);
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, (getWidth() - bitmap.getWidth()) / 2f, (getHeight() - bitmap.getHeight()) / 2f, null);
            }
            if (number != null) {
                canvas.drawText(number, getWidth() - text.measureText(number) - 8, getHeight() - 8, text);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (e.getX() > getWidth() * 2f / 3f) next();
                else if (e.getX() < getWidth() / 3f) prev();
            }
            return true;
        }
    }
}
