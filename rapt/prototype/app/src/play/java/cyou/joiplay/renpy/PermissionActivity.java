package cyou.joiplay.renpy;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.renpy.android.PythonSDLActivity;

public class PermissionActivity extends Activity {
    private static final int fileRequestCode = 4900;

    private TextView progView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.renpy_permission);

        progView = findViewById(R.id.progText);
        progView.setText(R.string.checking_config);

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"}, fileRequestCode);
        } else {
            startGame();
        }
    }

    private void startGame(){
        if (getIntent() != null && getIntent().getExtras() != null) {
            Intent i = new Intent(this, PythonSDLActivity.class);
            i.putExtras(getIntent().getExtras());
            startActivity(i);
        } else {
            progView.setText(R.string.got_no_data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == fileRequestCode){
            if (Build.VERSION.SDK_INT >= 23){
                if ( checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED) {
                    startGame();
                } else {
                    requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, fileRequestCode);
                }
            }
        }
    }
}
