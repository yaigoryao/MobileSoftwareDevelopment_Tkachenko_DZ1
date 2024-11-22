package ru.msfd.dz1;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerService extends Service {

    public static final String ALARM_MANAGER_ACTION = "tkachenko.dz1.alarmmanageraction";
    public static final String DEVICE_TYPE_EXTRAS = "device.type.extras";
    public static final String ACTION_TYPE_EXTRAS = "action.type.extras";


    private static final String CHANNEL_ID = "Tkachekno_DZ_CHANNEL";
    private NotificationChannel channel;
    private NotificationManager notificationManager;

    private ScheduledExecutorService scheduler;
    private HashMap<DEVICE_TYPE, ScheduledFuture> scheduledTasks;

    public enum DEVICE_TYPE
    {
        WIFI(0),
        BLUETOOTH(1),
        GPS(2),
        UNKNOWN(-1);

        private final int value;

        DEVICE_TYPE(int value) { this.value = value; }

        public int getValue() { return this.value;  }

        public static DEVICE_TYPE fromValue(int value)
        {
            for (DEVICE_TYPE type : DEVICE_TYPE.values())
                if (type.value == value) return type;
            return UNKNOWN;
        }
    }

    public enum ACTION_TYPE
    {
        SET(0),
        CLEAR(1),
        SEND_NOTIFICATION(2),
        DISABLE(3),
        UNKNOWN(-1);
        private final int value;

        ACTION_TYPE(int value) { this.value = value; }

        public int getValue() {  return this.value; }

        public static ACTION_TYPE fromValue(int value)
        {
            for (ACTION_TYPE type : ACTION_TYPE.values())
                if (type.value == value) return type;
            return UNKNOWN;
        }
    }

    public TimerService() { }

    @Override
    public void onCreate()
    {
        super.onCreate();
        scheduler = Executors.newScheduledThreadPool(3);
        InitNotificationChannel();
        scheduledTasks = new HashMap<>();
        for(DEVICE_TYPE type : DEVICE_TYPE.values()) scheduledTasks.put(type, null);
    }

    private void InitNotificationChannel()
    {
        channel = new NotificationChannel
                (
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
                );
        notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) notificationManager.createNotificationChannel(channel);
    }

    private NotificationCompat.Builder CreateNotification(String title, String content)
    {
        return new NotificationCompat.Builder(getApplicationContext(), channel.getId())
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        DEVICE_TYPE deviceType = DEVICE_TYPE.fromValue(intent.getIntExtra(DataTramsmissionDevicesMonitor.MONITOR_SERVICE_TYPE, -1));
        ACTION_TYPE action = ACTION_TYPE.fromValue(intent.getIntExtra(DataTramsmissionDevicesMonitor.ACTION_TYPE, -1));
        Log.d("SERVICE", "onStartCommand called with action: " + action + ", deviceType: " + deviceType);
        startForeground(UUID.randomUUID().hashCode(), CreateNotification("Сервис запущен", "По завершении таймера будет показано уведомление о состоянии выбранных устройств").build());
        LocalTime time = LocalTime.of(0, 0, 0);
        if(action == ACTION_TYPE.SET)
        {
            try
            {
                time = LocalTime.parse(intent.getStringExtra(DataTramsmissionDevicesMonitor.TIME_EXTRAS));
            }
            catch (Exception e)
            {
                Log.d("ERR", e.toString());
            }
        }
        PerformAction(time, deviceType, action);
        return START_REDELIVER_INTENT;
    }

    private static long GetSecondsDifference(LocalTime time) {
        LocalDateTime currentDateTime = LocalDateTime.now();

        LocalDateTime alarmDateTime = currentDateTime.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);

        if (currentDateTime.isBefore(alarmDateTime))
        {
            long millis = java.time.Duration.between(currentDateTime, alarmDateTime).toMillis();
            return millis;
        }
        else
        {
            alarmDateTime = alarmDateTime.plusDays(1);
            long millis = java.time.Duration.between(currentDateTime, alarmDateTime).toMillis();
            return millis;
        }
    }

    private Intent GetAlarmIntent(DEVICE_TYPE device)
    {
        Intent alarmIntent = new Intent(this, TimerService.class);
        alarmIntent.setAction(ALARM_MANAGER_ACTION);
        alarmIntent.putExtra(DataTramsmissionDevicesMonitor.MONITOR_SERVICE_TYPE, device.getValue());
        alarmIntent.putExtra(DataTramsmissionDevicesMonitor.ACTION_TYPE, ACTION_TYPE.SEND_NOTIFICATION.getValue());
        return alarmIntent;
    }


    private void PerformAction(LocalTime time, DEVICE_TYPE deviceType, ACTION_TYPE action)
    {
        if(action.equals(ACTION_TYPE.UNKNOWN) || deviceType.equals(DEVICE_TYPE.UNKNOWN)) return;

        switch (action)
        {
            case SET:
                    CancelDeviceTraking(deviceType);
                    scheduledTasks.put(deviceType, scheduler.schedule(() -> startForegroundService(GetAlarmIntent(deviceType)),
                                    GetSecondsDifference(time),
                                    TimeUnit.MILLISECONDS));
                Log.d("SERVICE", "SET ACTION, TIME: " + GetSecondsDifference(time));
                break;
            case CLEAR:
                CancelDeviceTraking(deviceType);
                Log.d("SERVICE", "CLEAR ACTION");
                TryStopForeground();
                break;
            case SEND_NOTIFICATION:
                notificationManager.notify(UUID.randomUUID().hashCode(), CreateNotification(GetNotificationTitle(deviceType), GetNotificationContent(deviceType)).build());
                CancelDeviceTraking(deviceType);
                TryStopForeground();
                Log.d("SERVICE", "SEND NOTIFICATION ACTION");
                break;
            default: break;
        }
        sendBroadcast(new Intent(DataTramsmissionDevicesMonitor.UPDATE_VIEW_ACTION).putExtra(DataTramsmissionDevicesMonitor.MONITOR_SERVICE_TYPE, deviceType.getValue()).putExtra(DataTramsmissionDevicesMonitor.ACTION_TYPE, action.getValue()));
    }

    private void CancelDeviceTraking(DEVICE_TYPE deviceType)
    {
        if(scheduledTasks != null && scheduledTasks.containsKey(deviceType))
        {
            ScheduledFuture task = scheduledTasks.get(deviceType);
            if(task != null) task.cancel(false);
            scheduledTasks.remove(deviceType);
        }
    }

    private String GetNotificationTitle(DEVICE_TYPE type)
    {
        switch (type)
        {
            case WIFI: return "WIFI";
            case BLUETOOTH: return "Bluetooth";
            case GPS: return "GPS";
            default: return "";
        }
    }

    private String GetNotificationContent(DEVICE_TYPE type)
    {
        boolean isConnected = false;
        ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        switch (type)
        {
            case WIFI:
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) isConnected = true;
                break;
            case BLUETOOTH:
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) isConnected = true;
                break;
            case GPS:
                LocationManager mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                if(mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) isConnected = true;
                break;
            default: return "";
        }
        return "Устройство" + (isConnected ? " включено" : " выключено");
    }


    private void TryStopForeground()
    {
        Log.d("SERVICE", "TryStopForeground");
        int i = 1;
        for (ScheduledFuture task : scheduledTasks.values())
        {
            Log.d("SERVICE","Task " + i + ( task == null ? " is null" : " is NOT null") );
            if(task != null)
            {
                Log.d("SERVICE","Task " + i + " is done: " + task.isDone() );
            }
            i++;
        }
        for (ScheduledFuture task : scheduledTasks.values())
        {
            if(task!= null)
            {
                if(!task.isDone())
                {
                    Log.d("SERVICE", "Task " + task.toString() + " not yet completed");
                    return;
                }
            }
        }

        stopForeground(true);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy()
    {
        for(DEVICE_TYPE type : scheduledTasks.keySet())
        {
            ScheduledFuture task = scheduledTasks.get(type);
            if(task != null)
            {
                task.cancel(false);
            }
        }
        scheduledTasks.clear();
        scheduler.shutdownNow();

    }
}

