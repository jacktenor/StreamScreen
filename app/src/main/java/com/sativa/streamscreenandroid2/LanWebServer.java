package com.sativa.streamscreenandroid2;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import fi.iki.elonen.NanoHTTPD;

public class LanWebServer extends NanoHTTPD {

    private static final String PREFS_NAME = "stream_prefs";
    private static final String KEY_PASSWORD = "password";
    public static final int DEFAULT_PORT = 8080;

    private final Context appContext;
    private final FrameBroadcaster frameBroadcaster;

    public LanWebServer(@NonNull Context context,
                        int port,
                        @NonNull FrameBroadcaster frameBroadcaster) {
        super(port);
        this.appContext = context.getApplicationContext();
        this.frameBroadcaster = frameBroadcaster;
    }

    private String getCurrentPassword() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD, ""); // default
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        // Root: show the login page
        if ("/".equals(uri) && method == Method.GET) {
            return serveRootPage();
        }

        // Login handler: we expect POST with "token" in the body
        if ("/view".equals(uri)) {
            // Make sure POST body is parsed
            java.util.Map<String, String> files = new java.util.HashMap<>();
            try {
                session.parseBody(files);
            } catch (Exception ignored) {
            }

            // New API: getParameters() instead of deprecated getParms()
            java.util.Map<String, java.util.List<String>> params = session.getParameters();
            String token = null;
            java.util.List<String> tokens = params.get("token");
            if (tokens != null && !tokens.isEmpty()) {
                token = tokens.get(0);
            }

            // Correct logic: if NOT authorized, deny access
            if (isAuthorized(token)) {
                return unauthorizedResponse();
            }

            return serveViewPage(token);
        }

        // Frame endpoint: browser reloads /frame?token=... repeatedly
        if ("/frame".equals(uri)) {
            java.util.Map<String, java.util.List<String>> params = session.getParameters();
            String token = null;
            java.util.List<String> tokens = params.get("token");
            if (tokens != null && !tokens.isEmpty()) {
                token = tokens.get(0);
            }

            if (isAuthorized(token)) {
                return unauthorizedResponse();
            }

            return serveFrame();
        }

        // Anything else: 404
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
    }

    @Contract("null -> true")
    private boolean isAuthorized(String token) {
        if (token == null) return true;
        String expected = getCurrentPassword();
        return !token.equals(expected);
    }

    private Response serveRootPage() {
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='utf-8'>" +
                "<link rel='icon' type='image/png' href='icon.png'>" +
                "<title>StreamScreen Login</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "html, body { margin:0; padding:0; height:100%; font-family: sans-serif; }" +
                "body { " +
                "  background: radial-gradient(circle at top, #1500ff 0%, #000000 55%, #000000 100%);" +
                "  display:flex; align-items:center; justify-content:center; color:#ffffff;" +
                "}" +
                ".card {" +
                "  background: rgba(0,0,0,0.85);" +
                "  border-radius: 16px;" +
                "  padding: 32px 28px;" +
                "  box-shadow: 0 0 24px rgba(21,0,255,0.45);" +
                "  text-align:center;" +
                "  max-width: 360px;" +
                "  width: 90%;" +
                "}" +
                ".title {" +
                "  font-size: 26px;" +
                "  margin: 0 0 8px 0;" +
                "  color:#1500ff;" +
                "  letter-spacing: 0.06em;" +
                "}" +
                ".subtitle {" +
                "  font-size: 13px;" +
                "  color:#cccccc;" +
                "  margin-bottom: 20px;" +
                "}" +
                "label { display:block; margin-bottom:6px; font-size:14px; color:#ffffff; }" +
                "input[type=password] {" +
                "  width:100%;" +
                "  padding:10px 12px;" +
                "  border-radius:10px;" +
                "  border:1px solid #333;" +
                "  background:#050510;" +
                "  color:#ffffff;" +
                "  outline:none;" +
                "  box-sizing:border-box;" +
                "  font-size:14px;" +
                "  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;" +
                "}" +
                "input[type=password]:focus {" +
                "  border-color:#1500ff;" +
                "  background:#080820;" +
                "  box-shadow:0 0 10px rgba(21,0,255,0.7);" +
                "}" +
                ".button-row { margin-top:16px; }" +
                "input[type=submit] {" +
                "  padding:10px 24px;" +
                "  border-radius:999px;" +
                "  border:none;" +
                "  background:#1500ff;" +
                "  color:#ffffff;" +
                "  font-size:14px;" +
                "  cursor:pointer;" +
                "  box-shadow:0 0 14px rgba(21,0,255,0.6);" +
                "  transition: background 0.2s, box-shadow 0.2s, transform 0.1s;" +
                "}" +
                "input[type=submit]:hover {" +
                "  background:#2a26ff;" +
                "  box-shadow:0 0 20px rgba(42,38,255,0.9);" +
                "}" +
                "input[type=submit]:active {" +
                "  transform:scale(0.97);" +
                "  box-shadow:0 0 10px rgba(21,0,255,0.7);" +
                "}" +
                ".hint {" +
                "  margin-top:18px;" +
                "  font-size:12px;" +
                "  color:#bbbbbb;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='card'>" +
                "<h1 class='title'>StreamScreen</h1>" +
                "<div class='subtitle'>Secure LAN screen share</div>" +
                "<form method='POST' action='/view'>" +
                "<label for='pw'>Password</label>" +
                "<input id='pw' type='password' name='token' autocomplete='off' />" +
                "<div class='button-row'>" +
                "<input type='submit' value='View' />" +
                "</div>" +
                "</form>" +
                "<div class='hint'>Enter the password configured in the Android app.</div>" +
                "</div>" +
                "</body>" +
                "</html>";

        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response serveViewPage(String token) {
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='utf-8'>" +
                "<title>StreamScreen Viewer</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "html, body { margin:0; padding:0; height:100%; font-family: sans-serif; }" +
                "body { " +
                "  background: radial-gradient(circle at top, #1500ff 0%, #000000 55%, #000000 100%);" +
                "  display:flex;" +
                "  align-items:center;" +
                "  justify-content:center;" +
                "  color:#ffffff;" +
                "}" +
                ".card {" +
                "  background: rgba(0,0,0,0.85);" +
                "  border-radius: 16px;" +
                "  padding: 20px 20px 24px 20px;" +
                "  box-shadow: 0 0 24px rgba(21,0,255,0.45);" +
                "  max-width: 1000px;" +
                "  width: 95vw;" +
                "  box-sizing:border-box;" +
                "  text-align:center;" +
                "  display:flex;" +
                "  flex-direction:column;" +
                "}" +
                ".title {" +
                "  font-size: 22px;" +
                "  margin: 0;" +
                "  color:#1500ff;" +
                "  letter-spacing: 0.06em;" +
                "}" +
                ".subtitle {" +
                "  font-size: 12px;" +
                "  color:#cccccc;" +
                "  margin-top:4px;" +
                "}" +
                ".top-row {" +
                "  display:flex;" +
                "  flex-direction:column;" +
                "  align-items:center;" +
                "  gap:6px;" +
                "}" +
                ".fs-btn {" +
                "  margin-top:10px;" +
                "  padding:6px 14px;" +
                "  border-radius:999px;" +
                "  border:none;" +
                "  background:#1500ff;" +
                "  color:#ffffff;" +
                "  font-size:12px;" +
                "  cursor:pointer;" +
                "  box-shadow:0 0 10px rgba(21,0,255,0.6);" +
                "  transition: background 0.2s, box-shadow 0.2s, transform 0.1s;" +
                "}" +
                ".fs-btn:hover {" +
                "  background:#2a26ff;" +
                "  box-shadow:0 0 16px rgba(42,38,255,0.9);" +
                "}" +
                ".fs-btn:active {" +
                "  transform:scale(0.97);" +
                "  box-shadow:0 0 8px rgba(21,0,255,0.7);" +
                "}" +
                ".viewer-box {" +
                "  background:#000000;" +
                "  border-radius:12px;" +
                "  box-shadow: 0 0 14px rgba(0,0,0,0.9);" +
                "  margin-top:12px;" +
                "  padding:8px;" +
                "}" +
                "#screenImg {" +
                "  max-width:100%;" +
                "  max-height:70vh;" +
                "  width:100%;" +
                "  height:auto;" +
                "  display:block;" +
                "  margin:0 auto;" +
                "  background:#000;" +
                "  border-radius:8px;" +
                "  object-fit:contain;" +
                "}" +
                ".hint {" +
                "  margin-top:10px;" +
                "  font-size:11px;" +
                "  color:#aaaaaa;" +
                "}" +
                "#screenImg:fullscreen, #screenImg:-webkit-full-screen {" +
                "  width:100vw;" +
                "  height:100vh;" +
                "  max-width:none;" +
                "  max-height:none;" +
                "  border-radius:0;" +
                "  object-fit:contain;" +
                "  background:#000;" +
                "}" +
                "body.fs-mode {" +
                "  background:#000000;" +
                "}" +
                "body.fs-mode .card {" +
                "  background:transparent;" +
                "  box-shadow:none;" +
                "  border-radius:0;" +
                "  padding:0;" +
                "  max-width:none;" +
                "  width:100vw;" +
                "  height:100vh;" +
                "}" +
                "body.fs-mode .viewer-box {" +
                "  margin:0;" +
                "  border-radius:0;" +
                "  box-shadow:none;" +
                "  padding:0;" +
                "}" +
                "body.fs-mode .top-row," +
                "body.fs-mode .hint {" +
                "  display:none;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='card' id='viewerCard'>" +
                "<div class='top-row'>" +
                "<h1 class='title'>StreamScreen</h1>" +
                "<div class='subtitle'>Live screen over secure LAN</div>" +
                "<button class='fs-btn' type='button' onclick='toggleFullscreen()'>⛶ Fullscreen</button>" +
                "</div>" +
                "<div class='viewer-box'>" +
                "<img id='screenImg' src='/frame?token=" + token + "&t=0' alt='Screen stream' />" +
                "</div>" +
                "<div class='hint'>Tip: Use fullscreen for best view. If the image freezes, refresh the page.</div>" +
                "</div>" +
                "<script>" +
                "const img = document.getElementById('screenImg');" +
                "setInterval(function(){" +
                "  img.src = '/frame?token=" + token + "&t=' + Date.now();" +
                "}, 150);" +
                "function toggleFullscreen(){" +
                "  if (!document.fullscreenElement && !document.webkitFullscreenElement) {" +
                "    if (img.requestFullscreen) img.requestFullscreen();" +
                "    else if (img.webkitRequestFullscreen) img.webkitRequestFullscreen();" +
                "  } else {" +
                "    if (document.exitFullscreen) document.exitFullscreen();" +
                "    else if (document.webkitExitFullscreen) document.webkitExitFullscreen();" +
                "  }" +
                "}" +
                "function onFsChange(){" +
                "  const fsElement = document.fullscreenElement || document.webkitFullscreenElement;" +
                "  if (fsElement) document.body.classList.add('fs-mode');" +
                "  else document.body.classList.remove('fs-mode');" +
                "}" +
                "document.addEventListener('fullscreenchange', onFsChange);" +
                "document.addEventListener('webkitfullscreenchange', onFsChange);" +
                "</script>" +
                "</body>" +
                "</html>";

        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response serveFrame() {
        if (!frameBroadcaster.hasFrame()) {
            // No frame yet
            return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE,
                    "text/plain",
                    "No frame available yet.");
        }

        byte[] jpeg = frameBroadcaster.getLatestFrame();
        Response res = newFixedLengthResponse(Response.Status.OK, "image/jpeg", new java.io.ByteArrayInputStream(jpeg), jpeg.length);
        res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.addHeader("Pragma", "no-cache");
        res.addHeader("Expires", "0");
        return res;
    }

    private Response unauthorizedResponse() {
        Response res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Access denied: invalid password.");
        res.addHeader("WWW-Authenticate", "Basic realm=\"ScreenShare\"");
        return res;
    }
}
