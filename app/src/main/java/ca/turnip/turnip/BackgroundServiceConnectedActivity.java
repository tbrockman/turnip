package ca.turnip.turnip;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v7.app.AppCompatActivity;

public class BackgroundServiceConnectedActivity extends AppCompatActivity {

    protected BackgroundService backgroundService;
    protected ServiceConnection connection;

    public void startAndBindConnectionService(Context context) {
        Intent serviceIntent = new Intent(context, BackgroundService.class);
        startService(serviceIntent);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }

    public void unbindConnectionService() {
        unbindService(connection);
    }

}
