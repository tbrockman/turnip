package ca.passtheaux.passtheaux;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import static ca.passtheaux.passtheaux.Main.APP_REDIRECT_URI;
import static ca.passtheaux.passtheaux.Main.CLIENT_ID;

public class Host extends AppCompatActivity {

    private static final String TAG = Host.class.getSimpleName();
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private String spotifyToken;
    private String spotifyCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);
        authenticateSpotify();
    }

    private void authenticateSpotify() {
        final AuthenticationRequest request = getAuthenticationRequest(AuthenticationResponse.Type.TOKEN);
        AuthenticationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request);
    }

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

        if (AUTH_TOKEN_REQUEST_CODE == requestCode) {
            spotifyToken = response.getAccessToken();

        } else if (AUTH_CODE_REQUEST_CODE == requestCode) {
            spotifyCode = response.getCode();
        }
        else {
            //TODO
        }
    }

    public void cancelClicked(View view) {
        finish();
    }

    public void startClicked(View view) {
        EditText roomName = (EditText) findViewById(R.id.roomName);
        EditText roomPassword = (EditText) findViewById(R.id.roomPassword);

        Intent roomIntent = new Intent(this, Room.class);
        roomIntent.putExtra("roomName", roomName.getText().toString());
        roomIntent.putExtra("roomPassword", roomPassword.getText().toString());
        roomIntent.putExtra("isHost", true);
        roomIntent.putExtra("spotifyToken", spotifyToken);

        startActivity(roomIntent);
    }
}
