package be.humanoids.webthingify;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

import java.util.Objects;

public class WebthingService extends Service {
    private static final String CHANNEL_ID = "wt:service";
    private static final String STOP_SELF_ACTION ="wt:service:stopself";
    private BatteryManager batteryManager;
    private Phone phone;
    private BroadcastReceiver batteryReceiver;
    private ServerTask server;
    private PowerManager.WakeLock wakeLock;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public WebthingService getService() {
            return WebthingService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent stopSelfIntent = new Intent(this, WebthingService.class);
        stopSelfIntent.setAction(STOP_SELF_ACTION);
        Notification.Action notificationAction = new Notification.Action.Builder(null, "Stop", PendingIntent.getService(this, 0, stopSelfIntent, PendingIntent.FLAG_CANCEL_CURRENT)).build();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Web thing server")
                .setContentText("Web thing server is running")
                .addAction(notificationAction)
                .build();
        startForeground(1, notification);

        return START_STICKY;
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        super.onCreate();

        if(server != null) {
            return;
        }
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);

        phone = new Phone(
                "Put Phone Name Here",
                (SensorManager) getSystemService(SENSOR_SERVICE),
                batteryManager,
                (CameraManager) getSystemService(CAMERA_SERVICE),
                (Vibrator) getSystemService(VIBRATOR_SERVICE)
        );

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Objects.equals(intent.getAction(), Intent.ACTION_POWER_CONNECTED)) {
                    phone.setCharging(true);
                } else if (Objects.equals(intent.getAction(), Intent.ACTION_POWER_DISCONNECTED)) {
                    phone.setCharging(false);
                }
                phone.setBattery(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            }
        };
        registerReceiver(batteryReceiver, filter);

        server = new ServerTask(isRunning -> {
            Log.d("wt:service", isRunning ? "isRunning" : "failed to start");
            if(!isRunning) {
                stopSelf();
            }
        });
        server.execute(phone);
        //TODO stopSelf if server isn't started and turn off switch

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Webthingify:Server");
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("wt:service", "destroy");
        if(wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        server.onDestroy();
        server = null;
        unregisterReceiver(batteryReceiver);
        batteryReceiver = null;
        phone.onDestroy();
        phone = null;
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(Objects.equals(intent.getAction(), STOP_SELF_ACTION)) {
            stopSelf();
        }
        return binder;
    }

    private void createNotificationChannel() {
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Web thing service", importance);
        channel.setDescription("Web thing service notifications");
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}