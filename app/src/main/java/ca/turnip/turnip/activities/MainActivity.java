package ca.turnip.turnip.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import ca.turnip.turnip.services.BackgroundService;
import ca.turnip.turnip.R;

import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    public static final String APP_ENDPOINT = "ca.turnip.turnip";
    public static final String APP_REDIRECT_URI = "ca.turnip.turnip://callback";
    public static String API_ENDPOINT;
    public static final String CLIENT_ID = "73c78b0a36de4ccfbe474c9e26ae8513";
    public static final String KEY_STORE_ALIAS = "turnip_keystore";
    public static final int ADD_SONG_REQUEST = 1;
    public static final int HOST_ROOM = 2;

    private static final String TAG = HostActivity.class.getSimpleName();


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
