package com.sativa.streamscreenandroid2;

public class FrameBroadcaster {

    private byte[] latestFrame;

    public FrameBroadcaster() {
    }

    public synchronized void updateFrame(byte[] jpegData) {
        this.latestFrame = jpegData;
    }

    public synchronized byte[] getLatestFrame() {
        return latestFrame;
    }

    public synchronized boolean hasFrame() {
        return latestFrame != null && latestFrame.length > 0;
    }
}
