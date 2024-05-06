package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.Manifest;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {
    private EditText et_host, et_port;
    private TextView tv_log;
    private Button bt_connect;

    private Socket socket;
    private DataOutputStream outputStream;
    private SendDataThread sendDataThread;
    private SensorManager sensorManager;
    private Sensor sensor_gyro;
    private float[] wib = new float[3];
    private int[] CNT = new int[1];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initVariables();
        requestPermissions();
        registerSensorListener();
    }

    private void initView() {
        et_host = findViewById(R.id.et_host);
        et_port = findViewById(R.id.et_port);
        tv_log = findViewById(R.id.tv_log);
        bt_connect = findViewById(R.id.bt_connect);
        bt_connect.setOnClickListener(view -> {
            if (socket != null && socket.isConnected()) {
                disconnectHost();
            } else {
                connectHost();
            }
        });
    }

    private void initVariables() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor_gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sendDataThread = new SendDataThread();
        socket = new Socket();
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 600);
        }
    }

    private void registerSensorListener() {
        sensorManager.registerListener(new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                Log.d("SensorData", "Sensor type: " + event.sensor.getType() + ", values: " + Arrays.toString(event.values));
                sendDataToServer(event.values);
            }

            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        }, sensor_gyro, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void sendDataToServer(float[] values) {
        if (socket.isConnected() && sendDataThread.isAlive()) {
            ByteBuffer buffer = ByteBuffer.allocate(4 * values.length);
            for (float value : values) {
                buffer.putFloat(value);
            }
            sendDataThread.insertData(buffer.array());
        }
    }

    @SuppressLint("SetTextI18n")
    private void connectHost() {
        new Thread(() -> {
            try {
                String host = et_host.getText().toString();
                String port = et_port.getText().toString();
                socket.connect(new InetSocketAddress(host, Integer.parseInt(port)), 1000);
                outputStream = new DataOutputStream(socket.getOutputStream());
                sendDataThread.start();
                runOnUiThread(() -> {
                    bt_connect.setText("DISCONNECT");
                    tv_log.setText("Connected");
                });
            } catch (IOException e) {
                Log.e("Connection", "Connection error", e);
                runOnUiThread(() -> tv_log.setText("Connection failed: " + e.getMessage()));
            }
        }).start();
    }

    private void disconnectHost() {
        try {
            if (sendDataThread != null) {
                sendDataThread.stopThread();
            }
            if (socket != null) {
                socket.close();
            }
            runOnUiThread(() -> {
                bt_connect.setText("CONNECT");
                tv_log.setText("Disconnected");
            });
        } catch (IOException e) {
            Log.e("Connection", "Disconnection error", e);
            runOnUiThread(() -> tv_log.setText("Disconnection failed: " + e.getMessage()));
        }
    }

    private class SendDataThread extends Thread {
        private LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private boolean isRunning = true;

        public void stopThread() {
            isRunning = false;
            interrupt();
        }

        public void insertData(byte[] data) {
            try {
                queue.put(data);
            } catch (InterruptedException e) {
                Log.e("SendDataThread", "Failed to insert data into queue", e);
            }
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    byte[] data = queue.take();
                    outputStream.write(data);
                } catch (InterruptedException | IOException e) {
                    Log.e("SendDataThread", "Error sending data", e);
                    return;
                }
            }
        }
    }
}
