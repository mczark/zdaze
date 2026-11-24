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
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;

/**
 * Planuje budzik (AlarmManager.setAlarmClock). O czasie:
 *  1) wybudza ekran i bezpośrednio otwiera aplikację (Android pozwala na to aplikacjom
 *     z uprawnieniem „wyświetlanie nad innymi aplikacjami”),
 *  2) pokazuje pełnoekranowe powiadomienie z dźwiękiem budzika – dzwoni, dopóki aplikacja
 *     się nie otworzy (zapasowa droga, gdy system zablokuje start z tła).
 */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CH = "zdaze_alarm_v2";
    static final int NID = 42;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("alarm", Context.MODE_PRIVATE); }

    static PendingIntent fire(Context c) {
        return PendingIntent.getBroadcast(c, 1, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Intent openIntent(Context c, boolean test) {
        return new Intent(c, MainActivity.class).putExtra("alarm", true).putExtra("test", test)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    static PendingIntent open(Context c, boolean test) {
        return PendingIntent.getActivity(c, test ? 4 : 2, openIntent(c, test), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, long ts, String label) {
        prefs(c).edit().putLong("ts", ts).putString("label", label).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(ts, open(c, false)), fire(c));
        } catch (SecurityException e) {
            // brak zgody na dokładne alarmy – najlepsza dostępna alternatywa
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, fire(c));
        }
    }

    /** Testowy budzik za kilka sekund – osobny PendingIntent, nie rusza zaplanowanego budzika. */
    static void scheduleTest(Context c, long ts) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 3, new Intent(c, AlarmReceiver.class).putExtra("test", true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try { am.setAlarmClock(new AlarmManager.AlarmClockInfo(ts, open(c, true)), pi); }
        catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi); }
    }

    static void cancel(Context c) {
        prefs(c).edit().clear().apply();
        c.getSystemService(AlarmManager.class).cancel(fire(c));
    }

    static void cancelNotification(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.cancel(NID); nm.cancel(NID_END);
    }

    /* ===== Koniec planu w trakcie pilnowania: wybudza aplikację z pytaniem „Czy zdążyłeś?” ===== */
    static final int NID_END = 43;
    static final String CH_END = "zdaze_end";

    static PendingIntent endFire(Context c) {
        return PendingIntent.getBroadcast(c, 5, new Intent(c, AlarmReceiver.class).putExtra("runEnd", true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleRunEnd(Context c, long ts) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, endFire(c)); }
        catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, endFire(c)); }
    }

    static void cancelRunEnd(Context c) {
        c.getSystemService(AlarmManager.class).cancel(endFire(c));
        c.getSystemService(NotificationManager.class).cancel(NID_END);
    }

    static Intent endIntent(Context c) {
        return new Intent(c, MainActivity.class).putExtra("runEnd", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    @SuppressWarnings("deprecation")
    static void onRunEnd(Context c) {
        try {
            PowerManager pm = c.getSystemService(PowerManager.class);
            pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "zdaze:end").acquire(10000);
        } catch (Exception ignored) {}
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH_END) == null) {
            NotificationChannel ch = new NotificationChannel(CH_END, "Koniec planu", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(c, 6, endIntent(c), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NID_END, new Notification.Builder(c, CH_END)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Plan zakończony – czy zdążyłeś?")
                .setContentText("Dotknij, aby odpowiedzieć")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
        try { c.startActivity(endIntent(c)); } catch (Exception ignored) {}
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent intent) {
        if (intent.getBooleanExtra("runEnd", false)) { onRunEnd(c); return; }
        boolean test = intent.getBooleanExtra("test", false);
        String label = test ? "Test budzika" : prefs(c).getString("label", "Zdążę!");
        if (!test) prefs(c).edit().clear().apply();

        // Wybudź ekran na chwilę
        try {
            PowerManager pm = c.getSystemService(PowerManager.class);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "zdaze:alarm");
            wl.acquire(15000);
        } catch (Exception ignored) {}

        // Powiadomienie z dźwiękiem budzika (dzwoni do otwarcia aplikacji)
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Budzik", NotificationManager.IMPORTANCE_HIGH);
            Uri snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (snd == null) snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Pobudka – czas zacząć!")
                .setContentText(label + " · dotknij, aby otworzyć")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(open(c, test), true)
                .setContentIntent(open(c, test))
                .setOngoing(true)
                .setAutoCancel(true)
                .build();
        n.flags |= Notification.FLAG_INSISTENT; // powtarzaj dźwięk
        nm.notify(NID, n);

        // Bezpośrednie otwarcie aplikacji
        try { c.startActivity(openIntent(c, test)); } catch (Exception ignored) {}
    }
}
