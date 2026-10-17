package com.comiclite.basic;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Bookmarks and the last folder. A comic is identified by file name and size, so moving it keeps the bookmark. */
final class Prefs {
    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("comiclite_basic", Context.MODE_PRIVATE);
    }

    private static String id(String path) {
        File f = new File(path);
        if (f.isDirectory()) {
            File parent = f.getParentFile();
            return (parent != null ? parent.getName() + "/" : "") + f.getName() + "|dir";
        }
        return f.getName() + "|" + f.length();
    }

    int page(String path) { return p.getInt("p:" + id(path), 0); }
    int pageCount(String path) { return p.getInt("n:" + id(path), 0); }

    void savePage(String path, int page, int count) {
        p.edit().putInt("p:" + id(path), page).putInt("n:" + id(path), count).apply();
    }

    String lastDir() { return p.getString("dir", null); }
    void setLastDir(String dir) { p.edit().putString("dir", dir).apply(); }
}
