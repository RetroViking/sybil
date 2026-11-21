package sybil.capture;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/* Capture from Quick Settings, which come down over the lock screen too, where it opens with no PIN (a lock screen
   shortcut asks for it on Samsung's phones): a tile that acts, not a switch, so never lit (and never unavailable, which
   would take no tap). */
public class CaptureTile extends TileService {
  @Override public void onStartListening() {
    getQsTile().setState(Tile.STATE_INACTIVE);
    getQsTile().updateTile();
  }

  /* Over the lock screen the system starts it, as Android 14 has tiles do (a PendingIntent). Unlocked, Samsung's Quick
     Settings started it that way behind themselves, staying down; so there it starts itself, as tiles did before, and
     the shade folds as it opens (allowed to an app targeting Android 13, as this one does for it). */
  @Override public void onClick() {
    Intent capture = new Intent(this, Capture.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (Build.VERSION.SDK_INT >= 34 && isLocked()) startActivityAndCollapse(PendingIntent.getActivity(this, 0, capture, PendingIntent.FLAG_IMMUTABLE));
    else startActivityAndCollapse(capture);
  }
}
