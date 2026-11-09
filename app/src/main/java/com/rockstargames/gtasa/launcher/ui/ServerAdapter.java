package com.rockstargames.gtasa.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.ServerItem;
import com.rockstargames.gtasa.launcher.net.SampQueryClient;
import com.rockstargames.gtasa.launcher.net.ServerInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal SA-MP Server Card Adapter.
 * Provides asynchronous live server querying and binding to item_server_card.xml.
 */
public class ServerAdapter extends BaseAdapter {

    public interface OnServerActionListener {
        void onConnect(ServerItem server);
        void onDelete(ServerItem server);
        void onInspect(ServerItem server);
    }

    private final Context mContext;
    private final List<ServerItem> mAllServers;
    private final List<ServerItem> mFilteredServers;
    private final boolean mIsFavorites;
    private final OnServerActionListener mListener;
    private final Map<String, ServerInfo> mInfoCache = new HashMap<>();

    public ServerAdapter(Context context, List<ServerItem> servers, boolean isFavorites, OnServerActionListener listener) {
        this.mContext = context;
        this.mAllServers = new java.util.ArrayList<>(servers);
        this.mFilteredServers = new java.util.ArrayList<>(servers);
        this.mIsFavorites = isFavorites;
        this.mListener = listener;
    }

    public void filter(String query) {
        mFilteredServers.clear();
        if (query == null || query.trim().isEmpty()) {
            mFilteredServers.addAll(mAllServers);
        } else {
            String lower = query.trim().toLowerCase();
            for (ServerItem item : mAllServers) {
                String name = item.getName() != null ? item.getName().toLowerCase() : "";
                String addr = item.getAddress().toLowerCase();
                ServerInfo info = mInfoCache.get(item.getAddress());
                String hostname = (info != null && info.getHostname() != null) ? info.getHostname().toLowerCase() : "";
                String gamemode = (info != null && info.getGamemode() != null) ? info.getGamemode().toLowerCase() : "";

                if (name.contains(lower) || addr.contains(lower) || hostname.contains(lower) || gamemode.contains(lower)) {
                    mFilteredServers.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mFilteredServers.size();
    }

    @Override
    public ServerItem getItem(int position) {
        return mFilteredServers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_server_card, parent, false);
            holder = new ViewHolder();
            holder.dotStatus = convertView.findViewById(R.id.view_status_dot);
            holder.tvName = convertView.findViewById(R.id.tv_server_name);
            holder.tvPing = convertView.findViewById(R.id.tv_server_ping);
            holder.tvAddress = convertView.findViewById(R.id.tv_server_address);
            holder.tvPlayers = convertView.findViewById(R.id.tv_server_players);
            holder.btnDelete = convertView.findViewById(R.id.btn_delete_server);
            holder.btnConnect = convertView.findViewById(R.id.btn_connect_server);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ServerItem item = getItem(position);
        final String addressKey = item.getAddress();

        holder.tvAddress.setText(addressKey);
        holder.tvName.setText(item.getName());

        // Configure delete button visibility (only for Favorites)
        if (mIsFavorites) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (mListener != null) mListener.onDelete(item);
            });
        } else {
            holder.btnDelete.setVisibility(View.GONE);
        }

        holder.btnConnect.setOnClickListener(v -> {
            if (mListener != null) mListener.onConnect(item);
        });

        convertView.setOnClickListener(v -> {
            if (mListener != null) mListener.onInspect(item);
        });

        // Check if we have cached query info
        if (mInfoCache.containsKey(addressKey)) {
            bindServerInfo(holder, mInfoCache.get(addressKey));
        } else {
            // Default pending state
            holder.dotStatus.setBackgroundColor(Color.parseColor("#94A3B8"));
            holder.tvPing.setText("...");
            holder.tvPlayers.setText("- / -");

            // Query asynchronously
            SampQueryClient.queryServer(item.getIp(), item.getPort(), info -> {
                mInfoCache.put(addressKey, info);
                notifyDataSetChanged();
            });
        }

        return convertView;
    }

    private void bindServerInfo(ViewHolder holder, ServerInfo info) {
        if (info != null && info.isOnline()) {
            holder.dotStatus.setBackgroundResource(R.drawable.dot_online);
            String rawHostname = info.getHostname();
            String cleanName = rawHostname != null ? rawHostname.replaceAll("\\{[0-9a-fA-F]{6}\\}", "").trim() : "";
            holder.tvName.setText(!cleanName.isEmpty() ? cleanName : rawHostname);
            holder.tvPing.setText(info.getPingMs() + "ms");
            holder.tvPlayers.setText(info.getPlayers() + " / " + info.getMaxPlayers());
        } else {
            holder.dotStatus.setBackgroundResource(R.drawable.dot_offline);
            holder.tvPing.setText("Offline");
            holder.tvPlayers.setText("0 / 0");
        }
    }

    private static class ViewHolder {
        View dotStatus;
        TextView tvName;
        TextView tvPing;
        TextView tvAddress;
        TextView tvPlayers;
        Button btnDelete;
        Button btnConnect;
    }
}

