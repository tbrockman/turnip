package ca.turnip.turnip;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static ca.turnip.turnip.HostActivity.AUTH_CODE_REQUEST_CODE;
import static ca.turnip.turnip.HostActivity.AUTH_TOKEN_REQUEST_CODE;
import static ca.turnip.turnip.MainActivity.APP_REDIRECT_URI;
import static ca.turnip.turnip.MainActivity.CLIENT_ID;

public class SpotifyAuthenticatedActivity extends AppCompatActivity {

    private static final String TAG = SpotifyAuthenticatedActivity.class.getSimpleName();

    protected BackgroundService backgroundService;
    protected ServiceConnection connection;

    // Spotify integration

    protected boolean spotifyEnabled = false;
    protected int spotifyExpiresIn;
    protected String spotifyAccessToken;
    protected String spotifyRefreshToken;
    protected final Handler spotifyTimerHandler = new Handler();
    protected final Runnable spotifyRefreshTokenTimer = new Runnable() {
        @Override
        public void run() {
            if (spotifyRefreshToken != null) {
                backgroundService.getAccessTokenFromRefreshToken(spotifyRefreshToken,
                                                                 accessTokenCallback);
            }
        }
    };

    @Override
    protected void onDestroy() {
        spotifyTimerHandler.removeCallbacks(spotifyRefreshTokenTimer);
        super.onDestroy();
    }

    public void authenticateSpotify() {
        try {
            spotifyRefreshToken  = backgroundService.getStoredSpotifyRefreshToken();
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving spotifyRefreshToken: " + e.toString());
        } finally {

            if (spotifyRefreshToken != null) {
                backgroundService.getAccessTokenFromRefreshToken(spotifyRefreshToken,
                                                                 accessTokenCallback);
            } else {
                openAuthenticationActivity();
            }
        }
    }

    private void openAuthenticationActivity() {
        final AuthenticationRequest request = getAuthenticationRequest(
                AuthenticationResponse.Type.CODE);
        AuthenticationClient.openLoginActivity(this,
                                                AUTH_CODE_REQUEST_CODE,
                                                request);
    }

    private Callback accessTokenCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Error retrieving access token:" + e.toString());
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            // TODO: store access and refresh tokens on device
            try {
                final JSONObject jsonResponse = new JSONObject(response.body().string());
                Log.i(TAG, jsonResponse.toString());
                spotifyAccessToken = jsonResponse.getString("access_token");
                spotifyExpiresIn = Integer.valueOf(jsonResponse.getString("expires_in"));
                backgroundService.setSpotifyAccessToken(spotifyAccessToken);
                backgroundService.emitSpotifyAccessTokenToConnectedClients(spotifyAccessToken);
                // refresh 5 minutes before token expiry
                spotifyTimerHandler.postDelayed(spotifyRefreshTokenTimer,
                                     (spotifyExpiresIn-5) * 1000);
            } catch (Exception e) {
                openAuthenticationActivity();
                Log.e(TAG, "Error parsing/emitting access token: " + e.toString());
            }
        }
    };

    private Callback accessAndRefreshTokenCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Error retrieving access/refresh token" + e.toString());
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            // TODO: store access and refresh tokens on device
            try {
                final JSONObject jsonResponse = new JSONObject(response.body().string());
                Log.i(TAG, jsonResponse.toString());
                spotifyAccessToken = jsonResponse.getString("access_token");
                spotifyRefreshToken = jsonResponse.getString("refresh_token");
                spotifyExpiresIn = Integer.valueOf(jsonResponse.getString("expires_in"));
                spotifyTimerHandler.postDelayed(spotifyRefreshTokenTimer,
                                       spotifyExpiresIn * 1000);
                backgroundService.storeSpotifyRefreshToken(spotifyRefreshToken);
                backgroundService.setSpotifyAccessToken(spotifyAccessToken);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing/storing access and refresh tokens:" + e.toString());
            }
        }
    };

    private AuthenticationRequest getAuthenticationRequest(AuthenticationResponse.Type type) {
        return new AuthenticationRequest.Builder(CLIENT_ID, type, APP_REDIRECT_URI)
                                        .setShowDialog(false)
                                        .setScopes(new String[]{"app-remote-control"})
                                        .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
        Log.i(TAG, "code:" + String.valueOf(resultCode) + "data:" + data.toString());

        if (AUTH_TOKEN_REQUEST_CODE == requestCode) {
            spotifyAccessToken = response.getAccessToken();
            spotifyExpiresIn = response.getExpiresIn();

        } else if (AUTH_CODE_REQUEST_CODE == requestCode) {
            spotifyEnabled = true;
            backgroundService.getAccessAndRefreshTokenFromCode(response.getCode(),
                                                               accessAndRefreshTokenCallback);
        }
        else {
            //TODO
        }
    }

    public void bindConnectionService(Context context) {
        Intent serviceIntent = new Intent(context, BackgroundService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }

    public void unbindConnectionService() {
        unbindService(connection);
    }
}
