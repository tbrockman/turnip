package ca.turnip.turnip;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = HostActivity.class.getSimpleName();
    protected static final String APP_ENDPOINT = "ca.turnip.turnip";
    protected static final String APP_REDIRECT_URI = "ca.turnip.turnip://callback";
    protected static String API_ENDPOINT;
    protected static final String CLIENT_ID = "73c78b0a36de4ccfbe474c9e26ae8513";
    protected static final String KEY_STORE_ALIAS = "turnip_keystore";

    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean isDebug = ((this.getApplicationInfo().flags &
                ApplicationInfo.FLAG_DEBUGGABLE) != 0);

        if (false && isDebug) {
            API_ENDPOINT = "http://192.168.0.17:3001";
        }
        else {
            API_ENDPOINT = "https://api.turnipapp.com";
        }

        startService(new Intent(this, BackgroundService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, getRequiredPermissions())) {
            requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void findAuxButtonClicked(View view) {
        Intent roomListIntent = new Intent(this, RoomListActivity.class);
        startActivity(roomListIntent);
    }

    public void hostButtonClicked(View view) {
        Intent hostIntent = new Intent(this, HostActivity.class);
        startActivity(hostIntent);
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }
}
