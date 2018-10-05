package ca.passtheaux.passtheaux;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public class Main extends AppCompatActivity {

    private static final String TAG = Host.class.getSimpleName();
    protected static final String APP_ENDPOINT = "ca.passtheaux.passtheaux";
    protected static final String APP_REDIRECT_URI = "ca.passtheaux.passtheaux://callback";
    protected static final String CLIENT_ID = "73c78b0a36de4ccfbe474c9e26ae8513";

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
        Intent roomListIntent = new Intent(this, RoomList.class);
        startActivity(roomListIntent);
    }

    public void hostButtonClicked(View view) {
        Intent hostIntent = new Intent(this, Host.class);
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
