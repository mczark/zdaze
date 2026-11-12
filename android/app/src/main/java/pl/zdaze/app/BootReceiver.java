package pl.zdaze.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Przywraca zaplanowany budzik po restarcie telefonu. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        long ts = AlarmReceiver.prefs(c).getLong("ts", 0);
        if (ts > System.currentTimeMillis())
            AlarmReceiver.schedule(c, ts, AlarmReceiver.prefs(c).getString("label", "Zdążę!"));
    }
}
