package ca.turnip.turnip;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SongHistory {

    private static final String TAG = SongHistory.class.getSimpleName();

    private Context context;
    private HashMap<String, Song> songs;
    private static final String songHistoryFilename = "song_history";

    public SongHistory(Context context) {
        this.context = context;
        this.songs = readFromDisk();
    }

    public void addSong(Song song) {
        String songUri = song.getString("uri");
        songs.put(songUri, song);
    }

    public ArrayList<Song> toArrayList() {
        ArrayList<Song> songListHistory = new ArrayList<>();
        songListHistory.addAll(songs.values());
        //TODO: sort return values
        return songListHistory;
    }

    public void writeToDisk() {
        try {
            FileOutputStream outputStream = context.openFileOutput(songHistoryFilename, context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Song> entry : songs.entrySet()) {
                json.put(entry.getKey(), entry.getValue().jsonSong);
            }
            outputStream.write(json.toString().getBytes());
            outputStream.close();
        } catch (Exception e) {
        }
    }

    public HashMap<String, Song> readFromDisk() {
        HashMap<String, Song> fromDisk;

        try {
            String stringSongHistory = readStringFromFile(context, songHistoryFilename);
            fromDisk = parseStringHashMap(stringSongHistory);
        } catch (IOException e) {
            fromDisk = new HashMap<>();
        }
        return fromDisk;
    }
    
    public String readStringFromFile(Context context, String filename) throws IOException  {
        InputStream inputStream = context.openFileInput(filename);
        String string = "";

        if ( inputStream != null ) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String receiveString = "";
            StringBuilder stringBuilder = new StringBuilder();

            while ( (receiveString = bufferedReader.readLine()) != null ) {
                stringBuilder.append(receiveString);
            }

            inputStream.close();
            string = stringBuilder.toString();
        }

        Log.i(TAG, "STRING HERE:" + string);
        return string;
    }

    public HashMap<String, Song> parseStringHashMap(String stringHashMap) {
        HashMap<String, Song> parsedHashMap = new HashMap<>();
        try {
            JSONObject jsonSongHistory = new JSONObject(stringHashMap);
            Iterator<String> keys = jsonSongHistory.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                JSONObject jsonSong = (JSONObject) jsonSongHistory.get(key);
                Song song = null;

                if (jsonSong.getString("songType").equals("spotify")) {
                    song = new SpotifySong(jsonSong);
                }

                if (song != null) {
                    parsedHashMap.put(key, song);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return parsedHashMap;
    }
}