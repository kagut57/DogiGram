/*
 * DogiGram: Data Center status screen. Pings every Telegram data center and shows
 * the round-trip latency, the current connection state and the data center in use.
 */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.net.InetSocketAddress;
import java.net.Socket;

public class DatacenterStatusActivity extends BaseFragment {

    private static final int[] DC_IDS = {1, 2, 3, 4, 5};
    private static final String[] DC_NAMES = {
            "Pluto (Miami)",
            "Venus (Amsterdam)",
            "Aurora (Miami)",
            "Vesta (Amsterdam)",
            "Flora (Singapore)"
    };
    private static final String[] DC_IPS = {
            "149.154.175.50",
            "149.154.167.51",
            "149.154.175.100",
            "149.154.167.91",
            "91.108.56.130"
    };
    private static final int DC_PORT = 443;
    private static final int PING_TIMEOUT_MS = 5000;

    // -1 = pinging, -2 = unavailable, >= 0 = latency in ms
    private final int[] pings = new int[DC_IDS.length];

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private volatile boolean destroyed;

    private int rowCount;
    private int stateRow;
    private int sectionRow;
    private int dcHeaderRow;
    private int dcStartRow;
    private int dcEndRow;
    private int infoRow;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        buildRows();
        for (int i = 0; i < pings.length; i++) {
            pings[i] = -1;
        }
        return true;
    }

    private void buildRows() {
        rowCount = 0;
        stateRow = rowCount++;
        sectionRow = rowCount++;
        dcHeaderRow = rowCount++;
        dcStartRow = rowCount;
        rowCount += DC_IDS.length;
        dcEndRow = rowCount;
        infoRow = rowCount++;
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.DogiServerStatus));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= dcStartRow && position < dcEndRow) {
                startPings();
            }
        });

        startPings();
        return fragmentView;
    }

    private void startPings() {
        for (int i = 0; i < DC_IDS.length; i++) {
            pings[i] = -1;
            final int index = i;
            final String ip = DC_IPS[i];
            Thread thread = new Thread(() -> {
                int result;
                Socket socket = null;
                try {
                    long start = System.currentTimeMillis();
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, DC_PORT), PING_TIMEOUT_MS);
                    result = (int) (System.currentTimeMillis() - start);
                } catch (Exception e) {
                    result = -2;
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception ignore) {}
                    }
                }
                final int finalResult = result;
                AndroidUtilities.runOnUIThread(() -> {
                    if (destroyed) {
                        return;
                    }
                    pings[index] = finalResult;
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(dcStartRow + index);
                    }
                });
            });
            thread.setDaemon(true);
            thread.start();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private String connectionStateText() {
        int state = getConnectionsManager().getConnectionState();
        switch (state) {
            case ConnectionsManager.ConnectionStateWaitingForNetwork:
                return LocaleController.getString(R.string.WaitingForNetwork);
            case ConnectionsManager.ConnectionStateConnecting:
                return LocaleController.getString(R.string.Connecting);
            case ConnectionsManager.ConnectionStateUpdating:
                return LocaleController.getString(R.string.Updating);
            case ConnectionsManager.ConnectionStateConnectingToProxy:
                return LocaleController.getString(R.string.ConnectingToProxy);
            case ConnectionsManager.ConnectionStateConnected:
            default:
                return LocaleController.getString(R.string.Connected);
        }
    }

    private String pingText(int index) {
        int value = pings[index];
        if (value == -1) {
            return LocaleController.getString(R.string.DogiPinging);
        } else if (value == -2) {
            return LocaleController.getString(R.string.DogiPingUnavailable);
        }
        return value + " " + LocaleController.getString(R.string.DogiPingMs);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= dcStartRow && position < dcEndRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 3:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 1:
                default:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(LocaleController.getString(R.string.DogiDataCenters));
                    break;
                }
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == stateRow) {
                        cell.setTextAndValue(LocaleController.getString(R.string.DogiConnectionState), connectionStateText(), true);
                    } else if (position >= dcStartRow && position < dcEndRow) {
                        int index = position - dcStartRow;
                        int currentDc = getConnectionsManager().getCurrentDatacenterId();
                        String title = "DC" + DC_IDS[index] + " — " + DC_NAMES[index];
                        if (DC_IDS[index] == currentDc) {
                            title = title + " ✓";
                        }
                        cell.setTextAndValue(title, pingText(index), index != DC_IDS.length - 1);
                    }
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(LocaleController.getString(R.string.DogiServerStatusInfo));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == dcHeaderRow) {
                return 0;
            } else if (position == sectionRow) {
                return 2;
            } else if (position == infoRow) {
                return 3;
            }
            return 1;
        }
    }
}
