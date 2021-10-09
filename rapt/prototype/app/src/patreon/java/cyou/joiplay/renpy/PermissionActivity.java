package cyou.joiplay.renpy;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;

import org.renpy.android.PythonSDLActivity;

import cyou.joiplay.commons.dialog.Dialog;

public class PermissionActivity extends Activity {
    private static final int fileRequestCode = 4900;
    private static final int allRequestCode = 4901;

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

        setContentView(R.layout.renpy_permission);

        progView = findViewById(R.id.progText);
        progView.setText(R.string.checking_config);

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"}, fileRequestCode);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                if (!Environment.isExternalStorageManager()){
                    showManageStoragePermissionDialog();
                } else {
                    startGame();
                }
            } else {
                startGame();
            }
        }
    }

    private void showManageStoragePermissionDialog(){
        Dialog permissionDialog = new Dialog();
        permissionDialog.setTitle(R.string.permission);
        permissionDialog.setMessage(R.string.manage_external_storage_permission_message);
        permissionDialog.setPositiveButton(R.string.ok, ()->{
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s",getApplicationContext().getPackageName())));
                startActivityForResult(intent, allRequestCode);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, allRequestCode);
            }
        });
        permissionDialog.setNegativeButton(R.string.close, this::finishAffinity);
        permissionDialog.show(this);
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
        switch (requestCode){
            case fileRequestCode:
                if (Build.VERSION.SDK_INT >= 23){
                    if ( checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                            if (!Environment.isExternalStorageManager()){
                                showManageStoragePermissionDialog();
                            } else {
                                startGame();
                            }
                        } else {
                            startGame();
                        }
                    } else {
                        requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, fileRequestCode);
                    }
                }
                break;
            case allRequestCode:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        startGame();
                    } else {
                        showManageStoragePermissionDialog();
                    }
                }
        }
    }
}
