package ca.turnip.turnip;

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

public class HostActivity extends SpotifyAuthenticatedActivity {

    private static final String TAG = HostActivity.class.getSimpleName();
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private Boolean spotifyIsChecked = false;
    private Boolean spotifyEnabled = false;
    private int spotifyExpiresIn;
    private String spotifyAccessToken;
    private String spotifyRefreshToken;

    private TableLayout table;
    private Switch spotifySwitch;
    private Button startButton;
    private TextInputEditText roomName;

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
    protected void onDestroy() {
        super.onDestroy();
        unbindConnectionService();
    }

    private void initializeStartButton() {
        startButton = findViewById(R.id.startButton);
        disableButton(startButton);
    }

    private void allowSubmit() {
        String text = roomName.getText().toString();
        if (text.length() > 0 && spotifyIsChecked) {
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

    private CompoundButton.OnCheckedChangeListener switchListener =
        new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    // TODO: only authenticate if we fail to get a new access using stored refresh token
                    authenticateSpotify();
                }

                spotifyIsChecked = isChecked;
                allowSubmit();
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

    private void bindHostActivityConnectionService() {
        connection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                backgroundService = ((BackgroundService.LocalBinder)service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "disconnected from service");
                backgroundService = null;
            }
        };
        bindConnectionService(context);
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
        roomIntent.putExtra("spotifyAccessToken", spotifyAccessToken);
        roomIntent.putExtra("spotifyRefreshToken", spotifyRefreshToken);
        roomIntent.putExtra("spotifyExiresIn", spotifyExpiresIn);

        startActivity(roomIntent);
    }
}
