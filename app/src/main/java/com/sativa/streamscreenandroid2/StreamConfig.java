package com.sativa.streamscreenandroid2;

public class StreamConfig {

    // Default fallback port if user input is invalid or not set
    private static int port = LanWebServer.DEFAULT_PORT;

    public static synchronized void setPort(int newPort) {
        // Simple sanity check; you can tighten this if you want
        if (newPort > 0 && newPort <= 65535) {
            port = newPort;
        }
    }

    public static synchronized int getPort() {
        return port;
    }
}
