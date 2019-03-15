package ca.turnip.turnip.models;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class RoomConfiguration {

    public static final int NO_SKIP = 0;
    public static final int MAJORITY = 1;
    public static final int PERCENTAGE = 2;
    private static final String TAG = RoomConfiguration.class.getSimpleName();

    private boolean passwordProtected = false;
    private boolean spotifyEnabled = false;
    private int skipMode = MAJORITY;
    private int hostAppVersion = 0;
    private String roomName;

    public RoomConfiguration(boolean passwordProtected, boolean spotifyEnabled,
                             int skipMode, int hostAppVersion,
                             String roomName) {
        this.passwordProtected = passwordProtected;
        this.spotifyEnabled = spotifyEnabled;
        this.skipMode = skipMode;
        this.hostAppVersion = hostAppVersion;
        this.roomName = roomName;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jsonRoomConfiguration = new JSONObject();

        jsonRoomConfiguration.put("passwordProtected", passwordProtected);
        jsonRoomConfiguration.put("spotifyEnabled", spotifyEnabled);
        jsonRoomConfiguration.put("skipMode", skipMode);
        jsonRoomConfiguration.put("hostAppVersion", hostAppVersion);
        jsonRoomConfiguration.put("roomName", roomName);

        return jsonRoomConfiguration;
    }

    public static RoomConfiguration fromJSON(JSONObject jsonRoomInfo) {
        boolean passwordProtected = false;
        boolean spotifyEnabled = false;
        int skipMode = MAJORITY;
        int hostAppVersion = 0;
        String roomName = "";

        try {
            passwordProtected = jsonRoomInfo.getBoolean("passwordProtected");
        } catch (JSONException e) {}

        try {
            spotifyEnabled = jsonRoomInfo.getBoolean("spotifyEnabled");
        } catch (JSONException e) {}

        try {
            skipMode = jsonRoomInfo.getInt("skipMode");
        } catch (JSONException e) {}

        try {
            hostAppVersion = jsonRoomInfo.getInt("hostAppVersion");
        } catch (JSONException e) {}

        try {
            roomName = jsonRoomInfo.getString("roomName");
        } catch (JSONException e) {}

        return new RoomConfiguration(passwordProtected,
                                     spotifyEnabled,
                                     skipMode,
                                     hostAppVersion,
                                     roomName);
    }

    public boolean isSpotifyEnabled() {
        return spotifyEnabled;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }

    public int getSkipMode() {
        return skipMode;
    }

    public int getHostAppVersion() {
        return hostAppVersion;
    }

    public String getRoomName() {
        return roomName;
    }

    @Override
    public String toString() {
        String result = null;
        try {
            result = this.toJSON().toString();
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return result;
    }
}
