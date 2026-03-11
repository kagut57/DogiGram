package org.telegram.ui.Feed;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedUtils {

    public static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024f);
        if (size < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", size / (1024f * 1024f));
        return String.format(Locale.US, "%.1f GB", size / (1024f * 1024f * 1024f));
    }

    public static String formatVoiceDuration(int seconds) {
        if (seconds < 3600) {
            return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
        }
        return String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public static String getPeerName(TLRPC.Peer peer, MessagesController controller) {
        if (peer == null) return null;
        if (peer.channel_id != 0) {
            TLRPC.Chat chat = controller.getChat(peer.channel_id);
            return chat != null ? chat.title : null;
        } else if (peer.chat_id != 0) {
            TLRPC.Chat chat = controller.getChat(peer.chat_id);
            return chat != null ? chat.title : null;
        } else if (peer.user_id != 0) {
            TLRPC.User user = controller.getUser(peer.user_id);
            if (user == null) return null;
            String name = user.first_name;
            if (user.last_name != null && !user.last_name.isEmpty())
                name += " " + user.last_name;
            return name;
        }
        return null;
    }

    public static boolean isReallyEdited(TLRPC.Message msg) {
        if (msg.edit_date == 0) return false;
        if (msg.edit_hide) return false;
        if (msg.fwd_from != null) return false;
        if (msg.media instanceof TLRPC.TL_messageMediaGeoLive) return false;
        return !(msg.media instanceof TLRPC.TL_messageMediaPoll);
    }

    public static String getMediaTypeLabel(TLRPC.MessageMedia media) {
        if (media instanceof TLRPC.TL_messageMediaPhoto) return "📷 Photo";
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            for (TLRPC.DocumentAttribute attr : media.document.attributes) {
                if (attr instanceof TLRPC.TL_documentAttributeVideo) return "📹 Video";
                if (attr instanceof TLRPC.TL_documentAttributeAnimated) return "GIF";
                if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                    if (attr.voice) return "🎤 Voice message";
                    return "🎵 Audio";
                }
                if (attr instanceof TLRPC.TL_documentAttributeSticker) return "Sticker";
            }
            return "📎 Document";
        }
        if (media instanceof TLRPC.TL_messageMediaPoll) return "📊 Poll";
        if (media instanceof TLRPC.TL_messageMediaGeo) return "📍 Location";
        if (media instanceof TLRPC.TL_messageMediaGeoLive) return "📍 Live location";
        if (media instanceof TLRPC.TL_messageMediaContact) return "👤 Contact";
        return "Attachment";
    }

    public static List<TLRPC.Document> getDocuments(FeedController.FeedItem item) {
        List<TLRPC.Document> docs = new ArrayList<>();
        for (MessageObject msg : item.messages) {
            TLRPC.MessageMedia media = msg.messageOwner.media;
            if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
                TLRPC.Document doc = media.document;
                boolean skip = false;
                for (TLRPC.DocumentAttribute attr : doc.attributes) {
                    if (attr instanceof TLRPC.TL_documentAttributeVideo) skip = true;
                    if (attr instanceof TLRPC.TL_documentAttributeAnimated) skip = true;
                    if (attr instanceof TLRPC.TL_documentAttributeSticker) skip = true;
                    if (attr instanceof TLRPC.TL_documentAttributeAudio) skip = true;
                }
                if (!skip) docs.add(doc);
            }
        }
        return docs;
    }

    public static int getVoiceDuration(MessageObject msg) {
        if (msg == null || msg.messageOwner == null || msg.messageOwner.media == null) return 0;
        if (!(msg.messageOwner.media instanceof TLRPC.TL_messageMediaDocument)) return 0;
        TLRPC.Document doc = msg.messageOwner.media.document;
        if (doc == null) return 0;
        for (TLRPC.DocumentAttribute attr : doc.attributes) {
            if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                return (int) ((TLRPC.TL_documentAttributeAudio) attr).duration;
            }
        }
        return 0;
    }
}