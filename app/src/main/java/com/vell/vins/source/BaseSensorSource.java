package com.vell.vins.source;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseSensorSource implements ISensorSource {
    protected List<SensorListener> sensorListeners = new ArrayList<>();

    @Override
    public void registerListener(SensorListener callback) {
        if (callback != null && !sensorListeners.contains(callback)) {
            sensorListeners.add(callback);
        }
    }

    @Override
    public void unregisterListener(SensorListener callback) {
        sensorListeners.remove(callback);
    }

    protected void recvImu(double timeSec, double ax, double ay, double az, double gx, double gy, double gz) {
        for (SensorListener listener : sensorListeners) {
            listener.recvImu(timeSec, ax, ay, az, gx, gy, gz);
        }
    }

    protected void recvImage(double timeSec, Mat bgr) {
        for (SensorListener listener : sensorListeners) {
            listener.recvImage(timeSec, bgr);
        }
    }

    protected void recvGPS(double timeSec, double latitude, double longitude, double altitude,
                           double posAccuracy) {
        for (SensorListener listener : sensorListeners) {
            listener.recvGPS(timeSec, latitude, longitude, altitude, posAccuracy);
        }
    }
}
