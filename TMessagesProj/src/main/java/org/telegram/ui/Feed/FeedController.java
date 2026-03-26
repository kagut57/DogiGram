package org.telegram.ui.Feed;

import android.content.SharedPreferences;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Custom.CustomSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FeedController implements NotificationCenter.NotificationCenterDelegate {
    private boolean observing = false;
    private final List<Runnable> newPostListeners = new ArrayList<>();

private static final int MAX_MESSAGES_PER_CHANNEL = 20; // было 50

    private final FeedRecommendationEngine recommendationEngine;

    private interface LocalLoadCallback {
        void onLoaded(List<FeedItem> items);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (!feedLoaded) return;

            long dialogId = (Long) args[0];
            if (dialogId >= 0) return;

            MessagesController controller = MessagesController.getInstance(currentAccount);
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat == null || !chat.broadcast || chat.megagroup) return;
            if (isChannelHidden(-dialogId)) return;
            if (CustomSettings.hideProxySponsor() && controller.isPromoDialog(dialogId, false))
                return;

            ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
            if (messages == null || messages.isEmpty()) return;

            List<MessageObject> validMessages = new ArrayList<>();
            for (MessageObject obj : messages) {
                if (obj.isOut() || obj.messageOwner.action != null) continue;
                boolean hasContent = (obj.messageText != null && obj.messageText.length() > 0)
                        || obj.messageOwner.media != null;
                if (!hasContent) continue;
                validMessages.add(obj);
            }
            if (validMessages.isEmpty()) return;

            List<FeedItem> newItems = groupIntoItems(validMessages, dialogId);
            boolean changed = false;

            for (FeedItem newItem : newItems) {
                MessageObject primary = newItem.getPrimaryMessage();
                long groupedId = primary.messageOwner.grouped_id;

                if (groupedId != 0) {
                    FeedItem existing = findExistingAlbum(dialogId, groupedId);
                    if (existing != null) {
                        if (mergeAlbumMessages(existing, newItem)) {
                            for (MessageObject msg : newItem.messages) {
                                loadedItemIds.add(dialogId + "_" + msg.getId());
                            }
                            mergedItems.add(existing);
                            changed = true;
                        }
                        continue;
                    }
                }

                String uid = newItem.getUniqueId();
                if (loadedItemIds.contains(uid)) continue;

                loadedItemIds.add(uid);
                for (MessageObject msg : newItem.messages) {
                    loadedItemIds.add(dialogId + "_" + msg.getId());
                }
                newItem.isRead = false;
                newItem.isBookmarked = isBookmarked(uid);
                cachedFeed.add(newItem);
                pendingNewItems.add(newItem);
                changed = true;
            }

            if (changed) {
                Collections.sort(cachedFeed, Comparator.comparingLong(a -> a.sortDate));
                AndroidUtilities.runOnUIThread(this::notifyNewPostListeners);
            }
        }
    }

    public void startObserving() {
        if (observing) return;
        observing = true;
        NotificationCenter.getInstance(currentAccount)
                .addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount)
                .addObserver(this, NotificationCenter.messagesDidLoad);
    }

    public void stopObserving() {
        if (!observing) return;
        observing = false;
        NotificationCenter.getInstance(currentAccount)
                .removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount)
                .removeObserver(this, NotificationCenter.messagesDidLoad);
    }

    public void addNewPostListener(Runnable listener) {
        if (!newPostListeners.contains(listener)) {
            newPostListeners.add(listener);
        }
    }

    public void removeNewPostListener(Runnable listener) {
        newPostListeners.remove(listener);
    }

    private void notifyNewPostListeners() {
        for (Runnable r : newPostListeners) {
            r.run();
        }
    }

    @FunctionalInterface
    public interface FeedLoadCallback {
        void onLoaded(List<FeedItem> items, boolean hasMore);
    }

    public static class FeedItem {
        public final long channelId;
        public final List<MessageObject> messages;
        public boolean isRead;
        public boolean isBookmarked;
        public long sortDate;

        public boolean isRecommendation = false;
        public String recommendationReason;
        public TLRPC.Chat recommendedChat;
        public long recommendedChannelId;

        public boolean textExpanded = false;
        public final java.util.HashSet<Integer> expandedQuoteOffsets = new java.util.HashSet<>();

        public boolean translationShown = false;

        public FeedItem(long channelId, List<MessageObject> messages, long date) {
            this.channelId = channelId;
            this.messages = messages;
            this.sortDate = date;
        }

        public MessageObject getPrimaryMessage() { return messages.get(0); }
        public boolean isAlbum() { return messages.size() > 1; }
        public int getMessageId() { return getPrimaryMessage().getId(); }
        public String getUniqueId() { return channelId + "_" + getMessageId(); }
    }

    private static final FeedController[] instances = new FeedController[UserConfig.MAX_ACCOUNT_COUNT];

    private final int currentAccount;
    private final Set<String> localReadIds = new HashSet<>();
    private final Set<String> bookmarkedIds = new HashSet<>();
    private final Set<Long> hiddenChannelIds = new HashSet<>();
    private final List<FeedItem> cachedFeed = new ArrayList<>();
    private boolean isLoading = false;
    private boolean feedLoaded = false;
    private Runnable saveRunnable;

    private boolean noMorePosts = false;
    private final Set<String> loadedItemIds = new HashSet<>();

    private final List<Object> displayItems = new ArrayList<>();
    private int postsSinceLastRec = 0;
    private int recPostsUsedIndex = 0;
    private final List<FeedItem> pendingNewItems = new ArrayList<>();
    private final List<FeedItem> mergedItems = new ArrayList<>();

    public static FeedController getInstance(int account) {
        FeedController local = instances[account];
        if (local == null) {
            synchronized (FeedController.class) {
                local = instances[account];
                if (local == null) {
                    instances[account] = local = new FeedController(account);
                }
            }
        }
        return local;
    }

    private FeedController(int account) {
        this.currentAccount = account;
        loadPersistedData();
        this.recommendationEngine = FeedRecommendationEngine.getInstance(account);
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("feed_v6_" + currentAccount, 0);
    }

    private void loadPersistedData() {
        SharedPreferences p = getPrefs();
        localReadIds.addAll(p.getStringSet("read", new HashSet<>()));
        bookmarkedIds.addAll(p.getStringSet("bookmarks", new HashSet<>()));

        Set<String> hiddenSet = p.getStringSet("hidden_channels", new HashSet<>());
        for (String s : hiddenSet) {
            try {
                hiddenChannelIds.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void saveNow() {
        if (saveRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(saveRunnable);
            saveRunnable = null;
        }
        performSave();
    }

    private void performSave() {
        Set<String> r = new HashSet<>(localReadIds);
        if (r.size() > 50000) {
            List<String> l = new ArrayList<>(r);
            r = new HashSet<>(l.subList(l.size() - 50000, l.size()));
        }

        Set<String> hiddenStrs = new HashSet<>();
        for (Long id : hiddenChannelIds) {
            hiddenStrs.add(String.valueOf(id));
        }

        getPrefs().edit()
                .putStringSet("read", r)
                .putStringSet("bookmarks", new HashSet<>(bookmarkedIds))
                .putStringSet("hidden_channels", hiddenStrs)
                .apply();
    }

    public void markAsRead(FeedItem item) {
        if (item == null) return;
        String uid = item.getUniqueId();
        if (localReadIds.contains(uid)) return;
        item.isRead = true;

        int maxId = 0;
        for (MessageObject msg : item.messages) {
            localReadIds.add(item.channelId + "_" + msg.getId());
            maxId = Math.max(maxId, msg.getId());
        }
        localReadIds.add(uid);

        saveNow();

        final int finalMaxId = maxId;
        final long dialogId = item.channelId;

        AndroidUtilities.runOnUIThread(() -> {
            MessagesController controller = MessagesController.getInstance(currentAccount);
            long chatId = -dialogId;
            TLRPC.Chat chat = controller.getChat(chatId);

            if (chat == null) return;

            TLRPC.TL_channels_readHistory req = new TLRPC.TL_channels_readHistory();
            req.channel = MessagesController.getInputChannel(chat);
            req.max_id = finalMaxId;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error != null) {
                    android.util.Log.w("FeedController",
                            "readHistory failed for " + dialogId + ": " + error.text);
                }
            });

            TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
            if (dialog != null) {
                int oldMaxId = dialog.read_inbox_max_id;

                if (finalMaxId > oldMaxId) {
                    dialog.read_inbox_max_id = finalMaxId;
                }

                int actuallyRead = 0;
                for (MessageObject msg : item.messages) {
                    if (msg.getId() > oldMaxId) actuallyRead++;
                }
                if (actuallyRead > 0) {
                    dialog.unread_count = Math.max(0, dialog.unread_count - actuallyRead);
                }

                LongSparseIntArray inbox = new LongSparseIntArray();
                inbox.put(dialogId, finalMaxId);
                MessagesStorage.getInstance(currentAccount).updateDialogsWithReadMessages(
                        inbox, null, null, null, true
                );

                NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.updateInterfaces,
                        MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE);
                NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.dialogsNeedReload);
            }
        });
    }

    public boolean isLocallyRead(long channelId, int messageId) {
        return localReadIds.contains(channelId + "_" + messageId);
    }

    public boolean isBookmarked(String uid) { return bookmarkedIds.contains(uid); }

    public boolean hasCachedFeed() { return feedLoaded && !cachedFeed.isEmpty(); }
    public List<FeedItem> getCachedFeed() { return cachedFeed; }
    public boolean isLoading() { return isLoading; }

    public void loadFeed(boolean force, FeedLoadCallback callback) {
        if (isLoading) return;
        if (!force && feedLoaded && !cachedFeed.isEmpty()) {
            callback.onLoaded(cachedFeed, false);
            return;
        }
        isLoading = true;

        MessagesController controller = MessagesController.getInstance(currentAccount);
        List<TLRPC.Dialog> channels = collectUnreadChannels(controller);

        if (channels.isEmpty()) {
            if (force) {
                cachedFeed.clear();
                feedLoaded = true;
                pendingNewItems.clear();
            }
            isLoading = false;
            noMorePosts = true;
            rebuildDisplayList();
            callback.onLoaded(new ArrayList<>(), false);
            return;
        }

        loadFromLocalDB(channels, localItems -> {
            boolean hasLocalData = !localItems.isEmpty();

            if (hasLocalData) {
                cachedFeed.clear();
                cachedFeed.addAll(localItems);
                loadedItemIds.clear();
                for (FeedItem item : localItems) {
                    loadedItemIds.add(item.getUniqueId());
                    for (MessageObject msg : item.messages) {
                        loadedItemIds.add(item.channelId + "_" + msg.getId());
                    }
                }
                rebuildDisplayList();
                callback.onLoaded(cachedFeed, true);
            }

            loadFromServer(channels, controller, () -> {
                feedLoaded = true;
                isLoading = false;
                noMorePosts = true;

                rebuildDisplayList();
                callback.onLoaded(cachedFeed, false);

                recommendationEngine.refreshPosts(() -> {
                    rebuildDisplayList();
                    notifyNewPostListeners();
                });
            });
        });
    }

    private List<TLRPC.Dialog> collectUnreadChannels(MessagesController controller) {
        List<TLRPC.Dialog> channels = new ArrayList<>();
        for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
            if (dialog == null || dialog.id >= 0) continue;
            TLRPC.Chat chat = controller.getChat(-dialog.id);
            if (chat == null || !chat.broadcast || chat.megagroup) continue;
            if (dialog.unread_count <= 0) continue;
            if (dialog.read_inbox_max_id <= 0) continue;
            if (dialog.top_message <= dialog.read_inbox_max_id) continue;
            if (isChannelHidden(-dialog.id)) continue;
            if (CustomSettings.hideProxySponsor()
                    && controller.isPromoDialog(dialog.id, false)) continue;
            channels.add(dialog);
        }
        return channels;
    }

    private void loadFromLocalDB(List<TLRPC.Dialog> channels,
                                 LocalLoadCallback callback) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);

        storage.getStorageQueue().postRunnable(() -> {
            List<FeedItem> allLocalItems = new ArrayList<>();

            try {
                long selfId = UserConfig.getInstance(currentAccount).getClientUserId();

                for (TLRPC.Dialog dialog : channels) {
                    int readMaxId = dialog.read_inbox_max_id;
                    int limit = Math.min(
                            dialog.unread_count + 2, MAX_MESSAGES_PER_CHANNEL);

                    SQLiteCursor cursor = null;
                    try {
                        cursor = storage.getDatabase().queryFinalized(
                                "SELECT data, mid, date FROM messages_v2"
                                        + " WHERE uid = " + dialog.id
                                        + " AND mid > " + readMaxId
                                        + " ORDER BY mid ASC"
                                        + " LIMIT " + limit
                        );

                        List<MessageObject> localMessages = new ArrayList<>();

                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data == null) continue;

                            try {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(
                                        data, data.readInt32(false), false);
                                if (message == null) continue;

                                message.readAttachPath(data, selfId);
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = dialog.id;

                                if (message.peer_id == null) {
                                    TLRPC.TL_peerChannel peer =
                                            new TLRPC.TL_peerChannel();
                                    peer.channel_id = -dialog.id;
                                    message.peer_id = peer;
                                }

                                if (isLocallyRead(dialog.id, message.id))
                                    continue;

                                MessageObject obj = new MessageObject(
                                        currentAccount, message, true, true);
                                if (obj.isOut()) continue;
                                if (obj.messageOwner.action != null) continue;

                                boolean hasContent =
                                        (obj.messageText != null
                                                && obj.messageText.length() > 0)
                                                || obj.messageOwner.media != null;
                                if (!hasContent) continue;

                                localMessages.add(obj);

                            } finally {
                                data.reuse();
                            }
                        }

                        if (!localMessages.isEmpty()) {
                            allLocalItems.addAll(
                                    groupIntoItems(localMessages, dialog.id));
                        }

                    } finally {
                        if (cursor != null) {
                            cursor.dispose();
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("FeedController",
                        "loadFromLocalDB failed", e);
            }

            Collections.sort(allLocalItems,
                    Comparator.comparingLong(a -> a.sortDate));

            final List<FeedItem> result = allLocalItems;
            AndroidUtilities.runOnUIThread(() -> callback.onLoaded(result));
        });
    }

    private void loadFromServer(List<TLRPC.Dialog> channels,
                                MessagesController controller,
                                Runnable onComplete) {
        final List<FeedItem> serverItems =
                Collections.synchronizedList(new ArrayList<>());
        final List<TLRPC.User> collectedUsers =
                Collections.synchronizedList(new ArrayList<>());
        final List<TLRPC.Chat> collectedChats =
                Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger completed = new AtomicInteger(0);
        final int totalChannels = channels.size();

        for (TLRPC.Dialog dialog : channels) {
            final int readMaxId = dialog.read_inbox_max_id;
            int limit = Math.min(
                    dialog.unread_count + 2, MAX_MESSAGES_PER_CHANNEL);

            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = controller.getInputPeer(dialog.id);
            req.limit = limit;
            req.offset_id = 0;
            req.max_id = 0;
            req.min_id = readMaxId;

            ConnectionsManager.getInstance(currentAccount)
                    .sendRequest(req, (response, error) -> {
                        if (response instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages msgs =
                                    (TLRPC.messages_Messages) response;

                           collectedUsers.addAll(msgs.users);
                            collectedChats.addAll(msgs.chats);

                            List<MessageObject> channelMessages =
                                    new ArrayList<>();
                            for (TLRPC.Message msg : msgs.messages) {
                                if (msg.id <= readMaxId) continue;
                                if (isLocallyRead(dialog.id, msg.id))
                                    continue;

                                MessageObject obj = new MessageObject(
                                        currentAccount, msg, true, true);
                                if (obj.isOut()) continue;
                                if (obj.messageOwner.action != null) continue;

                                boolean hasContent =
                                        (obj.messageText != null
                                                && obj.messageText.length() > 0)
                                                || obj.messageOwner.media
                                                != null;
                                if (!hasContent) continue;

                                channelMessages.add(obj);
                            }

                            serverItems.addAll(
                                    groupIntoItems(channelMessages, dialog.id));
                        }

                        int done = completed.incrementAndGet();
                        if (done >= totalChannels) {
                            AndroidUtilities.runOnUIThread(() -> {
                                controller.putUsers(
                                        new ArrayList<>(collectedUsers), false);
                                controller.putChats(
                                        new ArrayList<>(collectedChats), false);

                                mergeServerItems(new ArrayList<>(serverItems));

                                onComplete.run();
                            });
                        }
                    });
        }
    }

    private void mergeServerItems(List<FeedItem> serverItems) {
        for (FeedItem item : serverItems) {
            MessageObject primary = item.getPrimaryMessage();
            long groupedId = primary.messageOwner.grouped_id;

            if (groupedId != 0) {
                FeedItem existing = findExistingAlbum(item.channelId, groupedId);
                if (existing != null) {
                    mergeAlbumMessages(existing, item);
                    for (MessageObject msg : item.messages) {
                        loadedItemIds.add(
                                item.channelId + "_" + msg.getId());
                    }
                    continue;
                }
            }

            String uid = item.getUniqueId();
            if (loadedItemIds.contains(uid)) continue;

            loadedItemIds.add(uid);
            for (MessageObject msg : item.messages) {
                loadedItemIds.add(item.channelId + "_" + msg.getId());
            }
            item.isRead = false;
            item.isBookmarked = isBookmarked(uid);
            cachedFeed.add(item);
        }

        Collections.sort(cachedFeed, Comparator.comparingLong(a -> a.sortDate));

        loadedItemIds.clear();
        for (FeedItem item : cachedFeed) {
            loadedItemIds.add(item.getUniqueId());
            for (MessageObject msg : item.messages) {
                loadedItemIds.add(item.channelId + "_" + msg.getId());
            }
        }
    }

    private List<FeedItem> groupIntoItems(List<MessageObject> messages,
                                          long dialogId) {
        LinkedHashMap<String, List<MessageObject>> groups =
                new LinkedHashMap<>();
        for (MessageObject msg : messages) {
            long gid = msg.messageOwner.grouped_id;
            String key = gid != 0
                    ? "g_" + dialogId + "_" + gid
                    : "s_" + dialogId + "_" + msg.getId();
            List<MessageObject> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(msg);
        }

        List<FeedItem> items = new ArrayList<>();
        for (List<MessageObject> group : groups.values()) {
            Collections.sort(group,
                    (a, b) -> Integer.compare(a.getId(), b.getId()));
            MessageObject primary = group.get(0);
            FeedItem item = new FeedItem(
                    dialogId, group, primary.messageOwner.date);
            item.isRead = false;
            item.isBookmarked = isBookmarked(item.getUniqueId());
            items.add(item);
        }
        return items;
    }

    public void hideChannel(long channelId) {
        hiddenChannelIds.add(channelId);
        cachedFeed.removeIf(
                item -> item.channelId == -channelId
                        || item.channelId == channelId);
        saveNow();
    }

    public void unhideChannel(long channelId) {
        hiddenChannelIds.remove(channelId);
        saveNow();
    }

    public boolean isChannelHidden(long channelId) {
        return hiddenChannelIds.contains(channelId);
    }

    public Set<Long> getHiddenChannelIds() {
        return new HashSet<>(hiddenChannelIds);
    }

    public boolean hasMore() {
        return !noMorePosts;
    }

    public void resetLoadMore() {
        noMorePosts = false;
        loadedItemIds.clear();
        pendingNewItems.clear();
    }

    public void loadMore(FeedLoadCallback callback) {
        noMorePosts = true;
        callback.onLoaded(cachedFeed, false);
    }

    public FeedRecommendationEngine getRecommendationEngine() {
        return recommendationEngine;
    }

    public List<Object> getDisplayItems() {
        return displayItems;
    }

    public void rebuildDisplayList() {
        displayItems.clear();
        postsSinceLastRec = 0;
        recPostsUsedIndex = 0;

        List<FeedItem> recPosts = recommendationEngine.hasRecommendedPosts()
                ? recommendationEngine.getRecommendedPosts()
                : Collections.emptyList();

        int interval = FeedRecommendationEngine.getRecommendationInterval();

        for (FeedItem item : cachedFeed) {
            displayItems.add(item);
            postsSinceLastRec++;

            if (postsSinceLastRec >= interval
                    && recPostsUsedIndex < recPosts.size()) {
                displayItems.add(recPosts.get(recPostsUsedIndex++));
                postsSinceLastRec = 0;
            }
        }

        while (recPostsUsedIndex < recPosts.size()) {
            displayItems.add(recPosts.get(recPostsUsedIndex++));
        }
    }

    public List<FeedItem> flushPendingNewItems() {
        List<FeedItem> result = new ArrayList<>(pendingNewItems);
        pendingNewItems.clear();
        return result;
    }

    public int appendItemsToDisplay(List<FeedItem> items) {
        int added = 0;
        List<FeedItem> recPosts = recommendationEngine.hasRecommendedPosts()
                ? recommendationEngine.getRecommendedPosts()
                : Collections.emptyList();
        int interval = FeedRecommendationEngine.getRecommendationInterval();

        for (FeedItem item : items) {
            displayItems.add(item);
            added++;
            postsSinceLastRec++;

            if (postsSinceLastRec >= interval
                    && recPostsUsedIndex < recPosts.size()) {
                displayItems.add(recPosts.get(recPostsUsedIndex++));
                postsSinceLastRec = 0;
                added++;
            }
        }
        return added;
    }

    public int appendNewRecommendationsToDisplay() {
        List<FeedItem> recPosts = recommendationEngine.hasRecommendedPosts()
                ? recommendationEngine.getRecommendedPosts()
                : Collections.emptyList();

        int added = 0;
        while (recPostsUsedIndex < recPosts.size()) {
            displayItems.add(recPosts.get(recPostsUsedIndex++));
            added++;
        }
        return added;
    }

    public static class FeedSeparator {
        public final int type;
        public FeedSeparator(int type) {
            this.type = type;
        }
    }

    public void loadMoreRecommendations(FeedLoadCallback callback) {
        if (!recommendationEngine.canLoadMore()) {
            callback.onLoaded(cachedFeed, false);
            return;
        }

        recommendationEngine.loadMore(() ->
                AndroidUtilities.runOnUIThread(() ->
                        callback.onLoaded(cachedFeed,
                                recommendationEngine.canLoadMore())
                )
        );
    }

    private FeedItem findExistingAlbum(long dialogId, long groupedId) {
        for (FeedItem item : cachedFeed) {
            if (item.channelId != dialogId) continue;
            for (MessageObject msg : item.messages) {
                if (msg.messageOwner.grouped_id == groupedId) {
                    return item;
                }
            }
        }
        return null;
    }

    private boolean mergeAlbumMessages(FeedItem existing, FeedItem incoming) {
        Set<Integer> existingIds = new HashSet<>();
        for (MessageObject msg : existing.messages) {
            existingIds.add(msg.getId());
        }

        boolean added = false;
        for (MessageObject msg : incoming.messages) {
            if (!existingIds.contains(msg.getId())) {
                existing.messages.add(msg);
                added = true;
            }
        }

        if (added) {
            Collections.sort(existing.messages,
                    Comparator.comparingInt(MessageObject::getId));
        }
        return added;
    }

    public List<FeedItem> flushMergedItems() {
        List<FeedItem> result = new ArrayList<>(mergedItems);
        mergedItems.clear();
        return result;
    }
}