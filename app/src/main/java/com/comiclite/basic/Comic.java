package com.comiclite.basic;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A comic as a list of page images. Opening reads only the archive index;
 * a page is unpacked when it is asked for, so even big files open right away.
 */
abstract class Comic implements Closeable {
    final List<String> names = new ArrayList<String>();

    int count() {
        return names.size();
    }

    /** Bytes of one page. Called only from the loader thread. */
    abstract byte[] read(int i) throws IOException;

    /** The cover is the first page. For covers the archive is opened without background unpacking. */
    byte[] cover() throws IOException {
        return read(0);
    }

    static Comic open(File f, File cacheDir) throws IOException {
        if (f.isDirectory()) return new FolderComic(f);
        // Look at the file header, not the extension: a .cbr is often a zip and vice versa.
        byte[] head = new byte[8];
        int n;
        InputStream in = new FileInputStream(f);
        try {
            n = in.read(head);
        } finally {
            in.close();
        }
        if (n >= 4 && head[0] == 'P' && head[1] == 'K') {
            return new ZipComic(f);
        }
        if (n >= 7 && head[0] == 'R' && head[1] == 'a' && head[2] == 'r' && head[3] == '!') {
            if (head[6] == 1) {
                throw new IOException("This is a RAR5 archive, which ComicLite can't open. Repack it as cbz on a computer.");
            }
            return new RarComic(f, cacheDir);
        }
        throw new IOException("Not a comic: the file is neither zip nor rar.");
    }

    /** Like open(), but only to get the cover: a solid rar is not unpacked as a whole. */
    static Comic openForCover(File f) throws IOException {
        if (!f.isDirectory() && isRar(f)) return new RarComic(f, null);
        return open(f, null);
    }

    private static boolean isRar(File f) throws IOException {
        byte[] head = new byte[7];
        InputStream in = new FileInputStream(f);
        try {
            return in.read(head) == 7 && head[0] == 'R' && head[1] == 'a' && head[2] == 'r' && head[3] == '!' && head[6] == 0;
        } finally {
            in.close();
        }
    }

    /** Pictures of a folder in order — names only, the files themselves are not read. */
    static List<String> imagesIn(File dir) {
        List<String> out = new ArrayList<String>();
        String[] all = dir.list();
        if (all == null) return out;
        for (String n : all) {
            if (isImage(n)) out.add(n);
        }
        Collections.sort(out, NATURAL);
        return out;
    }

