package ca.turnip.turnip;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.IBinder;
import android.support.design.widget.TextInputEditText;
import android.support.v4.content.ContextCompat;
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

import static ca.turnip.turnip.MainActivity.APP_REDIRECT_URI;
import static ca.turnip.turnip.MainActivity.CLIENT_ID;
import static com.spotify.sdk.android.authentication.LoginActivity.REQUEST_CODE;

public class HostActivity extends BackgroundServiceConnectedActivity {

    private static final String TAG = HostActivity.class.getSimpleName();
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    // Spotify

    private Boolean spotifyEnabled = false;
    private String spotifyRefreshToken;

    // UI

    private TableLayout table;
    private Switch spotifySwitch;
    private Button startButton;
    private TextInputEditText roomName;
    private ErrorDialog errorDialog;

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_host);
        bindHostActivityConnectionService();

        initializeRoomNameField();
        initializeStartButton();
        initializeSwitch();
        initializeTable();

        // TODO: retrieve data in intents from potentially previous created room
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE) {
            final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
            Log.i(TAG, "code:" + String.valueOf(resultCode) + "data:" + data.toString());

            switch (response.getType()) {
                // did we get the code we requested
                case CODE:
                    backgroundService.getAccessAndRefreshTokenFromCode(response.getCode());
                    break;
                default:
                    handleSpotifyAuthError();
                    spotifySwitch.setChecked(false);
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindConnectionService();
    }

    private CompoundButton.OnCheckedChangeListener switchListener =
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        authenticateSpotify();
                    }
                }
            };

    private AuthenticationListener authenticationListener = new AuthenticationListener() {
        @Override
        public void onTokenSuccess() {
            handleSpotifyAuthSuccess();
        }

        @Override
        public void onTokenFailure() {
            handleSpotifyAuthError();

        }
    };

    private void handleSpotifyAuthSuccess() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spotifyEnabled = true;
                allowSubmit();
            }
        });
    }

    private void handleSpotifyAuthError() {
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spotifyEnabled = false;
                spotifySwitch.setChecked(false);
                if (isNetworkAvailable(context)) {
                    errorDialog = new ErrorDialog(activity,"Authentication error", "Unable to retrieve necessary permissions from Spotify.");
                }
                else {
                    errorDialog = new ErrorDialog(activity, "Network error", "Unable to establish network connection to Spotify.");
                }
            }
        });
    }

    private void initializeStartButton() {
        startButton = findViewById(R.id.startButton);
        disableButton(startButton);
    }

    private void allowSubmit() {
        String text = roomName.getText().toString();
        if (text.length() > 0 && spotifyEnabled) {
            enableButton(startButton);
        }
        else {
            disableButton(startButton);
        }
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
                allowSubmit();
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

    public void authenticateSpotify() {
        try {
            spotifyRefreshToken  = backgroundService.getStoredSpotifyRefreshToken();
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving spotifyRefreshToken: " + e.toString());
        } finally {
            if (spotifyRefreshToken != null) {
                backgroundService.getAccessTokenFromRefreshToken(spotifyRefreshToken);
            } else {
                openAuthenticationActivity();
            }
        }
    }

    private void openAuthenticationActivity() {
        AuthenticationRequest request = getAuthenticationRequest(AuthenticationResponse.Type.CODE);
        AuthenticationClient.openLoginActivity(this,
                                                AUTH_CODE_REQUEST_CODE,
                                                request);
    }

    private AuthenticationRequest getAuthenticationRequest(AuthenticationResponse.Type type) {
        return new AuthenticationRequest.Builder(CLIENT_ID, type, APP_REDIRECT_URI)
                                        .setShowDialog(false)
                                        .setScopes(new String[]{"app-remote-control"})
                                        .build();
    }

    private void bindHostActivityConnectionService() {
        connection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                backgroundService = ((BackgroundService.LocalBinder)service).getService();
                backgroundService.subscribeAuthListener(authenticationListener);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "disconnected from service");
                backgroundService = null;
            }
        };
        super.bindConnectionService(this);
    }

    public void cancelClicked(View view) {
        finish();
    }

    public void startClicked(View view) {
        EditText roomName = (EditText) findViewById(R.id.roomName);
//        EditText roomPassword = (EditText) findViewById(R.id.roomPassword);

        Intent roomIntent = new Intent(this, RoomActivity.class);
        roomIntent.putExtra("roomName", roomName.getText().toString());
//        roomIntent.putExtra("roomPassword", roomPassword.getText().toString());
        roomIntent.putExtra("isHost", true);
        roomIntent.putExtra("spotifyEnabled", spotifyEnabled);
        startActivity(roomIntent);
    }
}
