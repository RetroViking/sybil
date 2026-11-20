package sybil.capture;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static java.nio.charset.StandardCharsets.US_ASCII;

/* SIByL's Capture on a phone: a box at the foot of the screen over whatever shows, the lock screen too (no PIN: all it
   can do is add to the Inbox), the keyboard up at once. Enter sends what's typed to the Inbox (see Send) and empties
   the box for the next; Enter with nothing typed, Back or a tap around it closes it. Left any way at all, what's still
   typed goes too, and it closes, so it opens afresh and is never left over the lock screen. What went this time shows
   over the box, the last five, each ticked at its right once it has reached Dropbox. Not connected (yet, or Dropbox
   took the key back), it connects first: Connect Dropbox opens Dropbox's page, whose code is pasted in the box. */
public class Capture extends Activity {
  /* SIByL's colours (index.html's --fg, --fg-2, --fg-3, --accent, --surface, --tile). */
  private static final int FG = 0xffeff0f2, FG2 = 0xffa7abb2, FG3 = 0xff767a82, ACCENT = 0xffbe84fc, SURFACE = 0xff232326, TILE = 0xff29292c;
  private final Handler later = new Handler(Looper.getMainLooper());
  /* What went this time: each one's tick, and its file in the queue (gone once it has reached Dropbox). */
  private final Map<TextView, File> mine = new LinkedHashMap<>();
  private LinearLayout lines;
  private TextView connect;
  private EditText box;

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    FrameLayout page = new FrameLayout(this);
    page.setOnClickListener(v -> finish());
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(16), dp(12), dp(16), dp(16));
    panel.setClickable(true);
    GradientDrawable face = new GradientDrawable();
    face.setColor(SURFACE);
    float r = dp(10);
    face.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
    panel.setBackground(face);
    lines = new LinearLayout(this);
    lines.setOrientation(LinearLayout.VERTICAL);
    panel.addView(lines);
    connect = words("Connect Dropbox", ACCENT);
    connect.setOnClickListener(v -> connect());
    panel.addView(connect);
    box = new EditText(this);
    box.setSingleLine(true);
    box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
    box.setImeOptions(EditorInfo.IME_ACTION_DONE);
    box.setTextSize(16);
    box.setTextColor(FG);
    box.setHintTextColor(FG3);
    box.setPadding(dp(12), dp(10), dp(12), dp(10));
    GradientDrawable field = new GradientDrawable();
    field.setColor(TILE);
    field.setCornerRadius(dp(7));
    box.setBackground(field);
    /* Enter (once: a keyboard's key comes down and up), the keyboard kept up for the next. */
    box.setOnEditorActionListener((v, action, key) -> {
      if (key == null || key.getAction() == KeyEvent.ACTION_DOWN) enter();
      return true;
    });
    panel.addView(box);
    page.addView(panel, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM));
    setContentView(page);
  }

  @Override protected void onResume() {
    super.onResume();
    show();
    later.post(tick);
  }

  /* Capturing, or connecting first. */
  private void show() {
    connect.setVisibility(Send.connected(this) ? View.GONE : View.VISIBLE);
    box.setHint(Send.connected(this) ? "What’s on your mind?" : "Dropbox’s code");
  }

  @Override protected void onPause() {
    super.onPause();
    later.removeCallbacks(tick);
  }

  @Override protected void onStop() {
    super.onStop();
    if (Send.connected(this) && !typed().isEmpty()) send(typed());
    finish();
  }

  /* The keyboard up as it shows, over the lock screen too, where the window's own ask (stateAlwaysVisible) can come
     too early. */
  @Override public void onWindowFocusChanged(boolean focused) {
    if (focused && box.requestFocus()) getSystemService(InputMethodManager.class).showSoftInput(box, 0);
  }

  private String typed() { return box.getText().toString().trim(); }

  private void enter() {
    String text = typed();
    if (!Send.connected(this)) { if (!text.isEmpty()) exchange(text); }
    else if (text.isEmpty()) finish();
    else send(text);
  }

  private void send(String text) {
    File f;
    try { f = Send.keep(this, text); }
    catch (IOException e) { Toast.makeText(this, "The phone couldn’t keep it: its storage is full.", Toast.LENGTH_LONG).show(); return; }
    box.setText("");
    LinearLayout row = new LinearLayout(this);
    row.addView(words(text, FG2), new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
    TextView tick = words("", ACCENT);
    tick.setGravity(Gravity.END);
    row.addView(tick, new LinearLayout.LayoutParams(dp(40), WRAP_CONTENT));
    lines.addView(row);
    mine.put(tick, f);
    if (lines.getChildCount() > 5) {
      Iterator<TextView> first = mine.keySet().iterator();
      first.next();
      first.remove();
      lines.removeViewAt(0);
    }
  }

  /* Ticks what has reached Dropbox, looking again every half second while in view. */
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      for (Map.Entry<TextView, File> e : mine.entrySet()) if (!e.getValue().exists()) e.getKey().setText("✓");
      later.postDelayed(this, 500);
    }
  };

  /* A line of words over the box, starting where its typed words do. */
  private TextView words(String text, int color) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextSize(15);
    t.setTextColor(color);
    t.setPadding(dp(12), dp(4), dp(12), dp(8));
    return t;
  }

  /* Dropbox's page to allow Capture in, with PKCE as SIByL's own Connect Dropbox (a secret made for the asking, kept
     until the code comes back: see Send's connect); with no address to come back to, Dropbox shows the code, to paste
     in the box. Over the lock screen, the phone is unlocked first. */
  private void connect() {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    String verifier = b64(secret);
    Send.auth(this).edit().putString("verifier", verifier).commit();
    String challenge;
    try { challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(US_ASCII))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    Uri page = Uri.parse("https://www.dropbox.com/oauth2/authorize").buildUpon().appendQueryParameter("client_id", Send.KEY)
      .appendQueryParameter("response_type", "code").appendQueryParameter("token_access_type", "offline")
      .appendQueryParameter("code_challenge", challenge).appendQueryParameter("code_challenge_method", "S256").build();
    Runnable open = () -> startActivity(new Intent(Intent.ACTION_VIEW, page));
    KeyguardManager lock = getSystemService(KeyguardManager.class);
    if (!lock.isKeyguardLocked()) open.run();
    else lock.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
      @Override public void onDismissSucceeded() { open.run(); }
    });
  }

  /* The code pasted: connected, the box is for capturing. */
  private void exchange(String code) {
    new Thread(() -> {
      boolean ok = Send.connect(this, code);
      runOnUiThread(() -> {
        if (!ok) { Toast.makeText(this, "Dropbox didn’t take that code. Connect Dropbox again.", Toast.LENGTH_LONG).show(); return; }
        box.setText("");
        show();
      });
    }).start();
  }

  private static String b64(byte[] b) { return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }

  private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
