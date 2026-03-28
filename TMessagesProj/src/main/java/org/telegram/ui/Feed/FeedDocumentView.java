package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class FeedDocumentView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourceProvider;
    private final Context context;

    private final List<DocumentRow> rows = new ArrayList<>();
    private FeedController.FeedItem currentItem;

    public FeedController.FeedItem getCurrentItem() {
        return currentItem;
    }

    public void setCurrentItem(FeedController.FeedItem currentItem) {
        this.currentItem = currentItem;
    }

    private static class DocumentRow {
        TLRPC.Document document;
        MessageObject message;
        String fileKey;

        LinearLayout rowLayout;
        ImageView iconView;
        TextView nameView;
        TextView sizeView;
        ProgressBar progressBar;
    }

    public FeedDocumentView(Context context, int account, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.context = context;
        this.currentAccount = account;
        this.resourceProvider = resourceProvider;

        setOrientation(VERTICAL);
    }

    @SuppressLint("SetTextI18n")
    public void setData(FeedController.FeedItem item) {
        currentItem = item;
        removeAllViews();
        rows.clear();

        if (item == null) {
            setVisibility(GONE);
            return;
        }

        List<DocumentInfo> docs = collectDocuments(item);
        if (docs.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        for (int i = 0; i < docs.size(); i++) {
            DocumentInfo info = docs.get(i);
            DocumentRow row = createRow(info.document, info.message);
            rows.add(row);

            if (i < docs.size() - 1) {
                addView(row.rowLayout, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                        0, 0, 0, 0));
            } else {
                addView(row.rowLayout, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            updateRowState(row);
        }

        setVisibility(VISIBLE);
    }

    public void clear() {
        currentItem = null;
        removeAllViews();
        rows.clear();
        setVisibility(GONE);
    }

    private static class DocumentInfo {
        TLRPC.Document document;
        MessageObject message;

        DocumentInfo(TLRPC.Document doc, MessageObject msg) {
            this.document = doc;
            this.message = msg;
        }
    }

    private List<DocumentInfo> collectDocuments(FeedController.FeedItem item) {
        List<DocumentInfo> result = new ArrayList<>();
        for (MessageObject msg : item.messages) {
            TLRPC.MessageMedia media = msg.messageOwner.media;
            if (!(media instanceof TLRPC.TL_messageMediaDocument)) continue;
            if (media.document == null) continue;

            TLRPC.Document doc = media.document;

            boolean isMedia = false;
            for (TLRPC.DocumentAttribute attr : doc.attributes) {
                if (attr instanceof TLRPC.TL_documentAttributeVideo
                        || attr instanceof TLRPC.TL_documentAttributeAnimated
                        || attr instanceof TLRPC.TL_documentAttributeAudio
                        || attr instanceof TLRPC.TL_documentAttributeSticker) {
                    isMedia = true;
                    break;
                }
            }
            if (isMedia) continue;

            String mime = doc.mime_type;
            if (mime != null && (mime.startsWith("video/")
                    || mime.startsWith("audio/")
                    || mime.equals("image/webp"))) {
                continue;
            }

            result.add(new DocumentInfo(doc, msg));
        }
        return result;
    }

    @SuppressLint("SetTextI18n")
    private DocumentRow createRow(TLRPC.Document doc, MessageObject msg) {
        int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);
        int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider);

        DocumentRow row = new DocumentRow();
        row.document = doc;
        row.message = msg;
        row.fileKey = FileLoader.getAttachFileName(doc);

        row.rowLayout = new LinearLayout(context);
        row.rowLayout.setOrientation(HORIZONTAL);
        row.rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        row.rowLayout.setPadding(0, dp(8), 0, dp(4));
        row.rowLayout.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
        row.rowLayout.setOnClickListener(v -> onRowClick(row));

        row.iconView = new ImageView(context);
        row.iconView.setImageResource(R.drawable.msg_round_file_s);
        row.iconView.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        row.rowLayout.addView(row.iconView,
                LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(VERTICAL);
        textCol.setPadding(dp(10), 0, 0, 0);

        row.nameView = new TextView(context);
        row.nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        row.nameView.setTypeface(AndroidUtilities.bold());
        row.nameView.setTextColor(accentColor);
        row.nameView.setMaxLines(1);
        row.nameView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(row.nameView,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        row.sizeView = new TextView(context);
        row.sizeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        row.sizeView.setTextColor(grayColor);
        textCol.addView(row.sizeView,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                        0, 2, 0, 0));

        row.progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        row.progressBar.setMax(100);
        row.progressBar.setProgress(0);
        row.progressBar.setVisibility(GONE);
        row.progressBar.setScaleY(0.5f);
        textCol.addView(row.progressBar,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6,
                        0, 2, 0, 0));

        row.rowLayout.addView(textCol,
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        String fileName = null;
        for (TLRPC.DocumentAttribute attr : doc.attributes) {
            if (attr instanceof TLRPC.TL_documentAttributeFilename) {
                fileName = attr.file_name;
                break;
            }
        }
        if (fileName == null || fileName.isEmpty()) fileName = "Document";
        row.nameView.setText(fileName);
        row.sizeView.setText(FeedUtils.formatFileSize(doc.size));

        return row;
    }

    private void onRowClick(DocumentRow row) {
        if (row.document == null) return;

        FileLoader fl = FileLoader.getInstance(currentAccount);
        boolean isLoading = fl.isLoadingFile(row.fileKey);
        File path = getDocumentPath(fl, row.document);

        if (path.exists() && !isLoading) {
            openDocument(path, row.document);
        } else if (isLoading) {
            fl.cancelLoadFile(row.document);
            updateRowState(row);
        } else {
            fl.loadFile(row.document, row.message,
                    FileLoader.PRIORITY_HIGH, 0);
            updateRowState(row);
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateRowState(DocumentRow row) {
        if (row.document == null) return;

        FileLoader fl = FileLoader.getInstance(currentAccount);
        boolean isLoading = fl.isLoadingFile(row.fileKey);
        File path = getDocumentPath(fl, row.document);
        boolean exists = path.exists() && !isLoading;

        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);

        if (exists) {
            row.iconView.setImageResource(R.drawable.msg_round_file_s);
            row.iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            row.progressBar.setVisibility(GONE);
            row.sizeView.setText(FeedUtils.formatFileSize(row.document.size));
        } else if (isLoading) {
            row.iconView.setImageResource(R.drawable.msg_round_cancel_m);
            row.iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            row.progressBar.setVisibility(VISIBLE);
            row.sizeView.setText(LocaleController.getString("Loading", R.string.Loading)
                    + "… · " + FeedUtils.formatFileSize(row.document.size));
        } else {
            row.iconView.setImageResource(R.drawable.msg_round_load_m);
            row.iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            row.progressBar.setVisibility(GONE);
            row.progressBar.setProgress(0);
        }
    }

    private File getDocumentPath(FileLoader fl, TLRPC.Document doc) {
        File path = fl.getPathToAttach(doc, false);
        if (!path.exists()) path = fl.getPathToAttach(doc, true);
        return path;
    }

    private void openDocument(File file, TLRPC.Document doc) {
        if (file == null || !file.exists() || doc == null) return;

        String docName = FileLoader.getDocumentFileName(doc);
        if (TextUtils.isEmpty(docName)) docName = file.getName();
        String mime = doc.mime_type;
        if (TextUtils.isEmpty(mime)) mime = "application/octet-stream";

        Activity activity = FeedUtils.getActivity(getContext());
        if (activity == null) return;

        try {
            AndroidUtilities.openForView(file, docName, mime, activity, resourceProvider, false);
        } catch (Exception e) {
            FeedUtils.openFileFallback(getContext(), file, mime);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.fileLoaded);
        nc.addObserver(this, NotificationCenter.fileLoadFailed);
        nc.addObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.fileLoaded);
        nc.removeObserver(this, NotificationCenter.fileLoadFailed);
        nc.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount || rows.isEmpty()) return;

        String fileName = (String) args[0];

        for (DocumentRow row : rows) {
            if (row.fileKey == null || !row.fileKey.equals(fileName)) continue;

            if (id == NotificationCenter.fileLoaded
                    || id == NotificationCenter.fileLoadFailed) {
                updateRowState(row);

            } else if (id == NotificationCenter.fileLoadProgressChanged) {
                long loaded = (Long) args[1];
                long total = (Long) args[2];
                if (total > 0) {
                    int pct = (int) (loaded * 100 / total);
                    row.progressBar.setProgress(pct);
                    row.sizeView.setText(pct + "% · "
                            + FeedUtils.formatFileSize(row.document.size));
                }
            }
            break;
        }
    }
}