//AlarmManagerState alarmManagerState = alarmManagers.get(deviceType);
//                    if(alarmManagerState != null)
//                    {
//                        Log.d("SERVICE", "AlarmManagerState is not null");
//                        AlarmManager manager = alarmManagerState.getManager();
//                        if(manager != null)
//                        {
//                            Log.d("SERVICE", "AlarmManager is not null");
//                            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
//                           System.currentTimeMillis() + GetSecondsDifference(time), GetAlarmPendingIntent(deviceType));
//                        }
//                    }
//                    alarmManagers.get(deviceType).getManager().setExact(AlarmManager.RTC_WAKEUP,
//                            System.currentTimeMillis() + GetSecondsDifference(time),
//                                        GetAlarmPendingIntent(deviceType));


//alarmManagers.get(type).getManager().cancel(GetAlarmPendingIntent(type));
//alarmManagers.get(type).setState(false);
//unregisterReceiver(alarmManagerReceiver);

//alarmManagers.get(deviceType).getManager().cancel(GetAlarmPendingIntent(deviceType));
//alarmManagers.get(deviceType).setState(false);

//private class AlarmManagerState
//{
//    private boolean state = false;
//    private AlarmManager manager;
//
//    AlarmManagerState(boolean state, AlarmManager manager)
//    {
//        this.state = state;
//        this.manager = manager;
//    }
//
//    public void setState(boolean state) { this.state = state; }
//    public boolean getState() { return this.state; }
//
//    public void setManager(AlarmManager manager) { this.manager = manager; }
//    public AlarmManager getManager() { return this.manager; }
//}