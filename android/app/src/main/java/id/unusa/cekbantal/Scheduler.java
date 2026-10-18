package id.unusa.cekbantal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Menjadwalkan alarm harian (5 waktu obat + Cek BANTAL) dari jadwal yang dikirim halaman aplikasi. */
public final class Scheduler {

    static final String[] IDS = {"subuh", "dzuhur", "ashar", "maghrib", "isya", "cek"};
    static final String ACTION_ALARM = "id.unusa.cekbantal.ALARM";
    static final String ACTION_SNOOZE = "id.unusa.cekbantal.SNOOZE";
    static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private Scheduler() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cekbantal", Context.MODE_PRIVATE);
    }

    static int idx(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    static JSONObject schedule(Context c) {
        try {
            return new JSONObject(prefs(c).getString("schedule", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static boolean enabled(Context c) {
        return schedule(c).optBoolean("alarm", true);
    }

    static JSONObject item(Context c, String id) {
        JSONArray a = schedule(c).optJSONArray("items");
        if (a == null) return null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static PendingIntent alarmIntent(Context c, String id, int base) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, base + idx(id), i, FLAGS);
    }

    static PendingIntent openIntent(Context c, String id) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("slot", id);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 200 + idx(id), i, FLAGS);
    }

    static long nextTime(String hhmm) {
        int h = Integer.parseInt(hhmm.substring(0, 2));
        int m = Integer.parseInt(hhmm.substring(3, 5));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 30_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    static void setExact(Context c, long when, PendingIntent pi, PendingIntent show) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            return;
        }
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
    }

    /** Batalkan semua alarm lalu pasang ulang untuk kemunculan berikutnya. */
    static synchronized void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        boolean on = enabled(c);
        for (String id : IDS) {
            PendingIntent pi = alarmIntent(c, id, 0);
            am.cancel(pi);
            if (!on) {
                am.cancel(alarmIntent(c, id, 100));
                continue;
            }
            JSONObject o = item(c, id);
            if (o == null) continue;
            String t = o.optString("time", "");
            if (!t.matches("\\d{2}:\\d{2}")) continue;
            setExact(c, nextTime(t), pi, openIntent(c, id));
        }
    }

    static void snooze(Context c, String id) {
        setExact(c, System.currentTimeMillis() + 10 * 60_000L, alarmIntent(c, id, 100), openIntent(c, id));
    }
}
