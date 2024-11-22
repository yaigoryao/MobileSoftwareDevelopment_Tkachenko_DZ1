package ru.msfd.dz1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class AlarmManagerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.d("ALARM_RECEIVER", "Received intent with action: " + intent.getAction());
        if(intent.getAction().equalsIgnoreCase(TimerService.ALARM_MANAGER_ACTION))
        {
            Log.d("RECIEVER", "BROADCAST RECEIVER");
            TimerService.DEVICE_TYPE type = TimerService.DEVICE_TYPE.fromValue(intent.getIntExtra(TimerService.DEVICE_TYPE_EXTRAS, -1));
            Intent notificationIntent = new Intent(context, TimerService.class);
            notificationIntent.putExtra(TimerService.DEVICE_TYPE_EXTRAS, type.getValue());
            notificationIntent.putExtra(DataTramsmissionDevicesMonitor.ACTION_TYPE, TimerService.ACTION_TYPE.SEND_NOTIFICATION.getValue());
            notificationIntent.setData(Uri.parse(notificationIntent.toUri(Intent.URI_INTENT_SCHEME)));
            context.startForegroundService(notificationIntent);
        }
    }
}