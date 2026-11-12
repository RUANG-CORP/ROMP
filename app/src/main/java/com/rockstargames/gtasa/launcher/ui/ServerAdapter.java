package com.rockstargames.gtasa.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.ServerItem;
import com.rockstargames.gtasa.launcher.data.ThemeManager;
import com.rockstargames.gtasa.launcher.net.SampQueryClient;
import com.rockstargames.gtasa.launcher.net.ServerInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Universal SA-MP Server Card Adapter.
 * Supports Favorites, Official, and Hosted (open.mp masterlist) servers with
 * asynchronous UDP querying, multi-criteria filtering, and sorting.
 */
public class ServerAdapter extends BaseAdapter {

    public static final int SORT_DEFAULT = 0;
    public static final int SORT_PLAYERS = 1;
    public static final int SORT_PING = 2;
    public static final int SORT_NAME = 3;

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
    private final Set<String> mPendingQueries = new HashSet<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mNotifyScheduled = false;
    private final Runnable mNotifyRunnable = () -> {
        mNotifyScheduled = false;
        notifyDataSetChanged();
    };
    private int mAccentColor;

    private String mQuery = "";
    private boolean mOnlineOnly = false;
    private boolean mHideFull = false;
    private boolean mNoPassword = false;
    private int mSortMode = SORT_DEFAULT;

    public ServerAdapter(Context context, List<ServerItem> servers, boolean isFavorites, OnServerActionListener listener) {
        this.mContext = context;
        this.mAllServers = new ArrayList<>(servers);
        this.mFilteredServers = new ArrayList<>(servers);
        this.mIsFavorites = isFavorites;
        this.mListener = listener;
        this.mAccentColor = new ThemeManager(context).getAccentColor();
    }

    public void refreshTheme() {
        mAccentColor = new ThemeManager(mContext).getAccentColor();
        notifyDataSetChanged();
    }

    public void filter(String query) {
        mQuery = query != null ? query.trim().toLowerCase() : "";
        applyFiltersAndSort();
    }

    public void setFilterOptions(String query, boolean onlineOnly, boolean hideFull, boolean noPassword, int sortMode) {
        mQuery = query != null ? query.trim().toLowerCase() : "";
        mOnlineOnly = onlineOnly;
        mHideFull = hideFull;
        mNoPassword = noPassword;
        mSortMode = sortMode;
        applyFiltersAndSort();
    }

    private void applyFiltersAndSort() {
        mFilteredServers.clear();
        for (ServerItem item : mAllServers) {
            ServerInfo info = mInfoCache.get(item.getAddress());
            String name = item.getName() != null ? item.getName().toLowerCase() : "";
            String addr = item.getAddress().toLowerCase();
            String lang = item.getLanguage().toLowerCase();
            String gm = !item.getGamemode().isEmpty() ? item.getGamemode().toLowerCase()
                    : (info != null && info.getGamemode() != null ? info.getGamemode().toLowerCase() : "");
            String hostname = (info != null && info.getHostname() != null) ? info.getHostname().toLowerCase() : "";

            if (!mQuery.isEmpty()) {
                if (!name.contains(mQuery) && !addr.contains(mQuery)
                        && !hostname.contains(mQuery) && !gm.contains(mQuery) && !lang.contains(mQuery)) {
                    continue;
                }
            }

            boolean online = info != null ? info.isOnline() : (item.getPlayers() >= 0);
            int curPlayers = info != null ? info.getPlayers() : item.getPlayers();
            int maxPlayers = info != null ? info.getMaxPlayers() : item.getMaxPlayers();
            boolean pwd = info != null ? info.isPassworded() : item.isPassworded();

            if (mOnlineOnly && !online) continue;
            if (mHideFull && maxPlayers > 0 && curPlayers >= maxPlayers) continue;
            if (mNoPassword && pwd) continue;

            mFilteredServers.add(item);
        }

        if (mSortMode == SORT_PLAYERS) {
            Collections.sort(mFilteredServers, (a, b) -> Integer.compare(effectivePlayers(b), effectivePlayers(a)));
        } else if (mSortMode == SORT_PING) {
            Collections.sort(mFilteredServers, (a, b) -> Integer.compare(effectivePing(a), effectivePing(b)));
        } else if (mSortMode == SORT_NAME) {
            Collections.sort(mFilteredServers, (a, b) -> safeName(a).compareToIgnoreCase(safeName(b)));
        }

        notifyDataSetChanged();
    }

    private int effectivePlayers(ServerItem s) {
        ServerInfo info = mInfoCache.get(s.getAddress());
        return info != null && info.isOnline() ? info.getPlayers() : s.getPlayers();
    }

