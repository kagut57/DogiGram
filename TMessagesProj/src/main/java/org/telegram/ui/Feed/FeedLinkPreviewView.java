package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class FeedLinkPreviewView extends LinearLayout {

    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private final TextView siteNameView;
    private final TextView titleView;
    private final TextView descriptionView;
    private final BackupImageView previewImageView;

    private TLRPC.WebPage currentPage;
    private Runnable onClickRunnable;

    private static final int STRIPE_WIDTH = 3;
    private static final int CORNER_RADIUS = 8;
    private static final int LEFT_PADDING = 10;

    public FeedLinkPreviewView(Context context, Theme.ResourcesProvider resourceProvider) {
        super(context);
        setOrientation(VERTICAL);
        setWillNotDraw(false);

        int accentColor = Theme.getColor(
                Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);
        int grayColor = Theme.getColor(
                Theme.key_windowBackgroundWhiteGrayText3, resourceProvider);
        int textColor = Theme.getColor(
                Theme.key_windowBackgroundWhiteBlackText, resourceProvider);

        stripePaint.setColor(accentColor);
        bgPaint.setColor((accentColor & 0x00FFFFFF) | 0x0F000000);

        setPadding(
                dp(STRIPE_WIDTH + LEFT_PADDING),
                dp(8),
                dp(8),
                dp(8)
        );

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);

        siteNameView = new TextView(context);
        siteNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        siteNameView.setTextColor(accentColor);
        siteNameView.setTypeface(AndroidUtilities.bold());
        siteNameView.setMaxLines(1);
        siteNameView.setEllipsize(TextUtils.TruncateAt.END);
        siteNameView.setVisibility(GONE);
        textColumn.addView(siteNameView,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTextColor(textColor);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setVisibility(GONE);
        textColumn.addView(titleView,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                        0, 2, 0, 0));

        descriptionView = new TextView(context);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionView.setTextColor(grayColor);
        descriptionView.setMaxLines(3);
        descriptionView.setEllipsize(TextUtils.TruncateAt.END);
        descriptionView.setVisibility(GONE);
        textColumn.addView(descriptionView,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                        0, 2, 0, 0));

        row.addView(textColumn,
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        previewImageView = new BackupImageView(context);
        previewImageView.setRoundRadius(dp(6));
        previewImageView.setVisibility(GONE);
        row.addView(previewImageView,
                LayoutHelper.createLinear(72, 72,
                        Gravity.TOP, 8, 0, 0, 0));

        addView(row,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setClickable(true);
        setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
    }

    public void setWebPage(TLRPC.WebPage page, Runnable onClick) {
        this.currentPage = page;
        this.onClickRunnable = onClick;

        if (page == null) {
            setVisibility(GONE);
            return;
        }

        if (!TextUtils.isEmpty(page.site_name)) {
            siteNameView.setText(page.site_name.toUpperCase());
            siteNameView.setVisibility(VISIBLE);
        } else if (!TextUtils.isEmpty(page.url)) {
            siteNameView.setText(extractDomain(page.url));
            siteNameView.setVisibility(VISIBLE);
        } else {
            siteNameView.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(page.title)) {
            titleView.setText(page.title);
            titleView.setVisibility(VISIBLE);
        } else if (!TextUtils.isEmpty(page.author)) {
            titleView.setText(page.author);
            titleView.setVisibility(VISIBLE);
        } else {
            titleView.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(page.description)) {
            descriptionView.setText(page.description);
            descriptionView.setVisibility(VISIBLE);
        } else {
            descriptionView.setVisibility(GONE);
        }

        bindPhoto(page);

        setOnClickListener(v -> {
            if (onClickRunnable != null) onClickRunnable.run();
        });

        setVisibility(VISIBLE);
    }

    public void clear() {
        currentPage = null;
        onClickRunnable = null;
        siteNameView.setVisibility(GONE);
        titleView.setVisibility(GONE);
        descriptionView.setVisibility(GONE);
        previewImageView.setVisibility(GONE);
        previewImageView.getImageReceiver().clearImage();
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cr = dp(CORNER_RADIUS);
        bgRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(bgRect, cr, cr, bgPaint);

        float stripeW = dp(STRIPE_WIDTH);
        bgRect.set(0, dp(4), stripeW, getHeight() - dp(4));
        canvas.drawRoundRect(bgRect, stripeW / 2f, stripeW / 2f, stripePaint);
    }

    private void bindPhoto(TLRPC.WebPage page) {
        previewImageView.getImageReceiver().clearImage();

        if (page.photo instanceof TLRPC.TL_photo) {
            TLRPC.TL_photo photo = (TLRPC.TL_photo) page.photo;
            TLRPC.PhotoSize best = FeedMediaHelper.bestSize(photo.sizes);
            if (best != null) {
                previewImageView.setImage(
                        ImageLocation.getForPhoto(best, photo), "72_72",
                        null, null, 0, page);
                previewImageView.setVisibility(VISIBLE);
                adjustLayoutForImage(true);
                return;
            }
        }

        if (page.document instanceof TLRPC.TL_document) {
            TLRPC.TL_document doc = (TLRPC.TL_document) page.document;
            if (doc.thumbs != null && !doc.thumbs.isEmpty()) {
                TLRPC.PhotoSize thumb = FeedMediaHelper.bestSize(doc.thumbs);
                if (thumb != null) {
                    previewImageView.setImage(
                            ImageLocation.getForDocument(thumb, doc), "72_72",
                            null, null, 0, page);
                    previewImageView.setVisibility(VISIBLE);
                    adjustLayoutForImage(true);
                    return;
                }
            }
        }

        previewImageView.setVisibility(GONE);
        adjustLayoutForImage(false);
    }

    private void adjustLayoutForImage(boolean hasImage) {
        descriptionView.setMaxLines(hasImage ? 2 : 3);
        titleView.setMaxLines(hasImage ? 1 : 2);
    }

    private static String extractDomain(String url) {
        if (url == null) return "";
        try {
            String s = url.replaceFirst("https?://", "");
            int slash = s.indexOf('/');
            if (slash > 0) s = s.substring(0, slash);
            return s;
        } catch (Exception e) {
            return url;
        }
    }

    public static TLRPC.WebPage extractWebPage(FeedController.FeedItem item) {
        if (item == null) return null;

        MessageObject primary = item.getPrimaryMessage();
        if (primary == null || primary.messageOwner == null) return null;

        if (primary.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            TLRPC.TL_messageMediaWebPage media =
                    (TLRPC.TL_messageMediaWebPage) primary.messageOwner.media;
            if (media.webpage instanceof TLRPC.TL_webPage) {
                return media.webpage;
            }
        }

        return null;
    }

    public TLRPC.WebPage getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(TLRPC.WebPage currentPage) {
        this.currentPage = currentPage;
    }
}