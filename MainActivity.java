package com.dockgo.encmusic;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // =========================
    // API & ID PLAYLIST
    // =========================

    private static final String API = "https://spotify.xwolf.space/api";

    // New Music Friday Indonesia
    private static final String PL_NEW = "37i9dQZF1DX8vAahjzdXGC";

    // Top Hits Indonesia
    private static final String PL_TOP = "37i9dQZF1DXa2EiKmMLhFD";

    // Jumlah baris lagu yang diambilkan cover-nya per layar.
    private static final int THUMB_ROWS = 20;

    private static final int WHITE = Color.WHITE;

    // =========================
    // MODEL
    // =========================

    private static class Track {
        final String id, title, artist, duration, preview;
        String thumb;

        Track(String id, String title, String artist,
              String duration, String preview, String thumb) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.preview = preview;
            this.thumb = thumb == null ? "" : thumb;
        }
    }

    private static class Meta {
        String name = "", desc = "", thumb = "", url = "";
        int total = 0;
    }

    private interface TracksCallback {
        void done(ArrayList<Track> tracks, String error);
    }

    private interface JsonCallback {
        void done(JSONObject json, String error);
    }

    // =========================
    // TEMA
    // =========================

    private final int PURPLE = Color.rgb(128, 90, 245);

    private boolean dark = true;
    private int BG, SURFACE, SURFACE2, TEXT, MUTED, NAVBG;

    private void applyTheme() {
        if (dark) {
            BG = Color.rgb(7, 12, 25);
            SURFACE = Color.rgb(18, 26, 44);
            SURFACE2 = Color.rgb(29, 37, 60);
            TEXT = Color.rgb(245, 246, 255);
            MUTED = Color.rgb(145, 155, 180);
            NAVBG = Color.rgb(9, 14, 27);
        } else {
            BG = Color.rgb(247, 246, 252);
            SURFACE = Color.rgb(255, 255, 255);
            SURFACE2 = Color.rgb(236, 232, 248);
            TEXT = Color.rgb(24, 22, 40);
            MUTED = Color.rgb(110, 108, 130);
            NAVBG = Color.rgb(255, 255, 255);
        }

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(NAVBG);

        if (Build.VERSION.SDK_INT >= 23) {
            int flags = 0;
            if (!dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    // =========================
    // STATE UI
    // =========================

    private LinearLayout root;
    private LinearLayout header;
    private LinearLayout content;
    private LinearLayout bottomBar;
    private LinearLayout miniPlayer;

    private View miniCover, playerCover;
    private TextView miniTitle, miniArtist, miniPlay;
    private TextView playerTitle, playerArtist, playerPlay, playerFav,
            playerElapsed, playerTotal, playerShuffle, playerRepeat;
    private SeekBar playerSeek;
    private LinearLayout playerNextBox;
    private TextView playerNote;
    private EditText searchField;

    private String screen = "home";
    private String navTab = "home";
    private boolean dragging = false;

    // =========================
    // STATE PEMUTAR
    // =========================

    private MediaPlayer player;
    private boolean prepared = false;
    private boolean playing = false;
    private boolean shuffleOn = false;
    private boolean repeatOn = false;
    private ArrayList<Track> queue;
    private ArrayList<Track> baseQueue;
    private int index = -1;
    private Track current;
    private String queueSource = "";
    // 0 = cuplikan 30 detik, 1 = lagu penuh (server musik), 2 = file di HP
    private int playMode = 0;
    private boolean loading = false;
    private int playToken = 0;
    private boolean npReady = false;
    private String viewSource = "";
    private int nextKey = -1;

    // =========================
    // CACHE & EXECUTOR
    // =========================

    private final HashMap<String, ArrayList<Track>> cache = new HashMap<>();
    private final HashMap<String, Meta> metas = new HashMap<>();
    private final HashMap<String, String> thumbs = new HashMap<>();
    private final HashMap<String, Bitmap> bitmaps = new HashMap<>();
    private final HashSet<String> pending = new HashSet<>();

    private SharedPreferences prefs;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ExecutorService imgExec = Executors.newFixedThreadPool(3);

    private final Handler handler = new Handler(Looper.getMainLooper());

    // =========================
    // SIKLUS HIDUP
    // =========================

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences("enc", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", true);
        applyTheme();

        new File(getFilesDir(), "dl").mkdirs();

        showSplash();
        handler.post(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
        releasePlayer();
        executor.shutdownNow();
        imgExec.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (screen.equals("home") || screen.equals("splash")
                || screen.equals("login")) {
            super.onBackPressed();
        } else {
            showHome();
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player != null && prepared && playing
                    && playerSeek != null && !dragging) {
                try {
                    int d = player.getDuration();
                    int pos = player.getCurrentPosition();
                    playerSeek.setMax(d);
                    playerSeek.setProgress(pos);

                    if (playerElapsed != null) {
                        playerElapsed.setText(fmt(pos));
                    }
                    if (playerTotal != null) {
                        playerTotal.setText(fmt(d));
                    }
                } catch (Exception ignored) {
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    // =========================
    // HELPER UMUM
    // =========================

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable outline(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(1), stroke);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable grad(int radius) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(105, 49, 196),
                        Color.rgb(49, 40, 103),
                        Color.rgb(26, 31, 57)
                }
        );
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable tint(int i, int radius) {
        int[][] p = {
                {Color.rgb(120, 60, 200), Color.rgb(50, 40, 110)},
                {Color.rgb(200, 60, 90), Color.rgb(90, 30, 70)},
                {Color.rgb(230, 140, 40), Color.rgb(120, 50, 40)},
                {Color.rgb(50, 130, 210), Color.rgb(30, 50, 110)},
                {Color.rgb(40, 170, 150), Color.rgb(20, 70, 90)},
                {Color.rgb(200, 80, 170), Color.rgb(80, 40, 120)}
        };

        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, p[i % p.length]);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color,
                          boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return v;
    }

    private TextView centered(String value, int size, int color,
                              boolean bold) {
        TextView v = text(value, size, color, bold);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, -2, w);
    }

    private LinearLayout.LayoutParams mp(int w, int h, int l, int t,
                                         int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), lp(1, dp(height)));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void note(LinearLayout parent, String message) {
        TextView t = text(message, 12, MUTED, false);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        parent.addView(t, lp(-1, -2));
    }

    private String fmt(int ms) {
        int s = Math.max(0, ms / 1000);
        int sec = s % 60;
        return (s / 60) + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private int seconds(String duration) {
        try {
            String[] p = duration.split(":");
            if (p.length == 2) {
                return Integer.parseInt(p[0].trim()) * 60
                        + Integer.parseInt(p[1].trim());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String totalTime(ArrayList<Track> list) {
        int total = 0;
        for (Track t : list) total += seconds(t.duration);

        int h = total / 3600;
        int m = (total % 3600) / 60;

        return (h > 0 ? h + "j " : "") + m + "m";
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if (imm != null && v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void share(String message) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(i, "Bagikan"));
    }

    private boolean isOffline(String err) {
        return "OFFLINE".equals(err);
    }

    // =========================
    // GAMBAR (COVER)
    // =========================

    // Kotak cover: gradien + ikon, lalu gambar menimpa jika ada URL.
    private View cover(int radius, int glyph, String url) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(grad(radius));

        TextView g = centered("♫", glyph, WHITE, true);
        f.addView(g, new FrameLayout.LayoutParams(-1, -1));

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));

        f.setClipToOutline(true);

        if (url != null && !url.isEmpty()) {
            loadImage(url, iv);
        }

        return f;
    }

    private void setCoverUrl(View frame, String url) {
        if (frame instanceof FrameLayout && url != null
                && !url.isEmpty()) {
            View iv = ((FrameLayout) frame).getChildAt(1);
            if (iv instanceof ImageView) {
                loadImage(url, (ImageView) iv);
            }
        }
    }

    private void loadImage(final String url, final ImageView iv) {
        iv.setTag(url);

        Bitmap b = bitmaps.get(url);
        if (b != null) {
            iv.setImageBitmap(b);
            return;
        }

        imgExec.execute(() -> {
            try {
                HttpURLConnection c =
                        (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);

                InputStream in = c.getInputStream();
                final Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();

                if (bm != null) {
                    handler.post(() -> {
                        bitmaps.put(url, bm);
                        if (url.equals(iv.getTag())) {
                            iv.setImageBitmap(bm);
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private String thumbUrl(Track t) {
        if (!t.thumb.isEmpty()) return t.thumb;
        String u = thumbs.get(t.id);
        return u == null ? "" : u;
    }

    private String findImage(JSONObject o, int depth) {
        if (o == null || depth > 2) return "";

        String[] keys = {"thumbnail", "image", "cover", "img",
                "image_url", "cover_url"};

        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof String && ((String) v).startsWith("http")) {
                return (String) v;
            }
        }

        JSONArray imgs = o.optJSONArray("images");
        if (imgs != null && imgs.length() > 0) {
            Object f = imgs.opt(0);
            if (f instanceof JSONObject) {
                String u = str((JSONObject) f, "url");
                if (!u.isEmpty()) return u;
            } else if (f instanceof String) {
                return (String) f;
            }
        }

        String[] subs = {"track", "album", "data", "playlist"};
        for (String s : subs) {
            JSONObject sub = o.optJSONObject(s);
            if (sub != null) {
                String u = findImage(sub, depth + 1);
                if (!u.isEmpty()) return u;
            }
        }

        return "";
    }

    // Ambil cover lagu lewat /api/track/:id (hanya jika belum ada).
    private void ensureThumb(final Track t, final Runnable done) {
        if (t.id.isEmpty()) return;

        if (thumbs.containsKey(t.id)) {
            String u = thumbs.get(t.id);
            t.thumb = u;
            if (done != null && !u.isEmpty()) done.run();
            return;
        }

        if (pending.contains(t.id)) return;
        pending.add(t.id);

        imgExec.execute(() -> {
            String found = "";

            try {
                JSONObject o = new JSONObject(
                        fetch(API + "/track/" + t.id));
                found = findImage(o, 0);
            } catch (Exception ignored) {
            }

            final String u = found;

            handler.post(() -> {
                pending.remove(t.id);
                thumbs.put(t.id, u);
                t.thumb = u;
                if (done != null && !u.isEmpty()) done.run();
            });
        });
    }

    // =========================
    // JARINGAN & PARSING
    // =========================

    private String fetch(String address) throws Exception {
        try {
            HttpURLConnection c =
                    (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");

            int code = c.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = r.readLine()) != null) {
                sb.append(line);
            }

            r.close();
            c.disconnect();
            return sb.toString();
        } catch (java.net.UnknownHostException
                 | java.net.ConnectException
                 | java.net.SocketTimeoutException e) {
            throw new Exception("OFFLINE");
        }
    }

    private boolean fetchToFile(String address, File out) {
        try {
            HttpURLConnection c =
                    (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);

            if (c.getResponseCode() != 200) return false;

            InputStream in = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;

            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
            }

            fo.close();
            in.close();
            return true;
        } catch (Exception e) {
            out.delete();
            return false;
        }
    }

    private String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        return o.optString(key, "");
    }

    private JSONArray findArray(JSONObject o, int depth) {
        if (o == null || depth > 3) return null;

        String[] keys = {"tracks", "results", "data", "items", "songs",
                "albums", "artists", "playlists"};

        for (String k : keys) {
            JSONArray a = o.optJSONArray(k);
            if (a != null) return a;
        }

        for (String k : keys) {
            JSONObject sub = o.optJSONObject(k);
            if (sub != null) {
                JSONArray a = findArray(sub, depth + 1);
                if (a != null) return a;
            }
        }

        return null;
    }

    private String artistOf(JSONObject t) {
        String artist = str(t, "artist");
        if (!artist.isEmpty()) return artist;

        JSONArray as = t.optJSONArray("artists");
        if (as == null) return "";

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < as.length(); k++) {
            Object a = as.opt(k);
            String n = (a instanceof JSONObject)
                    ? str((JSONObject) a, "name")
                    : String.valueOf(a);
            if (sb.length() > 0) sb.append(", ");
            sb.append(n);
        }
        return sb.toString();
    }

    private ArrayList<Track> parseTracks(JSONArray arr) {
        ArrayList<Track> out = new ArrayList<>();
        if (arr == null) return out;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.optJSONObject(i);
            if (t == null) continue;

            String title = str(t, "title");
            if (title.isEmpty()) title = str(t, "name");
            if (title.isEmpty()) continue;

            String dur = str(t, "duration");
            if (dur.isEmpty() && t.has("duration_ms")) {
                dur = fmt(t.optInt("duration_ms"));
            } else if (dur.matches("\\d+")) {
                try {
                    dur = fmt((int) Long.parseLong(dur));
                } catch (Exception ignored) {
                }
            }

            String preview = str(t, "preview_url");
            if (preview.isEmpty()) preview = str(t, "preview");

            out.add(new Track(str(t, "id"), title, artistOf(t), dur,
                    preview, findImage(t, 0)));
        }

        return out;
    }

    private void getJson(final String path, final JsonCallback cb) {
        executor.execute(() -> {
            try {
                final JSONObject o = new JSONObject(fetch(API + path));
                handler.post(() -> cb.done(o, null));
            } catch (Exception e) {
                final String m = String.valueOf(e.getMessage());
                handler.post(() -> cb.done(null, m));
            }
        });
    }

    private void loadPlaylist(final String id, final TracksCallback cb) {
        ArrayList<Track> cached = cache.get(id);
        if (cached != null) {
            cb.done(cached, null);
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject root = new JSONObject(
                        fetch(API + "/playlist/" + id));
                JSONObject pl = root.optJSONObject("playlist");

                final ArrayList<Track> list = parseTracks(
                        findArray(pl != null ? pl : root, 0));

                if (list.isEmpty()) throw new Exception("Daftar kosong");

                final Meta m = new Meta();
                if (pl != null) {
                    m.name = str(pl, "name");
                    m.desc = str(pl, "description");
                    m.thumb = findImage(pl, 0);
                    m.url = str(pl, "url");
                    m.total = pl.optInt("total_tracks", list.size());
                }

                handler.post(() -> {
                    cache.put(id, list);
                    metas.put(id, m);
                    cb.done(list, null);
                });
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                handler.post(() -> cb.done(new ArrayList<Track>(), msg));
            }
        });
    }

    private void searchItems(final String query, final String type,
                             final TracksCallback cb) {
        executor.execute(() -> {
            try {
                String url = API + "/search?q="
                        + URLEncoder.encode(query, "UTF-8")
                        + "&type=" + type + "&limit=20";

                JSONObject root = new JSONObject(fetch(url));
                final ArrayList<Track> list =
                        parseTracks(findArray(root, 0));

                handler.post(() -> cb.done(list, null));
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                handler.post(() -> cb.done(new ArrayList<Track>(), msg));
            }
        });
    }

    // =========================
    // DATA LOKAL: FAVORIT, RIWAYAT, DOWNLOAD
    // =========================

    private ArrayList<Track> loadSaved(String key) {
        ArrayList<Track> out = new ArrayList<>();

        try {
            JSONArray arr = new JSONArray(prefs.getString(key, "[]"));

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Track(
                        o.optString("id"),
                        o.optString("t"),
                        o.optString("a"),
                        o.optString("d"),
                        o.optString("p"),
                        o.optString("i")
                ));
            }
        } catch (Exception ignored) {
        }

        return out;
    }

    private void saveList(String key, ArrayList<Track> list) {
        JSONArray arr = new JSONArray();

        try {
            for (Track t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("t", t.title);
                o.put("a", t.artist);
                o.put("d", t.duration);
                o.put("p", t.preview);
                o.put("i", thumbUrl(t));
                arr.put(o);
            }
        } catch (Exception ignored) {
        }

        prefs.edit().putString(key, arr.toString()).apply();
    }

    private boolean same(Track a, Track b) {
        if (a == null || b == null) return false;
        if (!a.id.isEmpty() && !b.id.isEmpty()) {
            return a.id.equals(b.id);
        }
        return a.title.equals(b.title) && a.artist.equals(b.artist);
    }

    private boolean isFav(Track t) {
        for (Track f : loadSaved("fav")) {
            if (same(f, t)) return true;
        }
        return false;
    }

    private void toggleFav(Track t) {
        if (t == null) return;

        ArrayList<Track> favs = loadSaved("fav");
        boolean removed = false;

        for (int i = 0; i < favs.size(); i++) {
            if (same(favs.get(i), t)) {
                favs.remove(i);
                removed = true;
                break;
            }
        }

        if (!removed) favs.add(0, t);

        saveList("fav", favs);
        updatePlayerUI();
        toast(removed ? "Dihapus dari favorit" : "Ditambah ke favorit");
    }

    private void addAllFav(ArrayList<Track> list) {
        ArrayList<Track> favs = loadSaved("fav");
        int added = 0;

        for (Track t : list) {
            boolean has = false;
            for (Track f : favs) {
                if (same(f, t)) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                favs.add(t);
                added++;
            }
        }

        saveList("fav", favs);
        toast(added + " lagu disimpan ke favorit");
    }

    private void addHistory(Track t) {
        ArrayList<Track> hist = loadSaved("hist");

        for (int i = 0; i < hist.size(); i++) {
            if (same(hist.get(i), t)) {
                hist.remove(i);
                break;
            }
        }

        hist.add(0, t);

        while (hist.size() > 30) {
            hist.remove(hist.size() - 1);
        }

        saveList("hist", hist);
    }

    private File localFile(Track t) {
        String key = t.id.isEmpty()
                ? Integer.toHexString((t.title + t.artist).hashCode())
                : t.id;
        return new File(new File(getFilesDir(), "dl"), key + ".mp3");
    }

    private void addDownload(Track t) {
        ArrayList<Track> dl = loadSaved("dl");

        for (Track x : dl) {
            if (same(x, t)) return;
        }

        dl.add(0, t);
        saveList("dl", dl);
    }

    private void downloadTrack(final Track t) {
        if (t.preview.isEmpty() && !fullEnabled()) {
            toast("Lagu ini tidak punya preview");
            return;
        }

        final File f = localFile(t);

        if (f.exists()) {
            addDownload(t);
            toast("Sudah diunduh");
            return;
        }

        toast("Mengunduh...");

        executor.execute(() -> {
            final boolean ok = fetchTrack(t, f);

            handler.post(() -> {
                if (ok) {
                    addDownload(t);
                    toast("Diunduh: " + t.title);
                } else {
                    toast("Gagal mengunduh");
                }
            });
        });
    }

    private void downloadAll(final ArrayList<Track> list) {
        toast("Mengunduh " + Math.min(30, list.size()) + " lagu...");

        executor.execute(() -> {
            final ArrayList<Track> okList = new ArrayList<>();
            int n = Math.min(30, list.size());

            for (int i = 0; i < n; i++) {
                Track t = list.get(i);
                if (t.preview.isEmpty() && !fullEnabled()) continue;

                File f = localFile(t);
                if (f.exists() || fetchTrack(t, f)) {
                    okList.add(t);
                }
            }

            handler.post(() -> {
                for (Track t : okList) addDownload(t);
                toast(okList.size() + " lagu diunduh");
            });
        });
    }

    // =========================
    // PEMUTARAN AUDIO
    // =========================

    // =========================
    // LAGU PENUH (NewPipeExtractor)
    // =========================

    private static final long CHUNK = 2L * 1024 * 1024;
    private static final String FIREFOX_UA =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0";

    private boolean fullEnabled() {
        return prefs.getBoolean("full", true);
    }

    private String sourceNote() {
        if (loading) return "Memuat lagu penuh...";
        if (playMode == 1) return "Lagu penuh • YouTube Music";
        if (playMode == 2) return "Tersimpan di HP";
        return "Cuplikan 30 detik dari Spotify";
    }

    // Downloader sederhana (HttpURLConnection) untuk NewPipeExtractor.
    private static class NpDownloader extends Downloader {
        @Override
        public Response execute(Request request)
                throws IOException, ReCaptchaException {
            HttpURLConnection c =
                    (HttpURLConnection) new URL(request.url()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestMethod(request.httpMethod());

            boolean hasUa = false;
            for (Map.Entry<String, List<String>> e
                    : request.headers().entrySet()) {
                String k = e.getKey();
                if (k == null || k.equalsIgnoreCase("Accept-Encoding")) {
                    continue;
                }
                if (k.equalsIgnoreCase("User-Agent")) hasUa = true;
                for (String v : e.getValue()) {
                    c.addRequestProperty(k, v);
                }
            }
            if (!hasUa) c.setRequestProperty("User-Agent", FIREFOX_UA);

            byte[] body = request.dataToSend();
            if (body != null && body.length > 0) {
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(body);
                os.close();
            }

            int code = c.getResponseCode();
            if (code == 429) {
                c.disconnect();
                throw new ReCaptchaException(
                        "reCaptcha Challenge requested", request.url());
            }

            String message = c.getResponseMessage();
            InputStream in = code >= 400 ? c.getErrorStream()
                    : c.getInputStream();

            String text = "";
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                }
                in.close();
                text = bo.toString("UTF-8");
            }

            Map<String, List<String>> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> e
                    : c.getHeaderFields().entrySet()) {
                if (e.getKey() != null) headers.put(e.getKey(), e.getValue());
            }

            String latest = c.getURL().toString();
            c.disconnect();

            return new Response(code, message, headers, text, latest);
        }
    }

    private synchronized void npInit() {
        if (npReady) return;
        NewPipe.init(new NpDownloader(), new Localization("id", "ID"),
                new ContentCountry("ID"));
        npReady = true;
    }

    private int npScore(AudioStream a) {
        int score = a.getAverageBitrate();
        if (a.getFormat() == MediaFormat.M4A) score += 1000000;
        return score;
    }

    // Cari lagu di YouTube Music lalu ambil URL audio terbaik.
    private String npResolve(Track t) throws Exception {
        npInit();

        StreamingService yt = ServiceList.YouTube;
        String query = (t.title + " " + t.artist).trim();

        SearchExtractor se = yt.getSearchExtractor(query,
                java.util.Collections.singletonList(
                        YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "");
        se.fetchPage();

        List<InfoItem> items = se.getInitialPage().getItems();
        int want = seconds(t.duration);

        StreamInfoItem best = null;
        long bestDiff = Long.MAX_VALUE;

        for (InfoItem it : items) {
            if (!(it instanceof StreamInfoItem)) continue;
            StreamInfoItem si = (StreamInfoItem) it;

            long diff = want > 0 ? Math.abs(si.getDuration() - want) : 0;
            if (best == null || diff < bestDiff) {
                best = si;
                bestDiff = diff;
            }
        }

        if (best == null) throw new Exception("Lagu tidak ditemukan");
        if (want > 0 && bestDiff > 15) {
            throw new Exception("Durasi tidak cocok");
        }

        StreamInfo info = StreamInfo.getInfo(yt, best.getUrl());

        AudioStream pick = null;
        for (AudioStream a : info.getAudioStreams()) {
            if (!a.isUrl()
                    || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) {
                continue;
            }

            AudioTrackType tt = a.getAudioTrackType();
            if (tt != null && tt != AudioTrackType.ORIGINAL) continue;

            if (pick == null || npScore(a) > npScore(pick)) pick = a;
        }

        if (pick == null) throw new Exception("Tidak ada stream audio");
        return pick.getContent();
    }

    // Unduh bertahap (parameter range) seperti NewPipe, simpan ke file.
    private boolean npDownload(String url, File out) {
        File tmp = new File(out.getParentFile(), out.getName() + ".part");

        try {
            out.getParentFile().mkdirs();

            String ua = FIREFOX_UA;
            if (YoutubeParsingHelper.isAndroidStreamingUrl(url)) {
                ua = YoutubeParsingHelper.getAndroidUserAgent(null);
            } else if (YoutubeParsingHelper.isIosStreamingUrl(url)) {
                ua = YoutubeParsingHelper.getIosUserAgent(null);
            }

            long total = -1;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[?&]clen=(\\d+)").matcher(url);
            if (m.find()) total = Long.parseLong(m.group(1));

            FileOutputStream fo = new FileOutputStream(tmp);
            long pos = 0;

            while (total < 0 || pos < total) {
                long end = pos + CHUNK - 1;
                if (total > 0) end = Math.min(end, total - 1);

                HttpURLConnection c = (HttpURLConnection)
                        new URL(url + "&range=" + pos + "-" + end)
                                .openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", ua);

                int code = c.getResponseCode();
                if (code != 200 && code != 206) {
                    c.disconnect();
                    if (pos == 0) {
                        fo.close();
                        tmp.delete();
                        return false;
                    }
                    break;
                }

                InputStream in = c.getInputStream();
                byte[] buf = new byte[16384];
                long got = 0;
                int n;

                while ((n = in.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    got += n;
                }

                in.close();
                c.disconnect();

                if (got == 0) break;
                pos += got;
                if (total < 0 && got < CHUNK) break;
            }

            fo.close();

            if (pos == 0 || (total > 0 && pos < total)) {
                tmp.delete();
                return false;
            }

            out.delete();
            return tmp.renameTo(out);
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
    }

    private File fullFile(Track t) {
        String key = t.id.isEmpty()
                ? Integer.toHexString((t.title + t.artist).hashCode())
                : t.id;
        return new File(new File(getCacheDir(), "full"), key + ".m4a");
    }

    // Jaga ukuran cache lagu penuh (maks ± 200 MB).
    private void trimFullCache() {
        File dir = new File(getCacheDir(), "full");
        File[] files = dir.listFiles();
        if (files == null) return;

        long total = 0;
        for (File f : files) total += f.length();
        if (total < 200L * 1024 * 1024) return;

        java.util.Arrays.sort(files, (x, y) ->
                Long.compare(x.lastModified(), y.lastModified()));

        for (File f : files) {
            if (total < 150L * 1024 * 1024) break;
            total -= f.length();
            f.delete();
        }
    }

    // Unduh lagu ke HP: utamakan lagu penuh, cadangan cuplikan.
    private boolean fetchTrack(Track t, File f) {
        if (fullEnabled()) {
            try {
                File cached = fullFile(t);

                if (cached.exists() && cached.length() > 0) {
                    f.getParentFile().mkdirs();
                    java.io.FileInputStream in =
                            new java.io.FileInputStream(cached);
                    FileOutputStream out = new FileOutputStream(f);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    out.close();
                    return true;
                }

                if (npDownload(npResolve(t), f)) return true;
            } catch (Throwable ignored) {
            }
        }

        return !t.preview.isEmpty() && fetchToFile(t.preview, f);
    }

    private String tag(String kind, String name) {
        return kind + "|" + name;
    }

    private void playQueue(ArrayList<Track> list, int start,
                           String source) {
        if (list == null || list.isEmpty()) return;

        queueSource = source == null ? "" : source;
        baseQueue = list;
        queue = list;
        index = start;

        if (shuffleOn) {
            shuffleQueue();
        }

        playCurrent();
    }

    private void playShuffled(ArrayList<Track> list, String source) {
        if (list == null || list.isEmpty()) return;

        ArrayList<Track> copy = new ArrayList<>(list);
        Collections.shuffle(copy);

        queueSource = source == null ? "" : source;
        baseQueue = list;
        queue = copy;
        index = 0;
        shuffleOn = true;
        playCurrent();
    }

    private void shuffleQueue() {
        if (baseQueue == null || index < 0 || index >= queue.size()) return;

        Track now = queue.get(index);
        ArrayList<Track> rest = new ArrayList<>(baseQueue);

        for (int i = 0; i < rest.size(); i++) {
            if (same(rest.get(i), now)) {
                rest.remove(i);
                break;
            }
        }

        Collections.shuffle(rest);
        rest.add(0, now);

        queue = rest;
        index = 0;
    }

    private void toggleShuffle() {
        shuffleOn = !shuffleOn;

        if (queue != null && current != null) {
            if (shuffleOn) {
                shuffleQueue();
            } else if (baseQueue != null) {
                queue = baseQueue;
                index = 0;
                for (int i = 0; i < queue.size(); i++) {
                    if (same(queue.get(i), current)) {
                        index = i;
                        break;
                    }
                }
            }
        }

        updatePlayerUI();
    }

    private void toggleRepeat() {
        repeatOn = !repeatOn;
        updatePlayerUI();
    }

    private void playPlaylist(String id) {
        toast("Memuat...");
        loadPlaylist(id, (list, err) -> {
            if (list.isEmpty()) {
                toast(isOffline(err) ? "Tidak ada koneksi internet"
                        : "Gagal memuat: " + err);
            } else {
                Meta m = metas.get(id);
                String nm = (m != null && !m.name.isEmpty())
                        ? m.name : "Playlist";
                playQueue(list, 0, tag("PLAYLIST", nm));
            }
        });
    }

    private void releasePlayer() {
        prepared = false;

        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void playCurrent() {
        startPlayback(false);
    }

    private void startPlayback(boolean forcePreview) {
        if (queue == null || index < 0 || index >= queue.size()) return;

        if (!forcePreview) {
            current = queue.get(index);
            addHistory(current);
        }

        playToken++;
        final int token = playToken;

        releasePlayer();
        playing = false;
        loading = false;

        File local = localFile(current);
        boolean useLocal = local.exists();
        boolean useFull = !useLocal && !forcePreview && fullEnabled();

        if (useLocal) {
            playMode = 2;
            playSource(local.getAbsolutePath());
            return;
        }

        if (useFull) {
            final File cached = fullFile(current);

            if (cached.exists() && cached.length() > 0) {
                playMode = 1;
                playSource(cached.getAbsolutePath());
                return;
            }

            // Belum ada di cache: cari di YouTube Music lalu unduh dulu.
            playMode = 1;
            loading = true;
            updatePlayerUI();

            final Track tr = current;

            executor.execute(() -> {
                String err = null;

                try {
                    trimFullCache();
                    String url = npResolve(tr);
                    if (!npDownload(url, cached)) {
                        throw new Exception("Unduhan gagal");
                    }
                } catch (Throwable e) {
                    err = String.valueOf(e.getMessage());
                    if (err.length() > 60) err = err.substring(0, 60);
                }

                final String fe = err;

                handler.post(() -> {
                    if (token != playToken) return;
                    loading = false;

                    if (fe == null) {
                        playSource(cached.getAbsolutePath());
                    } else if (!tr.preview.isEmpty()) {
                        toast("Lagu penuh gagal (" + fe + "), pakai cuplikan");
                        startPlayback(true);
                    } else {
                        playMode = 0;
                        toast("Lagu penuh gagal: " + fe);
                        updatePlayerUI();
                    }
                });
            });
            return;
        }

        playMode = 0;

        if (current.preview.isEmpty()) {
            toast("Lagu ini tidak punya preview");
            updatePlayerUI();
            return;
        }

        playSource(current.preview);
    }

    private void playSource(String source) {
        updatePlayerUI();

        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());

            player.setDataSource(source);

            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                playing = true;
                updatePlayerUI();
            });

            player.setOnCompletionListener(mp -> {
                playing = false;
                if (repeatOn) {
                    playCurrent();
                } else {
                    next();
                }
            });

            player.setOnErrorListener((mp, what, extra) -> {
                boolean wasFull = (playMode == 1);

                playing = false;
                prepared = false;

                if (wasFull && current != null) {
                    // File cache rusak: hapus dan pakai cuplikan.
                    fullFile(current).delete();

                    if (!current.preview.isEmpty()) {
                        toast("Lagu penuh gagal diputar, pakai cuplikan");
                        handler.post(() -> startPlayback(true));
                        return true;
                    }
                }

                toast("Gagal memutar lagu");
                updatePlayerUI();
                return true;
            });

            player.prepareAsync();
        } catch (Exception e) {
            toast("Gagal memutar lagu");
        }
    }

    private void togglePlay() {
        if (current == null) {
            toast("Pilih lagu dulu");
            return;
        }

        if (loading) return;

        if (player == null) {
            playCurrent();
            return;
        }

        try {
            if (!prepared) return;

            if (player.isPlaying()) {
                player.pause();
                playing = false;
            } else {
                player.start();
                playing = true;
            }
        } catch (Exception e) {
            playCurrent();
            return;
        }

        updatePlayerUI();
    }

    private void next() {
        if (queue != null && index + 1 < queue.size()) {
            index++;
            playCurrent();
        } else {
            playing = false;
            updatePlayerUI();
        }
    }

    private void previous() {
        if (queue == null) return;

        try {
            if (player != null && prepared
                    && player.getCurrentPosition() > 3000) {
                player.seekTo(0);
                return;
            }
        } catch (Exception ignored) {
        }

        if (index > 0) {
            index--;
            playCurrent();
        }
    }

    // Memperbarui mini player & layar pemutar tanpa membangun ulang layar.
    private void updatePlayerUI() {
        String icon = loading ? "…" : (playing ? "Ⅱ" : "▶");

        if (current != null) {
            if (miniTitle != null) miniTitle.setText(current.title);
            if (miniArtist != null) miniArtist.setText(current.artist);
            if (playerTitle != null) playerTitle.setText(current.title);
            if (playerArtist != null) playerArtist.setText(current.artist);

            if (playerFav != null) {
                playerFav.setText(isFav(current) ? "♥" : "♡");
            }

            String u = thumbUrl(current);
            if (!u.isEmpty()) {
                if (miniCover != null) setCoverUrl(miniCover, u);
                if (playerCover != null) setCoverUrl(playerCover, u);
            } else {
                ensureThumb(current, () -> updatePlayerUI());
            }

            if (miniPlayer != null && !screen.equals("player")) {
                miniPlayer.setVisibility(View.VISIBLE);
            }
        }

        if (miniPlay != null) miniPlay.setText(icon);
        if (playerPlay != null) playerPlay.setText(icon);

        // Daftar "Berikutnya" ikut berganti saat lagu pindah.
        if (playerNextBox != null) renderNextUp();
        if (playerNote != null) playerNote.setText(sourceNote());

        if (playerShuffle != null) {
            playerShuffle.setTextColor(shuffleOn ? PURPLE : MUTED);
        }
        if (playerRepeat != null) {
            playerRepeat.setTextColor(repeatOn ? PURPLE : MUTED);
        }
    }

    // =========================
    // CANGKANG LAYAR
    // =========================

    private void applyInsets(View view) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;

            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }

            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        view.requestApplyInsets();
    }

    private void createShell(String destination, String tab) {
        screen = destination;
        navTab = tab;
        viewSource = "";

        miniCover = null;
        playerCover = null;
        miniTitle = null;
        miniArtist = null;
        miniPlay = null;
        playerTitle = null;
        playerArtist = null;
        playerPlay = null;
        playerFav = null;
        playerElapsed = null;
        playerTotal = null;
        playerShuffle = null;
        playerRepeat = null;
        playerSeek = null;
        playerNextBox = null;
        playerNote = null;

        root = column();
        root.setBackgroundColor(BG);

        setContentView(root);
        applyInsets(root);

        header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(5), dp(12), dp(5));
        root.addView(header, lp(-1, dp(56)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);

        content = column();
        content.setPadding(dp(16), dp(4), dp(16), dp(18));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        createMiniPlayer();
        createBottomBar();
    }

    // =========================
    // HEADER
    // =========================

    private void headerBrand() {
        header.removeAllViews();

        header.addView(text("ENC Music", 20, TEXT, true), weight(1));

        TextView bell = centered("🔔", 17, TEXT, false);
        header.addView(bell, lp(dp(40), dp(42)));
        bell.setOnClickListener(v -> showNotifications());

        TextView avatar = centered("E", 14, WHITE, true);
        avatar.setBackground(round(PURPLE, 50));
        header.addView(avatar, mp(dp(34), dp(34), 4, 0, 0, 0));
        avatar.setOnClickListener(v -> showSettings());
    }

    private void headerTitle(String title) {
        header.removeAllViews();
        header.addView(text(title, 22, TEXT, true), weight(1));
    }

    private void headerBack(String title, String rightGlyph,
                            View.OnClickListener rightClick) {
        header.removeAllViews();

        TextView back = centered("‹", 31, TEXT, false);
        header.addView(back, lp(dp(38), dp(45)));
        back.setOnClickListener(v -> showHome());

        header.addView(text(title, 18, TEXT, true), weight(1));

        if (rightGlyph != null) {
            TextView right = centered(rightGlyph, 20, TEXT, true);
            header.addView(right, lp(dp(42), dp(45)));
            if (rightClick != null) right.setOnClickListener(rightClick);
        }
    }

    // =========================
    // MINI PLAYER & NAVIGASI BAWAH
    // =========================

    private void createMiniPlayer() {
        miniPlayer = row();
        miniPlayer.setPadding(dp(8), dp(7), dp(8), dp(7));
        miniPlayer.setBackground(round(SURFACE2, 17));

        if (current == null || screen.equals("player")) {
            miniPlayer.setVisibility(View.GONE);
        }

        root.addView(miniPlayer, mp(-1, dp(60), 10, 4, 10, 5));

        miniCover = cover(12, 20, current != null ? thumbUrl(current) : "");
        miniPlayer.addView(miniCover, lp(dp(46), dp(46)));

        LinearLayout labels = column();
        labels.setPadding(dp(10), 0, dp(5), 0);

        miniTitle = text(current != null ? current.title : "", 12,
                TEXT, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);

        miniArtist = text(current != null ? current.artist : "", 10,
                MUTED, false);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);

        labels.addView(miniTitle);
        labels.addView(miniArtist);

        miniPlayer.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        miniPlay = centered(playing ? "Ⅱ" : "▶", 20, TEXT, true);
        miniPlayer.addView(miniPlay, lp(dp(40), dp(45)));

        TextView nxt = centered("▶|", 16, TEXT, true);
        miniPlayer.addView(nxt, lp(dp(40), dp(45)));

        miniPlayer.setOnClickListener(v -> {
            if (current != null) showPlayer();
        });
        miniPlay.setOnClickListener(v -> togglePlay());
        nxt.setOnClickListener(v -> next());
    }

    private void createBottomBar() {
        bottomBar = row();
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(4), dp(3), dp(4), dp(3));
        bottomBar.setBackgroundColor(NAVBG);

        root.addView(bottomBar, lp(-1, dp(55)));

        addBottom("⌂", "Beranda", "home");
        addBottom("⌕", "Eksplor", "explore");
        addBottom("▤", "Perpustakaan", "library");
    }

    private void addBottom(String icon, String label,
                           final String destination) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);

        int color = navTab.equals(destination) ? PURPLE : MUTED;

        item.addView(centered(icon, 21, color, true));
        item.addView(centered(label, 9, color, true));

        bottomBar.addView(item, new LinearLayout.LayoutParams(0, -1, 1));

        item.setOnClickListener(v -> {
            if (destination.equals("home")) {
                showHome();
            } else if (destination.equals("explore")) {
                showExplore();
            } else {
                showLibrary();
            }
        });
    }

    // =========================
    // KOMPONEN UMUM
    // =========================

    private void section(String title, String action,
                         final Runnable callback) {
        LinearLayout line = row();
        line.addView(text(title, 16, TEXT, true), weight(1));

        if (action != null && !action.isEmpty()) {
            TextView more = text(action, 11, PURPLE, true);
            line.addView(more);
            more.setOnClickListener(v -> {
                if (callback != null) callback.run();
            });
        }

        content.addView(line, mp(-1, dp(43), 0, 10, 0, 0));
    }

    private void addSearchBar(final Runnable callback) {
        LinearLayout search = row();
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(round(SURFACE, 14));

        content.addView(search, lp(-1, dp(47)));

        search.addView(text("⌕", 22, MUTED, false));

        TextView hint = text("  Cari lagu, artis, album, atau playlist...",
                12, MUTED, false);
        search.addView(hint, new LinearLayout.LayoutParams(0, -1, 1));

        search.setOnClickListener(v -> callback.run());
    }

    private TextView chip(String label, boolean active) {
        TextView c = centered(label, 11, active ? WHITE : MUTED, true);
        c.setPadding(dp(14), 0, dp(14), 0);
        c.setBackground(round(active ? PURPLE : SURFACE, 20));
        return c;
    }

    private TextView bigButton(String label, boolean primary) {
        TextView b = centered(label, 13, primary ? WHITE : TEXT, true);
        b.setBackground(primary ? round(PURPLE, 14)
                : round(SURFACE2, 14));
        return b;
    }

    private void addSongRow(LinearLayout parent,
                            final ArrayList<Track> list, final int i,
                            boolean numbered, String suffix) {
        final Track t = list.get(i);
        final String src = viewSource;

        LinearLayout item = row();
        item.setPadding(dp(4), dp(5), dp(2), dp(5));
        parent.addView(item, lp(-1, dp(62)));

        if (numbered) {
            TextView n = centered(String.valueOf(i + 1), 11, MUTED, false);
            item.addView(n, lp(dp(22), dp(46)));
        }

        final View cv = cover(10, 18, thumbUrl(t));
        item.addView(cv, lp(dp(46), dp(46)));

        if (thumbUrl(t).isEmpty() && i < THUMB_ROWS) {
            ensureThumb(t, () -> setCoverUrl(cv, thumbUrl(t)));
        }

        LinearLayout labels = column();
        labels.setPadding(dp(10), 0, dp(6), 0);

        boolean active = same(current, t);

        TextView title = text(t.title, 13, active ? PURPLE : TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView artist = text(t.artist + suffix, 10, MUTED, false);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);

        labels.addView(title);
        labels.addView(artist);

        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        if (!t.duration.isEmpty()) {
            item.addView(text(t.duration, 10, MUTED, false));
        }

        final TextView more = centered("⋮", 18, MUTED, true);
        item.addView(more, lp(dp(32), dp(46)));
        more.setOnClickListener(v -> showTrackMenu(more, t));

        item.setOnClickListener(v -> playQueue(list, i, src));
    }

    private void showTrackMenu(View anchor, final Track t) {
        if (t == null) return;

        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0,
                isFav(t) ? "Hapus dari favorit" : "Tambah ke favorit");
        m.getMenu().add(0, 2, 1, "Download cuplikan");
        m.getMenu().add(0, 3, 2, "Bagikan");

        m.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == 1) {
                toggleFav(t);
            } else if (id == 2) {
                downloadTrack(t);
            } else {
                share(t.title + " - " + t.artist
                        + (t.id.isEmpty() ? ""
                        : "\nhttps://open.spotify.com/track/" + t.id));
            }
            return true;
        });

        m.show();
    }

    // Baris generik: artis / album / playlist (hasil pencarian).
    private void addItemRow(LinearLayout parent, final Track t,
                            final String kind) {
        LinearLayout item = row();
        item.setPadding(dp(4), dp(5), dp(6), dp(5));
        parent.addView(item, lp(-1, dp(62)));

        final View cv = cover(kind.equals("artist") ? 40 : 10, 18,
                thumbUrl(t));
        item.addView(cv, lp(dp(46), dp(46)));

        LinearLayout labels = column();
        labels.setPadding(dp(10), 0, dp(6), 0);

        TextView title = text(t.title, 13, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        String sub;
        if (kind.equals("artist")) {
            sub = "Artis";
        } else if (kind.equals("album")) {
            sub = (t.artist.isEmpty() ? "" : t.artist + " • ") + "Album";
        } else {
            sub = "Playlist";
        }

        labels.addView(title);
        labels.addView(text(sub, 10, MUTED, false));

        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(text("›", 20, MUTED, false));

        item.setOnClickListener(v -> {
            if (t.id.isEmpty()) {
                toast("ID tidak tersedia");
            } else if (kind.equals("artist")) {
                showArtist(t.id, t.title);
            } else if (kind.equals("album")) {
                showAlbum(t.id, t.title);
            } else {
                showPlaylist(t.id, t.title);
            }
        });
    }

    private void addShortcut(LinearLayout parent, String icon,
                             String label, final Runnable callback) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);

        TextView symbol = centered(icon, 22, PURPLE, true);
        symbol.setBackground(round(SURFACE2, 18));
        item.addView(symbol, lp(dp(55), dp(55)));

        TextView caption = centered(label, 10, MUTED, true);
        item.addView(caption, lp(-1, dp(28)));

        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        item.setOnClickListener(v -> callback.run());
    }

    private View addCard(LinearLayout parent, String title,
                         String subtitle, int tintIndex, String url,
                         final Runnable callback) {
        LinearLayout item = column();

        parent.addView(item, mp(0, -1, 3, 0, 7, 0));
        ((LinearLayout.LayoutParams) item.getLayoutParams()).weight = 1;

        View cv = cover(14, 30, url);
        if (url == null || url.isEmpty()) {
            cv.setBackground(tint(tintIndex, 14));
        }
        item.addView(cv, lp(-1, dp(95)));

        TextView name = text(title, 11, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(name, mp(-2, -2, 0, 5, 0, 0));

        TextView description = text(subtitle, 9, MUTED, false);
        description.setSingleLine(true);
        description.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(description);

        item.setOnClickListener(v -> callback.run());
        return cv;
    }

    // =========================
    // SPLASH & LOGIN
    // =========================

    private void showSplash() {
        screen = "splash";

        LinearLayout r = column();
        r.setBackgroundColor(BG);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        setContentView(r);

        r.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        r.addView(centered("♫", 78, PURPLE, true), lp(-2, -2));

        TextView name = centered("ENC Music", 30, TEXT, true);
        SpannableString sp = new SpannableString("ENC Music");
        sp.setSpan(new ForegroundColorSpan(PURPLE), 4, 9,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setText(sp);
        r.addView(name, lp(-2, -2));

        r.addView(centered("Listen • Feel • Be Better", 12, PURPLE, false),
                mp(-2, -2, 0, 8, 0, 0));

        r.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(SURFACE2, 4));
        r.addView(track, mp(dp(220), dp(4), 0, 0, 0, 60));

        final View fill = new View(this);
        fill.setBackground(round(PURPLE, 4));
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));

        ValueAnimator anim = ValueAnimator.ofInt(0, dp(220));
        anim.setDuration(1600);
        anim.addUpdateListener(a -> {
            FrameLayout.LayoutParams p =
                    (FrameLayout.LayoutParams) fill.getLayoutParams();
            p.width = (Integer) a.getAnimatedValue();
            fill.setLayoutParams(p);
        });
        anim.start();

        handler.postDelayed(() -> {
            if (!screen.equals("splash")) return;

            if (prefs.getBoolean("logged", false)) {
                showHome();
            } else {
                showLogin();
            }
        }, 1800);
    }

    private TextView authButton(String label, int fill, int fg) {
        TextView b = centered(label, 13, fg, true);
        b.setBackground(round(fill, 12));
        b.setOnClickListener(v -> toast("Segera hadir"));
        return b;
    }

    private EditText authField(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setInputType(password
                ? (InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                : InputType.TYPE_CLASS_TEXT);
        return e;
    }

    private void showLogin() {
        screen = "login";

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout r = column();
        r.setPadding(dp(24), dp(40), dp(24), dp(24));
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(r);

        setContentView(scroll);
        applyInsets(scroll);

        r.addView(centered("♫", 56, PURPLE, true), lp(-2, -2));
        r.addView(centered("ENC Music", 24, TEXT, true), lp(-2, -2));
        r.addView(centered("Masuk untuk melanjutkan", 12, MUTED, false),
                mp(-2, -2, 0, 6, 0, 22));

        r.addView(authButton("G   Masuk dengan Google", PURPLE, WHITE),
                lp(-1, dp(46)));
        r.addView(authButton("f   Masuk dengan Facebook", SURFACE2, TEXT),
                mp(-1, dp(46), 0, 10, 0, 0));
        r.addView(authButton("   Masuk dengan Apple", SURFACE2, TEXT),
                mp(-1, dp(46), 0, 10, 0, 0));

        r.addView(centered("atau", 11, MUTED, false), mp(-2, dp(36),
                0, 6, 0, 0));

        LinearLayout emailBox = row();
        emailBox.setPadding(dp(14), 0, dp(14), 0);
        emailBox.setBackground(outline(SURFACE, SURFACE2, 12));
        emailBox.addView(text("@", 16, MUTED, true), lp(dp(26), -1));
        emailBox.addView(authField("Email atau nomor telepon", false),
                new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(emailBox, lp(-1, dp(48)));

        LinearLayout passBox = row();
        passBox.setPadding(dp(14), 0, dp(14), 0);
        passBox.setBackground(outline(SURFACE, SURFACE2, 12));
        passBox.addView(text("•", 18, MUTED, true), lp(dp(26), -1));
        passBox.addView(authField("Kata sandi", true),
                new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(passBox, mp(-1, dp(48), 0, 10, 0, 0));

        TextView enter = bigButton("Masuk", true);
        r.addView(enter, mp(-1, dp(48), 0, 16, 0, 0));
        enter.setOnClickListener(v -> {
            hideKeyboard();
            prefs.edit().putBoolean("logged", true).apply();
            showHome();
        });

        LinearLayout links = row();
        TextView forgot = text("Lupa kata sandi?", 11, PURPLE, false);
        TextView signup = text("Daftar akun", 11, PURPLE, false);
        signup.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        links.addView(forgot, new LinearLayout.LayoutParams(0, -2, 1));
        links.addView(signup, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(links, mp(-1, dp(44), 0, 8, 0, 0));

        forgot.setOnClickListener(v -> toast("Segera hadir"));
        signup.setOnClickListener(v -> toast("Segera hadir"));
    }

    // =========================
    // BERANDA
    // =========================

    private void showHome() {
        createShell("home", "home");
        headerBrand();

        addSearchBar(() -> showSearch(null));

        LinearLayout banner = column();
        banner.setPadding(dp(17), dp(16), dp(15), dp(14));
        banner.setBackground(grad(20));
        content.addView(banner, mp(-1, dp(150), 0, 12, 0, 0));

        banner.addView(text("Good Vibes Only", 17, WHITE, true));
        gap(banner, 6);

        TextView msg = text("Playlist pilihan untuk hari yang lebih baik.",
                12, Color.rgb(220, 214, 245), false);
        msg.setMaxLines(2);
        banner.addView(msg);

        gap(banner, 14);

        TextView play = centered("▶", 15, WHITE, true);
        play.setBackground(round(PURPLE, 12));
        banner.addView(play, lp(dp(46), dp(40)));
        play.setOnClickListener(v -> playPlaylist(PL_NEW));

        LinearLayout shortcuts = row();
        shortcuts.setGravity(Gravity.CENTER);
        content.addView(shortcuts, mp(-1, dp(92), 0, 9, 0, 0));

        addShortcut(shortcuts, "♫", "Playlist", () -> showPlaylistList("Untukmu"));
        addShortcut(shortcuts, "♟", "Artis", () -> showSearchType("artist"));
        addShortcut(shortcuts, "▣", "Album", () -> showSearchType("album"));
        addShortcut(shortcuts, "▦", "Genre", () -> showExplore());

        section("Untuk Kamu", "Lihat semua", () -> showPlaylistList("Untukmu"));

        LinearLayout cards = row();
        cards.setGravity(Gravity.TOP);
        content.addView(cards, lp(-1, dp(143)));

        addCard(cards, "Daily Mix", "Dari selera musikmu", 0, "",
                () -> showDailyMix());

        final View newCover = addCard(cards, "Rilis Terbaru",
                "Lagu-lagu baru", 3, "",
                () -> showPlaylist(PL_NEW, "Rilis Terbaru"));

        final View topCover = addCard(cards, "Populer",
                "Yang paling didengar", 1, "",
                () -> showPlaylist(PL_TOP, "Top Hits Indonesia"));

        // Cover kartu diambil dari thumbnail playlist.
        loadPlaylist(PL_NEW, (list, err) -> {
            if (isOffline(err) && screen.equals("home")) {
                showOffline();
                return;
            }
            Meta m = metas.get(PL_NEW);
            if (m != null) setCoverUrl(newCover, m.thumb);
        });

        loadPlaylist(PL_TOP, (list, err) -> {
            Meta m = metas.get(PL_TOP);
            if (m != null) setCoverUrl(topCover, m.thumb);
        });
    }

    // =========================
    // EKSPLOR
    // =========================

    private void showExplore() {
        createShell("explore", "explore");
        headerTitle("Eksplor");

        addSearchBar(() -> showSearch(null));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);

        LinearLayout filters = row();
        filters.setPadding(0, dp(12), 0, dp(6));
        hs.addView(filters);
        content.addView(hs, lp(-1, dp(56)));

        String[] labels = {"Untukmu", "Playlist", "Artis", "Album", "Genre"};
        for (int i = 0; i < labels.length; i++) {
            final String l = labels[i];
            TextView c = chip(l, i == 0);
            filters.addView(c, mp(-2, dp(32), 0, 0, 6, 0));

            c.setOnClickListener(v -> {
                if (l.equals("Playlist")) showPlaylistList("Untukmu");
                else if (l.equals("Artis")) showSearchType("artist");
                else if (l.equals("Album")) showSearchType("album");
            });
        }

        section("Genre Populer", null, null);

        String[][] genres = {
                {"Pop", "Rock", "Hip Hop"},
                {"R&B", "EDM", "Indie"}
        };

        int n = 0;
        for (String[] line : genres) {
            LinearLayout genreRow = row();
            content.addView(genreRow, mp(-1, dp(78), 0, 0, 0, 7));

            for (final String genre : line) {
                TextView tile = centered(genre, 13, WHITE, true);
                tile.setBackground(tint(n++, 14));

                genreRow.addView(tile, mp(0, -1, 3, 0, 3, 0));
                ((LinearLayout.LayoutParams) tile.getLayoutParams()).weight = 1;

                tile.setOnClickListener(v -> showSearch(genre));
            }
        }

        section("Playlist Pilihan", "Lihat semua",
                () -> showPlaylistList("Untukmu"));

        HorizontalScrollView cardsScroll = new HorizontalScrollView(this);
        cardsScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout cards = row();
        cards.setGravity(Gravity.TOP);
        cardsScroll.addView(cards);
        content.addView(cardsScroll, lp(-1, dp(150)));

        addExploreCard(cards, "Rilis Terbaru Indonesia", "Playlist", 3,
                PL_NEW);
        addExploreCard(cards, "Top Hits Indonesia", "Playlist", 1, PL_TOP);
        addExploreCard(cards, "Daily Mix", "Dari selera musikmu", 0, "daily");
    }

    private void addExploreCard(LinearLayout parent, String title,
                                String subtitle, int tintIndex,
                                final String id) {
        LinearLayout item = column();
        parent.addView(item, mp(dp(120), -1, 0, 0, 10, 0));

        final View cv = cover(14, 30, "");
        cv.setBackground(tint(tintIndex, 14));
        item.addView(cv, lp(dp(120), dp(100)));

        TextView name = text(title, 11, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(name, mp(-1, -2, 0, 5, 0, 0));
        item.addView(text(subtitle, 9, MUTED, false));

        item.setOnClickListener(v -> {
            if (id.equals("daily")) showDailyMix();
            else showPlaylist(id, title);
        });

        if (!id.equals("daily")) {
            loadPlaylist(id, (list, err) -> {
                Meta m = metas.get(id);
                if (m != null) setCoverUrl(cv, m.thumb);
            });
        }
    }

    // =========================
    // DAFTAR PLAYLIST
    // =========================

    private void showPlaylistList(final String tab) {
        createShell("playlist", "explore");
        headerBack("Playlist", null, null);

        LinearLayout tabs = row();
        content.addView(tabs, lp(-1, dp(46)));

        String[] names = {"Untukmu", "Trending", "Terbaru"};
        for (final String n : names) {
            TextView c = chip(n, n.equals(tab));
            tabs.addView(c, mp(-2, dp(32), 0, 0, 8, 0));
            c.setOnClickListener(v -> showPlaylistList(n));
        }

        if (tab.equals("Untukmu") || tab.equals("Terbaru")) {
            addPlaylistRow("Rilis Terbaru Indonesia", "Playlist • Spotify",
                    PL_NEW, 3);
        }
        if (tab.equals("Untukmu") || tab.equals("Trending")) {
            addPlaylistRow("Top Hits Indonesia", "Playlist • Spotify",
                    PL_TOP, 1);
        }
        if (tab.equals("Untukmu")) {
            addPlaylistRow("Daily Mix", "Dari selera musikmu", "daily", 0);
        }
    }

    private void addPlaylistRow(final String title, String subtitle,
                                final String id, int tintIndex) {
        LinearLayout item = row();
        item.setPadding(dp(2), dp(5), dp(4), dp(5));
        content.addView(item, lp(-1, dp(66)));

        final View cv = cover(12, 20, "");
        cv.setBackground(tint(tintIndex, 12));
        item.addView(cv, lp(dp(52), dp(52)));

        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, dp(6), 0);
        labels.addView(text(title, 13, TEXT, true));
        labels.addView(text(subtitle, 10, MUTED, false));

        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(text("›", 20, MUTED, false));

        item.setOnClickListener(v -> {
            if (id.equals("daily")) showDailyMix();
            else showPlaylist(id, title);
        });

        if (!id.equals("daily")) {
            loadPlaylist(id, (list, err) -> {
                Meta m = metas.get(id);
                if (m != null) setCoverUrl(cv, m.thumb);
            });
        }
    }

    // =========================
    // DETAIL KOLEKSI (PLAYLIST / ALBUM / MIX)
    // =========================

    private void renderCollection(String kind, LinearLayout body,
                                  String coverUrl,
                                  String title, String line1,
                                  String line2, final ArrayList<Track> list,
                                  final String shareText) {
        final String source = tag(kind, title);
        viewSource = source;

        View cv = cover(18, 56, coverUrl);
        body.addView(cv, mp(dp(160), dp(160), 0, 4, 0, 0));
        ((LinearLayout.LayoutParams) cv.getLayoutParams()).gravity =
                Gravity.CENTER_HORIZONTAL;

        TextView t = centered(title, 19, TEXT, true);
        t.setMaxLines(2);
        body.addView(t, mp(-1, -2, 0, 12, 0, 0));

        body.addView(centered(line1, 11, MUTED, false), lp(-1, dp(26)));

        if (!line2.isEmpty()) {
            TextView d = centered(line2, 11, MUTED, false);
            d.setMaxLines(2);
            d.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(d, lp(-1, -2));
        }

        LinearLayout buttons = row();
        body.addView(buttons, mp(-1, dp(44), 0, 14, 0, 0));

        TextView playBtn = bigButton("▶  Putar", true);
        TextView shuffleBtn = bigButton("⇄  Shuffle", false);

        buttons.addView(playBtn, mp(0, -1, 0, 0, 5, 0));
        ((LinearLayout.LayoutParams) playBtn.getLayoutParams()).weight = 1;
        buttons.addView(shuffleBtn, mp(0, -1, 5, 0, 0, 0));
        ((LinearLayout.LayoutParams) shuffleBtn.getLayoutParams()).weight = 1;

        playBtn.setOnClickListener(v -> playQueue(list, 0, source));
        shuffleBtn.setOnClickListener(v -> playShuffled(list, source));

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER);
        body.addView(actions, lp(-1, dp(84)));

        addAction(actions, "♡", "Simpan", () -> addAllFav(list));
        addAction(actions, "↓", "Download", () -> downloadAll(list));
        addAction(actions, "↗", "Bagikan", () -> share(shareText));
        addAction(actions, "⋯", "Lainnya", () -> toast("Segera hadir"));

        int n = Math.min(60, list.size());
        for (int i = 0; i < n; i++) {
            addSongRow(body, list, i, true, "");
        }

        if (list.size() > n) {
            note(body, "Menampilkan " + n + " dari " + list.size()
                    + " lagu.");
        }
    }

    private void addAction(LinearLayout parent, String icon, String label,
                           final Runnable callback) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);

        TextView symbol = centered(icon, 17, PURPLE, true);
        symbol.setBackground(round(SURFACE2, 14));
        item.addView(symbol, lp(dp(46), dp(46)));
        item.addView(centered(label, 10, MUTED, false), lp(-1, dp(26)));

        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        item.setOnClickListener(v -> callback.run());
    }

    private void showPlaylist(final String id, final String title) {
        createShell("playlist", "explore");
        headerBack("", "⋮", v -> toast("Segera hadir"));

        final LinearLayout body = column();
        content.addView(body, lp(-1, -2));
        note(body, "Memuat...");

        loadPlaylist(id, (list, err) -> {
            if (isOffline(err)) {
                showOffline();
                return;
            }

            body.removeAllViews();

            if (list.isEmpty()) {
                note(body, "Gagal memuat lagu (" + err + ")");
                return;
            }

            Meta m = metas.get(id);
            String name = (m != null && !m.name.isEmpty()) ? m.name : title;
            int total = (m != null && m.total > 0) ? m.total : list.size();
            String desc = m != null ? m.desc : "";
            String url = (m != null && !m.url.isEmpty()) ? m.url
                    : "https://open.spotify.com/playlist/" + id;

            renderCollection("PLAYLIST", body, m != null ? m.thumb : "", name,
                    total + " lagu • " + totalTime(list), desc, list,
                    name + "\n" + url);
        });
    }

    private void addUnique(ArrayList<Track> target, Track t) {
        for (Track x : target) {
            if (same(x, t)) return;
        }
        target.add(t);
    }

    private void showDailyMix() {
        createShell("playlist", "home");
        headerBack("", "⋮", v -> toast("Segera hadir"));

        final LinearLayout body = column();
        content.addView(body, lp(-1, -2));
        note(body, "Menyusun mix...");

        final ArrayList<Track> mix = new ArrayList<>();

        for (Track t : loadSaved("fav")) addUnique(mix, t);
        for (Track t : loadSaved("hist")) addUnique(mix, t);

        loadPlaylist(PL_TOP, (list, err) -> {
            if (isOffline(err) && mix.isEmpty()) {
                showOffline();
                return;
            }

            ArrayList<Track> extra = new ArrayList<>(list);
            Collections.shuffle(extra);

            for (Track t : extra) {
                if (mix.size() >= 20) break;
                addUnique(mix, t);
            }

            Collections.shuffle(mix);
            body.removeAllViews();

            if (mix.isEmpty()) {
                note(body, "Belum ada lagu untuk mix ini.");
                return;
            }

            renderCollection("PLAYLIST", body, "", "Daily Mix",
                    mix.size() + " lagu • " + totalTime(mix),
                    "Campuran favorit, riwayat putar, dan lagu populer.",
                    mix, "Daily Mix dari ENC Music");
        });
    }

    // =========================
    // PENCARIAN
    // =========================

    private String searchType = "track";

    private void showSearchType(String type) {
        searchType = type;
        showSearch(null);
    }

    private void showSearch(String initial) {
        createShell("search", "explore");
        headerBack("Cari", null, null);

        LinearLayout box = row();
        box.setPadding(dp(12), 0, dp(6), 0);
        box.setBackground(round(SURFACE, 14));
        content.addView(box, lp(-1, dp(47)));

        box.addView(text("⌕", 20, MUTED, false), lp(dp(26), -1));

        searchField = new EditText(this);
        searchField.setHint("Cari lagu, artis, album...");
        searchField.setHintTextColor(MUTED);
        searchField.setTextColor(TEXT);
        searchField.setTextSize(14);
        searchField.setSingleLine(true);
        searchField.setBackground(null);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(searchField, new LinearLayout.LayoutParams(0, -1, 1));

        TextView clear = centered("✕", 15, MUTED, true);
        box.addView(clear, lp(dp(36), -1));
        clear.setOnClickListener(v -> searchField.setText(""));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);

        LinearLayout chips = row();
        chips.setPadding(0, dp(10), 0, dp(6));
        hs.addView(chips);
        content.addView(hs, lp(-1, dp(52)));

        final LinearLayout results = column();
        content.addView(results, lp(-1, -2));

        String[][] types = {
                {"Semua", "track"}, {"Lagu", "track"}, {"Artis", "artist"},
                {"Album", "album"}, {"Playlist", "playlist"}
        };

        for (final String[] ty : types) {
            boolean active = ty[1].equals(searchType)
                    && !(ty[0].equals("Semua") && !searchType.equals("track"))
                    && !(ty[0].equals("Lagu") && searchType.equals("track")
                    && false);
            TextView c = chip(ty[0], active && !ty[0].equals("Lagu"));
            chips.addView(c, mp(-2, dp(32), 0, 0, 6, 0));

            c.setOnClickListener(v -> {
                searchType = ty[1];
                showSearch(searchField.getText().toString().trim());
            });
        }

        searchField.setOnEditorActionListener((v, actionId, event) -> {
            doSearch(searchField.getText().toString().trim(), results);
            return true;
        });

        if (initial != null && !initial.isEmpty()) {
            searchField.setText(initial);
            searchField.setSelection(initial.length());
            doSearch(initial, results);
        } else {
            searchField.requestFocus();
            searchField.postDelayed(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(searchField, 0);
                } catch (Exception ignored) {
                }
            }, 200);
        }
    }

    private void doSearch(String query, final LinearLayout results) {
        if (query.isEmpty()) return;

        hideKeyboard();
        results.removeAllViews();
        note(results, "Mencari \"" + query + "\"...");

        final String type = searchType;

        searchItems(query, type, (list, err) -> {
            if (isOffline(err)) {
                showOffline();
                return;
            }

            results.removeAllViews();

            if (list.isEmpty()) {
                note(results, err == null
                        ? "Tidak ada hasil."
                        : "Pencarian gagal (" + err + ")");
                return;
            }

            viewSource = tag("SEARCH", "\"" + query + "\"");

            for (int i = 0; i < list.size(); i++) {
                if (type.equals("track")) {
                    addSongRow(results, list, i, false, " • Lagu");
                } else {
                    addItemRow(results, list.get(i), type);
                }
            }
        });
    }

    // =========================
    // PEMUTAR MUSIK
    // =========================

    private void showPlayer() {
        createShell("player", "home");

        headerBack("", "⋮", v -> showTrackMenu(v, current));
        setPlayerSource();

        if (current == null) {
            note(content, "Belum ada lagu yang diputar.");
            return;
        }

        playerCover = cover(22, 90, thumbUrl(current));
        LinearLayout.LayoutParams cp = lp(dp(250), dp(250));
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.topMargin = dp(8);
        content.addView(playerCover, cp);

        LinearLayout info = row();
        content.addView(info, mp(-1, dp(60), 4, 16, 4, 0));

        LinearLayout names = column();
        playerTitle = text(current.title, 19, TEXT, true);
        playerTitle.setSingleLine(true);
        playerTitle.setEllipsize(TextUtils.TruncateAt.END);

        playerArtist = text(current.artist, 13, MUTED, false);
        playerArtist.setSingleLine(true);
        playerArtist.setEllipsize(TextUtils.TruncateAt.END);

        names.addView(playerTitle);
        names.addView(playerArtist);
        info.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        playerFav = centered(isFav(current) ? "♥" : "♡", 24, PURPLE, true);
        info.addView(playerFav, lp(dp(46), dp(46)));
        playerFav.setOnClickListener(v -> toggleFav(current));

        playerSeek = new SeekBar(this);
        playerSeek.setProgressTintList(ColorStateList.valueOf(PURPLE));
        playerSeek.setThumbTintList(ColorStateList.valueOf(PURPLE));
        playerSeek.setMax(30000);

        playerSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar s, int p,
                                                  boolean fromUser) {
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar s) {
                        dragging = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar s) {
                        dragging = false;
                        try {
                            if (player != null && prepared) {
                                player.seekTo(s.getProgress());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });

        content.addView(playerSeek, mp(-1, dp(34), 0, 6, 0, 0));

        LinearLayout times = row();
        times.setPadding(dp(14), 0, dp(14), 0);
        playerElapsed = text("0:00", 10, MUTED, false);
        playerTotal = text("0:30", 10, MUTED, false);
        playerTotal.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        times.addView(playerElapsed, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(playerTotal, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(times, lp(-1, dp(22)));

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        content.addView(controls, mp(-1, dp(80), 0, 6, 0, 0));

        playerShuffle = centered("⇄", 20, shuffleOn ? PURPLE : MUTED, true);
        controls.addView(playerShuffle, lp(dp(48), dp(48)));

        TextView prev = centered("|◀", 20, TEXT, true);
        controls.addView(prev, lp(dp(52), dp(52)));

        playerPlay = centered(playing ? "Ⅱ" : "▶", 24, WHITE, true);
        playerPlay.setBackground(round(PURPLE, 40));
        controls.addView(playerPlay, mp(dp(66), dp(66), 8, 0, 8, 0));

        TextView nxt = centered("▶|", 20, TEXT, true);
        controls.addView(nxt, lp(dp(52), dp(52)));

        playerRepeat = centered("⟳", 22, repeatOn ? PURPLE : MUTED, true);
        controls.addView(playerRepeat, lp(dp(48), dp(48)));

        playerShuffle.setOnClickListener(v -> toggleShuffle());
        prev.setOnClickListener(v -> previous());
        playerPlay.setOnClickListener(v -> togglePlay());
        nxt.setOnClickListener(v -> next());
        playerRepeat.setOnClickListener(v -> toggleRepeat());

        playerNote = centered(sourceNote(), 10, MUTED, false);
        content.addView(playerNote, lp(-1, dp(28)));

        // Daftar "Berikutnya" (diperbarui otomatis saat lagu berganti).
        playerNextBox = column();
        content.addView(playerNextBox, lp(-1, -2));
        nextKey = -1;
        renderNextUp();
    }

    private void renderNextUp() {
        if (playerNextBox == null) return;

        int key = System.identityHashCode(queue) * 31 + index;
        if (key == nextKey) return;
        nextKey = key;

        playerNextBox.removeAllViews();

        if (queue == null || index < 0 || index + 1 >= queue.size()) {
            return;
        }

        playerNextBox.addView(text("Berikutnya", 15, TEXT, true),
                mp(-1, dp(40), 0, 8, 0, 0));

        viewSource = queueSource;

        int end = Math.min(queue.size(), index + 4);
        for (int i = index + 1; i < end; i++) {
            addSongRow(playerNextBox, queue, i, true, "");
        }
    }

    // Keterangan "Memutar dari ..." di tengah header pemutar.
    private void setPlayerSource() {
        if (queueSource.isEmpty() || header.getChildCount() < 2) return;

        View titleView = header.getChildAt(1);
        if (!(titleView instanceof TextView)) return;

        String[] p = queueSource.split("\\|", 2);
        String kind = p[0];
        String name = p.length > 1 ? p[1] : "";

        String caption;
        switch (kind) {
            case "PLAYLIST":
                caption = "MEMUTAR DARI PLAYLIST";
                break;
            case "ALBUM":
                caption = "MEMUTAR DARI ALBUM";
                break;
            case "SEARCH":
                caption = "MEMUTAR DARI PENCARIAN";
                break;
            case "ARTIST":
                caption = "MEMUTAR DARI ARTIS";
                break;
            default:
                caption = "MEMUTAR DARI";
                break;
        }

        SpannableString s = new SpannableString(caption + "\n" + name);
        s.setSpan(new ForegroundColorSpan(MUTED), 0, caption.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new RelativeSizeSpan(0.72f), 0, caption.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), caption.length() + 1,
                s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView tv = (TextView) titleView;
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(13);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setText(s);
    }

    // =========================
    // PERPUSTAKAAN
    // =========================

    private void libraryRow(String icon, String title, String subtitle,
                            final Runnable callback) {
        LinearLayout item = row();
        item.setPadding(dp(2), dp(5), dp(4), dp(5));
        content.addView(item, lp(-1, dp(66)));

        TextView ic = centered(icon, 19, PURPLE, true);
        ic.setBackground(round(SURFACE2, 12));
        item.addView(ic, lp(dp(46), dp(46)));

        LinearLayout labels = column();
        labels.setPadding(dp(14), 0, dp(6), 0);
        labels.addView(text(title, 14, TEXT, true));
        labels.addView(text(subtitle, 10, MUTED, false));

        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(text("›", 20, MUTED, false));

        item.setOnClickListener(v -> callback.run());
    }

    private void showLibrary() {
        createShell("library", "library");
        headerTitle("Perpustakaan");

        final ArrayList<Track> favs = loadSaved("fav");
        final ArrayList<Track> hist = loadSaved("hist");
        ArrayList<Track> dl = loadSaved("dl");

        libraryRow("☰", "Playlist saya", "Segera hadir",
                () -> toast("Segera hadir"));
        libraryRow("♥", "Lagu favorit", favs.size() + " lagu",
                () -> showTrackList("Lagu Favorit", favs));
        libraryRow("↓", "Download", dl.size() + " lagu",
                () -> showDownloads());
        libraryRow("◷", "Terakhir diputar", hist.size() + " lagu",
                () -> showTrackList("Terakhir Diputar", hist));
        libraryRow("♟", "Artis", "Cari lewat pencarian",
                () -> showSearchType("artist"));
        libraryRow("▣", "Album", "Cari lewat pencarian",
                () -> showSearchType("album"));
    }

    private void showTrackList(String title, ArrayList<Track> list) {
        createShell("playlist", "library");
        headerBack(title, null, null);
        viewSource = tag("LIST", title);

        if (list.isEmpty()) {
            note(content, "Belum ada lagu di sini.");
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            addSongRow(content, list, i, false, "");
        }
    }

    private void showDownloads() {
        createShell("downloads", "library");
        headerBack("Download", null, null);
        viewSource = tag("LIST", "Download");

        LinearLayout tabs = row();
        content.addView(tabs, lp(-1, dp(46)));

        TextView songs = chip("Lagu", true);
        TextView pls = chip("Playlist", false);
        TextView albums = chip("Album", false);

        tabs.addView(songs, mp(-2, dp(32), 0, 0, 8, 0));
        tabs.addView(pls, mp(-2, dp(32), 0, 0, 8, 0));
        tabs.addView(albums, mp(-2, dp(32), 0, 0, 8, 0));

        pls.setOnClickListener(v -> toast("Segera hadir"));
        albums.setOnClickListener(v -> toast("Segera hadir"));

        ArrayList<Track> dl = loadSaved("dl");

        if (dl.isEmpty()) {
            note(content, "Belum ada lagu yang diunduh. Buka menu ⋮ pada "
                    + "sebuah lagu lalu pilih Download.");
            return;
        }

        for (int i = 0; i < dl.size(); i++) {
            addSongRow(content, dl, i, false, "");
        }
    }

    // =========================
    // ARTIS & ALBUM
    // =========================

    private String followersText(JSONObject o) {
        String[] keys = {"followers", "followers_count", "follower_count"};

        for (String k : keys) {
            Object v = o.opt(k);

            if (v instanceof JSONObject) {
                v = ((JSONObject) v).opt("total");
            }

            if (v instanceof Number) {
                long n = ((Number) v).longValue();
                if (n >= 1000000) {
                    return String.format(java.util.Locale.US, "%.1f jt",
                            n / 1000000.0).replace('.', ',') + " pengikut";
                } else if (n >= 1000) {
                    return (n / 1000) + " rb pengikut";
                }
                return n + " pengikut";
            }

            if (v instanceof String && !((String) v).isEmpty()
                    && !v.equals("null")) {
                return v + " pengikut";
            }
        }

        return "";
    }

    private void showArtist(final String id, final String fallbackName) {
        createShell("artist", "explore");
        headerBack("", "⋮", v -> toast("Segera hadir"));

        final LinearLayout top = column();
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(top, lp(-1, -2));
        note(top, "Memuat...");

        final LinearLayout songsBox = column();
        content.addView(songsBox, lp(-1, -2));

        final LinearLayout albumsBox = column();
        content.addView(albumsBox, lp(-1, -2));

        getJson("/artist/" + id, (o, err) -> {
            if (isOffline(err)) {
                showOffline();
                return;
            }

            top.removeAllViews();

            JSONObject a = o;
            if (o != null && o.optJSONObject("artist") != null) {
                a = o.optJSONObject("artist");
            }

            String name = a != null ? str(a, "name") : "";
            if (name.isEmpty()) name = fallbackName;

            String img = a != null ? findImage(a, 0) : "";
            String followers = a != null ? followersText(a) : "";

            View avatar = cover(60, 40, img);
            top.addView(avatar, lp(dp(120), dp(120)));

            top.addView(centered(name, 20, TEXT, true), lp(-1, dp(40)));

            if (!followers.isEmpty()) {
                top.addView(centered(followers, 11, MUTED, false),
                        lp(-1, dp(22)));
            }

            final String key = "follow_" + id;
            final TextView follow = bigButton(
                    prefs.getBoolean(key, false) ? "Mengikuti" : "Ikuti",
                    !prefs.getBoolean(key, false));
            top.addView(follow, mp(-1, dp(42), 0, 12, 0, 6));

            follow.setOnClickListener(v -> {
                boolean now = !prefs.getBoolean(key, false);
                prefs.edit().putBoolean(key, now).apply();
                follow.setText(now ? "Mengikuti" : "Ikuti");
                follow.setTextColor(now ? TEXT : WHITE);
                follow.setBackground(round(now ? SURFACE2 : PURPLE, 14));
            });
        });

        getJson("/artist/" + id + "/top-tracks", (o, err) -> {
            if (o == null) return;

            ArrayList<Track> list = parseTracks(findArray(o, 0));
            if (list.isEmpty()) return;

            songsBox.addView(text("Lagu Populer", 16, TEXT, true),
                    mp(-1, dp(44), 0, 8, 0, 0));

            viewSource = tag("ARTIST", fallbackName);

            int n = Math.min(10, list.size());
            for (int i = 0; i < n; i++) {
                addSongRow(songsBox, list, i, false, "");
            }
        });

        getJson("/artist/" + id + "/albums?type=album&limit=10", (o, err) -> {
            if (o == null) return;

            ArrayList<Track> list = parseTracks(findArray(o, 0));
            if (list.isEmpty()) return;

            albumsBox.addView(text("Album", 16, TEXT, true),
                    mp(-1, dp(44), 0, 8, 0, 0));

            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);

            LinearLayout rowBox = row();
            rowBox.setGravity(Gravity.TOP);
            hs.addView(rowBox);
            albumsBox.addView(hs, lp(-1, dp(150)));

            for (final Track al : list) {
                LinearLayout item = column();
                rowBox.addView(item, mp(dp(110), -1, 0, 0, 10, 0));

                item.addView(cover(12, 26, thumbUrl(al)),
                        lp(dp(110), dp(110)));

                TextView nm = text(al.title, 11, TEXT, true);
                nm.setSingleLine(true);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                item.addView(nm, mp(-1, -2, 0, 5, 0, 0));

                item.setOnClickListener(v -> showAlbum(al.id, al.title));
            }
        });
    }

    private void showAlbum(final String id, final String fallbackTitle) {
        createShell("album", "explore");
        headerBack("", "⋮", v -> toast("Segera hadir"));

        final LinearLayout body = column();
        content.addView(body, lp(-1, -2));
        note(body, "Memuat...");

        getJson("/album/" + id, (o, err) -> {
            if (isOffline(err)) {
                showOffline();
                return;
            }

            body.removeAllViews();

            if (o == null) {
                note(body, "Gagal memuat album (" + err + ")");
                return;
            }

            JSONObject a = o.optJSONObject("album") != null
                    ? o.optJSONObject("album") : o;

            String name = str(a, "name");
            if (name.isEmpty()) name = str(a, "title");
            if (name.isEmpty()) name = fallbackTitle;

            String artist = artistOf(a);
            String img = findImage(a, 0);

            String year = str(a, "release_date");
            if (year.length() >= 4) year = year.substring(0, 4);
            if (year.isEmpty()) year = str(a, "year");

            ArrayList<Track> raw = parseTracks(findArray(a, 0));
            ArrayList<Track> list = new ArrayList<>();

            for (Track t : raw) {
                list.add(new Track(t.id, t.title,
                        t.artist.isEmpty() ? artist : t.artist,
                        t.duration, t.preview,
                        t.thumb.isEmpty() ? img : t.thumb));
            }

            if (list.isEmpty()) {
                note(body, "Album ini tidak punya daftar lagu.");
                return;
            }

            String line1 = (artist.isEmpty() ? "" : artist + "\n")
                    + "Album" + (year.isEmpty() ? "" : " • " + year)
                    + " • " + list.size() + " lagu";

            renderCollection("ALBUM", body, img, name, line1, "", list,
                    name + (artist.isEmpty() ? "" : " - " + artist)
                            + "\nhttps://open.spotify.com/album/" + id);
        });
    }

    // =========================
    // PENGATURAN, NOTIFIKASI, OFFLINE
    // =========================

    private long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();

        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) total += dirSize(k);
        }
        return total;
    }

    private void deleteContents(File dir) {
        File[] kids = dir == null ? null : dir.listFiles();
        if (kids == null) return;

        for (File k : kids) {
            if (k.isDirectory()) deleteContents(k);
            k.delete();
        }
    }

    private void settingRow(String icon, String title, String value,
                            View trailing, final Runnable callback) {
        LinearLayout item = row();
        item.setPadding(dp(2), 0, dp(4), 0);
        content.addView(item, lp(-1, dp(54)));

        item.addView(centered(icon, 16, MUTED, false), lp(dp(34), -1));
        item.addView(text(title, 13, TEXT, false), weight(1));

        if (value != null && !value.isEmpty()) {
            item.addView(text(value, 11, MUTED, false));
        }

        if (trailing != null) {
            item.addView(trailing);
        } else {
            item.addView(text(" ›", 18, MUTED, false));
        }

        if (callback != null) {
            item.setOnClickListener(v -> callback.run());
        }
    }

    private void showSettings() {
        createShell("settings", "home");
        headerBack("Pengaturan", null, null);

        LinearLayout profile = row();
        profile.setPadding(dp(2), dp(8), dp(2), dp(12));
        content.addView(profile, lp(-1, dp(74)));

        TextView avatar = centered("C", 20, WHITE, true);
        avatar.setBackground(round(PURPLE, 50));
        profile.addView(avatar, lp(dp(50), dp(50)));

        LinearLayout who = column();
        who.setPadding(dp(14), 0, 0, 0);
        who.addView(text("Christian Korompis", 14, TEXT, true));
        who.addView(text("christian@example.com", 11, MUTED, false));
        profile.addView(who, new LinearLayout.LayoutParams(0, -2, 1));

        settingRow("♟", "Akun", "", null, () -> toast("Segera hadir"));
        settingRow("♪", "Kualitas Audio", "Normal", null,
                () -> toast("Segera hadir"));
        settingRow("🔔", "Notifikasi", "", null, () -> showNotifications());

        final Switch fullSw = new Switch(this);
        fullSw.setChecked(fullEnabled());
        settingRow("♫", "Lagu Penuh", "", fullSw, null);
        fullSw.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("full", checked).apply());

        final Switch sw = new Switch(this);
        sw.setChecked(dark);
        settingRow("☾", "Mode Gelap", "", sw, null);

        sw.setOnCheckedChangeListener((b, checked) -> {
            dark = checked;
            prefs.edit().putBoolean("dark", dark).apply();
            applyTheme();
            showSettings();
        });

        long size = dirSize(getCacheDir()) + dirSize(new File(getFilesDir(), "dl"));
        String sizeText = size >= 1048576
                ? String.format(java.util.Locale.US, "%.1f MB",
                size / 1048576.0)
                : (size / 1024) + " KB";

        settingRow("▤", "Cache & Penyimpanan", sizeText, null, () -> {
            deleteContents(getCacheDir());
            toast("Cache dibersihkan");
            showSettings();
        });

        settingRow("ⓘ", "Tentang Aplikasi", "v1.0.0", null,
                () -> toast("ENC Music v1.0.0"));

        TextView logout = centered("Keluar", 14, Color.rgb(255, 99, 99), true);
        logout.setBackground(outline(Color.argb(40, 255, 99, 99),
                Color.argb(120, 255, 99, 99), 12));
        content.addView(logout, mp(-1, dp(48), 0, 24, 0, 0));

        logout.setOnClickListener(v -> {
            prefs.edit().putBoolean("logged", false).apply();
            releasePlayer();
            playing = false;
            current = null;
            showLogin();
        });
    }

    private void showNotifications() {
        createShell("settings", "home");
        headerBack("Notifikasi", null, null);

        content.addView(text("Hari ini", 14, TEXT, true),
                lp(-1, dp(40)));

        notificationRow("♪", "Rilis baru tiap Jumat",
                "Playlist Rilis Terbaru diperbarui Spotify setiap pekan.");
        notificationRow("♡", "Simpan lagu favoritmu",
                "Tekan ♡ di pemutar agar muncul di Daily Mix.");
        notificationRow("↓", "Download cuplikan",
                "Lagu yang diunduh bisa diputar tanpa internet.");
    }

    private void notificationRow(String icon, String title, String body) {
        LinearLayout item = row();
        item.setPadding(dp(2), dp(6), dp(2), dp(6));
        content.addView(item, lp(-1, dp(72)));

        TextView ic = centered(icon, 16, PURPLE, true);
        ic.setBackground(round(SURFACE2, 50));
        item.addView(ic, lp(dp(42), dp(42)));

        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text(title, 12, TEXT, true));

        TextView b = text(body, 10, MUTED, false);
        b.setMaxLines(2);
        labels.addView(b);

        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void showOffline() {
        createShell("offline", "home");
        headerBrand();

        content.addView(new View(this), lp(1, dp(90)));

        content.addView(centered("☁", 64, MUTED, false), lp(-1, dp(80)));
        content.addView(centered("Mode Offline", 18, TEXT, true),
                lp(-1, dp(40)));

        TextView msg = centered("Tidak ada koneksi internet.\n"
                + "Coba periksa jaringan Anda.", 12, MUTED, false);
        content.addView(msg, lp(-1, -2));

        TextView retry = bigButton("Coba Lagi", true);
        content.addView(retry, mp(-1, dp(46), 0, 24, 0, 0));
        retry.setOnClickListener(v -> showHome());

        TextView dl = bigButton("Buka Download", false);
        content.addView(dl, mp(-1, dp(46), 0, 10, 0, 0));
        dl.setOnClickListener(v -> showDownloads());
    }
}
