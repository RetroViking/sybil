package sybil.capture;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.Arrays;
import org.json.JSONException;
import org.json.JSONObject;
import static java.nio.charset.StandardCharsets.UTF_8;

/* What's captured goes to SIByL's Dropbox folder as sybil-add sends it: each capture a file of its own in the queue,
   named by its time in nanoseconds, uploaded to inbox/ under that name (overwrite: sent twice, it's still one capture;
   SIByL makes its lines Inbox items at its next sync), then deleted here. Uploading is a job that waits for a network
   and outlives a restart; one that fails goes again later (the system's backoff). The key is a refresh token of its
   own (see connect); refused (access taken back at Dropbox), it's forgotten, and the captures wait until Capture is
   connected again. */
public class Send extends JobService {
  /* SIByL's Dropbox app (index.html's DROPBOX): public, as PKCE, not a secret, proves who asks. */
  static final String KEY = "5098lrnrwicuodq";
  private static final String TOKEN = "https://api.dropboxapi.com/oauth2/token";
  private static final String[] FORM = { "Content-Type", "application/x-www-form-urlencoded" };

  static SharedPreferences auth(Context c) { return c.getSharedPreferences("auth", MODE_PRIVATE); }
  static boolean connected(Context c) { return auth(c).contains("refresh"); }
  private static File queue(Context c) { File d = new File(c.getFilesDir(), "queue"); d.mkdirs(); return d; }

  /* A capture queued and its sending asked for; its file, gone once it has reached Dropbox. */
  static File keep(Context c, String text) throws IOException {
    File f = new File(queue(c), String.valueOf(System.currentTimeMillis() * 1000000L));
    Files.write(f.toPath(), (text + "\n").getBytes(UTF_8));
    schedule(c);
    return f;
  }

  private static void schedule(Context c) {
    c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(1, new ComponentName(c, Send.class))
      .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build());
  }

  /* The refresh token for Dropbox's code (shown on its page once Allow is pressed) and the secret made for that asking
     (verifier: see Capture's connect), on the phone's storage before anything else (Dropbox gives a code's token once:
     a crash before an apply reached it lost the token, and the code too); what waits in the queue then goes. False if
     Dropbox refused. */
  static boolean connect(Context c, String code) {
    try {
      String refresh = json(post(TOKEN, FORM, form("code", code, "grant_type", "authorization_code", "client_id", KEY,
        "code_verifier", auth(c).getString("verifier", "")))).getString("refresh_token");
      auth(c).edit().putString("refresh", refresh).remove("verifier").commit();
      schedule(c);
      return true;
    } catch (IOException | JSONException e) { return false; }
  }

  @Override public boolean onStartJob(JobParameters job) {
    new Thread(() -> jobFinished(job, !sendAll())).start();
    return true;
  }
  /* The network gone mid-way: it goes again later (a file sent twice is still one capture). */
  @Override public boolean onStopJob(JobParameters job) { return true; }

  /* Every capture waiting, oldest first, each at the time it was captured: true once all have gone, or the key was
     refused; false to try again later. */
  private boolean sendAll() {
    File[] files = queue(this).listFiles();
    Arrays.sort(files);
    String token = null;
    try {
      for (File f : files) {
        byte[] text;
        /* Gone meanwhile: a job before this one sent it. */
        try { text = Files.readAllBytes(f.toPath()); } catch (NoSuchFileException e) { continue; }
        if (token == null && (token = access()) == null) return true;
        String at = Instant.ofEpochSecond(Long.parseLong(f.getName()) / 1000000000L).toString();
        post("https://content.dropboxapi.com/2/files/upload", new String[] { "Authorization", "Bearer " + token,
          "Content-Type", "application/octet-stream", "Dropbox-API-Arg",
          "{\"path\": \"/inbox/" + f.getName() + "\", \"mode\": \"overwrite\", \"mute\": true, \"client_modified\": \"" + at + "\"}" }, text);
        f.delete();
      }
      return true;
    } catch (IOException | JSONException e) { return false; }
  }

  /* An access token for the refresh token (each lasts 4 hours: one a run will do); null, the refresh token forgotten,
     once Dropbox refuses it. */
  private String access() throws IOException, JSONException {
    String refresh = auth(this).getString("refresh", null);
    if (refresh == null) return null;
    try {
      return json(post(TOKEN, FORM, form("grant_type", "refresh_token", "refresh_token", refresh, "client_id", KEY))).getString("access_token");
    } catch (Refused e) {
      auth(this).edit().remove("refresh").apply();
      return null;
    }
  }

  /* Dropbox saying no (4xx), apart from the network or Dropbox failing. */
  private static class Refused extends IOException {}

  /* A POST (headers: name, value, ...) and its answer. */
  private static byte[] post(String url, String[] headers, byte[] body) throws IOException {
    HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
    try {
      h.setDoOutput(true);
      h.setConnectTimeout(15000);
      h.setReadTimeout(30000);
      for (int i = 0; i < headers.length; i += 2) h.setRequestProperty(headers[i], headers[i + 1]);
      h.setFixedLengthStreamingMode(body.length);
      try (OutputStream o = h.getOutputStream()) { o.write(body); }
      int status = h.getResponseCode();
      if (status >= 400 && status < 500) throw new Refused();
      if (status >= 300) throw new IOException("Dropbox " + status);
      ByteArrayOutputStream got = new ByteArrayOutputStream();
      try (InputStream in = h.getInputStream()) { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0; ) got.write(b, 0, n); }
      return got.toByteArray();
    } finally { h.disconnect(); }
  }

  private static byte[] form(String... pairs) throws IOException {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < pairs.length; i += 2) s.append(i > 0 ? "&" : "").append(pairs[i]).append('=').append(URLEncoder.encode(pairs[i + 1], "UTF-8"));
    return s.toString().getBytes(UTF_8);
  }

  private static JSONObject json(byte[] b) throws JSONException { return new JSONObject(new String(b, UTF_8)); }
}
