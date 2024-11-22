package ru.msfd.dz1;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class DataTramsmissionDevicesMonitor extends AppCompatActivity {

    ToggleButton wifiTB, bluetoothTB, gpsTB;
    private static final int PERMISSION_REQUEST_CODE = 5811;
    public static String TIME_EXTRAS = "time_extras";
    public static String MONITOR_SERVICE_TYPE = "service_type";
    public static String ACTION_TYPE = "action_type";

    public static String SharedPreferences = "tkachenko.sharedpreferences";

    public static final String UPDATE_VIEW_ACTION = "tkachenko.update.view";

    private final BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getAction().equalsIgnoreCase(UPDATE_VIEW_ACTION))
            {
                TimerService.DEVICE_TYPE type = TimerService.DEVICE_TYPE.fromValue(intent.getIntExtra(MONITOR_SERVICE_TYPE, -1));
                TimerService.ACTION_TYPE action = TimerService.ACTION_TYPE.fromValue(intent.getIntExtra(ACTION_TYPE, -1));
                Log.d("asfasdf", action.toString());
                switch (action)
                {
                    case CLEAR:
                    case SEND_NOTIFICATION:
                        ToggleButton tb = null;
                        SetDeviceTrakingState(context, type, false);
                        switch (type)
                        {
                            case WIFI: tb = findViewById(R.id.wifi_tb); break;
                            case BLUETOOTH: tb = findViewById(R.id.bluetooth_tb); break;
                            case GPS: tb = findViewById(R.id.gps_tb); break;
                            default: break;
                        }
                        if(tb != null)
                        {
                            tb.setOnCheckedChangeListener(null);
                            tb.setChecked(GetDeviceTrakingState(context, type));
                            tb.setOnCheckedChangeListener(listener);
                        }
                        break;
                    case SET: break;
                    default: break;
                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_data_tramsmission_devices_monitor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        RequestPermissions();
        registerReceiver(receiver, new IntentFilter(UPDATE_VIEW_ACTION));
    }

    private void RequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            permissionsToRequest.add(Manifest.permission.SET_ALARM);
        }
        permissionsToRequest.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsToRequest.add(Manifest.permission.WAKE_LOCK);

        List<String> permissionsNotGranted = new ArrayList<>();
        for (String permission : permissionsToRequest)
        {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                permissionsNotGranted.add(permission);
            }
        }
        if (!permissionsNotGranted.isEmpty())  requestPermissions(permissionsNotGranted.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        else  Setup();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE)
        {
            boolean allPermissionsGranted = true;
            for (int result : grantResults)
            {
                if (result != PackageManager.PERMISSION_GRANTED)
                {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) Setup();
            else {
                Toast.makeText(this, "Некоторые разрешения не предоставлены. Приложение может работать некорректно.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    private void Setup()
    {
        SetupViewsReferences();
        SetupViewsListeners();
    }

    private void SetupViewsReferences()
    {
        wifiTB = findViewById(R.id.wifi_tb);
        bluetoothTB = findViewById(R.id.bluetooth_tb);
        gpsTB = findViewById(R.id.gps_tb);
    }

    private CompoundButton.OnCheckedChangeListener listener = (compoundButton, state) ->
    {
        Intent serviceIntent = new Intent(DataTramsmissionDevicesMonitor.this, TimerService.class);
        TimerService.DEVICE_TYPE[] deviceType = new TimerService.DEVICE_TYPE[]{ TimerService.DEVICE_TYPE.UNKNOWN };
        switch(compoundButton.getId())
        {
            case R.id.wifi_tb: deviceType[0] = TimerService.DEVICE_TYPE.WIFI;
                break;
            case R.id.bluetooth_tb: deviceType[0] = TimerService.DEVICE_TYPE.BLUETOOTH;
                break;
            case R.id.gps_tb: deviceType[0] = TimerService.DEVICE_TYPE.GPS;
                break;
            default: break;
        }
        if(state)
        {
            LocalTime[] time = new LocalTime[] { LocalTime.of(0, 0, 0) };
            Calendar currentTime = Calendar.getInstance();
            TimePickerDialog timePickerDialog = new TimePickerDialog(DataTramsmissionDevicesMonitor.this, (timePicker, hours, minutes) ->
            {
                time[0] = LocalTime.of(hours, minutes, 0);
                serviceIntent.putExtra(TIME_EXTRAS, time[0].toString());
                serviceIntent.putExtra(ACTION_TYPE, TimerService.ACTION_TYPE.SET.getValue());
                serviceIntent.putExtra(MONITOR_SERVICE_TYPE, deviceType[0].getValue());
                startForegroundService(serviceIntent);
                SetDeviceTrakingState(DataTramsmissionDevicesMonitor.this, deviceType[0], true);
            }, currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE), true);
            timePickerDialog.setOnCancelListener(dialog -> sendBroadcast(new Intent(DataTramsmissionDevicesMonitor.UPDATE_VIEW_ACTION)
                    .putExtra(DataTramsmissionDevicesMonitor.MONITOR_SERVICE_TYPE,
                    deviceType[0].getValue()).putExtra(DataTramsmissionDevicesMonitor.ACTION_TYPE,
                    TimerService.ACTION_TYPE.CLEAR.getValue())));
            timePickerDialog.show();

            serviceIntent.putExtra(TIME_EXTRAS, time);
            serviceIntent.putExtra(ACTION_TYPE, TimerService.ACTION_TYPE.SET.getValue());
        }
        else
        {
            serviceIntent.putExtra(ACTION_TYPE, TimerService.ACTION_TYPE.CLEAR.getValue());
            serviceIntent.putExtra(MONITOR_SERVICE_TYPE, deviceType[0].getValue());
            startForegroundService(serviceIntent);
            SetDeviceTrakingState(DataTramsmissionDevicesMonitor.this, deviceType[0], false);
        }
    };

    private void SetupViewsListeners()
    {
        if(wifiTB != null) wifiTB.setOnCheckedChangeListener(listener);
        if(bluetoothTB != null) bluetoothTB.setOnCheckedChangeListener(listener);
        if(gpsTB != null) gpsTB.setOnCheckedChangeListener(listener);
    }

    public static boolean GetDeviceTrakingState(Context ctx, TimerService.DEVICE_TYPE deviceType)
    {
        SharedPreferences sp = ctx.getSharedPreferences(SharedPreferences, MODE_PRIVATE);
        if(!sp.contains(String.valueOf(deviceType.getValue())))
        {
            android.content.SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(String.valueOf(deviceType.getValue()), false);
            editor.commit();
        }
        return sp.getBoolean(String.valueOf(deviceType.getValue()), false);
    }

    public static void SetDeviceTrakingState(Context ctx, TimerService.DEVICE_TYPE deviceType, boolean state)
    {
        SharedPreferences sp = ctx.getSharedPreferences(SharedPreferences, MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(String.valueOf(deviceType.getValue()), state);
        editor.commit();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(receiver);
    }
}