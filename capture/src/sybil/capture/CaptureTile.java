package sybil.capture;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/* Capture from Quick Settings, which come down over the lock screen too: a tile that acts, not a switch, so never lit
   (and never unavailable, which would take no tap). */
public class CaptureTile extends TileService {
  @Override public void onStartListening() {
    getQsTile().setState(Tile.STATE_INACTIVE);
    getQsTile().updateTile();
  }

  @Override public void onClick() {
    Intent capture = new Intent(this, Capture.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, capture, PendingIntent.FLAG_IMMUTABLE));
    else startActivityAndCollapse(capture);
  }
}
