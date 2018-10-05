package ca.passtheaux.passtheaux;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class RoomListAdapter extends RecyclerView.Adapter<RoomListAdapter.RoomListViewHolder> {

    private static final String TAG = ConnectionService.class.getSimpleName();

    private ArrayList<ConnectionService.Endpoint> rooms;

    public class RoomListViewHolder extends RecyclerView.ViewHolder {

        private final Context context;
        public TextView textView;
        RoomListViewHolder self = this;

        public RoomListViewHolder(View v) {
            super(v);
            context = v.getContext();
            textView = v.findViewById(R.id.textView);

            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                final int pos = self.getLayoutPosition();
                Intent roomIntent = new Intent(context, Room.class);
                roomIntent.putExtra("roomName", textView.getText().toString());
                roomIntent.putExtra("isHost", false);
                roomIntent.putExtra("endpointId", rooms.get(pos).getId());
                context.startActivity(roomIntent);
                }
            });
        }
    }

    public RoomListAdapter(ArrayList<ConnectionService.Endpoint> rooms) {
        this.rooms = rooms;
    }

    @NonNull
    @Override
    public RoomListAdapter.RoomListViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                 int viewType) {
        View v = LayoutInflater.from(
                parent.getContext()).inflate(R.layout.room_list_row,
                parent,
                false
        );

        RoomListViewHolder vh = new RoomListViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull RoomListViewHolder roomListViewHolder,
                                 int position) {
        roomListViewHolder.textView.setText(rooms.get(position).getName());
    }


    @Override
    public int getItemCount() {
        return rooms.size();
    }
}
