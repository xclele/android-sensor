package com.example.androidsensor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SensorService extends Service {

    // Tag for logging
    private static final String TAG = "SensorService";

    // Called when the service is created
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        // Initialization logic for the service
        // You could initialize sensor managers, listeners, etc. here
        Toast.makeText(this, "Sensor Service Created", Toast.LENGTH_SHORT).show();
    }

    // Called when the service is started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");
        // Add your sensor-related logic here
        // For example, you might start collecting data from sensors here

        // Return START_STICKY to restart the service if the system kills it
        return START_STICKY;
    }

    // Called when the service is stopped
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
        // Cleanup logic for the service
        // Stop sensor data collection and release resources here
        Toast.makeText(this, "Sensor Service Destroyed", Toast.LENGTH_SHORT).show();
    }

    // Called when the service is bound to an activity
    @Override
    public IBinder onBind(Intent intent) {
        // Return null if the service doesn't support binding
        return null;
    }
}