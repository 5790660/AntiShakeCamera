package com.aaron.camera;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static java.lang.Thread.sleep;

public class MainActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener, SensorListener {

    private int screenWidth;
    private int screenHeight;

    private int previewWidth = 1440;
    private int previewHeight = previewWidth * 3 / 4;

    private Camera camera;
    private Camera.Parameters cameraParameters;
    private int cameraSelection = 0;

    private int defaultScreenResolution = -1;
    private ImageView btnFlashlight, btnSwitchCam, btnShutter;
    private Switch switchAntiShake;
    private boolean isFlashOn;

    private Handler mHandler;
    private LineView mLineView;
    private boolean isDraw;

    private long lastUpdate = System.currentTimeMillis();
    private static final int SHAKE_THRESHOLD = 100;
    private float last_x = 0;
    private float last_y = 0;
    private float last_z = 0;
    volatile float currentSpeed;
    private boolean isShake = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        setContentView(R.layout.activity_main);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,
                SensorManager.SENSOR_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_GAME);

        screenWidth = displaymetrics.widthPixels;
        screenHeight = displaymetrics.heightPixels;
        initHandler();
        initLayout();
        initThread();
    }

    private void initThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                isDraw = true;
                while (isDraw) {
                    mHandler.sendEmptyMessage((int) currentSpeed);
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void initHandler() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mLineView.setLinePoint(msg.what);
            }
        };
    }

    private void initLayout() {
        mLineView = (LineView) this.findViewById(R.id.line);
        btnFlashlight = (ImageView) findViewById(R.id.btn_flashlight);
        btnSwitchCam = (ImageView) findViewById(R.id.btn_front_camera);
        btnShutter = (ImageView) findViewById(R.id.btn_camera_shutter);
        switchAntiShake = (Switch) findViewById(R.id.btnAntiShake);
        btnFlashlight.setOnClickListener(this);
        btnSwitchCam.setOnClickListener(this);
        btnShutter.setOnClickListener(this);
        switchAntiShake.setOnClickListener(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            btnSwitchCam.setVisibility(View.VISIBLE);
        }
        initCameraLayout();
    }

    private void initCameraLayout() {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                stopPreview();
                try {
                    camera = Camera.open(cameraSelection);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                cameraParameters = camera.getParameters();
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);
                if( ! result || camera == null){
                    Toast.makeText(MainActivity.this, "无法连接到相机", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                RelativeLayout layout = (RelativeLayout) findViewById(R.id.surfaceLayout);
                if (layout != null && layout.getChildCount() > 0)
                    layout.removeAllViews();

                SurfaceView surfaceView = new SurfaceView(MainActivity.this);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(screenWidth, (int) (screenWidth * (previewWidth / (previewHeight * 1f))));
//        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1080, 1440);

                SurfaceHolder holder = surfaceView.getHolder();
                holder.addCallback(MainActivity.this);
                holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

                layout.addView(surfaceView, params);

                if(cameraSelection == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    btnFlashlight.setVisibility(View.GONE);
                else
                    btnFlashlight.setVisibility(View.VISIBLE);
            }
        }.execute();
    }

    public void stopPreview() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
        }
    }

    public void setCameraParameters() {
        if(camera == null){
            Toast.makeText(this, "无法连接到相机", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        //获取摄像头的所有支持的分辨率
        List<Camera.Size> resolutionList = camera.getParameters().getSupportedPreviewSizes();
        if(resolutionList != null && resolutionList.size() > 0) {
            Collections.sort(resolutionList, new Util.ResolutionComparator());
            Camera.Size previewSize = null;
            // TODO: 2017/2/24
            if (defaultScreenResolution == -1) {
                boolean hasSize = false;
                //如果摄像头支持1440*1080，那么强制设为1440*1080
                for (int i = 0; i < resolutionList.size(); i++) {
                    Camera.Size size = resolutionList.get(i);
                    if (size != null && size.width == 1440 && size.height == 1080) {
                        previewSize = size;
                        hasSize = true;
                        break;
                    }
                }
                //如果不支持设为中间的那个
                if (!hasSize) {
                    int mediumResolution = resolutionList.size() / 2;
                    if (mediumResolution >= resolutionList.size())
                        mediumResolution = resolutionList.size() - 1;
                    previewSize = resolutionList.get(mediumResolution);
                }
            } else {
                if (defaultScreenResolution >= resolutionList.size())
                    defaultScreenResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(defaultScreenResolution);
            }
            //获取计算过的摄像头分辨率
            if (previewSize != null) {
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
                cameraParameters.setPreviewSize(previewWidth, previewHeight);
            }
        }
        Camera.Size pictureSize = Util.getLargePictureSize(camera);
        cameraParameters.setPictureSize(pictureSize.width, pictureSize.height);
        cameraParameters.setRotation(90);

        camera.setDisplayOrientation(Util.determineDisplayOrientation(this, cameraSelection));
        List<String> focusModes = cameraParameters.getSupportedFocusModes();
        if(focusModes != null){
            Log.i("video", Build.MODEL);
            if (((Build.MODEL.startsWith("GT-I950"))
                    || (Build.MODEL.endsWith("SCH-I959"))
                    || (Build.MODEL.endsWith("MEIZU MX3")))&&focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)){

                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }else if(focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }else
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
        }
        camera.setParameters(cameraParameters);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setCameraParameters();
        camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
        }
    }

    private void takePhoto() {
        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Log.i("pic", "takePic");
                camera.stopPreview();
                final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap != null) {
                    Log.i("pic", "savePic");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            saveBitmap(bitmap);
                        }
                    }).start();
                }
                Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String saveBitmap(Bitmap bitmap) {
        File file = getOutputMediaFile();
        if (file.exists()) {
            file.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.flush();
            out.close();
            bitmap.recycle();
            camera.startPreview();
            return file.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "tempCamera");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINESE).format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");

        return mediaFile;
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btn_camera_shutter){
            takePhoto();
        }else if(v.getId() == R.id.btn_flashlight){
            if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)){
                //showToast(this, "不能开启闪光灯");
                return;
            }
            //闪光灯
            if(isFlashOn){
                isFlashOn = false;
                btnFlashlight.setSelected(false);
                cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
            else{
                isFlashOn = true;
                btnFlashlight.setSelected(true);
                cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            }
            camera.setParameters(cameraParameters);
        }else if(v.getId() == R.id.btn_front_camera) {
            //转换摄像头
            cameraSelection = ((cameraSelection == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
            btnSwitchCam.setImageResource(cameraSelection == Camera.CameraInfo.CAMERA_FACING_FRONT ? R.drawable.ic_camera_rear_black_24dp : R.drawable.ic_camera_front_black_24dp);
            initCameraLayout();

            if (cameraSelection == Camera.CameraInfo.CAMERA_FACING_FRONT)
                btnFlashlight.setVisibility(View.GONE);
            else {
                btnFlashlight.setVisibility(View.VISIBLE);
                if (isFlashOn) {
                    cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(cameraParameters);
                }
            }
        } else if (v.getId() == R.id.btnAntiShake) {
            if (switchAntiShake.isChecked())
                mLineView.setVisibility(View.VISIBLE);
            else
                mLineView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onSensorChanged(int sensor, float[] values) {
        if (sensor == SensorManager.SENSOR_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            // only allow one update every 100ms.
            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                float x = values[SensorManager.DATA_X];
                float y = values[SensorManager.DATA_Y];
                float z = values[SensorManager.DATA_Z];

                currentSpeed = Math.abs(x+y+z - last_x - last_y - last_z) / diffTime * 10000;
                if (currentSpeed > SHAKE_THRESHOLD) {
                    isShake = true;
                    Log.i("sensor", "shake detected w/ speed: " + currentSpeed);
                } else {
                    isShake = false;
                }
                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    @Override
    public void onAccuracyChanged(int sensor, int accuracy) {

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
    }
}
