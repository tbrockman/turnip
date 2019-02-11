package ca.turnip.turnip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.IBinder;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static ca.turnip.turnip.MainActivity.APP_REDIRECT_URI;
import static ca.turnip.turnip.MainActivity.CLIENT_ID;
import static ca.turnip.turnip.MainActivity.HOST_ROOM;
import static ca.turnip.turnip.RoomConfiguration.MAJORITY;
import static ca.turnip.turnip.RoomConfiguration.NO_SKIP;
import static ca.turnip.turnip.RoomConfiguration.PERCENTAGE;

public class HostActivity extends BackgroundServiceConnectedActivity {

    private static final String TAG = HostActivity.class.getSimpleName();
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private Context context;

    // Spotify

    private boolean spotifyEnabled = false;
    private String spotifyRefreshToken;

    // Room configuration

    private int skipMode = MAJORITY;

    // UI

    private int shortAnimationDuration;
    private Animation labelErrorSlideInAnimation;
    private Button startButton;
    private ErrorDialog errorDialog;
    private FrameLayout switchProgressBarFrameLayout;
    private Interpolator linearOutSlowInInterpolator = new LinearOutSlowInInterpolator();
    private ProgressBar switchProgressBar;
    private Spinner skipModeDropdown;
    private SpotifyNotFoundDialog notFoundDialog;
    private Switch spotifySwitch;
    private TableLayout table;
    private TextInputLayout roomNameInputLayout;
    private TextInputEditText roomName;
    private TextView spotifySwitchText;
    private TextView spotifySwitchErrorText;

    // Start button validation

    private boolean isStarting = true;
    private boolean roomNameDirty = false;
    private boolean roomNameError = false;
    private boolean spotifySwitchDirty = false;
    private boolean spotifySwitchPending = false;
    private boolean spotifySwitchError = false;
    private boolean spotifyErrorShowing = false;
    private boolean startButtonEnabled = false;

    // Authentication listener

