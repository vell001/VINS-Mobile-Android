package com.vell.vins.source;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;

import com.vell.vins.source.ISensorSource;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileSensorRecorder implements ISensorSource.SensorListener {
    private boolean isRecording = false;
    private File recordRootDir = new File(Environment.getExternalStorageDirectory(), "0_vins_record");
    private File recordDir;
    private File imageSaveDir;
    private Writer frameTxtWriter;
    private Writer imuTxtWriter;
    private Writer magTxtWriter;
    private Writer gpsTxtWriter;
    private HandlerThread threadHandler;
    private Handler recordHandler;
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss", Locale.CHINA);

    public void startRecord() {
        if (isRecording) {
            return;
        }
        recordDir = new File(recordRootDir, formatter.format(new Date()));
        if (!recordDir.exists() && !recordDir.mkdirs()) {
            return;
        }
        imageSaveDir = new File(recordDir, "image");
        if (!imageSaveDir.exists() && !imageSaveDir.mkdirs()) {
            return;
        }
        try {
            frameTxtWriter = new BufferedWriter(new FileWriter(new File(recordDir, "frame.txt")));
            imuTxtWriter = new BufferedWriter(new FileWriter(new File(recordDir, "imu.txt")));
            magTxtWriter = new BufferedWriter(new FileWriter(new File(recordDir, "mag.txt")));
            gpsTxtWriter = new BufferedWriter(new FileWriter(new File(recordDir, "gps.txt")));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        threadHandler = new HandlerThread("VINSRecordThread");
        threadHandler.start();
        recordHandler = new Handler(threadHandler.getLooper());
        isRecording = true;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void stopRecord() {
        if (!isRecording) {
            return;
        }
        try {
            if (frameTxtWriter != null) {
                frameTxtWriter.flush();
                frameTxtWriter.close();
                frameTxtWriter = null;
            }
            if (imuTxtWriter != null) {
                imuTxtWriter.flush();
                imuTxtWriter.close();
                imuTxtWriter = null;
            }
            if (magTxtWriter != null) {
                magTxtWriter.flush();
                magTxtWriter.close();
                magTxtWriter = null;
            }
            if (gpsTxtWriter != null) {
                gpsTxtWriter.flush();
                gpsTxtWriter.close();
                gpsTxtWriter = null;
            }
            recordDir = null;
            if (threadHandler != null) {
                threadHandler.quit();
                threadHandler = null;
            }
            recordHandler = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        isRecording = false;
    }

    public void recvImu(final double timeSec, final double ax, final double ay, final double az, final double gx, final double gy, final double gz) {
        if (isRecording) {
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (imuTxtWriter == null) return;
                        imuTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f %.6f %.6f\n", timeSec, ax, ay, az, gx, gy, gz));
                        if (timeSec % 100 <= 10) {
                            imuTxtWriter.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }

    @Override
    public void recvMag(final double timeSec, final double yaw, final double pitch, final double roll) {
        if (isRecording) {
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (magTxtWriter == null) return;
                        magTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f \n", timeSec, yaw, pitch, roll));
                        if (timeSec % 100 <= 10) {
                            magTxtWriter.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }

    public void recvImage(final double timeSec, final Mat originMat) {
        if (isRecording) {
            final Mat bgrMat = new Mat();
            Imgproc.cvtColor(originMat, bgrMat, Imgproc.COLOR_RGB2BGR);
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    String fileName = String.format(Locale.CHINA, "%.6f.jpg", timeSec);
                    File imageFile = new File(imageSaveDir, fileName);

                    if (Imgcodecs.imwrite(imageFile.getAbsolutePath(), bgrMat)) {
                        try {
                            if (frameTxtWriter == null) return;
                            frameTxtWriter.append(String.format(Locale.CHINA, "%.6f\n", timeSec));
                            frameTxtWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            });
        }
    }

    public void recvGPS(final double timeSec, final double latitude, final double longitude, final double altitude,
                        final double posAccuracy) {
        if (isRecording) {
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (gpsTxtWriter == null) return;
                        gpsTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f\n", timeSec, latitude, longitude, altitude, posAccuracy));
                        gpsTxtWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }
}
