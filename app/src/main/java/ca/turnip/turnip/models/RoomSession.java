package ca.turnip.turnip.models;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import static ca.turnip.turnip.helpers.Utils.readStringFromFile;

public class RoomSession {

    private static String TAG = RoomSession.class.getSimpleName();

    private ArrayList<Song> sessionQueue;
    private boolean sessionError = false;
    private Context context;
    private Date sessionDate;
    private Integer sessionQueueIndex;
    private SimpleDateFormat format = new SimpleDateFormat("EEE_d_MMM_yyyy_HH_mm_ss");
    private Song currentlyPlaying;
    private SongFactory songFactory = new SongFactory();
    private String roomName;

    public RoomSession(Context context, String roomName) {
        this.context           = context;
        this.roomName          = roomName;
        this.sessionDate       = Calendar.getInstance().getTime();
        this.sessionQueueIndex = -1;
        this.sessionQueue      = new ArrayList<>();
    }

    public RoomSession(Context context, JSONObject json) throws JSONException, ParseException {

        JSONObject jsonCurrentlyPlaying = null;

        if (json.has("currentlyPlaying")) {
            jsonCurrentlyPlaying = json.getJSONObject("currentlyPlaying");
            this.currentlyPlaying = songFactory.fromJson(jsonCurrentlyPlaying);
        }
        this.context           = context;
        this.roomName          = json.getString("roomName");
        this.sessionError      = json.getBoolean("error");
        this.sessionDate       = format.parse(json.getString("date"));
        this.sessionQueueIndex = json.getInt("queueIndex");
        this.sessionQueue      = new ArrayList<>();

        JSONArray jsonQueue = json.getJSONArray("queue");

        for (int i = 0; i < jsonQueue.length(); i++) {
            JSONObject jsonSong = jsonQueue.getJSONObject(i);
            Song song = songFactory.fromJson(jsonSong);
            if (song != null) {
                this.sessionQueue.add(song);
            }
        }
}

    public void addSong(Song song) {
        sessionQueue.add(song);
        writeToDisk();
    }

    public void playSong(Song song) {
        this.currentlyPlaying = song;
        sessionQueueIndex += 1;
        writeToDisk();
    }

    public void writeToDisk() {
        try {
            FileOutputStream outputStream = context.openFileOutput(getSessionFilename(),
                                                                   context.MODE_PRIVATE);
            JSONObject json = toJSON();
            printRoomSession();
            outputStream.write(json.toString().getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray jsonSongs = new JSONArray();

        Iterator<Song> iterator = sessionQueue.iterator();

        while (iterator.hasNext()) {
            Song next = iterator.next();
            jsonSongs.put(next.jsonSong);
        }

        if (currentlyPlaying != null) {
            json.put("currentlyPlaying", currentlyPlaying.jsonSong);
        }
        json.put("date", format.format(sessionDate));
        json.put("error", sessionError);
        json.put("roomName", roomName);
        json.put("queue", jsonSongs);
        json.put("queueIndex", sessionQueueIndex);

        return json;
    }

    public static RoomSession fromDisk(Context context, String filename) throws IOException,
                                                                                JSONException,
                                                                                ParseException {
        RoomSession session;

        String stringJSON = readStringFromFile(context, filename);
        JSONObject jsonSession = new JSONObject(stringJSON);
        session = new RoomSession(context, jsonSession);

        return session;
    }

    public String getSessionFilename() {
        String stringDate = format.format(sessionDate);
        return roomName + "_" + stringDate;
    }

    public ArrayList<Song> getSessionQueue() {
        return sessionQueue;
    }

    public void clearSessionQueue() {
        sessionQueue.clear();
        writeToDisk();
    }

    public void resetSessionQueueIndex() {
        sessionQueueIndex = 0;
        writeToDisk();
    }

    public Integer getSessionQueueIndex() {
        return sessionQueueIndex;
    }

    public void setError(boolean error) {
        sessionError = error;
        writeToDisk();
    }

    public void updateSessionDate() {
        sessionDate = Calendar.getInstance().getTime();
        writeToDisk();
    }

    public void setSessionDate(Date date) {
        sessionDate = date;
    }

    public boolean hasError() {
        return sessionError;
    }

    public boolean sessionDidFinish() {
        return sessionQueueIndex >= sessionQueue.size() - 1;
    }

    public void printRoomSession() {
        if (currentlyPlaying != null) {
            Log.i(TAG, "CurrentlyPlaying: " + currentlyPlaying.getString("name"));
        }
        for (int i = 0; i < this.sessionQueue.size(); i++) {
            Song song = this.sessionQueue.get(i);
            Log.i(TAG, "SongQueue: " + song.getString("name"));
        }
        Log.i(TAG, "RoomName: " + roomName);
        Log.i(TAG, "HasError: " + hasError());
        Log.i(TAG, "SessionQueueIndex: " + sessionQueueIndex);
    }

    @Override
    public String toString() {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            return "Failed to convert RoomSession to JSON string.";
        }
    }

}