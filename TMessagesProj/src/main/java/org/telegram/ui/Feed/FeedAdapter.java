package org.telegram.ui.Feed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedViewHolder> {

    static final int VIEW_TYPE_POST = 0;
    static final int VIEW_TYPE_SEPARATOR = 1;

    private final Context context;
    private final int currentAccount;
    private final Theme.ResourcesProvider resourceProvider;
    private List<FeedController.FeedItem> feedItems = new ArrayList<>();
    private List<Object> displayItems;

    private FeedPostCell.Callback cellCallback;

    public FeedAdapter(Context context, int account, Theme.ResourcesProvider resourceProvider) {
        this.context = context;
        this.currentAccount = account;
        this.resourceProvider = resourceProvider;
        this.displayItems = FeedController.getInstance(account).getDisplayItems();
    }

    public void setCellCallback(FeedPostCell.Callback cb) {
        cellCallback = cb;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<FeedController.FeedItem> items) {
        this.feedItems = new ArrayList<>(items);
        rebuildDisplay();
        notifyDataSetChanged();
    }

    public void syncFeedItems(List<FeedController.FeedItem> items) {
        this.feedItems = new ArrayList<>(items);
    }

    public List<FeedController.FeedItem> getItems() {
        return feedItems;
    }

    public void updateItem(int pos) {
        if (pos >= 0 && pos < displayItems.size()) notifyItemChanged(pos);
    }

    private void rebuildDisplay() {
        displayItems = FeedController.getInstance(currentAccount).getDisplayItems();
    }

    public Object getDisplayItem(int position) {
        if (position >= 0 && position < displayItems.size()) {
            return displayItems.get(position);
        }
        return null;
    }

    public int findItemPosition(FeedController.FeedItem item) {
        for (int i = 0; i < displayItems.size(); i++) {
            if (displayItems.get(i) == item) return i;
        }
        return -1;
    }

    public int findPositionByUid(String uid) {
        if (uid == null) return -1;
        for (int i = 0; i < displayItems.size(); i++) {
            Object item = displayItems.get(i);
            if (item instanceof FeedController.FeedItem) {
                if (uid.equals(((FeedController.FeedItem) item).getUniqueId())) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayItems.get(position);
        if (item instanceof FeedController.FeedSeparator) {
            return VIEW_TYPE_SEPARATOR;
        }
        return VIEW_TYPE_POST;
    }

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return FeedViewHolder.create(context, currentAccount, resourceProvider,
                cellCallback, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        if (position < 0 || position >= displayItems.size()) return;
        holder.bind(displayItems.get(position));
    }
}