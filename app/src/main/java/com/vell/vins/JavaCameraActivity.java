package com.vell.vins;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.thkoeln.jmoeller.vins_mobile_androidport.R;
import com.vell.vins.source.FileSensorRecorder;
import com.vell.vins.source.FileSensorSource;
import com.vell.vins.source.ISensorSource;
import com.vell.vins.source.PhoneSensorSource;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class JavaCameraActivity extends Activity {
    private static final String TAG = JavaCameraActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_CODE = 12345;
    private ISensorSource sensorSource;
    private FileSensorRecorder sensorRecorder;
    private boolean saveFrame = false;
    private File saveDir = new File(Environment.getExternalStorageDirectory(), "0_vins_record");
    private ISensorSource fileSensorSource;

    private final ISensorSource.SensorListener sensorListener = new ISensorSource.SensorListener() {
        @Override
        public void recvImu(double timeSec, double ax, double ay, double az, double gx, double gy, double gz) {
            VinsUtils.recvImu(timeSec, ax, ay, az, gx, gy, gz);
        }

        Writer gpsTxtWriter = null;

        @Override
        public void recvImage(double timeSec, Mat bgr) {
            Mat vinsMat = new Mat();
            bgr.copyTo(vinsMat);
            VinsUtils.recvImage(timeSec, vinsMat.nativeObj);

            final Bitmap originBitmap = Bitmap.createBitmap(vinsMat.cols(), vinsMat.rows(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(vinsMat, originBitmap);
            if (saveFrame) {
                try {
                    saveFrame = false;
                    if (!saveDir.exists()) {
                        saveDir.mkdirs();
                    }
                    File frameFile = new File(saveDir, String.format("%s.jpg", new Date().toString()));

                    originBitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(frameFile));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

            final StringBuilder infoBuilder = new StringBuilder();
            float[] pos = VinsUtils.getLatestPosition();
            float[] ang = VinsUtils.getLatestEulerAngles();
            float[] gps = VinsUtils.getLatestGPS();
            if (gpsTxtWriter == null) {
                try {
                    gpsTxtWriter = new BufferedWriter(new FileWriter(new File(saveDir, "gps.txt")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (gps[0] != 0) {
                try {
                    gpsTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f\n", System.currentTimeMillis() / 1000.0, gps[0], gps[1], gps[2], 10.0));
                    gpsTxtWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            infoBuilder.append(String.format(Locale.CHINA, "pos: %.2f %.2f %.2f\n", pos[0], pos[1], pos[2]));
            infoBuilder.append(String.format(Locale.CHINA, "ang: %.2f %.2f %.2f\n", ang[0], ang[1], ang[2]));
            infoBuilder.append(String.format(Locale.CHINA, "gps: %.6f %.6f %.2f\n", gps[0], gps[1], gps[2]));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((ImageView) findViewById(R.id.java_camera_view)).setImageBitmap(originBitmap);

                    ((TextView) findViewById(R.id.tv_info)).setText(infoBuilder.toString());
                }
            });

            Log.i(TAG, "pos: " + VinsUtils.getLatestPosition()[0]);
            Log.i(TAG, "rot: " + VinsUtils.getLatestRotation()[0]);
        }

        @Override
        public void recvGPS(double timeSec, double latitude, double longitude, double altitude, double posAccuracy) {
            VinsUtils.recvGPS(timeSec, latitude, longitude, altitude, posAccuracy);
        }

        @Override
        public void recvMag(double timeSec, final double yaw, final double pitch, final double roll) {
            Log.i(TAG, String.format("mag: %.2f %.2f %.2f", yaw, pitch, roll));
            VinsUtils.setCurYaw((float) (yaw ));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.tv_mag_info)).setText(String.format(Locale.CHINA, "mag: %.2f %.2f %.2f", yaw, pitch, roll));
                }
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.act_java_camera);
        // first make sure the necessary permissions are given
        checkPermissionsIfNeccessary();

        sensorRecorder = new FileSensorRecorder();
        final ISensorSource phoneSensorSource = new PhoneSensorSource(JavaCameraActivity.this);
        fileSensorSource = new FileSensorSource(new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "0_vins_record/2019-03-12_14:26:00/"));
        sensorSource = phoneSensorSource;
        sensorSource.registerListener(sensorListener);
        sensorSource.registerListener(sensorRecorder);

        findViewById(R.id.tv_info).setOnClickListener(new View.OnClickListener() {
            private boolean useLocalImage = false;

            @Override
            public void onClick(View v) {
                sensorSource.close();
                sensorSource.unregisterListener(sensorListener);
                sensorSource.unregisterListener(sensorRecorder);
                if (useLocalImage) {
                    useLocalImage = false;
                    sensorSource = phoneSensorSource;

                    Toast.makeText(JavaCameraActivity.this, "使用摄像头数据", Toast.LENGTH_SHORT).show();
                } else {
                    useLocalImage = true;
                    sensorSource = fileSensorSource;
                    Toast.makeText(JavaCameraActivity.this, "使用本地数据", Toast.LENGTH_SHORT).show();

                }
                sensorSource.registerListener(sensorListener);
                sensorSource.registerListener(sensorRecorder);
                sensorSource.open(null);
            }
        });

        findViewById(R.id.start_slam).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!VinsUtils.initSucess()) {
                    VinsUtils.init("");
                    Toast.makeText(JavaCameraActivity.this, "开始slam", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.save_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFrame = true;
            }
        });
        findViewById(R.id.record).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (sensorRecorder.isRecording()) {
                    sensorRecorder.stopRecord();
                } else {
                    sensorRecorder.startRecord();
                }
                ((TextView) v).setText(sensorRecorder.isRecording() ? "停止" : "录像");
            }
        });
        findViewById(R.id.java_camera_view).setOnClickListener(new View.OnClickListener() {
            boolean enable = false;

            @Override
            public void onClick(View v) {
                enable = !enable;
                VinsUtils.enableAR(enable);
            }
        });

        findViewById(R.id.choose_dir).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser(saveDir);
            }
        });

        // 增加gps信息展示
        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.addGpsStatusListener(new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                GpsStatus status = locationManager.getGpsStatus(null); //取当前状态
                StringBuilder gpsInfo = new StringBuilder();
                gpsInfo.append("gps num: ");
                if (status == null) {
                    gpsInfo.append(0);
                } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                    int maxSatellites = status.getMaxSatellites();
                    Iterator<GpsSatellite> it = status.getSatellites().iterator();
                    int count = 0;
                    while (it.hasNext() && count <= maxSatellites) {
                        GpsSatellite s = it.next();
                        count++;
                    }
                    gpsInfo.append(count);
                }
                ((TextView) findViewById(R.id.tv_gps_info)).setText(gpsInfo.toString());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorSource.open(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorSource.close();
    }

    /**
     * @return true if permissions where given
     */
    private boolean checkPermissionsIfNeccessary() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                List<String> permissionsNotGrantedYet = new ArrayList<>(info.requestedPermissions.length);
                for (String p : info.requestedPermissions) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNotGrantedYet.add(p);
                    }
                }
                if (permissionsNotGrantedYet.size() > 0) {
                    ActivityCompat.requestPermissions(this, permissionsNotGrantedYet.toArray(new String[permissionsNotGrantedYet.size()]),
                            PERMISSIONS_REQUEST_CODE);
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean hasAllPermissions = true;
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length == 0)
                hasAllPermissions = false;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    hasAllPermissions = false;
            }

            if (!hasAllPermissions) {
                finish();
            }
        }
    }

    private static final int FILE_SELECT_CODE = 20001;

    private void showFileChooser(File baseDir) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");//无类型限制
        try {
            startActivityForResult(intent, FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    Log.d(TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = FileUtils.getPath(this, uri);
                    fileSensorSource = new FileSensorSource(new File(path).getParentFile());
                    Log.d(TAG, "File Path: " + path);
                    // Get the file instance
                    // File file = new File(path);
                    // Initiate the upload
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
