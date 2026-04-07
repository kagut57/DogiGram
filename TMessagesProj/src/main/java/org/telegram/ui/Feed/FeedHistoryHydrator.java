package org.telegram.ui.Feed;

import android.util.SparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FeedHistoryHydrator implements NotificationCenter.NotificationCenterDelegate {

    public static class Request {
        public final long dialogId;
        public final int afterMid;
        public final int topMessageId;
        public final int count;

        public Request(long dialogId, int afterMid, int topMessageId, int count) {
            this.dialogId = dialogId;
            this.afterMid = afterMid;
            this.topMessageId = topMessageId;
            this.count = count;
        }
    }

    public interface Callback {
        void onComplete(Set<Long> affectedDialogs);
    }

    private final int currentAccount;
    private final SparseArray<Long> pendingGuids = new SparseArray<>();
    private final HashSet<Long> affectedDialogs = new HashSet<>();

    private boolean active;
    private int nextGuid = 0x5F000000;
    private Callback callback;
    private Runnable timeoutRunnable;

    public FeedHistoryHydrator(int account) {
        currentAccount = account;
    }

    public boolean isActive() {
        return active;
    }

    public void hydrate(ArrayList<Request> requests, Callback cb) {
        if (active) {
            cb.onComplete(new HashSet<>());
            return;
        }
        if (requests == null || requests.isEmpty()) {
            cb.onComplete(new HashSet<>());
            return;
        }

        active = true;
        callback = cb;
        pendingGuids.clear();
        affectedDialogs.clear();

        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.messagesDidLoadWithoutProcess);
        nc.addObserver(this, NotificationCenter.loadingMessagesFailed);

        MessagesController controller = MessagesController.getInstance(currentAccount);
        ConnectionsManager cm = ConnectionsManager.getInstance(currentAccount);

        for (int i = 0; i < requests.size(); i++) {
            Request request = requests.get(i);

            TLRPC.InputPeer peer = controller.getInputPeer(request.dialogId);
            if (peer == null) {
                continue;
            }

            int guid = ++nextGuid;
            pendingGuids.put(guid, request.dialogId);
            affectedDialogs.add(request.dialogId);

            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = peer;
            req.limit = request.count;
            req.offset_id = request.afterMid;
            req.offset_date = 0;
            req.add_offset = -request.count - 1;

            int reqId = cm.sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;

                    if (res.messages.size() > request.count) {
                        res.messages.remove(0);
                    }

                    controller.processLoadedMessages(
                            res,
                            res.messages.size(),
                            request.dialogId,
                            0,
                            request.count,
                            request.afterMid,
                            0,
                            false,
                            guid,
                            0,
                            request.topMessageId,
                            0,
                            0,
                            1,
                            false,
                            0,
                            0,
                            0,
                            false,
                            0,
                            false,
                            false,
                            null
                    );
                } else {
                    AndroidUtilities.runOnUIThread(() -> finishGuid(guid));
                }
            });

            cm.bindRequestToGuid(reqId, guid);
        }

        if (pendingGuids.size() == 0) {
            finishAll();
            return;
        }

        timeoutRunnable = this::finishAll;
        AndroidUtilities.runOnUIThread(timeoutRunnable, 12000);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount || !active || args == null || args.length == 0) {
            return;
        }

        if (id == NotificationCenter.messagesDidLoadWithoutProcess) {
            if (!(args[0] instanceof Integer)) return;
            int guid = (Integer) args[0];
            finishGuid(guid);

        } else if (id == NotificationCenter.loadingMessagesFailed) {
            if (!(args[0] instanceof Integer)) return;
            int guid = (Integer) args[0];
            finishGuid(guid);
        }
    }

    private void finishGuid(int guid) {
        if (pendingGuids.indexOfKey(guid) < 0) {
            return;
        }
        pendingGuids.remove(guid);
        if (pendingGuids.size() == 0) {
            finishAll();
        }
    }

    private void finishAll() {
        if (!active) return;

        active = false;

        if (timeoutRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            timeoutRunnable = null;
        }

        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.messagesDidLoadWithoutProcess);
        nc.removeObserver(this, NotificationCenter.loadingMessagesFailed);

        Callback cb = callback;
        callback = null;

        HashSet<Long> result = new HashSet<>(affectedDialogs);
        pendingGuids.clear();
        affectedDialogs.clear();

        if (cb != null) {
            cb.onComplete(result);
        }
    }
}