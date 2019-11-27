package com.guichaguri.trackplayer.service;

import android.util.Log;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.annotation.SuppressLint;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.guichaguri.trackplayer.R;
import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import javax.annotation.Nullable;

/**
 * @author Guichaguri
 */
public class MusicService extends HeadlessJsTaskService {

    Handler handler;

    MusicBinder mbinder = null;
    MusicManager manager;

    private WifiLock wifiLock;
    private WakeLock wakeLock;

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        return new HeadlessJsTaskConfig("TrackPlayer", Arguments.createMap(), 0, true);
    }

    @Override
    public void onHeadlessJsTaskFinish(int taskId) {
        // Overridden to prevent the service from being terminated
    }

    public void emit(String event, Bundle data) {
        Intent intent = new Intent(Utils.EVENT_INTENT);

        intent.putExtra("event", event);
        if(data != null) intent.putExtra("data", data);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onCreate(){
        Log.d(Utils.LOG, "[MusicService] onCreate()");
        super.onCreate();
        create();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(Utils.NOTIFICATION_CHANNEL, "Playback", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(Utils.SERVICE_NAME);
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager not = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            not.createNotificationChannel(channel);
        }

        createWifiLock();
        newWakeLock();
    }

    public void create() {
        if (handler == null) {
            handler = new Handler();
        }

        if (manager == null) {
            manager = new MusicManager(this);
        }
        
        if (mbinder == null) {
            mbinder = new MusicBinder(this, manager);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(Utils.LOG, "[MusicService] onBind()");
        if(Utils.CONNECT_INTENT.equals(intent.getAction())) {
            return mbinder;
        }

        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(Utils.LOG, "[MusicService] onStartCommand() Intent - " + (intent != null ? intent.getAction() : "isNull"));

        boolean isMeduaButton = intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction());
        if(isMeduaButton) {
            MediaButtonReceiver.handleIntent(manager.getMetadata().getSession(), intent);
        }

        if(!manager.getMetadata().getSession().isActive()) {
            ReactInstanceManager reactInstanceManager = getReactNativeHost().getReactInstanceManager();
            ReactContext reactContext = reactInstanceManager.getCurrentReactContext();

            // Checks whether there is a React activity
            if(reactContext == null || !reactContext.hasCurrentActivity()) {
                String channel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? Utils.NOTIFICATION_CHANNEL : null;

                // Sets the service to foreground with an empty notification
                Notification notification = new NotificationCompat.Builder(this, channel)
                    .setSmallIcon(R.drawable.icon)
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .build();

                // Sets the service to foreground with an empty notification
                startForeground(Utils.NOTIFICATION_ID, new NotificationCompat.Builder(this, channel).build());

                // Stops the service right after
                if(isMeduaButton) {
                    stopSelf();
                }
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(Utils.LOG, "[MusicService] onDestroy()");
        super.onDestroy();
        destroy();
    }

    public void destroy() {
        if(handler != null) {
            handler.removeMessages(0);
            handler = null;
        }

        if(mbinder != null) {
            mbinder = null;
        }

        if(manager != null) {
            manager.destroy();
            manager = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(Utils.LOG, "[MusicService] onTaskRemoved()");
        super.onTaskRemoved(rootIntent);

        if(manager == null || manager.shouldStopWithApp()) {
            // destroy();
            stopForeground(true);
            stopSelf();
        }
    }

    @SuppressLint("WifiManagerPotentialLeak")
    private void createWifiLock() {
        if(wifiLock == null){
            // Create the Wifi lock (this does not acquire the lock, this just creates it)
            WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, Utils.SERVICE_NAME);
            wifiLock.setReferenceCounted(false);
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    private void newWakeLock() {
        if(wakeLock == null) {
            PowerManager powerManager = (PowerManager)getApplicationContext().getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Utils.SERVICE_NAME);
            wakeLock.setReferenceCounted(false);
        }
    }

    @SuppressLint("WakelockTimeout")
    public void lockServices(boolean isLocal) {
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        if (!isLocal && !wifiLock.isHeld()) {
            wifiLock.acquire();
        }
    }

    public void unlockServices() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
