package ca.passtheaux.turnip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.IBinder;
import android.support.design.widget.TextInputEditText;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TableLayout;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static ca.passtheaux.turnip.Main.APP_REDIRECT_URI;
import static ca.passtheaux.turnip.Main.CLIENT_ID;

public class Host extends AppCompatActivity {

    private static final String TAG = Host.class.getSimpleName();
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private int spotifyExpiresIn;
    private String spotifyAccessToken;
    private String spotifyRefreshToken;
    private String spotifyCode;

    private TableLayout table;
    private Switch spotifySwitch;
    private Button startButton;
    private TextInputEditText roomName;

    private Context context;

    private ConnectionService connectionService;
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "disconnected from service");
            connectionService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_host);
        bindConnectionService();
        initializeRoomNameField();
        initializeStartButton();
        initializeSwitch();
        initializeTable();
        // TODO: retrieve data in intents from potentially previous created room
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    private void initializeStartButton() {
        startButton = findViewById(R.id.startButton);
        disableButton(startButton);
    }

    public void disableButton(Button button) {
        button.setEnabled(false);
        button.setAlpha((float) 0.25);
    }

    public void enableButton(Button button) {
        button.setEnabled(true);
        button.setAlpha(1);
    }

    private void initializeRoomNameField() {
        roomName = findViewById(R.id.roomName);
        roomName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.i(TAG, "Length: " + s.length());
                if (before == 0 && s.length() > 0) {
                    enableButton(startButton);
                }
                else if (before > 0 && s.length() == 0) {
                    disableButton(startButton);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void initializeTable() {
        table = findViewById(R.id.hostTable);
        table.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager imm = (InputMethodManager) view.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        });
        table.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                InputMethodManager imm = (InputMethodManager) v.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        });
    }

    private CompoundButton.OnCheckedChangeListener switchListener =
        new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // TODO: only authenticate if we fail to get a new access using stored refresh token
                    authenticateSpotify();
                }
            }
    };

    private void initializeSwitch() {
        spotifySwitch = findViewById(R.id.spotifySwitch);
        ColorStateList colorStateList = new ColorStateList(
                new int[][] {
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[] {
                        ContextCompat.getColor(context, R.color.spotifyGreen),
                        ContextCompat.getColor(context, R.color.white)
                }
        );
        spotifySwitch.setThumbTintList(colorStateList);
        spotifySwitch.setOnCheckedChangeListener(switchListener);
    }

    private void authenticateSpotify() {
        final AuthenticationRequest request = getAuthenticationRequest(AuthenticationResponse.Type.CODE);
        AuthenticationClient.openLoginActivity(this, AUTH_CODE_REQUEST_CODE, request);
    }

    private AuthenticationRequest getAuthenticationRequest(AuthenticationResponse.Type type) {
        return new AuthenticationRequest.Builder(CLIENT_ID, type, APP_REDIRECT_URI)
                                        .setShowDialog(false)
                                        .setScopes(new String[]{"app-remote-control"})
                                        .build();
    }

    private Callback accessAndRefreshTokenCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {

        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            // TODO: store access and refresh tokens on device
            try {
                final JSONObject jsonResponse = new JSONObject(response.body().string());
                spotifyAccessToken = jsonResponse.getString("access_token");
                spotifyRefreshToken = jsonResponse.getString("refresh_token");
                spotifyExpiresIn = Integer.valueOf(jsonResponse.getString("expires_in"));
                connectionService.setSpotifyAccessToken(spotifyAccessToken);
                connectionService.setSpotifyRefreshToken(spotifyRefreshToken);
                Log.i(TAG, jsonResponse.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);

        if (AUTH_TOKEN_REQUEST_CODE == requestCode) {
            spotifyAccessToken = response.getAccessToken();
            spotifyExpiresIn = response.getExpiresIn();

        } else if (AUTH_CODE_REQUEST_CODE == requestCode) {
            spotifyCode = response.getCode();
            // TODO: wasteful, creating json object and then later we just convert it to a string
            // again
            try {
                JSONObject jsonCode = new JSONObject();
                jsonCode.put("authorization_code", spotifyCode);
                connectionService.getAccessAndRefreshToken(jsonCode, accessAndRefreshTokenCallback);
            } catch (JSONException e) {
                Log.e(TAG, "Error initializing JSON code object: " + e.toString());
            }
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
        roomIntent.putExtra("spotifyAccessToken", spotifyAccessToken);
        roomIntent.putExtra("spotifyExiresIn", spotifyExpiresIn);

        startActivity(roomIntent);
    }

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent,
                connection,
                Context.BIND_AUTO_CREATE);
    }
}
