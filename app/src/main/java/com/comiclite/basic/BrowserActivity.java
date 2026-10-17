package com.comiclite.basic;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A plain file list: folders and comics. It only reads file names, so even big folders open at once. */
public final class BrowserActivity extends Activity {
    private static final class Row {
        final File file;
        final String title, subtitle;
        final boolean up;

        Row(File file, String title, String subtitle, boolean up) {
            this.file = file;
            this.title = title;
            this.subtitle = subtitle;
            this.up = up;
        }
    }

    private final List<Row> rows = new ArrayList<Row>();
    private Prefs prefs;
    private ListView list;
    private TextView header;
    private File dir;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);
        header = new TextView(this);
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(Color.BLACK);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setSingleLine(true);
        int pad = Math.round(12 * d);
        header.setPadding(pad, pad, pad, pad);
        page.addView(header);

        list = new ListView(this);
        list.setBackgroundColor(Color.WHITE);
        list.setCacheColorHint(Color.WHITE);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                open(rows.get(position));
            }
        });
        page.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(page);

        String last = prefs.lastDir();
        dir = last != null && new File(last).isDirectory() ? new File(last) : Environment.getExternalStorageDirectory();
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        load(dir);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int pos = list.getFirstVisiblePosition();
        load(dir); // bookmarks may have changed while reading
        list.setSelection(Math.min(pos, Math.max(0, rows.size() - 1)));
    }

    private void load(File d) {
        dir = d;
        prefs.setLastDir(d.getPath());
        header.setText(d.getPath());
        rows.clear();
        File parent = d.getParentFile();
        if (parent != null) rows.add(new Row(parent, getString(R.string.up), parent.getPath(), true));
        File[] all = d.listFiles();
        if (all == null) {
            rows.add(new Row(null, getString(R.string.no_access), "", false));
        } else {
            List<String> dirs = new ArrayList<String>(), comics = new ArrayList<String>();
            for (File f : all) {
                String n = f.getName();
                if (n.startsWith(".")) continue;
                if (f.isDirectory()) dirs.add(n);
                else if (Comic.isComicFile(n)) comics.add(n);
            }
            Collections.sort(dirs, Comic.NATURAL);
            Collections.sort(comics, Comic.NATURAL);
            for (String n : dirs) {
                File f = new File(d, n);
                rows.add(new Row(f, n, progress(f, getString(R.string.folder)), false));
            }
            for (String n : comics) {
                File f = new File(d, n);
                rows.add(new Row(f, n, progress(f, String.format(Locale.US, "%.1f MB", f.length() / 1048576f)), false));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private String progress(File f, String fallback) {
        int n = prefs.pageCount(f.getPath());
        if (n == 0) return fallback;
        int p = prefs.page(f.getPath());
        return p >= n - 1 ? getString(R.string.finished) : getString(R.string.page_of, p + 1, n);
    }

    private void open(Row r) {
        if (r.file == null) return;
        if (r.up || (r.file.isDirectory() && Comic.imagesIn(r.file).isEmpty())) {
            load(r.file);
            list.setSelection(0);
        } else {
            // A comic file, or a folder of pictures — read it.
            startActivity(new Intent(this, ReaderActivity.class).putExtra(ReaderActivity.EXTRA_PATH, r.file.getPath()));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int visible = Math.max(1, list.getLastVisiblePosition() - list.getFirstVisiblePosition());
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                list.setSelection(Math.min(rows.size() - 1, list.getFirstVisiblePosition() + visible));
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                list.setSelection(Math.max(0, list.getFirstVisiblePosition() - visible));
                return true;
            case KeyEvent.KEYCODE_BACK:
                File parent = dir.getParentFile();
                if (parent != null && parent.list() != null) {
                    load(parent);
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean ours = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_UP;
        return ours || super.onKeyUp(keyCode, event);
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int i) {
            return rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout v = (LinearLayout) convert;
            if (v == null) {
                float d = getResources().getDisplayMetrics().density;
                v = new LinearLayout(BrowserActivity.this);
                v.setOrientation(LinearLayout.VERTICAL);
                v.setPadding(Math.round(14 * d), Math.round(10 * d), Math.round(14 * d), Math.round(10 * d));
                TextView t = new TextView(BrowserActivity.this);
                t.setTextColor(Color.BLACK);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                TextView s = new TextView(BrowserActivity.this);
                s.setTextColor(Color.rgb(0x44, 0x44, 0x44));
                s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                s.setSingleLine(true);
                v.addView(t);
                v.addView(s);
            }
            Row r = rows.get(i);
            ((TextView) v.getChildAt(0)).setText(r.title);
            ((TextView) v.getChildAt(1)).setText(r.subtitle);
            return v;
        }
    };
}
