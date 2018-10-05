package ca.passtheaux.passtheaux;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;

public class RoomList extends AppCompatActivity {

    private static final String TAG = RoomList.class.getSimpleName();

    private ConnectionService boundService;

    // RecyclerView

    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<ConnectionService.Endpoint> rooms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_list);
        bindConnectionService();

        recyclerView = (RecyclerView) findViewById(R.id.roomRecyclerView);
        layoutManager = new LinearLayoutManager(this);
        adapter = new RoomListAdapter(rooms);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                layoutManager.getLayoutDirection());
        dividerItemDecoration.setDrawable(ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.room_list_divider));
        recyclerView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    protected void onDestroy() {
        if (boundService != null) {
            boundService.unsubscribeRoomFound();
        }
        unbindConnectionService();
        super.onDestroy();
    }

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            boundService = ((ConnectionService.LocalBinder)service).getService();

            boundService.subscribeRoomFound(new ConnectionService.RoomFoundListener() {
                @Override
                public void onRoomFound(ConnectionService.Endpoint endpoint) {
                    rooms.add(endpoint);
                    adapter.notifyItemInserted(rooms.size() - 1);
                }
            });

            boundService.subscribeRoomLost(new ConnectionService.RoomLostListener() {
                @Override
                public void onRoomLost(String endpointId) {
                    Iterator<ConnectionService.Endpoint> it = rooms.iterator();
                    int pos = 0;

                    while (it.hasNext()) {
                        ConnectionService.Endpoint current = it.next();
                        Log.i(TAG, current.toString() + " " + endpointId);

                        if (current.getId().equals(endpointId)) {
                            it.remove();
                            adapter.notifyItemRemoved(pos);
                        }
                        pos++;
                    }
                }
            });

            boundService.startDiscovery();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundService = null;
        }
    };

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent,
                connection,
                Context.BIND_AUTO_CREATE);
    }

    private void unbindConnectionService() {
        this.unbindService(connection);
    }
}
