package ca.passtheaux.passtheaux;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TableLayout;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import static ca.passtheaux.passtheaux.Main.APP_REDIRECT_URI;
import static ca.passtheaux.passtheaux.Main.CLIENT_ID;

public class Host extends AppCompatActivity {

    private static final String TAG = Host.class.getSimpleName();
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private int spotifyExpiresIn;
    private String spotifyToken;
    private String spotifyCode;

    private TableLayout table;
    private Switch spotifySwitch;

    private Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_host);
        initializeSwitch();
        initializeTable();
        // TODO: retrieve data in intents from potentially previous created room
    }

    public void initializeSwitch() {
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

    public void initializeTable() {
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
        spotifySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    authenticateSpotify();
                }
            }
        });
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
            spotifyExpiresIn = response.getExpiresIn();

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
        roomIntent.putExtra("spotifyExiresIn", spotifyExpiresIn);

        startActivity(roomIntent);
    }
}
