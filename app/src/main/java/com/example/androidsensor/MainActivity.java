package com.example.androidsensor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.Manifest;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;


public class MainActivity extends AppCompatActivity {
    public static MsgHandler msgHandler;
    // 消息类型
    public static final int DISPMSG_SENSOR = 101;               // 显示传感器数据
    public static final int DISPMSG_GNSS = 102;                 // 显示航姿数据
    public static final int DISPMSG_NMEA = 103;                 // 显示导航数据
    public static final int DISPMSG_PRNG = 104;                 // 2.0.1后不再显示KF数据
    public static final int DISPMSG_NAVI = 105;                 // 2.0.1后不再显示KF数据
    public static final int DISPMSG_IMGE = 109;                 // 2.0.1后不再显示KF数据
    private static final int OPEN_SET_REQUEST_CODE = 600;   // 权限请求标志

    public boolean isSave = false, isConnect = false;
    //private final PSINSDataStore psinsDataStore = new PSINSDataStore(this);
    private double week = 0, sec =  0;
    int sumimu = 0;

    private EditText et_host, et_port;
    private TextView tv_log;
    private Button bt_connect;
    private Socket socket;
    private DataOutputStream outputStream;
    private SendDataThread sendDataThread;

    private Timer timer;
    private TimerTask task;
    private long delay = 0;
    private long period = 2000; // 10 seconds
    TextureView mTextureView;
    ImageView mImageView;
    TextureView.SurfaceTextureListener surfaceTextureListener;
    CameraManager cameraManager;
    CameraDevice.StateCallback cam_stateCallback;
    CameraDevice opened_camera;
    Surface texture_surface;
    CameraCaptureSession.StateCallback cam_capture_session_stateCallback;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder requestBuilder;
    CaptureRequest.Builder requestBuilder_image_reader;
    ImageReader imageReader;
    Surface imageReaderSurface;
    Bitmap bitmap;
    byte[] imageByteArray;
    CaptureRequest request;
    Matrix matrix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewInitial();

