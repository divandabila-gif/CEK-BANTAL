package id.unusa.cekbantal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

import org.json.JSONObject;

/** Menampilkan notifikasi alarm (bunyi alarm + getar, muncul di layar kunci). */
public class AlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL = "alarm_cekbantal_v1";

    @Override
    public void onReceive(Context c, Intent in) {
        String id = in.getStringExtra("id");
        if (id == null) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);

        if (Scheduler.ACTION_SNOOZE.equals(in.getAction())) {
            nm.cancel(Scheduler.idx(id) + 1);
            Scheduler.snooze(c, id);
            return;
        }

        // Pasang lagi alarm untuk besok.
        Scheduler.scheduleAll(c);

        if (!Scheduler.enabled(c)) return;
        JSONObject o = Scheduler.item(c, id);
        if (o == null) return;
        // Lewati bila obat/cek hari ini sudah dicentang.
        if (Scheduler.today().equals(o.optString("done"))) return;

        ensureChannel(c, nm);

        PendingIntent open = Scheduler.openIntent(c, id);
        String text = o.optString("text", "");
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF5B2A86)
                .setContentTitle(o.optString("title", "Cek BANTAL"))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(15 * 60_000L)
                .setContentIntent(open)
                .setFullScreenIntent(open, true);

        if (!"cek".equals(id)) {
            Intent s = new Intent(c, AlarmReceiver.class);
            s.setAction(Scheduler.ACTION_SNOOZE);
            s.putExtra("id", id);
            PendingIntent snooze = PendingIntent.getBroadcast(c, 300 + Scheduler.idx(id), s, Scheduler.FLAGS);
            b.addAction(new Notification.Action.Builder((Icon) null, "Ingatkan 10 menit lagi", snooze).build());
        }

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // bunyi berulang sampai dibuka
        nm.notify(Scheduler.idx(id) + 1, n);
    }

    static void ensureChannel(Context c, NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Alarm obat & Cek BANTAL",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Pengingat minum obat dan pemeriksaan harian");
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }
}
