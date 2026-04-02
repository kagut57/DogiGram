package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class FeedStickerView extends FrameLayout {

    private static final int MAX_STICKER_SIZE = dp(200);
    private static final int MIN_STICKER_SIZE = dp(100);

    private final BackupImageView stickerImage;
    private MessageObject currentMessage;
    private TLRPC.Document currentDocument;

    public FeedStickerView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        stickerImage = new BackupImageView(context);
        stickerImage.setAspectFit(true);
        stickerImage.getImageReceiver().setAutoRepeat(1);
        stickerImage.getImageReceiver().setAllowStartAnimation(true);
        addView(stickerImage, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    }

    public void setSticker(MessageObject msg) {
        currentMessage = msg;
        currentDocument = null;

        if (msg == null || msg.messageOwner == null || msg.messageOwner.media == null) {
            clear();
            return;
        }

        TLRPC.Document doc = msg.messageOwner.media.document;
        if (doc == null || !FeedUtils.isSticker(doc)) {
            clear();
            return;
        }

        currentDocument = doc;

        int w = 512, h = 512;
        for (TLRPC.DocumentAttribute attr : doc.attributes) {
            if (attr instanceof TLRPC.TL_documentAttributeImageSize) {
                w = attr.w;
                h = attr.h;
                break;
            }
            if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                w = attr.w;
                h = attr.h;
                break;
            }
        }

        if (w <= 0) w = 512;
        if (h <= 0) h = 512;

        float scale = Math.min((float) MAX_STICKER_SIZE / w, (float) MAX_STICKER_SIZE / h);
        int displayW = Math.max(MIN_STICKER_SIZE, (int) (w * scale));
        int displayH = Math.max(MIN_STICKER_SIZE, (int) (h * scale));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) stickerImage.getLayoutParams();
        lp.width = displayW;
        lp.height = displayH;
        lp.gravity = Gravity.CENTER;
        stickerImage.setLayoutParams(lp);

        ImageLocation thumbLoc = null;
        String thumbFilter = null;
        if (doc.thumbs != null && !doc.thumbs.isEmpty()) {
            TLRPC.PhotoSize stripped = findStripped(doc.thumbs);
            if (stripped != null) {
                thumbLoc = ImageLocation.getForDocument(stripped, doc);
                thumbFilter = "b";
            } else {
                TLRPC.PhotoSize thumb = FeedMediaHelper.bestSize(doc.thumbs);
                if (thumb != null) {
                    thumbLoc = ImageLocation.getForDocument(thumb, doc);
                    thumbFilter = displayW + "_" + displayH;
                }
            }
        }

        String filter = displayW + "_" + displayH;

        boolean isAnimated = MessageObject.isAnimatedStickerDocument(doc, true);
        boolean isVideo = MessageObject.isVideoStickerDocument(doc);

        if (isAnimated || isVideo) {
            stickerImage.setImage(
                    ImageLocation.getForDocument(doc), filter,
                    thumbLoc, thumbFilter,
                    (int) doc.size, msg);
            stickerImage.getImageReceiver().setAutoRepeat(1);
            stickerImage.getImageReceiver().setAllowStartAnimation(true);
        } else {
            stickerImage.setImage(
                    ImageLocation.getForDocument(doc), filter,
                    thumbLoc, thumbFilter,
                    0, msg);
        }

        setVisibility(VISIBLE);
    }

    public void clear() {
        currentMessage = null;
        currentDocument = null;
        stickerImage.setImageDrawable(null);
        stickerImage.getImageReceiver().clearImage();
        setVisibility(GONE);
    }

    public MessageObject getMessage() {
        return currentMessage;
    }

    public TLRPC.Document getDocument() {
        return currentDocument;
    }

    public TLRPC.InputStickerSet getInputStickerSet() {
        if (currentDocument == null) return null;
        for (TLRPC.DocumentAttribute attr : currentDocument.attributes) {
            if (attr instanceof TLRPC.TL_documentAttributeSticker) {
                TLRPC.InputStickerSet set = attr.stickerset;
                if (set != null && !(set instanceof TLRPC.TL_inputStickerSetEmpty)) {
                    return set;
                }
            }
        }
        return null;
    }

    private static TLRPC.PhotoSize findStripped(java.util.List<TLRPC.PhotoSize> sizes) {
        if (sizes == null) return null;
        for (TLRPC.PhotoSize s : sizes) {
            if (s instanceof TLRPC.TL_photoStrippedSize) return s;
        }
        return null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (currentMessage != null && currentDocument != null) {
            setSticker(currentMessage);
        }
    }
}