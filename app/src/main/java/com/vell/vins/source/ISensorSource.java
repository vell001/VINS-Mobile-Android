package com.vell.vins.source;

import android.content.Context;

import org.opencv.core.Mat;

public interface ISensorSource {
    void registerListener(SensorListener callback);

    void unregisterListener(SensorListener callback);

    void open(StateCallback stateCallback);

    void close();

    interface SensorListener {
        void recvImu(double timeSec, double ax, double ay, double az, double gx, double gy, double gz);

        void recvImage(double timeSec, Mat bgr);

        void recvGPS(double timeSec, double latitude, double longitude, double altitude,
                     double posAccuracy);
    }

    interface StateCallback {
        void onOpened();

        void onClosed();

        void onError();
    }
}