        // 请求权限
        int hasPermission = ContextCompat.checkSelfPermission(getApplication(),
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    OPEN_SET_REQUEST_CODE);
        }

        // 禁止手机锁屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 传感器消息处理函数
        msgHandler = new MsgHandler();
        sendDataThread = new SendDataThread();
    }

    // 控件初始化
    private void ViewInitial() {
        socket = new Socket();
        matrix = new Matrix();
        matrix.postRotate(90);
        et_host = findViewById(R.id.et_host);
        et_port = findViewById(R.id.et_port);
        bt_connect = findViewById(R.id.bt_connect);
        tv_log = findViewById(R.id.tv_log);

        bt_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!socket.isConnected()){
                    connectHost();
                    startTask();
                } else {
                    disconnectHost();
                    startTask();
                }
            }
        });

        mTextureView = findViewById(R.id.textureView);
        mImageView = findViewById(R.id.imageView);
        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                texture_surface = new Surface(mTextureView.getSurfaceTexture());
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        };
        mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        imageReader = ImageReader.newInstance(160,90, ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image= reader.acquireLatestImage();
                ByteBuffer buffer= image.getPlanes()[0].getBuffer();
                int length= buffer.remaining();
                byte[] bytes= new byte[length];
                buffer.get(bytes);
                image.close();
                bitmap = BitmapFactory.decodeByteArray(bytes,0,length);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                //sendData2Handle(msgHandler, bitmap);
                mImageView.setImageBitmap(bitmap);
            }
        }, null);
        imageReaderSurface = imageReader.getSurface();
    }

    @SuppressLint("SetTextI18n")
    private void disconnectHost() {
        stopService();
        sendDataThread.stopThread();
        bt_connect.setText("CONNECT");
    }

    @SuppressLint("SetTextI18n")
    private void connectHost() {
        String host = et_host.getText().toString();
        String port = et_port.getText().toString();
        SocketConnectionThread socketConnectionThread = new SocketConnectionThread(host, Integer.parseInt(port));
        socketConnectionThread.start();
        while(socketConnectionThread.isAlive());
        if (socket.isConnected()){
            // 获取Socket的输出流
            try {
                outputStream = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sendDataThread.start();
            startService();
            bt_connect.setText("DISCONNECT");
        }
    }

    // Method to start the service
    public void startService() {
//        Intent serviceIntent = new Intent(getBaseContext(), GNSSService.class);
//        startService(serviceIntent);
        Intent serviceIntent = new Intent(getBaseContext(), SensorService.class);
        startService(serviceIntent);
    }

    // Method to stop the service
    public void stopService() {
        stopService(new Intent(getBaseContext(), SensorService.class));
//        stopService(new Intent(getBaseContext(), GNSSService.class));
    }

    // 消息处理类
    private class MsgHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            ByteBuffer byteBuffer;
            byte[] byteArray;
            super.handleMessage(msg);
            switch (msg.arg1){
                case DISPMSG_SENSOR:
                    sumimu++;
                    float[] sensor_data = msg.getData().getFloatArray("data");
                    byteBuffer = ByteBuffer.allocate(4 * sensor_data.length+2);
                    byteBuffer.put((byte) 0xAA);
                    byteBuffer.put((byte) 0x55);
                    for (float f : sensor_data) {
                        byteBuffer.putFloat(f);
                    }
                    byteArray = byteBuffer.array();
                    sendDataThread.insertData(byteArray);
                    tv_log.setText(byteArrayToHex(byteArray));
                    week = 0;
                    sec = 0;
                    break;
                case DISPMSG_GNSS:
                    double[] gnss_data = msg.getData().getDoubleArray("data");
                    week = gnss_data[8];
                    sec = gnss_data[9];
//                    if (isSave) { psinsDataStore.writegnssdata(gnss_data);}
                    break;
                case DISPMSG_PRNG:
                    double[] prng_data = msg.getData().getDoubleArray("data");
//                    if (isSave) { psinsDataStore.writeprngdata(prng_data);}
                    break;
                case DISPMSG_NAVI:
                    String navInfo = msg.getData().getString("data");
                    break;
                case DISPMSG_IMGE:
                    int imageSize = msg.getData().getInt("imageSize");
                    byteBuffer = ByteBuffer.allocate(6);
                    byteBuffer.put((byte) 0xAA);
                    byteBuffer.put((byte) 0xF5);
                    byteBuffer.putInt(imageSize);
                    byteArray = byteBuffer.array();
                    sendDataThread.insertData(byteArray);
                    sendDataThread.insertData(imageByteArray);
                    break;
            }
        }
    }

    public class SocketConnectionThread extends Thread {
        private String host;
        private int port;

        public SocketConnectionThread(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void run() {
            try {
                Log.d("State","before");
                socket.connect(new InetSocketAddress(host, port),1000);
//                socket = new Socket(host, port);
                Log.d("State","After");

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class SendDataThread extends Thread {
        int capacity = 10; // 队列容量
        LinkedBlockingQueue<byte[]> queue;
        byte[] byteArray;
        boolean isRunning = true;
        public SendDataThread() {
            queue = new LinkedBlockingQueue<>(capacity);
            byteArray = new byte[0];
        }

        public void stopThread() {
            isRunning = false; // 设置原子布尔型变量为false，表示需要停止线程
        }
        public void insertData(byte[] data) {
            try {
                queue.put(data);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    byteArray = queue.take();
                    outputStream.write(byteArray);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                outputStream.close();
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String byteArrayToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // 定时任务
    private void startTask() {
        if (timer == null) {
            timer = new Timer();
            task = new MyTask();
            timer.scheduleAtFixedRate(task, delay, period); // 定时任务将在delay毫秒后开始执行，之后每隔period毫秒执行一次。
        } else {
            timer.cancel(); // 取消之前的定时任务。
            timer = null;
        }
    }

    private class MyTask extends TimerTask {
        @Override
        public void run() {
            Log.d("IMG", "定时任务执行");
            //B4.1 配置request的参数 拍照模式(这行代码要调用已启动的相机 opened_camera，所以不能放在外面
            try {
                requestBuilder_image_reader = opened_camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            requestBuilder_image_reader.set(CaptureRequest.JPEG_ORIENTATION,0);
            requestBuilder_image_reader.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            //B4.2 配置request的参数 的目标对象
            requestBuilder_image_reader.addTarget(imageReaderSurface );
            try {
                //B4.3 触发拍照
                cameraCaptureSession.capture(requestBuilder_image_reader.build(),null,null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void openCamera() {
        cameraManager= (CameraManager) getSystemService(Context.CAMERA_SERVICE);  // 初始化
        cam_stateCallback=new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                opened_camera=camera;
                try {
                    requestBuilder = opened_camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    requestBuilder.addTarget(texture_surface);
                    request = requestBuilder.build();
                    cam_capture_session_stateCallback=new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession=session;
                            try {
                                session.setRepeatingRequest(request,null,null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }
                    };
                    opened_camera.createCaptureSession( Arrays.asList(texture_surface,imageReaderSurface), cam_capture_session_stateCallback,null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
            }
            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
            }
        };
        checkPermission();
        try {
            cameraManager.openCamera(cameraManager.getCameraIdList()[0],cam_stateCallback,null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void checkPermission() {
        // 检查是否申请了权限
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.CAMERA)){

            }else{
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},1);
            }
        }
    }

    private void sendData2Handle(Handler handler, Bitmap bitmap){
        Message msg = handler.obtainMessage();
        msg.arg1 = DISPMSG_IMGE;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        imageByteArray = byteArrayOutputStream.toByteArray();
        Log.d("imageSize", String.valueOf(imageByteArray.length));
        Bundle bundle = new Bundle();
        bundle.putInt("imageSize", imageByteArray.length);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }
}