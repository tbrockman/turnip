package ca.turnip.turnip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Iterator;

public class RoomListActivity extends AppCompatActivity {

    private static final String TAG = RoomListActivity.class.getSimpleName();

    private BackgroundService backgroundService;

    // ProgressBar

    private ProgressBar findingRoomsProgressBar;

    // RecyclerView

    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<BackgroundService.Endpoint> rooms = new ArrayList<>();;

    // RoomActivity list listener

    private RoomListListener roomListListener =
            new RoomListListener() {
        @Override
        public void onRoomFound(BackgroundService.Endpoint host) {
            findingRoomsProgressBar.setVisibility(View.INVISIBLE);
            rooms.add(host);
            adapter.notifyItemInserted(rooms.size() - 1);
        }

        @Override
        public void onRoomLost(String endpointId) {
            Iterator<BackgroundService.Endpoint> it = rooms.iterator();
            if (rooms.size() == 0) {
                findingRoomsProgressBar.setVisibility(View.VISIBLE);
            }
            Log.i(TAG, "room lost: " + endpointId);
            while (it.hasNext()) {
                BackgroundService.Endpoint current = it.next();
                Log.i(TAG, current.toString() + " " + endpointId);

                if (current.getId().equals(endpointId)) {
                    it.remove();
                }
            }
            adapter.notifyDataSetChanged();
        }
    };

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            backgroundService = ((BackgroundService.LocalBinder)service).getService();
            backgroundService.subscribeRoomListListener(roomListListener);
            backgroundService.startDiscovery();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            backgroundService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_list);
        bindConnectionService();

        findingRoomsProgressBar = findViewById(R.id.findingRoomsProgressBar);
        recyclerView =  findViewById(R.id.roomRecyclerView);
        layoutManager = new LinearLayoutManager(this);
        adapter = new RoomListAdapter(rooms);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundService != null) {
            rooms = backgroundService.getDiscoveredHosts();
            adapter.notifyDataSetChanged();
            if (!backgroundService.isDiscovering()) {
                backgroundService.startDiscovery();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundService.stopDiscovery();
        unbindService(connection);
    }

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }
}
