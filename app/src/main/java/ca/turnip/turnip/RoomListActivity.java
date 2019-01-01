package ca.turnip.turnip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimerTask;

public class RoomListActivity extends AppCompatActivity {

    private static final String TAG = RoomListActivity.class.getSimpleName();

    // Constants

    private static final CharSequence findingToastText = "Finding nearby hosts";
    private static final int findHostsLength = 30 * 1000;

    // Background service

    private BackgroundService backgroundService;

    // Search related progress bars and indicators

    private int screenHeight;
    private LinearLayout noHostsFoundContainer;
    private SwipeRefreshLayout roomListSwipeRefresh;
    private Toast findingToast;

    // Find hosts timer

    private Handler findHostsTimerHandler = new Handler();
    private Runnable findHostsTimer = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (rooms.size() == 0) {
                        noHostsFoundContainer.setVisibility(View.VISIBLE);
                        roomListSwipeRefresh.setRefreshing(false);
                    }
                }
            });
            if (backgroundService.isDiscovering()) {
                backgroundService.stopDiscovery();
            }
        }
    };

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
            roomListSwipeRefresh.setRefreshing(false);
            findingToast.cancel();
            findHostsTimerHandler.removeCallbacks(findHostsTimer);

            rooms.add(host);
            adapter.notifyItemInserted(rooms.size() - 1);
        }

        @Override
        public void onRoomLost(String endpointId) {
            Iterator<BackgroundService.Endpoint> it = rooms.iterator();

            if (rooms.size() == 0) {
                roomListSwipeRefresh.setRefreshing(true);
                findHostsTimerHandler.postDelayed(findHostsTimer, findHostsLength);
            }
            Log.d(TAG, "room lost: " + endpointId);
            int i = 0;
            while (it.hasNext()) {
                BackgroundService.Endpoint current = it.next();
                Log.d(TAG, current.toString() + " " + endpointId);

                if (current.getId().equals(endpointId)) {
                    it.remove();
                    adapter.notifyItemRemoved(i);
                }
                else {
                    i++;
                }
            }
        }
    };

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            backgroundService = ((BackgroundService.LocalBinder)service).getService();
            backgroundService.subscribeRoomListListener(roomListListener);
            backgroundService.startDiscovery();
            findHostsTimerHandler.postDelayed(findHostsTimer, findHostsLength);
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

        createFindingHostsToast();
        noHostsFoundContainer = findViewById(R.id.noHostsFoundContainer);
        roomListSwipeRefresh = findViewById(R.id.roomListSwipeRefresh);
        recyclerView =  findViewById(R.id.roomRecyclerView);
        layoutManager = new LinearLayoutManager(this);
        adapter = new RoomListAdapter(rooms);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        setSwipeProgressEndTarget();

        roomListSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                roomListSwipeRefresh.setRefreshing(true);
                refreshHosts();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSwipeProgressEndTarget();
    }

    @Override
    protected void onResume() {
        super.onResume();
        createFindingHostsToast();
        refreshHosts();
        roomListSwipeRefresh.setRefreshing(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundService.unsubscribeRoomListListener();
        backgroundService.stopDiscovery();
        findingToast.cancel();
        unbindService(connection);
    }

    private void setSwipeProgressEndTarget() {
        screenHeight = getScreenHeight();
        roomListSwipeRefresh.setProgressViewEndTarget(false, (screenHeight / 2) - 100);
    }

    private int getScreenHeight() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y;
    }

    private void createFindingHostsToast() {
        findingToast = Toast.makeText(this, findingToastText, Toast.LENGTH_LONG);
        findingToast.show();
    }

    public void refreshHosts() {
        if (backgroundService != null) {
            rooms.clear();
            adapter.notifyDataSetChanged();
            noHostsFoundContainer.setVisibility(View.GONE);
            if (backgroundService.isDiscovering()) {
                backgroundService.stopDiscovery();
            }
            backgroundService.startDiscovery();
            createFindingHostsToast();
            findHostsTimerHandler.removeCallbacks(findHostsTimer);
            findHostsTimerHandler.postDelayed(findHostsTimer, findHostsLength);
        }
    }

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }
}
