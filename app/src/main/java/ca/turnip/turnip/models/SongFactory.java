package ca.turnip.turnip.models;

import org.json.JSONException;
import org.json.JSONObject;

public class SongFactory {

    public Song fromJson(JSONObject jsonSong) throws JSONException {
        Song song = null;

        if (jsonSong.getString("songType").equals("spotify")) {
            song = new SpotifySong(jsonSong);
        }

        return song;
    }
}
