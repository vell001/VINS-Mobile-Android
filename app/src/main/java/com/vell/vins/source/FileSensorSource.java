package com.vell.vins.source;


import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class FileSensorSource extends BaseSensorSource {
    private static final String TAG = FileSensorSource.class.getSimpleName();

    static {
        System.loadLibrary("opencv_java3");
    }

    private File dataDir;
    private File imageSaveDir;
    private BufferedReader frameTxtReader;
    private BufferedReader imuTxtReader;
    private BufferedReader gpsTxtReader;
    private double dataTimeToNowDeltaSec = 0;
    private HandlerThread threadHandler;
    private Handler playbackHandler;
    private StateCallback stateCallback;
    private Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                String frameLine = frameTxtReader.readLine();
                if (frameLine == null) {
                    Log.e(TAG, "frame data is empty");
                    // 当frame结束时，回放结束，close
                    close();
                } else {
                    final double[] frameData = str2NumArr(frameLine);
                    if (frameData.length == 1) {
                        if (dataTimeToNowDeltaSec == 0) {
                            dataTimeToNowDeltaSec = SystemClock.uptimeMillis() / 1000.0 - frameData[0] + 0.1;
                        }
                        playbackHandler.postAtTime(new Runnable() {
                            @Override
                            public void run() {
                                Mat image = Imgcodecs.imread(String.format(Locale.CHINA, "%s/%.6f.jpg", imageSaveDir.getAbsoluteFile(), frameData[0]));
                                recvImage(frameData[0], image);

                                // 继续执行读帧
                                playbackHandler.post(frameRunnable);
                            }
                        }, (long) ((frameData[0] + dataTimeToNowDeltaSec) * 1000));
                    } else {
                        Log.e(TAG, "frame data format error");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable imuRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                String imuLine = imuTxtReader.readLine();
                if (imuLine == null) {
                    Log.e(TAG, "imu data is empty");
                } else {
                    final double[] imuData = str2NumArr(imuLine);
                    if (imuData.length == 7) {
                        if (dataTimeToNowDeltaSec == 0) {
                            dataTimeToNowDeltaSec = SystemClock.uptimeMillis() / 1000.0 - imuData[0] + 0.1;
                        }
                        playbackHandler.postAtTime(new Runnable() {
                            @Override
                            public void run() {
                                recvImu(imuData[0], imuData[1], imuData[2], imuData[3], imuData[4], imuData[5], imuData[6]);

                                // 继续执行读帧
                                playbackHandler.post(imuRunnable);
                            }
                        }, (long) ((imuData[0] + dataTimeToNowDeltaSec) * 1000));
                    } else {
                        Log.e(TAG, "imu data format error");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable gpsRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                String gpsLine = gpsTxtReader.readLine();
                if (gpsLine == null) {
                    Log.e(TAG, "gps data is empty");
                } else {
                    final double[] gpsData = str2NumArr(gpsLine);
                    if (gpsData.length == 5) {
                        if (dataTimeToNowDeltaSec == 0) {
                            dataTimeToNowDeltaSec = SystemClock.uptimeMillis() / 1000.0 - gpsData[0] + 0.1;
                        }
                        playbackHandler.postAtTime(new Runnable() {
                            @Override
                            public void run() {
                                recvGPS(gpsData[0], gpsData[1], gpsData[2], gpsData[3], gpsData[4]);

                                // 继续执行读帧
                                playbackHandler.post(gpsRunnable);
                            }
                        }, (long) ((gpsData[0] + dataTimeToNowDeltaSec) * 1000));
                    } else {
                        Log.e(TAG, "gps data format error");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public FileSensorSource(File dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public void open(StateCallback callback) {
        stateCallback = callback;
        if (dataDir != null && dataDir.exists()) {
            imageSaveDir = new File(dataDir, "image");
            File frameTxt = new File(dataDir, "frame.txt");
            File imuTxt = new File(dataDir, "imu.txt");
            File gpsTxt = new File(dataDir, "gps.txt");
            try {
                frameTxtReader = new BufferedReader(new FileReader(frameTxt));
                imuTxtReader = new BufferedReader(new FileReader(imuTxt));
                gpsTxtReader = new BufferedReader(new FileReader(gpsTxt));

                threadHandler = new HandlerThread("FileSensorSourceThread");
                threadHandler.start();
                playbackHandler = new Handler(threadHandler.getLooper());
                playbackHandler.post(frameRunnable);
                playbackHandler.post(imuRunnable);
                playbackHandler.post(gpsRunnable);

                if (stateCallback != null) stateCallback.onOpened();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (stateCallback != null) stateCallback.onError();
    }

    @Override
    public void close() {
        try {
            if (frameTxtReader != null) {
                frameTxtReader.close();
                frameTxtReader = null;
            }
            if (imuTxtReader != null) {
                imuTxtReader.close();
                imuTxtReader = null;
            }
            if (gpsTxtReader != null) {
                gpsTxtReader.close();
                gpsTxtReader = null;
            }
            if (threadHandler != null) {
                threadHandler.quit();
                threadHandler = null;
            }
            if (stateCallback != null) stateCallback.onClosed();
        } catch (IOException e) {
            e.printStackTrace();
            if (stateCallback != null) stateCallback.onError();
        }
    }

    private double[] str2NumArr(String str) {
        if (str == null) {
            return new double[0];
        }
        String[] arr = str.trim().split("\\s+");

        double[] doubles = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            doubles[i] = Double.valueOf(arr[i]);
        }
        return doubles;
    }
}
