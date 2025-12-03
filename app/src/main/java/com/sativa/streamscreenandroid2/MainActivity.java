package com.sativa.streamscreenandroid2;

import static android.content.ContentValues.TAG;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PROJECTION = 100;
    private static final String PREFS_NAME = "stream_prefs";
    private static final String KEY_PASSWORD = "password";
    private int currentPort = LanWebServer.DEFAULT_PORT;
    private MediaProjectionManager projectionManager;
    private ActivityResultLauncher<Intent> projectionLauncher;
    private EditText editPassword;
    private TextView textStatus;
    private Button buttonStartStop;
    private boolean isSharing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        projectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String password = editPassword.getText().toString().trim();

                        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                        serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                        serviceIntent.putExtra(ScreenCaptureService.EXTRA_DATA_INTENT, data);
                        serviceIntent.putExtra(ScreenCaptureService.EXTRA_PASSWORD, password);

                        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent);

                        isSharing = true;
                        updateUi();
                    } else {
                        Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        editPassword = findViewById(R.id.editPassword);
        textStatus = findViewById(R.id.textStatus);
        buttonStartStop = findViewById(R.id.buttonStartStop);

        // Load saved password
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedPassword = prefs.getString(KEY_PASSWORD, "");
        editPassword.setText(savedPassword);

        updateUi();

        buttonStartStop.setOnClickListener(v -> {
            if (!isSharing) {
                startSharing();
            } else {
                stopSharing();
            }
        });
    }

    private int resolvePortFromUi() {
        int fallback = LanWebServer.DEFAULT_PORT;

        android.widget.EditText editPort = findViewById(R.id.editPort);
        if (editPort == null) {
            return fallback;
        }

        String text = editPort.getText().toString().trim();
        if (text.isEmpty()) {
            return fallback;
        }

        try {
            int value = Integer.parseInt(text);
            // Pick your allowed range; using 1024+ avoids privileged ports
            if (value >= 1024 && value <= 65535) {
                return value;
            }
        } catch (NumberFormatException ignore) {
        }

        // If invalid, just fall back
        return fallback;
    }

    private void startSharing() {
        int port = resolvePortFromUi();
        currentPort = port;
        StreamConfig.setPort(port);

        String password = editPassword.getText().toString().trim();
        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter a password for the stream.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save password so service + web server can use it
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_PASSWORD, password).apply();

        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        projectionLauncher.launch(captureIntent);

    }

    private void stopSharing() {
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        stopService(serviceIntent);
        isSharing = false;
        updateUi();
    }

    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface intf = interfaces.nextElement();
                java.util.Enumeration<java.net.InetAddress> addrs = intf.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        // This is a non-loopback IPv4 address (likely your LAN IP)
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        }
        return null; // fallback if nothing found
    }

    private void updateUi() {
        if (isSharing) {
            String ip = getLocalIpAddress();
            int port = currentPort;  // whatever we last chose

            String urlText;
            if (ip != null) {
                urlText = "Status: SHARING\nOpen http://" + ip + ":" + port + "/ in a browser on your LAN.";
            } else {
                urlText = "Status: SHARING\nOpen http://<phone-ip>:" + port + "/ in a browser on your LAN.";
            }
            textStatus.setText(urlText);
            buttonStartStop.setText(R.string.stop_sharing);
        } else {
            textStatus.setText(R.string.status_not_sharing2);
            buttonStartStop.setText(R.string.start_sharing2);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                String password = editPassword.getText().toString().trim();

                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_DATA_INTENT, data);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_PASSWORD, password);

                // Start as foreground service (required on new Android versions)
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent);

                isSharing = true;
                updateUi();
            } else {
                Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
