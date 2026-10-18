package id.unusa.cekbantal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Memasang ulang alarm setelah HP dinyalakan ulang atau jam HP diubah. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Scheduler.scheduleAll(c);
    }
}
