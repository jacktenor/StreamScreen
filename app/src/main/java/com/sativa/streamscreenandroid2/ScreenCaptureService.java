package com.sativa.streamscreenandroid2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA_INTENT = "dataIntent";
    public static final String EXTRA_PASSWORD = "password";
    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_share_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "stream_prefs";
    private static final String KEY_PASSWORD = "password";
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private FrameBroadcaster frameBroadcaster;
    private LanWebServer lanWebServer;
    private int currentOrientation;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        frameBroadcaster = new FrameBroadcaster();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        currentOrientation = getResources().getConfiguration().orientation;

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent dataIntent = intent.getParcelableExtra(EXTRA_DATA_INTENT);
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        if (password == null || password.trim().isEmpty()) {
            password = "1234";
        }

        // Store password in SharedPreferences for the web server
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_PASSWORD, password).apply();

        startForeground(NOTIFICATION_ID, buildNotification());

        if (mediaProjection == null && dataIntent != null) {
            startCapture(resultCode, dataIntent);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation != currentOrientation) {
            Log.d(TAG, "Orientation changed from " + currentOrientation + " to " + newConfig.orientation);
            currentOrientation = newConfig.orientation;

            // Resize the virtual display and recreate image reader
            if (virtualDisplay != null && mediaProjection != null) {
                resizeCapture();
            }
        }
    }

    private void resizeCapture() {
        Log.d(TAG, "Resizing capture for orientation change");

        // Get new display metrics
        DisplayMetrics metrics = getCurrentDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        Log.d(TAG, "New display size: " + width + "x" + height + " density=" + density);

        // Close old image reader
        if (imageReader != null) {
            imageReader.close();
        }

        // Create new image reader with new dimensions
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

        // Resize the existing virtual display instead of creating a new one
        virtualDisplay.resize(width, height, density);

        // Update the surface
        virtualDisplay.setSurface(imageReader.getSurface());
    }

    private DisplayMetrics getCurrentDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ way: use DisplayManager
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager != null) {
                Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null) {
                    display.getRealMetrics(metrics);
                }
            }
        } else {
            // Older way: use WindowManager
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                display.getRealMetrics(metrics);
            }
        }

        // Fallback in case something went wrong
        if (metrics.widthPixels == 0 || metrics.heightPixels == 0) {
            metrics = getResources().getDisplayMetrics();
        }

        return metrics;
    }

    private Notification buildNotification() {
        int port = StreamConfig.getPort();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Screen sharing active")
                        .setContentText("Visit http://<phone-ip>:" + port + "/ on your LAN.")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true);

        return builder.build();
    }

    private void createNotificationChannel() {
        CharSequence name = "Screen Share";
        String description = "Notifications for screen sharing";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
    }

    private void startCapture(int resultCode, Intent dataIntent) {
        Log.d(TAG, "startCapture");
        mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent);

        // Start HTTP server on selected port (only once)
        if (lanWebServer == null) {
            int port = StreamConfig.getPort();  // read whatever MainActivity chose
            try {
                lanWebServer = new LanWebServer(getApplicationContext(), port, frameBroadcaster);
                lanWebServer.start();
                Log.d(TAG, "Web server started on port " + port);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start web server on port " + port, e);
            }
        }

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null");
            stopSelf();
            return;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "MediaProjection stopped");
                stopSelf();
            }
        }, null);

        // Get real display metrics that respect current rotation/orientation
        DisplayMetrics metrics = getCurrentDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        Log.d(TAG, "Virtual display size: " + width + "x" + height + " density=" + density);

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        captureThread = new HandlerThread("ScreenCaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        Bitmap bitmap = null;
        Bitmap cropped = null;
        Bitmap scaled = null;
        ByteArrayOutputStream baos = null;

        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                return;
            }

            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            // Original full buffer bitmap
            bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            // Crop to actual screen size
            cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);

            // Downscale to reduce data size (about 1/4 pixels)
            int targetWidth = width / 2;
            int targetHeight = height / 2;
            if (targetWidth <= 0) targetWidth = width;
            if (targetHeight <= 0) targetHeight = height;

            scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true);

            // Compress to JPEG
            baos = new ByteArrayOutputStream();
            // Quality 50 is a good tradeoff for smoothness vs detail
            scaled.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] jpegData = baos.toByteArray();

            // Send to the HTTP side
            frameBroadcaster.updateFrame(jpegData);

        } catch (Exception e) {
            Log.e(TAG, "Error processing screen frame", e);
        } finally {
            if (image != null) {
                image.close();
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (cropped != null && !cropped.isRecycled()) {
                cropped.recycle();
            }
            if (scaled != null && !scaled.isRecycled()) {
                scaled.recycle();
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (Exception ignore) {
                }
            }
        }
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (lanWebServer != null) {
            lanWebServer.stop();
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (imageReader != null) {
            imageReader.close();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (captureThread != null) {
            captureThread.quitSafely();
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}