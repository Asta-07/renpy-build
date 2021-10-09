package org.renpy.android;

import org.json.JSONObject;
import org.libsdl.app.SDLActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Collections;

import cyou.joiplay.commons.dialog.ErrorDialog;
import cyou.joiplay.commons.model.Game;
import cyou.joiplay.commons.model.GamePad;
import cyou.joiplay.commons.model.RenPyConfiguration;
import cyou.joiplay.commons.parser.GamePadParser;
import cyou.joiplay.commons.parser.GameParser;
import cyou.joiplay.commons.parser.RenPyConfigurationParser;
import cyou.joiplay.joipad.JoiPad;

public class PythonSDLActivity extends SDLActivity {

    /**
     * This exists so python code can access this activity.
     */
    public static PythonSDLActivity mActivity = null;

    /**
     * The layout that contains the SDL view. VideoPlayer uses this to add
     * its own view on on top of the SDL view.
     */
    public FrameLayout mFrameLayout;


    /**
     * A layout that contains mLayout. This is a 3x3 grid, with the layout
     * in the center. The idea is that if someone wants to show an ad, they
     * can stick it in one of the other cells..
     */
    public LinearLayout mVbox;
    public JoiPad joiPad;

    static public Game game = new Game();
    static public RenPyConfiguration configuration = new RenPyConfiguration();
    static public GamePad gamePad = new GamePad();

    static public String TAG = "RenPyPlugin";

    ResourceManager resourceManager;

    protected String[] getLibraries() {
        return new String[] {
            "renpython",
        };
    }

    // GUI code. /////////////////////////////////////////////////////////////

