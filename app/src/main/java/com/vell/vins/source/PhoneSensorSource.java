package com.vell.vins.source;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

import com.vell.vins.ImageUtils;

import org.opencv.core.Mat;

public class PhoneSensorSource extends BaseSensorSource implements SensorEventListener, LocationListener {
    private JavaCamera cameraSource = new JavaCamera();
    private ImageReader imageReader;
    private final int imageWidth = 640;
    private final int imageHeight = 360;
    private long cameraTimestampsShiftWrtSensors = 0;
    private double sensorTimestampDalta = -1;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Context context;

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        @Override
        public void onImageAvailable(ImageReader reader) {
            // get the newest frame
            Image image = reader.acquireNextImage();

            if (image == null) {
                return;
            }
//            Log.i(TAG,"get new image, height: " + image.getHeight() + " width: " + image.getWidth());
            Mat originMat = ImageUtils.getMatFromImage(image);
            double timeSec = (image.getTimestamp() + cameraTimestampsShiftWrtSensors) / 1000000000.0 + sensorTimestampDalta;
            recvImage(timeSec, originMat);

            image.close();
        }
    };

    public PhoneSensorSource(Context context) {
        this.context = context;
    }

    @Override
    public void open(final StateCallback stateCallback) {
        // 启动相机
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
        cameraSource.addImageReader(imageReader);

        cameraSource.open(context, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                if (stateCallback != null) {
                    stateCallback.onOpened();
                }

                try {
                    cameraTimestampsShiftWrtSensors = ImageUtils.getCameraTimestampsShiftWrtSensors(cameraSource.getCameraCharacteristics());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                if (stateCallback != null) {
                    stateCallback.onClosed();
                }
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                if (stateCallback != null) {
                    stateCallback.onError();
                }
            }
        });


        // 注册imu和gps
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        final String bestProvider = locationManager.getBestProvider(new Criteria(), false);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(bestProvider, 20, 0.01f, this);
    }

    @Override
    public void close() {
        cameraSource.close();
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
    }

    private SensorEvent lastAccSensorEvent = null;
    private SensorEvent lastGyrSensorEvent = null;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (sensorTimestampDalta == -1) {
            sensorTimestampDalta = System.currentTimeMillis() / 1000.0 - event.timestamp / 1000000000.0;
        }
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                lastGyrSensorEvent = event;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                lastAccSensorEvent = event;
                break;
            case Sensor.TYPE_PRESSURE:
                break;
            default:
                break;
        }
        if (lastAccSensorEvent != null && lastGyrSensorEvent != null && lastAccSensorEvent.timestamp == lastGyrSensorEvent.timestamp) {
            long timeNanos = lastAccSensorEvent.timestamp;
            double ax = lastAccSensorEvent.values[0];
            double ay = lastAccSensorEvent.values[1];
            double az = lastAccSensorEvent.values[2];
            double gx = lastGyrSensorEvent.values[0];
            double gy = lastGyrSensorEvent.values[1];
            double gz = lastGyrSensorEvent.values[2];

            double timeSec = timeNanos / 1000000000.0 + sensorTimestampDalta;
            recvImu(timeSec, ax, ay, az, gx, gy, gz);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        double accuracy = location.getAccuracy();
        long timeNanos = location.getElapsedRealtimeNanos();

        double timeSec = timeNanos / 1000000000.0 + sensorTimestampDalta;
        recvGPS(timeSec, latitude, longitude, altitude, accuracy);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
