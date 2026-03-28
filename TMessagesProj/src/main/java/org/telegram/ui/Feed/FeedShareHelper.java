package org.telegram.ui.Feed;

import android.os.Bundle;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.DialogsActivity;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

class FeedShareHelper {

    private final FeedActivity activity;

    FeedShareHelper(FeedActivity activity) {
        this.activity = activity;
    }

    void sharePost(FeedController.FeedItem item) {
        if (activity.getParentActivity() == null) return;

        TLRPC.Chat chat = MessagesController.getInstance(activity.getAccount())
                .getChat(-item.channelId);
        boolean noForwards = chat != null && chat.noforwards;

        if (!noForwards) {
            ArrayList<MessageObject> msgs = new ArrayList<>(item.messages);
            ShareAlert alert = new ShareAlert(
                    activity.getParentActivity(), msgs, null, false, null, false);
            activity.showDialog(alert);
        } else {
            forwardAsCopy(item);
        }
    }

    private void forwardAsCopy(FeedController.FeedItem item) {
        String link = buildPostLink(item);
        showCopyDestinationPicker(item, link);
    }

    private void showCopyDestinationPicker(FeedController.FeedItem item, String link) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", 3);

        DialogsActivity dialogsActivity = new DialogsActivity(args);
        dialogsActivity.setDelegate((fragment, dids, message, param, param2,
                                     scheduleDate, sendMode, topicsFragment) -> {
            if (dids != null) {
                for (MessagesStorage.TopicKey key : dids) {
                    if (key.dialogId != 0) forwardDropAuthor(item, key.dialogId, link);
                }
            }
            fragment.finishFragment();
            BulletinFactory.of(activity)
                    .createSimpleBulletin(R.drawable.msg_forward,
                            LocaleController.getString("FeedSavedToBookmarks",
                                    R.string.FeedSavedToBookmarks))
                    .show();
            return true;
        });
        activity.presentFragment(dialogsActivity);
    }

    void forwardToSaved(FeedController.FeedItem item) {
        try {
            int account = activity.getAccount();
            long selfId = UserConfig.getInstance(account).getClientUserId();
            MessagesController controller = MessagesController.getInstance(account);

            TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
            req.to_peer = controller.getInputPeer(selfId);
            req.from_peer = controller.getInputPeer(item.channelId);
            req.random_id = new ArrayList<>();
            req.id = new ArrayList<>();
            req.silent = true;
            for (MessageObject m : item.messages) {
                req.id.add(m.getId());
                req.random_id.add(Utilities.random.nextLong());
            }
            ConnectionsManager.getInstance(account).sendRequest(req, (r, e) -> {});
        } catch (Exception ignored) {}
    }

    private void forwardDropAuthor(FeedController.FeedItem item, long targetDialogId,
                                   String link) {
        int account = activity.getAccount();
        MessagesController controller = MessagesController.getInstance(account);

        TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
        req.to_peer = controller.getInputPeer(targetDialogId);
        req.from_peer = controller.getInputPeer(item.channelId);
        req.drop_author = true;
        req.silent = false;
        req.random_id = new ArrayList<>();
        req.id = new ArrayList<>();
        for (MessageObject m : item.messages) {
            req.id.add(m.getId());
            req.random_id.add(Utilities.random.nextLong());
        }

        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(
                        () -> sendManualCopy(item, targetDialogId, link));
            } else {
                AndroidUtilities.runOnUIThread(
                        () -> sendFormattedMessage(targetDialogId, link, null));
            }
        });
    }

    private void sendManualCopy(FeedController.FeedItem item, long targetDialogId,
                                String link) {
        new Thread(() -> {
            try {
                String messageText = "";
                ArrayList<TLRPC.MessageEntity> entities = null;
                for (MessageObject msg : item.messages) {
                    String text = msg.messageOwner.message;
                    if (text != null && !text.trim().isEmpty()
                            && !isCopyPlaceholder(text.trim())) {
                        messageText = text;
                        entities = msg.messageOwner.entities;
                        break;
                    }
                }

                String caption = messageText.isEmpty()
                        ? link : messageText + "\n\n" + link;

                ArrayList<MessageObject> mediaMessages = new ArrayList<>();
                for (MessageObject msg : item.messages) {
                    TLRPC.MessageMedia media = msg.messageOwner.media;
                    if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) continue;
                    if (media instanceof TLRPC.TL_messageMediaWebPage) continue;
                    if (media instanceof TLRPC.TL_messageMediaPhoto
                            || media instanceof TLRPC.TL_messageMediaDocument) {
                        File f = getMediaFile(msg);
                        if (f != null && f.exists()) mediaMessages.add(msg);
                    }
                }

                if (mediaMessages.isEmpty()) {
                    sendFormattedMessage(targetDialogId, caption, entities);
                    return;
                }

                for (int i = 0; i < mediaMessages.size(); i++) {
                    MessageObject msg = mediaMessages.get(i);
                    TLRPC.Message raw = msg.messageOwner;
                    File file = getMediaFile(msg);
                    if (file == null || !file.exists()) continue;

                    String mediaCaption = (i == 0) ? caption : "";
                    ArrayList<TLRPC.MessageEntity> mediaEntities =
                            (i == 0) ? entities : null;

                    TLRPC.InputFile uploaded = uploadFile(file);
                    if (uploaded == null) continue;

                    TLRPC.TL_messages_sendMedia req = new TLRPC.TL_messages_sendMedia();
                    req.peer = MessagesController.getInstance(activity.getAccount())
                            .getInputPeer(targetDialogId);
                    req.random_id = Utilities.random.nextLong();
                    req.message = mediaCaption;
                    if (mediaEntities != null && !mediaEntities.isEmpty()) {
                        req.entities = new ArrayList<>(mediaEntities);
                        req.flags |= 8;
                    }

                    if (raw.media instanceof TLRPC.TL_messageMediaPhoto) {
                        TLRPC.TL_inputMediaUploadedPhoto photo =
                                new TLRPC.TL_inputMediaUploadedPhoto();
                        photo.file = uploaded;
                        req.media = photo;
                    } else if (raw.media instanceof TLRPC.TL_messageMediaDocument
                            && raw.media.document != null) {
                        TLRPC.TL_inputMediaUploadedDocument doc =
                                new TLRPC.TL_inputMediaUploadedDocument();
                        doc.file = uploaded;
                        doc.mime_type = raw.media.document.mime_type != null
                                ? raw.media.document.mime_type : "application/octet-stream";
                        doc.attributes = new ArrayList<>(raw.media.document.attributes);
                        req.media = doc;
                    } else {
                        continue;
                    }
                    sendRequestSync(req);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    String buildPostLink(FeedController.FeedItem item) {
        TLRPC.Chat chat = MessagesController.getInstance(activity.getAccount())
                .getChat(-item.channelId);
        MessageObject msg = item.getPrimaryMessage();
        if (chat != null && !TextUtils.isEmpty(chat.username))
            return "https://t.me/" + chat.username + "/" + msg.getId();
        return "https://t.me/c/" + (-item.channelId) + "/" + msg.getId();
    }

    File getMediaFile(MessageObject msg) {
        int account = activity.getAccount();
        TLRPC.Message raw = msg.messageOwner;

        if (raw.media instanceof TLRPC.TL_messageMediaPhoto && raw.media.photo != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                    raw.media.photo.sizes, AndroidUtilities.getPhotoSize());
            if (size != null) {
                File f = FileLoader.getInstance(account).getPathToAttach(size, true);
                if (f != null && f.exists()) return f;
                f = FileLoader.getInstance(account).getPathToAttach(size, false);
                if (f != null && f.exists()) return f;
            }
        } else if (raw.media instanceof TLRPC.TL_messageMediaDocument
                && raw.media.document != null) {
            File f = FileLoader.getInstance(account)
                    .getPathToAttach(raw.media.document, true);
            if (f != null && f.exists()) return f;
            f = FileLoader.getInstance(account)
                    .getPathToAttach(raw.media.document, false);
            if (f != null && f.exists()) return f;
        }

        File f = FileLoader.getInstance(account).getPathToMessage(raw);
        if (f != null && f.exists()) return f;
        return null;
    }

    TLRPC.InputFile uploadFile(File file) {
        try {
            int account = activity.getAccount();
            long fileSize = file.length();
            boolean isBig = fileSize > 10 * 1024 * 1024;
            long fileId = Utilities.random.nextLong();
            int partSize;

            if (fileSize < 1024 * 1024) partSize = 64 * 1024;
            else if (fileSize < 10 * 1024 * 1024) partSize = 128 * 1024;
            else partSize = 512 * 1024;

            int totalParts = (int) Math.ceil((double) fileSize / partSize);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[partSize];
            int bytesRead, partNum = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] partData;
                if (bytesRead < partSize) {
                    partData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, partData, 0, bytesRead);
                } else {
                    partData = buffer;
                }

                boolean success;
                if (isBig) {
                    TLRPC.TL_upload_saveBigFilePart req =
                            new TLRPC.TL_upload_saveBigFilePart();
                    req.file_id = fileId;
                    req.file_part = partNum;
                    req.file_total_parts = totalParts;
                    req.bytes = new org.telegram.tgnet.NativeByteBuffer(partData.length);
                    req.bytes.writeBytes(partData);
                    req.bytes.position(0);
                    success = sendPartSync(req, account);
                } else {
                    TLRPC.TL_upload_saveFilePart req = new TLRPC.TL_upload_saveFilePart();
                    req.file_id = fileId;
                    req.file_part = partNum;
                    req.bytes = new org.telegram.tgnet.NativeByteBuffer(partData.length);
                    req.bytes.writeBytes(partData);
                    req.bytes.position(0);
                    success = sendPartSync(req, account);
                }

                if (!success) { fis.close(); return null; }
                partNum++;
            }
            fis.close();

            if (isBig) {
                TLRPC.TL_inputFileBig f = new TLRPC.TL_inputFileBig();
                f.id = fileId; f.parts = totalParts; f.name = file.getName();
                return f;
            } else {
                TLRPC.TL_inputFile f = new TLRPC.TL_inputFile();
                f.id = fileId; f.parts = totalParts;
                f.name = file.getName(); f.md5_checksum = "";
                return f;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean sendPartSync(TLObject req, int account) {
        final boolean[] done = {false};
        final boolean[] result = {false};
        ConnectionsManager.getInstance(account).sendRequest(req, (resp, err) -> {
            result[0] = err == null;
            synchronized (done) { done[0] = true; done.notifyAll(); }
        });
        synchronized (done) {
            try { while (!done[0]) done.wait(5000); } catch (InterruptedException ignored) {}
        }
        return result[0];
    }

    private void sendFormattedMessage(long targetDialogId, String text,
                                      ArrayList<TLRPC.MessageEntity> entities) {
        TLRPC.TL_messages_sendMessage req = new TLRPC.TL_messages_sendMessage();
        req.peer = MessagesController.getInstance(activity.getAccount())
                .getInputPeer(targetDialogId);
        req.message = text;
        req.random_id = Utilities.random.nextLong();
        req.no_webpage = true;
        if (entities != null && !entities.isEmpty()) {
            req.entities = new ArrayList<>(entities);
            req.flags |= 8;
        }
        ConnectionsManager.getInstance(activity.getAccount())
                .sendRequest(req, (r, e) -> {});
    }

    private void sendRequestSync(TLObject request) {
        final Object lock = new Object();
        final boolean[] done = {false};
        ConnectionsManager.getInstance(activity.getAccount())
                .sendRequest(request, (r, e) -> {
                    synchronized (lock) { done[0] = true; lock.notifyAll(); }
                });
        synchronized (lock) {
            try { while (!done[0]) lock.wait(30000); } catch (InterruptedException ignored) {}
        }
    }

    private boolean isCopyPlaceholder(String text) {
        switch (text) {
            case "Photo": case "Video": case "GIF": case "Document":
            case "Sticker": case "Audio": case "Voice message":
            case "Video message": case "Contact": case "Location":
                return true;
        }
        return false;
    }
}