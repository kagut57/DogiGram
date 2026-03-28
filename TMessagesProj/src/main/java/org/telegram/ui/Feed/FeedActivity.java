package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;

import java.util.List;

public class FeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private static final int MENU_SETTINGS = 1;

    private final Runnable onNewPostRunnable = this::onNewPostsReceived;

    RecyclerListView listView;
    FeedAdapter adapter;
    LinearLayoutManager layoutManager;
    SwipeRefreshLayout swipeRefreshLayout;
    TextView emptyView;
    View loadingFooter;

    FeedController feedController;
    boolean hasMainTabs;
    boolean isLoadingMore;

    private static Parcelable savedScrollState;
    private static boolean hasScrollState;
    private Runnable markReadRunnable;

    FeedActionHandler actionHandler;
    FeedShareHelper shareHelper;
    FeedReactionHandler reactionHandler;
    FeedReportHelper reportHelper;

    int getAccount() {
        return currentAccount;
    }

    Theme.ResourcesProvider getResProvider() {
        return resourceProvider;
    }

    void showBulletinTop(Bulletin b) {
        b.show(true);
        b.getLayout().post(() -> {
            View pv = (View) b.getLayout().getParent();
            if (pv != null && pv.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pv.getLayoutParams();
                lp.topMargin = ActionBar.getCurrentActionBarHeight()
                        + AndroidUtilities.statusBarHeight + dp(8);
                pv.setLayoutParams(lp);
            }
        });
    }

   @Override
    public boolean onFragmentCreate() {
        feedController = FeedController.getInstance(currentAccount);
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);

        actionHandler = new FeedActionHandler(this);
        shareHelper = new FeedShareHelper(this);
        reactionHandler = new FeedReactionHandler(this);
        reportHelper = new FeedReportHelper(this);

        feedController.startObserving();
        feedController.addNewPostListener(onNewPostRunnable);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        feedController.removeNewPostListener(onNewPostRunnable);
        feedController.stopObserving();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (feedController.hasCachedFeed()) {
            adapter.syncFeedItems(feedController.getCachedFeed());
            if (hasScrollState && savedScrollState != null && layoutManager != null) {
                final Parcelable state = savedScrollState;
                listView.post(() -> {
                    if (layoutManager != null) layoutManager.onRestoreInstanceState(state);
                });
            }
            updateEmpty();
        } else {
            loadFeed(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        reactionHandler.flushPending();
        cancelScheduledMark();
        markVisibleAsRead();

        if (layoutManager != null) {
            savedScrollState = layoutManager.onSaveInstanceState();
            hasScrollState = true;
        }
        if (adapter != null) {
            adapter.syncFeedItems(feedController.getCachedFeed());
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("FeedTitle", R.string.FeedTitle));
        actionBar.setBackButtonDrawable(null);
        actionBar.setCastShadows(false);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        actionBar.setAddToContainer(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == MENU_SETTINGS) {
                    saveScroll();
                    presentFragment(new FeedSettingsActivity());
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings);

        FrameLayout rootView = new FrameLayout(context);
        rootView.setClipChildren(false);
        rootView.setClipToPadding(false);
        rootView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        int topPad = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
        int bottomPad = hasMainTabs
                ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN + 96)
                : dp(96);

        adapter = new FeedAdapter(context, currentAccount, resourceProvider);
        adapter.setCellCallback(createCellCallback());

        layoutManager = new LinearLayoutManager(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(adapter);
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(true);
        listView.setPadding(0, topPad, 0, bottomPad);
        listView.setClipChildren(false);

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                scheduleMarkAsRead();
                checkLoadMore();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    cancelScheduledMark();
                    markVisibleAsRead();
                    checkLoadMore();
                }
            }
        });

        buildLoadingFooter(context, rootView);

        swipeRefreshLayout = new SwipeRefreshLayout(context);
        swipeRefreshLayout.setClipChildren(false);
        swipeRefreshLayout.setClipToPadding(false);
        swipeRefreshLayout.setProgressViewOffset(false, topPad, topPad + dp(64));
        swipeRefreshLayout.setColorSchemeColors(
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        swipeRefreshLayout.setOnRefreshListener(() -> loadFeed(true));
        swipeRefreshLayout.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        rootView.addView(swipeRefreshLayout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText(LocaleController.getString("FeedEmpty", R.string.FeedEmpty));
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        emptyView.setTextSize(16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setPadding(dp(40), 0, dp(40), 0);
        rootView.addView(emptyView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FeedPlayerBar playerBar = new FeedPlayerBar(context, this, listView, resourceProvider);
        FrameLayout.LayoutParams playerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(38));
        playerLp.topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
        playerLp.gravity = Gravity.TOP;
        rootView.addView(playerBar, playerLp);

        fragmentView = rootView;
        return fragmentView;
    }

    private void buildLoadingFooter(Context context, FrameLayout rootView) {
        loadingFooter = new FrameLayout(context) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
            }
        };

        org.telegram.ui.Components.RadialProgressView progress =
                new org.telegram.ui.Components.RadialProgressView(context);
        progress.setSize(dp(28));
        progress.setProgressColor(
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        ((FrameLayout) loadingFooter).addView(progress,
                LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        loadingFooter.setVisibility(View.GONE);

        int footerBottom = hasMainTabs
                ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN + 24)
                : dp(24);
        rootView.addView(loadingFooter, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 60,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                0, 0, 0, footerBottom / AndroidUtilities.density));
    }

    private FeedPostCell.Callback createCellCallback() {
        return new FeedPostCell.Callback() {
            @Override
            public void onHeaderClick(FeedController.FeedItem item) {
                saveScroll();
                openChannel(item);
            }

            @Override
            public void onMediaClick(FeedController.FeedItem item, int idx) {
                actionHandler.openMedia(item, idx);
            }

            @Override
            public void onMenuClick(View anchor, FeedController.FeedItem item) {
                actionHandler.showMenu(anchor, item);
            }

            @Override
            public void onCommentsClick(FeedController.FeedItem item) {
                saveScroll();
                actionHandler.openComments(item);
            }

            @Override
            public void onShareClick(FeedController.FeedItem item) {
                shareHelper.sharePost(item);
            }

            @Override
            public void onForwardClick(long channelId, int messageId) {
                saveScroll();
                openChat(channelId, messageId);
            }

            @Override
            public void onReplyClick(long channelId, int messageId) {
                saveScroll();
                openChat(channelId, messageId);
            }

            @Override
            public void onInlineButtonClick(FeedController.FeedItem item,
                                            TLRPC.KeyboardButton button) {
                saveScroll();
                openChannel(item);
            }

            @Override
            public void onReactionToggle(FeedController.FeedItem item,
                                         TLRPC.Reaction reaction) {
                reactionHandler.sendReaction(item, reaction);
            }

            @Override
            public void onPaidReactionTap(FeedController.FeedItem item) {
                reactionHandler.handlePaidReaction(item, 1);
            }

            @Override
            public void onPaidReactionLongPress(FeedController.FeedItem item) {
                reactionHandler.showStarAmountPicker(item);
            }

            @Override
            public void onDoubleTap(FeedController.FeedItem item) {
                actionHandler.handleDoubleTap(item);
            }

            @Override
            public void onBookmarkClick(FeedController.FeedItem item) {
                actionHandler.toggleBookmark(item);
            }

            @Override
            public void onLinkClick(String url) {
                actionHandler.onLinkClick(url);
            }

            @Override
            public void onLinkLongPress(String url, View cell, ClickableSpan span) {
                actionHandler.showLinkOptions(url, cell, span);
            }

            @Override
            public void onPostLongPress(View cell) {
                actionHandler.showPostScrim(cell);
            }

            @Override
            public void onSubscribeClick(FeedController.FeedItem item) {
                actionHandler.subscribeFromRecommendation(item);
            }

            @Override
            public void onDismissRecommendation(FeedController.FeedItem item) {
                actionHandler.dismissRecommendedPost(item);
            }

            @Override
            public void onDateEntityClick(TLRPC.TL_messageEntityFormattedDate entity,
                                          View anchor) {
                actionHandler.onDateEntityClick(entity, anchor);
            }
        };
    }

    private void onNewPostsReceived() {
        if (adapter == null) return;

        List<FeedController.FeedItem> merged = feedController.flushMergedItems();
        for (FeedController.FeedItem item : merged) {
            int pos = adapter.findItemPosition(item);
            if (pos >= 0) adapter.notifyItemChanged(pos);
        }

        List<FeedController.FeedItem> pending = feedController.flushPendingNewItems();
        if (!pending.isEmpty()) {
            int oldSize = adapter.getItemCount();
            int addedCount = feedController.appendItemsToDisplay(pending);
            if (addedCount > 0) adapter.notifyItemRangeInserted(oldSize, addedCount);
            adapter.syncFeedItems(feedController.getCachedFeed());
        }
        updateEmpty();
    }

    void loadFeed(boolean force) {
        swipeRefreshLayout.setRefreshing(true);
        emptyView.setVisibility(View.GONE);

        if (force) feedController.resetLoadMore();

        feedController.loadFeed(force, (items, hasMore) -> {
            if (hasMore) {
                adapter.setItems(items);
            } else {
                String visibleUid = null;
                int visibleOffset = 0;
                if (layoutManager != null) {
                    int firstPos = layoutManager.findFirstVisibleItemPosition();
                    if (firstPos >= 0) {
                        Object d = adapter.getDisplayItem(firstPos);
                        if (d instanceof FeedController.FeedItem)
                            visibleUid = ((FeedController.FeedItem) d).getUniqueId();
                        View v = layoutManager.findViewByPosition(firstPos);
                        if (v != null) visibleOffset = v.getTop();
                    }
                }
                adapter.setItems(items);
                if (visibleUid != null) {
                    int newPos = adapter.findPositionByUid(visibleUid);
                    if (newPos >= 0 && layoutManager != null)
                        layoutManager.scrollToPositionWithOffset(newPos, visibleOffset);
                }
                swipeRefreshLayout.setRefreshing(false);
            }
            updateEmpty();
        });
    }

    void updateEmpty() {
        emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void checkLoadMore() {
        if (isLoadingMore || layoutManager == null || adapter == null) return;
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        int total = adapter.getItemCount();
        if (lastVisible >= total - 3 && total > 0) {
            if (feedController.hasMore()) loadMorePosts();
            else if (feedController.getRecommendationEngine().canLoadMore())
                loadMoreRecommendations();
        }
    }

    private void loadMorePosts() {
        if (isLoadingMore || !feedController.hasMore()) return;
        isLoadingMore = true;
        feedController.loadMore((items, hasMore) -> {
            isLoadingMore = false;
            updateEmpty();
        });
    }

    private void loadMoreRecommendations() {
        if (isLoadingMore) return;
        isLoadingMore = true;
        if (loadingFooter != null) loadingFooter.setVisibility(View.VISIBLE);

        feedController.loadMoreRecommendations((items, hasMore) -> {
            isLoadingMore = false;
            if (loadingFooter != null) loadingFooter.setVisibility(View.GONE);
            int oldSize = adapter.getItemCount();
            int added = feedController.appendNewRecommendationsToDisplay();
            if (added > 0) adapter.notifyItemRangeInserted(oldSize, added);
        });
    }

    private void scheduleMarkAsRead() {
        if (markReadRunnable != null) return;
        markReadRunnable = () -> {
            markVisibleAsRead();
            markReadRunnable = null;
        };
        AndroidUtilities.runOnUIThread(markReadRunnable, 1500);
    }

    private void cancelScheduledMark() {
        if (markReadRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(markReadRunnable);
            markReadRunnable = null;
        }
    }

    private void markVisibleAsRead() {
        if (layoutManager == null || adapter == null) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        for (int i = first; i <= last; i++) {
            Object obj = adapter.getDisplayItem(i);
            if (obj instanceof FeedController.FeedItem) {
                FeedController.FeedItem item = (FeedController.FeedItem) obj;
                if (!item.isRead && !item.isRecommendation) feedController.markAsRead(item);
            }
        }
    }

    void openChannel(FeedController.FeedItem item) {
        Bundle args = new Bundle();
        args.putLong("chat_id", -item.channelId);
        args.putInt("message_id", item.getMessageId());
        presentFragment(new ChatActivity(args));
    }

    private void openChat(long channelId, int messageId) {
        Bundle args = new Bundle();
        args.putLong("chat_id", channelId);
        if (messageId > 0) args.putInt("message_id", messageId);
        presentFragment(new ChatActivity(args));
    }

    void saveScroll() {
        if (layoutManager != null) {
            savedScrollState = layoutManager.onSaveInstanceState();
            hasScrollState = true;
        }
    }

    void refreshDisplayList() {
        if (adapter != null) {
            feedController.rebuildDisplayList();
            adapter.setItems(feedController.getCachedFeed());
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return true;
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) listView.smoothScrollToPosition(0);
        loadFeed(true);
    }
}