package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
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
    private Sensor sensorGyro, sensorAcc, sensorMag;
    private float[] wib = new float[3]; // Gyroscope
    private float[] acc = new float[3]; // Accelerometer
    private float[] mag = new float[3]; // Magnetometer

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initVariables();
        requestPermissions();
        registerSensorListeners();
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
        sensorGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sendDataThread = new SendDataThread();
        socket = new Socket();
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 600);
        }
    }

    private void registerSensorListeners() {
        SensorEventListener sensorListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_GYROSCOPE:
                        System.arraycopy(event.values, 0, wib, 0, event.values.length);
                        break;
                    case Sensor.TYPE_ACCELEROMETER:
                        System.arraycopy(event.values, 0, acc, 0, event.values.length);
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        System.arraycopy(event.values, 0, mag, 0, event.values.length);
                        break;
                }
                sendDataToServer();
            }

            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(sensorListener, sensorGyro, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorListener, sensorAcc, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorListener, sensorMag, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void sendDataToServer() {
        // 分配足够的ByteBuffer空间来存储标识符和传感器数据
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 * wib.length + 1 + 4 * acc.length + 1 + 4 * mag.length);

        // 添加陀螺仪数据及其标识符
        buffer.put((byte) 1);  // 陀螺仪标识符为1
        for (float value : wib) buffer.putFloat(value);

        // 添加加速度计数据及其标识符
        buffer.put((byte) 2);  // 加速度计标识符为2
        for (float value : acc) buffer.putFloat(value);

        // 添加磁力计数据及其标识符
        buffer.put((byte) 3);  // 磁力计标识符为3
        for (float value : mag) buffer.putFloat(value);

        // 检查Socket连接并发送数据
        if (socket.isConnected() && sendDataThread.isAlive()) {
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
            if (sendDataThread != null) sendDataThread.stopThread();
            if (socket != null) socket.close();
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