    private AuthenticationListener authenticationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_host);
        initializeColorChangingLabels();
        initializeRoomNameField();
        initializeStartButton();
        initializeSwitch();
        initializeSkipmodeDropdown();
        initializeTable();
        initializeSwitchProgressBar();
        initializeAnimations();

        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if ((backgroundService == null || !backgroundService.spotifyIsAuthenticated())
            && !isStarting) {
            spotifySwitch.setChecked(false);
            spotifyEnabled = false;
        }

        authenticationListener = new AuthenticationListener() {
            @Override
            public void onTokenSuccess() {
                backgroundService.getCurrentUserProfile(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        handleSpotifyAuthError();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        final JSONObject jsonResponse;
                        try {
                            jsonResponse = new JSONObject(response.body().string());
                            String product = jsonResponse.getString("product");
                            if (!product.equals("premium")) {
                                handleSpotifyNotPremium();
                            } else {
                                handleSpotifyAuthSuccess();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onTokenFailure() {
                handleSpotifyAuthError();
            }
        };

        if (backgroundService == null) {
            bindHostActivityConnectionService();
        } else {
            backgroundService.subscribeAuthListener(authenticationListener);
        }
        if (!isStarting) {
            validateSubmit(true);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AUTH_CODE_REQUEST_CODE) {
            final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);

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

        if (requestCode == HOST_ROOM) {
            isStarting = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
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
                    spotifySwitchDirty = true;
                    if (isChecked) {
                        spotifySwitchPending = true;
                        crossFadeAwaySwitch();
                        authenticateSpotify();
                    } else {
                        spotifyEnabled = false;
                        validateSubmit(true);
                    }
                }
            };

    DialogInterface.OnClickListener switchDialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            spotifyEnabled = false;
            spotifySwitch.setChecked(false);
            crossFadeInSwitch();
        }
    };

    private void handleSpotifyAuthSuccess() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spotifyEnabled = true;
                spotifySwitchPending = false;
                crossFadeInSwitch();
                validateSubmit(true);
            }
        });
    }

    private void handleSpotifyAuthError() {
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Utils.isNetworkAvailable(context)) {
                    errorDialog = new ErrorDialog(activity,"Authentication error", "Unable to retrieve necessary permissions from Spotify.", switchDialogClickListener);
                }
                else {
                    errorDialog = new ErrorDialog(activity, "Network error", "Unable to establish network connection to Spotify.", switchDialogClickListener);
                }
            }
        });
    }

    private void handleSpotifyNotPremium() {
        final Activity activity = this;
        if (backgroundService != null) {
            backgroundService.removeStoredSpotifyRefreshToken();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                errorDialog = new ErrorDialog(activity,
                        "Upgrade Spotify subscription",
                        "Hosting using Spotify doesn't work for free tier accounts, upgrade your Spotify subscription to Premium to continue.",
                        true);
            }
        });
    }

    private void createSpotifyNotInstalledDialog() {
        notFoundDialog = new SpotifyNotFoundDialog(this);
    }

    private void initializeColorChangingLabels() {
        spotifySwitchText = findViewById(R.id.spotifySwitchText);
    }

    private void initializeSkipmodeDropdown() {
        skipModeDropdown = findViewById(R.id.skipModeDropdown);
        skipModeDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String text = skipModeDropdown.getSelectedItem().toString();
                switch (text) {
                    case "Majority":
                        skipMode = MAJORITY;
                        break;
                    case "Percentage":
                        skipMode = PERCENTAGE;
                        break;
                    case "None":
                        Log.d(TAG, "setting no skip");
                        skipMode = NO_SKIP;
                        break;
                    default:
                        skipMode = MAJORITY;
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initializeStartButton() {
        startButton = findViewById(R.id.startButton);
        disableButton(startButton);
    }

    private void initializeSwitchProgressBar() {
        switchProgressBar = findViewById(R.id.switchProgressBar);
        switchProgressBar.setVisibility(View.GONE);
    }


    private void initializeAnimations() {
        labelErrorSlideInAnimation = new TranslateAnimation(0, 0,
                -16, 0);
        labelErrorSlideInAnimation.setDuration(210);
        labelErrorSlideInAnimation.setInterpolator(linearOutSlowInInterpolator);
        labelErrorSlideInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                spotifySwitchErrorText.setVisibility(View.VISIBLE);
                spotifySwitchText.setTextColor(getResources().getColor(R.color.inputLayoutLabelError));
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private void crossFadeAwaySwitch() {
        switchProgressBar.setAlpha(0f);
        switchProgressBar.setVisibility(View.VISIBLE);
        switchProgressBar.animate()
                         .alpha(1f)
                         .setDuration(shortAnimationDuration)
                         .setListener(null);

        spotifySwitch.animate()
                     .alpha(0f)
                     .setDuration(shortAnimationDuration)
                     .setListener(new AnimatorListenerAdapter() {
                         @Override
                         public void onAnimationEnd(Animator animation) {
                             super.onAnimationEnd(animation);
                             spotifySwitch.setVisibility(View.GONE);
                         }
                     });
    }

    private void crossFadeInSwitch() {
        spotifySwitch.setAlpha(0f);
        spotifySwitch.setVisibility(View.VISIBLE);
        spotifySwitch.animate()
                     .alpha(1f)
                     .setDuration(shortAnimationDuration)
                     .setListener(null);

        switchProgressBar.animate()
                         .alpha(0f)
                         .setDuration(shortAnimationDuration)
                         .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                switchProgressBar.setVisibility(View.GONE);
                            }
                         });
    }

    private void showRoomNameError() {
        roomNameInputLayout.setHelperTextEnabled(false);
        roomNameInputLayout.setHelperText(null);
        roomNameInputLayout.setError(getResources().getText(R.string.room_name_error));


    }

    private void clearRoomNameError() {
        if (roomNameError) {
            roomNameError = false;
            roomNameInputLayout.setError(null);
            roomNameInputLayout.setHelperText(getResources().getText(R.string.room_name_helper));
            roomNameInputLayout.setHelperTextEnabled(true);
        }
    }

    private void showSpotifySwitchError() {
        spotifyErrorShowing = true;
        spotifySwitchErrorText.startAnimation(labelErrorSlideInAnimation);
    }

    private void clearSpotifySwitchError() {
        if (spotifySwitchError) {
            spotifySwitchError = false;
            spotifyErrorShowing = false;
            spotifySwitchText.setTextColor(getResources().getColor(R.color.white));
            spotifySwitchErrorText.setVisibility(View.INVISIBLE);
        }
    }

    private void validateRoomName(Boolean checkDirty) {
        String text = roomName.getText().toString();
        if (text.length() == 0) {
            roomNameError = true;
            if (checkDirty && !roomNameDirty) return;
            showRoomNameError();
        }
        else {
            clearRoomNameError();
        }
    }

    private void validateSpotifySwitch(Boolean checkDirty) {
        if (!spotifyEnabled) {
            if (spotifyErrorShowing) return;
            spotifySwitchError = true;
            if ((checkDirty && !spotifySwitchDirty) || spotifySwitchPending) return;
            showSpotifySwitchError();
        }
        else {
            clearSpotifySwitchError();
        }
    }

    private void validateSubmit(Boolean checkDirty) {
        validateRoomName(checkDirty);
        validateSpotifySwitch(checkDirty);

        if (!spotifySwitchError && !roomNameError) {
            enableButton(startButton);
        } else {
            disableButton(startButton);
        }
    }

    public void disableButton(Button button) {
        startButtonEnabled = false;
        button.setAlpha((float) 0.25);
    }

    public void enableButton(Button button) {
        startButtonEnabled = true;
        button.setAlpha(1);
    }

    private void initializeRoomNameField() {
        roomNameInputLayout = findViewById(R.id.roomNameInputLayout);
        roomName = findViewById(R.id.roomName);
        roomName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                roomNameDirty = true;
                validateSubmit(true);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        roomName.requestFocus();
        InputMethodManager imm = (InputMethodManager)getSystemService(this.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,InputMethodManager.HIDE_IMPLICIT_ONLY);
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
        spotifySwitchErrorText = findViewById(R.id.switchErrorText);
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
        if (isSpotifyInstalled()) {
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
        else {
            if (backgroundService.existsStoredSpotifyRefreshToken()) {
                backgroundService.removeStoredSpotifyRefreshToken();
            }
            spotifySwitch.setChecked(false);
            createSpotifyNotInstalledDialog();
        }
    }

    private boolean isSpotifyInstalled() {
        return Utils.isPackageInstalled("com.spotify.music",
                                        context.getPackageManager());
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
                                        .setScopes(new String[]{"app-remote-control",
                                                                "user-read-private"})
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
                backgroundService = null;
            }
        };
        startAndBindConnectionService(this);
    }

    public void cancelClicked(View view) {
        finish();
    }

    public void startClicked(View view) {
        validateSubmit(false);
        if (startButtonEnabled) {
            EditText roomName = (EditText) findViewById(R.id.roomName);
//        EditText roomPassword = (EditText) findViewById(R.id.roomPassword);
            Intent roomIntent = new Intent(this, RoomActivity.class);
            roomIntent.putExtra("roomName", roomName.getText().toString());
//        roomIntent.putExtra("roomPassword", roomPassword.getText().toString());
            roomIntent.putExtra("isHost", true);
            roomIntent.putExtra("spotifyEnabled", spotifyEnabled);
            roomIntent.putExtra("skipMode", skipMode);
            startActivityForResult (roomIntent, HOST_ROOM);
        } else {

        }

    }
}