    static boolean isComicFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".cbz") || n.endsWith(".cbr") || n.endsWith(".zip") || n.endsWith(".rar");
    }

    static boolean isImage(String path) {
        String p = path.toLowerCase();
        if (p.startsWith("__macosx/") || p.contains("/__macosx/")) return false;
        int slash = p.lastIndexOf('/');
        if (p.startsWith(".", slash + 1)) return false;
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png")
                || p.endsWith(".webp") || p.endsWith(".gif") || p.endsWith(".bmp");
    }

    /** "Human" order: page 2 comes before page 10. */
    static int naturalCompare(String a, String b) {
        int i = 0, j = 0, na = a.length(), nb = b.length();
        while (i < na && j < nb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                while (i < na && a.charAt(i) == '0') i++;
                while (j < nb && b.charAt(j) == '0') j++;
                int ei = i, ej = j;
                while (ei < na && Character.isDigit(a.charAt(ei))) ei++;
                while (ej < nb && Character.isDigit(b.charAt(ej))) ej++;
                if (ei - i != ej - j) return (ei - i) - (ej - j);
                for (; i < ei; i++, j++) {
                    int d = a.charAt(i) - b.charAt(j);
                    if (d != 0) return d;
                }
            } else {
                int d = Character.toLowerCase(ca) - Character.toLowerCase(cb);
                if (d != 0) return d;
                i++;
                j++;
            }
        }
        return (na - i) - (nb - j);
    }

    static final Comparator<String> NATURAL = new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            return naturalCompare(a, b);
        }
    };

    static byte[] readAll(InputStream in, long size) throws IOException {
        if (size > 0 && size < Integer.MAX_VALUE) {
            byte[] b = new byte[(int) size];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            if (off == b.length) return b;
            byte[] cut = new byte[off];
            System.arraycopy(b, 0, cut, 0, off);
            return cut;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.delete();
    }

    // ---------- folder of pictures ----------

    static final class FolderComic extends Comic {
        private final File dir;

        FolderComic(File dir) {
            this.dir = dir;
            // The pictures of the folder, in natural order.
            names.addAll(imagesIn(dir));
        }

        @Override
        byte[] read(int i) throws IOException {
            File f = new File(dir, names.get(i));
            InputStream in = new FileInputStream(f);
            try {
                return readAll(in, f.length());
            } finally {
                in.close();
            }
        }

        @Override
        public void close() {
        }
    }

    // ---------- cbz ----------

    static final class ZipComic extends Comic {
        private final ZipFile zip;
        private final List<ZipEntry> pages = new ArrayList<ZipEntry>();

        ZipComic(File f) throws IOException {
            zip = new ZipFile(f);
            Enumeration<? extends ZipEntry> e = zip.entries();
            while (e.hasMoreElements()) {
                ZipEntry z = e.nextElement();
                if (!z.isDirectory() && isImage(z.getName())) pages.add(z);
            }
            Collections.sort(pages, new Comparator<ZipEntry>() {
                @Override
                public int compare(ZipEntry a, ZipEntry b) {
                    return naturalCompare(a.getName(), b.getName());
                }
            });
            for (ZipEntry z : pages) names.add(z.getName());
        }

        @Override
        synchronized byte[] read(int i) throws IOException {
            ZipEntry z = pages.get(i);
            InputStream in = zip.getInputStream(z);
            try {
                return readAll(in, z.getSize());
            } finally {
                in.close();
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    // ---------- cbr ----------

    /** Skipped files of a solid rar: they must be unpacked, but not kept. */
    static final OutputStream NOWHERE = new OutputStream() {
        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    };

    /** Writes straight into an array of the right size, without an extra copy of the page. */
    static final class ExactOutput extends OutputStream {
        private byte[] buf;
        private int len;

        ExactOutput(long size) {
            buf = new byte[(int) Math.max(1024, Math.min(size, Integer.MAX_VALUE - 8))];
        }

        @Override
        public void write(int b) {
            ensure(1);
            buf[len++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int n) {
            ensure(n);
            System.arraycopy(b, off, buf, len, n);
            len += n;
        }

        private void ensure(int n) {
            if (len + n > buf.length) {
                byte[] bigger = new byte[Math.max(buf.length * 2, len + n)];
                System.arraycopy(buf, 0, bigger, 0, len);
                buf = bigger;
            }
        }

        byte[] bytes() {
            if (len == buf.length) return buf;
            byte[] cut = new byte[len];
            System.arraycopy(buf, 0, cut, 0, len);
            return cut;
        }
    }

    /**
     * A normal rar can be read from any page. A solid rar can only be unpacked in order,
     * so a background thread unpacks its pages one by one into a temporary folder,
     * and reading a page waits until that page has been unpacked.
     */
    static final class RarComic extends Comic {
        private final Archive rar;
        private final List<FileHeader> pages = new ArrayList<FileHeader>();
        private final boolean solid;
        private final File dir;
        private final Map<FileHeader, File> ready = new IdentityHashMap<FileHeader, File>();
        /** Page number in archive order — unpacking goes in exactly this order. */
        private final Map<FileHeader, Integer> rank = new IdentityHashMap<FileHeader, Integer>();
        private static final int AHEAD = 10;
        private int wantedUpTo = AHEAD;
        private IOException failure;
        private boolean finished;
        private volatile boolean closed;
        private Thread worker;

        RarComic(File f, File cacheDir) throws IOException {
            try {
                rar = new Archive(f, null);
                if (rar.isEncrypted()) throw new IOException("The archive is password-protected.");
            } catch (RarException e) {
                throw new IOException("Can't read rar: " + e.getMessage());
            }
            final Map<FileHeader, String> nameOf = new HashMap<FileHeader, String>();
            for (FileHeader h : rar.getFileHeaders()) {
                if (h.isDirectory()) continue;
                String name = nameOf(h);
                if (!isImage(name)) continue;
                if (h.isEncrypted()) throw new IOException("Pages in this archive are password-protected.");
                pages.add(h);
                nameOf.put(h, name);
            }
            Collections.sort(pages, new Comparator<FileHeader>() {
                @Override
                public int compare(FileHeader a, FileHeader b) {
                    return naturalCompare(nameOf.get(a), nameOf.get(b));
                }
            });
            for (FileHeader h : pages) names.add(nameOf.get(h));

            solid = rar.getMainHeader().isSolid();
            int r = 0;
            for (FileHeader h : rar.getFileHeaders()) {
                if (nameOf.containsKey(h)) rank.put(h, r++);
            }
            // Every open comic gets its own folder, so opening the next one cannot wipe its pages.
            dir = solid && cacheDir != null ? new File(cacheDir, "rar-" + System.nanoTime()) : null;
            if (dir != null) {
                cleanStale(cacheDir);
                dir.mkdirs();
                worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        unpackInOrder();
                    }
                }, "rar-solid");
                worker.setPriority(Thread.MIN_PRIORITY);
                worker.start();
            }
        }

        private static String nameOf(FileHeader h) {
            String n = h.isUnicode() ? h.getFileNameW() : h.getFileNameString();
            return n.replace('\\', '/');
        }

        /** Folders left from earlier runs if the app was killed. */
        private static void cleanStale(File cacheDir) {
            File[] all = cacheDir.listFiles();
            if (all == null) return;
            long dayAgo = System.currentTimeMillis() - 24L * 3600 * 1000;
            for (File d : all) {
                if (d.getName().startsWith("rar") && d.isDirectory() && d.lastModified() < dayAgo) deleteDir(d);
            }
        }

        private void unpackInOrder() {
            IdentityHashMap<FileHeader, Boolean> wanted = new IdentityHashMap<FileHeader, Boolean>();
            for (FileHeader h : pages) wanted.put(h, Boolean.TRUE);
            int k = 0;
            try {
                for (FileHeader h : rar.getFileHeaders()) {
                    if (closed) return;
                    if (h.isDirectory()) continue;
                    if (wanted.containsKey(h)) {
                        // Stay close to the reader: no need to fill the storage with the whole volume.
                        synchronized (this) {
                            while (!closed && k > wantedUpTo + AHEAD) wait();
                        }
                        if (closed) return;
                        File out = new File(dir, String.valueOf(k++));
                        OutputStream os = new FileOutputStream(out);
                        try {
                            rar.extractFile(h, os);
                        } finally {
                            os.close();
                        }
                        synchronized (this) {
                            ready.put(h, out);
                            notifyAll();
                        }
                    } else {
                        // Skipped files still have to be passed through, or the next ones unpack wrong.
                        rar.extractFile(h, NOWHERE);
                    }
                }
            } catch (Exception e) {
                synchronized (this) {
                    if (failure == null) failure = new IOException("Rar unpacking error: " + e.getMessage());
                }
            } finally {
                synchronized (this) {
                    finished = true;
                    notifyAll();
                }
            }
        }

        @Override
        byte[] cover() throws IOException {
            if (!solid) return read(0);
            // A solid archive can only be read from the start: take the first picture in archive order.
            try {
                for (FileHeader h : rar.getFileHeaders()) {
                    if (h.isDirectory()) continue;
                    if (pages.contains(h)) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(h.getFullUnpackSize(), 1024));
                        rar.extractFile(h, out);
                        return out.toByteArray();
                    }
                    rar.extractFile(h, NOWHERE);
                }
            } catch (RarException e) {
                throw new IOException("Can't unpack the cover: " + e.getMessage());
            }
            return null;
        }

        @Override
        byte[] read(int i) throws IOException {
            FileHeader h = pages.get(i);
            if (!solid) {
                ExactOutput out = new ExactOutput(h.getFullUnpackSize());
                // rar reads from one shared file position — two pages at once would mix it up.
                synchronized (rar) {
                    try {
                        rar.extractFile(h, out);
                    } catch (RarException e) {
                        throw new IOException("Can't unpack the page: " + e.getMessage());
                    }
                }
                return out.bytes();
            }
            // Opened without background unpacking (cover) — a solid rar cannot be read.
            if (dir == null) throw new IOException("Solid rar opened without unpacking.");
            File f;
            synchronized (this) {
                Integer r = rank.get(h);
                if (r != null && r > wantedUpTo) {
                    wantedUpTo = r;
                    notifyAll();
                }
                while ((f = ready.get(h)) == null) {
                    if (failure != null) throw failure;
                    if (finished || closed) throw new IOException("The page could not be unpacked.");
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new IOException("Interrupted.");
                    }
                }
            }
            InputStream in = new FileInputStream(f);
            try {
                return readAll(in, f.length());
            } finally {
                in.close();
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            synchronized (this) {
                notifyAll();
            }
            // Wait until background unpacking finishes the current file, then clean up.
            if (worker != null) {
                try {
                    worker.join(3000);
                } catch (InterruptedException ignored) {
                }
            }
            rar.close();
            if (dir != null) deleteDir(dir);
        }
    }
}
