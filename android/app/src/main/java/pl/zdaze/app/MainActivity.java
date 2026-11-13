package pl.zdaze.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer recognizer;
    private boolean pageLoaded = false, pendingAlarm = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF0F172A);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                openExternal(u.toString());
                return true;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoaded = true;
                if (pendingAlarm) { pendingAlarm = false; js("window.__nativeAlarm&&window.__nativeAlarm()"); }
            }
        });
        setContentView(web);
        web.loadUrl(URL);

        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pl", "PL"));
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { js("window.__ttsDone(" + JSONObject.quote(id) + ")"); }
                    @Override public void onError(String id) { js("window.__ttsDone(" + JSONObject.quote(id) + ")"); }
                });
                ttsReady = true;
            }
        });

        askPermissions();
        handleIntent(getIntent());
    }

    private void askPermissions() {
        ArrayList<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 1);
    }

    private void ensureFullScreenPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (!nm.canUseFullScreenIntent()) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); }

    private void handleIntent(Intent i) {
        if (i != null && i.getBooleanExtra("alarm", false)) {
            AlarmReceiver.cancelNotification(this);
            if (pageLoaded) js("window.__nativeAlarm&&window.__nativeAlarm()"); else pendingAlarm = true;
        }
    }

    private void js(final String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else moveTaskToBack(true); }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }

    /** Interfejs dostępny w JS jako window.Native */
    class Bridge {
        @JavascriptInterface
        public void speak(String text, String id) {
            if (!ttsReady) { js("window.__ttsDone(" + JSONObject.quote(id) + ")"); return; }
            Bundle p = new Bundle();
            p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, p, id);
        }

        @JavascriptInterface
        public void listen(final String lang, final boolean partial) {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    askPermissions(); js("window.__srEnd('not-allowed')"); return;
                }
                if (!SpeechRecognizer.isRecognitionAvailable(MainActivity.this)) { js("window.__srEnd('unavailable')"); return; }
                if (recognizer != null) recognizer.destroy();
                recognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    boolean ended = false;
                    void end(String err) { if (ended) return; ended = true; js("window.__srEnd(" + (err == null ? "null" : JSONObject.quote(err)) + ")"); }
                    @Override public void onReadyForSpeech(Bundle p) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float v) {}
                    @Override public void onBufferReceived(byte[] b) {}
                    @Override public void onEndOfSpeech() {}
                    @Override public void onError(int e) {
                        end(e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "not-allowed" : "e" + e);
                    }
                    @Override public void onResults(Bundle r) {
                        ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l != null && !l.isEmpty()) js("window.__srFinal(" + JSONObject.quote(l.get(0)) + ")");
                        end(null);
                    }
                    @Override public void onPartialResults(Bundle r) {
                        ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l != null && !l.isEmpty()) js("window.__srPartial(" + JSONObject.quote(l.get(0)) + ")");
                    }
                    @Override public void onEvent(int t, Bundle p) {}
                });
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial);
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                recognizer.startListening(i);
            });
        }

        @JavascriptInterface
        public void stopListening() {
            runOnUiThread(() -> { if (recognizer != null) { recognizer.cancel(); js("window.__srEnd(null)"); } });
        }

        @JavascriptInterface
        public void setAlarm(double ts, String label) {
            AlarmReceiver.schedule(MainActivity.this, (long) ts, label);
            runOnUiThread(MainActivity.this::ensureFullScreenPermission);
        }

        @JavascriptInterface
        public void cancelAlarm() { AlarmReceiver.cancel(MainActivity.this); }

        @JavascriptInterface
        public void systemAlarm(int h, int m, String msg) {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, msg).putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            runOnUiThread(() -> { try { startActivity(i); } catch (Exception ignored) {} });
        }

        /**
         * Budzik w aplikacji Zegar na konkretny moment. Zegar przyjmuje tylko godzinę (bez daty)
         * i ustawia najbliższe jej wystąpienie – to wystarcza, gdy budzik jest w ciągu 24 h.
         * Dla późniejszego terminu przekazujemy dzień tygodnia (Zegar tworzy wtedy budzik
         * powtarzany w ten dzień – jedyny sposób, by trafić we właściwy dzień).
         */
        @JavascriptInterface
        public void systemAlarmAt(double ts, String msg) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis((long) ts);
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, c.get(java.util.Calendar.HOUR_OF_DAY))
                    .putExtra(AlarmClock.EXTRA_MINUTES, c.get(java.util.Calendar.MINUTE))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            if ((long) ts - System.currentTimeMillis() > 24L * 3600 * 1000) {
                ArrayList<Integer> days = new ArrayList<>();
                days.add(c.get(java.util.Calendar.DAY_OF_WEEK));
                i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, days);
            }
            runOnUiThread(() -> { try { startActivity(i); } catch (Exception ignored) {} });
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void openUrl(String url) { runOnUiThread(() -> openExternal(url)); }

        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Zdaze", text));
        }
    }
}
