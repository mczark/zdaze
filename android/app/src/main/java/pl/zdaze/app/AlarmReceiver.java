package pl.zdaze.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Planuje budzik (AlarmManager.setAlarmClock) i o czasie uruchamia aplikację na pełnym ekranie. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CH = "zdaze_alarm";
    static final int NID = 42;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("alarm", Context.MODE_PRIVATE); }

    static PendingIntent fire(Context c) {
        return PendingIntent.getBroadcast(c, 1, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class).putExtra("alarm", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 2, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, long ts, String label) {
        prefs(c).edit().putLong("ts", ts).putString("label", label).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(ts, open(c)), fire(c));
    }

    static void cancel(Context c) {
        prefs(c).edit().clear().apply();
        c.getSystemService(AlarmManager.class).cancel(fire(c));
    }

    static void cancelNotification(Context c) { c.getSystemService(NotificationManager.class).cancel(NID); }

    @Override
    public void onReceive(Context c, Intent intent) {
        String label = prefs(c).getString("label", "Zdążę!");
        prefs(c).edit().clear().apply();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, "Budzik", NotificationManager.IMPORTANCE_HIGH);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Pobudka – czas zacząć!")
                .setContentText(label)
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(open(c), true)
                .setContentIntent(open(c))
                .setAutoCancel(true)
                .build();
        nm.notify(NID, n);
        // Próba bezpośredniego uruchomienia (działa, gdy system pozwala na start z tła)
        try { open(c).send(); } catch (Exception ignored) {}
    }
}
