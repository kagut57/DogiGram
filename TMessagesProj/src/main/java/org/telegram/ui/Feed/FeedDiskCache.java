package org.telegram.ui.Feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedDiskCache {

    private static final int VERSION = 1;

    public static class SnapshotItem {
        public final long channelId;
        public final long sortDate;
        public final int[] messageIds;

        public SnapshotItem(long channelId, long sortDate, int[] messageIds) {
            this.channelId = channelId;
            this.sortDate = sortDate;
            this.messageIds = messageIds;
        }
    }

    public interface LoadCallback {
        void onLoaded(List<SnapshotItem> items);
    }

    private final File snapshotFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FeedDiskCache");
        t.setDaemon(true);
        return t;
    });

    public FeedDiskCache(int account) {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "feed_cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        snapshotFile = new File(dir, "feed_snapshot_" + account + ".bin");
    }

    public void save(List<SnapshotItem> items) {
        final ArrayList<SnapshotItem> copy = new ArrayList<>();
        if (items != null) {
            for (SnapshotItem item : items) {
                int[] mids = item.messageIds != null ? item.messageIds.clone() : new int[0];
                copy.add(new SnapshotItem(item.channelId, item.sortDate, mids));
            }
        }

        executor.execute(() -> {
            if (copy.isEmpty()) {
                if (snapshotFile.exists()) {
                    snapshotFile.delete();
                }
                return;
            }

            DataOutputStream out = null;
            try {
                out = new DataOutputStream(
                        new BufferedOutputStream(new FileOutputStream(snapshotFile)));
                out.writeInt(VERSION);
                out.writeInt(copy.size());

                for (SnapshotItem item : copy) {
                    out.writeLong(item.channelId);
                    out.writeLong(item.sortDate);
                    out.writeInt(item.messageIds.length);
                    for (int mid : item.messageIds) {
                        out.writeInt(mid);
                    }
                }
                out.flush();
            } catch (Exception ignore) {
            } finally {
                try {
                    if (out != null) out.close();
                } catch (Exception ignore) {}
            }
        });
    }

    public void load(LoadCallback callback) {
        executor.execute(() -> {
            ArrayList<SnapshotItem> result = new ArrayList<>();
            DataInputStream in = null;

            try {
                if (!snapshotFile.exists()) {
                    postResult(callback, result);
                    return;
                }

                in = new DataInputStream(
                        new BufferedInputStream(new FileInputStream(snapshotFile)));

                int version = in.readInt();
                if (version != VERSION) {
                    postResult(callback, result);
                    return;
                }

                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    long channelId = in.readLong();
                    long sortDate = in.readLong();
                    int midsCount = in.readInt();
                    if (midsCount <= 0 || midsCount > 64) {
                        result.clear();
                        break;
                    }

                    int[] mids = new int[midsCount];
                    for (int j = 0; j < midsCount; j++) {
                        mids[j] = in.readInt();
                    }

                    result.add(new SnapshotItem(channelId, sortDate, mids));
                }
            } catch (Exception ignore) {
                result.clear();
            } finally {
                try {
                    if (in != null) in.close();
                } catch (Exception ignore) {}
            }

            postResult(callback, result);
        });
    }

    public void clear() {
        executor.execute(() -> {
            if (snapshotFile.exists()) {
                snapshotFile.delete();
            }
        });
    }

    private void postResult(LoadCallback callback, List<SnapshotItem> result) {
        AndroidUtilities.runOnUIThread(() -> callback.onLoaded(result));
    }
}