    public void addView(View view, int index) {
        mVbox.addView(view, index, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, (float) 0.0));
    }

    public void removeView(View view) {
        mVbox.removeView(view);
    }

    @Override
    public void setContentView(View view) {
        mFrameLayout = new FrameLayout(this);
        mFrameLayout.addView(view);

        try{
            String gameJson = getIntent().getStringExtra("game");
            String settingsJson = getIntent().getStringExtra("settings");
            JSONObject settings = new JSONObject(settingsJson);
            String configurationJson = settings.getJSONObject("renpy").toString();
            String gamepadJson = settings.getJSONObject("gamepad").toString();

            GameParser.parse(game, gameJson);
            RenPyConfigurationParser.parse(configuration, configurationJson);
            GamePadParser.parse(gamePad, gamepadJson);
        } catch (Exception e){
            Log.d(TAG, Log.getStackTraceString(e));
            showErrorDialog(e);
        }

        JoiPad joiPad = new JoiPad();
        joiPad.init(this, gamePad);
        joiPad.cheats(configuration.cheats);
        joiPad.setGame(game);
        joiPad.setOnCloseListener(this::onDestroy);
        joiPad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        joiPad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);

        joiPad.attachTo(this, mFrameLayout);


        mVbox = new LinearLayout(this);
        mVbox.setOrientation(LinearLayout.VERTICAL);
        mVbox.addView(mFrameLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (float) 1.0));

        super.setContentView(mVbox);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (joiPad != null) {
            if (joiPad.processGamepadEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    void showErrorDialog(Throwable e){
        StackTraceElement causeElement = e.getStackTrace()[0];
        String errorMessage = "Error "+e.getMessage()+"\n"+causeElement.getFileName()+":"+causeElement.getLineNumber()+"/"+causeElement.getClassName()+":"+causeElement.getMethodName();
        ErrorDialog errorDialog = new ErrorDialog();
        errorDialog.setTitle(R.string.error);
        errorDialog.setMessage(errorMessage);
        errorDialog.setCloseButton(R.string.close, ()-> onDestroy());
        errorDialog.show(this);
    }


    // Overriding this makes SDL respect the orientation given in the Android
    // manifest.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        return;
    }

    // Code to unpack python and get things running ///////////////////////////

    public void recursiveDelete(File f) {
        if (f.isDirectory()) {
            for (File r : f.listFiles()) {
                recursiveDelete(r);
            }
        }
        f.delete();
    }

    /**
     * This determines if unpacking one the zip files included in
     * the .apk is necessary. If it is, the zip file is unpacked.
     */
    public void unpackData(final String resource, File target) {

        /**
         * Delete main.pyo unconditionally. This fixes a problem where we have
         * a main.py newer than main.pyo, but start.c won't run it.
         */
        new File(target, "main.pyo").delete();

        boolean shouldUnpack = false;

        // The version of data in memory and on disk.
        String data_version = resourceManager.getString(resource + "_version");
        String disk_version = null;

        String filesDir = target.getAbsolutePath();
        String disk_version_fn = filesDir + "/" + resource + ".version";

        // If no version, no unpacking is necessary.
        if (data_version != null) {

            try {
                byte buf[] = new byte[64];
                InputStream is = new FileInputStream(disk_version_fn);
                int len = is.read(buf);
                disk_version = new String(buf, 0, len);
                is.close();
            } catch (Exception e) {
                disk_version = "";
            }

            if (! data_version.equals(disk_version)) {
                shouldUnpack = true;
            }
        }


        // If the disk data is out of date, extract it and write the
        // version file.
        if (shouldUnpack) {
            Log.v("python", "Extracting " + resource + " assets.");

            // Delete old libraries & renpy files.
            recursiveDelete(new File(target, "lib"));
            recursiveDelete(new File(target, "renpy"));

            target.mkdirs();

            AssetExtract ae = new AssetExtract(this);
            if (!ae.extractTar(resource + ".mp3", target.getAbsolutePath())) {
                toastError("Could not extract " + resource + " data.");
            }

            try {
                // Write .nomedia.
                new File(target, ".nomedia").createNewFile();

                // Write version file.
                FileOutputStream os = new FileOutputStream(disk_version_fn);
                os.write(data_version.getBytes());
                os.close();
            } catch (Exception e) {
                Log.w("python", e);
            }
        }

    }

    /**
     * Show an error using a toast. (Only makes sense from non-UI
     * threads.)
     */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(new Runnable () {
            public void run() {
                Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    public native void nativeSetEnv(String variable, String value);

    public void preparePython() {
        Log.v("python", "Starting preparePython.");

        mActivity = this;

        resourceManager = new ResourceManager(this);

        File externalStorage = new File(game.folder);
        File path;

        unpackData("private", getFilesDir());

        nativeSetEnv("ANDROID_PRIVATE", getFilesDir().getAbsolutePath());
        nativeSetEnv("ANDROID_PUBLIC",  externalStorage.getAbsolutePath());
        nativeSetEnv("ANDROID_OLD_PUBLIC", externalStorage.getAbsolutePath());

        // Figure out the APK path.
        String apkFilePath;
        ApplicationInfo appInfo;
        PackageManager packMgmr = getApplication().getPackageManager();

        try {
            appInfo = packMgmr.getApplicationInfo(getPackageName(), 0);
            apkFilePath = appInfo.sourceDir;
        } catch (NameNotFoundException e) {
            apkFilePath = "";
        }

        nativeSetEnv("JOIPLAY_HW_VIDEO", configuration.renpy_hw_video ? "1":"0");
        nativeSetEnv("JOIPLAY_AUTOSAVE", configuration.renpy_autosave ? "1":"0");
        nativeSetEnv("JOIPLAY_VARIANT_PHONE", configuration.renpy_phonesmallvariant ? "1":"0");
        nativeSetEnv("RENPY_GL_VSYNC", configuration.renpy_vsync ? "1":"0");

        if (configuration.renpy_less_memory){
            nativeSetEnv("RENPY_LESS_MEMORY", "1");
        }

        if (configuration.renpy_less_updates){
            nativeSetEnv("RENPY_LESS_UPDATES", "1");
        }

        nativeSetEnv("RENPY_RENDERER","gles");

        if (game.folder.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())  && (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)){
            String id = game.id.isEmpty() ? game.title.replaceAll("/[^\\p{L}\\p{N}]/ug","_") : game.id;

            File saveDir = new File(getExternalFilesDir(null).getAbsolutePath() + File.separator + id + File.separator + "saves");
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            nativeSetEnv("JOIPLAY_SAVEDIR", saveDir.getAbsolutePath());
        } else {
            nativeSetEnv("JOIPLAY_SAVEDIR", game.folder + File.separator + "saves");
        }

        nativeSetEnv("ANDROID_APK", apkFilePath);

        Log.v("python", "Finished preparePython.");
    };

    // App lifecycle.
    public ImageView mPresplash = null;

    Bitmap getBitmap(String assetName) {
        try {
            InputStream is = getAssets().open(assetName);
            Bitmap rv = BitmapFactory.decodeStream(is);
            is.close();

            return rv;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v("python", "onCreate()");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController windowInsetsController = getWindow().getDecorView().getWindowInsetsController();
            windowInsetsController.hide(WindowInsets.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
        super.onCreate(savedInstanceState);

        // Ensure that the surface has the right format when GL starts.
        setSurfaceViewFormat(1);

        // Show the presplash.
        Bitmap presplashBitmap =  getBitmap( "android-presplash.jpg");

        if (presplashBitmap != null) {

            mPresplash = new ImageView(this);
            mPresplash.setBackgroundColor(presplashBitmap.getPixel(0, 0));
            mPresplash.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mPresplash.setImageBitmap(presplashBitmap);

            mLayout.addView(mPresplash, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        }

    }

    /**
     * Called by Ren'Py to hide the presplash after start.
     */
    public void hidePresplash() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mActivity.mPresplash != null) {
                    mActivity.mLayout.removeView(mActivity.mPresplash);
                    mActivity.mPresplash = null;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.v("python", "onDestroy()");
        finishAffinity();
        System.exit(0);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.v("python", "onNewIntent()");
        setIntent(intent);
    }

    // Support public APIs. ////////////////////////////////////////////////////

    public void openUrl(String url) {
        openURL(url);
    }

    public void vibrate(double s) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate((int) (1000 * s));
        }
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    public PowerManager.WakeLock wakeLock = null;

    @SuppressLint("InvalidWakeLockTag")
    public void setWakeLock(boolean active) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
            wakeLock.setReferenceCounted(false);
        }

        if (active) {
            wakeLock.acquire();
        } else {
            wakeLock.release();
        }
    }

    // Activity Requests ///////////////////////////////////////////////////////

    // The thought behind this is that this will make it possible to call
    // mActivity.startActivity(Intent, requestCode), then poll the fields on
    // this object until the response comes back.

    public int mActivityResultRequestCode = -1;
    public int mActivityResultResultCode = -1;
    public Intent mActivityResultResultData = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.v("python", "onActivityResult(" + requestCode + ", " + resultCode + ", " + resultData.toString() + ")");

        mActivityResultRequestCode = requestCode;
        mActivityResultResultCode = resultCode;
        mActivityResultResultData = resultData;

        super.onActivityResult(requestCode, resultCode, resultData);
    }
}