    private int effectivePing(ServerItem s) {
        ServerInfo info = mInfoCache.get(s.getAddress());
        return info != null && info.isOnline() ? info.getPingMs() : 9999;
    }

    private String safeName(ServerItem s) {
        return s.getName() != null ? s.getName() : s.getAddress();
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
            holder.btnDelete.setOnClickListener(v -> {
                if (mListener != null && holder.boundItem != null) mListener.onDelete(holder.boundItem);
            });
            holder.btnConnect.setOnClickListener(v -> {
                if (mListener != null && holder.boundItem != null) mListener.onConnect(holder.boundItem);
            });
            convertView.setOnClickListener(v -> {
                if (mListener != null && holder.boundItem != null) mListener.onInspect(holder.boundItem);
            });
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ServerItem item = getItem(position);
        holder.boundItem = item;
        final String addressKey = item.getAddress();

        holder.tvName.setText(item.getName());
        holder.tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                item.isPassworded() ? R.drawable.ic_romp_lock : 0, 0, 0, 0);
        String subMeta = addressKey;
        if (!item.getGamemode().isEmpty()) subMeta += " · " + item.getGamemode();
        if (!item.getLanguage().isEmpty()) subMeta += " · " + item.getLanguage();
        holder.tvAddress.setText(subMeta);

        if (holder.styledAccent != mAccentColor) {
            holder.styledAccent = mAccentColor;
            ThemeManager.stylePrimaryCta(mContext, holder.btnConnect, mAccentColor);
        }

        holder.btnDelete.setVisibility(mIsFavorites ? View.VISIBLE : View.GONE);

        if (mInfoCache.containsKey(addressKey)) {
            bindServerInfo(holder, mInfoCache.get(addressKey));
        } else if ("HOSTED".equals(item.getSource()) && item.getPlayers() >= 0) {
            holder.dotStatus.setBackgroundResource(R.drawable.dot_online);
            holder.tvPing.setText("HOSTED");
            holder.tvPing.setTextColor(Color.parseColor("#38BDF8"));
            holder.tvPlayers.setText(item.getPlayers() + " / " + item.getMaxPlayers());
            queryLazyOnce(item, addressKey);
        } else {
            holder.dotStatus.setBackgroundResource(R.drawable.dot_pending);
            holder.tvPing.setText("...");
            holder.tvPing.setTextColor(Color.parseColor("#CBD5E1"));
            holder.tvPlayers.setText("- / -");
            queryLazyOnce(item, addressKey);
        }

        return convertView;
    }

    private void queryLazyOnce(ServerItem item, String addressKey) {
        if (mPendingQueries.contains(addressKey)) return;
        mPendingQueries.add(addressKey);
        SampQueryClient.queryServer(item.getIp(), item.getPort(), info -> {
            mPendingQueries.remove(addressKey);
            mInfoCache.put(addressKey, info);
            if (info != null && info.isOnline()) {
                item.setPassworded(info.isPassworded());
                item.setPlayers(info.getPlayers());
                item.setMaxPlayers(info.getMaxPlayers());
                if (item.getGamemode().isEmpty()) item.setGamemode(info.getGamemode());
            }
            scheduleNotifyDataSetChanged();
        });
    }

    private void scheduleNotifyDataSetChanged() {
        if (!mNotifyScheduled) {
            mNotifyScheduled = true;
            mMainHandler.postDelayed(mNotifyRunnable, 120);
        }
    }

    private void bindServerInfo(ViewHolder holder, ServerInfo info) {
        if (info != null && info.isOnline()) {
            holder.dotStatus.setBackgroundResource(R.drawable.dot_online);
            String rawHostname = info.getHostname();
            String cleanName = rawHostname != null ? rawHostname.replaceAll("\\{[0-9a-fA-F]{6}\\}", "").trim() : "";
            holder.tvName.setText(!cleanName.isEmpty() ? cleanName : rawHostname);
            holder.tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    info.isPassworded() ? R.drawable.ic_romp_lock : 0, 0, 0, 0);
            holder.tvPing.setText(info.getPingMs() + " ms");
            holder.tvPing.setTextColor(Color.parseColor("#22C55E"));
            holder.tvPlayers.setText(info.getPlayers() + " / " + info.getMaxPlayers());
        } else {
            holder.dotStatus.setBackgroundResource(R.drawable.dot_offline);
            holder.tvPing.setText("Offline");
            holder.tvPing.setTextColor(Color.parseColor("#F87171"));
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
        ServerItem boundItem;
        int styledAccent;
    }
}
